package com.personal.dashboard.services.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.dto.DeviceStatus;
import com.personal.dashboard.catalog.service.DeviceOperations;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.services.dto.ServiceDto;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Provides live device and container operations only for resources bound to a Service. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class ServiceRuntimeService {
  private final ServiceCatalogService services;
  private final DeviceOperations devices;
  private final ObjectMapper json;

  public ServiceRuntimeService(
      ServiceCatalogService services, DeviceOperations devices, ObjectMapper json) {
    this.services = services;
    this.devices = devices;
    this.json = json;
  }

  /** A failed device or Docker query is isolated to its own row. */
  public List<ServiceDto.RuntimeSnapshot> snapshots(String serviceId) {
    return services.resources(serviceId).stream()
        .filter(
            resource ->
                resource.type().equals("DEVICE") || resource.type().equals("DOCKER_CONTAINER"))
        .map(this::snapshot)
        .toList();
  }

  private ServiceDto.RuntimeSnapshot snapshot(ServiceDto.Resource resource) {
    String deviceId = resource.type().equals("DEVICE") ? resource.reference() : resource.deviceId();
    String name = resource.label().isBlank() ? resource.reference() : resource.label();
    long checkedAt = System.currentTimeMillis();
    if (resource.orphaned())
      return new ServiceDto.RuntimeSnapshot(
          resource.id(),
          resource.type(),
          name,
          deviceId,
          "UNKNOWN",
          null,
          null,
          null,
          "",
          "장비 연결이 삭제되었습니다.",
          checkedAt);
    try {
      if (resource.type().equals("DEVICE")) {
        DeviceStatus status = devices.status(deviceId);
        return new ServiceDto.RuntimeSnapshot(
            resource.id(),
            resource.type(),
            name,
            deviceId,
            status.state(),
            status.cpu(),
            status.memory(),
            status.disk(),
            "",
            status.details(),
            status.checkedAt());
      }
      JsonNode container = findContainer(devices.containers(deviceId), resource.reference());
      if (container == null)
        return new ServiceDto.RuntimeSnapshot(
            resource.id(),
            resource.type(),
            name,
            deviceId,
            "UNKNOWN",
            null,
            null,
            null,
            "",
            "컨테이너를 찾을 수 없습니다.",
            checkedAt);
      String status = container.path("Status").asText("");
      return new ServiceDto.RuntimeSnapshot(
          resource.id(),
          resource.type(),
          name,
          deviceId,
          status.startsWith("Up ") || status.equals("Up") ? "RUNNING" : "STOPPED",
          null,
          null,
          null,
          container.path("Image").asText(""),
          status,
          checkedAt);
    } catch (Exception ignored) {
      return new ServiceDto.RuntimeSnapshot(
          resource.id(),
          resource.type(),
          name,
          deviceId,
          "UNKNOWN",
          null,
          null,
          null,
          "",
          "상태를 조회할 수 없습니다.",
          checkedAt);
    }
  }

  private JsonNode findContainer(String listing, String name) throws Exception {
    for (String line : listing.lines().toList()) {
      if (line.isBlank()) continue;
      JsonNode container = json.readTree(line);
      if (container.path("Names").asText().equals(name)) return container;
    }
    return null;
  }

  /**
   * Returns the last 200 log lines; neither the service context nor activity stores their content.
   */
  public ServiceDto.RuntimeOutput logs(String serviceId, String resourceId) {
    ServiceDto.Resource resource = requireContainer(serviceId, resourceId);
    return new ServiceDto.RuntimeOutput(
        devices.dockerLogs(resource.deviceId(), resource.reference()));
  }

  /** Runs only the fixed Docker actions against the saved container binding. */
  public ServiceDto.RuntimeOutput action(
      String serviceId, String resourceId, ServiceDto.RuntimeActionRequest request) {
    ServiceDto.Resource resource = requireContainer(serviceId, resourceId);
    return new ServiceDto.RuntimeOutput(
        devices.docker(resource.deviceId(), resource.reference(), request.action()));
  }

  private ServiceDto.Resource requireContainer(String serviceId, String resourceId) {
    ServiceDto.Resource resource =
        services.resources(serviceId).stream()
            .filter(item -> item.id().equals(resourceId))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(404, "서비스 연결을 찾을 수 없습니다."));
    if (!resource.type().equals("DOCKER_CONTAINER"))
      throw new WorkspaceException(400, "컨테이너 연결만 사용할 수 있습니다.");
    if (resource.orphaned()) throw new WorkspaceException(409, "연결된 장비가 삭제되었습니다.");
    return resource;
  }
}
