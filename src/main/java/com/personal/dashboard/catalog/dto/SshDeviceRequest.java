package com.personal.dashboard.catalog.dto;

import com.personal.dashboard.catalog.entity.NetworkMode;
import jakarta.validation.constraints.*;

/** Command-shaped connection input; credentials never appear in diagnostic output. */
public record SshDeviceRequest(
    @NotBlank @Size(max = 512) String command,
    @NotBlank @Size(max = 4096) String password,
    @Size(max = 80) String name,
    NetworkMode networkMode) {
  public SshDeviceRequest(String command, String password, String name) {
    this(command, password, name, NetworkMode.DIRECT);
  }

  @Override
  public String toString() {
    return "SshDeviceRequest[credentials redacted]";
  }
}
