CREATE TABLE photo_work (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    author_account_id BIGINT NOT NULL,
    publication_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    public_revision_id BIGINT NULL,
    working_revision_id BIGINT NULL,
    published_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_photo_work_public_id (work_id),
    KEY idx_work_author_updated (author_account_id, updated_at DESC, id DESC),
    KEY idx_work_public_feed (publication_state, published_at DESC, id DESC),
    CONSTRAINT fk_work_author FOREIGN KEY (author_account_id) REFERENCES user_account(id),
    CONSTRAINT chk_publication_state CHECK (publication_state IN ('NEVER_PUBLISHED','PUBLISHED','OFFLINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE photo_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    work_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    origin VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title VARCHAR(512) NULL,
    description TEXT NULL,
    location VARCHAR(512) NULL,
    category_id BIGINT NULL,
    submitted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_revision_public_id (revision_id),
    UNIQUE KEY uk_revision_work_number (work_id, revision_number),
    KEY idx_revision_queue (state, submitted_at, id),
    CONSTRAINT fk_revision_work FOREIGN KEY (work_id) REFERENCES photo_work(id) ON DELETE CASCADE,
    CONSTRAINT fk_revision_category FOREIGN KEY (category_id) REFERENCES photo_category(id),
    CONSTRAINT chk_revision_state CHECK (state IN ('DRAFT','PENDING','REJECTED','PUBLISHED','SUPERSEDED')),
    CONSTRAINT chk_revision_origin CHECK (origin IN ('NEW','EDIT_PUBLISHED','REJECTED_REWORK','OFFLINE_REWORK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE photo_work
    ADD CONSTRAINT fk_work_public_revision FOREIGN KEY (public_revision_id) REFERENCES photo_revision(id),
    ADD CONSTRAINT fk_work_working_revision FOREIGN KEY (working_revision_id) REFERENCES photo_revision(id);

CREATE TABLE moderation_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    work_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL,
    action VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    resulting_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    submitter_account_id BIGINT NOT NULL,
    reviewer_account_id BIGINT NULL,
    reason VARCHAR(1000) NULL,
    self_review TINYINT(1) NOT NULL DEFAULT 0,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_moderation_event_public_id (event_id),
    KEY idx_moderation_work_time (work_id, occurred_at DESC, id DESC),
    CONSTRAINT fk_moderation_work FOREIGN KEY (work_id) REFERENCES photo_work(id) ON DELETE CASCADE,
    CONSTRAINT fk_moderation_revision FOREIGN KEY (revision_id) REFERENCES photo_revision(id) ON DELETE CASCADE,
    CONSTRAINT fk_moderation_submitter FOREIGN KEY (submitter_account_id) REFERENCES user_account(id),
    CONSTRAINT fk_moderation_reviewer FOREIGN KEY (reviewer_account_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE photo_tombstone (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    author_uid VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    deleted_by_uid VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state_before_delete VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    deleted_at DATETIME(6) NOT NULL,
    last_moderation_action VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_reviewer_uid VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_reviewed_at DATETIME(6) NULL,
    reason VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_photo_tombstone_work_id (work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
