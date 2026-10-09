package com.personal.dashboard.database.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.database.dto.DatabaseDto;
import com.personal.dashboard.database.entity.DatabaseConnection;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.CommandAdapter;
import com.personal.dashboard.global.integration.SshAdapter;
import java.net.*;
import java.util.*;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Component;

/** Resolves registered targets and owns one loopback SSH tunnel per JDBC connection. */
@Component
public class DatabaseTargetAdapter {
  public static final String DIRECT = "DIRECT", DEVICE = "DEVICE", DOCKER = "DOCKER";
  private final CatalogService catalog;
  private final CommandAdapter commands;
  private final SshAdapter ssh;
  private final ObjectMapper json;

  public DatabaseTargetAdapter(
      CatalogService catalog, CommandAdapter commands, SshAdapter ssh, ObjectMapper json) {
    this.catalog = catalog;
    this.commands = commands;
    this.ssh = ssh;
    this.json = json;
  }

  public void validate(String mode, String deviceId, String containerId, String type) {
    if (!Set.of(DIRECT, DEVICE, DOCKER).contains(mode))
      throw new WorkspaceException(400, "연결 방식을 확인해 주세요.");
    if (DIRECT.equals(mode)) {
      if (!deviceId.isEmpty() || !containerId.isEmpty())
        throw new WorkspaceException(400, "직접 연결에는 장비나 컨테이너를 지정하지 않습니다.");
      return;
    }
    if (type.equals("SQLITE"))
      throw new WorkspaceException(
          400, "장비 연결은 PostgreSQL/MySQL/MariaDB를 지원합니다. SQLite는 서버 파일 연결을 사용하세요.");
    var device = catalog.requireDevice(deviceId);
    if (!device.id().equals("local") && device.fingerprint().isBlank())
      throw new WorkspaceException(400, "SSH 인증과 호스트 지문이 등록된 장비를 선택하세요.");
    if (DOCKER.equals(mode)) requireContainer(containerId);
    else if (!containerId.isEmpty()) throw new WorkspaceException(400, "장비 연결에는 컨테이너를 지정하지 않습니다.");
  }

  /** Uses Docker's filtered JSON output; never requests container environment or credentials. */
  public List<DatabaseDto.Container> containers(String deviceId) {
    var device = catalog.requireDevice(deviceId);
    String output = commands.execute(device, "docker ps --no-trunc --format '{{json .}}'");
    List<DatabaseDto.Container> result = new ArrayList<>();
    try {
      for (String line : output.split("\\R")) {
        if (line.isBlank()) continue;
        var row = json.readTree(line);
        String id = row.path("ID").asText();
        if (!id.matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
        result.add(
            new DatabaseDto.Container(
                id,
                row.path("Names").asText(),
                row.path("Image").asText(),
                row.path("Ports").asText()));
        if (result.size() == 200) break;
      }
      return result;
    } catch (Exception error) {
      throw new WorkspaceException(
          502, "장비의 Docker 목록을 읽지 못했습니다. Docker 설치와 SSH 계정의 접근 권한을 확인하세요.");
    }
  }

  private void requireContainer(String id) {
    if (!id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))
      throw new WorkspaceException(400, "컨테이너를 선택해 주세요.");
  }

  private record Destination(String host, int port) {}

  private Destination containerAddress(String deviceId, String containerId, int port) {
    requireContainer(containerId);
    String template =
        "{\"running\":{{json .State.Running}},\"mode\":{{json .HostConfig.NetworkMode}},\"networks\":{{json .NetworkSettings.Networks}},\"ports\":{{json .NetworkSettings.Ports}}}";
    String output =
        commands.execute(
            catalog.requireDevice(deviceId),
            "docker inspect --type container --format '" + template + "' " + containerId);
    try {
      var value = json.readTree(output);
      if (!value.path("running").asBoolean())
        throw new WorkspaceException(409, "컨테이너가 실행 중이 아닙니다.");
      if (value.path("mode").asText().equals("host")) return new Destination("127.0.0.1", port);
      // Published ports work on rootless Docker and Docker Desktop as well as Linux bridges.
      if (!deviceId.equals("local")) {
        for (var binding : value.path("ports").path(port + "/tcp")) {
          String address = binding.path("HostIp").asText();
          int published = binding.path("HostPort").asInt();
          if (published < 1 || published > 65535) continue;
          if (Set.of("0.0.0.0", "127.0.0.1", "::", "::1").contains(address))
            return new Destination(address.contains(":") ? "::1" : "127.0.0.1", published);
          if (address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))
            return new Destination(address, published);
        }
      }
      var networks = value.path("networks");
      List<String> names = new ArrayList<>();
      networks.fieldNames().forEachRemaining(names::add);
      Collections.sort(names);
      for (String name : names) {
        String address = networks.path(name).path("IPAddress").asText();
        if (address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return new Destination(address, port);
      }
      throw new WorkspaceException(409, "컨테이너의 내부 IP가 없습니다. 장비 연결에서 게시된 포트를 지정하세요.");
    } catch (WorkspaceException error) {
      throw error;
    } catch (Exception error) {
      throw new WorkspaceException(502, "컨테이너 정보를 확인할 수 없습니다. 장비·Docker 권한·컨테이너를 확인하세요.");
    }
  }

  public Endpoint open(DatabaseConnection item) {
    validate(item.targetMode(), item.deviceId(), item.containerId(), item.type());
    if (DIRECT.equals(item.targetMode()))
      return new Endpoint(item.host(), item.port(), null, null, null);
    var device = catalog.requireDevice(item.deviceId());
    Destination destination =
        DOCKER.equals(item.targetMode())
            ? containerAddress(device.id(), item.containerId(), item.port())
            : new Destination(item.host(), item.port());
    if (device.id().equals("local"))
      return new Endpoint(destination.host(), destination.port(), null, null, null);
    SSHClient client = ssh.connect(device);
    ServerSocket listener = null;
    try {
      listener = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
      var server = listener;
      var forwarder =
          client.newLocalPortForwarder(
              new net.schmizz.sshj.connection.channel.direct.Parameters(
                  "127.0.0.1", listener.getLocalPort(), destination.host(), destination.port()),
              listener);
      Thread thread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      forwarder.listen();
                    } catch (java.io.IOException ignored) {
                    } finally {
                      try {
                        server.close();
                      } catch (Exception ignored) {
                      }
                    }
                  });
      return new Endpoint("127.0.0.1", listener.getLocalPort(), client, listener, thread);
    } catch (Exception error) {
      if (listener != null)
        try {
          listener.close();
        } catch (Exception ignored) {
        }
      try {
        client.close();
      } catch (Exception ignored) {
      }
      throw new WorkspaceException(502, "데이터베이스 SSH 터널을 열지 못했습니다.");
    }
  }

  /**
   * Closing a JDBC connection must also release its listener, forwarding thread and SSH session.
   */
  public record Endpoint(
      String host, int port, SSHClient client, ServerSocket listener, Thread thread)
      implements AutoCloseable {
    public void close() {
      if (listener != null)
        try {
          listener.close();
        } catch (Exception ignored) {
        }
      if (client != null)
        try {
          client.close();
        } catch (Exception ignored) {
        }
      if (thread != null) thread.interrupt();
    }
  }
}
