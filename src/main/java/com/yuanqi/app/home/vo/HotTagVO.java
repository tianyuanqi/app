package com.yuanqi.app.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 热门标签项。
 */
@Data
@Schema(description = "热门标签")
public class HotTagVO {
    private Long id;
    private String name;
    private Long photoCount;
}
