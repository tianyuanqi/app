package com.yuanqi.app.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rate_limit_bucket")
public class RateLimitBucket {
    private String bucketKey;
    private String actionType;
    private LocalDateTime windowStartedAt;
    private Integer windowSeconds;
    private Integer requestCount;
    private LocalDateTime updatedAt;
}
