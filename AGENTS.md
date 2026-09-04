# 摄影社区后端维护约定

## 维护边界

- 本仓库是 `app` 后端工程。只修改本仓库，不直接修改 `photo-frontend`。
- `2400px-BE` 是独立 Codex 项目。新 Agent、新任务上下文、首次进入本 Repository 或无法确认规则基线时，显式读取 `/Users/yuanqi/2400px/AGENTS.md`、`/Users/yuanqi/2400px/PROJECT_MAP.md` 和本文件；不能假设父级 Workspace 规则会自动加载。同一任务上下文确认 `workflow_revision`、Git Commit 或文件指纹未变化时复用稳定规则，只加载本工作包增量；规则或作用域变化时重新读取必要原文。
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
2. 定向阅读受影响的 Controller、DTO、Service、Security、迁移和调用链，再判断责任归属；小范围任务不默认扫描整个 Repository。
3. 用最小改动实现需求；接口契约发生变化时同步更新 OpenAPI 注解、`docs/frontend-api-contract.md` 与 `handoff/backend_status.yaml`。
4. 在 `handoff/backend_status.yaml` 明确记录：已实现接口、方法、鉴权、破坏性变更、已知问题和是否可联调。
5. 至少执行 `./mvnw -DskipTests clean package`；若任务允许测试，再执行与改动范围相称的测试。
6. 能启动时检查 MySQL、Flyway、`/actuator/health`、`/v3/api-docs` 和受影响接口；涉及会话的手工验收结束后注销创建的 refresh 会话。
7. 最后检查 Git diff/status，并进行与风险相称的需求、权限、边界和跨模块 Review；只向前端通知已验证且在交接文件中标为可联调的能力。

自动化通过后不再用模型重新模拟编译、类型或测试结论；只 Review 本次 diff 和自动化无法证明的业务风险。失败时先分析失败日志、stack trace、本次 diff 和直接相关代码，只有证据不足或影响扩散时才扩大读取范围。

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

## Java 源码注释规范

### 目标与执行要求

源码注释用于提高代码的可理解性、可维护性和设计意图可追溯性，补充代码本身无法清楚表达的信息，重点说明设计原因、业务约束、边界条件和非显而易见的行为。

- Agent 新增或修改 Java 代码时，必须同时检查是否需要增加或更新注释，并在本次修改中完成必要的注释维护。
- 涉及非显而易见的权限、可见性、事务回滚、并发控制、幂等、兼容或失败补偿规则时，必须在合适位置说明相关约束及已确认的原因；已有准确说明时不重复添加。
- 不采用“所有类、所有方法、所有代码块必须有注释”的机械规则；是否增加注释应根据维护价值判断。

### 基本原则

1. 优先通过清晰命名、合理的方法拆分和代码结构表达含义；代码本身不足以说明意图时才增加注释。
2. 注释重点说明“为什么这样实现”，不得为了增加数量而逐行翻译代码。
3. 注释必须与当前实现一致，不得把尚未实现、计划实现或根据惯例推测的行为描述为当前能力。
4. 无法确认设计原因时，仅记录可验证的行为或限制；必要时标明“原因待确认”，不得把推测写成事实。
5. 不使用大量注释掩盖复杂代码；实现明显难以理解时优先考虑合理重构，但不得为了增加注释而扩大当前任务范围。

### 注释形式与职责分工

- 类、接口和方法的职责及调用契约说明使用 Javadoc（`/** ... */`）；方法内部的实现原因使用 `//` 或局部块注释。
- `@param`、`@return`、`@throws` 按实际说明需要使用，不生成空白或仅重复名称的标签。
- 存在接口时，共用调用契约优先写在接口方法上；实现类仅补充实现特有的限制和设计原因，不复制整段接口注释。
- Controller 与 Service 的注释分别说明 HTTP 接口语义和业务规则，避免重复描述。

### 类级注释

Controller、Service、Mapper、Adapter、Port / Interface、重要领域对象、公共基础设施类，以及职责或使用方式不能从类名直接判断的类，应优先提供简洁的类级说明。

类级注释说明核心职责，并按需要补充所属架构层或模块、重要边界或约束、主要协作对象；不重复罗列所有字段和方法。职责明显、结构简单的数据类不强制添加冗余类注释。

### 方法注释

以下方法应优先增加方法级注释：

- 对外暴露的重要接口、核心业务方法和公共可复用方法；
- 存在非显而易见业务规则、事务边界、特殊异常语义或重要副作用的方法；
- 参数或返回值含义无法通过命名准确判断的方法。

方法注释按需要说明方法目的、关键参数和返回值语义、重要前置条件、业务约束、副作用，以及调用方需要理解的异常行为。

简单 getter/setter、普通转换方法以及含义已完全由签名表达的方法，不强制增加方法注释。

### 行内与代码块注释

行内或代码块注释只解释非显而易见的逻辑，例如特殊业务规则、边界或查询条件、历史数据兼容、性能或并发考虑、看似多余但不能删除的代码、特殊异常处理、难以理解的转换或过滤逻辑，以及有明确原因的实现取舍。

禁止添加仅重复代码的注释，例如：

```java
// 判断用户是否为空
if (user == null) {
```

```java
// 返回结果
return result;
```

```java
// 设置用户名
user.setUsername(username);
```

### 框架注解与 OpenAPI

- Spring Boot、Spring MVC、MyBatis-Plus、Lombok、Jackson 等框架注解属于代码的一部分。常规且含义明确的注解，如 `@RestController`、`@Service`、`@Mapper`、`@Data`、`@Getter`、`@Setter`、`@TableName`、`@GetMapping`、`@PostMapping`，不要求逐个解释。
- 仅当注解的使用方式、参数配置或组合产生非显而易见的重要行为时，才增加说明。
- 仅补充源码注释时，不得顺带修改框架注解的行为配置；任务要求更新接口文档时，可以按契约维护规则修改 OpenAPI 描述。
- 面向 API 使用者的字段含义、约束、示例和鉴权信息，应维护在对应 DTO/VO、校验与 OpenAPI 注解中，并与运行时 `/v3/api-docs` 一致。源码注释补充内部设计原因，不替代接口契约元数据，也不机械复制其内容。

### DTO、VO、Entity 与字段

- 类名或字段名能够准确表达含义时，不强制添加重复注释。
- 业务含义特殊、单位不明确、取值范围有限、状态含义特殊或容易产生歧义的字段，应增加说明。
- 时间、金额、状态码、枚举值、外部系统标识等容易误解的字段，应优先说明其业务语义。
- 字段说明遵循上述 OpenAPI 分工，避免在源码注释和接口文档中维护重复或冲突的描述。

### TODO / FIXME

- TODO、FIXME 只能描述真实存在且尚未完成的问题，尽可能说明当前限制或问题及后续需要处理的内容。
- 不得使用没有上下文的 `// TODO` 或 `// FIXME`。
- 已完成的事项应删除对应 TODO/FIXME，必要时改为当前行为或设计原因的说明；不得继续作为未完成事项保留。

### 注释语言

本项目后端源码注释统一使用简洁、准确的中文。Java、Spring、数据库和架构领域中的标准技术术语可以保留英文，如 Controller、Service、DTO、Entity、Transaction、Session；避免为了中文化而创造不常用的术语。

### 修改范围与同步维护

- 修改现有实现时同步检查相邻注释，在当前修改范围内修正或删除已失效、与实际代码不一致或具有误导性的注释。
- 除非任务明确要求进行注释专项治理，否则不得仅因为其他文件缺少注释而扩大任务范围。

## 测试与验证要求

- 业务逻辑变更补充 JUnit 单元测试；Security、序列化、持久化、事务或迁移行为优先补充 Spring 集成/切片测试。
- `src/test/java` 中还混有 Python/pytest API 脚本。部分脚本使用旧 `/api/...` 路径，迁移到未来 `qa/` 前不得把它们默认视为可靠回归套件，也不得在本轮删除或搬迁。
- API 变更至少核对 `/v3/api-docs`、Swagger UI、鉴权标注、统一响应、错误码、分页和兼容性；涉及数据库时验证 Flyway 空库/已有库路径。
- 默认完成门槛是受影响测试 + `./mvnw clean verify`。若环境不允许，执行可行的最强验证，明确报告未执行项及风险；不得用跳过测试的 package 冒充完整验证。
