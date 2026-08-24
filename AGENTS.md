# 摄影社区后端维护约定

## 维护边界

- 本仓库是 `app` 后端工程。只修改本仓库，不直接修改 `photo-frontend`。
- `2400px-BE` 是独立 Codex 项目。每个正式任务开始前显式读取 `/Users/yuanqi/2400px/AGENTS.md`、`/Users/yuanqi/2400px/PROJECT_MAP.md` 和本文件；不能假设父级 Workspace 规则会从本 Repository 自动加载。
- 后端映射、DTO/VO、校验与 OpenAPI 注解共同生成运行中的 `/v3/api-docs`；该机器可读文档是接口契约的权威输出，README、`docs/` 和 `handoff/` 必须向它对齐。
- 前端字段或路径调用错误时，先核对 Controller、DTO、Security 配置和 `/v3/api-docs`，不要为了兼容错误调用增加重复字段或模糊别名。
- 未经明确需求，不新增点赞、评论、收藏等业务，不顺手扩大任务范围。

## 项目结构

- `auth/`：登录、注册、JWT、refresh 会话、认证审计与 Security。
- `user/`：当前用户资料、公开主页、公开作品墙。
- `photo/`：作品上传、检索、详情、编辑、状态与分类/标签。
- `home/`：发现流、首页分类、热门标签。
- `moderation/`：管理员审核、驳回与下架。
- `interaction/`：互动域占位，当前无接口。
- `common/`：统一响应、异常、上下文及基础配置。
- `src/main/resources/db/migration/`：Flyway 迁移，当前基线至 V5。
- `handoff/backend_status.yaml`：前后端交接状态，接口变更后必须同步。
- `docs/frontend-api-contract.md`：面向前端的当前稳定契约说明。

## 接口与鉴权

- API 前缀统一为 `/api/v1`，响应统一为 `{code, message, data}`。
- 鉴权使用 `Authorization: Bearer <accessToken>`；对外用户标识优先使用 `uid`，不要让新调用依赖内部 `userId`。
- 新增或修改接口时，同时检查 Controller 映射、DTO 校验、服务层权限、`SecurityConfig` 和 OpenAPI 鉴权标注。
- 公开接口不得因 OpenAPI 全局 security 配置被误标为需登录；需要鉴权的 operation 必须显式声明 Bearer security。
- 所有者、管理员、未发布作品可见性必须在服务层二次校验，不能只依赖 URL 权限。
- 列表接口页码从 1 开始，单页上限 100；新增列表需保持一致。

## 数据库与文件

- Flyway 迁移只允许追加，不修改已经执行过的迁移；结构或种子修正使用新的版本脚本。
- 新迁移要同时考虑空库和已有数据，不使用会覆盖既有业务数据的固定主键种子方案。
- 表结构、实体字段和 MyBatis-Plus 映射必须一致；复合主键表避免调用依赖单一 `@TableId` 的 ById 方法。
- 上传目录通过 `APP_UPLOAD_DIR` 配置；不要提交本机密码、JWT 密钥、上传文件或本地 secrets。
- 文件系统写入和数据库写入无法形成同一事务，改上传/删除流程时必须明确补偿策略。

## 修改流程

1. 先执行 `git status --short --branch`，保留所有已有修改，不改写用户提交历史。
2. 阅读相关 Controller、DTO、Service、Security 和迁移，再判断责任归属。
3. 用最小改动实现需求；接口契约发生变化时同步更新 OpenAPI 注解、`docs/frontend-api-contract.md` 与 `handoff/backend_status.yaml`。
4. 在 `handoff/backend_status.yaml` 明确记录：已实现接口、方法、鉴权、破坏性变更、已知问题和是否可联调。
5. 至少执行 `./mvnw -DskipTests clean package`；若任务允许测试，再执行与改动范围相称的测试。
6. 能启动时检查 MySQL、Flyway、`/actuator/health`、`/v3/api-docs` 和受影响接口；涉及会话的手工验收结束后注销创建的 refresh 会话。
7. 最后再次检查 Git diff/status，只向前端通知已验证且在交接文件中标为可联调的能力。

## 当前接管基线（2026-08-12）

- Java 17、Spring Boot 3.5.16、MySQL 8.0、Flyway schema version 5。
- 编译可通过，服务可启动，健康检查为 UP。
- refresh 旋转/吊销、登录失败锁定、种子静态资源和分类种子存在已知缺陷；详见 `handoff/backend_status.yaml`。
- 修复这些 P0/P1 缺陷前，不得把后端整体标记为 `ready_for_testing` 或通知前端进行完整认证联调。

## 技术与运行环境

- Java 17（当前本机 17.0.18）、Spring Boot 3.5.16、Maven Wrapper（当前 Maven 3.9.14）。
- Spring MVC、Bean Validation、Spring Security、JJWT 0.12.6。
- MyBatis-Plus 3.5.12、MySQL 8.0、Flyway；不是 Spring Data JPA。
- springdoc OpenAPI 2.8.9、Actuator、SLF4J/Logback（Spring Boot 默认日志体系）。
- 公共配置在 `application.yml`，环境差异在 `application-{local,dev,prod}.yml`；密码、JWT 密钥和上传路径优先由环境变量或已忽略的本地 secrets 文件提供。

常用命令：

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw clean verify
./mvnw -DskipTests clean package
```

`./mvnw test` / `clean verify` 需要可用的测试配置与 MySQL，因为现有 `@SpringBootTest` 会加载应用上下文。`-DskipTests` 只证明编译和打包，不等于测试通过。

## 分层规则

- Controller：负责 HTTP 映射、请求校验、鉴权元数据和响应装配；不得承载核心业务或绕过统一 `Result` 响应与全局异常体系。
- Service：业务规则、事务、所有权/角色/可见性二次校验放在服务层。安全状态更新必须考虑事务回滚语义；文件与数据库组合操作必须明确补偿。
- Mapper / Entity：使用 MyBatis-Plus；Mapper 保持持久化职责，实体与表字段一致。查询性能变更需检查 N+1、分页和索引影响。
- DTO / VO：请求使用 DTO 并声明 Validation/OpenAPI 约束；响应使用 VO，不向客户端泄漏内部实体或不必要的 `userId`。
- Exception：预期业务失败使用 `BusinessException`/`ErrorCode`，由 `GlobalExceptionHandler` 映射；不得吞异常或把可预期的 4xx 变成 500。
- Logging：使用 SLF4J，记录可行动的上下文；不得记录密码、完整 Token、JWT secret、数据库密码或敏感个人信息。

## 测试与验证要求

- 业务逻辑变更补充 JUnit 单元测试；Security、序列化、持久化、事务或迁移行为优先补充 Spring 集成/切片测试。
- `src/test/java` 中还混有 Python/pytest API 脚本。部分脚本使用旧 `/api/...` 路径，迁移到未来 `qa/` 前不得把它们默认视为可靠回归套件，也不得在本轮删除或搬迁。
- API 变更至少核对 `/v3/api-docs`、Swagger UI、鉴权标注、统一响应、错误码、分页和兼容性；涉及数据库时验证 Flyway 空库/已有库路径。
- 默认完成门槛是受影响测试 + `./mvnw clean verify`。若环境不允许，执行可行的最强验证，明确报告未执行项及风险；不得用跳过测试的 package 冒充完整验证。
