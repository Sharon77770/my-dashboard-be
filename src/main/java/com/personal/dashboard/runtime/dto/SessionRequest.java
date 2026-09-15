package com.personal.dashboard.runtime.dto;

import jakarta.validation.constraints.*;

/** A server-owned runtime resource requested with CSRF protection. */
public record SessionRequest(
    @NotNull @Pattern(regexp = "TERMINAL|REMOTE|APP|DESKTOP") String kind,
    @NotBlank String targetId,
    @Min(320) @Max(3840) int width,
    @Min(240) @Max(2160) int height) {}
