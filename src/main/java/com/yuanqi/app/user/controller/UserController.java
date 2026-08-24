package com.yuanqi.app.user.controller;

import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.user.dto.ProfileRequests;
import com.yuanqi.app.user.service.ProfileService;
import com.yuanqi.app.user.vo.ProfileViews;
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

    public UserController(ProfileService profileService) {
        this.profileService = profileService;
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
}
