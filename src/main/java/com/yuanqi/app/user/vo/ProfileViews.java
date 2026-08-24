package com.yuanqi.app.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class ProfileViews {
    private ProfileViews() {
    }

    @Schema(name = "PrivateProfileView")
    public record PrivateProfile(String uid, String email, String username, String bio,
                                 LocalDate birthDate, String gender, Object avatar,
                                 OffsetDateTime joinedAt, String versionTag) {
    }

    @Schema(name = "PublicProfileView")
    public record PublicProfile(String uid, String username, String bio, Integer age, String gender,
                                Object avatar, OffsetDateTime joinedAt,
                                long publicPhotoCount, long receivedLikeCount) {
    }
}
