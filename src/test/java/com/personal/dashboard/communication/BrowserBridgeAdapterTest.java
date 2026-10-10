package com.personal.dashboard.communication;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.adapter.BrowserBridgeAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserBridgeAdapterTest {
  @TempDir Path directory;

  @Test
  void detectsAutomaticallyProvisionedCredentialAfterDashboardStarts() throws Exception {
    Path path = directory.resolve("token");
    var adapter = new BrowserBridgeAdapter(new ObjectMapper(), "localhost", path.toString());
    assertThat(adapter.configured()).isFalse();
    Files.writeString(path, "a".repeat(64));
    assertThat(adapter.configured()).isTrue();
    Files.writeString(path, "invalid");
    assertThat(adapter.configured()).isFalse();
    Files.writeString(path, "a".repeat(65));
    assertThat(adapter.configured()).isFalse();
    Files.delete(path);
    assertThat(adapter.configured()).isFalse();
  }
}
