package com.personal.dashboard.runtime.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Requested viewport in CSS pixels; the server browser profile is never returned. */
public record ChromeSessionRequest(
    @Min(320) @Max(3840) int width, @Min(240) @Max(2160) int height) {}
