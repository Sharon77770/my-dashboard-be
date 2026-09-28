package com.personal.dashboard.github;

import static org.junit.jupiter.api.Assertions.*;

import com.personal.dashboard.github.service.GithubApprovalService;
import com.personal.dashboard.global.WorkspaceException;
import org.junit.jupiter.api.Test;

/** Dangerous actions require an exact, browser-approved, one-use request. */
class GithubApprovalServiceTest {
  private final GithubApprovalService approvals = new GithubApprovalService();

  @Test
  void mergeApprovalIsExactAndSingleUse() {
    var request = approvals.requestMerge("alice/repo", 7);

    assertEquals(
        403,
        assertThrows(
                WorkspaceException.class,
                () -> approvals.consumeMerge(request.id(), "alice/repo", 7))
            .status());
    approvals.approve(request.id());
    assertEquals(
        403,
        assertThrows(
                WorkspaceException.class,
                () -> approvals.consumeMerge(request.id(), "alice/other", 7))
            .status());
    approvals.consumeMerge(request.id(), "alice/repo", 7);
    assertEquals(
        403,
        assertThrows(
                WorkspaceException.class,
                () -> approvals.consumeMerge(request.id(), "alice/repo", 7))
            .status());
  }

  @Test
  void archiveApprovalCannotAuthorizeMergeOrAnotherRepository() {
    var request = approvals.requestArchive("alice/repo");
    approvals.approve(request.id());

    assertEquals(
        403,
        assertThrows(
                WorkspaceException.class,
                () -> approvals.consumeMerge(request.id(), "alice/repo", 1))
            .status());
    assertEquals(
        403,
        assertThrows(
                WorkspaceException.class,
                () -> approvals.consumeArchive(request.id(), "alice/other"))
            .status());
    approvals.consumeArchive(request.id(), "alice/repo");
  }
}
