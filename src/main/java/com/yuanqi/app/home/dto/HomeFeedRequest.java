package com.yuanqi.app.home.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 发现流查询参数。
 */
@Data
public class HomeFeedRequest {

    @Min(value = 1, message = "页码必须大于0")
    private Integer current = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    private Integer pageSize = 10;

    private Long categoryId;
    private String tag;
    private String keyword;
    /** latest | hot；hot 暂按创建时间降序（点赞落地后可替换） */
    private String sort = "latest";

    public int safePageSize() {
        return Math.min(pageSize == null ? 10 : pageSize, 100);
    }
}
