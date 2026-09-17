package com.personal.dashboard.notes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.notes.repository.NoteRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Limits block documents and rejects executable URLs before content reaches the editor. */
@Component
public class NoteContentValidator {
  private static final Set<String> BLOCKS =
      Set.of(
          "paragraph",
          "heading",
          "bulletListItem",
          "numberedListItem",
          "checkListItem",
          "toggleListItem",
          "quote",
          "codeBlock",
          "divider",
          "image",
          "table");
  private final NoteRepository repository;

  public NoteContentValidator(NoteRepository repository) {
    this.repository = repository;
  }

  public String validate(String documentId, JsonNode blocks) {
    if (!blocks.isArray()) throw new WorkspaceException(400, "블록 배열이 필요합니다.");
    String content = blocks.toString();
    if (content.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
      throw new WorkspaceException(413, "문서는 2 MiB 이하여야 합니다.");
    int[] count = {0};
    validateBlocks(blocks, 0, count);
    validateValues(documentId, blocks, 0);
    return content;
  }

  private void validateBlocks(JsonNode blocks, int depth, int[] count) {
    if (depth > 16 || !blocks.isArray()) throw new WorkspaceException(400, "블록 중첩은 16단계까지 가능합니다.");
    for (JsonNode block : blocks) {
      if (++count[0] > 2000) throw new WorkspaceException(400, "문서는 최대 2,000개 블록을 지원합니다.");
      if (!block.isObject() || !BLOCKS.contains(block.path("type").asText()))
        throw new WorkspaceException(400, "지원하지 않는 블록 형식입니다.");
      if (block.has("props") && !block.get("props").isObject()) invalidContent();
      if (block.has("id") && (!block.get("id").isTextual() || block.get("id").asText().isBlank()))
        invalidContent();
      if (block.has("content")) {
        if (block.path("type").asText().equals("table")) validateTable(block.get("content"));
        else if (!Set.of("image", "divider").contains(block.path("type").asText()))
          validateInline(block.get("content"), 0);
      }
      if (block.has("children")) validateBlocks(block.get("children"), depth + 1, count);
    }
  }

  /**
   * Validate nested inline/table shapes so a malformed API write cannot break subsequent editing.
   */
  private void validateInline(JsonNode content, int depth) {
    if (depth > 16) invalidContent();
    if (content.isTextual()) return;
    if (!content.isArray()) invalidContent();
    for (JsonNode item : content) {
      if (!item.isObject()) invalidContent();
      if (item.path("type").asText().equals("text")) {
        if (!item.path("text").isTextual()
            || (item.has("styles") && !item.get("styles").isObject())) invalidContent();
      } else if (item.path("type").asText().equals("link")) {
        if (!item.path("href").isTextual() || !item.has("content")) invalidContent();
        validateInline(item.get("content"), depth + 1);
      } else invalidContent();
    }
  }

  private void validateTable(JsonNode content) {
    if (!content.isObject()
        || !content.path("type").asText().equals("tableContent")
        || !content.path("rows").isArray()
        || content.path("rows").isEmpty()) invalidContent();
    int width = -1;
    for (JsonNode row : content.get("rows")) {
      JsonNode cells = row.path("cells");
      if (!cells.isArray() || cells.isEmpty() || cells.size() > 100) invalidContent();
      if (width == -1) width = cells.size();
      if (width != cells.size()) invalidContent();
      for (JsonNode cell : cells) {
        if (cell.isObject()) {
          if (!cell.path("type").asText().equals("tableCell") || !cell.has("content"))
            invalidContent();
          validateInline(cell.get("content"), 0);
        } else validateInline(cell, 0);
      }
    }
  }

  private void invalidContent() {
    throw new WorkspaceException(400, "블록 본문 형식을 확인하세요.");
  }

  private void validateValues(String documentId, JsonNode node, int depth) {
    if (depth > 48) throw new WorkspaceException(400, "문서 구조가 너무 깊습니다.");
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              field -> {
                if (field.getKey().equals("url") || field.getKey().equals("href"))
                  validateUrl(documentId, field.getValue(), field.getKey().equals("url"));
                validateValues(documentId, field.getValue(), depth + 1);
              });
    } else if (node.isArray())
      for (JsonNode child : node) validateValues(documentId, child, depth + 1);
  }

  private void validateUrl(String documentId, JsonNode node, boolean image) {
    if (!node.isTextual()) throw new WorkspaceException(400, "잘못된 링크입니다.");
    String value = node.asText();
    if (value.isEmpty()) return;
    if (value.startsWith("/api/v1/notes/images/")) {
      String id = value.substring("/api/v1/notes/images/".length());
      if (!repository.ownsImage(documentId, id))
        throw new WorkspaceException(400, "이 문서에 첨부한 이미지만 사용할 수 있습니다.");
      return;
    }
    try {
      URI uri = URI.create(value);
      String scheme = uri.getScheme();
      if (("https".equalsIgnoreCase(scheme) || (!image && "http".equalsIgnoreCase(scheme)))
          && uri.getHost() != null
          && uri.getUserInfo() == null) return;
      if (!image && "mailto".equalsIgnoreCase(scheme)) return;
    } catch (IllegalArgumentException ignored) {
    }
    throw new WorkspaceException(400, "이미지는 HTTPS 또는 첨부 이미지, 링크는 HTTP(S)/mailto만 지원합니다.");
  }
}
