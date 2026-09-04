package com.yuanqi.app.photo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 照片分类实体。
 */
@Data
@Schema(description = "照片分类实体")
@TableName("photo_category")
public class PhotoCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String publicId;
    private String name;
    private String normalizedName;
    private Boolean active;
    private Integer sortOrder;
    private String source;

    @Deprecated public int getSortorder() { return sortOrder == null ? 0 : sortOrder; }
}
