package com.personal.dashboard.runtime.adapter;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.integration.SshAdapter;
import java.io.*;
import org.springframework.stereotype.Component;

/** Opens a real local PTY or SSH shell; the client receives output only through the server. */
@Component
public class TerminalAdapter {
  public interface Connection extends AutoCloseable {
    InputStream output();

    OutputStream input();

    void resize(int columns, int rows) throws Exception;

    void close() throws Exception;
  }

  private final SshAdapter ssh;

  public TerminalAdapter(SshAdapter ssh) {
    this.ssh = ssh;
  }

  public Connection open(DeviceRecord device) throws Exception {
    if (device.id().equals("local")) {
      ProcessBuilder builder =
          new ProcessBuilder("script", "-q", "-f", "-c", "/bin/bash", "/dev/null");
      builder.directory(new File(device.rootPath())).redirectErrorStream(true);
      builder
          .environment()
          .keySet()
          .removeIf(key -> !java.util.Set.of("PATH", "HOME", "LANG", "LC_ALL").contains(key));
      builder.environment().put("TERM", "xterm-256color");
      Process process = builder.start();
      return new Connection() {
        public InputStream output() {
          return process.getInputStream();
        }

        public OutputStream input() {
          return process.getOutputStream();
        }

        public void resize(int columns, int rows) throws Exception {
          // Resize the PTY descriptor without injecting commands into a running interactive
          // program.
          var shell =
              process
                  .descendants()
                  .filter(child -> child.info().command().orElse("").endsWith("/bash"))
                  .findFirst();
          if (shell.isPresent()) {
            Process resize =
                new ProcessBuilder(
                        "stty",
                        "-F",
                        "/proc/" + shell.get().pid() + "/fd/0",
                        "cols",
                        Integer.toString(columns),
                        "rows",
                        Integer.toString(rows))
                    .redirectErrorStream(true)
                    .start();
            if (!resize.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) resize.destroyForcibly();
          }
        }

        public void close() {
          process.descendants().forEach(ProcessHandle::destroyForcibly);
          process.destroyForcibly();
        }
      };
    }
    var client = ssh.connect(device);
    try {
      var session = client.startSession();
      session.allocatePTY("xterm-256color", 120, 32, 0, 0, java.util.Map.of());
      var shell = session.startShell();
      return new Connection() {
        public InputStream output() {
          return shell.getInputStream();
        }

        public OutputStream input() {
          return shell.getOutputStream();
        }

        public void resize(int columns, int rows) throws Exception {
          shell.changeWindowDimensions(columns, rows, 0, 0);
        }

        public void close() throws Exception {
          try {
            shell.close();
          } finally {
            try {
              session.close();
            } finally {
              client.close();
            }
          }
        }
      };
    } catch (Exception exception) {
      client.close();
      throw exception;
    }
  }
}
