ALTER TABLE media_cleanup_job
    DROP CHECK chk_cleanup_status,
    DROP FOREIGN KEY fk_cleanup_media;

ALTER TABLE media_cleanup_job
    MODIFY COLUMN media_id BIGINT NULL,
    MODIFY COLUMN reason VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD COLUMN storage_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL AFTER media_id,
    ADD COLUMN locked_until DATETIME(6) NULL AFTER next_attempt_at,
    ADD COLUMN completed_at DATETIME(6) NULL AFTER last_error_category,
    ADD UNIQUE KEY uk_cleanup_storage_key (storage_key),
    ADD CONSTRAINT fk_cleanup_media FOREIGN KEY (media_id) REFERENCES media_asset(id),
    ADD CONSTRAINT chk_cleanup_target CHECK ((media_id IS NULL) <> (storage_key IS NULL)),
    ADD CONSTRAINT chk_cleanup_status CHECK (status IN ('PENDING','RUNNING','RETRY','DELETED','EXHAUSTED','CANCELLED'));

CREATE TABLE media_consistency_issue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issue_type VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    media_id BIGINT NULL,
    storage_key VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    first_detected_at DATETIME(6) NOT NULL,
    last_detected_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_consistency_fingerprint (fingerprint),
    KEY idx_media_consistency_status (status, issue_type),
    CONSTRAINT fk_media_consistency_asset FOREIGN KEY (media_id) REFERENCES media_asset(id),
    CONSTRAINT chk_media_consistency_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
