-- 主页与审核：作品状态、用户主页字段
-- 旧作品默认置为 PUBLISHED，保证已有公开内容仍可展示

ALTER TABLE photo_info
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '作品状态：DRAFT/PENDING/PUBLISHED/REJECTED/OFFLINE' AFTER category_id,
    ADD COLUMN reject_reason VARCHAR(512) NULL COMMENT '驳回原因' AFTER status,
    ADD COLUMN reviewed_at DATETIME NULL COMMENT '最近审核时间' AFTER reject_reason,
    ADD COLUMN reviewed_by BIGINT NULL COMMENT '审核人内部用户 ID' AFTER reviewed_at,
    ADD KEY idx_photo_info_status (status),
    ADD KEY idx_photo_info_status_create_time (status, create_time);

ALTER TABLE t_user
    ADD COLUMN bio VARCHAR(500) NULL COMMENT '个人简介' AFTER email,
    ADD COLUMN avatar_url VARCHAR(512) NULL COMMENT '头像 URL' AFTER bio;
