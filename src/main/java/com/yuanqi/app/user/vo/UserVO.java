package com.yuanqi.app.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 对外用户资料（不含密码）。
 */
@Data
@Schema(description = "用户资料")
public class UserVO {
    private String uid;
    private String username;
    private Date birth;
    private Integer gender;
    private String email;
    private String bio;
    private String avatarUrl;
}
