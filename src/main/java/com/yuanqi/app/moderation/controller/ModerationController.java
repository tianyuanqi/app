package com.yuanqi.app.moderation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.moderation.dto.ModerationRequests;
import com.yuanqi.app.moderation.service.ModerationService;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.photo.vo.PhotoDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容审核接口（管理员）。
 */
@Tag(name = "6. 内容审核", description = "作品审核通过、驳回与下架")
@SecurityRequirement(name = "Authorization")
@RestController
@RequestMapping("/api/v1/moderation/photos")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Operation(summary = "待审作品列表")
    @GetMapping
    public Result<IPage<PhotoCardVO>> listPending(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(moderationService.listPending(UserContext.getUserId(), current, pageSize));
    }

    @Operation(summary = "审核通过并发布")
    @PostMapping("/{id}/approve")
    public Result<PhotoDetailVO> approve(@PathVariable Long id) {
        return Result.success(moderationService.approve(id, UserContext.getUserId()));
    }

    @Operation(summary = "驳回作品")
    @PostMapping("/{id}/reject")
    public Result<PhotoDetailVO> reject(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) ModerationRequests.Reject request) {
        return Result.success(moderationService.reject(id, UserContext.getUserId(),
                request == null ? new ModerationRequests.Reject() : request));
    }

    @Operation(summary = "下架已发布作品")
    @PostMapping("/{id}/offline")
    public Result<PhotoDetailVO> offline(@PathVariable Long id) {
        return Result.success(moderationService.offline(id, UserContext.getUserId()));
    }
}
