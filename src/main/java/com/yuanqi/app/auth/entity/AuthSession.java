package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_session")
public class AuthSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long accountId;
    private String status;
    private LocalDateTime loginAt;
    private LocalDateTime absoluteExpiresAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private Long rowVersion;
}
