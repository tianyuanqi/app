-- 主页种子：分类 + 已发布/待审作品（图片路径为占位，便于接口联调）

INSERT INTO photo_category (id, name, sortorder)
SELECT 1, '风光', 1 WHERE NOT EXISTS (SELECT 1 FROM photo_category WHERE id = 1);
INSERT INTO photo_category (id, name, sortorder)
SELECT 2, '人像', 2 WHERE NOT EXISTS (SELECT 1 FROM photo_category WHERE id = 2);
INSERT INTO photo_category (id, name, sortorder)
SELECT 3, '街拍', 3 WHERE NOT EXISTS (SELECT 1 FROM photo_category WHERE id = 3);

INSERT INTO photo_tag (name)
SELECT '旅行' WHERE NOT EXISTS (SELECT 1 FROM photo_tag WHERE name = '旅行');
INSERT INTO photo_tag (name)
SELECT '人文' WHERE NOT EXISTS (SELECT 1 FROM photo_tag WHERE name = '人文');
INSERT INTO photo_tag (name)
SELECT '夜景' WHERE NOT EXISTS (SELECT 1 FROM photo_tag WHERE name = '夜景');

-- user1 已发布作品
INSERT INTO photo_info (user_id, location, title, description, image_url, camera_body, lens, focal_length, aperture, shutter_speed, iso, create_time, category_id, status)
SELECT u.id, '云南·大理', '洱海边的清晨', '种子作品：已发布风光', '/uploads/seed_user1_1.jpg', 'Nikon Z8', '24-70', '35mm', 'f/2.8', '1/200', 100, NOW(), 1, 'PUBLISHED'
FROM t_user u WHERE u.username = 'user1'
  AND NOT EXISTS (SELECT 1 FROM photo_info p WHERE p.title = '洱海边的清晨' AND p.user_id = u.id);

INSERT INTO photo_info (user_id, location, title, description, image_url, camera_body, lens, focal_length, aperture, shutter_speed, iso, create_time, category_id, status)
SELECT u.id, '上海·外滩', '雨夜霓虹', '种子作品：已发布街拍', '/uploads/seed_user1_2.jpg', 'Sony A7M4', '35mm', '35mm', 'f/1.8', '1/60', 800, NOW(), 3, 'PUBLISHED'
FROM t_user u WHERE u.username = 'user1'
  AND NOT EXISTS (SELECT 1 FROM photo_info p WHERE p.title = '雨夜霓虹' AND p.user_id = u.id);

-- user1 待审作品（不应出现在公开首页）
INSERT INTO photo_info (user_id, location, title, description, image_url, camera_body, lens, focal_length, aperture, shutter_speed, iso, create_time, category_id, status)
SELECT u.id, '北京·胡同', '待审样片', '种子作品：待审核', '/uploads/seed_user1_pending.jpg', 'Fuji X100V', '23mm', '23mm', 'f/2.0', '1/125', 200, NOW(), 3, 'PENDING'
FROM t_user u WHERE u.username = 'user1'
  AND NOT EXISTS (SELECT 1 FROM photo_info p WHERE p.title = '待审样片' AND p.user_id = u.id);

-- user2 已发布作品
INSERT INTO photo_info (user_id, location, title, description, image_url, camera_body, lens, focal_length, aperture, shutter_speed, iso, create_time, category_id, status)
SELECT u.id, '成都·宽窄巷', '午后光影', '种子作品：人像', '/uploads/seed_user2_1.jpg', 'Canon R5', '85mm', '85mm', 'f/1.8', '1/400', 100, NOW(), 2, 'PUBLISHED'
FROM t_user u WHERE u.username = 'user2'
  AND NOT EXISTS (SELECT 1 FROM photo_info p WHERE p.title = '午后光影' AND p.user_id = u.id);

INSERT INTO photo_info (user_id, location, title, description, image_url, camera_body, lens, focal_length, aperture, shutter_speed, iso, create_time, category_id, status)
SELECT u.id, '杭州·西湖', '断桥残雪', '种子作品：风光', '/uploads/seed_user2_2.jpg', 'Nikon Z6', '24-120', '50mm', 'f/8', '1/250', 200, NOW(), 1, 'PUBLISHED'
FROM t_user u WHERE u.username = 'user2'
  AND NOT EXISTS (SELECT 1 FROM photo_info p WHERE p.title = '断桥残雪' AND p.user_id = u.id);

-- 给已发布种子作品挂上标签（若尚未关联）
INSERT INTO t_photo_tag (photo_id, tag_id)
SELECT p.id, t.id
FROM photo_info p
JOIN t_user u ON u.id = p.user_id
JOIN photo_tag t ON t.name = '旅行'
WHERE p.title IN ('洱海边的清晨', '断桥残雪')
  AND NOT EXISTS (
      SELECT 1 FROM t_photo_tag r WHERE r.photo_id = p.id AND r.tag_id = t.id
  );

INSERT INTO t_photo_tag (photo_id, tag_id)
SELECT p.id, t.id
FROM photo_info p
JOIN photo_tag t ON t.name = '夜景'
WHERE p.title = '雨夜霓虹'
  AND NOT EXISTS (
      SELECT 1 FROM t_photo_tag r WHERE r.photo_id = p.id AND r.tag_id = t.id
  );

UPDATE t_user SET bio = '摄影爱好者，喜欢记录旅途光影', avatar_url = '/uploads/avatar_user1.png'
WHERE username = 'user1' AND (bio IS NULL OR bio = '');

UPDATE t_user SET bio = '人像与街拍练习生', avatar_url = '/uploads/avatar_user2.png'
WHERE username = 'user2' AND (bio IS NULL OR bio = '');
