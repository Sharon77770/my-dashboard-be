package com.personal.dashboard.catalog.dto;

import jakarta.validation.constraints.*;

/** Browser location selection input; no connection passwords are accepted by this contract. */
public record BrowserSettingsRequest(
    @NotNull @Pattern(regexp = "CLIENT|SERVER|REMOTE") String mode,
    @NotNull @Size(max = 80) String deviceId,
    @Min(1) @Max(65535) int debugPort) {}
