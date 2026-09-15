package com.personal.dashboard.runtime.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import com.personal.dashboard.global.security.CredentialVault;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Fixed SSH provisioning program and durable, encrypted managed-connection metadata. */
@Component
public class DesktopSetupAdapter {
  public record Managed(String identity, String passwordCipher, int port) {
    @Override
    public String toString() {
      return "Managed[credentials redacted]";
    }
  }

  public record Outcome(String code, int port) {}

  private final SshAdapter ssh;
  private final CredentialVault vault;
  private final ObjectMapper json;
  private final Path root;
  private final String program;

  public DesktopSetupAdapter(
      SshAdapter ssh,
      CredentialVault vault,
      ObjectMapper json,
      @Value("${workspace.data-dir:./data}") String data)
      throws IOException {
    this.ssh = ssh;
    this.vault = vault;
    this.json = json;
    root = Path.of(data).toAbsolutePath().resolve("remote-desktops");
    Files.createDirectories(root);
    try (var input = new ClassPathResource("remote-desktop/setup.py").getInputStream()) {
      program = Base64.getEncoder().encodeToString(input.readAllBytes());
    }
  }

  private String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String identity(DeviceRecord device) {
    return digest(
        device.host()
            + "\n"
            + device.sshPort()
            + "\n"
            + device.username()
            + "\n"
            + device.fingerprint());
  }

  private Path file(DeviceRecord device) {
    return root.resolve(digest(device.id()) + ".json");
  }

  public synchronized Managed managed(DeviceRecord device) {
    try {
      if (!Files.exists(file(device))) return null;
      Managed item = json.readValue(file(device).toFile(), Managed.class);
      return item.identity().equals(identity(device)) ? item : null;
    } catch (IOException e) {
      throw new WorkspaceException(500, "원격 자동 구성 상태를 읽을 수 없습니다.");
    }
  }

  private synchronized void save(DeviceRecord device, Managed item) throws IOException {
    Path temp = Files.createTempFile(root, "desktop-", ".tmp");
    try {
      json.writeValue(temp.toFile(), item);
      Files.move(
          temp, file(device), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public String password(Managed managed) {
    return vault.decrypt(managed.passwordCipher());
  }

  public Outcome configure(DeviceRecord device) {
    try (var client = ssh.connect(device)) {
      client.setTimeout(0);
      try (var timer = Executors.newSingleThreadScheduledExecutor()) {
        var deadline =
            timer.schedule(
                () -> {
                  try {
                    client.close();
                  } catch (IOException ignored) {
                  }
                },
                12,
                TimeUnit.MINUTES);
        try {
          try (var session = client.startSession();
              var command = session.exec("uname -s")) {
            String os =
                new String(command.getInputStream().readNBytes(256), StandardCharsets.UTF_8).trim();
            if (!os.equals("Linux"))
              return new Outcome(os.equals("Darwin") ? "MACOS" : "UNSUPPORTED_OS", 0);
          }
          Managed item = managed(device);
          if (item == null) {
            var random = new java.security.SecureRandom();
            String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            var password = new StringBuilder();
            for (int i = 0; i < 8; i++)
              password.append(alphabet.charAt(random.nextInt(alphabet.length())));
            String secret = password.toString();
            item = new Managed(identity(device), vault.encrypt(secret), 0);
            save(device, item);
          }
          // Bootstrap only the interpreter; package installation remains in the fixed helper.
          String bootstrap =
              "if ! command -v python3 >/dev/null 2>&1; then "
                  + "if ! command -v apt-get >/dev/null 2>&1; then echo '{\"code\":\"PYTHON_REQUIRED\",\"port\":0}'; exit; fi; "
                  + "if [ \"$(id -u)\" = 0 ]; then p=''; elif sudo -n true >/dev/null 2>&1; then p='sudo -n'; else echo '{\"code\":\"ADMIN_REQUIRED\",\"port\":0}'; exit; fi; "
                  + "$p env DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=30 update >/dev/null 2>&1 && "
                  + "$p env DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=30 install -y python3 >/dev/null 2>&1 || exit 1; fi; "
                  + "exec python3 -c 'import base64;exec(base64.b64decode(\""
                  + program
                  + "\"))'";
          try (var session = client.startSession();
              var command = session.exec(bootstrap)) {
            command
                .getOutputStream()
                .write((password(item) + "\n").getBytes(StandardCharsets.UTF_8));
            command.getOutputStream().flush();
            byte[] output = command.getInputStream().readNBytes(4097);
            if (output.length > 4096) throw new IOException("Output limit");
            Outcome result = json.readValue(output, Outcome.class);
            if (result.code().equals("READY")) {
              if (result.port() < 5920 || result.port() > 5999)
                throw new IOException("Invalid port");
              save(device, new Managed(item.identity(), item.passwordCipher(), result.port()));
            }
            return result;
          }
        } finally {
          deadline.cancel(false);
        }
      }
    } catch (WorkspaceException e) {
      throw e;
    } catch (Exception e) {
      throw new WorkspaceException(
          502, "자동 구성을 완료하지 못했습니다. SSH 연결, 설치 저장소 접근, 남은 공간을 확인한 뒤 다시 시도하세요.");
    }
  }
}
