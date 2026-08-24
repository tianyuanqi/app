package com.yuanqi.app.photo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_asset")
public class MediaAsset {
    @TableId(type = IdType.AUTO) private Long id;
    private String mediaId;
    private String clientUploadId;
    private Long ownerAccountId;
    private String purpose;
    private String originalStorageKey;
    private String webStorageKey;
    private String sha256;
    private String mimeType;
    private Long byteSize;
    private Integer width;
    private Integer height;
    private Integer frameCount;
    private String status;
    private String failureCode;
    private Boolean retryable;
    private LocalDateTime retryUntil;
    private Long rowVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
