package com.yuanqi.app.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("account_governance_event")
public class AccountGovernanceEvent {
    @TableId(type=IdType.AUTO) private Long id; private String publicId; private Long targetAccountId;
    private Long actorAccountId; private String action; private String reason; private String previousStatus;
    private String resultingStatus; private LocalDateTime occurredAt;
}
