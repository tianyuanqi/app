# V1-BE-005 分类与审核媒体闭环证据

## 结论

- 结果：`PASS WITH FINDINGS`
- Backend 判定：`FE-V1-CONTRACT-004`、`FE-V1-RUNTIME-003` 均已达到“可供 Frontend 验证”；最终 `CLOSED` 仍由 Coordinator 回收 Frontend Regression 证据后决定。
- Branch：`codex/v1-backend`
- 起点：`96015a591befe4b99f7df6d945a636344a0fed73`
- 实现 Commit：`3ab2a34`（修复分类 ID 与审核媒体访问）
- 工作流：`WF-2026-08-28-02`

## 基线与边界

- 任务开始前逐字读取 Workspace、项目地图、Backend Repository 规则和 V1-BE-005 handoff。
- handoff 的 6 个 `input_checksums` 全部精确匹配：workflow revision、冻结需求基线、Frontend regression handoff、Frontend status、Frontend evidence、Backend status。
- 起始 Worktree 仅有预存未跟踪目录 `private-media-local/`；本工作包未读取其业务内容、未修改、未暂存、未删除。
- 未修改 Frontend、共享文档、冻结需求、旧数据库或 Remote；未 push、PR、merge、Tag、Fetch；未启动 Tester 正式测试。
- 所有新运行数据和媒体均位于 `/private/tmp`；测试账号与 Fixture 均为合成数据，本文不记录凭证或个人数据。

## 实现证据

### FE-V1-CONTRACT-004

- 根因：分类查询使用 `c.*`，Controller 却读取 `publicId`；Map 实际键为 `public_id`，随后 `String.valueOf(null)` 被序列化为字符串 `"null"`。
- 修复：Mapper 显式投影 `c.public_id AS categoryId`、`name`、`selectable`、`filterable`；Controller 读取 `categoryId` 并使用空值安全转换，不再把 Java `null` 转成字符串。
- 运行时 `GET /api/v1/categories`：11 条、11 个唯一 ID、全部为非空字符串、`"null"` 计数 0、包含 `cat_landscape`，未返回内部主键。
- 真实 HTTP round-trip：创建草稿、作者 Revision 读取、`PUT` 二次保存均为 200；分类保持 `cat_landscape`；提交后的审核 Target 仍为 `cat_landscape`。
- OpenAPI：`CategoryView.categoryId.type=string`。

### FE-V1-RUNTIME-003

- 修复：保留公开媒体与作者访问规则；仅当服务端查询到当前账号角色为 `ADMIN`，且 Web 媒体属于某 Work 当前 `working_revision_id`、该 Revision 状态为 `PENDING` 时，允许管理员读取既有 Web 衍生图。
- 未增加 Operation、原图路径、暂存路径或本机路径；管理员访问无关用户私有媒体仍返回防枚举 404。
- 真实 HTTP 权限矩阵：作者 200、管理员 200、普通他人 404、匿名 404、管理员访问无关私有媒体 404。
- 三个失败响应均为 `RESOURCE_NOT_FOUND`；管理员与作者响应字节一致，均为 `Content-Type: image/jpeg`、`ETag: "media-1"`。
- Moderation Target 返回 `BEARER_FETCH` Web URL；Target 中 `/private/`、`/Users/`、staging、originalPath 泄漏计数为 0。

## 自动化与空库证据

- 定向套件：`BackendP0IntegrationMatrixTest`、`OpenApiContractTest`、`MediaProcessorIntegrationTest`，16/16 通过。
- `./mvnw clean verify`：32 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS`，总耗时 13.622 秒。
- 套件统计：App 1、AuthPolicy 3、CSRF 1、Unicode 4、Idempotency 3、StrongETag 2、OpenAPI 3、Admin security 2、P0 matrix 10、Media processor 3。
- 新增自动化覆盖：11 个分类公开 ID 的序列化与 round-trip；作者/管理员/普通他人/匿名/无关私有媒体矩阵；审核 Target 无路径泄漏。
- 定向 Schema：`px2400_v1_be005_targeted_20260829`，从空库应用 Flyway V1～V14。
- 完整验证 Schema：`px2400_v1_be005_verify_20260829`，从空库应用 Flyway V1～V14；未复用失败后修改的 Schema。
- 测试媒体目录：`/private/tmp/px2400-v1-be005-test-media.QHB8zZ`。

## 提交后运行时证据

- Runtime Schema：`px2400_v1_be005_runtime_20260829`，Flyway 当前版本 14，14 个迁移验证成功。
- Runtime 媒体目录：`/private/tmp/px2400-v1-be005-runtime-media.XL27AV`。
- 合成 Fixture：有效 1200×1200 PNG；上传由 `PROCESSING` 到 `READY`，Web 衍生图为 1200×1200 JPEG。
- `/actuator/health`：`UP`；DB component：`UP`。
- `/v3/api-docs` 临时导出：`/private/tmp/2400px-v1-be005-runtime-openapi.json`。
- OpenAPI SHA-256：`36c01c8470d76ad1a244ae2196c10890066ab838217c8340475bd95b84629a70`；330468 bytes；40 Paths；51 Operations；336 个逐 Operation 错误 Schema；宽泛错误引用计数 0。
- 相对前一工作包 OpenAPI SHA 变化由 `CategoryView.categoryId` 字符串约束及直接相关契约注解产生；未新增 Operation。

## 残余项与未执行项

- 本工作包不关闭其他 `V1-BE-GAP-004`～`009`；其 P1/P2 状态与风险保持不变。
- 两项 Frontend Finding 尚需独立 Frontend Regression，Backend 不自行标记最终 `CLOSED`。
- 启动日志保留既有复合主键 `@TableId` 和 Spring 默认内存用户密码警告；均属既有 P2，未在本包扩围处理。
- 未读取、迁移或清理旧数据库；未执行历史数据处置、生产配置修改或外部资源操作。
