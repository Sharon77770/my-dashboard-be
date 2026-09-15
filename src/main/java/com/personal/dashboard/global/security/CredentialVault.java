package com.personal.dashboard.global.security;

import com.personal.dashboard.global.WorkspaceException;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Encrypts connection passwords using a persistent, non-exported AES-GCM key outside SQLite. */
@Component
public class CredentialVault {
  private final SecretKeySpec key;

  public CredentialVault(@Value("${workspace.key-path:./data/credential.key}") String keyPath)
      throws Exception {
    Path path = Path.of(keyPath).toAbsolutePath();
    Files.createDirectories(path.getParent());
    if (!Files.exists(path)) {
      byte[] generated = new byte[32];
      new SecureRandom().nextBytes(generated);
      try {
        Files.write(path, generated, StandardOpenOption.CREATE_NEW);
      } catch (FileAlreadyExistsException ignored) {
        /* Another startup created the same key. */
      }
      if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(
            path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      }
    }
    byte[] bytes = Files.readAllBytes(path);
    if (bytes.length != 32) throw new IllegalStateException("Credential key must contain 32 bytes");
    key = new SecretKeySpec(bytes, "AES");
  }

  public String encrypt(String value) {
    if (value == null || value.isEmpty()) return "";
    try {
      byte[] nonce = new byte[12];
      new SecureRandom().nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      return Base64.getEncoder().encodeToString(nonce)
          + "."
          + Base64.getEncoder()
              .encodeToString(
                  cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new WorkspaceException(500, "접속 정보를 암호화하지 못했습니다.");
    }
  }

  public String decrypt(String value) {
    if (value == null || value.isEmpty()) return "";
    try {
      String[] parts = value.split("\\.");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(parts[1])),
          java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new WorkspaceException(500, "접속 정보 암호화 키를 확인해 주세요.");
    }
  }
}
