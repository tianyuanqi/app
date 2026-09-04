CREATE TABLE photo_like (
    work_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (work_id, account_id),
    KEY idx_like_account (account_id, work_id),
    CONSTRAINT fk_like_work FOREIGN KEY (work_id) REFERENCES photo_work(id) ON DELETE CASCADE,
    CONSTRAINT fk_like_account FOREIGN KEY (account_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE photo_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    comment_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    work_id BIGINT NOT NULL,
    author_account_id BIGINT NOT NULL,
    root_comment_id BIGINT NULL,
    content VARCHAR(4096) NULL,
    display_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_public_id (comment_id),
    KEY idx_comment_root_page (work_id, root_comment_id, created_at, id),
    CONSTRAINT fk_comment_work FOREIGN KEY (work_id) REFERENCES photo_work(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_account_id) REFERENCES user_account(id),
    CONSTRAINT fk_comment_root FOREIGN KEY (root_comment_id) REFERENCES photo_comment(id) ON DELETE CASCADE,
    CONSTRAINT chk_comment_state CHECK (display_state IN ('ACTIVE','DELETED_PLACEHOLDER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comment_moderation_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    comment_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_account_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_moderation_event_id (event_id),
    KEY idx_comment_moderation_comment (comment_id, occurred_at DESC),
    CONSTRAINT fk_comment_moderation_actor FOREIGN KEY (actor_account_id) REFERENCES user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
