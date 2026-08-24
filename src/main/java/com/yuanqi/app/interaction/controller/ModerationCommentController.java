package com.yuanqi.app.interaction.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.interaction.dto.InteractionRequests;
import com.yuanqi.app.interaction.service.InteractionService;
import com.yuanqi.app.interaction.vo.InteractionViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moderation/comments")
@SecurityRequirement(name="Authorization")
public class ModerationCommentController {
    private final InteractionService service;
    public ModerationCommentController(InteractionService service){this.service=service;}
    @Operation(summary="管理员删除违规评论；公开结果不披露操作者和原因")
    @DeleteMapping("/{commentId}") public Result<InteractionViews.CommentMutation> delete(@PathVariable String commentId,@Valid @RequestBody InteractionRequests.AdminDelete request){return Result.success(service.deleteAdmin(UserContext.getUserId(),commentId));}
}
