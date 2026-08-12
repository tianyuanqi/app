# 摄影社区服务端

模块化单体 Spring Boot 服务，提供用户认证、作品上传管理与分类查询能力。

## 技术栈

- Java 17 / Spring Boot 3.5.x
- Spring Security + JWT
- MyBatis-Plus / MySQL
- Flyway
- springdoc OpenAPI
- Actuator

## 模块包

| 包 | 职责 |
|----|------|
| `auth` | 登录、注册、令牌、Security |
| `user` | 用户资料与公开主页 |
| `photo` | 作品、分类、标签、上传 |
| `interaction` | 互动域占位（暂无接口） |
| `moderation` | 审核域占位（暂无接口） |
| `common` | 统一响应、异常、配置 |

## 快速启动

1. 复制环境变量示例并填写本地值：

```bash
cp .env.example .env
# 编辑 DB_PASSWORD、JWT_SECRET、APP_UPLOAD_DIR 等
```

2. 准备 MySQL 库（默认库名 `blog`），首次启动 Flyway 会执行 `V1__baseline_schema.sql`（已有表则 `IF NOT EXISTS` / baseline 安全接入）。

3. 启动（默认 `local` profile）：

```bash
export $(grep -v '^#' .env | xargs)   # 可选
./mvnw spring-boot:run
# 或
mvn spring-boot:run
```

## 环境变量

| 变量 | 说明 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | `local` / `dev` / `prod` |
| `DB_URL` | JDBC 连接串 |
| `DB_USERNAME` | 数据库用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `JWT_SECRET` | JWT HMAC 密钥（建议 ≥32 字符） |
| `APP_UPLOAD_DIR` | 图片物理存储目录 |

配置文件：

- `application.yml`：公共项
- `application-local.yml` / `application-dev.yml` / `application-prod.yml`

## 接口前缀

统一前缀：`/api/v1`（方案 A）

| 能力 | 方法 | 路径 | 鉴权 |
|------|------|------|------|
| 登录（邮箱或用户名） | POST | `/api/v1/auth/login` | 否 |
| 邮箱注册 | POST | `/api/v1/auth/register` | 否 |
| 刷新令牌（旋转） | POST | `/api/v1/auth/token/refresh` | 否 |
| 退出（吊销 refresh） | POST | `/api/v1/auth/logout` | 是 |
| 当前资料 | GET/PUT | `/api/v1/users/me` | 是 |
| 改密（吊销全部会话） | PUT | `/api/v1/users/me/password` | 是 |
| 公开主页 | GET | `/api/v1/users/{uid}` | 否 |
| 用户作品墙 | GET | `/api/v1/users/{uid}/photos` | 否 |
| 发现流 | GET | `/api/v1/home/feed` | 否 |
| 首页分类 | GET | `/api/v1/home/categories` | 否 |
| 热门标签 | GET | `/api/v1/home/hot-tags` | 否 |
| 作品搜索 | GET | `/api/v1/photos`、`/list` | 否 |
| 我的作品 | GET | `/api/v1/photos/mine`、`/my-list` | 是 |
| 作品详情 | GET | `/api/v1/photos/{id}` | 视状态 |
| 提交审核 | POST | `/api/v1/photos/{id}/submit` | 是 |
| 编辑/删除 | PUT/DELETE | `/api/v1/photos/{id}` | 是 |
| 上传（默认 PENDING） | POST | `/api/v1/photos`、`/upload` | 是 |
| 待审列表 | GET | `/api/v1/moderation/photos` | ADMIN |
| 审核通过 | POST | `/api/v1/moderation/photos/{id}/approve` | ADMIN |
| 驳回 | POST | `/api/v1/moderation/photos/{id}/reject` | ADMIN |
| 下架 | POST | `/api/v1/moderation/photos/{id}/offline` | ADMIN |
| 分类列表 | GET | `/api/v1/categories`、`/list` | 否 |

鉴权 Header：`Authorization: Bearer <accessToken>`

### 认证要点

- 注册：**邮箱必填**；用户名字母开头；密码 ≥8 位且含字母+数字
- 登录字段：`account`（邮箱或用户名）+ `password`
- 失败锁定：连续 **5** 次错误锁定 **15** 分钟
- 令牌：access 默认 30 分钟；refresh 14 天且落库可吊销；刷新会旋转
- 对外身份以 **`uid` 为主**；`userId` 暂留兼容
- 种子账号：`admin` / `user1` / `user2`，密码均为 `Passw0rd`
- 完整说明见：[docs/auth-module.md](docs/auth-module.md)

### 主页要点

- 发现流：`GET /api/v1/home/feed`（仅已发布卡片）
- 个人主页：`GET /api/v1/users/{uid}` + `/{uid}/photos`
- 上传默认 **PENDING**；管理员审核：`/api/v1/moderation/photos/**`
- 完整说明见：[docs/home-module.md](docs/home-module.md)

上传表单字段（兼容旧客户端）：`file`、`title`、`description`、`location`、`category`、`tag`

## 文档与健康检查

- 认证模块：[docs/auth-module.md](docs/auth-module.md)
- 主页模块：[docs/home-module.md](docs/home-module.md)
- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs
- Health：http://localhost:8080/actuator/health
- 静态图片：http://localhost:8080/uploads/**

## 响应约定

统一 JSON：

```json
{ "code": 200, "message": "操作成功", "data": {} }
```

HTTP 状态码与业务码对齐（含认证细粒度码如 40001/40103/40302/42901 等，详见认证文档）。
