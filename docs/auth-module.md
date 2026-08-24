# 认证模块技术文档（Auth）

> 版本：v1（邮箱注册渠道）  
> 包路径：`com.yuanqi.app.auth` + 关联 `com.yuanqi.app.user`  
> 更新说明：将 Demo 级无状态双 JWT，升级为可吊销会话 + 登录风控 + 审计的公司常见账号形态。

> 接管验收提示（2026-08-12）：本文第 5 节描述的是目标设计。当前 refresh JWT 的 `jti` 与 `auth_session.jti` 不一致，导致新 refresh 立即返回 `40102`；logout 可能返回成功但未吊销会话；失败锁定与 refresh 复用处置存在事务回滚风险。修复和复验前，前端只可使用 accessToken 已验证范围，详见 `handoff/backend_status.yaml`。

---

## 1. 模块目标

本模块负责摄影社区服务端的**身份认证与会话管理**，对标主流互联网后台的账号能力（精简版）：

- 邮箱注册（手机号注册预留后续拓展）
- 邮箱或用户名登录
- 短效 access + 可旋转、可吊销的 refresh 会话
- 连续失败锁定、账号禁用
- 退出 / 改密真正失效旧令牌
- 认证审计日志，便于排障与后续接口测试断言

**明确不做（本期）：** 短信/邮件验证码实发、OAuth、MFA、Redis 会话、忘记密码邮件流。

---

## 2. 包结构与职责

```text
com.yuanqi.app.auth
├── controller/AuthController          # HTTP 入口
├── dto/AuthRequests                   # 登录/注册/刷新/退出请求
├── vo/LoginVO                         # 统一令牌响应
├── config/AuthProperties              # 锁定、限流、令牌时长
├── entity/AuthSession                 # refresh 会话
├── entity/AuthAuditLog                # 认证审计
├── mapper/…                           # MyBatis Mapper
├── service/AuthService                # 注册登录编排
├── service/AuthSessionService         # 会话签发/旋转/吊销
├── service/AuthAuditService           # 审计写入
├── service/AuthRateLimiter            # 内存 IP 限流
├── service/JwtService                 # JWT 签发解析
├── support/AuthPolicy                 # 用户名/密码策略
├── support/ClientInfo                 # IP/UA 提取
└── security/
    ├── SecurityConfig                 # 路径权限
    └── JwtAuthenticationFilter        # Bearer 解析 + 账号状态二次校验

com.yuanqi.app.user
├── entity/User                        # 含 accountStatus 等认证字段
├── enums/AccountStatus
└── service/...ChangePassword          # 改密后吊销全部会话
```

---

## 3. 数据模型

### 3.1 `t_user` 认证相关字段（Flyway V2）

| 字段 | 说明 |
|------|------|
| `email` | 注册必填，唯一，小写存储 |
| `role` | `USER` / `ADMIN` |
| `account_status` | `ACTIVE` / `LOCKED` / `DISABLED` |
| `failed_login_count` | 连续登录失败次数 |
| `locked_until` | 锁定截止时间 |
| `password_changed_at` | 最近改密时间 |
| `last_login_at` | 最近登录成功时间 |
| `created_at` / `updated_at` | 审计时间 |

### 3.2 `auth_session`

每个有效 refresh 对应一行：

| 字段 | 说明 |
|------|------|
| `jti` | refresh JWT 唯一 ID |
| `token_hash` | refresh 原文 SHA-256（不存明文） |
| `expires_at` | 过期时间 |
| `revoked_at` | 吊销时间；空表示有效 |
| `replaced_by_jti` | 旋转后的新 jti，用于检测复用 |
| `ip` / `user_agent` | 登录环境 |

### 3.3 `auth_audit_log`

记录：`REGISTER_*`、`LOGIN_*`、`REFRESH_*`、`LOGOUT_SUCCESS`、`PASSWORD_CHANGED` 等。  
**禁止**写入密码明文。

### 3.4 种子账号（Flyway V3）

| 用户名 | 邮箱 | 角色 | 密码 |
|--------|------|------|------|
| `admin` | admin@example.com | ADMIN | `Passw0rd` |
| `user1` | user1@example.com | USER | `Passw0rd` |
| `user2` | user2@example.com | USER | `Passw0rd` |

---

## 4. 账号状态机

```text
注册成功 ──────────────────► ACTIVE
                                │
                 连续失败 ≥ 5 次 │
                                ▼
                             LOCKED ──(locked_until 到期或登录成功清零)──► ACTIVE
                                │
管理员禁用 ───────────────────► DISABLED（不可登录/刷新；已有 access 也会被 Filter 拒绝）
```

配置默认：`max-failed-login=5`，`lock-minutes=15`。

---

## 5. 令牌与会话语义

| 类型 | 默认有效期 | 存储 | 用途 |
|------|------------|------|------|
| access | 30 分钟 | 仅客户端 | 调业务接口 |
| refresh | 14 天 | 客户端 + `auth_session` | 换票 |

### 5.1 签发（登录/注册）

1. 创建 `jti`  
2. 签发 refresh（claims 含 `userId/uid/tokenType=refresh/jti`）  
3. 写入 `auth_session`（hash、过期、IP、UA）  
4. 签发 access（claims 含 `userId/uid/username/role/tokenType=access`）

### 5.2 刷新（旋转）

1. 解析 refresh → 查 session  
2. 若 session 已吊销且存在 `replaced_by_jti` → **判定复用**：吊销该用户全部会话，返回 `40103`  
3. 否则吊销旧 session，写入 `replaced_by_jti`，签发新对令牌

### 5.3 退出

`POST /api/v1/auth/logout`（需 access）+ body `{ "refreshToken": "..." }`  
吊销对应 session；已吊销视为幂等成功。

### 5.4 改密

`PUT /api/v1/users/me/password` 成功后：更新密码哈希 + **吊销该用户全部会话**。

---

## 6. API 契约

统一前缀：`/api/v1/auth`  
统一响应：`{ "code", "message", "data" }`，HTTP 状态与业务码对齐。

### 6.1 注册 `POST /api/v1/auth/register`

请求：

```json
{
  "username": "photographer01",
  "password": "Passw0rd",
  "email": "user@example.com"
}
```

规则：

- 用户名：`^[a-zA-Z][a-zA-Z0-9_]{2,31}$`
- 密码：至少 8 位，需同时含字母与数字
- 邮箱：必填、格式校验、唯一（当前唯一注册渠道）

成功：`200` + `LoginVO`

### 6.2 登录 `POST /api/v1/auth/login`

请求：

```json
{
  "account": "user1@example.com",
  "password": "Passw0rd"
}
```

`account` 含 `@` 按邮箱查，否则按用户名查。  
失败对外统一：`40001 用户名或密码错误`（防枚举）；审计区分原因。

### 6.3 刷新 `POST /api/v1/auth/token/refresh`

```json
{ "refreshToken": "..." }
```

### 6.4 退出 `POST /api/v1/auth/logout`

Header：`Authorization: Bearer <accessToken>`

```json
{ "refreshToken": "..." }
```

### 6.5 LoginVO 字段

| 字段 | 说明 |
|------|------|
| `accessToken` | 访问令牌 |
| `refreshToken` | 刷新令牌 |
| `token` | 兼容字段，等于 accessToken（建议废弃） |
| `tokenType` | 固定 `Bearer` |
| `expiresIn` | access 有效秒数 |
| `uid` | **对外主标识（推荐客户端使用）** |
| `userId` | 内部主键，**暂留兼容，不建议新客户端依赖** |
| `username` / `role` / `accountStatus` | 基础身份 |

鉴权 Header：`Authorization: Bearer <accessToken>`

---

## 7. 错误码一览（认证相关）

| code | HTTP | 含义 |
|------|------|------|
| 40001 | 400 | 用户名或密码错误 |
| 40002 | 400 | 密码强度不符 |
| 40003 | 400 | 用户名格式不符 |
| 40101 | 401 | access 无效/过期 |
| 40102 | 401 | refresh 无效/已吊销 |
| 40103 | 401 | refresh 复用（已吊销全部会话） |
| 40301 | 403 | 账号禁用 |
| 40302 | 403 | 账号锁定 |
| 40901 | 409 | 用户名已存在 |
| 40902 | 409 | 邮箱已注册 |
| 42901 | 429 | 登录/注册过于频繁 |

---

## 8. 安全设计要点

1. **密码**：BCrypt 存储；策略与注册/改密共用 `AuthPolicy`  
2. **防枚举**：登录失败对外文案统一  
3. **会话可吊销**：refresh 落库哈希；logout/改密/复用检测均可失效  
4. **角色真实生效**：Filter 从用户表读取 role，写入 `ROLE_USER` / `ROLE_ADMIN`  
5. **状态二次校验**：即使 access 未过期，DISABLED/LOCKED 也会被 Filter 拒绝  
6. **限流**：单机内存按 IP 限制登录/注册频率（多实例需改 Redis，本期不做）  
7. **密钥外置**：`JWT_SECRET` 环境变量  

---

## 9. 配置项

```yaml
app:
  auth:
    max-failed-login: 5
    lock-minutes: 15
    login-rate-limit-per-minute: 20
    register-rate-limit-per-minute: 10
    access-expire-ms: 1800000      # 30 分钟
    refresh-expire-ms: 1209600000  # 14 天
  jwt:
    secret: ${JWT_SECRET}
```

迁移脚本：

- `V2__auth_account_and_session.sql`
- `V3__auth_seed_users.sql`

---

## 10. 关键调用链

### 登录

```text
AuthController.login
  → AuthRateLimiter.checkLogin
  → AuthService.findByAccount / 密码校验 / 锁定逻辑
  → AuthSessionService.issueTokenPair
  → AuthAuditService.record(LOGIN_*)
```

### 业务请求鉴权

```text
JwtAuthenticationFilter
  → JwtService.parseAccessClaims
  → UserMapper 查用户状态
  → UserContext + SecurityContext(ROLE_*)
  → Controller
```

---

## 11. 手工验收清单

1. 邮箱注册成功，返回含 `uid/accessToken/refreshToken/expiresIn`  
2. 弱密码、非法用户名、重复邮箱分别返回对应错误码  
3. 错误密码 5 次后锁定，返回 `40302`；15 分钟内无法登录  
4. `user1@example.com` / `Passw0rd` 可登录，access 可调 `/api/v1/users/me`  
5. refresh 成功拿到新对令牌；旧 refresh 再刷返回 `40103`  
6. logout 后 refresh 失败（`40102`）  
7. 改密后旧 access/refresh 均不可用  
8. 种子 `admin` 登录后 role 为 `ADMIN`

---

## 12. 后续可拓展（不在本期）

- 手机号注册 / 登录  
- 忘记密码（固定验证码桩或真邮件）  
- `GET /api/v1/auth/sessions` 多端会话列表与踢下线  
- Redis 分布式限流与会话  
- 邮箱验证状态 `email_verified`

---

## 13. 相关文件索引

| 类型 | 路径 |
|------|------|
| 控制器 | `auth/controller/AuthController.java` |
| 编排服务 | `auth/service/AuthService.java` |
| 会话 | `auth/service/AuthSessionService.java` |
| JWT | `auth/service/JwtService.java` |
| 安全过滤 | `auth/security/JwtAuthenticationFilter.java` |
| 错误码 | `common/api/ErrorCode.java` |
| 迁移 | `db/migration/V2__*.sql`、`V3__*.sql` |
| 用户改密 | `user/service/impl/UserServiceImpl.java` |
