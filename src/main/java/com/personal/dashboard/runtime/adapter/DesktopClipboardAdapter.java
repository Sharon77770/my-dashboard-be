package com.personal.dashboard.runtime.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Writes Unicode text to the managed desktop over its verified SSH connection. */
@Component
public class DesktopClipboardAdapter {
  private final SshAdapter ssh;
  private final ObjectMapper json;
  private final String program;

  public DesktopClipboardAdapter(SshAdapter ssh, ObjectMapper json) throws IOException {
    this.ssh = ssh;
    this.json = json;
    try (var input = new ClassPathResource("remote-desktop/clipboard.py").getInputStream()) {
      program = Base64.getEncoder().encodeToString(input.readAllBytes());
    }
  }

  /** Fixed code only in command arguments; clipboard contents travel solely through stdin. */
  public void send(DeviceRecord device, int port, String text) {
    byte[] input = null;
    try (var client = ssh.connect(device)) {
      client.setTimeout(10000);
      try (var session = client.startSession();
          var command =
              session.exec(
                  "exec python3 -c 'import base64;exec(base64.b64decode(\"" + program + "\"))'")) {
        input =
            (json.writeValueAsString(Map.of("port", port, "text", text)) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        command.getOutputStream().write(input);
        command.getOutputStream().flush();
        command.getOutputStream().close();
        String result =
            new String(command.getInputStream().readNBytes(32), StandardCharsets.UTF_8).trim();
        if (!result.equals("READY")) throw new IOException("Clipboard unavailable");
      }
    } catch (Exception exception) {
      throw new WorkspaceException(502, "원격 클립보드에 전송하지 못했습니다. 원격 화면과 SSH 연결을 확인하고 다시 시도하세요.");
    } finally {
      if (input != null) Arrays.fill(input, (byte) 0);
    }
  }
}
