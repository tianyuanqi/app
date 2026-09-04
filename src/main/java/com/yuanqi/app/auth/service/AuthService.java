package com.yuanqi.app.auth.service;

import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.auth.dto.AuthRequests;
import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.entity.LoginSecurityState;
import com.yuanqi.app.auth.entity.VerificationFlow;
import com.yuanqi.app.auth.entity.RegistrationAttempt;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.mapper.LoginSecurityStateMapper;
import com.yuanqi.app.auth.mapper.RegistrationAttemptMapper;
import com.yuanqi.app.auth.support.AuthPolicy;
import com.yuanqi.app.auth.support.EmailNormalizer;
import com.yuanqi.app.auth.support.PublicIdGenerator;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.user.entity.UserProfile;
import com.yuanqi.app.user.mapper.UserProfileMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 冻结 v1.0 的邮箱注册、自动登录和登录锁定编排。 */
@Service
public class AuthService {
    private final AccountMapper accountMapper;
    private final UserProfileMapper profileMapper;
    private final LoginSecurityStateMapper securityMapper;
    private final VerificationService verificationService;
    private final AuthRateLimiter rateLimiter;
    private final AuthSessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final EmailNormalizer emailNormalizer;
    private final PublicIdGenerator idGenerator;
    private final AuthProperties properties;
    private final Clock clock;
    private final LoginAttemptService loginAttemptService;
    private final RegistrationAttemptMapper attemptMapper;

    public AuthService(AccountMapper accountMapper, UserProfileMapper profileMapper,
                       LoginSecurityStateMapper securityMapper, VerificationService verificationService,
                       AuthRateLimiter rateLimiter, AuthSessionService sessionService,
                       PasswordEncoder passwordEncoder, EmailNormalizer emailNormalizer,
                       PublicIdGenerator idGenerator, AuthProperties properties, Clock clock,
                       LoginAttemptService loginAttemptService, RegistrationAttemptMapper attemptMapper) {
        this.accountMapper = accountMapper;
        this.profileMapper = profileMapper;
        this.securityMapper = securityMapper;
        this.verificationService = verificationService;
        this.rateLimiter = rateLimiter;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.emailNormalizer = emailNormalizer;
        this.idGenerator = idGenerator;
        this.properties = properties;
        this.clock = clock;
        this.loginAttemptService = loginAttemptService;
        this.attemptMapper = attemptMapper;
    }

    @Transactional
    public AuthSessionService.IssuedSession register(AuthRequests.Register request, String ip) {
        if (attemptMapper.selectById(request.registrationAttemptId().toString()) != null)
            throw new BusinessException(ErrorCode.REGISTRATION_ALREADY_COMPLETED);
        AuthPolicy.validatePassword(request.password());
        String emailKey = emailNormalizer.normalize(request.email());
        rateLimiter.checkRegistration(emailKey, ip);
        VerificationFlow flow = verificationService.verifyForRegistration(request.flowId(), request.email(),
                request.verificationCode(), ip);
        if (accountMapper.findByEmailKeyForUpdate(emailKey) != null) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        LocalDateTime now = now();
        Account account = new Account();
        account.setUid(idGenerator.next());
        account.setEmail(emailKey);
        account.setEmailKey(emailKey);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole("USER");
        account.setGovernanceStatus("ACTIVE");
        account.setRowVersion(0L);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        try {
            accountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        UserProfile profile = new UserProfile();
        profile.setAccountId(account.getId());
        profile.setUsername(AuthPolicy.initialUsername());
        profile.setRowVersion(0L);
        profile.setUpdatedAt(now);
        profileMapper.insert(profile);
        LoginSecurityState security = new LoginSecurityState();
        security.setAccountId(account.getId());
        security.setFailedCount(0);
        security.setRowVersion(0L);
        securityMapper.insert(security);
        verificationService.consume(flow);
        RegistrationAttempt attempt=new RegistrationAttempt();attempt.setAttemptId(request.registrationAttemptId().toString());
        attempt.setFlowId(flow.getId());attempt.setAccountId(account.getId());attempt.setStatus("COMPLETED");attempt.setCompletedAt(now);
        attemptMapper.insert(attempt);
        return sessionService.issue(account);
    }

    /**
     * 使用归一化邮箱完成凭据校验后签发新会话。
     * 此方法不声明事务，常规登录入口中失败计数事务与会话签发事务分别完成。
     */
    public AuthSessionService.IssuedSession login(AuthRequests.Login request, String ip) {
        String emailKey = emailNormalizer.normalize(request.email());
        Account account = loginAttemptService.authenticate(emailKey, request.password(), ip);
        return sessionService.issue(account);
    }

    /** 旧资料服务的过渡调用；目标改密接口移除后随旧服务一起删除。 */
    public void revokeAllSessionsAfterPasswordChange(Long accountId, String ip, String userAgent) {
        sessionService.revokeAll(accountId, "PASSWORD_CHANGED");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
