package com.personal.dashboard.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.github.adapter.GithubCliAdapter;
import com.personal.dashboard.github.service.GithubService;
import com.personal.dashboard.global.WorkspaceException;
import org.junit.jupiter.api.Test;

/** Verifies repository identifiers are bounded before reaching gh. */
class GithubServiceTest {
  private final GithubCliAdapter cli = mock(GithubCliAdapter.class);
  private final GithubService service = new GithubService(cli, new ObjectMapper());

  @Test
  void rejectsInvalidRepositoryBeforeCliCall() {
    when(cli.authenticated()).thenReturn(true);
    for (String input :
        new String[] {"../repo", "owner/repo/other", "owner name/repo", "owner/repo.."}) {
      assertEquals(
          400, assertThrows(WorkspaceException.class, () -> service.issues(input)).status());
    }
    verify(cli, never()).issues(anyString());
  }

  @Test
  void forwardsValidRepositoryAndRequiresAuthentication() {
    when(cli.authenticated()).thenReturn(true, false);
    when(cli.pullRequests("owner/repo")).thenReturn(new ObjectMapper().createArrayNode());
    assertTrue(service.pullRequests("owner/repo").isEmpty());
    assertEquals(
        409,
        assertThrows(WorkspaceException.class, () -> service.pullRequests("owner/repo")).status());
    verify(cli, times(1)).pullRequests("owner/repo");
  }
}
