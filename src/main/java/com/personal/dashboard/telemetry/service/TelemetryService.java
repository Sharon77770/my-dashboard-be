package com.personal.dashboard.telemetry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.telemetry.dto.TelemetryDto.*;
import com.personal.dashboard.telemetry.repository.TelemetryRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns service credentials, telemetry validation, ingestion and analytics calculations. */
@Service
public class TelemetryService {
  private final TelemetryRepository repository;
  private final ObjectMapper json;

  public TelemetryService(TelemetryRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  @Transactional
  public ServiceView create(ServiceRequest input) {
    String id = "svc_" + token(12), key = key();
    long now = System.currentTimeMillis();
    repository.create(id, input, now, hash(key));
    return new ServiceView(
        id,
        input.name(),
        Objects.requireNonNullElse(input.description(), ""),
        input.serviceType(),
        now,
        true,
        null,
        "No recent telemetry",
        key);
  }

  public List<Summary> list() {
    return repository.services().stream()
        .map(
            row ->
                summary(
                    (String) row.get("id"),
                    System.currentTimeMillis() - 86_400_000,
                    System.currentTimeMillis()))
        .toList();
  }

  public ServiceView detail(String id) {
    var row = require(id);
    long last = ((Number) row.get("created_at")).longValue();
    return new ServiceView(
        id,
        (String) row.get("name"),
        (String) row.get("description"),
        (String) row.get("service_type"),
        last,
        ((Number) row.get("enabled")).intValue() == 1,
        (Long) row.get("last_used_at"),
        status(row),
        null);
  }

  @Transactional
  public ServiceView regenerate(String id) {
    var row = require(id);
    String key = key();
    repository.key(id, hash(key));
    return new ServiceView(
        id,
        (String) row.get("name"),
        (String) row.get("description"),
        (String) row.get("service_type"),
        ((Number) row.get("created_at")).longValue(),
        ((Number) row.get("enabled")).intValue() == 1,
        (Long) row.get("last_used_at"),
        status(row),
        key);
  }

  @Transactional
  public void revoke(String id) {
    require(id);
    repository.key(id, hash(key()));
  }

  @Transactional
  public ServiceView enabled(String id, boolean value) {
    require(id);
    repository.enabled(id, value);
    return detail(id);
  }

  @Transactional
  public void delete(String id) {
    require(id);
    repository.enabled(id, false);
  }

  public String authenticate(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer "))
      throw new WorkspaceException(401, "Telemetry API key is required.");
    String raw = authorization.substring(7);
    if (!raw.matches("dash_sk_[A-Za-z0-9_-]{32}"))
      throw new WorkspaceException(401, "Telemetry API key is invalid.");
    String id =
        repository
            .serviceForKey(hash(raw))
            .orElseThrow(() -> new WorkspaceException(401, "Telemetry API key is invalid."));
    repository.used(id, System.currentTimeMillis());
    return id;
  }

  @Transactional
  public Ingested event(String id, EventRequest event) {
    repository.event(
        id, validate(event), timestamp(event.timestamp()), properties(event.properties()));
    return new Ingested(1, 0);
  }

  @Transactional
  public Ingested gauge(String id, GaugeRequest gauge) {
    repository.gauge(id, validate(gauge), timestamp(gauge.timestamp()));
    return new Ingested(0, 1);
  }

  @Transactional
  public Ingested batch(String id, BatchRequest batch) {
    int events = 0, gauges = 0;
    if (batch.events() != null)
      for (EventRequest e : batch.events()) {
        repository.event(id, validate(e), timestamp(e.timestamp()), properties(e.properties()));
        events++;
      }
    if (batch.gauges() != null)
      for (GaugeRequest g : batch.gauges()) {
        repository.gauge(id, validate(g), timestamp(g.timestamp()));
        gauges++;
      }
    if (events + gauges == 0)
      throw new WorkspaceException(400, "At least one event or gauge is required.");
    return new Ingested(events, gauges);
  }

  public Analytics analytics(String id, String range) {
    require(id);
    long end = System.currentTimeMillis(),
        duration =
            switch (Objects.requireNonNullElse(range, "24h")) {
              case "1h" -> 3_600_000L;
              case "7d" -> 604_800_000L;
              case "30d" -> 2_592_000_000L;
              case "24h" -> 86_400_000L;
              default -> throw new WorkspaceException(400, "Range must be 1h, 24h, 7d or 30d.");
            };
    long start = end - duration;
    Summary summary = summary(id, start, end);
    List<Map<String, Object>> events = repository.events(id, start, end);
    Map<String, Long> endpoints = new TreeMap<>(),
        methods = new TreeMap<>(),
        statuses = new TreeMap<>();
    Map<String, long[]> endpointTotals = new TreeMap<>();
    List<Double> latencies = new ArrayList<>();
    Map<Long, long[]> hours = new TreeMap<>();
    for (Map<String, Object> e : events) {
      Map<String, Object> p = parseProperties((String) e.get("properties"));
      if ("request".equals(e.get("type"))) {
        increment(endpoints, p.get("endpoint"));
        increment(methods, p.get("method"));
        increment(statuses, p.get("status"));
        if (p.get("endpoint") != null) {
          long[] totals =
              endpointTotals.computeIfAbsent(String.valueOf(p.get("endpoint")), key -> new long[2]);
          totals[0]++;
          if (p.get("status") instanceof Number status && status.intValue() >= 400) totals[1]++;
        }
        long hour = ((Number) e.get("occurred_at")).longValue() / 3_600_000 * 3_600_000;
        hours.computeIfAbsent(hour, k -> new long[2])[0]++;
        if (p.get("latencyMs") instanceof Number n) latencies.add(n.doubleValue());
      }
      if ("error".equals(e.get("type"))) {
        long hour = ((Number) e.get("occurred_at")).longValue() / 3_600_000 * 3_600_000;
        hours.computeIfAbsent(hour, k -> new long[2])[1]++;
      }
    }
    List<Map<String, Object>> timeline = new ArrayList<>();
    hours.forEach(
        (hour, count) ->
            timeline.add(Map.of("timestamp", hour, "requests", count[0], "errors", count[1])));
    Map<String, Object> aggregate = repository.aggregate(id, start, end);
    long requests = ((Number) aggregate.get("requests")).longValue(),
        errors = ((Number) aggregate.get("errors")).longValue();
    double sum = ((Number) aggregate.get("latency_sum")).doubleValue();
    long count = ((Number) aggregate.get("latency_count")).longValue();
    Map<String, Object> row = require(id);
    summary =
        new Summary(
            id,
            (String) row.get("name"),
            (String) row.get("service_type"),
            ((Number) row.get("enabled")).intValue() == 1,
            status(row),
            (Long) row.get("last_used_at"),
            requests,
            requestCount(id, end - 60_000, end),
            requestCount(id, end - 3_600_000, end),
            requestCount(id, end - 86_400_000, end),
            errors,
            requests == 0 ? null : errors * 100.0 / requests,
            count == 0 ? null : sum / count,
            percentile(latencies, .50),
            percentile(latencies, .95),
            percentile(latencies, .99),
            repository.uniqueUsers(id, start, end),
            repository.peakConcurrentUsers(id, start, end),
            repository.activityCount(id, end - 86_400_000, end),
            repository.activityCount(id, end - 604_800_000, end),
            repository.activityCount(id, end - 2_592_000_000L, end),
            latestGauges(id, start));
    Map<String, Double> endpointErrorRates = new TreeMap<>();
    endpointTotals.forEach(
        (endpoint, totals) -> endpointErrorRates.put(endpoint, totals[1] * 100.0 / totals[0]));
    return new Analytics(summary, timeline, endpoints, endpointErrorRates, methods, statuses);
  }

  private Summary summary(String id, long from, long to) {
    Map<String, Object> row = require(id), a = repository.aggregate(id, from, to);
    long requests = ((Number) a.get("requests")).longValue(),
        errors = ((Number) a.get("errors")).longValue(),
        count = ((Number) a.get("latency_count")).longValue();
    List<Double> latencies = new ArrayList<>();
    for (Map<String, Object> event : repository.events(id, from, to)) {
      if ("request".equals(event.get("type"))) {
        Object latency = parseProperties((String) event.get("properties")).get("latencyMs");
        if (latency instanceof Number number) latencies.add(number.doubleValue());
      }
    }
    return new Summary(
        id,
        (String) row.get("name"),
        (String) row.get("service_type"),
        ((Number) row.get("enabled")).intValue() == 1,
        status(row),
        (Long) row.get("last_used_at"),
        requests,
        requestCount(id, to - 60_000, to),
        requestCount(id, to - 3_600_000, to),
        requestCount(id, to - 86_400_000, to),
        errors,
        requests == 0 ? null : errors * 100.0 / requests,
        count == 0 ? null : ((Number) a.get("latency_sum")).doubleValue() / count,
        percentile(latencies, .50),
        percentile(latencies, .95),
        percentile(latencies, .99),
        repository.uniqueUsers(id, from, to),
        repository.peakConcurrentUsers(id, from, to),
        repository.activityCount(id, to - 86_400_000, to),
        repository.activityCount(id, to - 604_800_000, to),
        repository.activityCount(id, to - 2_592_000_000L, to),
        latestGauges(id, from));
  }

  private long requestCount(String id, long from, long to) {
    return ((Number) repository.aggregate(id, from, to).get("requests")).longValue();
  }

  private Map<String, Double> latestGauges(String id, long from) {
    Map<String, Double> result = new LinkedHashMap<>();
    repository
        .gauges(id, from)
        .forEach(
            g ->
                result.putIfAbsent(
                    (String) g.get("name"), ((Number) g.get("value")).doubleValue()));
    return result;
  }

  private Map<String, Object> require(String id) {
    try {
      return repository.service(id);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      throw new WorkspaceException(404, "Service not found.");
    }
  }

  private String status(Map<String, Object> row) {
    if (((Number) row.get("enabled")).intValue() == 0) return "Disabled";
    Long last = (Long) row.get("last_used_at");
    return last == null
        ? "No recent telemetry"
        : System.currentTimeMillis() - last < 300_000 ? "Receiving data" : "Idle";
  }

  private EventRequest validate(EventRequest e) {
    if (e.properties() != null) {
      if (e.properties().size() > 24)
        throw new WorkspaceException(400, "Event properties are limited to 24 fields.");
      for (var entry : e.properties().entrySet()) {
        Object value = entry.getValue();
        if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
            || !(value instanceof String || value instanceof Number || value instanceof Boolean)
            || value instanceof String text && text.length() > 512
            || value instanceof Number number && !Double.isFinite(number.doubleValue()))
          throw new WorkspaceException(
              400, "Event properties must use short, finite scalar values.");
        if (e.type().equals("request")
            && entry.getKey().equals("latencyMs")
            && value instanceof Number number
            && number.doubleValue() < 0)
          throw new WorkspaceException(400, "latencyMs cannot be negative.");
        if (e.type().equals("request")
            && entry.getKey().equals("status")
            && value instanceof Number number
            && (number.intValue() < 100 || number.intValue() > 599))
          throw new WorkspaceException(400, "status must be an HTTP status code.");
      }
    }
    if (e.anonymousUserId() != null && !e.type().equals("user_activity"))
      throw new WorkspaceException(
          400, "anonymousUserId is only accepted for user_activity events.");
    return e;
  }

  private GaugeRequest validate(GaugeRequest g) {
    return g;
  }

  private long timestamp(Instant time) {
    long value;
    try {
      value = (time == null ? Instant.now() : time).toEpochMilli();
    } catch (ArithmeticException error) {
      throw new WorkspaceException(400, "Timestamp is outside the supported range.");
    }
    long now = System.currentTimeMillis();
    if (value > now + 300_000 || value < now - 2_592_000_000L)
      throw new WorkspaceException(
          400, "Timestamp must be within the last 30 days and not in the future.");
    return value;
  }

  private String properties(Map<String, Object> p) {
    try {
      String value = json.writeValueAsString(p == null ? Map.of() : p);
      if (value.length() > 4096)
        throw new WorkspaceException(400, "Event properties exceed 4 KiB.");
      return value;
    } catch (JsonProcessingException e) {
      throw new WorkspaceException(400, "Event properties are invalid.");
    }
  }

  private Map<String, Object> parseProperties(String value) {
    try {
      return json.readValue(value, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  private void increment(Map<String, Long> map, Object value) {
    if (value != null) map.merge(String.valueOf(value), 1L, Long::sum);
  }

  private Double percentile(List<Double> values, double p) {
    if (values.isEmpty()) return null;
    Collections.sort(values);
    return values.get((int) Math.ceil(p * values.size()) - 1);
  }

  private String key() {
    return "dash_sk_" + token(24);
  }

  private String token(int bytes) {
    byte[] data = new byte[bytes];
    new SecureRandom().nextBytes(data);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
