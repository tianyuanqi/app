package com.yuanqi.app.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ReviewRequests {
    private ReviewRequests() {}
    public record Reason(@NotBlank @Size(max = 1000) String reason) {}
    public record Delete(@NotBlank @Size(max = 1000) String reason, boolean confirmation) {}
}
