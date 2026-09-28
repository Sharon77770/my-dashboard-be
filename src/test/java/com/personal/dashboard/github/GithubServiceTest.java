package com.personal.dashboard.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.github.adapter.GithubCliAdapter;
import com.personal.dashboard.github.dto.GithubDto;
import com.personal.dashboard.github.service.GithubApprovalService;
import com.personal.dashboard.github.service.GithubService;
import com.personal.dashboard.global.WorkspaceException;
import org.junit.jupiter.api.Test;

/** Verifies repository identifiers are bounded before reaching gh. */
class GithubServiceTest {
  private final GithubCliAdapter cli = mock(GithubCliAdapter.class);
  private final GithubService service =
      new GithubService(cli, new ObjectMapper(), mock(GithubApprovalService.class));

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

  @Test
  void includesUserAndAccessibleOrganizationsAsOwners() throws Exception {
    when(cli.authenticated()).thenReturn(true);
    ObjectMapper json = new ObjectMapper();
    when(cli.api("user"))
        .thenReturn(
            json.readTree("{\"login\":\"alice\",\"html_url\":\"https://github.com/alice\"}"));
    when(cli.api("user/orgs?per_page=100"))
        .thenReturn(
            json.readTree(
                "[{\"login\":\"example-org\",\"html_url\":\"https://github.com/example-org\"}]"));

    var owners = service.owners();

    assertEquals(2, owners.size());
    assertEquals("USER", owners.get(0).type());
    assertEquals("ORGANIZATION", owners.get(1).type());
  }

  @Test
  void rejectsUnsafeFilePathBeforeApiCall() {
    when(cli.authenticated()).thenReturn(true);

    assertEquals(
        400,
        assertThrows(
                WorkspaceException.class, () -> service.file("alice/repo", "../secret", "main"))
            .status());
    verify(cli, never()).api(anyString());
  }

  @Test
  void validatesIssueBeforeWrite() {
    when(cli.authenticated()).thenReturn(true);

    assertEquals(
        400,
        assertThrows(
                WorkspaceException.class,
                () -> service.createIssue("alice/repo", new GithubDto.CreateIssue(" ", "body")))
            .status());
    verify(cli, never()).apiWrite(anyString(), anyString(), any());
  }

  @Test
  void ownerIssueFiltersKeepRepositoryScopeAndQualifiers() throws Exception {
    when(cli.authenticated()).thenReturn(true);
    String endpoint =
        "search/issues?q=repo%3Aalice%2Frepo+is%3Aissue+is%3Aclosed"
            + "+assignee%3A%40me+label%3A%22bug%22&per_page=100";
    when(cli.api(endpoint)).thenReturn(new ObjectMapper().readTree("{\"items\":[]}"));

    assertTrue(service.filterIssues("alice", "closed", "assigned", "alice/repo", "bug").isEmpty());
    verify(cli).api(endpoint);
    assertEquals(
        400,
        assertThrows(
                WorkspaceException.class,
                () -> service.filterIssues("alice", "open", "all", "other/repo", null))
            .status());
  }

  @Test
  void organizationActivityUsesOrganizationEventsInsteadOfViewerEvents() throws Exception {
    ObjectMapper json = new ObjectMapper();
    when(cli.authenticated()).thenReturn(true);
    when(cli.api("user")).thenReturn(json.readTree("{\"login\":\"alice\"}"));
    when(cli.api("user/orgs?per_page=100"))
        .thenReturn(json.readTree("[{\"login\":\"example-org\"}]"));
    when(cli.api("orgs/example-org/events?per_page=30"))
        .thenReturn(
            json.readTree("[{\"type\":\"PushEvent\",\"repo\":{\"name\":\"example-org/api\"}}]"));

    var activity = service.recentActivity("example-org");

    assertEquals("example-org/api", activity.get(0).repository());
    verify(cli).api("orgs/example-org/events?per_page=30");
    verify(cli, never()).api("users/alice/events/orgs/example-org?per_page=30");
  }

  @Test
  void releaseDetailIncludesDownloadableAssetMetadata() throws Exception {
    when(cli.authenticated()).thenReturn(true);
    when(cli.api("repos/alice/repo/releases/5000000000"))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    "{\"id\":5000000000,\"tag_name\":\"v1\",\"assets\":[{\"name\":\"app.zip\",\"size\":42,\"content_type\":\"application/zip\",\"browser_download_url\":\"https://github.com/alice/repo/releases/download/v1/app.zip\"}]}"));

    var release = service.release("alice/repo", 5000000000L);

    assertEquals("app.zip", release.assets().get(0).name());
    assertEquals(42, release.assets().get(0).size());
  }

  @Test
  void workflowArtifactsExposeMetadataWithoutArchiveTokenUrl() throws Exception {
    when(cli.authenticated()).thenReturn(true);
    when(cli.api("repos/alice/repo/actions/runs/5000000000/artifacts?per_page=100"))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    "{\"artifacts\":[{\"id\":9,\"name\":\"build\",\"size_in_bytes\":123,\"expired\":false,\"archive_download_url\":\"https://api.github.com/secret\"}]}"));

    var artifacts = service.workflowArtifacts("alice/repo", 5000000000L);

    assertEquals("build", artifacts.get(0).name());
    assertEquals(123, artifacts.get(0).size());
  }
}
