package com.personal.dashboard.services.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Service Catalog contracts; resource references never contain credentials. */
public final class ServiceDto {
  private ServiceDto() {}

  public record Request(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Pattern(regexp = "[a-z0-9-]{1,32}") String icon,
      @NotBlank @Size(max = 40) String environment,
      @Size(max = 500) String description) {}

  public record View(
      String id,
      String name,
      String icon,
      String environment,
      String description,
      long createdAt,
      long updatedAt) {}

  public record ResourceRequest(
      @NotBlank
          @Pattern(
              regexp =
                  "GITHUB_REPOSITORY|GITHUB_ORGANIZATION|DEVICE|DOCKER_CONTAINER|TELEMETRY|ENDPOINT|FILE|DATABASE")
          String type,
      @NotBlank @Size(max = 500) String reference,
      @Size(max = 36) String deviceId,
      @Size(max = 100) String label) {}

  public record Resource(
      String id,
      String serviceId,
      String type,
      String reference,
      String deviceId,
      String label,
      long createdAt,
      boolean orphaned) {}

  public record Signal(String source, String reference, String state, String detail) {}

  public record Health(String state, List<Signal> signals, long checkedAt) {}

  public record Activity(
      String id,
      String source,
      String type,
      long timestamp,
      String severity,
      String title,
      Map<String, Object> metadata) {}

  public record Context(
      View service,
      List<Resource> resources,
      Health health,
      Map<String, Object> github,
      Map<String, Object> runtime,
      Map<String, Object> telemetry,
      Map<String, Object> databases,
      List<Activity> activity) {}
}
