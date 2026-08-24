package com.yuanqi.app.moderation.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.moderation.dto.ReviewRequests;
import com.yuanqi.app.moderation.service.ReviewService;
import com.yuanqi.app.moderation.vo.ModerationViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.yuanqi.app.common.api.PageResult;

@Tag(name = "内容审核")
@RestController
@RequestMapping("/api/v1/moderation/photos")
@SecurityRequirement(name = "Authorization")
public class ModerationController {
    private final ReviewService service;
    private final com.yuanqi.app.photo.service.WorkDeletionService deletionService;
    public ModerationController(ReviewService service,com.yuanqi.app.photo.service.WorkDeletionService deletionService) { this.service = service;this.deletionService=deletionService; }

    @Operation(summary="待审队列") @GetMapping public Result<PageResult<ModerationViews.TargetSummary>> queue(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return Result.success(service.queue(page,pageSize));}
    @Operation(summary="读取待审目标；普通草稿统一 404") @GetMapping("/{workId}/revisions/{revisionId}") public Result<ModerationViews.Target> target(@PathVariable String workId,@PathVariable String revisionId){return Result.success(service.target(UserContext.getUserId(),workId,revisionId));}
    @Operation(summary="审核历史") @GetMapping("/{workId}/history") public Result<PageResult<ModerationViews.Event>> history(@PathVariable String workId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return Result.success(service.history(workId,page,pageSize));}

    @Operation(summary = "审核通过；允许管理员自审并记录 selfReview")
    @PostMapping("/{workId}/revisions/{revisionId}/approve")
    public ResponseEntity<Result<ModerationViews.Mutation>> approve(@PathVariable String workId,
            @PathVariable String revisionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return response(service.approve(UserContext.getUserId(), workId, revisionId, ifMatch));
    }

    @Operation(summary = "驳回待审版本")
    @PostMapping("/{workId}/revisions/{revisionId}/reject")
    public ResponseEntity<Result<ModerationViews.Mutation>> reject(@PathVariable String workId,
            @PathVariable String revisionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReviewRequests.Reason request) {
        return response(service.reject(UserContext.getUserId(), workId, revisionId, ifMatch, request.reason()));
    }

    @Operation(summary = "下架当前公开作品并使待审修改失效")
    @PostMapping("/{workId}/offline")
    public ResponseEntity<Result<ModerationViews.Mutation>> offline(@PathVariable String workId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ReviewRequests.Reason request) {
        return response(service.offline(UserContext.getUserId(), workId, ifMatch, request.reason()));
    }
    @Operation(summary="管理员彻底删除作品并仅保留最小记录") @org.springframework.web.bind.annotation.DeleteMapping("/{workId}") public Result<com.yuanqi.app.photo.vo.WorkViews.DeleteResult> delete(@PathVariable String workId,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String match,@Valid @RequestBody ReviewRequests.Delete request){return Result.success(deletionService.deleteAdmin(UserContext.getUserId(),workId,match,request.confirmation(),request.reason()));}

    private ResponseEntity<Result<ModerationViews.Mutation>> response(ModerationViews.Mutation view) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (view.versionTag() != null) builder.header(HttpHeaders.ETAG, view.versionTag());
        return builder.body(Result.success(view));
    }
}
