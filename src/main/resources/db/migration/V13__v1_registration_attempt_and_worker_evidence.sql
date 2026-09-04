CREATE TABLE registration_attempt (
    attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    flow_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (attempt_id),
    UNIQUE KEY uk_registration_attempt_flow (flow_id),
    CONSTRAINT fk_registration_attempt_flow FOREIGN KEY (flow_id) REFERENCES email_verification_flow(id),
    CONSTRAINT fk_registration_attempt_account FOREIGN KEY (account_id) REFERENCES user_account(id),
    CONSTRAINT chk_registration_attempt_status CHECK (status IN ('COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE media_processing_attempt
    ADD COLUMN trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER failure_category;
