package com.personal.dashboard.studio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.integration.SshAdapter;
import com.personal.dashboard.studio.adapter.StudioAdapter;
import com.personal.dashboard.studio.dto.StudioDto.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the local process transport against real Linux Python/Git without an SSH daemon. */
@EnabledOnOs(OS.LINUX)
class StudioLocalAdapterTest {
  @TempDir Path directory;
  private final ObjectMapper json = new ObjectMapper();
  private final SshAdapter ssh = mock(SshAdapter.class);

  private StudioAdapter.Message execute(String action, Map<String, Object> args) throws Exception {
    var device =
        new DeviceRecord(
            "local",
            "Server",
            "localhost",
            22,
            "",
            "",
            "",
            directory.toString(),
            "NONE",
            3389,
            "",
            "",
            "",
            "",
            true);
    var input =
        new Request("local", directory.toString(), action, json.convertValue(args, Args.class));
    var messages = new ArrayList<StudioAdapter.Message>();
    new StudioAdapter(ssh, json)
        .execute(device, input, new StudioAdapter.Execution(), messages::add);
    verifyNoInteractions(ssh);
    assertThat(messages).isNotEmpty();
    return messages.getLast();
  }

  @Test
  void localFilesAndGitWorkWithoutSshAndRetainConflictProtection() throws Exception {
    assertThat(execute("create", Map.of("path", "code.py")).result().ok()).isTrue();
    var original = execute("read", Map.of("path", "code.py")).result();
    assertThat(
            execute(
                    "save",
                    Map.of(
                        "path",
                        "code.py",
                        "revision",
                        original.revision(),
                        "content",
                        "print('local')\n"))
                .result())
        .isNotNull();
    assertThat(Files.readString(directory.resolve("code.py"))).isEqualTo("print('local')\n");
    assertThat(
            execute(
                    "save",
                    Map.of("path", "code.py", "revision", original.revision(), "content", "stale"))
                .status())
        .isEqualTo(409);
    assertThat(execute("read", Map.of("path", "../outside")).status()).isEqualTo(403);
    assertThat(execute("git-init", Map.of()).result().ok()).isTrue();
    assertThat(
            execute("git-identity", Map.of("name", "Local test", "email", "test@example.invalid"))
                .result()
                .ok())
        .isTrue();
    assertThat(execute("git-stage", Map.of("path", "code.py")).result().ok()).isTrue();
    assertThat(execute("git-commit", Map.of("message", "local fixture")).result().ok()).isTrue();
    assertThat(execute("git-status", Map.of()).result().changes()).isEmpty();
  }

  @Test
  void cancellationClosesStdinAndStopsChildProcessGroups() throws Exception {
    Path pid = directory.resolve("child.pid");
    String program =
        "import os,sys,subprocess,threading,signal; p=subprocess.Popen(['sleep','60'],start_new_session=True); open(sys.argv[1],'w').write(str(p.pid)); os.read(0,1); os.killpg(p.pid,signal.SIGKILL); p.wait()";
    var process = new ProcessBuilder("python3", "-u", "-c", program, pid.toString()).start();
    var execution = new StudioAdapter.Execution();
    execution.attach(process);
    try {
      for (int attempt = 0;
          attempt < 100 && (!Files.exists(pid) || Files.size(pid) == 0);
          attempt++) Thread.sleep(20);
      long child = Long.parseLong(Files.readString(pid));
      execution.cancel();
      assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
      assertThat(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    } finally {
      execution.cancel();
    }
  }

  @Test
  void cancellationBeforeAttachStopsTheNewProcess() throws Exception {
    var execution = new StudioAdapter.Execution();
    execution.cancel();
    var process = new ProcessBuilder("sleep", "60").start();
    assertThatThrownBy(() -> execution.attach(process)).isInstanceOf(java.io.IOException.class);
    assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
  }
}
