package com.yuanqi.app.photo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("photo_work")
public class PhotoWork {
    @TableId(type = IdType.AUTO) private Long id;
    private String workId;
    private Long authorAccountId;
    private String publicationState;
    private Long publicRevisionId;
    private Long workingRevisionId;
    private LocalDateTime publishedAt;
    private Long rowVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
