package com.personal.dashboard.global.integration;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** Executes only service-provided commands, with output bounds and a hard deadline. */
@Component
public class CommandAdapter {
  private final SshAdapter ssh;

  public CommandAdapter(SshAdapter ssh) {
    this.ssh = ssh;
  }

  public String execute(DeviceRecord device, String command) {
    try {
      if (device.id().equals("local")) {
        ProcessBuilder builder =
            new ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true);
        builder
            .environment()
            .keySet()
            .removeIf(key -> !java.util.Set.of("PATH", "HOME", "LANG", "LC_ALL").contains(key));
        Process process = builder.start();
        CompletableFuture<byte[]> output =
            CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        try {
          if (!process.waitFor(10, TimeUnit.SECONDS))
            throw new WorkspaceException(504, "명령 실행 시간이 초과되었습니다.");
          return new String(output.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        } finally {
          process.descendants().forEach(ProcessHandle::destroyForcibly);
          process.destroyForcibly();
        }
      }
      try (var client = ssh.connect(device);
          var session = client.startSession();
          var remote = session.exec(command + " 2>&1")) {
        CompletableFuture<byte[]> output =
            CompletableFuture.supplyAsync(() -> read(remote.getInputStream()));
        remote.join(10, TimeUnit.SECONDS);
        if (remote.getExitStatus() == null) throw new WorkspaceException(504, "명령 실행 시간이 초과되었습니다.");
        return new String(output.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8);
      }
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new WorkspaceException(502, "서버 명령을 실행하지 못했습니다. 연결과 설치 상태를 확인해 주세요.");
    }
  }

  private byte[] read(InputStream stream) {
    try {
      return stream.readNBytes(262144);
    } catch (IOException exception) {
      return new byte[0];
    }
  }
}
