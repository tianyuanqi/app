CREATE TABLE photo_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tag_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_photo_tag_public_id (tag_id),
    UNIQUE KEY uk_photo_tag_normalized_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE revision_tag (
    revision_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    position INT NOT NULL,
    PRIMARY KEY (revision_id, tag_id),
    UNIQUE KEY uk_revision_tag_position (revision_id, position),
    KEY idx_revision_tag_tag (tag_id, revision_id),
    CONSTRAINT fk_revision_tag_revision FOREIGN KEY (revision_id) REFERENCES photo_revision(id) ON DELETE CASCADE,
    CONSTRAINT fk_revision_tag_tag FOREIGN KEY (tag_id) REFERENCES photo_tag(id),
    CONSTRAINT chk_revision_tag_position CHECK (position BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
