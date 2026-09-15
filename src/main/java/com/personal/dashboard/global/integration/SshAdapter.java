package com.personal.dashboard.global.integration;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Component;

/**
 * Opens password-authenticated SSH connections only after verifying an explicitly pinned host key.
 */
@Component
public class SshAdapter {
  private final CredentialVault vault;
  private final DeviceNetworkAdapter network;

  public SshAdapter(CredentialVault vault, DeviceNetworkAdapter network) {
    this.vault = vault;
    this.network = network;
  }

  public record DiscoveredHost(String fingerprint, String rootPath) {}

  /** Trust the first key only during enrollment; previously enrolled hosts must match their key. */
  public DiscoveredHost discover(
      String host, int port, String username, String password, String expected) {
    var fingerprint = new java.util.concurrent.atomic.AtomicReference<String>();
    try (SSHClient client = new SSHClient()) {
      client.setConnectTimeout(5000);
      client.setTimeout(10000);
      client.addHostKeyVerifier(
          new net.schmizz.sshj.transport.verification.HostKeyVerifier() {
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
                fingerprint.compareAndSet(null, actual);
                return actual.equals(fingerprint.get());
              } catch (Exception exception) {
                return false;
              }
            }

            public java.util.List<String> findExistingAlgorithms(String hostname, int remotePort) {
              return java.util.List.of();
            }
          });
      client.connect(host, port);
      client.authPassword(username, password);
      try (var sftp = client.newSFTPClient()) {
        String root = sftp.canonicalize(".");
        if (fingerprint.get() == null || !root.startsWith("/") || root.length() > 1024)
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
    String address = network.resolve(device);
    SSHClient client = new SSHClient();
    try {
      client.setConnectTimeout(5000);
      client.setTimeout(10000);
      client.addHostKeyVerifier(device.fingerprint());
      client.connect(address, device.sshPort());
      client.authPassword(device.username(), vault.decrypt(device.passwordCipher()));
      return client;
    } catch (Exception exception) {
      try {
        client.close();
      } catch (Exception ignored) {
      }
      throw new WorkspaceException(502, "SSH 연결 실패: 주소·인증정보·호스트 지문을 확인해 주세요.");
    }
  }
}
