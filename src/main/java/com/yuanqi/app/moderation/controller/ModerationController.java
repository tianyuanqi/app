package com.yuanqi.app.moderation.controller;

import com.yuanqi.app.common.api.PageResult;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.common.idempotency.IdempotencyService;
import com.yuanqi.app.moderation.dto.ReviewRequests;
import com.yuanqi.app.moderation.service.ReviewService;
import com.yuanqi.app.moderation.vo.ModerationViews;
import com.yuanqi.app.photo.service.WorkDeletionService;
import com.yuanqi.app.photo.vo.WorkViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "内容审核")
@RestController
@RequestMapping("/api/v1/moderation/photos")
@SecurityRequirement(name = "Authorization")
public class ModerationController {
    private final ReviewService service;
    private final WorkDeletionService deletionService;
    private final IdempotencyService idempotency;

    public ModerationController(ReviewService service, WorkDeletionService deletionService,
                                IdempotencyService idempotency) {
        this.service = service;
        this.deletionService = deletionService;
        this.idempotency = idempotency;
    }

    @Operation(summary = "待审队列")
    @GetMapping
    public Result<PageResult<ModerationViews.TargetSummary>> queue(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.queue(page, pageSize));
    }

    @Operation(summary = "读取管理员作品治理摘要")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功",
                    headers = @Header(name = "ETag", description = "作品管理资源的原始强 ETag",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "401", description = "未认证"),
            @ApiResponse(responseCode = "403", description = "无管理员权限"),
            @ApiResponse(responseCode = "404", description = "资源不可用")
    })
    @GetMapping("/{workId}")
    public ResponseEntity<Result<ModerationViews.AdminPhotoSummary>> summary(@PathVariable String workId) {
        ModerationViews.AdminPhotoSummary view = service.summary(workId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, view.versionTag()).body(Result.success(view));
    }

    @Operation(summary = "读取待审目标；普通草稿统一 404")
    @GetMapping("/{workId}/revisions/{revisionId}")
    public ResponseEntity<Result<ModerationViews.Target>> target(@PathVariable String workId,
                                                                 @PathVariable String revisionId) {
        ModerationViews.Target view = service.target(UserContext.getUserId(), workId, revisionId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, view.versionTag()).body(Result.success(view));
    }

    @Operation(summary = "审核历史")
    @GetMapping("/{workId}/history")
    public Result<PageResult<ModerationViews.Event>> history(@PathVariable String workId,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.history(workId, page, pageSize));
    }

    @Operation(summary = "审核通过；允许管理员自审并记录 selfReview")
    @PostMapping("/{workId}/revisions/{revisionId}/approve")
    public ResponseEntity<Result<ModerationViews.Mutation>> approve(
            @PathVariable String workId, @PathVariable String revisionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return idempotency.execute(subject(), "POST",
                "/api/v1/moderation/photos/{workId}/revisions/{revisionId}/approve", key,
                Map.of("workId", workId, "revisionId", revisionId, "ifMatch", String.valueOf(ifMatch)),
                ModerationViews.Mutation.class,
                () -> response(service.approve(UserContext.getUserId(), workId, revisionId, ifMatch)));
    }

    @Operation(summary = "驳回待审版本")
    @PostMapping("/{workId}/revisions/{revisionId}/reject")
    public ResponseEntity<Result<ModerationViews.Mutation>> reject(
            @PathVariable String workId, @PathVariable String revisionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReviewRequests.Reason request) {
        return idempotency.execute(subject(), "POST",
                "/api/v1/moderation/photos/{workId}/revisions/{revisionId}/reject", key,
                Map.of("workId", workId, "revisionId", revisionId, "ifMatch", String.valueOf(ifMatch),
                        "reason", request.reason()), ModerationViews.Mutation.class,
                () -> response(service.reject(UserContext.getUserId(), workId, revisionId,
                        ifMatch, request.reason())));
    }

    @Operation(summary = "下架当前公开作品并使待审修改失效")
    @PostMapping("/{workId}/offline")
    public ResponseEntity<Result<ModerationViews.Mutation>> offline(
            @PathVariable String workId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReviewRequests.Reason request) {
        return idempotency.execute(subject(), "POST", "/api/v1/moderation/photos/{workId}/offline", key,
                Map.of("workId", workId, "ifMatch", String.valueOf(ifMatch), "reason", request.reason()),
                ModerationViews.Mutation.class,
                () -> response(service.offline(UserContext.getUserId(), workId, ifMatch, request.reason())));
    }

    @Operation(summary = "管理员彻底删除作品并仅保留最小记录")
    @DeleteMapping("/{workId}")
    public Result<WorkViews.DeleteResult> delete(
            @PathVariable String workId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReviewRequests.Delete request) {
        return idempotency.execute(subject(), "DELETE", "/api/v1/moderation/photos/{workId}", key,
                Map.of("workId", workId, "ifMatch", String.valueOf(ifMatch),
                        "confirmation", request.confirmation(), "reason", request.reason()),
                WorkViews.DeleteResult.class,
                () -> ResponseEntity.ok(Result.success(deletionService.deleteAdmin(UserContext.getUserId(), workId,
                        ifMatch, request.confirmation(), request.reason())))).getBody();
    }

    private ResponseEntity<Result<ModerationViews.Mutation>> response(ModerationViews.Mutation view) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (view.versionTag() != null) builder.header(HttpHeaders.ETAG, view.versionTag());
        return builder.body(Result.success(view));
    }

    private String subject() {
        return UserContext.getUid();
    }
}
