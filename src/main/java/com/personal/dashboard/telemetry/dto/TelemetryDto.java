package com.personal.dashboard.telemetry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Public request and response contracts for service telemetry. */
public final class TelemetryDto {
  private TelemetryDto() {}

  public record ServiceRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 500) String description,
      @NotBlank @Pattern(regexp = "Backend API|Web|Discord Bot|Worker|Custom")
          String serviceType) {}

  public record ServiceView(
      String serviceId,
      String serviceName,
      String description,
      String serviceType,
      long createdAt,
      boolean enabled,
      Long lastUsedAt,
      String status,
      String apiKey) {}

  public record EventRequest(
      @NotBlank @Size(max = 40) String type,
      Instant timestamp,
      @Size(max = 128) String anonymousUserId,
      @Size(max = 24) Map<String, Object> properties) {}

  public record GaugeRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,63}") String name,
      @NotNull @DecimalMin("0") @DecimalMax("1000000000000") Double value,
      Instant timestamp) {}

  public record BatchRequest(
      @Size(max = 100) List<@Valid EventRequest> events,
      @Size(max = 100) List<@Valid GaugeRequest> gauges) {}

  public record Ingested(int events, int gauges) {}

  public record Summary(
      String serviceId,
      String serviceName,
      String serviceType,
      boolean enabled,
      String status,
      Long lastUsedAt,
      Long requests,
      Long requestsLastMinute,
      Long requestsLastHour,
      Long requestsToday,
      Long errors,
      Double errorRate,
      Double averageLatencyMs,
      Double p50LatencyMs,
      Double p95LatencyMs,
      Double p99LatencyMs,
      Long uniqueUsers,
      Double peakConcurrentUsers,
      Long dau,
      Long wau,
      Long mau,
      Map<String, Double> gauges) {}

  public record Analytics(
      Summary summary,
      List<Map<String, Object>> timeline,
      Map<String, Long> endpoints,
      Map<String, Double> endpointErrorRates,
      Map<String, Long> methods,
      Map<String, Long> statuses) {}
}
