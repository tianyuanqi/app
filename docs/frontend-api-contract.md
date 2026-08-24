# 前端稳定接口契约（接管基线）

> 基线日期：2026-08-12  
> API 版本：v1  
> 唯一来源：后端真实实现和运行中的 `GET /v3/api-docs`  
> 本文是便于前端接入的人工索引；若与运行中的 OpenAPI 不一致，以后端实现和 `/v3/api-docs` 为准。

## 1. 通用约定

- 本地 Base URL：`http://localhost:8080`
- API 前缀：`/api/v1`
- Swagger UI：`/swagger-ui.html`
- OpenAPI JSON：`/v3/api-docs`
- 健康检查：`/actuator/health`
- 鉴权 Header：`Authorization: Bearer <accessToken>`
- 日期时间：ISO-8601 字符串，例如 `2026-08-12T13:00:00`
- 分页从 1 开始，默认 `current=1&pageSize=10`，服务端最多返回 100 条/页。
- 成功与失败均使用统一包装：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

- HTTP 状态和业务错误大类对齐。认证细分业务码包括 `40001`、`40002`、`40003`、`40101`、`40102`、`40103`、`40301`、`40302`、`40901`、`40902`、`42901`。
- 对外用户主标识使用 `uid`。`userId` 是内部数据库 ID，仅为现存兼容字段，新前端不得依赖。
- 图片字段目前返回相对路径 `/uploads/**`，前端需使用 Base URL 拼接。

分页 `data` 结构：

```json
{
  "records": [],
  "total": 0,
  "size": 10,
  "current": 1,
  "pages": 0
}
```

## 2. 当前联调结论

- 可联调：公开首页、作品公开检索/详情、分类、公开主页/作品墙、access 登录鉴权、当前用户资料、我的作品、管理员待审列表及权限边界。
- 暂不可联调：refresh 旋转、refresh 吊销语义、登录失败锁定完整链路、种子作品/头像静态资源。
- 上传、编辑、删除、提交审核、审核状态写操作已经实现，但本轮接管未执行破坏性人工验收；联调前建议先修复 P0/P1 问题并准备独立验收数据。

## 3. 认证接口

### `POST /api/v1/auth/login`（公开）

JSON：`account`（邮箱或用户名，必填）、`password`（必填）。

成功 `data`：

```json
{
  "token": "与 accessToken 相同的兼容字段",
  "accessToken": "JWT",
  "refreshToken": "JWT",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "uid": "seeduser100000001",
  "userId": 1,
  "username": "user1",
  "role": "USER",
  "accountStatus": "ACTIVE"
}
```

### `POST /api/v1/auth/register`（公开）

JSON：`username`、`password`、`email` 均必填。用户名规则为 `^[a-zA-Z][a-zA-Z0-9_]{2,31}$`；密码 8–72 位且同时包含字母与数字。成功响应同登录。

### `POST /api/v1/auth/token/refresh`（公开，当前阻塞）

JSON：`refreshToken`。设计契约是旋转并返回新的登录响应，但当前实现中新签发 refresh 无法匹配数据库会话，实测返回 HTTP 401 / `code=40102`。前端暂勿依赖自动刷新。

### `POST /api/v1/auth/logout`（Bearer，当前语义不可靠）

JSON：`refreshToken`。设计契约为吊销该 refresh 会话。当前“数据库找不到会话”也返回成功，结合 refresh `jti` 缺陷不能保证真实吊销。

## 4. 用户接口

| 方法 | 路径 | 鉴权 | 请求/查询 | 响应 `data` |
|---|---|---|---|---|
| GET | `/api/v1/users/me` | Bearer | 无 | `UserVO` |
| PUT | `/api/v1/users/me` | Bearer | JSON `email?, birth?, gender?, bio?, avatarUrl?` | `UserVO` |
| PUT | `/api/v1/users/me/password` | Bearer | JSON `oldPassword, newPassword` | `null` |
| GET | `/api/v1/users/{uid}` | 公开 | 路径 `uid` | `UserProfileVO` |
| GET | `/api/v1/users/{uid}/photos` | 公开 | `current?, pageSize?` | `Page<PhotoCardVO>` |

`UserVO`：`uid, username, birth, gender, email, bio, avatarUrl`。

`UserProfileVO`：`uid, username, bio, avatarUrl, photoCount, joinedAt`，不含邮箱。

## 5. 首页与公开发现

| 方法 | 路径 | 鉴权 | 查询 | 响应 `data` |
|---|---|---|---|---|
| GET | `/api/v1/home/feed` | 公开 | `current?, pageSize?, categoryId?, tag?, keyword?, sort?` | `Page<PhotoCardVO>` |
| GET | `/api/v1/home/categories` | 公开 | 无 | `CategoryVO[]` |
| GET | `/api/v1/home/hot-tags` | 公开 | `limit?`，默认 20，范围收敛为 1–50 | `HotTagVO[]` |

Feed 只返回 `PUBLISHED`。`sort` 支持 `latest`、`hot`、`oldest`；互动尚未实现，`hot` 当前等同 `latest`。

`HotTagVO`：`id, name, photoCount`。

## 6. 作品接口

### 公开查询

`GET /api/v1/photos`，兼容别名 `GET /api/v1/photos/list`。

查询参数：

- 分页：`current?`, `pageSize?`
- 文本/作者：`keyword?`, `authorId?`, `author?`
- 分类/标签：`categoryId?`, `tagId?`, `tag?`
- 拍摄条件：`location?`, `cameraBody?`, `isoMin?`, `isoMax?`, `shootFrom?`, `shootTo?`
- 排序：`sort?=latest|hot|oldest`

仅返回 `PUBLISHED`，响应为 `Page<PhotoCardVO>`。

### 我的作品

`GET /api/v1/photos/mine`（Bearer），兼容别名 `GET /api/v1/photos/my-list`。

查询：`current?`, `pageSize?`, `status?`。状态支持 `DRAFT|PENDING|PUBLISHED|REJECTED|OFFLINE`。

### 详情与写操作

| 方法 | 路径 | 鉴权 | 请求 | 响应 `data` |
|---|---|---|---|---|
| GET | `/api/v1/photos/{id}` | 公开；未发布仅作者/管理员可见 | 无 | `PhotoDetailVO` |
| PUT | `/api/v1/photos/{id}` | Bearer + 作者 | JSON `title?, description?, location?, categoryId?, tags?` | `PhotoDetailVO` |
| DELETE | `/api/v1/photos/{id}` | Bearer + 作者 | 无 | `null` |
| POST | `/api/v1/photos/{id}/submit` | Bearer + 作者 | 无 | `PhotoDetailVO` |
| POST | `/api/v1/photos` | Bearer | `multipart/form-data` | `PhotoDetailVO` |
| POST | `/api/v1/photos/upload` | Bearer | 同上，兼容别名 | `PhotoDetailVO` |

上传 multipart 字段：`file`、`title`、`category` 必填；`description`、`location`、重复 `tag` 可选。上传后状态固定为 `PENDING`。

`PhotoCardVO`：

```text
id, title, imageUrl, thumbUrl, location, status, createTime,
likeCount, favoriteCount,
author{uid, username, avatarUrl},
category{id, name}
```

`likeCount`、`favoriteCount` 当前固定为 0。当前部分历史/种子作品的 `category` 可能为 `null`。

`PhotoDetailVO`：

```text
id, title, description, imageUrl, location, createTime, shootDate,
cameraBody, lens, focalLength, aperture, shutterSpeed, iso,
status, rejectReason,
author{uid, username}, category{id, name}, tags[{id, name}]
```

未发布作品对无权访问者统一返回 404，不暴露资源存在性。

## 7. 分类接口

- `GET /api/v1/categories`（公开）
- `GET /api/v1/categories/list`（公开兼容别名）

响应 `CategoryVO[]`，字段为 `id, name, sortOrder`。

## 8. 管理员审核接口

以下接口均需 Bearer，且服务层要求用户 `role=ADMIN`：

| 方法 | 路径 | 请求 | 响应 `data` |
|---|---|---|---|
| GET | `/api/v1/moderation/photos` | `current?, pageSize?` | `Page<PhotoCardVO>` |
| POST | `/api/v1/moderation/photos/{id}/approve` | 无 | `PhotoDetailVO` |
| POST | `/api/v1/moderation/photos/{id}/reject` | 可选 JSON `{ "reason": "..." }` | `PhotoDetailVO` |
| POST | `/api/v1/moderation/photos/{id}/offline` | 无 | `PhotoDetailVO` |

状态机：上传 `PENDING`；通过后 `PUBLISHED`；驳回后 `REJECTED`；作者可从 `DRAFT/REJECTED` 提交为 `PENDING`；管理员可将 `PUBLISHED` 下架为 `OFFLINE`，并可将 `OFFLINE` 再次通过为 `PUBLISHED`。

## 9. 前端当前不得自行兼容的字段

- 登录请求只接受 `account`，不要改发 `username` 或 `email` 字段。
- 上传分类字段是 `category`；编辑分类字段是 `categoryId`，两者是不同 DTO 的真实契约。
- 上传标签字段是重复 multipart `tag`；编辑标签字段是 JSON 数组 `tags`。
- 用户路由参数使用 `uid`；不要用内部 `userId` 替代。
- access 使用 `accessToken`；`token` 仅是现存兼容字段，不应成为新代码首选。
