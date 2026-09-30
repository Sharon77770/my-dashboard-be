package com.personal.dashboard.services.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.catalog.service.DeviceOperations;
import com.personal.dashboard.database.service.DatabaseStudioService;
import com.personal.dashboard.github.service.GithubService;
import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.dto.ServiceOnboardingDto;
import com.personal.dashboard.telemetry.service.TelemetryService;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Discovers registered resources and produces bounded, credential-free correlation hints. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class ServiceDiscoveryService {
  private static final Set<String> GENERIC =
      Set.of(
          "api",
          "app",
          "web",
          "server",
          "dev",
          "prod",
          "production",
          "mysql",
          "postgres",
          "postgresql",
          "redis",
          "db",
          "database",
          "worker",
          "service",
          "container",
          "latest");
  private final CatalogService catalog;
  private final DeviceOperations devices;
  private final GithubService github;
  private final DatabaseStudioService databases;
  private final TelemetryService telemetry;
  private final ServiceCatalogService services;
  private final ObjectMapper json;
  private final ExecutorService workers = Executors.newFixedThreadPool(4);

  public ServiceDiscoveryService(
      CatalogService catalog,
      DeviceOperations devices,
      GithubService github,
      DatabaseStudioService databases,
      TelemetryService telemetry,
      ServiceCatalogService services,
      ObjectMapper json) {
    this.catalog = catalog;
    this.devices = devices;
    this.github = github;
    this.databases = databases;
    this.telemetry = telemetry;
    this.services = services;
    this.json = json;
  }

  /** Each source fails independently; Docker is queried only on bounded registered devices. */
  public ServiceOnboardingDto.Discovery discover(String query) {
    String target = query == null ? "" : query.trim();
    if (target.length() > 100)
      throw new com.personal.dashboard.global.WorkspaceException(400, "검색어가 너무 깁니다.");
    Map<String, String> sources = new LinkedHashMap<>();
    List<ServiceOnboardingDto.Candidate> candidates = new ArrayList<>();
    Map<String, Integer> githubHints = new java.util.concurrent.ConcurrentHashMap<>();
    var repoTask = source("GitHub", () -> repositories(target, githubHints), sources);
    var dbTask =
        source(
            "Database",
            () ->
                databases.list().stream()
                    .limit(50)
                    .map(
                        db ->
                            candidate(
                                "DATABASE",
                                db.id(),
                                "",
                                db.name() + " · " + db.type() + " · " + db.databaseName(),
                                "",
                                "",
                                "",
                                "",
                                ""))
                    .toList(),
            sources);
    var telemetryTask =
        source(
            "Telemetry",
            () ->
                telemetry.list().stream()
                    .limit(50)
                    .map(
                        item ->
                            candidate(
                                "TELEMETRY",
                                item.serviceId(),
                                "",
                                item.serviceName(),
                                "",
                                "",
                                "",
                                "",
                                item.status()))
                    .toList(),
            sources);
    var localTask =
        source(
            "Workspace",
            () -> {
              List<ServiceOnboardingDto.Candidate> found = new ArrayList<>();
              var registered = catalog.devices().stream().limit(50).toList();
              registered.forEach(
                  device ->
                      found.add(
                          candidate("DEVICE", device.id(), "", device.name(), "", "", "", "", "")));
              catalog.applications().stream()
                  .limit(50)
                  .filter(app -> safeEndpoint(app.url()))
                  .forEach(
                      app ->
                          found.add(
                              candidate(
                                  "ENDPOINT",
                                  app.url(),
                                  "",
                                  app.name() + " · " + app.url(),
                                  "",
                                  "",
                                  "",
                                  "",
                                  "")));
              registered.stream()
                  .filter(device -> device.id().equals("local") || !device.fingerprint().isBlank())
                  .sorted(
                      Comparator.comparingInt(
                              (com.personal.dashboard.catalog.dto.DeviceView device) ->
                                  affinity(target, device.name()))
                          .reversed())
                  .limit(5)
                  .forEach(
                      device -> {
                        try {
                          var status = devices.status(device.id());
                          if (!Set.of("ONLINE", "REACHABLE").contains(status.state())) return;
                          found.addAll(containers(device.id()));
                        } catch (Exception ignored) {
                          /* This device is unavailable; other sources remain useful. */
                        }
                      });
              return found;
            },
            sources);
    candidates.addAll(repoTask.join());
    candidates.addAll(dbTask.join());
    candidates.addAll(telemetryTask.join());
    candidates.addAll(localTask.join());
    synchronized (sources) {
      for (String name : List.of("GitHub", "Database", "Telemetry", "Workspace"))
        sources.putIfAbsent(name, "UNAVAILABLE");
    }
    List<ServiceDto.View> existing;
    try {
      existing = services.list().stream().limit(50).toList();
      sources.put("Services", "OK");
    } catch (Exception exception) {
      existing = List.of();
      sources.put("Services", "UNAVAILABLE");
    }
    for (ServiceDto.View service : existing) {
      try {
        services.resources(service.id()).stream()
            .filter(
                resource ->
                    resource.type().equals("FILE")
                        || resource.type().equals("ENDPOINT") && safeEndpoint(resource.reference()))
            .forEach(
                resource ->
                    candidates.add(
                        candidate(
                            resource.type(),
                            resource.reference(),
                            resource.deviceId(),
                            resource.label().isBlank() ? resource.reference() : resource.label(),
                            "",
                            "",
                            "",
                            "",
                            "")));
      } catch (Exception ignored) {
        /* A stale service does not hide other candidates. */
      }
    }
    Map<String, String> boundResources = new LinkedHashMap<>();
    List<ServiceOnboardingDto.ExistingService> summaries = new ArrayList<>();
    for (ServiceDto.View service : existing) {
      try {
        var resources = services.resources(service.id());
        resources.forEach(
            resource ->
                boundResources.put(
                    resource.type()
                        + "\u0000"
                        + resource.deviceId()
                        + "\u0000"
                        + resource.reference(),
                    service.name()));
        summaries.add(
            new ServiceOnboardingDto.ExistingService(
                service.id(),
                service.name(),
                service.environment(),
                resources.stream()
                    .filter(
                        resource ->
                            !resource.type().equals("ENDPOINT")
                                || safeEndpoint(resource.reference()))
                    .map(
                        resource ->
                            new ServiceOnboardingDto.ResourceLink(
                                resource.type(), resource.reference(), resource.deviceId()))
                    .toList()));
      } catch (Exception ignored) {
        summaries.add(
            new ServiceOnboardingDto.ExistingService(
                service.id(), service.name(), service.environment(), List.of()));
      }
    }
    List<ServiceOnboardingDto.Candidate> correlated =
        candidates.stream()
            .distinct()
            .map(item -> correlate(target, item, candidates, boundResources, githubHints))
            .toList();
    Set<String> runtimeDevices =
        correlated.stream()
            .filter(item -> item.type().equals("DOCKER_CONTAINER") && item.selected())
            .map(ServiceOnboardingDto.Candidate::deviceId)
            .collect(java.util.stream.Collectors.toSet());
    correlated =
        correlated.stream()
            .map(
                item -> {
                  if (!item.type().equals("DEVICE") || !runtimeDevices.contains(item.reference()))
                    return item;
                  return new ServiceOnboardingDto.Candidate(
                      item.type(),
                      item.reference(),
                      item.deviceId(),
                      item.displayName(),
                      "HIGH",
                      "선택한 컨테이너가 이 장비에서 실행됩니다.",
                      true,
                      false,
                      item.composeProject(),
                      item.composeService(),
                      item.image(),
                      item.state(),
                      item.containerId(),
                      item.ports(),
                      item.workingDirectory());
                })
            .toList();
    List<String> questions = new ArrayList<>();
    List<ServiceOnboardingDto.Candidate> selectedContainers =
        correlated.stream()
            .filter(item -> item.type().equals("DOCKER_CONTAINER") && item.selected())
            .toList();
    List<ServiceOnboardingDto.Candidate> matchingRepositories =
        correlated.stream()
            .filter(item -> item.type().equals("GITHUB_REPOSITORY"))
            .filter(
                repo ->
                    selectedContainers.stream()
                        .anyMatch(container -> repositoryMatchesImage(repo, container)))
            .toList();
    List<ServiceOnboardingDto.Candidate> nameRepositories =
        correlated.stream()
            .filter(item -> item.type().equals("GITHUB_REPOSITORY") && item.selected())
            .toList();
    ServiceOnboardingDto.Candidate preferredRepository =
        matchingRepositories.size() == 1
            ? matchingRepositories.get(0)
            : nameRepositories.size() == 1 ? nameRepositories.get(0) : null;
    if (preferredRepository == null
        && (matchingRepositories.size() > 1 || nameRepositories.size() > 1))
      questions.add("관련 저장소가 여러 개입니다. 서비스에 연결할 저장소를 선택해 주세요.");
    correlated =
        correlated.stream()
            .map(
                item -> {
                  if (!item.type().equals("GITHUB_REPOSITORY")) return item;
                  boolean selected =
                      item == preferredRepository
                          && !boundResources.containsKey(
                              item.type()
                                  + "\u0000"
                                  + item.deviceId()
                                  + "\u0000"
                                  + item.reference());
                  return new ServiceOnboardingDto.Candidate(
                      item.type(),
                      item.reference(),
                      item.deviceId(),
                      item.displayName(),
                      selected ? "HIGH" : item.confidence(),
                      selected && matchingRepositories.contains(item)
                          ? "선택한 컨테이너 이미지 이름과 일치합니다."
                          : item.reason(),
                      selected,
                      !selected,
                      item.composeProject(),
                      item.composeService(),
                      item.image(),
                      item.state(),
                      item.containerId(),
                      item.ports(),
                      item.workingDirectory());
                })
            .toList();
    var projects =
        correlated.stream()
            .filter(
                item -> item.type().equals("DOCKER_CONTAINER") && !item.composeProject().isBlank())
            .collect(
                java.util.stream.Collectors.groupingBy(
                    ServiceOnboardingDto.Candidate::composeProject));
    projects.forEach(
        (project, members) -> {
          if (members.size() > 1)
            questions.add(
                "Compose project '"
                    + project
                    + "'의 컨테이너 "
                    + members.stream().map(ServiceOnboardingDto.Candidate::reference).toList()
                    + " 중 어느 항목을 같은 서비스로 묶을까요?");
        });
    Map<String, String> sourceStatus;
    synchronized (sources) {
      sourceStatus = Map.copyOf(sources);
    }
    return new ServiceOnboardingDto.Discovery(correlated, summaries, sourceStatus, questions);
  }

  private CompletableFuture<List<ServiceOnboardingDto.Candidate>> source(
      String name,
      Supplier<List<ServiceOnboardingDto.Candidate>> fetch,
      Map<String, String> sources) {
    var ownerContext = SecurityContextHolder.getContext();
    return CompletableFuture.supplyAsync(
            () -> {
              SecurityContextHolder.setContext(ownerContext);
              try {
                List<ServiceOnboardingDto.Candidate> values = fetch.get();
                synchronized (sources) {
                  sources.put(name, "OK");
                }
                return values;
              } catch (Exception exception) {
                synchronized (sources) {
                  sources.put(name, "UNAVAILABLE");
                }
                return List.<ServiceOnboardingDto.Candidate>of();
              } finally {
                SecurityContextHolder.clearContext();
              }
            },
            workers)
        .completeOnTimeout(
            List.of(), Set.of("GitHub", "Workspace").contains(name) ? 25 : 15, TimeUnit.SECONDS);
  }

  private List<ServiceOnboardingDto.Candidate> containers(String deviceId) {
    List<ServiceOnboardingDto.Candidate> result = new ArrayList<>();
    // Fixed Docker CLI formatting through DeviceOperations; no shell supplied by the model.
    for (String line : devices.containers(deviceId).lines().limit(50).toList()) {
      try {
        JsonNode item = json.readTree(line);
        String name = item.path("Names").asText("");
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")) continue;
        String labels = item.path("Labels").asText("");
        String image = item.path("Image").asText("");
        String containerId = item.path("ID").asText("");
        String ports = item.path("Ports").asText("");
        result.add(
            new ServiceOnboardingDto.Candidate(
                "DOCKER_CONTAINER",
                name,
                deviceId,
                name,
                "LOW",
                "Docker metadata에서 확인했습니다.",
                false,
                true,
                label(labels, "com.docker.compose.project"),
                label(labels, "com.docker.compose.service"),
                image.substring(0, Math.min(200, image.length())),
                item.path("State").asText(""),
                containerId.substring(0, Math.min(64, containerId.length())),
                ports.substring(0, Math.min(200, ports.length())),
                label(labels, "com.docker.compose.project.working_dir")));
      } catch (Exception ignored) {
        /* Malformed Docker lines are skipped. */
      }
    }
    return result;
  }

  private List<ServiceOnboardingDto.Candidate> repositories(
      String target, Map<String, Integer> hints) {
    List<ServiceOnboardingDto.Candidate> result = new ArrayList<>();
    List<com.personal.dashboard.github.dto.GithubDto.Repository> repositories = new ArrayList<>();
    List<com.personal.dashboard.github.dto.GithubDto.Owner> owners;
    try {
      owners = github.owners().stream().limit(10).toList();
    } catch (Exception ignored) {
      owners = List.of();
    }
    for (var owner : owners) {
      try {
        repositories.addAll(github.repositories(owner.login()));
      } catch (Exception ignored) {
        /* One inaccessible owner must not hide repositories from other owners. */
      }
    }
    if (repositories.isEmpty()) repositories.addAll(github.repositories());
    repositories = repositories.stream().distinct().limit(1000).toList();
    for (var repo : repositories) {
      String name = repo.nameWithOwner();
      int hint = affinity(target, java.util.Objects.toString(repo.description(), ""));
      if (hint > 0) hints.put(name, 1);
      result.add(candidate("GITHUB_REPOSITORY", name, "", name, "", "", "", "", ""));
    }
    repositories.stream()
        .filter(
            repo ->
                affinity(
                        target,
                        repo.nameWithOwner().substring(repo.nameWithOwner().lastIndexOf('/') + 1))
                    > 0)
        .limit(2)
        .forEach(
            repo -> {
              try {
                var detail = github.repository(repo.nameWithOwner());
                if (detail.topics() != null
                    && detail.topics().stream().anyMatch(topic -> affinity(target, topic) > 0))
                  hints.merge(repo.nameWithOwner(), 1, Integer::sum);
              } catch (Exception ignored) {
                /* Repository listing remains available without detail. */
              }
            });
    try {
      owners.stream()
          .filter(owner -> owner.type().equals("ORGANIZATION"))
          .forEach(
              org ->
                  result.add(
                      candidate(
                          "GITHUB_ORGANIZATION",
                          org.login(),
                          "",
                          org.login(),
                          "",
                          "",
                          "",
                          "",
                          "")));
    } catch (Exception ignored) {
      /* Organization access is optional. */
    }
    return result;
  }

  private String label(String labels, String key) {
    for (String part : labels.split(","))
      if (part.startsWith(key + "=")) {
        String value = part.substring(key.length() + 1);
        return value.substring(0, Math.min(100, value.length()));
      }
    return "";
  }

  private ServiceOnboardingDto.Candidate candidate(
      String type,
      String reference,
      String deviceId,
      String displayName,
      String composeProject,
      String composeService,
      String image,
      String reason,
      String state) {
    return new ServiceOnboardingDto.Candidate(
        type,
        reference,
        deviceId,
        displayName,
        "LOW",
        reason,
        false,
        true,
        composeProject,
        composeService,
        image,
        state,
        "",
        "",
        "");
  }

  private ServiceOnboardingDto.Candidate correlate(
      String target,
      ServiceOnboardingDto.Candidate item,
      List<ServiceOnboardingDto.Candidate> all,
      Map<String, String> boundResources,
      Map<String, Integer> githubHints) {
    String matchName =
        item.type().equals("GITHUB_REPOSITORY")
            ? item.reference().substring(item.reference().lastIndexOf('/') + 1)
            : item.displayName() + " " + item.reference();
    int query = affinity(target, matchName);
    boolean related = query >= 1;
    boolean generic = token(item.reference()).stream().allMatch(GENERIC::contains);
    int signal = related ? 1 : 0;
    String reason = related ? "요청한 서비스 이름과 관련됩니다." : "등록된 Workspace 리소스입니다.";
    if (item.type().equals("GITHUB_REPOSITORY") && githubHints.containsKey(item.reference())) {
      signal += githubHints.get(item.reference());
      reason = "저장소 설명 또는 토픽도 요청과 관련됩니다.";
    }
    if (item.type().equals("TELEMETRY")
        && !token(target).isEmpty()
        && normalize(target).equals(normalize(item.displayName()))) {
      signal += 2;
      reason = "Telemetry 서비스 이름이 요청한 서비스 이름과 일치합니다.";
    }
    if (item.type().equals("DOCKER_CONTAINER")) {
      for (var repo : all) {
        if (!repo.type().equals("GITHUB_REPOSITORY")) continue;
        if (repositoryMatchesImage(repo, item)) {
          signal += 2;
          reason = "저장소 이름과 컨테이너 이미지 또는 이름이 일치합니다.";
          break;
        }
      }
      if (!item.composeProject().isBlank() && affinity(target, item.composeProject()) >= 2)
        signal++;
    }
    String boundService =
        boundResources.get(item.type() + "\u0000" + item.deviceId() + "\u0000" + item.reference());
    boolean bound = boundService != null;
    if (bound) reason += " 기존 서비스 '" + boundService + "'에도 연결되어 있습니다.";
    String confidence =
        signal >= 3 && !generic ? "HIGH" : signal >= 1 && !generic ? "MEDIUM" : "LOW";
    boolean selected =
        !bound && !generic && (item.type().equals("GITHUB_REPOSITORY") ? related : signal >= 3);
    return new ServiceOnboardingDto.Candidate(
        item.type(),
        item.reference(),
        item.deviceId(),
        item.displayName(),
        confidence,
        reason,
        selected,
        !selected || item.type().equals("DOCKER_CONTAINER"),
        item.composeProject(),
        item.composeService(),
        item.image(),
        item.state(),
        item.containerId(),
        item.ports(),
        item.workingDirectory());
  }

  private int affinity(String left, String right) {
    Set<String> a = token(left);
    Set<String> b = token(right);
    return (int) a.stream().filter(b::contains).count();
  }

  private Set<String> token(String value) {
    return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9가-힣]+"))
        .filter(part -> part.length() >= 3 && !GENERIC.contains(part))
        .collect(java.util.stream.Collectors.toSet());
  }

  private String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
  }

  /** An image basename is stronger evidence than an organization or registry name. */
  static boolean repositoryMatchesImage(
      ServiceOnboardingDto.Candidate repository, ServiceOnboardingDto.Candidate container) {
    if (!repository.type().equals("GITHUB_REPOSITORY")
        || !container.type().equals("DOCKER_CONTAINER")) return false;
    String repositoryName =
        repository.reference().substring(repository.reference().lastIndexOf('/') + 1);
    String imageName = container.image().substring(container.image().lastIndexOf('/') + 1);
    imageName = imageName.split("[:@]", 2)[0];
    return !repositoryName.isBlank()
        && !GENERIC.contains(repositoryName.toLowerCase(Locale.ROOT))
        && repositoryName
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9가-힣]", "")
            .equals(imageName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", ""));
  }

  private boolean safeEndpoint(String value) {
    try {
      var uri = java.net.URI.create(value);
      return Set.of("http", "https").contains(uri.getScheme())
          && uri.getHost() != null
          && uri.getUserInfo() == null
          && uri.getRawQuery() == null
          && uri.getFragment() == null;
    } catch (Exception exception) {
      return false;
    }
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
