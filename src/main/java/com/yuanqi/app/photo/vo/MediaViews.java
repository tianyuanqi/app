package com.yuanqi.app.photo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public final class MediaViews {
    private MediaViews() {}

    @Schema(name = "WebMediaRef")
    public record WebMedia(String mediaId, String accessMode, String url, String mimeType,
                           Integer width, Integer height, String etag) {}

    @Schema(name = "MediaFailure")
    public record Failure(String code, String message) {}

    @Schema(name = "MediaWarning")
    public record Warning(
            @Schema(allowableValues = {"EXIF_PARSE_FAILED", "EXIF_CAPTURE_TIME_IN_FUTURE", "EXIF_FIELD_IGNORED"})
            String code,
            @Schema(allowableValues = {"captureTime", "cameraBody", "lens", "focalLength", "aperture",
                    "shutterSpeed", "iso"})
            String field,
            String message) {}

    @Schema(name = "MediaProcessingView")
    public record Processing(String mediaId, String clientUploadId, String status, Long byteSize,
                             Integer width, Integer height, WebMedia web, WorkViews.PhotoParameters exifCandidate,
                             List<Warning> warnings, Failure failure, boolean retryable,
                             OffsetDateTime retryUntil, String versionTag) {}

    @Schema(name = "MediaDeleteResult")
    public record DeleteResult(String mediaId, boolean deleted, String status) {}
}
