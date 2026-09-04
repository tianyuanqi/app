# V1-BE-010：P2 持久化与安全加固验证证据

## 结论

工作包结果：PASS。`V1-BE-GAP-008` 与 `V1-BE-GAP-009` 已具备供 Coordinator 核验的实现和验证证据，状态仅为 `ready_for_coordinator_verification`，本工作包不自行关闭 Finding、不推进 Release 或 Human Gate 状态。

## 冻结基线与边界

- 工作流：`WF-2026-08-28-02`；交接中列出的 workflow、需求基线、状态文件、V1-BE-009 证据和四个源码输入 SHA-256 均在写入前逐项匹配。
- 起点：`codex/v1-backend` 的 `d062ef4d487ddcbe0c5d6882a779a1c1f7b4d675`；实现 Commit：`b8f7331b35e2185d3160858f662a759259ea4868`。
- 仅修改 `app/`。未读取、迁移或清理旧数据库；未修改 Frontend、共享文档、Remote；未 Push、PR、Merge 或 Tag。
- 预存未跟踪目录 `private-media-local/` 始终避开。开始和实现 Commit 后的目录元数据均为 `size=160`、`mtime=1787563881`、`inode=151039734`。

## 实现证据

### V1-BE-GAP-008：复合键 Mapper

- `PhotoTagRelationMapper` 与 `RevisionMediaMapper` 移除 MyBatis-Plus `BaseMapper` 继承。
- 未向 `PhotoTagRelation` 或 `RevisionMedia` 添加伪造 `@TableId`。两类关联均通过明确的 `insertRelation`、`list...`、`delete...` SQL 操作；调用点同步改用这些方法。
- 该改动不涉及数据模型、Flyway、API Path、Operation 或 DTO 变更。

### V1-BE-GAP-009：默认内存用户

- `AppApplication` 显式排除 `UserDetailsServiceAutoConfiguration`。
- 保留已有 `SecurityConfig` 的 JWT `FilterChain`；未启用 HTTP Basic，也未引入新的认证方式。
- `P2HardeningContractTest` 检查 Mapper 基类、ById 误用和 `@TableId`，并检查启动类排除与既有安全链；`AppApplicationTests` 检查运行上下文不存在 `UserDetailsService`；`AdminContractSecurityTest` 增加 HTTP Basic 反向断言。

## 自动化与空库验证

- 定向命令：`P2HardeningContractTest`、`BackendP0IntegrationMatrixTest`、`AdminContractSecurityTest`、`OpenApiContractTest`，共 17 个测试通过，0 failures、0 errors、0 skipped。
- 最终命令：`./mvnw clean verify`，共 57 个测试通过，0 failures、0 errors、0 skipped。
- 两轮均使用新的隔离 MySQL Schema；最终验证 Schema 为 `px2400_v1_be010_verify_final_20260831`，从空状态成功应用 Flyway `V1`～`V16`。未使用旧数据库。

## 最终隔离运行时证据

- 最终构建在独立 Schema `px2400_v1_be010_runtime_20260831` 启动，Flyway 验证 16 个迁移；`/actuator/health` 返回 HTTP 200，应用和 db 均为 `UP`。
- 启动日志中 `Using generated security password`、`Not found @TableId`、`Cannot use ... ById` 的匹配数均为 0。
- 权限矩阵：`GET /api/v1/admin/users/u_unknown` 的匿名请求为 401/`AUTH_REQUIRED`；携带 HTTP Basic 的请求为 401/`SESSION_INVALID`，且响应无 `WWW-Authenticate`。这证明未回退到 HTTP Basic，不能替代 JWT 认证验证。
- 公开 `GET /api/v1/categories` 为 200。运行时 `/v3/api-docs` 为 40 Paths、51 Operations；`GET /api/v1/admin/users/{uid}` 声明 `Authorization` security。
- OpenAPI 临时导出：SHA-256 `dc5f4bf9a2bddb813adfa87d31daf77a4c0d47b3b2d521a4c5597535b322dd4f`，332436 bytes。导出只用于本地核验，随后与隔离运行时一并清理；该哈希不表示接口发生变更或获批。

## 清理与剩余事项

- 临时运行时已优雅停止；合成运行账号未创建业务 Session，因此无 Session 可吊销。隔离 MySQL、媒体目录和临时 HTTP/OpenAPI 响应均已停止并清理，不保留凭据、个人数据或 Secret。
- `V1-BE-GAP-008/009` 仍需 Coordinator 核对实现和本证据后决定是否 `CLOSED`。`V1-BE-GAP-005`（生产邮件 Provider）和 Tester 正式测试不属于本工作包，仍为后续门槛。
