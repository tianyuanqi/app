# V1-BE-006 逐图 EXIF 与拍摄时间闭环证据

## 结论

- 结果：`PASS WITH FINDINGS`
- Finding：`V1-BE-GAP-004` 已达到 `ready_for_frontend_verification`；按交接约束，Backend 不自行标记 `CLOSED`。
- Branch：`codex/v1-backend`
- 起始 Commit：`ded449af402da942747d021097f69fa21ed9f34b`
- 实现 Commit：`d8ad64ed9774dfc48060fd3789b3df526fe0f836`
- 冻结需求与交接输入均只读；未修改 Frontend、共享文档、旧数据库或 Remote。

## 基线和边界

- `workflow_revision`：`WF-2026-08-28-02`。
- 交接列出的 8 个 `input_checksums` 在写入前逐项匹配。
- 起始 Worktree 仅有预存未跟踪目录 `private-media-local/`；全过程未读取、修改、暂存或删除该目录。
- 现有 40 个 Path、51 个 Operation 足以表达逐图候选 EXIF、警告和手工参数，没有新增 Path 或 Operation。
- 仅追加 `V15__v1_media_exif_candidate.sql`；既有 `V1～V14` 未修改。

## 实现证据

### 逐图 EXIF

- 每个原始媒体独立解析拍摄时间、机身、镜头、焦距、光圈、快门和 ISO，并将候选值保存在对应 `media_asset`。
- 缺失 EXIF 不产生阻断；损坏或不可解析 EXIF 只产生稳定警告，媒体仍按图像处理结果进入 `READY` 或既有媒体失败状态。
- 晚于服务端当前 `Asia/Shanghai` 时间的自动 EXIF 拍摄时间不进入有效参数；候选的其他字段保留，并返回 `EXIF_CAPTURE_TIME_IN_FUTURE`。
- 候选信息通过既有媒体状态 Operation 的 `exifCandidate` 和 `warnings` 返回；警告为类型化 DTO，不使用自由文本判断。

### 手工覆盖与清空

- 草稿更新沿用 `PUT /api/v1/photos/{workId}/draft`，在既有请求中增加可选 `mediaParameters[]`；每项以 `mediaId` 绑定单张媒体。
- 出现的媒体参数项对该图执行全量替换；字段为 `null` 表示清空。未出现的当前媒体保持原值，新加入媒体使用自身候选 EXIF。
- 修改一张媒体不会借用或改变其他媒体的参数。
- 机身和镜头最多 100 个 grapheme；焦距、光圈、快门和 ISO 最多 50 个 grapheme。输入执行 NFC、单行和控制字符/双向控制字符校验。
- 手工参数是作者声明值，不标记为官方验证。

### 时间规则

- 拍摄时间请求和响应使用 `OffsetDateTime`；保存前统一换算至 `Asia/Shanghai`，有效非空响应固定为 ISO-8601 `+08:00`。
- 比较使用注入 `Clock`，不依赖 JVM 或服务器默认时区。
- 晚于当前时刻的手工值返回 HTTP 400 / `VALIDATION_FAILED`；等于当前、过去和空值允许保存。
- 固定 Clock 测试同时将服务器默认时区设为非 `Asia/Shanghai`，覆盖过去、当前、未来、跨日和序列化。

### 投影一致性

- 作者 Revision、审核 Target 和公开详情均按有序媒体返回各自 `parameters`。
- 空字段以 JSON `null` 表达，不输出字符串 `"null"`；三种投影没有跨媒体污染。

## 依赖审计

- EXIF 解析复用已存在的 `com.drewnoakes:metadata-extractor:2.18.0`，没有修改 `pom.xml` 或升级依赖。
- 本地依赖 POM 声明许可证为 Apache License 2.0；其既有传递依赖为 `com.adobe.xmp:xmpcore:6.1.11`。
- 该库仅用于元数据解析，未引入新的图像处理框架。已知残余风险是极端或厂商私有 EXIF 的兼容范围；解析失败由稳定警告降级，不阻断媒体处理。

## 自动化验证

### 定向测试

- 全新 Schema：`px2400_v1_be006_targeted_final_20260829`。
- Flyway：空库一次性应用 `V1～V15`。
- 结果：22 个测试通过，0 failures，0 errors，0 skipped。
- 覆盖完整 EXIF、无 EXIF、损坏 EXIF、未来 EXIF、逐图隔离、覆盖、清空、未来手工拒绝、长度/控制字符和 `+08:00`。
- 早期定向运行使用另一全新 Schema；19 个业务断言已通过，但一个新增 OpenAPI 测试预期了错误的生成 Schema 名。修正测试断言后弃用该 Schema，并以新的 `targeted_final` 空库重新执行；没有在失败 Schema 上修改或复用数据库结构。

### clean verify

- 命令：`./mvnw clean verify`
- 全新 Schema：`px2400_v1_be006_verify_20260829`。
- Flyway：空库一次性应用 `V1～V15`。
- 结果：38 tests run，0 failures，0 errors，0 skipped，`BUILD SUCCESS`。
- 套件：`AppApplicationTests` 1、`BackendP0IntegrationMatrixTest` 10、`CsrfTokenServiceTest` 1、`AuthPolicyTest` 3、`AdminContractSecurityTest` 2、`OpenApiContractTest` 3、`IdempotencyServiceTest` 3、`StrongEtagTest` 2、`UnicodeTextTest` 4、`ExifTimeRulesIntegrationTest` 3、`MediaProcessorIntegrationTest` 6。

## 真实 HTTP 与运行时证据

- 运行 Schema：`px2400_v1_be006_runtime_20260829`，从空库一次性应用 `V1～V15`。
- 媒体目录：新建于 `/private/tmp`，与 Workspace 和预存 `private-media-local/` 隔离。
- 健康检查：`UP`；数据库组件：`UP`。
- 使用确定性生成的三张 1200×1200 JPEG：A、B 含不同历史 EXIF，Future 含 2099 年 EXIF；三张均通过真实 multipart 上传从 `PROCESSING` 到 `READY`。
- A 返回 `2020-01-02T03:04:05+08:00`、Camera A/Lens A/ISO 100；B 返回 `2021-02-03T04:05:06+08:00`、Camera B/Lens B/ISO 400，证明逐图隔离。
- Future 的有效 `captureTime` 为 null，机身/镜头等其他候选仍可读，警告为 `EXIF_CAPTURE_TIME_IN_FUTURE`；上传、草稿保存和提交审核均成功。
- 真实 HTTP 将 A 覆盖为 `2022-03-04T05:06:07+08:00` 后，B 与 Future 不变；随后清空 A，B 与 Future 仍不变。
- 真实 HTTP 保存 2099 年手工时间返回 HTTP 400 / `VALIDATION_FAILED`。
- 提交后管理员审核 Target 返回三张各自参数；批准后公开详情返回相同逐图参数。
- 验证仅使用合成账号和合成媒体；本证据不记录邮箱、密码、Token、Cookie 或其他个人数据。验收结束时吊销全部合成 Session。

## 运行时 OpenAPI

- 临时导出：`/private/tmp/2400px-v1-be006-runtime-openapi.json`（不提交）。
- SHA-256：`7461088e0e9831d443bb3f599d2909d187ce2895d8feddbf62caacc720036348`
- 字节数：331847。
- Path：40；Operation：51；逐 Operation Error Schema：336。
- 草稿请求含 `mediaParameters`；媒体状态含类型化 `exifCandidate`/`warnings`；作者 Revision、审核 Target、公开详情均引用逐图参数 DTO。
- 草稿更新 Operation 的 HTTP 400 受限错误集合保持 `INVALID_IF_MATCH`、`VALIDATION_FAILED`；既有 ETag、幂等和媒体权限契约未放宽。

## 数据库和安全

- 所有测试和运行均使用新的隔离 Schema；未读取、迁移或清理旧数据库。
- 原图、衍生图和 Fixture 仅位于新的 `/private/tmp` 目录，未提交运行时媒体或 OpenAPI 文件。
- 私有媒体权限和防枚举由既有 `BackendP0IntegrationMatrixTest` 回归通过。
- 数据库与文件系统仍不能形成单一事务；本工作包没有扩大 `V1-BE-GAP-006`，既有孤儿对账和跨存储补偿风险保持 P1。

## 剩余 Finding 和未执行项

- `V1-BE-GAP-004`：`ready_for_frontend_verification`；需 Coordinator 派发 Frontend Regression 并回收证据后决定是否 `CLOSED`。
- `V1-BE-GAP-005～009`：本工作包禁止处理，状态不变。
- 未启动 Tester 正式测试；未执行 push、PR、merge、Tag、Fetch 或 Remote 修改。
- 启动日志仍有复合主键无 `TableId` 和未使用 Spring 默认内存用户密码警告，分别保留在 `V1-BE-GAP-008/009`。
