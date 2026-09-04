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
import com.yuanqi.app.common.idempotency.IdempotencyService;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.RequestHeader;import java.util.Map;

@RestController
@RequestMapping("/api/v1/moderation/comments")
@SecurityRequirement(name="Authorization")
public class ModerationCommentController {
    private final InteractionService service;
    private final IdempotencyService idempotency;public ModerationCommentController(InteractionService service,IdempotencyService idempotency){this.service=service;this.idempotency=idempotency;}
    @Operation(summary="管理员删除违规评论；公开结果不披露操作者和原因")
    @DeleteMapping("/{commentId}") public Result<InteractionViews.CommentMutation> delete(@PathVariable String commentId,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody InteractionRequests.AdminDelete request){return idempotency.execute(UserContext.getUid(),"DELETE","/api/v1/moderation/comments/{commentId}",key,Map.of("commentId",commentId,"reason",request.reason()),InteractionViews.CommentMutation.class,()->ResponseEntity.ok(Result.success(service.deleteAdmin(UserContext.getUserId(),commentId,request.reason())))).getBody();}
}
