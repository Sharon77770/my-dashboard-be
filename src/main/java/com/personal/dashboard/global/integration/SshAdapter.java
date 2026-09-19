package com.personal.dashboard.global.integration;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.repository.CatalogRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Component;

/**
 * Opens password-authenticated SSH connections only after verifying an explicitly pinned host key.
 */
@Component
public class SshAdapter {
  private final CredentialVault vault;
  private final DeviceNetworkAdapter network;
  private final CatalogRepository catalog;

  public SshAdapter(
      CredentialVault vault, DeviceNetworkAdapter network, CatalogRepository catalog) {
    this.vault = vault;
    this.network = network;
    this.catalog = catalog;
  }

  public record DiscoveredHost(String fingerprint, String rootPath) {}

  /** Trust the first key only during enrollment; previously enrolled hosts must match their key. */
  public DiscoveredHost discover(
      String host, int port, String username, String password, String expected) {
    return discover(host, port, username, password, expected, List.of());
  }

  public DiscoveredHost discover(
      String host,
      int port,
      String username,
      String password,
      String expected,
      List<DeviceRecord> jumps) {
    var fingerprint = new java.util.concurrent.atomic.AtomicReference<String>();
    DeviceRecord target =
        new DeviceRecord(
            "discovery",
            "discovery",
            host,
            port,
            username,
            "",
            expected,
            "/",
            "NONE",
            0,
            "",
            "",
            "",
            "",
            false,
            com.personal.dashboard.catalog.entity.NetworkMode.DIRECT,
            List.of());
    try (SSHClient client = connectThrough(target, password, jumps, fingerprint)) {
      client.setConnectTimeout(5000);
      client.setTimeout(10000);
      try (var sftp = client.newSFTPClient()) {
        String root = sftp.canonicalize(".");
        if (!root.startsWith("/") || root.length() > 1024)
          throw new java.io.IOException("Unsupported SFTP home");
        return new DiscoveredHost(fingerprint.get(), root);
      }
    } catch (Exception exception) {
      throw new WorkspaceException(
          502, "SSH 연결 실패: 주소·포트·계정·비밀번호와 SFTP 사용 가능 여부를 확인해 주세요. 저장된 호스트 키가 변경된 경우에도 연결을 차단합니다.");
    }
  }

  public SSHClient connect(DeviceRecord device) {
    if (device.fingerprint().isBlank() || device.username().isBlank())
      throw new WorkspaceException(400, "SSH로 장비 연결에서 계정과 비밀번호로 장비를 다시 연결해 주세요.");
    List<DeviceRecord> jumps = resolveJumps(device);
    try {
      return connectThrough(device, vault.decrypt(device.passwordCipher()), jumps, null);
    } catch (Exception exception) {
      throw new WorkspaceException(502, "SSH 연결 실패: 주소·인증정보·호스트 지문을 확인해 주세요.");
    }
  }

  private List<DeviceRecord> resolveJumps(DeviceRecord target) {
    if (target.jumpDeviceIds().size() > 5)
      throw new WorkspaceException(400, "점프 프록시는 최대 5개까지 지정할 수 있습니다.");
    List<DeviceRecord> jumps = new ArrayList<>();
    for (String id : target.jumpDeviceIds()) {
      DeviceRecord jump =
          catalog.device(id).orElseThrow(() -> new WorkspaceException(404, "점프 장비를 찾을 수 없습니다."));
      if (jump.id().equals(target.id())
          || jump.id().equals("local")
          || !jump.jumpDeviceIds().isEmpty())
        throw new WorkspaceException(400, "점프 장비 설정이 유효하지 않습니다.");
      jumps.add(jump);
    }
    return jumps;
  }

  private SSHClient connectThrough(DeviceRecord target, String password, List<DeviceRecord> jumps)
      throws IOException {
    return connectThrough(target, password, jumps, null);
  }

  private SSHClient connectThrough(
      DeviceRecord target,
      String password,
      List<DeviceRecord> jumps,
      java.util.concurrent.atomic.AtomicReference<String> fingerprint)
      throws IOException {
    if (jumps.size() > 5) throw new WorkspaceException(400, "점프 프록시는 최대 5개까지 지정할 수 있습니다.");
    if (jumps.stream().map(DeviceRecord::id).distinct().count() != jumps.size())
      throw new WorkspaceException(400, "점프 프록시는 중복해서 지정할 수 없습니다.");
    if (jumps.stream()
        .anyMatch(
            jump ->
                jump.id().equals("local")
                    || !jump.jumpDeviceIds().isEmpty()
                    || jump.username().isBlank()
                    || jump.passwordCipher().isBlank()
                    || jump.fingerprint().isBlank()))
      throw new WorkspaceException(400, "점프 장비 설정이 유효하지 않습니다.");
    List<SSHClient> chain = new ArrayList<>();
    SSHClient targetClient = null;
    try {
      SSHClient previous = null;
      for (DeviceRecord jump : jumps) {
        SSHClient client = open(jump, previous);
        chain.add(client);
        previous = client;
      }
      targetClient = new ChainedClient(chain);
      openInto(targetClient, target, password, previous, fingerprint);
      return targetClient;
    } catch (Exception exception) {
      if (targetClient != null) closeQuietly(targetClient);
      for (int index = chain.size() - 1; index >= 0; index--) closeQuietly(chain.get(index));
      if (exception instanceof IOException io) throw io;
      throw new IOException(exception);
    }
  }

  private SSHClient open(DeviceRecord device, SSHClient previous) throws IOException {
    return open(device, vault.decrypt(device.passwordCipher()), previous);
  }

  private SSHClient open(DeviceRecord device, String password, SSHClient previous)
      throws IOException {
    SSHClient client = new SSHClient();
    try {
      openInto(client, device, password, previous, null);
      return client;
    } catch (Exception exception) {
      closeQuietly(client);
      if (exception instanceof IOException io) throw io;
      throw new IOException(exception);
    }
  }

  private void openInto(
      SSHClient client,
      DeviceRecord device,
      String password,
      SSHClient previous,
      java.util.concurrent.atomic.AtomicReference<String> fingerprintHolder)
      throws IOException {
    client.setConnectTimeout(5000);
    client.setTimeout(10000);
    if ("discovery".equals(device.id())) {
      String expected = device.fingerprint();
      client.addHostKeyVerifier(
          new net.schmizz.sshj.transport.verification.HostKeyVerifier() {
            @Override
            public boolean verify(String hostname, int remotePort, java.security.PublicKey key) {
              try {
                byte[] encoded =
                    new net.schmizz.sshj.common.Buffer.PlainBuffer()
                        .putPublicKey(key)
                        .getCompactData();
                String actual =
                    "SHA256:"
                        + java.util.Base64.getEncoder()
                            .withoutPadding()
                            .encodeToString(
                                java.security.MessageDigest.getInstance("SHA-256").digest(encoded));
                if (!expected.isBlank() && !actual.equals(expected.replace("=", ""))) return false;
                if (fingerprintHolder != null) fingerprintHolder.set(actual);
                return true;
              } catch (Exception exception) {
                return false;
              }
            }

            @Override
            public List<String> findExistingAlgorithms(String hostname, int remotePort) {
              return List.of();
            }
          });
    } else {
      client.addHostKeyVerifier(device.fingerprint());
    }
    if (previous == null) client.connect(network.resolve(device), device.sshPort());
    else client.connectVia(previous.newDirectConnection(network.resolve(device), device.sshPort()));
    client.authPassword(device.username(), password);
  }

  private void closeQuietly(SSHClient client) {
    try {
      client.close();
    } catch (Exception ignored) {
    }
  }

  private static final class ChainedClient extends SSHClient {
    private final List<SSHClient> chain;

    private ChainedClient(List<SSHClient> chain) {
      this.chain = chain;
    }

    @Override
    public void close() throws IOException {
      IOException failure = null;
      try {
        super.close();
      } catch (IOException exception) {
        failure = exception;
      }
      for (int index = chain.size() - 1; index >= 0; index--) {
        try {
          chain.get(index).close();
        } catch (IOException exception) {
          if (failure == null) failure = exception;
        }
      }
      if (failure != null) throw failure;
    }
  }
}
