package com.personal.dashboard.notes.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.dashboard.notes.domain.NoteKind;
import jakarta.validation.constraints.*;

/** Explicit contracts for the private notebook and revision-checked editing. */
public final class NoteDto {
  private NoteDto() {}

  public record Create(
      @NotNull NoteKind kind,
      @Size(max = 36) String parentId,
      @NotBlank @Size(max = 200) String title,
      @NotNull @Size(max = 16) String icon,
      @NotNull JsonNode blocks) {}

  public record Metadata(
      @Size(max = 36) String parentId,
      @NotBlank @Size(max = 200) String title,
      @NotNull @Size(max = 16) String icon,
      @NotNull @Min(0) Long revision) {}

  public record Content(@NotNull JsonNode blocks, @NotNull @Min(0) Long revision) {}

  public record Entry(
      String id,
      String parentId,
      NoteKind kind,
      String title,
      String icon,
      long revision,
      long createdAt,
      long updatedAt) {}

  public record Document(Entry entry, JsonNode blocks) {}

  public record ImageView(String id, String url) {}

  public record ImageContent(String mediaType, byte[] data) {}
}
