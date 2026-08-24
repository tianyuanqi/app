package com.yuanqi.app.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("photo_comment")
public class PhotoComment {
    @TableId(type = IdType.AUTO) private Long id;
    private String commentId;
    private Long workId;
    private Long authorAccountId;
    private Long rootCommentId;
    private String content;
    private String displayState;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    private Long rowVersion;
}
