package com.personal.dashboard.studio.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import com.personal.dashboard.studio.entity.StudioApiExchange;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/** Encrypted project API state, using the existing vault key. Never stores plaintext requests. */
@Repository
public class StudioApiRepository {
  public record State(Map<String, String> environment, List<StudioApiExchange> history) {}

  private final Path directory;
  private final CredentialVault vault;
  private final ObjectMapper json;

  public StudioApiRepository(
      CredentialVault vault,
      ObjectMapper json,
      @Value("${workspace.studio-state-path:./data/studio-api}") String directory) {
    this.vault = vault;
    this.json = json;
    this.directory = Path.of(directory).toAbsolutePath();
  }

  private Path path(String device, String root) throws Exception {
    String key = device + "\n" + root;
    return directory.resolve(
        HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(key.getBytes(StandardCharsets.UTF_8)))
            + ".vault");
  }

  public synchronized State read(String device, String root) {
    try {
      Path file = path(device, root);
      return Files.exists(file)
          ? json.readValue(vault.decrypt(Files.readString(file)), State.class)
          : new State(new LinkedHashMap<>(), new ArrayList<>());
    } catch (Exception error) {
      throw new WorkspaceException(500, "암호화된 API 기록을 읽지 못했습니다.");
    }
  }

  public synchronized void update(
      String device, String root, java.util.function.UnaryOperator<State> operation) {
    try {
      State next = operation.apply(read(device, root));
      Files.createDirectories(directory);
      Path file = path(device, root), temporary = Files.createTempFile(directory, "api-", ".tmp");
      try {
        Files.writeString(temporary, vault.encrypt(json.writeValueAsString(next)));
        Files.move(
            temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (WorkspaceException error) {
      throw error;
    } catch (Exception error) {
      throw new WorkspaceException(500, "API 기록을 암호화하여 저장하지 못했습니다.");
    }
  }
}
