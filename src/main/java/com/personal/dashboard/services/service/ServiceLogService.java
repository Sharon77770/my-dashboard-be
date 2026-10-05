package com.personal.dashboard.services.service;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.services.adapter.ServiceLogAdapter;
import com.personal.dashboard.services.dto.ServiceDto;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Reads retained runtime evidence without storing raw logs in service context or activity. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class ServiceLogService {
  private final ServiceCatalogService services;
  private final CatalogService catalog;
  private final ServiceLogAdapter adapter;

  public ServiceLogService(
      ServiceCatalogService services, CatalogService catalog, ServiceLogAdapter adapter) {
    this.services = services;
    this.catalog = catalog;
    this.adapter = adapter;
  }

  public ServiceDto.LogHistory read(
      String serviceId, String resourceId, String since, String until, String filter) {
    Instant start = timestamp(since), end = timestamp(until);
    if (!start.isBefore(end) || Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0)
      throw new WorkspaceException(400, "로그 조회 기간은 시작보다 끝이 늦고 31일 이하여야 합니다.");
    if (filter == null) filter = "errors";
    if (!java.util.Set.of("all", "errors").contains(filter))
      throw new WorkspaceException(400, "로그 필터는 all 또는 errors여야 합니다.");
    var resource =
        services.resources(serviceId).stream()
            .filter(row -> row.id().equals(resourceId))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(404, "서비스 연결을 찾을 수 없습니다."));
    if (!resource.type().equals("DOCKER_CONTAINER"))
      throw new WorkspaceException(400, "로그 조회는 서비스에 연결된 컨테이너를 선택해 주세요.");
    if (resource.orphaned()) throw new WorkspaceException(409, "연결된 장비가 삭제되었습니다.");
    var result =
        adapter.read(
            catalog.requireDevice(resource.deviceId()), resource.reference(), start, end, filter);
    return new ServiceDto.LogHistory(
        serviceId,
        resourceId,
        resource.deviceId(),
        resource.reference(),
        start.toString(),
        end.toString(),
        filter,
        result.output(),
        result.scannedLines(),
        result.matchedLines(),
        result.truncated(),
        result.scanComplete(),
        result.firstTimestamp(),
        result.lastTimestamp(),
        "현재 컨테이너에 보존된 Docker 로그만 조회했습니다. 교체·삭제·회전된 로그와 별도 파일 로그는 포함되지 않습니다. 오류 필터는 후보 탐색용이며 HTTP 5xx 집계가 아닙니다.");
  }

  private Instant timestamp(String value) {
    try {
      return java.time.OffsetDateTime.parse(value).toInstant();
    } catch (Exception exception) {
      throw new WorkspaceException(400, "since/until에 시간대가 포함된 ISO 8601 시각을 입력하세요.");
    }
  }
}
