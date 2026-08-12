package com.yuanqi.app.photo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 分类对外视图。
 */
@Data
@Schema(description = "照片分类")
public class CategoryVO {
    private Long id;
    private String name;
    private Integer sortOrder;
}
