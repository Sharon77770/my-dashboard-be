package com.personal.dashboard.studio;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.security.CredentialVault;
import com.personal.dashboard.studio.repository.StudioApiRepository;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudioApiRepositoryTest {
  @TempDir Path root;

  @Test
  void encryptsSecretsAndIsolatesProjectsAcrossRestart() throws Exception {
    var vault = new CredentialVault(root.resolve("key").toString());
    var repository =
        new StudioApiRepository(vault, new ObjectMapper(), root.resolve("api").toString());
    repository.update(
        "local",
        "/project",
        state ->
            new StudioApiRepository.State(Map.of("TOKEN", "fixture-private-value"), List.of()));
    try (var files = Files.list(root.resolve("api"))) {
      for (var file : files.toList())
        assertThat(Files.readString(file)).doesNotContain("fixture-private-value", "TOKEN");
    }
    var reopened =
        new StudioApiRepository(vault, new ObjectMapper(), root.resolve("api").toString());
    assertThat(reopened.read("local", "/project").environment())
        .containsEntry("TOKEN", "fixture-private-value");
    assertThat(reopened.read("remote", "/project").environment()).isEmpty();
    assertThat(reopened.read("local", "/other").environment()).isEmpty();
  }
}
