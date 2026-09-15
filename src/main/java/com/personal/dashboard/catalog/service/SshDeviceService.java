package com.personal.dashboard.catalog.service;

import com.personal.dashboard.catalog.dto.*;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Enrolls a device after verifying SSH credentials and discovering its SFTP home. */
@Service
public class SshDeviceService {
  private final CatalogService catalog;
  private final SshAdapter ssh;
  private final com.personal.dashboard.global.integration.DeviceNetworkAdapter network;

  public SshDeviceService(
      CatalogService catalog,
      SshAdapter ssh,
      com.personal.dashboard.global.integration.DeviceNetworkAdapter network) {
    this.catalog = catalog;
    this.ssh = ssh;
    this.network = network;
  }

  public record Target(String username, String host, int port) {}

  /** Parse supported SSH syntax as data, never as a shell command. */
  static Target parse(String command) {
    if (command == null || command.isBlank() || command.length() > 512) throw invalid();
    String[] tokens = command.trim().split("\\s+");
    if (!tokens[0].equals("ssh")) throw invalid();
    String destination = null;
    int port = 22;
    boolean portSeen = false;
    for (int index = 1; index < tokens.length; index++) {
      String token = tokens[index];
      if (token.startsWith("-p")) {
        if (portSeen) throw invalid();
        portSeen = true;
        String value = token.substring(2);
        if (value.isEmpty()) {
          if (++index >= tokens.length) throw invalid();
          value = tokens[index];
        }
        if (!value.matches("[0-9]{1,5}")) throw invalid();
        port = Integer.parseInt(value);
        if (port < 1 || port > 65535) throw invalid();
      } else {
        if (destination != null) throw invalid();
        destination = token;
      }
    }
    if (destination == null) throw invalid();
    String[] parts = destination.split("@", -1);
    if (parts.length != 2 || !parts[0].matches("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}"))
      throw invalid();
    String host = parts[1];
    if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
    if (host.length() > 253 || !host.matches("[a-zA-Z0-9][a-zA-Z0-9.:-]*|:[a-fA-F0-9:]+"))
      throw invalid();
    return new Target(parts[0], host, port);
  }

  private static WorkspaceException invalid() {
    return new WorkspaceException(
        400, "ssh 사용자@호스트 또는 ssh -p 2222 사용자@호스트 형식으로 입력해 주세요. 현재는 -p 옵션과 비밀번호 인증을 지원합니다.");
  }

  @PreAuthorize("hasRole('OWNER')")
  public synchronized DeviceView connect(SshDeviceRequest request) {
    Target target = parse(request.command());
    var matching =
        catalog.devices().stream()
            .filter(
                device ->
                    !device.id().equals("local")
                        && device.host().equalsIgnoreCase(target.host())
                        && device.sshPort() == target.port())
            .toList();
    var fingerprints =
        matching.stream()
            .map(DeviceView::fingerprint)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    if (fingerprints.size() > 1)
      throw new WorkspaceException(409, "같은 호스트의 저장된 SSH 키가 서로 다릅니다. 기존 장비 설정을 확인해 주세요.");
    var existing =
        matching.stream()
            .filter(device -> device.username().equals(target.username()))
            .findFirst()
            .orElse(null);
    var mode =
        request.networkMode() == null
            ? (existing == null
                ? com.personal.dashboard.catalog.entity.NetworkMode.DIRECT
                : existing.networkMode())
            : request.networkMode();
    var discovered =
        ssh.discover(
            network.resolve(target.host(), mode),
            target.port(),
            target.username(),
            request.password(),
            fingerprints.isEmpty() ? "" : fingerprints.getFirst());
    String name = request.name() == null ? "" : request.name().trim();
    if (name.isEmpty())
      name = existing == null ? target.username() + "@" + target.host() : existing.name();
    if (name.length() > 80) name = name.substring(0, 80);
    return catalog.saveDevice(
        existing == null ? null : existing.id(),
        new DeviceRequest(
            name,
            target.host(),
            target.port(),
            target.username(),
            request.password(),
            discovered.fingerprint(),
            existing == null ? discovered.rootPath() : existing.rootPath(),
            existing == null ? "NONE" : existing.remoteProtocol(),
            existing == null ? 3389 : existing.remotePort(),
            existing == null ? "" : existing.remoteUsername(),
            "",
            existing == null ? "" : existing.mac(),
            existing == null ? "" : existing.broadcast(),
            existing == null || existing.pinned(),
            mode));
  }
}
