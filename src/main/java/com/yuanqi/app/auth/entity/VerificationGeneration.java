package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_verification_generation")
public class VerificationGeneration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private Integer generation;
    private String codeHmac;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
