package com.yuanqi.app.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class WorkRequests {
    private WorkRequests() {
    }

    @Schema(name = "WorkDraftRequest")
    public record Draft(@Size(max = 100) String title,
                        @Size(max = 5000) String description,
                        @Size(max = 100) String location,
                        String categoryId,
                        @Size(max = 5) List<@Size(min = 1, max = 20) String> tags,
                        @NotNull @Size(min = 1, max = 9) List<String> mediaIds) {
    }
}
