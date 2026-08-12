package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证审计日志实体，对应表 auth_audit_log。
 * <p>记录登录、注册、刷新、退出、改密等安全相关事件，不落密码明文。</p>
 */
@Data
@TableName("auth_audit_log")
public class AuthAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String eventType;
    private Boolean success;
    private String ip;
    private String userAgent;
    private String detail;
    private LocalDateTime createdAt;
}
