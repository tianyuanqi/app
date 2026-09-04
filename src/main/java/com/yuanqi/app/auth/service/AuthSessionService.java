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

/** 管理持久化会话与刷新凭证；内部 LocalDateTime 统一按 UTC 解释，刷新不延长绝对期限。 */
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

    /** 为调用方已认证的账号创建独立会话及首个刷新凭证，不吊销该账号的其他会话。 */
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

    /**
     * 锁定刷新凭证后锁定所属会话，消费旧凭证并建立下一代凭证。
     * 直接调用本方法遇到已旋转凭证时会吊销会话；HTTP 入口还受前置 resolve 校验限制。
     * BusinessException 不回滚，保留重放、过期或账号异常引起的状态更新。
     */
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

    /**
     * 供 CSRF 获取、刷新和注销做只读前置校验，不加行锁、不旋转或吊销凭证。
     * 检查凭证 ACTIVE、会话状态及绝对期限和账号状态，不单独检查凭证 expiresAt。
     * 已旋转凭证在这里返回 SESSION_INVALID，不进入 rotate 的重放处理。
     */
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

    /**
     * 吊销凭证所属会话；返回 true 表示无需再次吊销，false 表示本次执行了注销。
     * 直接传入已旋转凭证且所属会话仍有效时，记录重放并抛出业务异常，保留吊销结果。
     */
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

    /** 批量吊销账号的 ACTIVE 会话；不改写刷新凭证行，后续认证通过会话状态拒绝访问。 */
    @Transactional
    public void revokeAll(Long accountId, String reason) {
        sessionMapper.update(null, new LambdaUpdateWrapper<AuthSession>()
                .eq(AuthSession::getAccountId, accountId).eq(AuthSession::getStatus, "ACTIVE")
                .set(AuthSession::getStatus, "REVOKED").set(AuthSession::getRevokedAt, now())
                .set(AuthSession::getRevokeReason, reason).setSql("row_version=row_version+1"));
    }

    /** 按公开会话标识查询；会话须为 ACTIVE 且绝对期限严格晚于当前时间，账号状态由调用方复核。 */
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
        // 数据库仅保存随机凭证的哈希；各代凭证沿用会话绝对期限，原文只返回给调用方写 Cookie。
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

    /** HTTP 装配用内部结果；refreshCredential 原文不得写入日志，expiresAt 为 UTC 会话绝对期限。 */
    public record IssuedSession(AuthViews.SessionView view, String refreshCredential, String csrfToken,
                                LocalDateTime expiresAt) {
    }

    public record ResolvedSession(Account account, AuthSession session) {
    }
}
