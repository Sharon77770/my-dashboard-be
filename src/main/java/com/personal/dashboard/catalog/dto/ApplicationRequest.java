package com.personal.dashboard.catalog.dto;

import jakarta.validation.constraints.*;

/** A named HTTP(S) destination opened by the server browser. */
public record ApplicationRequest(
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Size(max = 2048) String url,
    boolean pinned) {}
