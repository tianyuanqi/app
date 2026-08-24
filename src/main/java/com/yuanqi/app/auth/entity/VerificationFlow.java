package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_verification_flow")
public class VerificationFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowId;
    private String emailKey;
    private String emailAddress;
    private String purpose;
    private String status;
    private Integer failedAttempts;
    private Integer activeGeneration;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private Long rowVersion;
}
