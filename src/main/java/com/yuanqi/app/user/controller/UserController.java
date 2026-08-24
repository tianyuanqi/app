package com.yuanqi.app.user.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.user.dto.ProfileRequests;
import com.yuanqi.app.user.service.ProfileService;
import com.yuanqi.app.user.vo.ProfileViews;
import com.yuanqi.app.photo.service.PublicPhotoService;
import com.yuanqi.app.photo.vo.PublicPhotoViews;
import com.yuanqi.app.common.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户资料")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final ProfileService profileService;
    private final PublicPhotoService publicPhotoService;
    private final com.yuanqi.app.user.service.AvatarService avatarService;

    public UserController(ProfileService profileService, PublicPhotoService publicPhotoService,com.yuanqi.app.user.service.AvatarService avatarService) {
        this.profileService = profileService;
        this.publicPhotoService=publicPhotoService;
        this.avatarService=avatarService;
    }

    @Operation(summary = "获取当前用户私有资料", security = @SecurityRequirement(name = "Authorization"))
    @GetMapping("/me")
    public ResponseEntity<Result<ProfileViews.PrivateProfile>> me() {
        ProfileViews.PrivateProfile view = profileService.privateProfile(UserContext.getUserId());
        return ResponseEntity.ok().header(HttpHeaders.ETAG, view.versionTag()).body(Result.success(view));
    }

    @Operation(summary = "修改当前用户资料", security = @SecurityRequirement(name = "Authorization"))
    @PutMapping("/me")
    public ResponseEntity<Result<ProfileViews.PrivateProfile>> update(
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ProfileRequests.Update request) {
        ProfileViews.PrivateProfile view = profileService.update(UserContext.getUserId(), ifMatch, request);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, view.versionTag()).body(Result.success(view));
    }

    @Operation(summary = "获取公开用户主页")
    @GetMapping("/{uid}")
    public Result<ProfileViews.PublicProfile> publicProfile(@PathVariable String uid) {
        return Result.success(profileService.publicProfile(uid));
    }

    @Operation(summary="公开用户作品墙") @GetMapping("/{uid}/photos")
    public Result<PageResult<PublicPhotoViews.Card>> publicPhotos(@PathVariable String uid,@org.springframework.web.bind.annotation.RequestParam(defaultValue="1")int page){profileService.publicProfile(uid);return Result.success(publicPhotoService.feed(UserContext.getUserId(),null,null,java.util.List.of(),page,uid));}

    @Operation(summary="上传并生成 512x512 头像",security=@SecurityRequirement(name="Authorization")) @org.springframework.web.bind.annotation.PostMapping(value="/me/avatar",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<com.yuanqi.app.user.service.AvatarService.Mutation>> avatar(@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String match,@org.springframework.web.bind.annotation.RequestPart("file")org.springframework.web.multipart.MultipartFile file){var v=avatarService.upload(UserContext.getUserId(),match,file);return ResponseEntity.ok().header(HttpHeaders.ETAG,v.profileVersionTag()).body(Result.success(v));}
    @Operation(summary="删除当前头像",security=@SecurityRequirement(name="Authorization")) @org.springframework.web.bind.annotation.DeleteMapping("/me/avatar") public ResponseEntity<Result<com.yuanqi.app.user.service.AvatarService.Mutation>> deleteAvatar(@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String match){var v=avatarService.delete(UserContext.getUserId(),match);return ResponseEntity.ok().header(HttpHeaders.ETAG,v.profileVersionTag()).body(Result.success(v));}
}
