package com.personal.dashboard.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

/** Only remote connection fields; unrelated SSH and jump configuration remains intact. */
public record DesktopConnectionRequest(
    @NotNull @Pattern(regexp = "RDP|VNC") String protocol,
    @Min(1) @Max(65535) int port,
    @Size(max = 128) String username,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 4096) String password) {
  @Override
  public String toString() {
    return "DesktopConnectionRequest[credentials redacted]";
  }
}
