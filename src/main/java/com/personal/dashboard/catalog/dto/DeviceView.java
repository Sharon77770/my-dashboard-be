package com.personal.dashboard.catalog.dto;

import com.personal.dashboard.catalog.entity.NetworkMode;

/** Safe profile projection; only password-presence flags are exposed. */
public record DeviceView(
    String id,
    String name,
    String host,
    int sshPort,
    String username,
    boolean hasPassword,
    String fingerprint,
    String rootPath,
    String remoteProtocol,
    int remotePort,
    String remoteUsername,
    boolean hasRemotePassword,
    String mac,
    String broadcast,
    boolean pinned,
    NetworkMode networkMode) {
  public DeviceView(
      String id,
      String name,
      String host,
      int sshPort,
      String username,
      boolean hasPassword,
      String fingerprint,
      String rootPath,
      String remoteProtocol,
      int remotePort,
      String remoteUsername,
      boolean hasRemotePassword,
      String mac,
      String broadcast,
      boolean pinned) {
    this(
        id,
        name,
        host,
        sshPort,
        username,
        hasPassword,
        fingerprint,
        rootPath,
        remoteProtocol,
        remotePort,
        remoteUsername,
        hasRemotePassword,
        mac,
        broadcast,
        pinned,
        NetworkMode.DIRECT);
  }
}
