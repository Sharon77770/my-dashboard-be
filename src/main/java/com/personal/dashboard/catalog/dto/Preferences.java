package com.personal.dashboard.catalog.dto;

import jakarta.validation.constraints.*;

/** Server-persisted presentation preferences used by all workspace pages. */
public record Preferences(
    @NotNull @Pattern(regexp = "dark|light") String theme,
    boolean compact,
    @Min(10) @Max(24) int terminalFont,
    @Min(1) @Max(1440) int clipMinutes) {}
