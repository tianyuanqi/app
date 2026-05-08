package com.yuanqi.app.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "首页展示照片实体信息")
public class PhotoVO {
    private Long id;
    private String title;
    private String imageUrl;
    private String description;
    private LocalDateTime createTime;

    // 以下是需要“组装”的字段
    private String categoryName; // 从 categoryId 转化而来
    private List<String> tagNames; // 从中间表和标签表组装而来

    // 拍摄信息（可以根据首页需求决定是否保留）
    private String cameraBody;
}