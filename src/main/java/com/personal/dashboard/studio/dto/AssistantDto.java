package com.personal.dashboard.studio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

/** Stable dashboard projections of Codex threads, models and interactive requests. */
public final class AssistantDto {
  private AssistantDto() {}

  public record Context(
      @NotBlank @Pattern(regexp = "file|selection|image|skill") String kind,
      @Size(max = 4096) String path,
      @Size(max = 200) String name,
      @Size(max = 32000) String content,
      @Min(1) Integer fromLine,
      @Min(1) Integer toLine,
      @Size(max = 3000000) String dataUrl) {}

  public record Control(
      @NotBlank @Pattern(regexp = "approval|answer|steer|interrupt") String type,
      @Size(max = 100) String requestId,
      @Pattern(regexp = "accept|acceptForSession|decline|cancel") String decision,
      @Size(max = 32000) String text,
      @Size(max = 10)
          Map<@Size(max = 100) String, @Size(max = 10) List<@Size(max = 4000) String>> answers) {}

  public record Effort(String reasoningEffort, String description) {}

  public record Model(
      String id,
      String name,
      String description,
      boolean defaultModel,
      String defaultEffort,
      List<Effort> efforts,
      List<String> inputModalities) {}

  public record FileChange(String path, String diff, String kind) {}

  public record Item(
      String id,
      String type,
      String text,
      String status,
      String command,
      String output,
      List<FileChange> files) {}

  public record Turn(String id, String status, List<Item> items, String error) {}

  public record Thread(
      String id,
      String name,
      String preview,
      String cwd,
      long createdAt,
      long updatedAt,
      String status,
      List<Turn> turns) {}

  public record Usage(
      Long totalTokens,
      Long inputTokens,
      Long outputTokens,
      Long cachedInputTokens,
      Long contextWindow,
      Long contextTokens) {}

  public record Option(String label, String description) {}

  public record Question(
      String id, String header, String question, boolean secret, List<Option> options) {}

  public record Interaction(
      String id, String kind, String reason, String command, List<Question> questions) {}

  public record Skill(String name, String description, String path, boolean enabled) {}

  public record Connection(String name, String status) {}

  public record RateLimit(String name, Double usedPercent, Long resetsAt) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Result(
      List<Model> models,
      List<Thread> threads,
      String nextCursor,
      Thread thread,
      String model,
      String effort,
      Boolean authenticated,
      String plan,
      List<Skill> skills,
      List<Connection> connections,
      List<RateLimit> rateLimits,
      Usage usage,
      String turnId,
      String status) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Event(
      long sequence,
      String kind,
      String threadId,
      String turnId,
      Item item,
      Usage usage,
      Interaction interaction,
      String requestId,
      String text) {}
}
