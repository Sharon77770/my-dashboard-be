package com.personal.dashboard.github.service;

import com.personal.dashboard.global.WorkspaceException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Keeps short-lived, single-use browser approvals for dangerous GitHub operations. */
@Service
public class GithubApprovalService {
  private static final long VALID_SECONDS = 600;
  private final ConcurrentHashMap<String, Approval> approvals = new ConcurrentHashMap<>();

  public record Approval(
      String id,
      String operation,
      String repository,
      int number,
      Instant expiresAt,
      boolean approved) {}

  public Approval requestMerge(String repository, int number) {
    return request("MERGE_PULL_REQUEST", repository, number);
  }

  public Approval requestArchive(String repository) {
    return request("ARCHIVE_REPOSITORY", repository, 0);
  }

  private Approval request(String operation, String repository, int number) {
    cleanup();
    Approval approval =
        new Approval(
            UUID.randomUUID().toString(),
            operation,
            repository,
            number,
            Instant.now().plusSeconds(VALID_SECONDS),
            false);
    approvals.put(approval.id(), approval);
    return approval;
  }

  public List<Approval> pending() {
    cleanup();
    return approvals.values().stream()
        .filter(approval -> !approval.approved())
        .sorted((left, right) -> left.expiresAt().compareTo(right.expiresAt()))
        .toList();
  }

  public Approval approve(String id) {
    cleanup();
    Approval approved =
        approvals.computeIfPresent(
            id,
            (key, value) ->
                new Approval(
                    value.id(),
                    value.operation(),
                    value.repository(),
                    value.number(),
                    value.expiresAt(),
                    true));
    if (approved == null) throw new WorkspaceException(404, "승인 요청을 찾을 수 없습니다.");
    return approved;
  }

  /** Consumes approval before external side effects; failed calls require a new approval. */
  public void consumeMerge(String id, String repository, int number) {
    consume(id, "MERGE_PULL_REQUEST", repository, number);
  }

  public void consumeArchive(String id, String repository) {
    consume(id, "ARCHIVE_REPOSITORY", repository, 0);
  }

  private void consume(String id, String operation, String repository, int number) {
    cleanup();
    Approval approval = approvals.get(id);
    if (approval == null
        || !approval.approved()
        || !approval.repository().equals(repository)
        || approval.number() != number
        || !approval.operation().equals(operation)
        || !approvals.remove(id, approval))
      throw new WorkspaceException(403, "대시보드에서 이 위험 작업을 승인해 주세요.");
  }

  private void cleanup() {
    Instant now = Instant.now();
    approvals.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
  }
}
