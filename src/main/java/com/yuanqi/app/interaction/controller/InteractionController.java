package com.yuanqi.app.interaction.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.interaction.dto.InteractionRequests;
import com.yuanqi.app.interaction.service.InteractionService;
import com.yuanqi.app.interaction.vo.InteractionViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.yuanqi.app.common.idempotency.IdempotencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.Map;

@Tag(name="作品互动")
@RestController
@RequestMapping("/api/v1/photos/{workId}")
public class InteractionController {
    private final InteractionService service;
    private final IdempotencyService idempotency;
    public InteractionController(InteractionService service,IdempotencyService idempotency){this.service=service;this.idempotency=idempotency;}

    @Operation(summary="点赞作品；允许作者自赞",security=@SecurityRequirement(name="Authorization"))
    @PutMapping("/like") public Result<InteractionViews.LikeMutation> like(@PathVariable String workId){return Result.success(service.like(UserContext.getUserId(),workId,true));}
    @Operation(summary="取消点赞",security=@SecurityRequirement(name="Authorization"))
    @DeleteMapping("/like") public Result<InteractionViews.LikeMutation> unlike(@PathVariable String workId){return Result.success(service.like(UserContext.getUserId(),workId,false));}
    @Operation(summary="分页读取一级评论")
    @GetMapping("/comments") public Result<InteractionViews.CommentPage> comments(@PathVariable String workId,@RequestParam(defaultValue="1") int page){return Result.success(service.comments(UserContext.getUserId(),workId,page));}
    @Operation(summary="发表评论",security=@SecurityRequirement(name="Authorization"))
    @PostMapping("/comments") public Result<InteractionViews.CommentCreate> comment(@PathVariable String workId,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody InteractionRequests.Comment request){return idempotency.execute(subject(),"POST","/api/v1/photos/{workId}/comments",key,Map.of("workId",workId,"content",request.content()),InteractionViews.CommentCreate.class,()->ResponseEntity.ok(Result.success(service.createComment(UserContext.getUserId(),workId,request.content())))).getBody();}
    @Operation(summary="按 Cursor 读取一级回复")
    @GetMapping("/comments/{rootId}/replies") public Result<InteractionViews.CursorPage<InteractionViews.Reply>> replies(@PathVariable String workId,@PathVariable String rootId,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit){return Result.success(service.replies(UserContext.getUserId(),workId,rootId,cursor,limit));}
    @Operation(summary="回复一级评论",security=@SecurityRequirement(name="Authorization"))
    @PostMapping("/comments/{rootId}/replies") public Result<InteractionViews.ReplyCreate> reply(@PathVariable String workId,@PathVariable String rootId,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody InteractionRequests.Comment request){return idempotency.execute(subject(),"POST","/api/v1/photos/{workId}/comments/{rootId}/replies",key,Map.of("workId",workId,"rootId",rootId,"content",request.content()),InteractionViews.ReplyCreate.class,()->ResponseEntity.ok(Result.success(service.createReply(UserContext.getUserId(),workId,rootId,request.content())))).getBody();}
    @Operation(summary="删除自己的评论或回复",security=@SecurityRequirement(name="Authorization"))
    @DeleteMapping("/comments/{commentId}") public Result<InteractionViews.CommentMutation> delete(@PathVariable String workId,@PathVariable String commentId,@RequestHeader(value="Idempotency-Key",required=false)String key){return idempotency.execute(subject(),"DELETE","/api/v1/photos/{workId}/comments/{commentId}",key,Map.of("workId",workId,"commentId",commentId),InteractionViews.CommentMutation.class,()->ResponseEntity.ok(Result.success(service.deleteOwn(UserContext.getUserId(),workId,commentId)))).getBody();}
    private String subject(){return UserContext.getUid();}
}
