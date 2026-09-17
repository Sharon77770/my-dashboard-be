package com.personal.dashboard.notes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.notes.domain.NoteKind;
import com.personal.dashboard.notes.dto.NoteDto.*;
import com.personal.dashboard.notes.entity.NoteRecord;
import com.personal.dashboard.notes.repository.NoteRepository;
import java.io.IOException;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Owns folder invariants, document revisions and private image attachments. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class NoteService {
  private final NoteRepository repository;
  private final NoteContentValidator validator;
  private final ObjectMapper json;

  public NoteService(NoteRepository repository, NoteContentValidator validator, ObjectMapper json) {
    this.repository = repository;
    this.validator = validator;
    this.json = json;
  }

  public List<Entry> entries() {
    return repository.list().stream().map(this::view).toList();
  }

  public Document document(String id) {
    NoteRecord note = require(id);
    try {
      return new Document(view(note), json.readTree(note.content()));
    } catch (IOException error) {
      throw new WorkspaceException(500, "문서를 읽지 못했습니다.");
    }
  }

  @Transactional
  public Document create(Create input) {
    String id = UUID.randomUUID().toString();
    validateParent(id, input.parentId());
    if (repository.list().size() >= 5000)
      throw new WorkspaceException(409, "폴더와 문서는 최대 5,000개까지 만들 수 있습니다.");
    String content = validator.validate(id, input.blocks());
    if (input.kind() == NoteKind.FOLDER && !input.blocks().isEmpty())
      throw new WorkspaceException(400, "폴더에는 문서 블록을 저장할 수 없습니다.");
    long now = System.currentTimeMillis();
    repository.insert(
        new NoteRecord(
            id,
            input.parentId(),
            input.kind(),
            input.title().strip(),
            input.icon(),
            content,
            0,
            now,
            now));
    return document(id);
  }

  @Transactional
  public Entry metadata(String id, Metadata input) {
    require(id);
    validateParent(id, input.parentId());
    changed(
        repository.metadata(
            id,
            input.parentId(),
            input.title().strip(),
            input.icon(),
            input.revision(),
            System.currentTimeMillis()));
    return view(require(id));
  }

  @Transactional
  public Entry save(String id, Content input) {
    requireDocument(id);
    changed(
        repository.content(
            id,
            validator.validate(id, input.blocks()),
            input.revision(),
            System.currentTimeMillis()));
    return view(require(id));
  }

  @Transactional
  public void delete(String id, long revision) {
    require(id);
    // Non-empty folders require deliberate child deletion instead of silently discarding projects.
    if (repository.list().stream().anyMatch(item -> id.equals(item.parentId())))
      throw new WorkspaceException(409, "폴더 안의 문서와 하위 폴더를 먼저 이동하거나 삭제하세요.");
    changed(repository.delete(id, revision));
  }

  @Transactional
  public ImageView upload(String id, MultipartFile file) throws IOException {
    requireDocument(id);
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new WorkspaceException(413, "이미지는 0바이트 초과, 10 MiB 이하여야 합니다.");
    byte[] data;
    try (var stream = file.getInputStream()) {
      data = stream.readNBytes(10 * 1024 * 1024 + 1);
    }
    if (data.length > 10 * 1024 * 1024) throw new WorkspaceException(413, "이미지는 10 MiB 이하여야 합니다.");
    String media = mediaType(data), imageId = UUID.randomUUID().toString();
    repository.insertImage(imageId, id, media, data);
    return new ImageView(imageId, "/api/v1/notes/images/" + imageId);
  }

  public ImageContent image(String id) {
    var image =
        repository.image(id).orElseThrow(() -> new WorkspaceException(404, "이미지를 찾을 수 없습니다."));
    return new ImageContent(image.mediaType(), image.data());
  }

  private String mediaType(byte[] data) {
    if (data.length >= 8
        && Arrays.equals(
            Arrays.copyOf(data, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
      return "image/png";
    if (data.length >= 3
        && (data[0] & 255) == 255
        && (data[1] & 255) == 216
        && (data[2] & 255) == 255) return "image/jpeg";
    if (data.length >= 6) {
      String signature = new String(data, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
      if (signature.equals("GIF87a") || signature.equals("GIF89a")) return "image/gif";
    }
    if (data.length >= 12
        && new String(data, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
        && new String(data, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))
      return "image/webp";
    throw new WorkspaceException(415, "PNG, JPEG, GIF, WebP 이미지만 첨부할 수 있습니다.");
  }

  private void validateParent(String id, String parent) {
    Set<String> visited = new HashSet<>();
    int depth = 0;
    while (parent != null) {
      if (parent.equals(id) || !visited.add(parent))
        throw new WorkspaceException(400, "자기 자신이나 하위 폴더로 이동할 수 없습니다.");
      if (++depth > 32) throw new WorkspaceException(400, "폴더 중첩은 32단계까지 가능합니다.");
      NoteRecord ancestor = require(parent);
      if (ancestor.kind() != NoteKind.FOLDER) throw new WorkspaceException(400, "상위 항목은 폴더여야 합니다.");
      parent = ancestor.parentId();
    }
    // Moving a folder also moves its descendants; validate their resulting depth as well.
    Map<String, List<String>> children = new HashMap<>();
    for (NoteRecord item : repository.list()) {
      if (item.parentId() != null)
        children.computeIfAbsent(item.parentId(), key -> new ArrayList<>()).add(item.id());
    }
    List<String> level = List.of(id);
    while (!level.isEmpty()) {
      if (depth++ > 32) throw new WorkspaceException(400, "폴더 중첩은 32단계까지 가능합니다.");
      List<String> next = new ArrayList<>();
      for (String item : level) next.addAll(children.getOrDefault(item, List.of()));
      level = next;
    }
  }

  private NoteRecord require(String id) {
    return repository
        .find(id)
        .orElseThrow(() -> new WorkspaceException(404, "문서 또는 폴더를 찾을 수 없습니다."));
  }

  private void requireDocument(String id) {
    if (require(id).kind() != NoteKind.DOCUMENT) throw new WorkspaceException(400, "문서를 선택하세요.");
  }

  private void changed(int count) {
    if (count != 1) throw new WorkspaceException(409, "다른 창에서 변경되었습니다. 내용을 보관한 뒤 최신 문서를 다시 여세요.");
  }

  private Entry view(NoteRecord note) {
    return new Entry(
        note.id(),
        note.parentId(),
        note.kind(),
        note.title(),
        note.icon(),
        note.revision(),
        note.createdAt(),
        note.updatedAt());
  }
}
