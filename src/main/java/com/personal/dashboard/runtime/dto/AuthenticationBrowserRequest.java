package com.personal.dashboard.runtime.dto;

import jakarta.validation.constraints.*;

/** Opens an account page, a device authorization link, or a registered web application. */
public record AuthenticationBrowserRequest(
    @NotNull @Pattern(regexp = "BROWSER|GOOGLE|GITHUB|CODEX|APP") String provider,
    @Size(max = 8192) String url,
    @Size(max = 200) String applicationId,
    @Min(320) @Max(3840) int width,
    @Min(240) @Max(2160) int height) {}
