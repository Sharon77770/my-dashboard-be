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

  /** Probes existing tools and privileges without installing or changing the target. */
  public String inspect(DeviceRecord device) {
    try (var client = ssh.connect(device)) {
      client.setTimeout(15000);
      String os = readCommand(client, "uname -s").trim();
      if (os.equals("Darwin")) return "MACOS";
      if (!os.equals("Linux")) return "UNSUPPORTED_OS";
      String probe =
          "if command -v python3 >/dev/null 2>&1 && "
              + "(command -v Xtigervnc >/dev/null 2>&1 || command -v Xvnc >/dev/null 2>&1) && "
              + "(command -v tigervncpasswd >/dev/null 2>&1 || command -v vncpasswd >/dev/null 2>&1) && "
              + "command -v openbox >/dev/null 2>&1 && command -v xterm >/dev/null 2>&1; then echo TOOLS_READY; "
              + "elif ! command -v apt-get >/dev/null 2>&1; then echo UNSUPPORTED_PACKAGES; "
              + "elif [ \"$(id -u)\" = 0 ] || sudo -n true >/dev/null 2>&1; then echo INSTALL; "
              + "elif command -v sudo >/dev/null 2>&1; then echo ADMIN_REQUIRED; else echo NO_SUDO; fi;";
      String code = readCommand(client, probe).trim();
      return Set.of("TOOLS_READY", "INSTALL", "ADMIN_REQUIRED", "NO_SUDO", "UNSUPPORTED_PACKAGES")
              .contains(code)
          ? code
          : "UNSUPPORTED_OS";
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new WorkspaceException(502, "장비 환경을 확인하지 못했습니다. SSH 연결을 확인하고 다시 시도하세요.");
    }
  }

  private String readCommand(net.schmizz.sshj.SSHClient client, String script) throws IOException {
    try (var session = client.startSession();
        var command = session.exec(script)) {
      byte[] output = command.getInputStream().readNBytes(4097);
      if (output.length > 4096) throw new IOException("Output limit");
      return new String(output, StandardCharsets.UTF_8);
    }
  }

  public Outcome configure(DeviceRecord device) {
    return configure(device, "", stage -> {});
  }

  /** Password travels only over SSH stdin and is neither persisted nor echoed. */
  public Outcome configure(
      DeviceRecord device, String sudoPassword, java.util.function.Consumer<String> progress) {
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
          String os = readCommand(client, "uname -s").trim();
          if (!os.equals("Linux"))
            return new Outcome(os.equals("Darwin") ? "MACOS" : "UNSUPPORTED_OS", 0);
          if (readCommand(client, "command -v python3").isBlank()) {
            progress.accept("INSTALLING");
            String bootstrap =
                "IFS= read -r setup_password; "
                    + "if ! command -v apt-get >/dev/null 2>&1; then echo UNSUPPORTED_PACKAGES; exit; fi; "
                    + "install_python() { env DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=60 update >/dev/null 2>&1 && env DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=60 install -y python3 >/dev/null 2>&1; }; "
                    + "if [ \"$(id -u)\" = 0 ]; then install_python && echo READY || echo COMMAND_FAILED; "
                    + "else printf '%s\\n' \"$setup_password\" | sudo -S -p '' true >/dev/null 2>&1 || { echo ADMIN_REQUIRED; exit; }; "
                    + "printf '%s\\n' \"$setup_password\" | sudo -S -p '' env DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=60 update >/dev/null 2>&1 && "
                    + "printf '%s\\n' \"$setup_password\" | sudo -S -p '' env DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=60 install -y python3 >/dev/null 2>&1 && echo READY || echo COMMAND_FAILED; fi";
            try (var session = client.startSession();
                var command = session.exec(bootstrap)) {
              command
                  .getOutputStream()
                  .write((sudoPassword + "\n").getBytes(StandardCharsets.UTF_8));
              command.getOutputStream().flush();
              command.getOutputStream().close();
              String result =
                  new String(command.getInputStream().readNBytes(128), StandardCharsets.UTF_8)
                      .trim();
              if (!result.equals("READY")) return new Outcome(result, 0);
            }
          }
          Managed item = managed(device);
          if (item == null) {
            var random = new java.security.SecureRandom();
            String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            var password = new StringBuilder();
            for (int index = 0; index < 8; index++)
              password.append(alphabet.charAt(random.nextInt(alphabet.length())));
            item = new Managed(identity(device), vault.encrypt(password.toString()), 0);
            save(device, item);
          }
          String script =
              "exec python3 -c 'import base64;exec(base64.b64decode(\"" + program + "\"))'";
          try (var session = client.startSession();
              var command = session.exec(script)) {
            byte[] input =
                (json.writeValueAsString(
                            Map.of("password", password(item), "sudoPassword", sudoPassword))
                        + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            command.getOutputStream().write(input);
            command.getOutputStream().flush();
            Arrays.fill(input, (byte) 0);
            var reader = new InputStreamReader(command.getInputStream(), StandardCharsets.UTF_8);
            var line = new StringBuilder();
            int count = 0, character;
            while ((character = reader.read()) != -1) {
              if (++count > 4096) throw new IOException("Output limit");
              if (character != '\n') {
                line.append((char) character);
                continue;
              }
              var event = json.readTree(line.toString());
              line.setLength(0);
              String stage = event.path("stage").asText();
              if (Set.of("CHECKING", "INSTALLING", "STARTING").contains(stage)) {
                progress.accept(stage);
                continue;
              }
              String code = event.path("code").asText();
              if (!Set.of(
                      "READY",
                      "ADMIN_REQUIRED",
                      "EXISTING_VNC",
                      "UNSUPPORTED_PACKAGES",
                      "NO_PORT",
                      "BUSY",
                      "TIMEOUT",
                      "COMMAND_FAILED",
                      "PASSWORD_FAILED",
                      "START_FAILED",
                      "DESKTOP_FAILED",
                      "UNSAFE_STATE",
                      "INVALID_SECRET",
                      "SETUP_FAILED")
                  .contains(code)) code = "SETUP_FAILED";
              int port = event.path("port").asInt();
              if (code.equals("READY")) {
                if (port < 5920 || port > 5999) throw new IOException("Invalid port");
                save(device, new Managed(item.identity(), item.passwordCipher(), port));
              }
              return new Outcome(code, port);
            }
            throw new IOException("Missing result");
          }
        } finally {
          deadline.cancel(false);
        }
      }
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new WorkspaceException(502, "자동 준비를 완료하지 못했습니다. SSH 연결과 설치 저장소 접근을 확인하고 다시 시도하세요.");
    }
  }
}
