package com.yuanqi.app.moderation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import com.yuanqi.app.photo.vo.WorkViews;

public final class ModerationViews {
    private ModerationViews() {}
    @Schema(name = "ModerationMutationResult")
    public record Mutation(String workId, String revisionId, String action,
                           String resultingPublicationState, String resultingWorkingState,
                           OffsetDateTime occurredAt, String versionTag) {}
    public record TargetSummary(String workId,String revisionId,String authorUid,String title,OffsetDateTime submittedAt,String versionTag){}
    @Schema(name = "ModerationTargetView")
    public record Target(String workId, WorkViews.Revision targetRevision,
                         WorkViews.PublicRevision currentPublicRevision, WorkViews.PublicAuthor author,
                         OffsetDateTime submittedAt, boolean selfReview, String versionTag) {}
    @Schema(name = "AdminPhotoSummary")
    public record AdminPhotoSummary(String workId,String publicationState,String workingRevisionId,
                                    String workingRevisionState,String publicRevisionId,
                                    java.util.List<String> allowedActions,String versionTag){}
    public record Event(String eventId,String revisionId,String action,String previousState,String resultingState,String submitterUid,String reviewerUid,String reason,boolean selfReview,OffsetDateTime occurredAt){}
}
