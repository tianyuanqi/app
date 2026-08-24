package com.yuanqi.app.photo.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.photo.dto.WorkRequests;
import com.yuanqi.app.photo.service.WorkService;
import com.yuanqi.app.photo.service.WorkDeletionService;
import com.yuanqi.app.photo.vo.WorkViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "作品")
@RestController
@RequestMapping("/api/v1/photos")
@SecurityRequirement(name = "Authorization")
public class PhotoController {
    private final WorkService service;
    private final WorkDeletionService deletionService;

    public PhotoController(WorkService service, WorkDeletionService deletionService) { this.service = service; this.deletionService=deletionService; }

    @Operation(summary = "创建作品和首个草稿")
    @PostMapping
    public ResponseEntity<Result<WorkViews.AuthorWork>> create(@Valid @RequestBody WorkRequests.Draft request) {
        return response(service.create(UserContext.getUserId(), request));
    }

    @Operation(summary = "读取作者复合状态")
    @GetMapping("/{workId}/author-view")
    public ResponseEntity<Result<WorkViews.AuthorWork>> authorView(@PathVariable String workId) {
        return response(service.authorView(UserContext.getUserId(), workId));
    }

    @Operation(summary = "创建或恢复可编辑草稿")
    @PostMapping("/{workId}/draft")
    public ResponseEntity<Result<WorkViews.AuthorWork>> createDraft(@PathVariable String workId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return response(service.createDraft(UserContext.getUserId(), workId, ifMatch));
    }

    @Operation(summary = "保存草稿")
    @PutMapping("/{workId}/draft")
    public ResponseEntity<Result<WorkViews.AuthorWork>> update(@PathVariable String workId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody WorkRequests.Draft request) {
        return response(service.updateDraft(UserContext.getUserId(), workId, ifMatch, request));
    }

    @Operation(summary = "提交审核")
    @PostMapping("/{workId}/submit")
    public ResponseEntity<Result<WorkViews.AuthorWork>> submit(@PathVariable String workId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return response(service.submit(UserContext.getUserId(), workId, ifMatch));
    }

    @Operation(summary = "撤回待审版本")
    @PostMapping("/{workId}/withdraw")
    public ResponseEntity<Result<WorkViews.AuthorWork>> withdraw(@PathVariable String workId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return response(service.withdraw(UserContext.getUserId(), workId, ifMatch));
    }

    @Operation(summary="彻底删除自己的作品、互动和媒体引用")
    @org.springframework.web.bind.annotation.DeleteMapping("/{workId}")
    public Result<WorkViews.DeleteResult> delete(@PathVariable String workId,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String ifMatch,@org.springframework.web.bind.annotation.RequestParam boolean confirmation){return Result.success(deletionService.delete(UserContext.getUserId(),workId,ifMatch,confirmation));}

    private ResponseEntity<Result<WorkViews.AuthorWork>> response(WorkViews.AuthorWork view) {
        return ResponseEntity.ok().header(HttpHeaders.ETAG, view.summary().versionTag()).body(Result.success(view));
    }
}
