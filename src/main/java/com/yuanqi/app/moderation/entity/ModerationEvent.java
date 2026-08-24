package com.yuanqi.app.moderation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("moderation_event")
public class ModerationEvent {
    @TableId(type = IdType.AUTO) private Long id;
    private String eventId;
    private Long workId;
    private Long revisionId;
    private String action;
    private String previousState;
    private String resultingState;
    private Long submitterAccountId;
    private Long reviewerAccountId;
    private String reason;
    private Boolean selfReview;
    private LocalDateTime occurredAt;
}
