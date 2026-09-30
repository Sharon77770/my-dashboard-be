package com.personal.dashboard.services.service;

import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.dto.ServiceOnboardingDto;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Keeps one temporary draft per Assistant thread; browser approval is a separate state transition.
 */
@Service
@PreAuthorize("hasRole('OWNER')")
public class ServiceOnboardingService {
  private static final long LIFETIME_MS = 30 * 60 * 1000L;
  private final ServiceDiscoveryService discovery;
  private final ServiceCatalogService catalog;
  private final Validator validator;
  private final Map<String, ServiceOnboardingDto.Draft> drafts = new HashMap<>();
  private final Map<String, String> threadDrafts = new HashMap<>();
  private final Map<String, ServiceOnboardingDto.Discovery> snapshots = new HashMap<>();
  private final Map<String, Long> snapshotTimes = new HashMap<>();
  private final Map<String, java.util.Set<String>> existingBindings = new HashMap<>();

  public ServiceOnboardingService(
      ServiceDiscoveryService discovery, ServiceCatalogService catalog, Validator validator) {
    this.discovery = discovery;
    this.catalog = catalog;
    this.validator = validator;
  }

  /** Returns only compact resource metadata and deterministic correlation hints. */
  public synchronized ServiceOnboardingDto.Discovery discover(String threadId, String query) {
    checkThread(threadId);
    var result = discovery.discover(query);
    snapshots.put(threadId, result);
    snapshotTimes.put(threadId, System.currentTimeMillis());
    return result;
  }

  public synchronized ServiceOnboardingDto.Discovery snapshot(String threadId) {
    checkThread(threadId);
    return snapshots.getOrDefault(
        threadId, new ServiceOnboardingDto.Discovery(List.of(), List.of(), Map.of(), List.of()));
  }

  /** A model may prepare a proposal, but this method never writes to Service Catalog. */
  public synchronized ServiceOnboardingDto.Draft create(ServiceOnboardingDto.DraftRequest request) {
    checkThread(request.threadId());
    var found = snapshots.get(request.threadId());
    if (found == null) throw new WorkspaceException(409, "먼저 서비스 리소스를 탐색해 주세요.");
    ServiceDto.View existing =
        request.serviceId() == null || request.serviceId().isBlank()
            ? null
            : catalog.get(request.serviceId());
    String name = require(request.name(), 100, "name");
    String environment = require(request.environment(), 40, "environment");
    String description = optional(request.description(), 500, "description");
    List<ServiceOnboardingDto.Candidate> base = new ArrayList<>(found.candidates());
    if (existing != null) {
      for (ServiceDto.Resource resource : catalog.resources(existing.id())) {
        if (base.stream()
            .noneMatch(
                item -> matches(item, resource.type(), resource.reference(), resource.deviceId())))
          base.add(
              new ServiceOnboardingDto.Candidate(
                  resource.type(),
                  resource.reference(),
                  resource.deviceId(),
                  resource.label().isBlank() ? resource.reference() : resource.label(),
                  "HIGH",
                  "기존 서비스에 연결되어 있습니다.",
                  true,
                  false,
                  "",
                  "",
                  "",
                  "",
                  "",
                  "",
                  ""));
      }
    }
    List<ServiceDto.ResourceRequest> selection = request.resources();
    if (selection == null) {
      selection =
          base.stream()
              .filter(ServiceOnboardingDto.Candidate::selected)
              .map(this::request)
              .toList();
    } else if (!selection.isEmpty()) {
      // A model's partial proposal is completed from discovered relationships before browser
      // review.
      select(base, selection);
      selection = completeModelSelection(base, found.services(), request.serviceId(), selection);
    }
    if (existing != null) {
      var proposed = new ArrayList<>(selection);
      catalog
          .resources(existing.id())
          .forEach(
              item -> {
                if (proposed.stream()
                    .noneMatch(
                        candidate ->
                            candidate.type().equals(item.type())
                                && candidate.reference().equals(item.reference())
                                && Objects.toString(candidate.deviceId(), "")
                                    .equals(item.deviceId())))
                  proposed.add(
                      new ServiceDto.ResourceRequest(
                          item.type(), item.reference(), item.deviceId(), item.label()));
              });
      selection = proposed;
    }
    List<ServiceOnboardingDto.Candidate> candidates = select(base, selection);
    List<String> questions =
        new ArrayList<>(
            ServiceDiscoveryService.questionsForSelection(found.questions(), candidates));
    if (existing == null
        && found.services().stream().anyMatch(service -> service.name().equalsIgnoreCase(name)))
      questions.add("같은 이름의 서비스가 있습니다. 기존 서비스 수정 또는 새 서비스 생성 중 선택해 주세요.");
    long now = System.currentTimeMillis();
    var draft =
        new ServiceOnboardingDto.Draft(
            UUID.randomUUID().toString(),
            request.threadId(),
            existing == null ? null : existing.id(),
            name,
            description,
            environment,
            "DRAFT",
            1,
            existing == null ? 0 : existing.updatedAt(),
            candidates,
            questions,
            now);
    String previousId = threadDrafts.get(draft.threadId());
    if (previousId != null) {
      drafts.remove(previousId);
      existingBindings.remove(previousId);
    }
    drafts.put(draft.id(), draft);
    threadDrafts.put(draft.threadId(), draft.id());
    if (existing != null)
      existingBindings.put(
          draft.id(),
          catalog.resources(existing.id()).stream()
              .map(item -> key(item.type(), item.reference(), item.deviceId()))
              .collect(java.util.stream.Collectors.toSet()));
    return draft;
  }

  public synchronized ServiceOnboardingDto.Draft get(String id) {
    var draft = drafts.get(id);
    if (draft == null || System.currentTimeMillis() - draft.updatedAt() > LIFETIME_MS) {
      drafts.remove(id);
      throw new WorkspaceException(404, "Draft가 만료되었거나 없습니다. 다시 탐색해 주세요.");
    }
    return draft;
  }

  public synchronized ServiceOnboardingDto.Draft forThread(String threadId) {
    checkThread(threadId);
    String id = threadDrafts.get(threadId);
    return id == null ? null : get(id);
  }

  /** Replaces selection using known candidates; edits revoke any prior approval. */
  public synchronized ServiceOnboardingDto.Draft update(
      String id, ServiceOnboardingDto.DraftUpdate input) {
    var current = editable(id, input.revision());
    var updatedCandidates =
        input.resources() == null
            ? current.candidates()
            : select(current.candidates(), input.resources());
    List<ServiceOnboardingDto.ResourceLink> excludedResources =
        input.excludedResources() == null
            ? current.excludedResources()
            : validateExclusions(current.candidates(), input.excludedResources());
    if (input.excludedResources() != null) {
      List<ServiceOnboardingDto.ResourceLink> explicitExclusions = excludedResources;
      updatedCandidates =
          select(
              updatedCandidates,
              updatedCandidates.stream()
                  .filter(ServiceOnboardingDto.Candidate::selected)
                  .filter(
                      item ->
                          explicitExclusions.stream()
                              .noneMatch(
                                  excluded ->
                                      matches(
                                          item,
                                          excluded.type(),
                                          excluded.reference(),
                                          excluded.deviceId())))
                  .map(this::request)
                  .toList());
    }
    List<ServiceOnboardingDto.Candidate> finalCandidates = updatedCandidates;
    excludedResources =
        excludedResources.stream()
            .filter(
                excluded ->
                    finalCandidates.stream()
                        .noneMatch(
                            item ->
                                item.selected()
                                    && matches(
                                        item,
                                        excluded.type(),
                                        excluded.reference(),
                                        excluded.deviceId())))
            .toList();
    var changed =
        new ServiceOnboardingDto.Draft(
            current.id(),
            current.threadId(),
            current.serviceId(),
            input.name() == null ? current.name() : require(input.name(), 100, "name"),
            input.description() == null
                ? current.description()
                : optional(input.description(), 500, "description"),
            input.environment() == null
                ? current.environment()
                : require(input.environment(), 40, "environment"),
            "DRAFT",
            current.revision() + 1,
            current.serviceUpdatedAt(),
            updatedCandidates,
            ServiceDiscoveryService.questionsForSelection(
                current.questions(), updatedCandidates, excludedResources),
            System.currentTimeMillis(),
            excludedResources);
    drafts.put(id, changed);
    return changed;
  }

  /** Only the OWNER browser endpoint may perform this transition. */
  public synchronized ServiceOnboardingDto.Draft approve(String id, long revision) {
    var current = editable(id, revision);
    if (current.candidates().stream().noneMatch(ServiceOnboardingDto.Candidate::selected))
      throw new WorkspaceException(400, "연결할 리소스를 하나 이상 선택해 주세요.");
    var approved =
        new ServiceOnboardingDto.Draft(
            current.id(),
            current.threadId(),
            current.serviceId(),
            current.name(),
            current.description(),
            current.environment(),
            "APPROVED",
            current.revision(),
            current.serviceUpdatedAt(),
            current.candidates(),
            current.questions(),
            System.currentTimeMillis(),
            current.excludedResources());
    drafts.put(id, approved);
    return approved;
  }

  /** MCP can commit only the exact browser-approved revision. */
  public synchronized ServiceDto.View commit(String id, long revision) {
    var draft = get(id);
    if (!draft.status().equals("APPROVED") || draft.revision() != revision)
      throw new WorkspaceException(403, "브라우저에서 현재 Draft를 먼저 승인해 주세요.");
    var details =
        new ServiceDto.Request(draft.name(), "layers", draft.environment(), draft.description());
    var violations = validator.validate(details);
    if (!violations.isEmpty()) throw new WorkspaceException(400, "서비스 정보를 확인해 주세요.");
    var selected =
        draft.candidates().stream()
            .filter(ServiceOnboardingDto.Candidate::selected)
            .map(this::request)
            .toList();
    var service =
        catalog.applyAssistantDraft(
            draft.serviceId(),
            draft.serviceUpdatedAt(),
            existingBindings.getOrDefault(draft.id(), java.util.Set.of()),
            details,
            selected);
    drafts.put(
        id,
        new ServiceOnboardingDto.Draft(
            draft.id(),
            draft.threadId(),
            service.id(),
            draft.name(),
            draft.description(),
            draft.environment(),
            "COMMITTED",
            draft.revision(),
            service.updatedAt(),
            draft.candidates(),
            draft.questions(),
            System.currentTimeMillis(),
            draft.excludedResources()));
    return service;
  }

  public synchronized void cancel(String id) {
    var draft = get(id);
    if (draft.status().equals("COMMITTED"))
      throw new WorkspaceException(409, "이미 반영된 서비스는 초안으로 취소할 수 없습니다.");
    drafts.remove(id);
    threadDrafts.remove(draft.threadId(), id);
    existingBindings.remove(id);
  }

  /** Cancels only an active draft owned by the supplied conversation. */
  public synchronized boolean cancelForThread(String threadId) {
    checkThread(threadId);
    String id = threadDrafts.get(threadId);
    if (id == null) return false;
    var draft = drafts.get(id);
    if (draft == null || System.currentTimeMillis() - draft.updatedAt() > LIFETIME_MS) {
      drafts.remove(id);
      threadDrafts.remove(threadId, id);
      existingBindings.remove(id);
      return false;
    }
    if (draft.status().equals("COMMITTED")) return false;
    cancel(draft.id());
    return true;
  }

  /** Bounds temporary memory without changing any persisted Service. */
  @Scheduled(fixedDelay = 60000)
  @PreAuthorize("permitAll()")
  public synchronized void cleanup() {
    long now = System.currentTimeMillis();
    drafts.values().removeIf(draft -> now - draft.updatedAt() > LIFETIME_MS);
    threadDrafts.entrySet().removeIf(entry -> !drafts.containsKey(entry.getValue()));
    existingBindings.keySet().removeIf(id -> !drafts.containsKey(id));
    snapshotTimes.entrySet().removeIf(entry -> now - entry.getValue() > LIFETIME_MS);
    snapshots.keySet().removeIf(thread -> !snapshotTimes.containsKey(thread));
  }

  private ServiceOnboardingDto.Draft editable(String id, long revision) {
    var current = get(id);
    if (current.status().equals("COMMITTED") || current.revision() != revision)
      throw new WorkspaceException(409, "Draft가 변경되었습니다. 다시 확인해 주세요.");
    return current;
  }

  private List<ServiceOnboardingDto.Candidate> select(
      List<ServiceOnboardingDto.Candidate> base, List<ServiceDto.ResourceRequest> resources) {
    if (resources.size() > 100) throw new WorkspaceException(400, "리소스는 최대 100개입니다.");
    for (var resource : resources) {
      var violations = validator.validate(resource);
      if (!violations.isEmpty()) throw new WorkspaceException(400, "리소스 입력값을 확인해 주세요.");
      if (base.stream()
          .noneMatch(
              item ->
                  matches(
                      item,
                      resource.type(),
                      resource.reference(),
                      Objects.toString(resource.deviceId(), ""))))
        throw new WorkspaceException(400, "탐색된 리소스에서 선택해 주세요: " + resource.type());
    }
    if (resources.stream()
            .map(item -> item.type() + ":" + item.deviceId() + ":" + item.reference())
            .distinct()
            .count()
        != resources.size()) throw new WorkspaceException(400, "중복 리소스가 있습니다.");
    return base.stream()
        .map(
            item ->
                new ServiceOnboardingDto.Candidate(
                    item.type(),
                    item.reference(),
                    item.deviceId(),
                    item.displayName(),
                    item.confidence(),
                    item.reason(),
                    resources.stream()
                        .anyMatch(
                            resource ->
                                matches(
                                    item,
                                    resource.type(),
                                    resource.reference(),
                                    Objects.toString(resource.deviceId(), ""))),
                    item.requiresConfirmation(),
                    item.composeProject(),
                    item.composeService(),
                    item.image(),
                    item.state(),
                    item.containerId(),
                    item.ports(),
                    item.workingDirectory()))
        .toList();
  }

  /** Accepts explicit exclusions only for discovered Compose containers. */
  private List<ServiceOnboardingDto.ResourceLink> validateExclusions(
      List<ServiceOnboardingDto.Candidate> candidates,
      List<ServiceOnboardingDto.ResourceLink> exclusions) {
    if (exclusions.size() > 100) throw new WorkspaceException(400, "제외 리소스는 최대 100개입니다.");
    for (var excluded : exclusions) {
      if (excluded == null
          || !"DOCKER_CONTAINER".equals(excluded.type())
          || candidates.stream()
              .noneMatch(
                  item ->
                      item.type().equals("DOCKER_CONTAINER")
                          && matches(
                              item,
                              excluded.type(),
                              excluded.reference(),
                              Objects.toString(excluded.deviceId(), ""))))
        throw new WorkspaceException(400, "탐색된 컨테이너에서 제외 대상을 선택해 주세요.");
    }
    List<ServiceOnboardingDto.ResourceLink> normalized =
        exclusions.stream()
            .map(
                item ->
                    new ServiceOnboardingDto.ResourceLink(
                        item.type(), item.reference(), Objects.toString(item.deviceId(), "")))
            .toList();
    if (normalized.stream().distinct().count() != normalized.size())
      throw new WorkspaceException(400, "중복 제외 리소스가 있습니다.");
    return normalized;
  }

  private List<ServiceDto.ResourceRequest> completeModelSelection(
      List<ServiceOnboardingDto.Candidate> candidates,
      List<ServiceOnboardingDto.ExistingService> existingServices,
      String serviceId,
      List<ServiceDto.ResourceRequest> requested) {
    List<ServiceDto.ResourceRequest> completed = new ArrayList<>(requested);
    List<ServiceOnboardingDto.Candidate> selectedContainers =
        candidates.stream()
            .filter(item -> item.type().equals("DOCKER_CONTAINER"))
            .filter(item -> contains(completed, item))
            .toList();
    for (var container : selectedContainers) {
      candidates.stream()
          .filter(item -> item.type().equals("DEVICE"))
          .filter(item -> item.reference().equals(container.deviceId()))
          .findFirst()
          .ifPresent(item -> addIfMissing(completed, item));
      var matchingRepositories =
          candidates.stream()
              .filter(item -> ServiceDiscoveryService.repositoryMatchesImage(item, container))
              .filter(item -> !boundToAnotherService(existingServices, serviceId, item))
              .toList();
      if (matchingRepositories.size() == 1 && !hasRepository(completed))
        addIfMissing(completed, matchingRepositories.get(0));
    }
    var recommendedRepositories =
        candidates.stream()
            .filter(item -> item.type().equals("GITHUB_REPOSITORY") && item.selected())
            .toList();
    if (recommendedRepositories.size() == 1 && !hasRepository(completed))
      addIfMissing(completed, recommendedRepositories.get(0));
    return completed;
  }

  private boolean hasRepository(List<ServiceDto.ResourceRequest> selected) {
    return selected.stream().anyMatch(item -> item.type().equals("GITHUB_REPOSITORY"));
  }

  private boolean boundToAnotherService(
      List<ServiceOnboardingDto.ExistingService> services,
      String serviceId,
      ServiceOnboardingDto.Candidate candidate) {
    return services.stream()
        .filter(service -> !service.id().equals(serviceId))
        .flatMap(service -> service.resources().stream())
        .anyMatch(item -> matches(candidate, item.type(), item.reference(), item.deviceId()));
  }

  private void addIfMissing(
      List<ServiceDto.ResourceRequest> selected, ServiceOnboardingDto.Candidate candidate) {
    if (!contains(selected, candidate)) selected.add(request(candidate));
  }

  private boolean contains(
      List<ServiceDto.ResourceRequest> selected, ServiceOnboardingDto.Candidate candidate) {
    return selected.stream()
        .anyMatch(
            item ->
                matches(
                    candidate,
                    item.type(),
                    item.reference(),
                    Objects.toString(item.deviceId(), "")));
  }

  private boolean matches(
      ServiceOnboardingDto.Candidate item, String type, String reference, String deviceId) {
    return item.type().equals(type)
        && item.reference().equals(reference)
        && item.deviceId().equals(deviceId);
  }

  private String key(String type, String reference, String deviceId) {
    return type + "\u0000" + deviceId + "\u0000" + reference;
  }

  private ServiceDto.ResourceRequest request(ServiceOnboardingDto.Candidate item) {
    return new ServiceDto.ResourceRequest(
        item.type(),
        item.reference(),
        item.deviceId(),
        item.displayName().substring(0, Math.min(100, item.displayName().length())));
  }

  private String require(String value, int max, String field) {
    if (value == null || value.isBlank() || value.length() > max)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + field);
    return value.trim();
  }

  private String optional(String value, int max, String field) {
    if (value != null && value.length() > max)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + field);
    return value == null ? "" : value.trim();
  }

  private void checkThread(String threadId) {
    if (threadId == null || !threadId.matches("[A-Za-z0-9_-]{1,100}"))
      throw new WorkspaceException(400, "대화 ID를 확인해 주세요.");
  }
}
