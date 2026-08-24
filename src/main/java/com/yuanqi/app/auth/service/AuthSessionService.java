package com.yuanqi.app.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuanqi.app.auth.config.AuthProperties;
import com.yuanqi.app.auth.entity.AuthSession;
import com.yuanqi.app.auth.mapper.AuthSessionMapper;
import com.yuanqi.app.auth.vo.LoginVO;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.user.entity.User;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 认证会话服务：签发、旋转、吊销 refresh 会话。
 */
@Service
public class AuthSessionService {

    private final AuthSessionMapper authSessionMapper;
    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final AuthAuditService authAuditService;

    public AuthSessionService(AuthSessionMapper authSessionMapper,
                              JwtService jwtService,
                              AuthProperties authProperties,
                              AuthAuditService authAuditService) {
        this.authSessionMapper = authSessionMapper;
        this.jwtService = jwtService;
        this.authProperties = authProperties;
        this.authAuditService = authAuditService;
    }

    /**
     * 为用户创建新会话并签发双令牌。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO issueTokenPair(User user, String ip, String userAgent) {
        String jti = jwtService.newJti();
        String refreshToken = jwtService.createRefreshToken(user, jti);
        String accessToken = jwtService.createAccessToken(user);

        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setJti(jti);
        session.setTokenHash(sha256(refreshToken));
        session.setExpiresAt(LocalDateTime.now().plusSeconds(authProperties.getRefreshExpireMs() / 1000L));
        session.setIp(ip);
        session.setUserAgent(userAgent);
        session.setCreatedAt(LocalDateTime.now());
        authSessionMapper.insert(session);

        return toLoginVO(user, accessToken, refreshToken);
    }

    /**
     * 使用 refresh 旋转会话：旧会话吊销，签发新对令牌。
     * <p>若检测到已旋转令牌被再次使用，则吊销该用户全部会话。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO rotateRefreshToken(String refreshToken, User user, String ip, String userAgent) {
        Claims claims = jwtService.parseRefreshClaims(refreshToken);
        if (claims == null) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        String jti = jwtService.readJti(claims);
        Long tokenUserId = jwtService.readUserId(claims);
        if (jti == null || tokenUserId == null || !tokenUserId.equals(user.getId())) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }

        AuthSession session = authSessionMapper.selectOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getJti, jti));
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }

        // 已吊销且存在替换 jti → 判定 refresh 复用（疑似令牌被盗）
        if (session.getRevokedAt() != null && session.getReplacedByJti() != null) {
            revokeAllSessions(user.getId());
            authAuditService.record(user.getId(), AuthAuditService.REFRESH_REUSE, false,
                    ip, userAgent, "检测到 refresh 复用，已吊销全部会话");
            throw new BusinessException(ErrorCode.REFRESH_REUSED);
        }

        if (session.getRevokedAt() != null
                || session.getExpiresAt().isBefore(LocalDateTime.now())
                || !sha256(refreshToken).equals(session.getTokenHash())) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }

        String newJti = jwtService.newJti();
        String newRefresh = jwtService.createRefreshToken(user, newJti);
        String newAccess = jwtService.createAccessToken(user);

        // 吊销旧会话并记录被谁替换
        session.setRevokedAt(LocalDateTime.now());
        session.setReplacedByJti(newJti);
        authSessionMapper.updateById(session);

        AuthSession newSession = new AuthSession();
        newSession.setUserId(user.getId());
        newSession.setJti(newJti);
        newSession.setTokenHash(sha256(newRefresh));
        newSession.setExpiresAt(LocalDateTime.now().plusSeconds(authProperties.getRefreshExpireMs() / 1000L));
        newSession.setIp(ip);
        newSession.setUserAgent(userAgent);
        newSession.setCreatedAt(LocalDateTime.now());
        authSessionMapper.insert(newSession);

        return toLoginVO(user, newAccess, newRefresh);
    }

    /**
     * 退出登录：吊销当前 refresh 对应会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeByRefreshToken(String refreshToken) {
        Claims claims = jwtService.parseRefreshClaims(refreshToken);
        if (claims == null) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        String jti = jwtService.readJti(claims);
        AuthSession session = authSessionMapper.selectOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getJti, jti));
        if (session == null || session.getRevokedAt() != null) {
            // 幂等：会话已不存在或已吊销，视为退出成功
            return;
        }
        if (!sha256(refreshToken).equals(session.getTokenHash())) {
            throw new BusinessException(ErrorCode.SESSION_INVALID);
        }
        session.setRevokedAt(LocalDateTime.now());
        authSessionMapper.updateById(session);
    }

    /** 吊销指定用户的全部有效会话（改密、复用攻击时使用） */
    @Transactional(rollbackFor = Exception.class)
    public void revokeAllSessions(Long userId) {
        authSessionMapper.update(null, new LambdaUpdateWrapper<AuthSession>()
                .eq(AuthSession::getUserId, userId)
                .isNull(AuthSession::getRevokedAt)
                .set(AuthSession::getRevokedAt, LocalDateTime.now()));
    }

    private LoginVO toLoginVO(User user, String accessToken, String refreshToken) {
        LoginVO vo = new LoginVO();
        vo.setToken(accessToken);
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(jwtService.accessExpireSeconds());
        // uid 为主；userId 暂留兼容旧客户端
        vo.setUid(user.getUid());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setAccountStatus(user.getAccountStatus());
        return vo;
    }

    /** 计算 refresh 原文哈希，避免会话表存明文令牌 */
    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("计算令牌哈希失败", e);
        }
    }
}
