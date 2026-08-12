package com.yuanqi.app.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.context.UserContext;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.user.dto.UserRequests;
import com.yuanqi.app.user.service.UserService;
import com.yuanqi.app.user.vo.UserProfileVO;
import com.yuanqi.app.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料与公开主页接口。
 */
@Tag(name = "2. 用户资料", description = "当前用户与公开主页资料")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "获取当前用户资料")
    @GetMapping("/me")
    public Result<UserVO> profile() {
        return Result.success(userService.getProfile(UserContext.getUserId()));
    }

    @Operation(summary = "修改当前用户资料", description = "支持邮箱、生日、性别、简介、头像")
    @PutMapping("/me")
    public Result<UserVO> updateProfile(@Valid @RequestBody UserRequests.UpdateProfile request) {
        return Result.success(userService.updateProfile(UserContext.getUserId(), request));
    }

    @Operation(summary = "修改当前用户密码")
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody UserRequests.ChangePassword request) {
        userService.changePassword(UserContext.getUserId(), request);
        return Result.success(null);
    }

    @Operation(summary = "获取用户公开主页资料")
    @GetMapping("/{uid}")
    public Result<UserProfileVO> publicProfile(@PathVariable String uid) {
        return Result.success(userService.getPublicProfile(uid));
    }

    @Operation(summary = "获取用户已发布作品墙")
    @GetMapping("/{uid}/photos")
    public Result<IPage<PhotoCardVO>> publicPhotos(
            @PathVariable String uid,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(userService.listPublicPhotos(uid, current, pageSize));
    }
}
