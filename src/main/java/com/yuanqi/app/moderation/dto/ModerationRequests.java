package com.yuanqi.app.moderation.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核操作请求。
 */
public final class ModerationRequests {

    private ModerationRequests() {
    }

    @Data
    public static class Reject {
        @Size(max = 512, message = "驳回原因不能超过512个字符")
        private String reason;
    }
}
