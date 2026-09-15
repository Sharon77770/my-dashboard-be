package com.personal.dashboard.studio.service;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
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
          "codex-thread-unarchive",
          "codex-thread-fork",
          "codex-thread-compact",
          "codex-thread-rollback",
          "codex-skills",
          "codex-connections",
          "codex-account",
          "codex-review");
  private static final Set<String> AUTH_ACTIONS = Set.of("github-login", "github-status");
  private final CatalogService catalog;
  private final StudioAdapter adapter;
  private final Map<String, Job> jobs = new LinkedHashMap<>();
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

  public StudioService(CatalogService catalog, StudioAdapter adapter) {
    this.catalog = catalog;
    this.adapter = adapter;
  }

  public synchronized JobView start(String owner, Request input) {
    if (!ACTIONS.contains(input.action()) && !AUTH_ACTIONS.contains(input.action()))
      throw new WorkspaceException(400, "지원하지 않는 작업 또는 입력 크기입니다.");
    if (input.args() != null && input.args().context() != null) {
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
    if (jobs.values().stream().filter(Job::running).count() >= 4)
      throw new WorkspaceException(429, "최대 4개 작업을 실행할 수 있습니다.");
    while (jobs.size() >= 32) {
      var oldest = jobs.values().stream().filter(job -> !job.running()).findFirst();
      if (oldest.isEmpty()) break;
      jobs.remove(oldest.get().id);
    }
    var job = new Job(owner, input.action());
    jobs.put(job.id, job);
    workers.submit(
        () -> {
          try {
            adapter.execute(device, input, job.execution, job::accept);
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

  public synchronized void cancel(String owner, String id) {
    owned(owner, id).cancel();
  }

  public synchronized void control(String owner, String id, AssistantDto.Control input) {
    var job = owned(owner, id);
    if (!job.running()
        || !Set.of("codex-run", "codex-review", "codex-thread-compact").contains(job.action))
      throw new WorkspaceException(409, "실행 중인 Codex 작업만 입력을 받을 수 있습니다.");
    adapter.control(job.execution, input);
  }

  private Job owned(String owner, String id) {
    var job = jobs.get(id);
    if (job == null || !job.owner.equals(owner))
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
    final long created = System.currentTimeMillis();
    final StudioAdapter.Execution execution = new StudioAdapter.Execution();
    final List<Event> events = new ArrayList<>();
    String state = "RUNNING", error = "";
    int errorStatus;
    Result result;
    int eventBytes;

    Job(String owner, String action) {
      this.owner = owner;
      this.action = action;
    }

    synchronized boolean running() {
      return state.equals("RUNNING");
    }

    synchronized void accept(StudioAdapter.Message message) {
      if (!running()) return;
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
      }
    }

    synchronized void fail(String message, int status) {
      if (running()) {
        state = "FAILED";
        error = message;
        errorStatus = status;
      }
    }

    void cancel() {
      synchronized (this) {
        if (!running()) return;
        state = "CANCELLED";
        error = "작업이 중지되었습니다. 이미 적용된 파일/Git 변경은 유지됩니다.";
      }
      execution.cancel();
    }

    synchronized JobView view() {
      return new JobView(id, action, state, List.copyOf(events), result, error, errorStatus);
    }
  }
}
