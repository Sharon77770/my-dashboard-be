package com.personal.dashboard.services.repository;

import com.personal.dashboard.services.dto.ServiceDto;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists catalog metadata and service events; external resources stay in their owner modules. */
@Repository
public class ServiceRepository {
  private final JdbcTemplate jdbc;

  public ServiceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<ServiceDto.View> services() {
    return jdbc.query("SELECT * FROM services ORDER BY name", (r, i) -> view(r));
  }

  public Optional<ServiceDto.View> service(String id) {
    return jdbc.query("SELECT * FROM services WHERE id=?", (r, i) -> view(r), id).stream()
        .findFirst();
  }

  private ServiceDto.View view(java.sql.ResultSet r) throws java.sql.SQLException {
    return new ServiceDto.View(
        r.getString("id"),
        r.getString("name"),
        r.getString("icon"),
        r.getString("environment"),
        r.getString("description"),
        r.getLong("created_at"),
        r.getLong("updated_at"));
  }

  public void save(ServiceDto.View service) {
    jdbc.update(
        "INSERT INTO services VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,icon=excluded.icon,environment=excluded.environment,description=excluded.description,updated_at=excluded.updated_at",
        service.id(),
        service.name(),
        service.icon(),
        service.environment(),
        service.description(),
        service.createdAt(),
        service.updatedAt());
  }

  public void delete(String id) {
    jdbc.update("DELETE FROM services WHERE id=?", id);
  }

  public List<ServiceDto.Resource> resources(String id) {
    return jdbc.query(
        "SELECT * FROM service_resources WHERE service_id=? ORDER BY created_at,id",
        (r, i) ->
            new ServiceDto.Resource(
                r.getString("id"),
                r.getString("service_id"),
                r.getString("type"),
                r.getString("reference"),
                r.getString("device_id"),
                r.getString("label"),
                r.getLong("created_at"),
                false),
        id);
  }

  public void add(ServiceDto.Resource resource) {
    jdbc.update(
        "INSERT INTO service_resources VALUES (?,?,?,?,?,?,?)",
        resource.id(),
        resource.serviceId(),
        resource.type(),
        resource.reference(),
        resource.deviceId(),
        resource.label(),
        resource.createdAt());
  }

  public int remove(String serviceId, String resourceId) {
    return jdbc.update(
        "DELETE FROM service_resources WHERE service_id=? AND id=?", serviceId, resourceId);
  }

  public List<ServiceDto.Activity> activity(String serviceId) {
    return jdbc.query(
        "SELECT * FROM service_activity WHERE service_id=? ORDER BY occurred_at DESC LIMIT 50",
        (r, i) ->
            new ServiceDto.Activity(
                r.getString("id"),
                r.getString("source"),
                r.getString("type"),
                r.getLong("occurred_at"),
                r.getString("severity"),
                r.getString("title"),
                java.util.Map.of()),
        serviceId);
  }

  public void event(String id, String serviceId, String type, String title, long timestamp) {
    jdbc.update(
        "INSERT INTO service_activity (id,service_id,source,type,occurred_at,severity,title) VALUES (?,?,'CATALOG',?,?,'INFO',?)",
        id,
        serviceId,
        type,
        timestamp,
        title);
  }
}
