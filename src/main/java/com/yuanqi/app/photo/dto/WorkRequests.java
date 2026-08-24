package com.yuanqi.app.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class WorkRequests {
    private WorkRequests() {
    }

    @Schema(name = "WorkDraftRequest")
    public record Draft(@Size(max = 512) String title,
                        @Size(max = 20000) String description,
                        @Size(max = 512) String location,
                        String categoryId,
                        List<@Size(max = 128) String> tags,
                        List<String> mediaIds) {
    }
}
