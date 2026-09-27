package com.personal.dashboard.telemetry.repository;

import com.personal.dashboard.telemetry.dto.TelemetryDto.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized SQLite persistence for service credentials and bounded telemetry records. */
@Repository
public class TelemetryRepository {
  private final JdbcTemplate jdbc;

  public TelemetryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void create(String id, ServiceRequest input, long now, String hash) {
    jdbc.update(
        "INSERT INTO telemetry_services(id,name,description,service_type,created_at,api_key_hash)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        input.name().strip(),
        Objects.requireNonNullElse(input.description(), ""),
        input.serviceType(),
        now,
        hash);
  }

  public List<Map<String, Object>> services() {
    return jdbc.queryForList(
        "SELECT id,name,description,service_type,enabled,created_at,last_used_at FROM"
            + " telemetry_services ORDER BY created_at DESC");
  }

  public Map<String, Object> service(String id) {
    return jdbc.queryForMap("SELECT * FROM telemetry_services WHERE id=?", id);
  }

  public int enabled(String id, boolean enabled) {
    return jdbc.update("UPDATE telemetry_services SET enabled=? WHERE id=?", enabled ? 1 : 0, id);
  }

  public int key(String id, String hash) {
    return jdbc.update("UPDATE telemetry_services SET api_key_hash=? WHERE id=?", hash, id);
  }

  public Optional<String> serviceForKey(String hash) {
    return jdbc
        .query(
            "SELECT id FROM telemetry_services WHERE api_key_hash=? AND enabled=1",
            (r, i) -> r.getString(1),
            hash)
        .stream()
        .findFirst();
  }

  public void used(String id, long now) {
    jdbc.update("UPDATE telemetry_services SET last_used_at=? WHERE id=?", now, id);
  }

  public void event(String id, EventRequest e, long time, String properties) {
    jdbc.update(
        "INSERT INTO telemetry_events(id,service_id,type,occurred_at,anonymous_user_id,properties)"
            + " VALUES(?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        e.type(),
        time,
        e.anonymousUserId(),
        properties);
    boolean request = e.type().equals("request");
    Object status = e.properties() == null ? null : e.properties().get("status");
    boolean error =
        e.type().equals("error") || (request && status instanceof Number n && n.intValue() >= 400);
    Object latency = e.properties() == null ? null : e.properties().get("latencyMs");
    double ms = latency instanceof Number n ? n.doubleValue() : 0;
    long hour = time / 3_600_000 * 3_600_000;
    jdbc.update(
        "INSERT INTO"
            + " service_metrics_hourly(service_id,hour_start,request_count,error_count,latency_sum,latency_count)"
            + " VALUES(?,?,?,?,?,?) ON CONFLICT(service_id,hour_start) DO UPDATE SET"
            + " request_count=request_count+excluded.request_count,error_count=error_count+excluded.error_count,latency_sum=latency_sum+excluded.latency_sum,latency_count=latency_count+excluded.latency_count",
        id,
        hour,
        request ? 1 : 0,
        error ? 1 : 0,
        request && latency instanceof Number ? ms : 0,
        request && latency instanceof Number ? 1 : 0);
    long day = time / 86_400_000 * 86_400_000;
    jdbc.update(
        "INSERT INTO"
            + " service_metrics_daily(service_id,day_start,request_count,error_count,latency_sum,latency_count)"
            + " VALUES(?,?,?,?,?,?) ON CONFLICT(service_id,day_start) DO UPDATE SET"
            + " request_count=request_count+excluded.request_count,error_count=error_count+excluded.error_count,latency_sum=latency_sum+excluded.latency_sum,latency_count=latency_count+excluded.latency_count",
        id,
        day,
        request ? 1 : 0,
        error ? 1 : 0,
        request && latency instanceof Number ? ms : 0,
        request && latency instanceof Number ? 1 : 0);
  }

  public void gauge(String id, GaugeRequest g, long time) {
    jdbc.update(
        "INSERT INTO telemetry_gauges(id,service_id,name,value,occurred_at) VALUES(?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        g.name(),
        g.value(),
        time);
  }

  public List<Map<String, Object>> events(String id, long from, long to) {
    return jdbc.queryForList(
        "SELECT type,occurred_at,anonymous_user_id,properties FROM telemetry_events WHERE"
            + " service_id=? AND occurred_at>=? AND occurred_at<=? ORDER BY occurred_at",
        id,
        from,
        to);
  }

  public List<Map<String, Object>> gauges(String id, long from) {
    return jdbc.queryForList(
        "SELECT name,value,occurred_at FROM telemetry_gauges WHERE service_id=? AND occurred_at>=?"
            + " ORDER BY occurred_at DESC",
        id,
        from);
  }

  public Double peakConcurrentUsers(String id, long from, long to) {
    return jdbc.queryForObject(
        "SELECT MAX(value) FROM telemetry_gauges WHERE service_id=? AND occurred_at BETWEEN ? AND ?"
            + " AND name IN ('active_users','concurrent_users')",
        Double.class,
        id,
        from,
        to);
  }

  public Map<String, Object> aggregate(String id, long from, long to) {
    long firstCompleteHour = (from + 3_599_999) / 3_600_000 * 3_600_000;
    long afterLastCompleteHour = to / 3_600_000 * 3_600_000;
    Map<String, Object> completeHours =
        jdbc.queryForMap(
            "SELECT COALESCE(SUM(request_count),0) requests,COALESCE(SUM(error_count),0)"
                + " errors,COALESCE(SUM(latency_sum),0) latency_sum,COALESCE(SUM(latency_count),0)"
                + " latency_count FROM service_metrics_hourly WHERE service_id=? AND hour_start>=?"
                + " AND hour_start<?",
            id,
            firstCompleteHour,
            afterLastCompleteHour);
    Map<String, Object> partialHours =
        jdbc.queryForMap(
            "SELECT COALESCE(SUM(CASE WHEN type='request' THEN 1 ELSE 0 END),0)"
                + " requests,COALESCE(SUM(CASE WHEN type='error' OR (type='request' AND"
                + " CAST(json_extract(properties,'$.status') AS INTEGER)>=400) THEN 1 ELSE 0"
                + " END),0) errors,COALESCE(SUM(CASE WHEN type='request' AND"
                + " json_type(properties,'$.latencyMs') IN ('integer','real') THEN"
                + " CAST(json_extract(properties,'$.latencyMs') AS REAL) ELSE 0 END),0)"
                + " latency_sum,COALESCE(SUM(CASE WHEN type='request' AND"
                + " json_type(properties,'$.latencyMs') IN ('integer','real') THEN 1 ELSE 0 END),0)"
                + " latency_count FROM telemetry_events WHERE service_id=? AND occurred_at>=? AND"
                + " occurred_at<=? AND (occurred_at<? OR occurred_at>=?)",
            id,
            from,
            to,
            firstCompleteHour,
            afterLastCompleteHour);
    Map<String, Object> result = new HashMap<>();
    for (String field : List.of("requests", "errors", "latency_count"))
      result.put(
          field,
          ((Number) completeHours.get(field)).longValue()
              + ((Number) partialHours.get(field)).longValue());
    result.put(
        "latency_sum",
        ((Number) completeHours.get("latency_sum")).doubleValue()
            + ((Number) partialHours.get("latency_sum")).doubleValue());
    return result;
  }

  public long uniqueUsers(String id, long from, long to) {
    return jdbc.queryForObject(
        "SELECT COUNT(DISTINCT anonymous_user_id) FROM telemetry_events WHERE service_id=? AND"
            + " type='user_activity' AND anonymous_user_id IS NOT NULL AND occurred_at BETWEEN ?"
            + " AND ?",
        Long.class,
        id,
        from,
        to);
  }

  public Long activityCount(String id, long from, long to) {
    return jdbc.queryForObject(
        "SELECT COUNT(DISTINCT anonymous_user_id) FROM telemetry_events WHERE service_id=? AND"
            + " type='user_activity' AND anonymous_user_id IS NOT NULL AND occurred_at BETWEEN ?"
            + " AND ?",
        Long.class,
        id,
        from,
        to);
  }
}
