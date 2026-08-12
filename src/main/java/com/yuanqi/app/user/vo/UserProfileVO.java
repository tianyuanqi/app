package com.yuanqi.app.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户公开主页资料（不含邮箱、密码等敏感信息）。
 */
@Data
@Schema(description = "用户公开主页")
public class UserProfileVO {
    private String uid;
    private String username;
    private String bio;
    private String avatarUrl;
    /** 已发布作品数量 */
    private Long photoCount;
    private LocalDateTime joinedAt;
}
