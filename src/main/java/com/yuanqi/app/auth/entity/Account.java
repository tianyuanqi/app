package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class Account {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uid;
    private String email;
    private String emailKey;
    private String passwordHash;
    private String role;
    private String governanceStatus;
    private Long rowVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
