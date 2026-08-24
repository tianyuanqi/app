package com.yuanqi.app.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfile {
    @TableId
    private Long accountId;
    private String username;
    private String bio;
    private LocalDate birthDate;
    private String gender;
    private Long avatarMediaId;
    private Long rowVersion;
    private LocalDateTime updatedAt;
}
