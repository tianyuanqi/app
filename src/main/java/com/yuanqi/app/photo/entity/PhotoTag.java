package com.yuanqi.app.photo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 照片标签实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "照片标签实体")
@TableName("photo_tag")
public class PhotoTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tagId;
    private String displayName;
    private String normalizedName;
    private java.time.LocalDateTime createdAt;

    @Deprecated public String getName() { return displayName; }
    @Deprecated public void setName(String name) { this.displayName = name; }
}
