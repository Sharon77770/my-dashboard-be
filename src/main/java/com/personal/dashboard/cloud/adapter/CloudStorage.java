package com.personal.dashboard.cloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.cloud.dto.CloudDto.*;
import com.personal.dashboard.cloud.entity.TrashRecord;
import com.personal.dashboard.files.service.FileDownload;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.nas.service.DavLocks;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Private disk adapter. No symlinks or client absolute filesystem paths are followed. */
@Component
public class CloudStorage {
  private final Path root, files, trash, staging;
  private final ObjectMapper json;
  private final DavLocks locks;
  private static final int MAX_ENTRIES = 10000;

  public CloudStorage(String configured, ObjectMapper json) throws IOException {
    this(configured, json, new DavLocks());
  }

  @Autowired
  public CloudStorage(
      @Value("${cloud.root:./data/cloud}") String configured, ObjectMapper json, DavLocks locks)
      throws IOException {
    this.json = json;
    this.locks = locks;
    root = Path.of(configured).toAbsolutePath().normalize();
    Files.createDirectories(root);
    if (Files.isSymbolicLink(root)) throw new IOException("Cloud root is a symlink");
    files = root.resolve("files");
    trash = root.resolve("trash");
    staging = root.resolve("staging");
    for (Path folder : List.of(files, trash, staging)) {
      if (Files.isSymbolicLink(folder)) throw new IOException("Cloud folder is a symlink");
      Files.createDirectories(folder);
    }
  }

  private Path resolve(String value) throws IOException {
    if (value == null
        || !value.startsWith("/")
        || value.length() > 4096
        || value.contains("\\")
        || value.chars().anyMatch(c -> c < 32))
      throw new WorkspaceException(400, "유효한 드라이브 경로가 아닙니다.");
    Path relative = Path.of(value.substring(1));
    for (Path part : relative) {
      String name = part.toString();
      if (name.equals("..") || name.equals(".") || name.length() > 255)
        throw new WorkspaceException(400, "허용되지 않는 경로입니다.");
    }
    Path target = files.resolve(relative).normalize();
    if (!target.startsWith(files)) throw new WorkspaceException(400, "드라이브 밖의 경로입니다.");
    checkPath(target);
    return target;
  }

  private void checkPath(Path target) throws IOException {
    Path current = root;
    for (Path part : root.relativize(target)) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current)) throw new WorkspaceException(400, "심볼릭 링크는 지원하지 않습니다.");
    }
  }

  private void mutable(Path path) {
    if (path.equals(files)) throw new WorkspaceException(400, "드라이브 루트는 변경할 수 없습니다.");
  }

  private void checkParent(Path path) {
    if (path.getParent() != null && path.getParent().startsWith(files))
      locks.check(virtual(path.getParent()), false);
  }

  private void createParents(Path target) throws IOException {
    Path parent = target.getParent();
    while (!Files.exists(parent)) {
      checkParent(parent);
      parent = parent.getParent();
    }
    Files.createDirectories(target.getParent());
  }

  private String virtual(Path path) {
    return "/" + files.relativize(path).toString().replace('\\', '/');
  }

  private Entry entry(Path path) throws IOException {
    checkPath(path);
    var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attrs.isDirectory() && !attrs.isRegularFile())
      throw new WorkspaceException(400, "일반 파일과 폴더만 지원합니다.");
    return new Entry(
        path.getFileName().toString(),
        virtual(path),
        attrs.isDirectory(),
        attrs.isDirectory() ? 0 : attrs.size(),
        attrs.lastModifiedTime().toMillis());
  }

  public synchronized Listing list(String path, String query) throws IOException {
    Path directory = resolve(path);
    if (!Files.isDirectory(directory)) throw new NoSuchFileException(path);
    List<Entry> entries = new ArrayList<>();
    boolean truncated = false;
    int scanned = 0;
    try (Stream<Path> stream = query.isBlank() ? Files.list(directory) : Files.walk(directory)) {
      var iterator = stream.iterator();
      while (iterator.hasNext()) {
        Path candidate = iterator.next();
        if (candidate.equals(directory) || Files.isSymbolicLink(candidate)) continue;
        if (++scanned > MAX_ENTRIES) {
          truncated = true;
          break;
        }
        if (query.isBlank()
            || candidate
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT)
                .contains(query.toLowerCase(Locale.ROOT))) entries.add(entry(candidate));
      }
    }
    entries.sort(
        Comparator.comparing(Entry::directory)
            .reversed()
            .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
    var store = Files.getFileStore(root);
    return new Listing(
        virtual(directory), entries, truncated, store.getTotalSpace(), store.getUsableSpace());
  }

  public synchronized Entry info(String path) throws IOException {
    return entry(resolve(path));
  }

  public synchronized void create(String path, boolean directory) throws IOException {
    Path target = resolve(path);
    mutable(target);
    locks.check(virtual(target), true);
    checkParent(target);
    if (directory) Files.createDirectory(target);
    else Files.createFile(target);
  }

  public synchronized void upload(String path, InputStream input, boolean overwrite)
      throws IOException {
    Path target = resolve(path);
    mutable(target);
    locks.check(virtual(target), true);
    if (Files.exists(target) && (!overwrite || Files.isDirectory(target)))
      throw new FileAlreadyExistsException(path);
    if (!Files.exists(target)) checkParent(target);
    createParents(target);
    Path temporary = Files.createTempFile(staging, "upload-", ".tmp");
    try {
      Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
      checkPath(target);
      if (overwrite) Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      else Files.move(temporary, target);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private List<Path> tree(Path source) throws IOException {
    List<Path> result;
    try (var stream = Files.walk(source)) {
      result = stream.limit(MAX_ENTRIES + 1L).toList();
    }
    if (result.size() > MAX_ENTRIES)
      throw new WorkspaceException(413, "한 번에 최대 10,000개 항목을 처리할 수 있습니다.");
    for (Path path : result) {
      checkPath(path);
      if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
          && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        throw new WorkspaceException(400, "특수 파일 또는 링크가 포함되어 있습니다.");
    }
    return result;
  }

  public synchronized void transfer(String source, String target, boolean copy) throws IOException {
    Path from = resolve(source), to = resolve(target);
    mutable(from);
    mutable(to);
    locks.check(virtual(to), true);
    checkParent(to);
    if (!copy) checkParent(from);
    if (!copy) locks.check(virtual(from), true);
    if (to.startsWith(from)) throw new WorkspaceException(400, "자기 자신이나 하위 폴더로 옮길 수 없습니다.");
    if (Files.exists(to)) throw new FileAlreadyExistsException(target);
    if (!Files.isDirectory(to.getParent())) throw new NoSuchFileException(target);
    List<Path> paths = tree(from);
    if (!copy) {
      Files.move(from, to);
      return;
    }
    Path container = Files.createTempDirectory(staging, "copy-");
    Path payload = container.resolve("payload");
    try {
      for (Path path : paths) {
        Path dest = payload.resolve(from.relativize(path));
        if (Files.isDirectory(path)) Files.createDirectories(dest);
        else Files.copy(path, dest, StandardCopyOption.COPY_ATTRIBUTES);
      }
      Files.move(payload, to);
    } finally {
      removeTree(container);
    }
  }

  public synchronized String delete(String path) throws IOException {
    Path from = resolve(path);
    mutable(from);
    locks.check(virtual(from), true);
    checkParent(from);
    tree(from);
    Path container = trash.resolve(UUID.randomUUID().toString());
    Files.createDirectory(container);
    try {
      json.writeValue(
          container.resolve("record.json").toFile(),
          new TrashRecord(virtual(from), System.currentTimeMillis()));
      Files.move(from, container.resolve("payload"));
    } catch (IOException | RuntimeException error) {
      removeTree(container);
      throw error;
    }
    return container.getFileName().toString();
  }

  private Path trashPath(String id) throws IOException {
    if (id == null || !id.matches("[a-f0-9-]{36}"))
      throw new WorkspaceException(400, "잘못된 휴지통 항목입니다.");
    Path container = trash.resolve(id);
    checkPath(container);
    checkPath(container.resolve("record.json"));
    checkPath(container.resolve("payload"));
    if (!Files.exists(container)) throw new NoSuchFileException(id);
    return container;
  }

  public synchronized List<TrashItem> trash() throws IOException {
    List<TrashItem> result = new ArrayList<>();
    try (var stream = Files.list(trash)) {
      for (Path path : stream.limit(MAX_ENTRIES).toList()) {
        Path container = trashPath(path.getFileName().toString());
        var record = json.readValue(container.resolve("record.json").toFile(), TrashRecord.class);
        result.add(
            new TrashItem(
                path.getFileName().toString(),
                record.path(),
                record.deletedAt(),
                Files.isDirectory(container.resolve("payload"))));
      }
    }
    result.sort(Comparator.comparingLong(TrashItem::deletedAt).reversed());
    return result;
  }

  public synchronized void restore(String id) throws IOException {
    Path container = trashPath(id);
    var record = json.readValue(container.resolve("record.json").toFile(), TrashRecord.class);
    Path target = resolve(record.path());
    mutable(target);
    locks.check(virtual(target), true);
    if (Files.exists(target)) throw new FileAlreadyExistsException(record.path());
    createParents(target);
    Files.move(container.resolve("payload"), target);
    removeTree(container);
  }

  public synchronized void purge(String id) throws IOException {
    removeTree(trashPath(id));
  }

  private void removeTree(Path target) throws IOException {
    if ((!target.startsWith(trash) && !target.startsWith(staging))
        || target.equals(trash)
        || target.equals(staging)) throw new IOException("Invalid cleanup scope");
    Files.walkFileTree(
        target,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path directory, IOException error)
              throws IOException {
            if (error != null) throw error;
            Files.delete(directory);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  public synchronized FileDownload download(String path) throws IOException {
    Path target = resolve(path);
    Entry entry = entry(target);
    if (entry.directory()) return archive(List.of(path));
    return new FileDownload(
        entry.name(), entry.size(), Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS));
  }

  public synchronized FileDownload archive(List<String> requested) throws IOException {
    if (requested.isEmpty() || requested.size() > 100)
      throw new WorkspaceException(400, "ZIP은 1~100개 항목을 선택하세요.");
    List<Path> sources = new ArrayList<>();
    for (String item : requested) {
      Path target = resolve(item);
      if (!sources.contains(target)) sources.add(target);
    }
    sources.removeIf(
        path ->
            sources.stream().anyMatch(parent -> !parent.equals(path) && path.startsWith(parent)));
    Map<String, Path> archive = new LinkedHashMap<>();
    for (Path source : sources) {
      for (Path path : tree(source)) {
        String name =
            (source.equals(files) ? Path.of("내 드라이브") : source.getFileName())
                .resolve(source.relativize(path))
                .toString()
                .replace('\\', '/');
        if (archive.putIfAbsent(name, path) != null)
          throw new WorkspaceException(409, "같은 이름의 항목은 각각 다운로드하세요.");
        if (archive.size() > MAX_ENTRIES) throw new WorkspaceException(413, "ZIP 항목 수 제한을 초과했습니다.");
      }
    }
    Path temporary = Files.createTempFile(staging, "download-", ".zip");
    try {
      try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
        for (var pair : archive.entrySet()) {
          boolean directory = Files.isDirectory(pair.getValue());
          var item = new ZipEntry(pair.getKey() + (directory ? "/" : ""));
          item.setTime(Files.getLastModifiedTime(pair.getValue()).toMillis());
          zip.putNextEntry(item);
          if (!directory) Files.copy(pair.getValue(), zip);
          zip.closeEntry();
        }
      }
      InputStream input =
          new FilterInputStream(Files.newInputStream(temporary)) {
            @Override
            public void close() throws IOException {
              try {
                super.close();
              } finally {
                Files.deleteIfExists(temporary);
              }
            }
          };
      String name =
          sources.size() == 1
              ? (sources.getFirst().equals(files)
                      ? "내 드라이브"
                      : sources.getFirst().getFileName().toString())
                  + ".zip"
              : "선택한 파일.zip";
      return new FileDownload(name, Files.size(temporary), input);
    } catch (IOException | RuntimeException error) {
      Files.deleteIfExists(temporary);
      throw error;
    }
  }

  private String digest(byte[] data) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  public synchronized void save(Save input) throws IOException {
    Text current = preview(input.path());
    if (!current.revision().equals(input.revision()))
      throw new WorkspaceException(409, "파일이 변경되었습니다. 다시 열어 수정하세요.");
    byte[] data = input.content().getBytes(StandardCharsets.UTF_8);
    if (data.length > 1024 * 1024) throw new WorkspaceException(413, "텍스트 저장은 1 MiB 이하여야 합니다.");
    upload(input.path(), new ByteArrayInputStream(data), true);
  }

  public synchronized String davPath(String path) throws IOException {
    return virtual(resolve(path));
  }

  public synchronized String davCreated(String path) throws IOException {
    return Files.readAttributes(resolve(path), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
        .creationTime()
        .toInstant()
        .toString();
  }

  public synchronized String davEtag(String path) throws IOException {
    var attributes =
        Files.readAttributes(resolve(path), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    return "\""
        + digest(
            (attributes.lastModifiedTime().toString()
                    + ":"
                    + attributes.size()
                    + ":"
                    + attributes.fileKey())
                .getBytes(StandardCharsets.UTF_8))
        + "\"";
  }

  public synchronized Text preview(String path) throws IOException {
    Path target = resolve(path);
    Entry entry = entry(target);
    if (entry.directory() || entry.size() > 1024 * 1024)
      throw new WorkspaceException(413, "텍스트 미리보기는 1 MiB 이하 파일만 지원합니다.");
    byte[] data = Files.readAllBytes(target);
    for (byte value : data) if (value == 0) throw new WorkspaceException(415, "텍스트 파일이 아닙니다.");
    try {
      return new Text(
          path,
          StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString(),
          digest(data));
    } catch (CharacterCodingException error) {
      throw new WorkspaceException(415, "UTF-8 텍스트가 아닙니다. 다운로드로 확인하세요.");
    }
  }
}
