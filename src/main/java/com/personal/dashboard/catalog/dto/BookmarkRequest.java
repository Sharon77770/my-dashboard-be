package com.personal.dashboard.catalog.dto;

import jakarta.validation.constraints.*;

/** A device-relative file location. */
public record BookmarkRequest(@NotBlank String deviceId, @NotBlank @Size(max = 1024) String path) {}
