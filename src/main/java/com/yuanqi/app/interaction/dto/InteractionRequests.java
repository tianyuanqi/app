package com.yuanqi.app.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class InteractionRequests {
    private InteractionRequests() {}
    public record Comment(@NotBlank @Size(max = 4096) String content) {}
    public record AdminDelete(@NotBlank @Size(max = 1000) String reason) {}
}
