# V1-BE-007 逐图拍摄参数错误定位证据

## 结论

- 结果：`PASS WITH FINDINGS`。
- `FE-V1-CONTRACT-005`：`ready_for_frontend_verification`；Backend 未自行标记 `CLOSED`。
- Branch：`codex/v1-backend`。
- 起始 Commit：`26e30cead497f026685a30911b3be45129b4bdac`。
- 实现 Commit：`65d4a73cd6f454fea62db5dff5b4f35cf45e0521`。
- 未新增 Path、Operation、错误 envelope、Migration 或依赖。

## 基线和边界

- `workflow_revision`：`WF-2026-08-28-02`。
- 交接列出的 8 项 `input_checksums` 在写入前全部匹配。
- 起始 Worktree 仅有预存未跟踪目录 `private-media-local/`；全过程未读取、修改、暂存或删除。
- 冻结需求、Frontend、共享文档、旧数据库与 Remote 均未修改。
- 只修改现有 `ErrorResult` 定位链路、逐项参数校验、OpenAPI 描述、直接测试和 Backend 契约说明。

## 错误定位契约

- HTTP Status 与顶层错误保持 `400 / VALIDATION_FAILED`。
- 逐项字段路径使用零基数组下标：`mediaParameters[index].parameters.field`。
- `itemErrors.resourceId` 只返回请求调用者已知的公开 `mediaId`；不返回数据库主键或其他内部 ID。
- `itemErrors.code` 固定为 `INVALID_MEDIA_PARAMETERS`；同一无效数组项只返回一个 ItemError。
- 字段级稳定代码：未来时间为 `CAPTURE_TIME_IN_FUTURE`，超过用户可见字符上限为 `MAX_GRAPHEME_LENGTH`，换行/控制字符为 `INVALID_TEXT`。
- 同一请求先完整收集全部无效媒体和字段，再按数组项顺序及固定字段顺序返回，不以异常首项覆盖后续错误。
- 嵌套 Bean Validation 使用相同字段路径和 ItemError 媒体定位；普通顶层字段继续只进入 `fieldErrors`。
- 全局 `message` 仅供人类阅读，Frontend 不需要也不应解析消息文本。

## 真实 HTTP 400 结构

以下为第三项未来手工拍摄时间的完整字段结构；运行时 `traceId` 已验证为非空，不在持久证据中记录具体值：

```json
{
  "code": "VALIDATION_FAILED",
  "message": "请求参数校验失败",
  "retryable": false,
  "retryAfterSeconds": null,
  "fieldErrors": [
    {
      "path": "mediaParameters[2].parameters.captureTime",
      "code": "CAPTURE_TIME_IN_FUTURE",
      "message": "拍摄时间不能晚于当前 Asia/Shanghai 时间"
    }
  ],
  "itemErrors": [
    {
      "clientItemId": null,
      "resourceId": "media_be007_future",
      "code": "INVALID_MEDIA_PARAMETERS",
      "message": "该媒体包含无效拍摄参数",
      "retryable": false
    }
  ],
  "conflict": null,
  "verification": null,
  "traceId": "<非空运行时追踪标识>"
}
```

该响应可以同时以数组下标、公开媒体 ID 和字段名唯一定位第三张媒体的 `captureTime`，且未出现内部 ID。

## 自动化验证

### 定向测试

- 最终 Schema：`px2400_v1_be007_targeted_final_20260830`。
- 从空库一次性应用 Flyway `V1～V15`。
- 结果：12 tests，0 failures，0 errors，0 skipped。
- `ExifErrorLocationIntegrationTest` 6/6：第三项未来时间、第一和第三项同时失败、同项多字段失败、嵌套 Bean Validation、普通顶层字段、合法三媒体保存。
- `ExifTimeRulesIntegrationTest` 3/3：注入 `Clock` 的过去/当前/未来、跨日、非默认时区和 `+08:00` 回归。
- `OpenApiContractTest` 3/3：路径语义、公开 mediaId、ItemError 代码以及草稿 400 Schema 引用。
- 首次定向运行的业务实现和 OpenAPI 测试已通过，但新增 HTTP 测试把业务固定 Clock 同时用于签发 JWT，令 JWT 相对墙钟过期并在安全过滤器返回 401。该测试 Schema 随即弃用；移除 HTTP 测试的固定 Clock 后，在新的 `targeted_final` 空库重新运行。时间规则仍由独立注入 Clock 测试覆盖，未在失败 Schema 上修改或复用数据库结构。

### clean verify

- 命令：`./mvnw clean verify`。
- Schema：`px2400_v1_be007_verify_20260830`。
- 从空库一次性应用 Flyway `V1～V15`。
- 结果：44 tests，0 failures，0 errors，0 skipped，`BUILD SUCCESS`。
- 套件：`AppApplicationTests` 1、`BackendP0IntegrationMatrixTest` 10、`CsrfTokenServiceTest` 1、`AuthPolicyTest` 3、`AdminContractSecurityTest` 2、`OpenApiContractTest` 3、`IdempotencyServiceTest` 3、`StrongEtagTest` 2、`UnicodeTextTest` 4、`ExifTimeRulesIntegrationTest` 3、`ExifErrorLocationIntegrationTest` 6、`MediaProcessorIntegrationTest` 6。

## 运行时和真实 HTTP

- 运行 Profile：`local`。
- 运行 Schema：`px2400_v1_be007_runtime_20260830`，从空库一次性应用 `V1～V15`。
- 媒体目录：`/private/tmp/px2400-v1-be007-runtime-media.SjRJzP`。
- 健康：应用 `UP`，数据库组件 `UP`。
- 使用两个合成账号和三项合成媒体；未使用真实 PII，未在证据中记录密码、Token、Cookie 或哈希。
- 第三项未来手工 `captureTime`：HTTP 400，字段路径、字段代码、公开 mediaId 和 ItemError 代码均与上文一致。
- 同一草稿随后使用合法三媒体参数保存：HTTP 200，证明失败事务没有污染状态或 ETag。
- 未来 EXIF 媒体状态：`EXIF_CAPTURE_TIME_IN_FUTURE`、候选 `captureTime=null`；其他候选参数仍存在。
- 使用包含未来 EXIF 媒体的另一作品完成创建、提交、管理员 Target、批准和公开详情；Target 与公开详情均返回该媒体自身 `Camera Future`，相关 HTTP 均为 200，证明原有警告降级与作者/审核/公开投影未被破坏。
- 首次运行启动因本机被忽略的 local secrets 覆盖密码而在建立数据库连接前失败；未读取 secrets，运行 Schema 保持空状态。随后用显式隔离数据库参数成功启动并从零迁移该 Schema。

## 运行时 OpenAPI

- 临时导出：`/private/tmp/2400px-v1-be007-runtime-openapi.json`（不提交）。
- SHA-256：`531dbaa4fd0d4cb4af0baa4a785a74866b18c01fc209d65e9376d5f79d9ab92f`。
- 字节数：332290。
- Path：40；Operation：51；逐 Operation Error Schema：336。
- `PUT /api/v1/photos/{workId}/draft` 的 400 继续引用 `ErrorResult_PUT_photos_workId_draft_400`。
- `FieldError.path` 明确描述零基数组路径；`ItemError.resourceId` 明确为调用者已知公开 ID；代码示例为 `INVALID_MEDIA_PARAMETERS`。
- 既有逐 Operation ErrorCode、ETag、幂等、权限和统一 envelope 未改变。

## 安全、数据和残余项

- 未新增 Migration；三个最终 Schema 均从空状态应用既有 `V1～V15`。
- 未读取、迁移或清理旧数据库；未提交临时响应、OpenAPI、媒体或构建产物。
- 真实验收使用的合成 Session 和 refresh 凭证在环境关闭前全部吊销。
- `FE-V1-CONTRACT-005` 仍需 Coordinator 派发最小 Frontend Regression 后决定是否 `CLOSED`；`V1-BE-GAP-004` 同步等待该回归。
- `V1-BE-GAP-005～009` 未处理，状态不变。
- 未启动 Tester 正式测试；未执行 push、PR、merge、Tag、Fetch 或 Remote 修改。
- 复合主键无 `TableId` 与 Spring 默认内存用户密码警告仍分别保留为 `V1-BE-GAP-008/009`。
