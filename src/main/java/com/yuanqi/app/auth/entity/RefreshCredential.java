package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_refresh_token")
public class RefreshCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tokenId;
    private Long sessionId;
    private String tokenHash;
    private Integer rotationNo;
    private Long parentTokenId;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
