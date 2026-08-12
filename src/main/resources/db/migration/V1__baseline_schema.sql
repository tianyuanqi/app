-- 基线：版本化现有摄影社区核心表结构（不改变既有业务语义）
-- 表：t_user / photo_info / photo_category / photo_tag / t_photo_tag

CREATE TABLE IF NOT EXISTS t_user (
    id       BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '内部主键',
    uid      VARCHAR(32)  NOT NULL COMMENT '对外业务 UID',
    username VARCHAR(64)  NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
    birth    DATE         NULL COMMENT '生日',
    gender   INT          NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女',
    email    VARCHAR(128) NULL COMMENT '邮箱',
    role     VARCHAR(32)  NULL COMMENT '角色标识',
    UNIQUE KEY uk_t_user_uid (uid),
    UNIQUE KEY uk_t_user_username (username),
    UNIQUE KEY uk_t_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS photo_category (
    id        BIGINT      NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '分类主键',
    name      VARCHAR(64) NOT NULL COMMENT '分类名称',
    sortorder INT         NOT NULL DEFAULT 0 COMMENT '排序权重'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='照片分类';

CREATE TABLE IF NOT EXISTS photo_info (
    id            BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '作品主键',
    user_id       BIGINT       NULL COMMENT '上传者内部用户 ID',
    location      VARCHAR(100) NULL COMMENT '拍摄位置',
    title         VARCHAR(100) NULL COMMENT '标题',
    description   VARCHAR(2000) NULL COMMENT '描述',
    image_url     VARCHAR(512) NULL COMMENT '图片访问路径',
    camera_body   VARCHAR(128) NULL COMMENT '机身',
    lens          VARCHAR(128) NULL COMMENT '镜头',
    focal_length  VARCHAR(64)  NULL COMMENT '焦距',
    aperture      VARCHAR(64)  NULL COMMENT '光圈',
    shutter_speed VARCHAR(64)  NULL COMMENT '快门',
    iso           INT          NULL COMMENT '感光度',
    create_time   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    shoot_date    DATETIME     NULL COMMENT '拍摄时间',
    category_id   INT          NULL COMMENT '分类 ID',
    KEY idx_photo_info_user_id (user_id),
    KEY idx_photo_info_category_id (category_id),
    KEY idx_photo_info_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品信息';

CREATE TABLE IF NOT EXISTS photo_tag (
    id   BIGINT      NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '标签主键',
    name VARCHAR(64) NOT NULL COMMENT '标签名',
    UNIQUE KEY uk_photo_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='照片标签';

CREATE TABLE IF NOT EXISTS t_photo_tag (
    photo_id BIGINT NOT NULL COMMENT '作品 ID',
    tag_id   BIGINT NOT NULL COMMENT '标签 ID',
    PRIMARY KEY (photo_id, tag_id),
    KEY idx_t_photo_tag_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品与标签关联';
