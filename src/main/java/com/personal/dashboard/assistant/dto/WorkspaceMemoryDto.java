package com.personal.dashboard.assistant.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/** Public contracts for cross-session Assistant memory and retention preferences. */
public final class WorkspaceMemoryDto {
  private WorkspaceMemoryDto() {}

  public record Input(
      @NotBlank @Size(max = 500) String content,
      @NotBlank String type,
      @NotBlank String confidence,
      @NotBlank String scope,
      String importance,
      @Size(max = 200) String tags,
      @Size(max = 120) String timeHint,
      @Size(max = 36) String relatedServiceId,
      @Size(max = 120) String relatedProject,
      Boolean pinned,
      Long expiresAt,
      @Size(max = 100) String sourceThreadId,
      @Size(max = 200) String sourceDescription) {}

  public record View(
      String id,
      String content,
      String type,
      String confidence,
      String scope,
      String status,
      String importance,
      String tags,
      String timeHint,
      String relatedServiceId,
      String relatedProject,
      String sourceType,
      String sourceThreadId,
      String sourceDescription,
      boolean pinned,
      boolean manuallyCreated,
      long createdAt,
      long updatedAt,
      long lastAccessedAt,
      int accessCount,
      Long expiresAt,
      String supersededBy,
      String promotedTargetType,
      String promotedTargetId) {}

  public record Page(List<View> items, int nextOffset, boolean hasMore) {}

  public record Preferences(
      boolean autoArchive,
      boolean autoDelete,
      boolean protectManual,
      int tentativeDays,
      int possibilityDays,
      int followUpDays,
      int archivedDays,
      long lastCleanupAt) {}

  public record Context(String text, List<String> memoryIds) {}

  public record NotePromotion(String noteId, List<View> memories) {}
}
