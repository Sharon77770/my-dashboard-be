package com.personal.dashboard.catalog.entity;

/** SQLite connection profile; encrypted fields are never serialized as an API response. */
public record DeviceRecord(
    String id,
    String name,
    String host,
    int sshPort,
    String username,
    String passwordCipher,
    String fingerprint,
    String rootPath,
    String remoteProtocol,
    int remotePort,
    String remoteUsername,
    String remotePasswordCipher,
    String mac,
    String broadcast,
    boolean pinned,
    NetworkMode networkMode) {
  public DeviceRecord(
      String id,
      String name,
      String host,
      int sshPort,
      String username,
      String passwordCipher,
      String fingerprint,
      String rootPath,
      String remoteProtocol,
      int remotePort,
      String remoteUsername,
      String remotePasswordCipher,
      String mac,
      String broadcast,
      boolean pinned) {
    this(
        id,
        name,
        host,
        sshPort,
        username,
        passwordCipher,
        fingerprint,
        rootPath,
        remoteProtocol,
        remotePort,
        remoteUsername,
        remotePasswordCipher,
        mac,
        broadcast,
        pinned,
        NetworkMode.DIRECT);
  }

  @Override
  public String toString() {
    return "DeviceRecord[credentials redacted]";
  }
}
