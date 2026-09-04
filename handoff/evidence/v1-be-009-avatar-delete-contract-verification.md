# V1-BE-009 头像与管理员删除契约验证证据

## 结论

- 工作包结果：`PASS`；`V1-BE-GAP-007` 已达到 `ready_for_coordinator_verification`，最终关闭由 Coordinator 决定。
- Branch：`codex/v1-backend`。
- 起始 Commit：`3f31f4c1499f58394c2e6355446f43903d4bd768`。
- 实现 Commit：`e18d0e606742de415c11c3af284ae778a9c6148c`。
- 工作流版本（workflow_revision）：`WF-2026-08-28-02`。
- 未新增服务端裁剪矩形、缩放或坐标参数；头像仍由 Frontend 生成 512×512 裁剪文件后上传。

## 实现与问题修正

1. `AvatarMutationResult` 和 `PhotoDeleteResult` 的专用 Schema 名称及必需字段进入运行时 OpenAPI。
2. 管理员彻底删除 Controller 保留 `IdempotencyService` 返回的 `ResponseEntity`，首次响应和幂等重放均返回已声明的强 `ETag`。
3. 头像解码器原先使用 `seekForwardOnly=true` 后调用 `getNumImages(true)`，导致有效 PNG 被映射为 `INVALID_CONTENT`；改为允许搜索的输入模式后，JPEG、PNG 与 WebP 均可按既有约束解码。
4. 作品删除原先依赖 MyBatis-Plus 用 `null` 更新两个 Revision 外键，但默认更新策略忽略 `null`，管理员删除触发 `fk_work_working_revision` 并返回 500；现在在同一事务中显式清空 `public_revision_id/working_revision_id` 后删除。
5. 新增 `AvatarDeleteContractIntegrationTest`，覆盖头像格式/大小/原图不保留、512×512 输出、管理员删除 DTO、幂等重放、立即撤权和最小 Tombstone。

## 自动化与空库验证

- 所有测试均使用 `/private/tmp` 中全新初始化的隔离 MySQL 数据目录、独立端口、全新 Schema 和独立媒体目录；未连接、读取或迁移旧数据库。
- 首轮定向验证如实暴露并固定了三个问题：有效 PNG 返回 422、管理员删除因外键返回 500、新测试 UID 超过数据库 32 字符上限。完成最小修正后放弃中间状态，并从新空 Schema 重新验证。
- 最终定向：`AvatarDeleteContractIntegrationTest` 3/3、`OpenApiContractTest` 3/3，0 failure、0 error、0 skipped。
- 最终 `./mvnw clean verify`：53 tests，0 failure、0 error、0 skipped，`BUILD SUCCESS`。
- 最终 Schema 从空状态应用 Flyway `V1～V16`。
- clean verify 日志 SHA-256：`55955ed51cea5c8a4439f641c0cbe70b3794c1bc5271732c09c4ed36cbc4101e`；记录哈希后临时日志已移入系统废纸篓。

## 运行时 OpenAPI

- 健康检查：`UP`。
- Path：40；Operation：51；字节数：332,436。
- SHA-256：`25968d008895bc2fabc3110ae33be03e08d599a4911f18c6106fdf2016da4c3a`。
- `POST /api/v1/users/me/avatar` 的 multipart 请求仅包含必需 `file`，无服务端裁剪坐标。
- `AvatarMutationResult` 必需字段为 `avatar` 与 `profileVersionTag`。
- `PhotoDeleteResult` 必需字段为 `workId`、`deleted` 与 `deletedAt`。
- `DELETE /api/v1/moderation/photos/{workId}` 的 200 响应声明字符串 `ETag`。
- 运行时 OpenAPI 临时文件在记录哈希后已移入系统废纸篓；未把端口相关生成文件提交为静态契约。

## 真实 HTTP

### 头像

- 使用程序生成的 512×512 PNG，等价于 Frontend 浏览器裁剪后的上传文件。
- 上传返回 HTTP 200；响应含 `avatar.width=512`、`avatar.height=512`、`avatar.mimeType=image/jpeg`、`profileVersionTag` 和新 `ETag: "profile-1"`。
- Web 头像读取 HTTP 200，文件确认为 JPEG 512×512。
- 头像响应 SHA-256：`e44bf6e485f07bd8c2dd032aea39a0d82a0744386a0dafb2cc4279db4d0252aa`。
- 自动化同时证明 JPEG、PNG、WebP、恰好 10MB 接受，超过 10MB 拒绝；`original_storage_key` 始终为空，只保留 512×512 Web 头像。

### 管理员彻底删除

- 1200×1200 PNG 经真实 HTTP 上传从 202 进入 `READY`，随后创建作品成功。
- 管理员第一次 DELETE 与相同 `Idempotency-Key` 重放均返回 HTTP 200；响应正文逐字一致，均返回原始强 `ETag: "work-0"`。
- 响应包含相同 `workId`、`deleted=true` 和字符串 `deletedAt`；首次响应 SHA-256 为 `9f06c070ff1644b63ca8a11562f88b285dae97ff819d2b6f2c78fe3ab4be4bb2`。
- 删除提交后作者读取立即返回 404 / `RESOURCE_NOT_FOUND`；数据库对账为 `photo_work=0`、`photo_tombstone=1`。

## 安全收尾与边界

- 两个合成 Session 与两个 refresh token 均已置为 `REVOKED`。
- 临时应用已优雅停止；隔离 MySQL 已通过 `mysqladmin` 停止。
- 临时数据库目录、媒体、HTTP 响应、OpenAPI 和日志均已移入系统废纸篓，可恢复；`/private/tmp` 中不再残留 `2400px-v1-be009-*`。
- 预存未跟踪 `private-media-local/` 未读取、修改、暂存或删除；结束元数据仍为 `size=160`、`mtime=1787563881`、`inode=151039734`。
- 未修改 Frontend、共享需求或 API 路径；未执行 Tester 正式测试、push、PR、merge、Tag、Fetch 或 Remote 修改。
- 既有 `V1-BE-GAP-008`（复合主键无 `TableId` 警告）与 `V1-BE-GAP-009`（Spring 默认内存密码）仍为 P2，本任务未处理。

## 验收标准逐项结论

| AC | 结论 | 证据 |
|---|---|---|
| 1 | PASS | 运行时 Schema 专用且字段必需；头像请求仅有 `file`，无新增裁剪参数 |
| 2 | PASS | 真实 512×512 PNG 上传、响应 ETag、Web JPEG 512×512 均通过 |
| 3 | PASS | JPEG/PNG/WebP、10MB 边界与不保存原始文件由自动化覆盖 |
| 4 | PASS | 管理员删除固定 DTO、ETag、幂等重放、立即 404 和 Tombstone 均通过 |
| 5 | PASS | 新增集成测试与 OpenAPI 契约断言，不再只依赖人工观察 |
| 6 | PASS | 全新空库 V1～V16、定向 6/6、clean verify 53/53 |
| 7 | PASS | 只修运行时事实差异，40 Path/51 Operation 不变，无权限扩大 |
| 8 | PASS | `V1-BE-GAP-007` 达到 `ready_for_coordinator_verification`；仅 Coordinator 可关闭 |
