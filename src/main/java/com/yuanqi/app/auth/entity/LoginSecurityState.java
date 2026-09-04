package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("login_security_state")
public class LoginSecurityState {
    @TableId
    private Long accountId;
    private Integer failedCount;
    private LocalDateTime windowStartedAt;
    private LocalDateTime lockedUntil;
    private Long rowVersion;
}
