package com.personal.dashboard.catalog.service;

import com.personal.dashboard.catalog.dto.DeviceStatus;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.CommandAdapter;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.file.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Performs real server measurements, Docker/GPU inspection and Wake-on-LAN requests. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class DeviceOperations {
  private final CatalogService catalog;
  private final CommandAdapter commands;

  public DeviceOperations(CatalogService catalog, CommandAdapter commands) {
    this.catalog = catalog;
    this.commands = commands;
  }

  public DeviceStatus status(String id) {
    var device = catalog.requireDevice(id);
    try {
      if (id.equals("local")) {
        var operatingSystem =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        var disk = Files.getFileStore(Path.of(device.rootPath()));
        double cpu = operatingSystem.getCpuLoad();
        return new DeviceStatus(
            "ONLINE",
            cpu < 0 ? null : cpu * 100,
            (1.0
                    - (double) operatingSystem.getFreeMemorySize()
                        / operatingSystem.getTotalMemorySize())
                * 100,
            (1.0 - (double) disk.getUsableSpace() / disk.getTotalSpace()) * 100,
            "대시보드 실행 환경",
            System.currentTimeMillis());
      }
      if (device.fingerprint().isBlank()) {
        try (Socket socket = new Socket()) {
          socket.connect(
              new InetSocketAddress(
                  catalog.connectionHost(device),
                  device.remoteProtocol().equals("NONE") ? device.sshPort() : device.remotePort()),
              2000);
        }
        return new DeviceStatus(
            "REACHABLE", null, null, null, "포트 응답 확인 · SSH 계측 미설정", System.currentTimeMillis());
      }
      String result =
          commands.execute(
              device,
              "LC_ALL=C; export LC_ALL; top -bn2 -d 0.2 | grep 'Cpu(s)' | tail -1 | awk '{print 100-$8}'; free | awk '/Mem:/{print $3/$2*100}'; df -P / | awk 'NR==2{gsub(/%/,\"\",$5);print $5}'");
      String[] lines = result.strip().split("\\R");
      return new DeviceStatus(
          "ONLINE",
          number(lines, 0),
          number(lines, 1),
          number(lines, 2),
          "SSH Linux 계측",
          System.currentTimeMillis());
    } catch (Exception exception) {
      return new DeviceStatus(
          "UNAVAILABLE",
          null,
          null,
          null,
          "연결 또는 계측 실패 · 장비 설정을 확인하세요",
          System.currentTimeMillis());
    }
  }

  private Double number(String[] lines, int index) {
    try {
      double value = Double.parseDouble(lines[index]);
      return Double.isFinite(value) && value >= 0 && value <= 100 ? value : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  public String inspect(String id, String kind) {
    return commands.execute(
        catalog.requireDevice(id),
        switch (kind) {
          case "docker" ->
              "docker ps -a --format 'table {{.ID}}\\t{{.Names}}\\t{{.Status}}\\t{{.Image}}'";
          case "gpu" -> "nvidia-smi";
          default -> throw new WorkspaceException(400, "지원하지 않는 조회입니다.");
        });
  }

  public String docker(String id, String container, String action) {
    if (!container.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")
        || !java.util.Set.of("start", "stop", "restart").contains(action))
      throw new WorkspaceException(400, "컨테이너와 동작을 확인해 주세요.");
    return commands.execute(catalog.requireDevice(id), "docker " + action + " " + container);
  }

  public void wake(String id) {
    var device = catalog.requireDevice(id);
    if (device.networkMode() == com.personal.dashboard.catalog.entity.NetworkMode.TAILSCALE)
      throw new WorkspaceException(
          400, "Tailscale 장비의 Wake-on-LAN은 지원하지 않습니다. 대상 LAN 안의 장비에서 실행해 주세요.");
    if (device.mac().isBlank() || device.broadcast().isBlank())
      throw new WorkspaceException(400, "장비의 MAC 주소와 브로드캐스트 주소를 설정해 주세요.");
    try (DatagramSocket socket = new DatagramSocket()) {
      byte[] mac =
          java.util.HexFormat.of().parseHex(device.mac().replace(":", "").replace("-", ""));
      byte[] packet = new byte[102];
      java.util.Arrays.fill(packet, 0, 6, (byte) 0xff);
      for (int index = 6; index < 102; index += 6) System.arraycopy(mac, 0, packet, index, 6);
      socket.setBroadcast(true);
      socket.send(
          new DatagramPacket(packet, packet.length, InetAddress.getByName(device.broadcast()), 9));
    } catch (Exception exception) {
      throw new WorkspaceException(502, "Wake 패킷 전송에 실패했습니다.");
    }
  }
}
