package com.personal.dashboard.cloud;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.cloud.adapter.CloudStorage;
import com.personal.dashboard.cloud.dto.CloudDto.*;
import com.personal.dashboard.global.WorkspaceException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CloudStorageTest {
  @TempDir Path directory;
  CloudStorage storage;

  @BeforeEach
  void setup() throws IOException {
    storage = new CloudStorage(directory.resolve("drive").toString(), new ObjectMapper());
  }

  private void upload(String path, String text) throws IOException {
    storage.upload(path, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), false);
  }

  @Test
  void nestedUploadCopyMoveAndUnicodeArchive() throws IOException {
    upload("/학기/자료/안녕.txt", "hello");
    storage.transfer("/학기", "/사본", true);
    storage.transfer("/사본", "/이동", false);
    assertThat(storage.preview("/이동/자료/안녕.txt").content()).isEqualTo("hello");
    assertThat(storage.list("/", "안녕").entries()).hasSize(2);
    var download = storage.archive(List.of("/학기", "/학기/자료/안녕.txt"));
    var names = new ArrayList<String>();
    try (var zip = new ZipInputStream(download.stream())) {
      java.util.zip.ZipEntry item;
      while ((item = zip.getNextEntry()) != null) {
        names.add(item.getName());
        if (!item.isDirectory())
          assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
      }
    }
    assertThat(names).contains("학기/자료/안녕.txt");
    try (var files = Files.list(directory.resolve("drive/staging"))) {
      assertThat(files.count()).isZero();
    }
  }

  @Test
  void trashRestoreConflictAndPermanentDeletion() throws IOException {
    upload("/folder/file.txt", "original");
    storage.delete("/folder");
    assertThat(storage.list("/", "").entries()).isEmpty();
    var item = storage.trash().getFirst();
    assertThat(item.path()).isEqualTo("/folder");
    storage.create("/folder", true);
    assertThatThrownBy(() -> storage.restore(item.id()))
        .isInstanceOf(FileAlreadyExistsException.class);
    storage.delete("/folder");
    storage.restore(item.id());
    assertThat(storage.preview("/folder/file.txt").content()).isEqualTo("original");
    storage.delete("/folder");
    for (var deleted : storage.trash()) storage.purge(deleted.id());
    assertThat(storage.trash()).isEmpty();
  }

  @Test
  void rejectsEscapeRootMutationAndRecursiveDestination() throws IOException {
    upload("/folder/file", "safe");
    for (String path :
        List.of(
            "/../outside",
            "/folder/../../outside",
            "/folder/../other",
            "/bad\\name",
            "relative",
            "/bad\u0000name"))
      assertThatThrownBy(() -> storage.create(path, false)).isInstanceOf(WorkspaceException.class);
    assertThatThrownBy(() -> storage.delete("/")).isInstanceOf(WorkspaceException.class);
    assertThatThrownBy(() -> storage.transfer("/folder", "/folder/nested", true))
        .isInstanceOf(WorkspaceException.class);
    assertThatThrownBy(() -> storage.restore("../../outside"))
        .isInstanceOf(WorkspaceException.class);
  }

  @Test
  void refusesSymbolicLinksAndNeverDeletesExternalTargets() throws IOException {
    Assumptions.assumeTrue(System.getProperty("os.name").equalsIgnoreCase("Linux"));
    Path outside = directory.resolve("outside");
    Files.createDirectory(outside);
    Files.writeString(outside.resolve("keep"), "sentinel");
    Path link = directory.resolve("drive/files/link");
    Files.createSymbolicLink(link, outside);
    assertThat(storage.list("/", "").entries()).isEmpty();
    assertThatThrownBy(() -> storage.download("/link/keep")).isInstanceOf(WorkspaceException.class);
    assertThatThrownBy(() -> storage.delete("/link")).isInstanceOf(WorkspaceException.class);
    assertThat(Files.readString(outside.resolve("keep"))).isEqualTo("sentinel");
  }

  @Test
  void overwriteRequiresConsentAndFailedUploadsPreserveOriginal() throws IOException {
    upload("/file.txt", "old");
    assertThatThrownBy(() -> upload("/file.txt", "new"))
        .isInstanceOf(FileAlreadyExistsException.class);
    InputStream broken =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("fixture");
          }
        };
    assertThatThrownBy(() -> storage.upload("/file.txt", broken, true))
        .isInstanceOf(IOException.class);
    assertThat(storage.preview("/file.txt").content()).isEqualTo("old");
    storage.upload(
        "/file.txt", new ByteArrayInputStream("new".getBytes(StandardCharsets.UTF_8)), true);
    assertThat(storage.preview("/file.txt").content()).isEqualTo("new");
  }

  @Test
  void textSaveChecksRevisionAndBinaryPreviewFails() throws IOException {
    upload("/file.txt", "before");
    var initial = storage.preview("/file.txt");
    storage.save(new Save("/file.txt", "after", initial.revision()));
    assertThatThrownBy(() -> storage.save(new Save("/file.txt", "stale", initial.revision())))
        .isInstanceOf(WorkspaceException.class);
    assertThat(storage.preview("/file.txt").content()).isEqualTo("after");
    storage.upload("/binary", new ByteArrayInputStream(new byte[] {0, 1}), false);
    assertThatThrownBy(() -> storage.preview("/binary")).isInstanceOf(WorkspaceException.class);
  }
}
