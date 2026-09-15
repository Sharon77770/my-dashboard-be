package com.personal.dashboard.catalog.dto;

import jakarta.validation.constraints.*;

/** Explicit app-launch selection, independent from server file/terminal execution. */
public record BrowserSettings(
    @NotNull @Pattern(regexp = "CLIENT|SERVER|REMOTE") String mode,
    @NotNull @Size(max = 80) String deviceId,
    @Min(1) @Max(65535) int debugPort) {}
