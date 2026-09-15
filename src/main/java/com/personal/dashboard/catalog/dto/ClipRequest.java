package com.personal.dashboard.catalog.dto;

import jakarta.validation.constraints.*;

/** Explicitly stored text with bounded expiry. */
public record ClipRequest(
    @NotBlank @Size(max = 32000) String content, @Min(1) @Max(1440) int minutes) {}
