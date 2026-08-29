package com.yuanqi.app.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.OffsetDateTime;
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
                        @NotNull @Size(min = 1, max = 9) List<String> mediaIds,
                        @Size(max = 9) List<@Valid MediaParameters> mediaParameters) {
        public Draft(String title, String description, String location, String categoryId,
                     List<String> tags, List<String> mediaIds) {
            this(title, description, location, categoryId, tags, mediaIds, null);
        }
    }

    @Schema(name = "PhotoParametersInput", description = "单张媒体的手工拍摄参数；字段为 null 表示清空")
    public record PhotoParameters(
            @Schema(type = "string", format = "date-time", example = "2026-08-28T10:30:00+08:00")
            OffsetDateTime captureTime,
            @Schema(maxLength = 100) String cameraBody,
            @Schema(maxLength = 100) String lens,
            @Schema(maxLength = 50) String focalLength,
            @Schema(maxLength = 50) String aperture,
            @Schema(maxLength = 50) String shutterSpeed,
            @Schema(maxLength = 50) String iso) {
    }

    @Schema(name = "RevisionMediaParametersInput")
    public record MediaParameters(@NotBlank String mediaId, @NotNull @Valid PhotoParameters parameters) {
    }
}
