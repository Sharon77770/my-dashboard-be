package com.personal.dashboard.studio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public final class StudioApiDto {
  private StudioApiDto() {}

  public record Field(
      @NotNull @Size(max = 200) String name,
      @NotNull @Size(max = 1048576) String value,
      @Size(max = 200) String fileName) {}

  public record Auth(
      @NotNull @Pattern(regexp = "none|basic|bearer|api-key") String type,
      @Size(max = 200) String username,
      @Size(max = 4000) String value,
      @Size(max = 200) String name,
      @Pattern(regexp = "header|query") String location) {}

  public record Request(
      @NotBlank @Size(max = 100) String deviceId,
      @NotBlank @Size(max = 4096) String root,
      @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method,
      @NotBlank @Size(max = 2048) String url,
      @Valid @Size(max = 100) List<Field> params,
      @Valid @Size(max = 100) List<Field> headers,
      @NotNull @Pattern(regexp = "none|json|text|form|multipart") String bodyType,
      @Size(max = 1048576) String body,
      @Valid @Size(max = 100) List<Field> fields,
      @Valid Auth auth) {}

  public record Project(
      @NotBlank @Size(max = 100) String deviceId, @NotBlank @Size(max = 4096) String root) {}

  public record Environment(
      @NotBlank @Size(max = 100) String deviceId,
      @NotBlank @Size(max = 4096) String root,
      @NotNull @Size(max = 50)
          Map<@Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]{0,79}") String, @Size(max = 4000) String>
              values) {}

  public record Response(
      int status,
      List<Field> headers,
      String body,
      boolean base64,
      long latency,
      long size,
      boolean truncated) {}

  public record History(String id, long time, String method, String url, int status) {}

  public record Exchange(String id, Response response) {}

  public record Replay(
      @NotBlank @Size(max = 100) String deviceId,
      @NotBlank @Size(max = 4096) String root,
      @NotBlank @Pattern(regexp = "[a-f0-9-]{36}") String id) {}
}
