-- 本地/联调种子账号（密码均为 Passw0rd）
-- BCrypt: $2a$10$7O2g4WpzGpgMmzwh6FIryO7FYXn3qAlaT3IaKBjHfywyzyX8HcGOe

INSERT INTO t_user (uid, username, password, gender, email, role, account_status, failed_login_count, password_changed_at, created_at, updated_at)
SELECT 'seedadmin00000001', 'admin', '$2a$10$7O2g4WpzGpgMmzwh6FIryO7FYXn3qAlaT3IaKBjHfywyzyX8HcGOe', 0, 'admin@example.com', 'ADMIN', 'ACTIVE', 0, NOW(), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'admin');

INSERT INTO t_user (uid, username, password, gender, email, role, account_status, failed_login_count, password_changed_at, created_at, updated_at)
SELECT 'seeduser100000001', 'user1', '$2a$10$7O2g4WpzGpgMmzwh6FIryO7FYXn3qAlaT3IaKBjHfywyzyX8HcGOe', 0, 'user1@example.com', 'USER', 'ACTIVE', 0, NOW(), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'user1');

INSERT INTO t_user (uid, username, password, gender, email, role, account_status, failed_login_count, password_changed_at, created_at, updated_at)
SELECT 'seeduser200000002', 'user2', '$2a$10$7O2g4WpzGpgMmzwh6FIryO7FYXn3qAlaT3IaKBjHfywyzyX8HcGOe', 0, 'user2@example.com', 'USER', 'ACTIVE', 0, NOW(), NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'user2');
