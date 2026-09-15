package com.personal.dashboard.catalog.dto;

import jakarta.validation.constraints.*;

/** Validated settings input, separate from the preferences response projection. */
public record PreferencesRequest(
    @NotNull @Pattern(regexp = "dark|light") String theme,
    boolean compact,
    @Min(10) @Max(24) int terminalFont,
    @Min(1) @Max(1440) int clipMinutes) {}
