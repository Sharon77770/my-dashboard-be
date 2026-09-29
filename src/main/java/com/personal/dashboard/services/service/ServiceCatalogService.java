package com.personal.dashboard.services.service;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.catalog.service.DeviceOperations;
import com.personal.dashboard.database.service.DatabaseStudioService;
import com.personal.dashboard.github.service.GithubService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.repository.ServiceRepository;
import com.personal.dashboard.telemetry.service.TelemetryService;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects existing workspace resources under an application service without copying their data.
 */
@Service
@DependsOn("workspaceSchema")
@PreAuthorize("hasRole('OWNER')")
public class ServiceCatalogService {
  private final ServiceRepository repository;
  private final CatalogService catalog;
  private final DeviceOperations devices;
  private final TelemetryService telemetry;
  private final GithubService github;
  private final DatabaseStudioService databases;

  public ServiceCatalogService(
      ServiceRepository repository,
      CatalogService catalog,
      DeviceOperations devices,
      TelemetryService telemetry,
      GithubService github,
      DatabaseStudioService databases) {
    this.repository = repository;
    this.catalog = catalog;
    this.devices = devices;
    this.telemetry = telemetry;
    this.github = github;
    this.databases = databases;
  }

  public List<ServiceDto.View> list() {
    return repository.services();
  }

  public ServiceDto.View get(String id) {
    return repository.service(id).orElseThrow(() -> new WorkspaceException(404, "서비스를 찾을 수 없습니다."));
  }

  @Transactional
  public ServiceDto.View save(String id, ServiceDto.Request request) {
    long now = System.currentTimeMillis();
    ServiceDto.View old = id == null ? null : get(id);
    ServiceDto.View value =
        new ServiceDto.View(
            id == null ? UUID.randomUUID().toString() : id,
            request.name().trim(),
            request.icon(),
            request.environment().trim(),
            Objects.toString(request.description(), "").trim(),
            old == null ? now : old.createdAt(),
            now);
    repository.save(value);
    repository.event(
        UUID.randomUUID().toString(),
        value.id(),
        old == null ? "CREATED" : "UPDATED",
        old == null ? "서비스 등록" : "서비스 수정",
        now);
    return value;
  }

  @Transactional
  public void delete(String id) {
    get(id);
    repository.delete(id);
  }

  public List<ServiceDto.Resource> resources(String id) {
    get(id);
    return repository.resources(id).stream().map(this::resolve).toList();
  }

  @Transactional
  public ServiceDto.Resource bind(String id, ServiceDto.ResourceRequest request) {
    get(id);
    String reference = request.reference().trim();
    String deviceId = Objects.toString(request.deviceId(), "").trim();
    String label = Objects.toString(request.label(), "").trim();
    if (reference.isEmpty()) throw new WorkspaceException(400, "리소스를 선택해 주세요.");
    if (request.type().equals("DOCKER_CONTAINER")) {
      if (deviceId.isEmpty() || !reference.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}"))
        throw new WorkspaceException(400, "장비와 컨테이너 이름을 확인해 주세요.");
      catalog.requireDevice(deviceId);
      String listing;
      try {
        listing = devices.inspect(deviceId, "docker");
      } catch (Exception exception) {
        throw new WorkspaceException(502, "Docker 컨테이너 목록을 조회할 수 없습니다.");
      }
      boolean found =
          listing
              .lines()
              .skip(1)
              .map(line -> line.trim().split("\\s+"))
              .anyMatch(parts -> parts.length > 1 && parts[1].equals(reference));
      if (!found) throw new WorkspaceException(404, "컨테이너를 찾을 수 없습니다.");
    } else if (request.type().equals("FILE")) {
      if (!deviceId.isEmpty()) catalog.requireDevice(deviceId);
      validate(request.type(), reference);
    } else {
      if (!deviceId.isEmpty()) throw new WorkspaceException(400, "이 리소스에는 장비 ID를 사용하지 않습니다.");
      validate(request.type(), reference);
    }
    ServiceDto.Resource value =
        new ServiceDto.Resource(
            UUID.randomUUID().toString(),
            id,
            request.type(),
            reference,
            deviceId,
            label,
            System.currentTimeMillis(),
            false);
    if (repository.resources(id).stream()
        .anyMatch(
            existing ->
                existing.type().equals(value.type())
                    && existing.reference().equals(value.reference())
                    && existing.deviceId().equals(value.deviceId())))
      throw new WorkspaceException(409, "이미 연결된 리소스입니다.");
    repository.add(value);
    repository.event(
        UUID.randomUUID().toString(),
        id,
        "RESOURCE_BOUND",
        "리소스 연결: " + request.type(),
        System.currentTimeMillis());
    return value;
  }

  private void validate(String type, String reference) {
    switch (type) {
      case "DEVICE" -> catalog.requireDevice(reference);
      case "TELEMETRY" -> telemetry.detail(reference);
      case "DATABASE" -> databases.get(reference);
      case "GITHUB_REPOSITORY" -> {
        if (!reference.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))
          throw new WorkspaceException(400, "저장소는 owner/name 형식이어야 합니다.");
        github.repository(reference);
      }
      case "GITHUB_ORGANIZATION" -> {
        if (github.organizations().stream()
            .noneMatch(owner -> owner.login().equalsIgnoreCase(reference)))
          throw new WorkspaceException(404, "접근 가능한 GitHub 조직을 찾을 수 없습니다.");
      }
      case "ENDPOINT" -> {
        URI uri;
        try {
          uri = URI.create(reference);
        } catch (IllegalArgumentException exception) {
          throw new WorkspaceException(400, "URL 형식을 확인해 주세요.");
        }
        if (!List.of("http", "https").contains(uri.getScheme())
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getFragment() != null)
          throw new WorkspaceException(400, "HTTP(S) URL을 입력해 주세요.");
      }
      case "FILE" -> {
        if (!reference.startsWith("/") || reference.contains("\u0000") || reference.contains(".."))
          throw new WorkspaceException(400, "파일 경로를 확인해 주세요.");
      }
      default -> throw new WorkspaceException(400, "지원하지 않는 리소스입니다.");
    }
  }

  private ServiceDto.Resource resolve(ServiceDto.Resource resource) {
    boolean orphaned =
        switch (resource.type()) {
          case "DEVICE" ->
              catalog.devices().stream()
                  .noneMatch(device -> device.id().equals(resource.reference()));
          case "DOCKER_CONTAINER" ->
              catalog.devices().stream()
                  .noneMatch(device -> device.id().equals(resource.deviceId()));
          case "FILE" ->
              !resource.deviceId().isEmpty()
                  && catalog.devices().stream()
                      .noneMatch(device -> device.id().equals(resource.deviceId()));
          case "TELEMETRY" ->
              telemetry.list().stream()
                  .noneMatch(item -> item.serviceId().equals(resource.reference()));
          case "DATABASE" ->
              databases.list().stream().noneMatch(item -> item.id().equals(resource.reference()));
          default -> false;
        };
    return new ServiceDto.Resource(
        resource.id(),
        resource.serviceId(),
        resource.type(),
        resource.reference(),
        resource.deviceId(),
        resource.label(),
        resource.createdAt(),
        orphaned);
  }

  @Transactional
  public void unbind(String id, String resourceId) {
    get(id);
    if (repository.remove(id, resourceId) == 0) throw new WorkspaceException(404, "연결을 찾을 수 없습니다.");
    repository.event(
        UUID.randomUUID().toString(),
        id,
        "RESOURCE_UNBOUND",
        "리소스 연결 해제",
        System.currentTimeMillis());
  }

  public ServiceDto.Health health(String id) {
    List<ServiceDto.Signal> signals = new ArrayList<>();
    for (ServiceDto.Resource resource : resources(id)) {
      if (resource.orphaned()) {
        signals.add(
            new ServiceDto.Signal(
                resource.type(), resource.reference(), "UNKNOWN", "연결된 리소스가 삭제되었습니다."));
        continue;
      }
      switch (resource.type()) {
        case "DEVICE" -> {
          var status = devices.status(resource.reference());
          signals.add(
              new ServiceDto.Signal(
                  "DEVICE",
                  resource.reference(),
                  status.state().equals("ONLINE") || status.state().equals("REACHABLE")
                      ? "HEALTHY"
                      : "DOWN",
                  status.details()));
        }
        case "DOCKER_CONTAINER" -> {
          try {
            String listing = devices.inspect(resource.deviceId(), "docker");
            String line =
                listing
                    .lines()
                    .filter(item -> item.contains("\t" + resource.reference() + "\t"))
                    .findFirst()
                    .orElse("");
            String state =
                line.isEmpty()
                    ? "UNKNOWN"
                    : line.toLowerCase().contains("\tup ") ? "HEALTHY" : "DOWN";
            signals.add(
                new ServiceDto.Signal("DOCKER_CONTAINER", resource.reference(), state, line));
          } catch (Exception exception) {
            signals.add(
                new ServiceDto.Signal(
                    "DOCKER_CONTAINER", resource.reference(), "UNKNOWN", "Docker 상태를 조회할 수 없습니다."));
          }
        }
        case "TELEMETRY" -> {
          var item =
              telemetry.list().stream()
                  .filter(value -> value.serviceId().equals(resource.reference()))
                  .findFirst();
          String state =
              item.map(value -> value.status().equals("Receiving data") ? "HEALTHY" : "UNKNOWN")
                  .orElse("UNKNOWN");
          signals.add(
              new ServiceDto.Signal(
                  "TELEMETRY",
                  resource.reference(),
                  state,
                  item.map(value -> value.status()).orElse("조회 불가")));
        }
        case "DATABASE" -> {
          var test = databases.test(resource.reference());
          signals.add(
              new ServiceDto.Signal(
                  "DATABASE",
                  resource.reference(),
                  test.connected() ? "HEALTHY" : "DEGRADED",
                  test.connected() ? test.version() : test.errorType()));
        }
        case "GITHUB_REPOSITORY" -> {
          try {
            var runs = github.workflowRuns(resource.reference());
            String conclusion =
                runs.isEmpty() ? "" : Objects.toString(runs.getFirst().conclusion(), "");
            String state =
                "success".equals(conclusion)
                    ? "HEALTHY"
                    : "failure".equals(conclusion) ? "DEGRADED" : "UNKNOWN";
            signals.add(
                new ServiceDto.Signal(
                    "GITHUB_ACTION",
                    resource.reference(),
                    state,
                    conclusion.isEmpty() ? "최근 완료한 Action 없음" : conclusion));
          } catch (Exception exception) {
            signals.add(
                new ServiceDto.Signal(
                    "GITHUB_ACTION",
                    resource.reference(),
                    "UNKNOWN",
                    "GitHub Action 상태를 조회할 수 없습니다."));
          }
        }
        case "ENDPOINT" ->
            signals.add(
                new ServiceDto.Signal(
                    "ENDPOINT", resource.reference(), "UNKNOWN", "자동 HTTP 검사가 설정되지 않았습니다."));
        default -> {}
      }
    }
    boolean down =
        signals.stream()
            .anyMatch(
                signal ->
                    signal.state().equals("DOWN")
                        && List.of("DEVICE", "DOCKER_CONTAINER").contains(signal.source()));
    boolean runtimeHealthy =
        signals.stream()
            .anyMatch(
                signal ->
                    signal.state().equals("HEALTHY")
                        && List.of("DEVICE", "DOCKER_CONTAINER").contains(signal.source()));
    boolean healthy = signals.stream().anyMatch(signal -> signal.state().equals("HEALTHY"));
    boolean degraded = signals.stream().anyMatch(signal -> signal.state().equals("DEGRADED"));
    boolean unknown = signals.stream().anyMatch(signal -> signal.state().equals("UNKNOWN"));
    String state =
        down
            ? (runtimeHealthy ? "DEGRADED" : "DOWN")
            : degraded
                ? "DEGRADED"
                : unknown ? (healthy ? "DEGRADED" : "UNKNOWN") : healthy ? "HEALTHY" : "UNKNOWN";
    return new ServiceDto.Health(state, signals, System.currentTimeMillis());
  }

  public List<ServiceDto.Activity> activity(String id) {
    List<ServiceDto.Activity> result = new ArrayList<>(repository.activity(get(id).id()));
    for (ServiceDto.Resource resource : resources(id)) {
      if (resource.orphaned()) continue;
      if (resource.type().equals("GITHUB_REPOSITORY")) {
        try {
          github.commits(resource.reference()).stream()
              .limit(5)
              .forEach(
                  commit ->
                      result.add(
                          new ServiceDto.Activity(
                              "commit-" + commit.sha(),
                              "GITHUB",
                              "COMMIT",
                              timestamp(commit.date()),
                              "INFO",
                              commit.message(),
                              Map.of("repository", resource.reference(), "url", commit.url()))));
          github.workflowRuns(resource.reference()).stream()
              .limit(5)
              .forEach(
                  run ->
                      result.add(
                          new ServiceDto.Activity(
                              "run-" + run.id(),
                              "GITHUB",
                              "ACTION",
                              timestamp(run.updatedAt()),
                              "failure".equals(run.conclusion()) ? "WARNING" : "INFO",
                              run.name() + " · " + Objects.toString(run.conclusion(), run.status()),
                              Map.of("repository", resource.reference(), "url", run.url()))));
        } catch (Exception ignored) {
          /* Unavailable GitHub does not hide local activity. */
        }
      }
      if (resource.type().equals("TELEMETRY")) {
        telemetry.list().stream()
            .filter(item -> item.serviceId().equals(resource.reference()))
            .findFirst()
            .ifPresent(
                item -> {
                  if (item.lastUsedAt() != null)
                    result.add(
                        new ServiceDto.Activity(
                            "telemetry-" + resource.reference(),
                            "TELEMETRY",
                            "HEARTBEAT",
                            item.lastUsedAt(),
                            "INFO",
                            "Telemetry received",
                            Map.of("serviceId", resource.reference())));
                });
      }
      if (resource.type().equals("DEVICE")) {
        catalog.activity().stream()
            .filter(item -> item.targetId().equals(resource.reference()))
            .limit(5)
            .forEach(
                item ->
                    result.add(
                        new ServiceDto.Activity(
                            item.id(),
                            "DEVICE",
                            item.kind(),
                            item.occurredAt(),
                            "INFO",
                            item.label(),
                            Map.of())));
      }
    }
    return result.stream()
        .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
        .limit(50)
        .toList();
  }

  private long timestamp(String value) {
    try {
      return Instant.parse(value).toEpochMilli();
    } catch (Exception ignored) {
      return 0L;
    }
  }

  public ServiceDto.Context context(String id) {
    ServiceDto.View service = get(id);
    List<ServiceDto.Resource> resources = resources(id);
    ServiceDto.Health health = health(id);
    Map<String, Object> githubData = new HashMap<>();
    Map<String, Object> runtime = new HashMap<>();
    Map<String, Object> telemetryData = new HashMap<>();
    Map<String, Object> databaseData = new HashMap<>();
    for (ServiceDto.Resource resource : resources) {
      if (resource.orphaned()) continue;
      try {
        switch (resource.type()) {
          case "GITHUB_REPOSITORY" ->
              githubData.put(
                  resource.reference(),
                  Map.of(
                      "repository", github.repository(resource.reference()),
                      "commits", github.commits(resource.reference()),
                      "pullRequests", github.pullRequests(resource.reference()),
                      "issues", github.issues(resource.reference()),
                      "actions", github.workflowRuns(resource.reference())));
          case "DEVICE" ->
              runtime.put(
                  resource.reference(),
                  Map.of(
                      "device",
                      catalog.devices().stream()
                          .filter(device -> device.id().equals(resource.reference()))
                          .findFirst()
                          .orElseThrow(),
                      "status",
                      devices.status(resource.reference())));
          case "DOCKER_CONTAINER" ->
              runtime.put(
                  resource.deviceId() + "/" + resource.reference(),
                  Map.of("deviceId", resource.deviceId(), "container", resource.reference()));
          case "TELEMETRY" ->
              telemetryData.put(
                  resource.reference(), telemetry.analytics(resource.reference(), "24h"));
          case "DATABASE" -> {
            var connection = databases.get(resource.reference());
            databaseData.put(
                resource.reference(),
                Map.of(
                    "id",
                    connection.id(),
                    "name",
                    connection.name(),
                    "type",
                    connection.type(),
                    "mode",
                    connection.accessMode(),
                    "connected",
                    health.signals().stream()
                        .anyMatch(
                            signal ->
                                signal.source().equals("DATABASE")
                                    && signal.reference().equals(connection.id())
                                    && signal.state().equals("HEALTHY"))));
          }
          default -> {}
        }
      } catch (Exception exception) {
        // A missing external system must not hide the rest of this service's context.
      }
    }
    return new ServiceDto.Context(
        service, resources, health, githubData, runtime, telemetryData, databaseData, activity(id));
  }
}
