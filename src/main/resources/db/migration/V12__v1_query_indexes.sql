CREATE INDEX idx_profile_username ON user_profile(username);
CREATE INDEX idx_revision_category_state ON photo_revision(category_id, state, work_id);
CREATE INDEX idx_revision_title ON photo_revision(title(191));
CREATE INDEX idx_revision_location ON photo_revision(location(191));
CREATE INDEX idx_tag_display_name ON photo_tag(display_name);
