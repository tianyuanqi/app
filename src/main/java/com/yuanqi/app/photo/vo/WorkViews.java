package com.yuanqi.app.photo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public final class WorkViews {
    private WorkViews() {
    }

    @Schema(name = "WorkCapabilities")
    public record Capabilities(boolean canCreateDraft, boolean canEditDraft, boolean canSubmit,
                               boolean canWithdraw, boolean canDelete) {
    }

    @Schema(name = "PublicAuthorView")
    public record PublicAuthor(String uid, String username, MediaViews.WebMedia avatar) {
    }

    @Schema(name = "CategoryView")
    public record Category(String categoryId, String name, boolean selectable, boolean filterable) {
    }

    @Schema(name = "TagView")
    public record Tag(String tagId, String name) {
    }

    @Schema(name = "PhotoParameters")
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

    @Schema(name = "RevisionMediaView")
    public record RevisionMedia(String mediaId, int position, boolean cover, MediaViews.WebMedia web,
                                PhotoParameters parameters) {
    }

    @Schema(name = "AuthorRevisionView")
    public record Revision(String revisionId, int revisionNumber, String state, String origin,
                           String title, String description, String location,
                           Category category, List<Tag> tags, List<RevisionMedia> media,
                           OffsetDateTime createdAt, OffsetDateTime updatedAt,
                           OffsetDateTime submittedAt, String versionTag) {
    }

    @Schema(name = "PublicRevisionSummary")
    public record PublicRevision(String revisionId, int revisionNumber, String title, String description,
                                 String location, Category category, List<Tag> tags,
                                 List<RevisionMedia> media, OffsetDateTime publishedAt) {
    }

    @Schema(name = "AuthorWorkSummary")
    public record Summary(String workId, String publicationState, String workingRevisionId,
                          String workingRevisionState, OffsetDateTime publishedAt, OffsetDateTime updatedAt,
                          long likeCount, long commentCount, Capabilities capabilities, String versionTag) {
    }

    @Schema(name = "AuthorWorkView")
    public record AuthorWork(Summary summary, PublicRevision currentPublicRevision, Revision workingRevision) {
    }

    @Schema(name = "PhotoDeleteResult")
    public record DeleteResult(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean deleted,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime deletedAt) {
    }
}
