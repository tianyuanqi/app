package com.yuanqi.app.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthRequests {
    private AuthRequests() {
    }

    @Schema(name = "SendCodeRequest")
    public record SendCode(@NotBlank @Email @Size(max = 320) String email) {
    }

    @Schema(name = "RegisterRequest")
    public record Register(
            @NotBlank @Size(max = 32) String flowId,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String verificationCode,
            @jakarta.validation.constraints.NotNull UUID registrationAttemptId) {
    }

    @Schema(name = "LoginRequest")
    public record Login(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 64) String password) {
    }
}
