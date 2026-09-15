package com.personal.dashboard.studio.adapter;

import com.fasterxml.jackson.databind.*;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import com.personal.dashboard.studio.dto.AssistantDto;
import com.personal.dashboard.studio.dto.StudioDto.Request;
import com.personal.dashboard.studio.dto.StudioDto.Result;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.schmizz.sshj.SSHClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Runs fixed programs locally or over verified SSH; request values travel only through stdin. */
@Component
public class StudioAdapter {
  public record Message(
      Result result,
      String error,
      Integer status,
      String event,
      String text,
      String state,
      String url,
      String code,
      AssistantDto.Event assistant,
      Long sequence) {}

  private final SshAdapter ssh;
  private final ObjectMapper json;
  private final String program;
  private final String bootstrap;

  public StudioAdapter(SshAdapter ssh, ObjectMapper json) throws IOException {
    this.ssh = ssh;
    this.json = json;
    program =
        resource("remote.py")
            .replace("# CODEX_BRIDGE", resource("codex_bridge.py"))
            .replace("# LOGS_BRIDGE", resource("logs.py"));
    bootstrap = resource("bootstrap.sh");
  }

  private String resource(String name) throws IOException {
    try (var input = new ClassPathResource("studio/" + name).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
  }

  public static final class Execution {
    private SSHClient client;
    private Process process;
    private OutputStream controlInput;
    private boolean cancelled;

    public synchronized void attach(SSHClient value) throws IOException {
      if (cancelled) {
        value.close();
        throw new IOException("Cancelled");
      }
      client = value;
    }

    public synchronized void attachInput(OutputStream value) throws IOException {
      if (cancelled) throw new IOException("Cancelled");
      controlInput = value;
    }

    public synchronized void send(byte[] value) throws IOException {
      if (cancelled || controlInput == null) throw new IOException("Not connected");
      controlInput.write(value);
      controlInput.flush();
    }

    public synchronized void cancel() {
      cancelled = true;
      if (process != null) {
        // EOF tells the helper to kill its independently grouped CLI children.
        try {
          process.getOutputStream().close();
        } catch (IOException ignored) {
        }
        try {
          if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
          }
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          process.descendants().forEach(ProcessHandle::destroyForcibly);
          process.destroyForcibly();
        }
      }
      if (client != null)
        try {
          client.close();
        } catch (IOException ignored) {
        }
    }

    public synchronized void attach(Process value) throws IOException {
      process = value;
      if (cancelled) {
        cancel();
        throw new IOException("Cancelled");
      }
    }
  }

  public void execute(
      DeviceRecord device, Request request, Execution execution, Consumer<Message> output) {
    String encoded = Base64.getEncoder().encodeToString(program.getBytes(StandardCharsets.UTF_8));
    String python =
        "exec python3 -u -c 'import base64;exec(base64.b64decode(\"" + encoded + "\"))'";
    String command = (request.action().equals("setup") ? bootstrap + "\n" : "") + python;
    if (device.id().equals("local")) {
      executeLocal(device, request, execution, output, command);
      return;
    }
    try (var client = ssh.connect(device)) {
      execution.attach(client);
      client.setTimeout(950000);
      try (var session = client.startSession();
          var remote = session.exec(command)) {
        exchange(
            device, request, remote.getOutputStream(), remote.getInputStream(), execution, output);
      }
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new WorkspaceException(502, "SSH 작업을 완료하지 못했습니다. 연결과 원격 도구 설치 상태를 확인해 주세요.");
    }
  }

  private void executeLocal(
      DeviceRecord device,
      Request request,
      Execution execution,
      Consumer<Message> output,
      String command) {
    if (!System.getProperty("os.name").equalsIgnoreCase("Linux"))
      throw new WorkspaceException(400, "서버 자체 IDE는 Linux에서 실행됩니다. Docker로 대시보드를 실행해 주세요.");
    try {
      var builder =
          new ProcessBuilder("/bin/sh", "-c", command)
              .directory(new File(device.rootPath()))
              .redirectError(ProcessBuilder.Redirect.DISCARD);
      builder
          .environment()
          .keySet()
          .removeIf(key -> !Set.of("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR").contains(key));
      var process = builder.start();
      execution.attach(process);
      exchange(
          device, request, process.getOutputStream(), process.getInputStream(), execution, output);
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new WorkspaceException(502, "서버 작업을 완료하지 못했습니다. Linux 실행 환경과 도구 설치 상태를 확인해 주세요.");
    } finally {
      execution.cancel();
    }
  }

  public void control(Execution execution, AssistantDto.Control control) {
    try {
      execution.send((json.writeValueAsString(control) + "\n").getBytes(StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new WorkspaceException(409, "Codex 작업 연결이 아직 준비되지 않았거나 종료되었습니다.");
    }
  }

  private void exchange(
      DeviceRecord device,
      Request request,
      OutputStream inputStream,
      InputStream outputStream,
      Execution execution,
      Consumer<Message> output)
      throws IOException {
    var input = json.createObjectNode();
    input.put("base", device.rootPath());
    input.put("root", request.root());
    input.put("action", request.action());
    input.set(
        "args",
        request.args() == null ? json.createObjectNode() : json.valueToTree(request.args()));
    inputStream.write((json.writeValueAsString(input) + "\n").getBytes(StandardCharsets.UTF_8));
    inputStream.flush();
    execution.attachInput(inputStream);
    // Keeping stdin open gives the helper an EOF cancellation signal when SSH disconnects.
    try (var reader = new InputStreamReader(outputStream, StandardCharsets.UTF_8)) {
      var line = new StringBuilder();
      int value;
      while ((value = reader.read()) != -1) {
        if (value == '\n') {
          if (!line.isEmpty()) output.accept(json.readValue(line.toString(), Message.class));
          line.setLength(0);
        } else {
          if (line.length() >= 8 * 1024 * 1024)
            throw new WorkspaceException(413, "원격 응답 크기 제한을 초과했습니다.");
          line.append((char) value);
        }
      }
    }
  }
}
