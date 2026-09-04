package com.yuanqi.app.photo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("photo_revision")
public class PhotoRevision {
    @TableId(type = IdType.AUTO) private Long id;
    private String revisionId;
    private Long workId;
    private Integer revisionNumber;
    private String state;
    private String origin;
    private String title;
    private String description;
    private String location;
    private Long categoryId;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long rowVersion;
}
