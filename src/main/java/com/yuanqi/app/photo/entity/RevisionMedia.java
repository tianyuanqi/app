package com.yuanqi.app.photo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("revision_media")
public class RevisionMedia {
    private Long revisionId;
    private Long mediaId;
    private Integer position;
    private LocalDateTime captureTime;
    private String cameraBody;
    private String lens;
    private String focalLength;
    private String aperture;
    private String shutterSpeed;
    private String isoValue;
    private String parameterSource;
    private String warningCodes;
}
