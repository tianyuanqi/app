package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证会话实体，对应表 auth_session。
 * <p>每个有效 refresh 对应一条会话；旋转时旧会话吊销并记录 replaced_by_jti。</p>
 */
@Data
@TableName("auth_session")
public class AuthSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** refresh JWT 的 jti */
    private String jti;

    /** refresh 原文的 SHA-256 十六进制哈希 */
    private String tokenHash;

    private LocalDateTime expiresAt;

    /** 非空表示已吊销 */
    private LocalDateTime revokedAt;

    /** 被旋转后指向的新 jti；若旧 token 再次出现则判定复用攻击 */
    private String replacedByJti;

    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
