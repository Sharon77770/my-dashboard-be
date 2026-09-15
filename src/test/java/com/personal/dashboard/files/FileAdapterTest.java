package com.personal.dashboard.files;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.files.adapter.FileAdapter;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks upload rollback and local symlink boundary enforcement against a real filesystem. */
class FileAdapterTest {
  @TempDir Path temporary;

  private DeviceRecord device(Path root) {
    return new DeviceRecord(
        "local",
        "Local",
        "localhost",
        22,
        "",
        "",
        "",
        root.toString(),
        "NONE",
        3389,
        "",
        "",
        "",
        "",
        false);
  }

  @Test
  void failedUploadDoesNotLeavePartialTarget() throws Exception {
    var adapter = new FileAdapter(mock(SshAdapter.class));
    InputStream broken =
        new InputStream() {
          public int read() throws IOException {
            throw new IOException("interrupted transfer");
          }
        };
    assertThatThrownBy(() -> adapter.upload(device(temporary), "/partial.txt", broken))
        .isInstanceOf(IOException.class);
    try (var files = Files.list(temporary)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  @Test
  void symlinkCannotExposeFileOutsideRoot() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeFalse(
        System.getProperty("os.name").startsWith("Windows"));
    Path root = Files.createDirectory(temporary.resolve("root"));
    Path outside = Files.writeString(temporary.resolve("outside.txt"), "private");
    Files.createSymbolicLink(root.resolve("link.txt"), outside);
    var adapter = new FileAdapter(mock(SshAdapter.class));
    assertThatThrownBy(() -> adapter.download(device(root), "/link.txt"))
        .isInstanceOf(WorkspaceException.class);
  }
}
