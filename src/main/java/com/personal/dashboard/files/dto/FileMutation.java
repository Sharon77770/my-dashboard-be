package com.personal.dashboard.files.dto;

import jakarta.validation.constraints.*;

/** A file location and optional new basename. */
public record FileMutation(@NotBlank @Size(max = 1024) String path, @Size(max = 255) String name) {}
