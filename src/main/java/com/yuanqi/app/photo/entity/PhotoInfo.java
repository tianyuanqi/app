package com.yuanqi.app.photo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品实体，对应表 photo_info。
 * <p>部分字段名保持与历史库列一致（shoot_date / category_id）。</p>
 */
@Data
@Schema(description = "照片详细信息实体")
@TableName("photo_info")
public class PhotoInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String location;
    private String title;
    private String description;
    private String imageUrl;
    private String cameraBody;
    private String lens;
    private String focalLength;
    private String aperture;
    private String shutterSpeed;
    private Integer iso;
    private LocalDateTime createTime;

    /** 拍摄时间列名 shoot_date */
    private LocalDateTime shoot_date;

    /** 分类 ID 列名 category_id */
    private Integer category_id;

    /** 发布状态：DRAFT/PENDING/PUBLISHED/REJECTED/OFFLINE */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    private LocalDateTime reviewedAt;
    private Long reviewedBy;
}
