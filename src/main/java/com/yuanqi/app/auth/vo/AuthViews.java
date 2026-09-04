package com.yuanqi.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public final class AuthViews {
    private AuthViews() {
    }

    @Schema(name = "CurrentIdentity")
    public record CurrentIdentity(String uid, String username, Object avatar, String role) {
    }

    @Schema(name = "CsrfView")
    public record CsrfView(String cookieName, String headerName, OffsetDateTime expiresAt) {
    }

    @Schema(name = "SessionView")
    public record SessionView(String accessToken, String tokenType, OffsetDateTime accessTokenExpiresAt,
                              OffsetDateTime sessionExpiresAt, CurrentIdentity currentUser, CsrfView csrf) {
    }

    @Schema(name = "VerificationFlowView")
    public record VerificationFlowView(String flowId, OffsetDateTime expiresAt,
                                       OffsetDateTime resendAvailableAt, int attemptsRemaining) {
    }

    @Schema(name = "LogoutResult")
    public record LogoutResult(boolean loggedOut, boolean alreadyLoggedOut) {
    }
}
