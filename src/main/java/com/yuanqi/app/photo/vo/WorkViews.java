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

    @Schema(name = "AuthorRevisionView")
    public record Revision(String revisionId, int revisionNumber, String state, String origin,
                           String title, String description, String location,
                           List<String> mediaIds, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                           OffsetDateTime submittedAt, String versionTag) {
    }

    @Schema(name = "AuthorWorkSummary")
    public record Summary(String workId, String publicationState, String workingRevisionId,
                          String workingRevisionState, OffsetDateTime publishedAt, OffsetDateTime updatedAt,
                          long likeCount, long commentCount, Capabilities capabilities, String versionTag) {
    }

    @Schema(name = "AuthorWorkView")
    public record AuthorWork(Summary summary, Revision currentPublicRevision, Revision workingRevision) {
    }

    @Schema(name = "PhotoDeleteResult")
    public record DeleteResult(String workId, boolean deleted, OffsetDateTime deletedAt) {
    }
}
