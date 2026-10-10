package com.personal.dashboard.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Optional installation credential exists only for the lifetime of the setup job. */
public record DesktopSetupRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Size(max = 4096)
        @Pattern(regexp = "[^\\r\\n]*")
        String sudoPassword) {
  @Override
  public String toString() {
    return "DesktopSetupRequest[credentials redacted]";
  }
}
