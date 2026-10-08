package com.personal.dashboard.studio.dto;

import jakarta.validation.constraints.*;

public final class StudioBrowserDto {
  private StudioBrowserDto() {}

  public record Input(
      @NotBlank @Size(max = 100) String deviceId,
      @NotBlank @Size(max = 4096) String root,
      @NotBlank @Size(max = 20) String action,
      @Size(max = 2048) String url,
      @Size(max = 4000) String text,
      @Min(0) @Max(1200) Integer x,
      @Min(0) @Max(720) Integer y,
      @Min(-3000) @Max(3000) Integer delta) {}
}
