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

    private String name;

    /** 排序权重，列名 sortorder */
    private int sortorder;
}
