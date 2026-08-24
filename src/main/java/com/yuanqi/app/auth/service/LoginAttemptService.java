package com.yuanqi.app.auth.service;

import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.entity.LoginSecurityState;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.mapper.LoginSecurityStateMapper;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 登录失败状态必须在错误响应事务中提交，不能随业务异常回滚。 */
@Service
public class LoginAttemptService {
    private final String dummyPasswordHash;
    private final AccountMapper accountMapper;
    private final LoginSecurityStateMapper securityMapper;
    private final AuthRateLimiter rateLimiter;
    private final PasswordEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public LoginAttemptService(AccountMapper accountMapper, LoginSecurityStateMapper securityMapper,
                               AuthRateLimiter rateLimiter, PasswordEncoder encoder,
                               AuthProperties properties, Clock clock) {
        this.accountMapper = accountMapper;
        this.securityMapper = securityMapper;
        this.rateLimiter = rateLimiter;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = encoder.encode("2400px-dummy-password-7Q");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Account authenticate(String emailKey, String password, String ip) {
        Account account = accountMapper.findByEmailKey(emailKey);
        if (account != null && "DISABLED".equals(account.getGovernanceStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        LoginSecurityState state = account == null ? null : securityMapper.findForUpdate(account.getId());
        LocalDateTime now = now();
        if (state != null && state.getLockedUntil() != null && state.getLockedUntil().isAfter(now)) {
            throw locked(now, state.getLockedUntil());
        }
        rateLimiter.checkLogin(emailKey, ip);
        if (account == null) {
            encoder.matches(password, dummyPasswordHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (state == null) {
            state = new LoginSecurityState();
            state.setAccountId(account.getId());
            state.setFailedCount(0);
            state.setRowVersion(0L);
            securityMapper.insert(state);
            state = securityMapper.findForUpdate(account.getId());
        }
        if (!encoder.matches(password, account.getPasswordHash())) {
            recordFailure(state, now);
            if (state.getLockedUntil() != null && state.getLockedUntil().isAfter(now)) {
                throw locked(now, state.getLockedUntil());
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        state.setFailedCount(0);
        state.setWindowStartedAt(null);
        state.setLockedUntil(null);
        state.setRowVersion(state.getRowVersion() + 1);
        securityMapper.updateById(state);
        return account;
    }

    private void recordFailure(LoginSecurityState state, LocalDateTime now) {
        if (state.getWindowStartedAt() == null || !state.getWindowStartedAt().plusMinutes(15).isAfter(now)) {
            state.setWindowStartedAt(now);
            state.setFailedCount(1);
        } else {
            state.setFailedCount(state.getFailedCount() + 1);
        }
        if (state.getFailedCount() >= properties.getMaxFailedLogin()) {
            state.setLockedUntil(now.plusMinutes(properties.getLockMinutes()));
        }
        state.setRowVersion(state.getRowVersion() + 1);
        securityMapper.updateById(state);
    }

    private BusinessException locked(LocalDateTime now, LocalDateTime until) {
        int retry = (int) Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000);
        return new BusinessException(ErrorCode.ACCOUNT_LOCKED, ErrorCode.ACCOUNT_LOCKED.getMessage(), true, retry);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
