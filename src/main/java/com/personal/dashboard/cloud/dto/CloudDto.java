package com.personal.dashboard.cloud.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class CloudDto {
  private CloudDto() {}

  public record Entry(String name, String path, boolean directory, long size, long modified) {}

  public record Listing(
      String path, List<Entry> entries, boolean truncated, long totalSpace, long usableSpace) {}

  public record Create(@NotBlank @Size(max = 4096) String path, boolean directory) {}

  public record Transfer(
      @NotBlank @Size(max = 4096) String source,
      @NotBlank @Size(max = 4096) String target,
      boolean copy) {}

  public record TrashItem(String id, String path, long deletedAt, boolean directory) {}

  public record Text(String path, String content, String revision) {}

  public record Save(
      @NotBlank @Size(max = 4096) String path,
      @NotNull @Size(max = 1048576) String content,
      @NotBlank @Size(max = 64) String revision) {}
}
