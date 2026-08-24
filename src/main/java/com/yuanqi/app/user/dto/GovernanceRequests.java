package com.yuanqi.app.user.dto;
import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.Size;
public final class GovernanceRequests{private GovernanceRequests(){} public record Reason(@NotBlank @Size(max=1000) String reason){}}
