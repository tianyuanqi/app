package com.yuanqi.app.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.auth.dto.AuthRequests;
import com.yuanqi.app.auth.support.AuthPolicy;
import com.yuanqi.app.auth.vo.LoginVO;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.user.entity.User;
import com.yuanqi.app.user.enums.AccountStatus;
import com.yuanqi.app.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 认证领域服务：注册、登录、刷新、退出编排。
 * <p>会话细节委托 {@link AuthSessionService}，审计委托 {@link AuthAuditService}。</p>
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final AuthAuditService authAuditService;
    private final AuthRateLimiter authRateLimiter;
    private final AuthProperties authProperties;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       AuthSessionService authSessionService,
                       AuthAuditService authAuditService,
                       AuthRateLimiter authRateLimiter,
                       AuthProperties authProperties,
                       JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.authAuditService = authAuditService;
        this.authRateLimiter = authRateLimiter;
        this.authProperties = authProperties;
        this.jwtService = jwtService;
    }

    /**
     * 邮箱注册并自动登录。
     * <p>当前仅支持邮箱注册渠道；手机号注册留待后续拓展。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(AuthRequests.Register request, String ip, String userAgent) {
        authRateLimiter.checkRegister(ip);

        String username = request.getUsername().trim();
        AuthPolicy.validateUsername(username);
        AuthPolicy.validatePassword(request.getPassword());
        String email = normalizeEmail(request.getEmail());
        if (email == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "邮箱不能为空");
        }

        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))) {
            authAuditService.record(null, AuthAuditService.REGISTER_FAILED, false, ip, userAgent, "用户名冲突:" + username);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email))) {
            authAuditService.record(null, AuthAuditService.REGISTER_FAILED, false, ip, userAgent, "邮箱冲突:" + email);
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUid(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(email);
        user.setRole("USER");
        user.setAccountStatus(AccountStatus.ACTIVE.name());
        user.setFailedLoginCount(0);
        user.setPasswordChangedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        LoginVO vo = authSessionService.issueTokenPair(user, ip, userAgent);
        authAuditService.record(user.getId(), AuthAuditService.REGISTER_SUCCESS, true, ip, userAgent, "邮箱注册成功");
        return vo;
    }

    /**
     * 登录：account 可为邮箱或用户名。
     * <p>对外统一失败文案，审计日志区分原因；失败累计达到阈值后锁定。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(AuthRequests.Login request, String ip, String userAgent) {
        authRateLimiter.checkLogin(ip);

        String account = request.getAccount().trim();
        User user = findByAccount(account);

        // 用户不存在：对外与密码错误一致，防枚举
        if (user == null) {
            authAuditService.record(null, AuthAuditService.LOGIN_FAILED, false, ip, userAgent, "账号不存在:" + account);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        assertAccountCanAuthenticate(user, ip, userAgent);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            onLoginFailed(user, ip, userAgent);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 登录成功：清零失败计数，解除过期锁定状态
        user.setFailedLoginCount(0);
        if (AccountStatus.LOCKED.name().equals(user.getAccountStatus())) {
            user.setAccountStatus(AccountStatus.ACTIVE.name());
            user.setLockedUntil(null);
        }
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        LoginVO vo = authSessionService.issueTokenPair(user, ip, userAgent);
        authAuditService.record(user.getId(), AuthAuditService.LOGIN_SUCCESS, true, ip, userAgent, "登录成功");
        return vo;
    }

    /**
     * 刷新令牌：校验用户状态后执行会话旋转。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO refresh(AuthRequests.RefreshToken request, String ip, String userAgent) {
        Claims claims = jwtService.parseRefreshClaims(request.getRefreshToken());
        if (claims == null) {
            authAuditService.record(null, AuthAuditService.REFRESH_FAILED, false, ip, userAgent, "refresh 解析失败");
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        Long userId = jwtService.readUserId(claims);
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            authAuditService.record(userId, AuthAuditService.REFRESH_FAILED, false, ip, userAgent, "用户不存在");
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }

        assertAccountCanAuthenticate(user, ip, userAgent);

        try {
            LoginVO vo = authSessionService.rotateRefreshToken(request.getRefreshToken(), user, ip, userAgent);
            authAuditService.record(user.getId(), AuthAuditService.REFRESH_SUCCESS, true, ip, userAgent, "刷新成功");
            return vo;
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.REFRESH_REUSED) {
                authAuditService.record(user.getId(), AuthAuditService.REFRESH_FAILED, false, ip, userAgent, ex.getMessage());
            }
            throw ex;
        }
    }

    /**
     * 退出登录：吊销当前 refresh 会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(AuthRequests.Logout request, Long userId, String ip, String userAgent) {
        authSessionService.revokeByRefreshToken(request.getRefreshToken());
        authAuditService.record(userId, AuthAuditService.LOGOUT_SUCCESS, true, ip, userAgent, "退出登录");
    }

    /** 改密成功后由用户服务调用：吊销全部会话 */
    public void revokeAllSessionsAfterPasswordChange(Long userId, String ip, String userAgent) {
        authSessionService.revokeAllSessions(userId);
        authAuditService.record(userId, AuthAuditService.PASSWORD_CHANGED, true, ip, userAgent, "改密并吊销全部会话");
    }

    /** 供过滤器二次校验用户是否仍可访问系统 */
    public User requireActiveUser(Long userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        if (AccountStatus.DISABLED.name().equals(user.getAccountStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        if (isCurrentlyLocked(user)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请于 " + user.getLockedUntil() + " 后再试");
        }
        return user;
    }

    private User findByAccount(String account) {
        if (account.contains("@")) {
            return userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, account.toLowerCase(Locale.ROOT)));
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, account));
    }

    /** 校验账号是否允许发起认证（登录/刷新） */
    private void assertAccountCanAuthenticate(User user, String ip, String userAgent) {
        if (AccountStatus.DISABLED.name().equals(user.getAccountStatus())) {
            authAuditService.record(user.getId(), AuthAuditService.LOGIN_FAILED, false, ip, userAgent, "账号禁用");
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        if (isCurrentlyLocked(user)) {
            authAuditService.record(user.getId(), AuthAuditService.LOGIN_LOCKED, false, ip, userAgent,
                    "锁定至 " + user.getLockedUntil());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请于 " + user.getLockedUntil() + " 后再试");
        }
        // 锁定已过期：恢复为 ACTIVE，允许本次继续校验密码
        if (AccountStatus.LOCKED.name().equals(user.getAccountStatus())
                && user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(LocalDateTime.now())) {
            user.setAccountStatus(AccountStatus.ACTIVE.name());
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
            userMapper.updateById(user);
        }
    }

    private boolean isCurrentlyLocked(User user) {
        return AccountStatus.LOCKED.name().equals(user.getAccountStatus())
                && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    /** 密码错误：累计失败次数，达到阈值则锁定 */
    private void onLoginFailed(User user, String ip, String userAgent) {
        int failed = user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount();
        failed += 1;
        user.setFailedLoginCount(failed);
        user.setUpdatedAt(LocalDateTime.now());
        if (failed >= authProperties.getMaxFailedLogin()) {
            user.setAccountStatus(AccountStatus.LOCKED.name());
            user.setLockedUntil(LocalDateTime.now().plusMinutes(authProperties.getLockMinutes()));
            userMapper.updateById(user);
            authAuditService.record(user.getId(), AuthAuditService.LOGIN_LOCKED, false, ip, userAgent,
                    "失败次数达到 " + failed + "，锁定 " + authProperties.getLockMinutes() + " 分钟");
        } else {
            userMapper.updateById(user);
            authAuditService.record(user.getId(), AuthAuditService.LOGIN_FAILED, false, ip, userAgent,
                    "密码错误，累计失败 " + failed);
        }
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
