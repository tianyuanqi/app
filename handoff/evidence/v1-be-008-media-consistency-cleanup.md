# V1-BE-008 媒体一致性、补偿与清理证据

## 结论

- 工作包结果：`PASS`；`V1-BE-GAP-006` 已达到 `ready_for_coordinator_verification`，Backend 未自行标记 `CLOSED`。
- Backend 整体结果：`PASS WITH FINDINGS`；本工作包禁止处理的 `V1-BE-GAP-005/007/008/009` 状态不变。
- Branch：`codex/v1-backend`。
- 起始 Commit：`14ee6dcad407364c8ed616efa5b7e0eb9fd94922`。
- 实现 Commit：`2502a0b3cf2f4b5a05c4bc89e2d67daf5dc0a079`。
- workflow_revision：`WF-2026-08-28-02`。

## 基线与边界

- 写入前完整读取交接指定的 Workspace/Repository 规则与 V1-BE-008 handoff。
- `workflow_revision`、七项 `input_checksums`、Branch、HEAD 和 Worktree 全部匹配固定输入后才开始写入。
- 起始及结束前的非任务状态均只有预存未跟踪目录 `private-media-local/`；该目录未读取、修改、暂存或删除。
- 未读取、迁移或清理旧数据库；所有验证使用 `/private/tmp` 中全新初始化的独立 MySQL 8.0 数据目录、独立端口、全新 Schema 和独立媒体目录。
- 未修改 Frontend、共享文档、冻结需求、Remote；未执行 Tester 正式测试、push、PR、merge、Tag 或 Fetch。

## 实现闭环

### StoragePort 与补偿

- 新增 `StoragePort`，把暂存、移动、读、写、删除、存在性和对象枚举纳入可替换边界；本地私有目录仍由 `MediaStorage` 实现。
- 上传的 DB 关联改为显式 `TransactionTemplate`；相同 `owner + clientUploadId` 并发竞争只保留一个 Asset，失败暂存对象会同步补偿，补偿删除失败登记 `UPLOAD_COMPENSATION` 持久任务。
- 确定性校验失败删除暂存对象；删除失败登记 `VALIDATION_COMPENSATION`。媒体保持 `FAILED` 且不可公开读取，不会覆盖上一份一致 Revision。
- Avatar 上传补偿和替换/删除也使用相同 StoragePort 与持久清理机制，不再在业务事务中同步删除文件。

### 立即撤权与幂等物理删除

- 作者删除独立 Media、作者彻底删除作品、管理员彻底删除作品及 Avatar 替换/删除，均先在业务事务中把无引用 Asset 置为 `DELETE_PENDING` 并登记清理任务。
- `readableWeb` 只允许 `READY`，因此业务提交后原图从未开放、Web 读取立即返回防枚举 404；文件系统故障不再回滚已提交的逻辑撤权。
- Worker 对原图与 Web 对象使用 `deleteIfExists` 语义；成功后显式 SQL 清空两个 Storage Key 并置 `DELETED`，重复运行结果稳定。

### 持久任务、重试与并发

- 追加 Flyway `V16__v1_media_consistency_cleanup.sql`，未修改既有 Migration。
- `media_cleanup_job` 支持 Media 目标或仅 Storage Key 目标，并记录 `reason/status/attempt_count/next_attempt_at/deadline_at/locked_until/last_error_category/completed_at`。
- 状态集合为 `PENDING/RUNNING/RETRY/DELETED/EXHAUSTED/CANCELLED`；默认最多 6 次尝试，指数退避上限 60 分钟，并受 24 小时 deadline 约束，不会无限静默重试。
- Worker 使用短事务 `FOR UPDATE SKIP LOCKED` 领取单条任务，随后在事务外做文件 I/O；五分钟租约到期的 `RUNNING` 可由重启后的 Worker 恢复。
- 删除前再次确认 Asset 为 `DELETE_PENDING` 且没有 Revision/Avatar 引用；发现引用或状态不符时转 `CANCELLED`，不会误删合法媒体。

### 双向对账

- 新增可调度且可同步测试驱动的 `MediaConsistencyReconciler`。
- 能识别并持久记录 `UNTRACKED_FILE`、`DB_REFERENCE_MISSING_FILE`、`DUPLICATE_STORAGE_KEY`、`STATUS_ANOMALY` 和 `STALE_UNREFERENCED_MEDIA`。
- 只把超过 24 小时且没有任何 DB Storage Key 归属的文件登记为 `ORPHAN_RECONCILIATION`；新文件、正在处理文件、合法私有文件和仍被引用文件均不自动删除。
- 超过 24 小时的无引用 `PROCESSING/FAILED` Asset 先逻辑撤权再排队；`DELETE_PENDING` 缺任务时自动补登记。对归属不明确的缺文件、重复键或状态异常只记录，不自行猜测或迁移。

## 自动化验证

### 最终定向矩阵

- Schema：`px2400_v1_be008_targeted_final2_20260830`，从空状态一次性应用 Flyway `V1～V16`。
- 媒体目录：独立 `/private/tmp` 目录，未使用 `private-media-local/`。
- 命令范围：`MediaConsistencyCleanupIntegrationTest,MediaProcessorIntegrationTest`。
- 结果：12 tests，0 failures，0 errors，0 skipped。
- 新增 6 个确定性场景：
  - 删除提交立即撤权，随后物理清理成功且重复 Runner 无动作；
  - 第一次删除故障进入 `RETRY`，Clock 前进后重试成功；
  - 过期 `RUNNING` 租约模拟进程重启后恢复；
  - 达到最大尝试进入 `EXHAUSTED`，保留原因、次数、下次时间、错误分类和终态时间；
  - 两个并发 Runner 对同一任务只产生一次有效领取，最终稳定；
  - 双向对账识别四类基础异常，只排队超过保护期的无 DB 文件；
  - 校验失败的补偿删除故障进入持久任务，相同 `clientUploadId` 重放仍只有一个 Asset。
- `FaultStorage` 与 `MutableClock` 仅存在于 `src/test`；生产代码未新增测试 HTTP Endpoint 或绕过开关。

### clean verify

- Schema：`px2400_v1_be008_verify_20260830`，从空状态一次性应用 Flyway `V1～V16`。
- 命令：`./mvnw clean verify`。
- 结果：50 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS`。
- 套件：`BackendP0IntegrationMatrixTest` 10、`MediaConsistencyCleanupIntegrationTest` 6、`MediaProcessorIntegrationTest` 6、`ExifErrorLocationIntegrationTest` 6、`ExifTimeRulesIntegrationTest` 3、`OpenApiContractTest` 3、`IdempotencyServiceTest` 3、`AuthPolicyTest` 3、`AdminContractSecurityTest` 2、`StrongEtagTest` 2、`UnicodeTextTest` 4、`CsrfTokenServiceTest` 1、`AppApplicationTests` 1。
- 既有认证、关键状态机、ETag、稳定分页、防枚举、EXIF、逐 Operation 错误和有效 PNG 回归全部保持通过。

### 中间诊断说明

- 首个定向空库在 V16 的单条 `ALTER TABLE` 内先删除再同名重建 FK 时被 MySQL 判定重名；迁移拆成两条追加脚本语句后，放弃该 Schema，所有最终 Schema 均从空状态重新应用 V1～V16。
- 后续定向测试分别暴露 JDBC `DATETIME` 可能直接返回 `LocalDateTime`，以及 MyBatis-Plus 默认不更新 `null` Storage Key；实现改为双类型时间映射和显式 SQL 清空键。最终定向与 clean verify 均使用新的空 Schema，不把中间诊断结果作为通过证据。

## 空库运行时与真实 HTTP

- Runtime Schema：`px2400_v1_be008_runtime_20260830`，从空状态应用 `V1～V16`。
- `local` Profile 使用本地 Mail Sink；数据库 URL、空密码和上传目录由命令行显式指向隔离实例与 `/private/tmp`。没有读取旧数据库或本机旧媒体。
- 健康检查：应用 `UP`、数据库组件 `UP`。
- 使用一个纯合成账号和一张程序生成的 1200×1200 PNG，不含真实 PII。
- 真实 HTTP 链路：登录 200；上传 202/PROCESSING；状态读取 200/READY；Web JPEG 读取 200；删除 200/DELETE_PENDING；同一 Web URL 在删除提交后立即 404。
- 后台 Worker 随后得到 `OWNER_MEDIA_DELETE / attempt_count=1 / DELETED`；Asset 为 `DELETED`，原图与 Web Storage Key 均为 `NULL`，对应临时媒体文件计数为 0，开放一致性 Issue 为 0。
- 中断恢复后，Coordinator 已精确吊销该合成账号唯一 `auth_session` 与 `auth_refresh_token`，确认应用不再监听、临时 MySQL 经 `mysqladmin` 正常停止，并将所有 V1-BE-008 临时数据库目录、媒体、运行响应和 OpenAPI 文件移入系统废纸篓；Repository 未因此变更。

## 运行时 OpenAPI

- 采集时临时文件：`/private/tmp/2400px-v1-be008-runtime-openapi.json`；安全收尾后已移入系统废纸篓，不是提交的静态契约。
- SHA-256：`3aefdbcf51a1e3dea84d7c1f319ea28affca23440990297d98bf4dcebf5119bb`。
- 字节数：332290；Path：40；Operation：51。
- 本工作包未新增或修改 Controller Mapping、DTO/VO、Validation、错误集合或 OpenAPI 注解；`OpenApiContractTest` 3/3 通过。
- 相比 V1-BE-007 的整体文件哈希不同，运行时采集端口由 18087 改为 18088；生成文档包含运行时 Server URL。业务 Path/Operation 数、文件字节数与闭合契约测试保持不变，因此没有消费者契约变化。

## 验收标准逐项结论

| AC | 结论 | 证据 |
|---|---|---|
| 1 | PASS | 上传/处理失败不可访问；补偿删除失败持久排队；`clientUploadId` 单一 Asset；上一致 Revision 不受影响 |
| 2 | PASS | 双向对账覆盖无 DB 文件、缺文件、重复键、状态异常与过期媒体；只自动处理无归属且超过保护期的对象 |
| 3 | PASS | 所有删除入口先 `DELETE_PENDING`；真实 HTTP 立即 404；物理任务幂等到 `DELETED` |
| 4 | PASS | 24 小时保护/deadline、指数退避、尝试次数、错误分类、next attempt 与终态完整可观察 |
| 5 | PASS | 非 HTTP 同步 Runner、可控 Clock、StoragePort 故障替身覆盖成功/失败/重试/耗尽/重启/并发 |
| 6 | PASS | 最终定向、clean verify 和 runtime 三个最终 Schema 均从空状态应用 V1～V16；DB/文件/任务终态对账一致 |
| 7 | PASS | 40 Path/51 Operation 保持；无消费者业务契约变化 |
| 8 | PASS | `V1-BE-GAP-006` 达到 `ready_for_coordinator_verification`；仅 Coordinator 可决定 `CLOSED` |

## 残余风险与未执行项

- `EXHAUSTED` 是明确终态，需要部署环境的运维告警/人工重排流程；本地 v1.0 Backend 已保留完整可查询字段，本包未创建外部监控资源。
- 对账对缺文件、重复键和归属异常采取保守记录策略，不自动修复业务归属；这避免误删，但需要后续运维处置清单。
- 未执行长时间多进程压力、真实磁盘断电或对象存储测试；本包默认技术方向仍是本地私有目录。
- `V1-BE-GAP-005/007/008/009` 不在本工作包范围，状态保持不变。
- 未启动 Tester 正式测试；最终 `CLOSED` 由 Coordinator 回收本证据后决定。
