package com.personal.dashboard.catalog.dto;

import com.personal.dashboard.catalog.entity.NetworkMode;
import jakarta.validation.constraints.*;
import java.util.List;

/** Password blanks preserve existing credentials; fingerprints pin SSH host identity. */
public record DeviceRequest(
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Size(max = 253) String host,
    @Min(1) @Max(65535) int sshPort,
    @Size(max = 128) String username,
    @Size(max = 4096) String password,
    @Size(max = 120) String fingerprint,
    @NotBlank @Size(max = 1024) String rootPath,
    @Pattern(regexp = "NONE|RDP|VNC") @NotNull String remoteProtocol,
    @Min(1) @Max(65535) int remotePort,
    @Size(max = 128) String remoteUsername,
    @Size(max = 4096) String remotePassword,
    @Size(max = 17) String mac,
    @Size(max = 253) String broadcast,
    boolean pinned,
    NetworkMode networkMode,
    List<@Size(max = 80) String> jumpDeviceIds) {
  public DeviceRequest(
      String name,
      String host,
      int sshPort,
      String username,
      String password,
      String fingerprint,
      String rootPath,
      String remoteProtocol,
      int remotePort,
      String remoteUsername,
      String remotePassword,
      String mac,
      String broadcast,
      boolean pinned) {
    this(
        name,
        host,
        sshPort,
        username,
        password,
        fingerprint,
        rootPath,
        remoteProtocol,
        remotePort,
        remoteUsername,
        remotePassword,
        mac,
        broadcast,
        pinned,
        NetworkMode.DIRECT,
        List.of());
  }

  public DeviceRequest(
      String name,
      String host,
      int sshPort,
      String username,
      String password,
      String fingerprint,
      String rootPath,
      String remoteProtocol,
      int remotePort,
      String remoteUsername,
      String remotePassword,
      String mac,
      String broadcast,
      boolean pinned,
      NetworkMode networkMode) {
    this(
        name,
        host,
        sshPort,
        username,
        password,
        fingerprint,
        rootPath,
        remoteProtocol,
        remotePort,
        remoteUsername,
        remotePassword,
        mac,
        broadcast,
        pinned,
        networkMode,
        List.of());
  }

  @Override
  public String toString() {
    return "DeviceRequest[credentials redacted]";
  }
}
