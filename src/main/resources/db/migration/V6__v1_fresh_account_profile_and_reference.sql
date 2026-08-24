-- 2400px v1.0 fresh-only baseline.
-- IMPORTANT: this migration is authorized only for a dedicated empty v1 Schema after V1-V5.
-- It must never be pointed at the retained legacy database. V1-V5 are immutable; their sample
-- rows are removed here so the final production baseline contains reference data only.

DROP TABLE IF EXISTS t_photo_tag;
DROP TABLE IF EXISTS photo_info;
DROP TABLE IF EXISTS photo_tag;
DROP TABLE IF EXISTS photo_category;
DROP TABLE IF EXISTS auth_audit_log;
DROP TABLE IF EXISTS auth_session;
DROP TABLE IF EXISTS t_user;

CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uid VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email VARCHAR(320) NOT NULL,
    email_key VARCHAR(320) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER',
    governance_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    locked_until DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_uid (uid),
    UNIQUE KEY uk_user_account_email_key (email_key),
    CONSTRAINT chk_user_account_role CHECK (role IN ('USER','ADMIN')),
    CONSTRAINT chk_user_account_governance CHECK (governance_status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_profile (
    account_id BIGINT NOT NULL,
    username VARCHAR(128) NOT NULL,
    bio VARCHAR(1000) NULL,
    birth_date DATE NULL,
    gender VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    avatar_media_id BIGINT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id),
    CONSTRAINT fk_user_profile_account FOREIGN KEY (account_id) REFERENCES user_account(id),
    CONSTRAINT chk_user_profile_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER','UNDISCLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_governance_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_account_id BIGINT NOT NULL,
    actor_account_id BIGINT NOT NULL,
    action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    previous_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resulting_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_governance_public_id (public_id),
    KEY idx_governance_target_time (target_account_id, occurred_at DESC, id DESC),
    CONSTRAINT fk_governance_target FOREIGN KEY (target_account_id) REFERENCES user_account(id),
    CONSTRAINT fk_governance_actor FOREIGN KEY (actor_account_id) REFERENCES user_account(id),
    CONSTRAINT chk_governance_action CHECK (action IN ('DISABLE','ENABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE photo_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SYSTEM',
    PRIMARY KEY (id),
    UNIQUE KEY uk_photo_category_public_id (public_id),
    UNIQUE KEY uk_photo_category_normalized_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO photo_category (public_id, name, normalized_name, active, sort_order, source) VALUES
('cat_landscape', '风光', '风光', 1, 10, 'SYSTEM'),
('cat_portrait', '人像', '人像', 1, 20, 'SYSTEM'),
('cat_street', '街拍', '街拍', 1, 30, 'SYSTEM'),
('cat_documentary', '纪实', '纪实', 1, 40, 'SYSTEM'),
('cat_architecture', '建筑', '建筑', 1, 50, 'SYSTEM'),
('cat_ecology', '生态', '生态', 1, 60, 'SYSTEM'),
('cat_still_life', '静物', '静物', 1, 70, 'SYSTEM'),
('cat_macro', '微距', '微距', 1, 80, 'SYSTEM'),
('cat_astronomy', '天文', '天文', 1, 90, 'SYSTEM'),
('cat_sports', '体育', '体育', 1, 100, 'SYSTEM'),
('cat_other', '其他', '其他', 1, 110, 'SYSTEM');
