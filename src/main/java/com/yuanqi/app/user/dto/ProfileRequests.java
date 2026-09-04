package com.yuanqi.app.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class ProfileRequests {
    private ProfileRequests() {
    }

    @Schema(name = "UpdateProfileRequest")
    public record Update(@Size(min = 1, max = 20) String username,
                         @Size(max = 200) String bio,
                         LocalDate birthDate,
                         String gender) {
    }
}
