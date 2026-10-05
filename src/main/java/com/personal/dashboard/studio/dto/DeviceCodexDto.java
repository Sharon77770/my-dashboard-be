package com.personal.dashboard.studio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** Device identity comes from the route, never from a second body field. */
public final class DeviceCodexDto {
  private DeviceCodexDto() {}

  public record Request(
      @NotBlank @Size(max = 4096) String root,
      @NotBlank @Size(max = 40) String action,
      @Valid StudioDto.Args args) {}
}
