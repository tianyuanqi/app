package com.yuanqi.app.moderation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public final class ModerationViews {
    private ModerationViews() {}
    @Schema(name = "ModerationMutationResult")
    public record Mutation(String workId, String revisionId, String action,
                           String resultingPublicationState, String resultingWorkingState,
                           OffsetDateTime occurredAt, String versionTag) {}
    public record TargetSummary(String workId,String revisionId,String authorUid,String title,OffsetDateTime submittedAt,String versionTag){}
    public record Target(String workId,String revisionId,String authorUid,String title,String description,String location,java.util.List<String> mediaIds,OffsetDateTime submittedAt,boolean selfReview,String versionTag){}
    @Schema(name = "AdminPhotoSummary")
    public record AdminPhotoSummary(String workId,String publicationState,String workingRevisionId,
                                    String workingRevisionState,String publicRevisionId,
                                    java.util.List<String> allowedActions,String versionTag){}
    public record Event(String eventId,String revisionId,String action,String previousState,String resultingState,String submitterUid,String reviewerUid,String reason,boolean selfReview,OffsetDateTime occurredAt){}
}
