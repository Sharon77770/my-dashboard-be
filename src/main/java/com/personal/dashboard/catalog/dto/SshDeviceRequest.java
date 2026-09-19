package com.personal.dashboard.catalog.dto;

import com.personal.dashboard.catalog.entity.NetworkMode;
import jakarta.validation.constraints.*;
import java.util.List;

/** Command-shaped connection input; credentials never appear in diagnostic output. */
public record SshDeviceRequest(
    @NotBlank @Size(max = 512) String command,
    @NotBlank @Size(max = 4096) String password,
    @Size(max = 80) String name,
    NetworkMode networkMode,
    List<@Size(max = 80) String> jumpDeviceIds) {
  public SshDeviceRequest(String command, String password, String name) {
    this(command, password, name, NetworkMode.DIRECT, List.of());
  }

  public SshDeviceRequest(String command, String password, String name, NetworkMode networkMode) {
    this(command, password, name, networkMode, List.of());
  }

  @Override
  public String toString() {
    return "SshDeviceRequest[credentials redacted]";
  }
}
