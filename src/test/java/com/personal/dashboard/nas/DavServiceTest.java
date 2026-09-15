package com.personal.dashboard.nas;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.cloud.adapter.CloudStorage;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.nas.dto.DavDto.*;
import com.personal.dashboard.nas.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DavServiceTest {
  @TempDir Path root;
  CloudStorage storage;
  DavLocks locks;
  DavService service;
  static final String LOCK =
      "<d:lockinfo xmlns:d=\"DAV:\"><d:lockscope><d:exclusive/></d:lockscope><d:locktype><d:write/></d:locktype></d:lockinfo>";

  @BeforeEach
  void setup() throws IOException {
    locks = new DavLocks();
    storage = new CloudStorage(root.toString(), new ObjectMapper(), locks);
    service = new DavService(storage, locks, true, "", "1MB");
  }

  Reply request(String method, String path, Map<String, String> headers, String body) {
    return service.execute(
        new Request(
            method,
            path,
            "http://localhost:8080",
            headers,
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
  }

  Reply request(String method, String path) {
    return request(method, path, Map.of(), "");
  }

  String text(Reply reply) {
    return new String(reply.body(), StandardCharsets.UTF_8);
  }

  void fails(int status, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            WorkspaceException.class, error -> assertThat(error.status()).isEqualTo(status));
  }

  @Test
  void collectionPropertiesAndUnicodeRoundTrip() throws IOException {
    assertThat(request("OPTIONS", "/").headers().get("DAV")).isEqualTo("1, 2");
    assertThat(request("MKCOL", "/folder").status()).isEqualTo(201);
    assertThat(request("PUT", "/folder/한글 & x.txt", Map.of(), "hello").status()).isEqualTo(201);
    var reply =
        request(
            "PROPFIND",
            "/folder",
            Map.of("depth", "1"),
            "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/><d:missing/></d:prop></d:propfind>");
    assertThat(reply.status()).isEqualTo(207);
    assertThat(text(reply)).contains("한글 &amp; x.txt", "404 Not Found", "%ED%95%9C%EA%B8%80");
    assertThat(storage.preview("/folder/한글 & x.txt").content()).isEqualTo("hello");
    fails(403, () -> request("PROPFIND", "/", Map.of(), ""));
  }

  @Test
  void locksBlockBrowserWritesAndSupportRefreshUnlock() throws IOException {
    request("PUT", "/file", Map.of(), "old");
    var lock = request("LOCK", "/file", Map.of("depth", "0"), LOCK);
    String token = lock.headers().get("Lock-Token");
    fails(
        423,
        () -> {
          try {
            storage.upload("/file", new ByteArrayInputStream(new byte[0]), true);
          } catch (IOException error) {
            throw new RuntimeException(error);
          }
        });
    fails(412, () -> request("PUT", "/file", Map.of("if", "(Not " + token + ")"), "wrong"));
    assertThat(request("PUT", "/file", Map.of("if", "(" + token + ")"), "new").status())
        .isEqualTo(204);
    assertThat(
            request("LOCK", "/file", Map.of("if", "(" + token + ")", "timeout", "Second-60"), "")
                .status())
        .isEqualTo(200);
    assertThat(request("UNLOCK", "/file", Map.of("lock-token", token), "").status()).isEqualTo(204);
    assertThat(storage.preview("/file").content()).isEqualTo("new");
    request("PUT", "/file", Map.of(), "unlocked");
  }

  @Test
  void parentLocksProtectMembershipAndLockCreatesEmptyFile() {
    request("MKCOL", "/folder");
    var lock = request("LOCK", "/folder", Map.of("depth", "0"), LOCK);
    fails(423, () -> request("PUT", "/folder/new", Map.of(), "blocked"));
    assertThat(
            request(
                    "PUT",
                    "/folder/new",
                    Map.of(
                        "if",
                        "<http://localhost:8080/dav/folder> ("
                            + lock.headers().get("Lock-Token")
                            + ")"),
                    "allowed")
                .status())
        .isEqualTo(201);
    assertThat(request("LOCK", "/empty", Map.of(), LOCK).status()).isEqualTo(201);
  }

  @Test
  void copyMoveOverwriteKeepsTrashAndRejectsRemoteDestination() throws IOException {
    request("PUT", "/source", Map.of(), "source");
    request("PUT", "/target", Map.of(), "old");
    fails(
        412,
        () ->
            request(
                "COPY",
                "/source",
                Map.of("destination", "http://localhost:8080/dav/target", "overwrite", "F"),
                ""));
    assertThat(request("COPY", "/source", Map.of("destination", "/dav/target"), "").status())
        .isEqualTo(204);
    assertThat(storage.preview("/target").content()).isEqualTo("source");
    assertThat(storage.trash()).hasSize(1);
    assertThat(request("MOVE", "/target", Map.of("destination", "/dav/moved"), "").status())
        .isEqualTo(201);
    request("DELETE", "/moved");
    assertThat(storage.trash()).hasSize(2);
    fails(
        502,
        () ->
            request(
                "COPY", "/source", Map.of("destination", "http://external.invalid/dav/no"), ""));
    fails(400, () -> request("COPY", "/source", Map.of("destination", "/dav/../outside"), ""));
  }

  @Test
  void rangesEtagsLimitsAndXmlAreSafe() throws IOException {
    var created = request("PUT", "/file", Map.of("if-none-match", "*"), "0123456789");
    String etag = created.headers().get("ETag");
    fails(412, () -> request("PUT", "/file", Map.of("if-none-match", "*"), "bad"));
    fails(412, () -> request("PUT", "/file", Map.of("if-match", "\"stale\""), "bad"));
    assertThat(request("GET", "/file", Map.of("if-none-match", etag), "").status()).isEqualTo(304);
    var range = request("GET", "/file", Map.of("range", "bytes=2-5"), "");
    assertThat(range.status()).isEqualTo(206);
    assertThat(range.headers().get("Content-Range")).isEqualTo("bytes 2-5/10");
    try (var input = range.file().stream()) {
      input.skipNBytes(range.offset());
      assertThat(new String(input.readNBytes((int) range.length()), StandardCharsets.UTF_8))
          .isEqualTo("2345");
    }
    assertThat(request("HEAD", "/file").file()).isNull();
    assertThat(request("GET", "/file", Map.of("range", "bytes=99-"), "").status()).isEqualTo(416);
    fails(413, () -> request("PUT", "/file", Map.of("content-length", "2000000"), "x"));
    fails(
        400,
        () ->
            request(
                "PROPFIND",
                "/",
                Map.of("depth", "0"),
                "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>"));
    assertThat(request("POST", "/file", Map.of(), "form").status()).isEqualTo(405);
  }
}
