-- 认证增强：账号状态字段 + 会话表 + 审计表
-- 说明：兼容已有 t_user 数据，新列给出默认值

ALTER TABLE t_user
    ADD COLUMN account_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE/LOCKED/DISABLED' AFTER role,
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数' AFTER account_status,
    ADD COLUMN locked_until DATETIME NULL COMMENT '锁定截止时间' AFTER failed_login_count,
    ADD COLUMN password_changed_at DATETIME NULL COMMENT '最近改密时间' AFTER locked_until,
    ADD COLUMN last_login_at DATETIME NULL COMMENT '最近登录成功时间' AFTER password_changed_at,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER last_login_at,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER created_at;

CREATE TABLE IF NOT EXISTS auth_session (
    id               BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '会话主键',
    user_id          BIGINT       NOT NULL COMMENT '用户内部 ID',
    jti              VARCHAR(64)  NOT NULL COMMENT 'refresh 令牌唯一标识',
    token_hash       VARCHAR(128) NOT NULL COMMENT 'refresh 令牌 SHA-256 哈希',
    expires_at       DATETIME     NOT NULL COMMENT '会话过期时间',
    revoked_at       DATETIME     NULL COMMENT '吊销时间，空表示有效',
    replaced_by_jti  VARCHAR(64)  NULL COMMENT '旋转后的新 jti，用于检测令牌复用',
    ip               VARCHAR(64)  NULL COMMENT '登录 IP',
    user_agent       VARCHAR(512) NULL COMMENT 'User-Agent',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_auth_session_jti (jti),
    KEY idx_auth_session_user_id (user_id),
    KEY idx_auth_session_token_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证会话（refresh 可吊销）';

CREATE TABLE IF NOT EXISTS auth_audit_log (
    id           BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '审计主键',
    user_id      BIGINT       NULL COMMENT '关联用户，可为空',
    event_type   VARCHAR(64)  NOT NULL COMMENT '事件类型',
    success      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否成功：1成功 0失败',
    ip           VARCHAR(64)  NULL COMMENT '客户端 IP',
    user_agent   VARCHAR(512) NULL COMMENT 'User-Agent',
    detail       VARCHAR(512) NULL COMMENT '补充说明（不含敏感明文）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    KEY idx_auth_audit_user_id (user_id),
    KEY idx_auth_audit_event_type (event_type),
    KEY idx_auth_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证审计日志';
