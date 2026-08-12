package com.yuanqi.app.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

/**
 * 用户资料相关请求 DTO。
 */
public final class UserRequests {

    private UserRequests() {
    }

    @Data
    public static class ChangePassword {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "新密码长度必须为8到72个字符")
        private String newPassword;
    }

    @Data
    public static class UpdateProfile {
        @Email(message = "邮箱格式不正确")
        private String email;
        private Date birth;
        private Integer gender;

        @Size(max = 500, message = "简介不能超过500个字符")
        private String bio;

        @Size(max = 512, message = "头像地址过长")
        private String avatarUrl;
    }
}
