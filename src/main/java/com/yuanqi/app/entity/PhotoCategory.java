package com.yuanqi.app.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "照片分类实体类")
@Data
@TableName("photo_category")
public class PhotoCategory {

    @Schema(description = "照片分类唯一ID")
    private Long id;

    @Schema(description = "分类名称",example = "建筑")
    private String name;

    @Schema(description = "分类优先级",example = "1")
    private int sortorder;
}
