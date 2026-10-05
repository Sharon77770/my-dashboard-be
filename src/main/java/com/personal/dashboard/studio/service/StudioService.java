package com.personal.dashboard.studio.service;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.realtime.service.WorkspaceEvents;
import com.personal.dashboard.studio.adapter.StudioAdapter;
import com.personal.dashboard.studio.dto.AssistantDto;
import com.personal.dashboard.studio.dto.StudioDto.*;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Owns remote jobs, authorization, bounded retention and cancellation lifetimes. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class StudioService {
  private static final Set<String> ACTIONS =
      Set.of(
          "setup",
          "logs-targets",
          "logs-follow",
          "list",
          "read",
          "save",
          "create",
          "mkdir",
          "rename",
          "delete",
          "git-init",
          "git-clone",
          "git-status",
          "git-stage",
          "git-unstage",
          "git-diff",
          "git-commit",
          "git-identity",
          "git-remote",
          "git-branch",
          "git-switch",
          "git-fetch",
          "git-pull",
          "git-push",
          "codex-status",
          "codex-login",
          "codex-logout",
          "codex-run",
          "codex-models",
          "codex-threads",
          "codex-thread-read",
          "codex-thread-new",
          "codex-thread-rename",
          "codex-thread-archive",
          "codex-thread-delete",
          "codex-thread-unarchive",
          "codex-thread-fork",
          "codex-thread-compact",
          "codex-thread-rollback",
          "codex-skills",
          "codex-connections",
          "codex-account",
          "codex-rate-limits",
          "codex-review");
  private static final Set<String> AUTH_ACTIONS = Set.of("github-login", "github-status");
  private final CatalogService catalog;
  private final StudioAdapter adapter;
  private final WorkspaceEvents events;
  private final Map<String, Job> jobs = new LinkedHashMap<>();
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

  public StudioService(CatalogService catalog, StudioAdapter adapter, WorkspaceEvents events) {
    this.catalog = catalog;
    this.adapter = adapter;
    this.events = events;
  }

  public synchronized JobView start(String owner, Request input) {
    return start(owner, input, false);
  }

  /** Dedicated browser assistant entry; only server-local Codex and setup actions are accepted. */
  public synchronized JobView startAssistant(String owner, Request input) {
    return start(owner, input, true);
  }

  private JobView start(String owner, Request input, boolean assistant) {
    return start(owner, input, assistant, null);
  }

  /** Device management uses a separate remote Codex home and job access boundary. */
  public synchronized JobView startDevice(String owner, Request input) {
    if (input.deviceId().equals("local")
        || !(input.action().equals("setup")
            || input.action().startsWith("codex-")
            || AUTH_ACTIONS.contains(input.action())))
      throw new WorkspaceException(400, "장비 Codex는 등록한 SSH 장비의 Codex 작업만 지원합니다.");
    if (!input.root().startsWith("/")) throw new WorkspaceException(400, "장비 작업 폴더는 절대 경로로 입력하세요.");
    return start(owner, input, false, input.deviceId());
  }

  private JobView start(String owner, Request input, boolean assistant, String deviceScope) {
    if (!ACTIONS.contains(input.action()) && !AUTH_ACTIONS.contains(input.action()))
      throw new WorkspaceException(400, "지원하지 않는 작업 또는 입력 크기입니다.");
    if (input.args() != null && input.args().context() != null) {
      if (assistant
          && (!input.action().equals("codex-run")
              || input.args().context().stream()
                  .anyMatch(
                      context ->
                          context == null || !Set.of("image", "upload").contains(context.kind()))))
        throw new WorkspaceException(400, "도우미에는 이미지와 텍스트 파일만 첨부할 수 있습니다.");
      long contextSize =
          input.args().context().stream()
              .filter(Objects::nonNull)
              .mapToLong(
                  context ->
                      (context.dataUrl() == null ? 0 : context.dataUrl().length())
                          + (context.content() == null ? 0 : context.content().length()))
              .sum();
      if (contextSize > 4000000) throw new WorkspaceException(413, "첨부 컨텍스트 전체 크기는 4 MB 이하여야 합니다.");
    }
    var device = catalog.requireDevice(input.deviceId());
    if (assistant
        && (!device.id().equals("local")
            || !(input.action().equals("setup") || input.action().startsWith("codex-"))))
      throw new WorkspaceException(400, "서버 assistant는 local Codex 및 setup 작업만 실행할 수 있습니다.");
    if (!assistant && input.action().startsWith("codex-") && device.id().equals("local"))
      throw new WorkspaceException(400, "프로젝트 Codex는 등록한 SSH 원격 장비에서 실행합니다.");
    if (jobs.values().stream().filter(Job::running).count() >= 4)
      throw new WorkspaceException(429, "최대 4개 작업을 실행할 수 있습니다.");
    while (jobs.size() >= 32) {
      var oldest = jobs.values().stream().filter(job -> !job.running()).findFirst();
      if (oldest.isEmpty()) break;
      jobs.remove(oldest.get().id);
    }
    var job = new Job(owner, input.action(), assistant, deviceScope, events);
    jobs.put(job.id, job);
    workers.submit(
        () -> {
          try {
            if (deviceScope != null)
              adapter.executeDeviceCodex(device, input, job.execution, job::accept);
            else if (assistant) adapter.executeAssistant(device, input, job.execution, job::accept);
            else adapter.execute(device, input, job.execution, job::accept);
            job.finish();
          } catch (WorkspaceException exception) {
            job.fail(exception.getMessage(), exception.status());
          } catch (Exception exception) {
            job.fail("작업 실패: 실행 환경·도구 설치·입력값을 확인해 주세요.", 502);
          }
        });
    return job.view();
  }

  public synchronized JobView get(String owner, String id) {
    return owned(owner, id).view();
  }

  public synchronized JobView getDevice(String owner, String deviceId, String id) {
    return owned(owner, id, deviceId).view();
  }

  public synchronized void cancelDevice(String owner, String deviceId, String id) {
    owned(owner, id, deviceId).cancel();
  }

  public synchronized void controlDevice(
      String owner, String deviceId, String id, AssistantDto.Control input) {
    control(owned(owner, id, deviceId), input);
  }

  public synchronized void cancel(String owner, String id) {
    owned(owner, id).cancel();
  }

  public synchronized void control(String owner, String id, AssistantDto.Control input) {
    control(owned(owner, id), input);
  }

  private void control(Job job, AssistantDto.Control input) {
    if (!job.running()
        || !Set.of("codex-run", "codex-review", "codex-thread-compact").contains(job.action))
      throw new WorkspaceException(409, "실행 중인 Codex 작업만 입력을 받을 수 있습니다.");
    adapter.control(job.execution, input);
  }

  private Job owned(String owner, String id) {
    return owned(owner, id, null);
  }

  private Job owned(String owner, String id, String deviceScope) {
    var job = jobs.get(id);
    if (job == null || !job.owner.equals(owner) || !Objects.equals(job.deviceScope, deviceScope))
      throw new WorkspaceException(404, "작업을 찾을 수 없습니다.");
    return job;
  }

  // Lifecycle callbacks are server-owned, with no request security context.
  @PreAuthorize("permitAll()")
  public synchronized void closeOwner(String owner) {
    jobs.values()
        .removeIf(
            job -> {
              if (!job.owner.equals(owner)) return false;
              job.cancel();
              return true;
            });
  }

  @Scheduled(fixedDelay = 15000)
  @PreAuthorize("permitAll()")
  public synchronized void cleanup() {
    long now = System.currentTimeMillis();
    for (var job : jobs.values()) if (job.running() && now - job.created > 900000) job.cancel();
    jobs.values().removeIf(job -> now - job.created > 1800000);
  }

  @PreDestroy
  @PreAuthorize("permitAll()")
  public synchronized void shutdown() {
    jobs.values().forEach(Job::cancel);
    workers.shutdownNow();
  }

  private static final class Job {
    final String id = UUID.randomUUID().toString();
    final String owner, action;
    final boolean assistant;
    final String deviceScope;
    final WorkspaceEvents notifications;
    final long created = System.currentTimeMillis();
    final StudioAdapter.Execution execution = new StudioAdapter.Execution();
    final List<Event> events = new ArrayList<>();
    String state = "RUNNING", error = "";
    int errorStatus;
    Result result;
    int eventBytes;

    Job(
        String owner,
        String action,
        boolean assistant,
        String deviceScope,
        WorkspaceEvents notifications) {
      this.owner = owner;
      this.action = action;
      this.assistant = assistant;
      this.deviceScope = deviceScope;
      this.notifications = notifications;
    }

    synchronized boolean running() {
      return state.equals("RUNNING");
    }

    synchronized void accept(StudioAdapter.Message message) {
      if (!running()) return;
      notifications.jobChanged(owner, id);
      if (message.error() != null) {
        fail(message.error(), message.status() == null ? 502 : message.status());
        return;
      }
      if (message.result() != null) {
        result = message.result();
        return;
      }
      var event =
          new Event(
              message.event(),
              message.text(),
              message.state(),
              message.url(),
              message.code(),
              message.assistant(),
              message.sequence());
      int size = event.toString().length();
      while (!events.isEmpty() && (events.size() >= 150 || eventBytes + size > 200000))
        eventBytes -= events.removeFirst().toString().length();
      events.add(event);
      eventBytes += size;
    }

    synchronized void finish() {
      if (running()) {
        if (result != null) state = "SUCCEEDED";
        else fail("원격 도구가 결과 없이 종료되었습니다. 도구 준비를 다시 실행해 주세요.", 502);
        notifications.jobChanged(owner, id);
      }
    }

    synchronized void fail(String message, int status) {
      if (running()) {
        state = "FAILED";
        error = message;
        errorStatus = status;
        notifications.jobChanged(owner, id);
      }
    }

    void cancel() {
      synchronized (this) {
        if (!running()) return;
        state = "CANCELLED";
        error = "작업이 중지되었습니다. 이미 적용된 파일/Git 변경은 유지됩니다.";
        notifications.jobChanged(owner, id);
      }
      execution.cancel();
    }

    synchronized JobView view() {
      return new JobView(id, action, state, List.copyOf(events), result, error, errorStatus);
    }
  }
}
