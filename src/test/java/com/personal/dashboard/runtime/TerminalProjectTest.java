package com.personal.dashboard.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.files.adapter.FileAdapter;
import com.personal.dashboard.global.integration.SshAdapter;
import com.personal.dashboard.runtime.adapter.TerminalAdapter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

/** Real local PTY proof, independent from DOM/socket mocks. */
@EnabledOnOs(OS.LINUX)
class TerminalProjectTest {
  @TempDir Path root;

  @Test
  void shellStartsInProjectAndRejectsEscapes() throws Exception {
    Path project = Files.createDirectory(root.resolve("a project"));
    var device =
        new DeviceRecord(
            "local",
            "local",
            "localhost",
            22,
            "",
            "",
            "",
            root.toString(),
            "NONE",
            0,
            "",
            "",
            "",
            "",
            false);
    var files = new FileAdapter(mock(SshAdapter.class));
    assertThatThrownBy(() -> files.projectDirectory(device, root + "/../outside"))
        .isInstanceOf(RuntimeException.class);
    var adapter = new TerminalAdapter(mock(SshAdapter.class), files);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();
        var terminal = adapter.open(device, project.toString())) {
      terminal
          .input()
          .write("pwd; printf '\\120\\124\\131_ALIVE\\n'; exit\n".getBytes(StandardCharsets.UTF_8));
      terminal.input().flush();
      String output =
          executor
              .submit(
                  () -> {
                    var text = new StringBuilder();
                    int value;
                    while ((value = terminal.output().read()) != -1) {
                      text.append((char) value);
                      if (text.toString().contains("PTY_ALIVE")) break;
                    }
                    return text.toString();
                  })
              .get(10, TimeUnit.SECONDS);
      assertThat(output).contains(project.toString()).contains("PTY_ALIVE");
    }
  }
}
