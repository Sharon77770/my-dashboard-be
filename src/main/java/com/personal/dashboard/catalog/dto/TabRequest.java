package com.personal.dashboard.catalog.dto;

import jakarta.validation.constraints.*;

/** Serializable navigation descriptor; live runtime handles are never persisted. */
public record TabRequest(
    @NotBlank @Size(max = 80) String id,
    @Pattern(regexp = "FILES|TERMINAL|REMOTE|APP|DOCKER|GPU|DESKTOP") @NotNull String kind,
    @NotBlank @Size(max = 80) String targetId,
    @NotNull @Size(max = 1024) String path,
    @NotBlank @Size(max = 120) String title,
    boolean pinned) {}
