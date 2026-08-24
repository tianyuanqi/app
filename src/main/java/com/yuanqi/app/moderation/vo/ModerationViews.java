package com.yuanqi.app.moderation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public final class ModerationViews {
    private ModerationViews() {}
    @Schema(name = "ModerationMutationResult")
    public record Mutation(String workId, String revisionId, String action,
                           String resultingPublicationState, String resultingWorkingState,
                           OffsetDateTime occurredAt, String versionTag) {}
}
