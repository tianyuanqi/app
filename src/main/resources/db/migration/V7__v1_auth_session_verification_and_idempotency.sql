CREATE TABLE email_verification_flow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    flow_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email_key VARCHAR(320) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    email_ciphertext VARBINARY(1024) NOT NULL,
    purpose VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'REGISTER',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    active_generation INT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_verification_flow_id (flow_id),
    KEY idx_verification_email_status (email_key, status, expires_at),
    CONSTRAINT chk_verification_status CHECK (status IN ('ACTIVE','EXHAUSTED','CONSUMED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE email_verification_generation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    flow_id BIGINT NOT NULL,
    generation INT NOT NULL,
    code_hmac CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sent_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_verification_generation (flow_id, generation),
    CONSTRAINT fk_verification_generation_flow FOREIGN KEY (flow_id) REFERENCES email_verification_flow(id) ON DELETE CASCADE,
    CONSTRAINT chk_generation_status CHECK (status IN ('CREATED','SENDING','SENT','FAILED','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mail_delivery_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    generation_id BIGINT NOT NULL,
    adapter_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_no INT NOT NULL,
    result VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    error_category VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mail_attempt_public_id (public_id),
    CONSTRAINT fk_mail_attempt_generation FOREIGN KEY (generation_id) REFERENCES email_verification_generation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    login_at DATETIME(6) NOT NULL,
    absolute_expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revoke_reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_session_public_id (session_id),
    KEY idx_auth_session_account_status (account_id, status, absolute_expires_at),
    CONSTRAINT fk_auth_session_account FOREIGN KEY (account_id) REFERENCES user_account(id),
    CONSTRAINT chk_auth_session_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_refresh_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    token_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    rotation_no INT NOT NULL,
    parent_token_id BIGINT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_public_id (token_id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    UNIQUE KEY uk_refresh_session_rotation (session_id, rotation_no),
    CONSTRAINT fk_refresh_session FOREIGN KEY (session_id) REFERENCES auth_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_parent FOREIGN KEY (parent_token_id) REFERENCES auth_refresh_token(id),
    CONSTRAINT chk_refresh_status CHECK (status IN ('ACTIVE','ROTATED','REUSED','REVOKED','EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE login_security_state (
    account_id BIGINT NOT NULL,
    failed_count INT NOT NULL DEFAULT 0,
    window_started_at DATETIME(6) NULL,
    locked_until DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_login_security_account FOREIGN KEY (account_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rate_limit_bucket (
    bucket_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    window_seconds INT NOT NULL,
    request_count INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (bucket_key, action_type, window_started_at, window_seconds)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NULL,
    session_id BIGINT NULL,
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    success TINYINT(1) NOT NULL,
    subject_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_event_public_id (public_id),
    KEY idx_auth_event_account_time (account_id, occurred_at DESC, id DESC),
    CONSTRAINT fk_auth_event_account FOREIGN KEY (account_id) REFERENCES user_account(id),
    CONSTRAINT fk_auth_event_session FOREIGN KEY (session_id) REFERENCES auth_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scope_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    http_status INT NULL,
    response_body MEDIUMTEXT NULL,
    response_etag VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_scope (scope_key),
    KEY idx_idempotency_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
