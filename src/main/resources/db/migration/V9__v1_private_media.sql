CREATE TABLE media_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    media_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_account_id BIGINT NOT NULL,
    purpose VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    original_storage_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    web_storage_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    mime_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    byte_size BIGINT NULL,
    width INT NULL,
    height INT NULL,
    frame_count INT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failure_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    retryable TINYINT(1) NOT NULL DEFAULT 0,
    retry_until DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_asset_public_id (media_id),
    KEY idx_media_owner_status (owner_account_id, status, created_at),
    CONSTRAINT fk_media_owner FOREIGN KEY (owner_account_id) REFERENCES user_account(id),
    CONSTRAINT chk_media_purpose CHECK (purpose IN ('PHOTO','AVATAR')),
    CONSTRAINT chk_media_status CHECK (status IN ('PROCESSING','READY','FAILED','DELETE_PENDING','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE user_profile
    ADD CONSTRAINT fk_user_profile_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_asset(id);

CREATE TABLE revision_media (
    revision_id BIGINT NOT NULL,
    media_id BIGINT NOT NULL,
    position INT NOT NULL,
    capture_time DATETIME(6) NULL,
    camera_body VARCHAR(512) NULL,
    lens VARCHAR(512) NULL,
    focal_length VARCHAR(256) NULL,
    aperture VARCHAR(256) NULL,
    shutter_speed VARCHAR(256) NULL,
    iso_value VARCHAR(256) NULL,
    parameter_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NONE',
    warning_codes VARCHAR(512) NULL,
    PRIMARY KEY (revision_id, media_id),
    UNIQUE KEY uk_revision_media_position (revision_id, position),
    CONSTRAINT fk_revision_media_revision FOREIGN KEY (revision_id) REFERENCES photo_revision(id) ON DELETE CASCADE,
    CONSTRAINT fk_revision_media_asset FOREIGN KEY (media_id) REFERENCES media_asset(id),
    CONSTRAINT chk_revision_media_position CHECK (position BETWEEN 1 AND 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE media_cleanup_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    media_id BIGINT NOT NULL,
    reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    deadline_at DATETIME(6) NOT NULL,
    last_error_category VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cleanup_media (media_id),
    KEY idx_cleanup_due (status, next_attempt_at),
    CONSTRAINT fk_cleanup_media FOREIGN KEY (media_id) REFERENCES media_asset(id),
    CONSTRAINT chk_cleanup_status CHECK (status IN ('PENDING','RETRY','DELETED','EXHAUSTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
