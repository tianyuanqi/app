# 主页模块技术文档（Home + Profile + Moderation）

> 版本：v1  
> 范围：发现流首页、个人主页、作品状态机、最小审核  
> 决策：上传默认 `PENDING`；含 approve / reject / offline

---

## 1. 目标

支撑摄影社区两类「主页」：

1. **发现流首页**：访客浏览已发布作品（分类 / 关键词 / 标签 / 排序）
2. **个人主页**：公开资料 + 该作者已发布作品墙

并补齐可见性前提：作品状态机 + 管理员最小审核。

---

## 2. 作品状态机

```text
上传 ──────────────► PENDING ──approve──► PUBLISHED ──offline──► OFFLINE
                       │                     ▲
                       reject                │
                       ▼                     │
                    REJECTED ──submit────────┘
DRAFT ──submit──► PENDING
```

| 状态 | 含义 | 公开可见 |
|------|------|----------|
| DRAFT | 草稿 | 否 |
| PENDING | 待审（上传默认） | 否 |
| PUBLISHED | 已发布 | 是 |
| REJECTED | 驳回 | 否 |
| OFFLINE | 下架 | 否 |

未发布作品对非作者/非管理员访问详情时统一返回 **404**（不暴露存在性）。

---

## 3. 核心 VO

### PhotoCardVO（列表/Feed/作品墙）
`id, title, imageUrl, thumbUrl, location, status, createTime, likeCount, favoriteCount, author, category`  
`likeCount/favoriteCount` 暂为 0，预留给互动迭代。

### PhotoDetailVO（详情）
在原有 EXIF/标签基础上增加 `status`、`rejectReason`。

### UserProfileVO（公开主页头）
`uid, username, bio, avatarUrl, photoCount, joinedAt`（不含邮箱）。

---

## 4. API 一览

### 发现首页 `/api/v1/home`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/feed` | 已发布作品卡片分页 | 否 |
| GET | `/categories` | 分类入口 | 否 |
| GET | `/hot-tags?limit=20` | 热门标签（按已发布关联数） | 否 |

Feed 参数：`current, pageSize, categoryId, tag, keyword, sort=latest|hot|oldest`  
说明：`hot` 暂与 `latest` 同序，待点赞落地后替换。

### 个人主页 `/api/v1/users`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/{uid}` | 公开主页资料 | 否 |
| GET | `/{uid}/photos` | 该用户已发布作品墙 | 否 |
| PUT | `/me` | 更新资料（含 bio、avatarUrl） | 是 |

### 作品 `/api/v1/photos`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `` `/list` | 公开搜索（仅 PUBLISHED，卡片） | 否 |
| GET | `/mine` | 我的作品（可 `status` 筛选） | 是 |
| GET | `/{id}` | 详情（可见性控制） | 视状态 |
| POST | `` `/upload` | 上传，默认 PENDING | 是 |
| POST | `/{id}/submit` | 草稿/驳回 → PENDING | 是 |
| PUT/DELETE | `/{id}` | 编辑/删除 | 是 |

### 审核 `/api/v1/moderation/photos`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `` | 待审列表 | ADMIN |
| POST | `/{id}/approve` | 通过并发布 | ADMIN |
| POST | `/{id}/reject` | 驳回（可带 reason） | ADMIN |
| POST | `/{id}/offline` | 下架已发布 | ADMIN |

服务层二次校验 `role=ADMIN`，非管理员返回 403。

---

## 5. 数据迁移

| 脚本 | 内容 |
|------|------|
| `V4__photo_status_and_profile.sql` | `photo_info.status` 等；`t_user.bio/avatar_url`；旧作品默认 PUBLISHED |
| `V5__home_seed_photos.sql` | 分类、标签、user1/user2 已发布与待审样例、简介 |

种子账号密码仍为认证模块的 `Passw0rd`。

---

## 6. 包结构

```text
home/          HomeController, HomeService, HomeFeedRequest, HotTagVO
photo/         状态枚举、PhotoCardVO、Assembler、可见性逻辑
moderation/    ModerationController, ModerationService
user/          UserProfileVO、公开主页与作品墙
```

---

## 7. 手工验收

1. `GET /home/feed` 只出现 PUBLISHED（含种子「洱海边的清晨」等）  
2. 「待审样片」不在 feed；user1 的 `/photos/mine?status=PENDING` 可见  
3. admin 登录后 `GET /moderation/photos` 能看到待审；approve 后进入 feed  
4. reject 后作者可见 REJECTED；submit 可再次进入 PENDING  
5. offline 后从 feed 消失  
6. `GET /users/{uid}` 无 email，含 photoCount  
7. `GET /users/{uid}/photos` 仅已发布  
8. 非作者访问他人 PENDING 详情 → 404  

---

## 8. 后续衔接

- 互动模块落地后：`likeCount` 真实化，`hot` 按热度排序  
- 头像上传可复用媒体上传能力  
- 审核可扩展审核日志表与批量操作  
