package com.yuanqi.app.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.entity.AuthSession;
import com.yuanqi.app.auth.entity.RefreshCredential;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.mapper.AuthSessionMapper;
import com.yuanqi.app.auth.mapper.RefreshCredentialMapper;
import com.yuanqi.app.auth.security.AuthCookieService;
import com.yuanqi.app.auth.security.CsrfTokenService;
import com.yuanqi.app.auth.support.CryptoSupport;
import com.yuanqi.app.auth.support.PublicIdGenerator;
import com.yuanqi.app.auth.vo.AuthViews;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.user.entity.UserProfile;
import com.yuanqi.app.user.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
public class AuthSessionService {
    private final AuthSessionMapper sessionMapper;
    private final RefreshCredentialMapper credentialMapper;
    private final AccountMapper accountMapper;
    private final UserProfileMapper profileMapper;
    private final JwtService jwtService;
    private final CsrfTokenService csrfTokenService;
    private final CryptoSupport crypto;
    private final PublicIdGenerator idGenerator;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionService(AuthSessionMapper sessionMapper,
                              RefreshCredentialMapper credentialMapper,
                              AccountMapper accountMapper,
                              UserProfileMapper profileMapper,
                              JwtService jwtService,
                              CsrfTokenService csrfTokenService,
                              CryptoSupport crypto,
                              PublicIdGenerator idGenerator,
                              AuthProperties properties,
                              Clock clock) {
        this.sessionMapper = sessionMapper;
        this.credentialMapper = credentialMapper;
        this.accountMapper = accountMapper;
        this.profileMapper = profileMapper;
        this.jwtService = jwtService;
        this.csrfTokenService = csrfTokenService;
        this.crypto = crypto;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedSession issue(Account account) {
        LocalDateTime now = now();
        AuthSession session = new AuthSession();
        session.setSessionId(idGenerator.next());
        session.setAccountId(account.getId());
        session.setStatus("ACTIVE");
        session.setLoginAt(now);
        session.setAbsoluteExpiresAt(now.plusNanos(properties.getSessionExpireMs() * 1_000_000));
        session.setRowVersion(0L);
        sessionMapper.insert(session);
        String rawRefresh = newCredential(session, null, 0);
        return result(account, session, rawRefresh);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedSession rotate(String rawRefresh) {
        RefreshCredential credential = requireCredential(rawRefresh);
        AuthSession session = sessionMapper.selectById(credential.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        session = sessionMapper.findBySessionIdForUpdate(session.getSessionId());
        if ("ROTATED".equals(credential.getStatus()) || "REUSED".equals(credential.getStatus())) {
            credential.setStatus("REUSED");
            credentialMapper.updateById(credential);
            revoke(session, "REFRESH_REUSED");
            throw new BusinessException(ErrorCode.REFRESH_REUSED);
        }
        validateActive(session, credential);
        Account account = accountMapper.selectById(session.getAccountId());
        if (account == null) {
            revoke(session, "ACCOUNT_MISSING");
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        if ("DISABLED".equals(account.getGovernanceStatus())) {
            revoke(session, "ACCOUNT_DISABLED");
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        credential.setStatus("ROTATED");
        credential.setUsedAt(now());
        credentialMapper.updateById(credential);
        String next = newCredential(session, credential.getId(), credential.getRotationNo() + 1);
        return result(account, session, next);
    }

    /** CSRF bootstrap 只校验，不旋转 Refresh。 */
    @Transactional(readOnly = true)
    public ResolvedSession resolve(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        RefreshCredential credential = credentialMapper.findByHash(crypto.sha256(rawRefresh));
        if (credential == null || !"ACTIVE".equals(credential.getStatus())) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        AuthSession session = sessionMapper.selectById(credential.getSessionId());
        if (session == null || !"ACTIVE".equals(session.getStatus()) || !session.getAbsoluteExpiresAt().isAfter(now())) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        Account account = accountMapper.selectById(session.getAccountId());
        if (account == null) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        if ("DISABLED".equals(account.getGovernanceStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        return new ResolvedSession(account, session);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public boolean logout(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            return true;
        }
        RefreshCredential credential = credentialMapper.findByHashForUpdate(crypto.sha256(rawRefresh));
        if (credential == null || "REVOKED".equals(credential.getStatus()) || "EXPIRED".equals(credential.getStatus())) {
            return true;
        }
        AuthSession session = sessionMapper.selectById(credential.getSessionId());
        if (session == null || !"ACTIVE".equals(session.getStatus())) {
            return true;
        }
        session = sessionMapper.findBySessionIdForUpdate(session.getSessionId());
        if ("ROTATED".equals(credential.getStatus()) || "REUSED".equals(credential.getStatus())) {
            credential.setStatus("REUSED");
            credentialMapper.updateById(credential);
            revoke(session, "REFRESH_REUSED");
            throw new BusinessException(ErrorCode.REFRESH_REUSED);
        }
        revoke(session, "LOGOUT");
        return false;
    }

    @Transactional
    public void revokeAll(Long accountId, String reason) {
        sessionMapper.update(null, new LambdaUpdateWrapper<AuthSession>()
                .eq(AuthSession::getAccountId, accountId).eq(AuthSession::getStatus, "ACTIVE")
                .set(AuthSession::getStatus, "REVOKED").set(AuthSession::getRevokedAt, now())
                .set(AuthSession::getRevokeReason, reason).setSql("row_version=row_version+1"));
    }

    public AuthSession findActiveSession(String sessionId) {
        AuthSession session = sessionMapper.findBySessionId(sessionId);
        return session != null && "ACTIVE".equals(session.getStatus()) && session.getAbsoluteExpiresAt().isAfter(now())
                ? session : null;
    }

    private RefreshCredential requireCredential(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        RefreshCredential credential = credentialMapper.findByHashForUpdate(crypto.sha256(raw));
        if (credential == null) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        return credential;
    }

    private void validateActive(AuthSession session, RefreshCredential credential) {
        LocalDateTime now = now();
        if (!"ACTIVE".equals(session.getStatus()) || !"ACTIVE".equals(credential.getStatus())
                || !session.getAbsoluteExpiresAt().isAfter(now) || !credential.getExpiresAt().isAfter(now)) {
            if ("ACTIVE".equals(session.getStatus())) {
                revoke(session, "EXPIRED");
            }
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
    }

    private String newCredential(AuthSession session, Long parentId, int rotation) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshCredential credential = new RefreshCredential();
        credential.setTokenId(idGenerator.next());
        credential.setSessionId(session.getId());
        credential.setTokenHash(crypto.sha256(raw));
        credential.setRotationNo(rotation);
        credential.setParentTokenId(parentId);
        credential.setStatus("ACTIVE");
        credential.setIssuedAt(now());
        credential.setExpiresAt(session.getAbsoluteExpiresAt());
        credentialMapper.insert(credential);
        return raw;
    }

    private IssuedSession result(Account account, AuthSession session, String rawRefresh) {
        UserProfile profile = profileMapper.selectById(account.getId());
        JwtService.AccessToken access = jwtService.createAccessToken(account, session.getSessionId(),
                session.getAbsoluteExpiresAt());
        String csrf = csrfTokenService.issue(session.getSessionId(), session.getAbsoluteExpiresAt());
        AuthViews.CsrfView csrfView = new AuthViews.CsrfView(AuthCookieService.CSRF_COOKIE,
                AuthCookieService.CSRF_HEADER, utc(session.getAbsoluteExpiresAt()));
        AuthViews.CurrentIdentity identity = new AuthViews.CurrentIdentity(account.getUid(),
                profile == null ? null : profile.getUsername(), null, account.getRole());
        AuthViews.SessionView view = new AuthViews.SessionView(access.value(), "Bearer",
                access.expiresAt().atOffset(ZoneOffset.UTC), utc(session.getAbsoluteExpiresAt()), identity, csrfView);
        return new IssuedSession(view, rawRefresh, csrf, session.getAbsoluteExpiresAt());
    }

    private void revoke(AuthSession session, String reason) {
        session.setStatus("REVOKED");
        session.setRevokedAt(now());
        session.setRevokeReason(reason);
        session.setRowVersion(session.getRowVersion() + 1);
        sessionMapper.updateById(session);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private OffsetDateTime utc(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    public record IssuedSession(AuthViews.SessionView view, String refreshCredential, String csrfToken,
                                LocalDateTime expiresAt) {
    }

    public record ResolvedSession(Account account, AuthSession session) {
    }
}
