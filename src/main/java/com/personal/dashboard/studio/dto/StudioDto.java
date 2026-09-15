package com.personal.dashboard.studio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** HTTP requests and safe, session-owned remote job projections. */
public final class StudioDto {
  private StudioDto() {}

  public record Request(
      @NotBlank @Size(max = 100) String deviceId,
      @NotBlank @Size(max = 4096) String root,
      @NotBlank @Size(max = 40) String action,
      @Valid Args args) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Args(
      @Size(max = 4096) String path,
      @Size(max = 4096) String target,
      @Size(max = 64) String revision,
      @Size(max = 1048576) String content,
      @Size(max = 4000) String message,
      @Size(max = 200) String branch,
      @Size(max = 200) String name,
      @Size(max = 200) String email,
      @Size(max = 2048) String url,
      @Size(max = 32000) String prompt,
      @Size(max = 100) String model,
      @Size(max = 30) String mode,
      Boolean staged,
      Boolean untracked,
      @Size(max = 100) String threadId,
      @Size(max = 100) String turnId,
      @Size(max = 100) String effort,
      @Size(max = 20) String approval,
      @Size(max = 2000) String cursor,
      @Size(max = 200) String query,
      Boolean archived,
      @Valid @Size(max = 16) List<AssistantDto.Context> context) {}

  public record Entry(String name, String path, boolean directory) {}

  public record LogTarget(String id, String name, String status) {}

  public record Change(String index, String worktree, String path, String oldPath) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Result(
      String root,
      String path,
      List<Entry> entries,
      String content,
      String revision,
      String git,
      String codex,
      Boolean ok,
      String branch,
      List<String> branches,
      List<Change> changes,
      String history,
      String diff,
      Boolean authenticated,
      String version,
      AssistantDto.Result assistant,
      List<LogTarget> logTargets) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Event(
      String event,
      String text,
      String state,
      String url,
      String code,
      AssistantDto.Event assistant,
      Long sequence) {}

  public record JobView(
      String id,
      String action,
      String state,
      List<Event> events,
      Result result,
      String error,
      int errorStatus) {}
}
