package com.personal.dashboard.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.assistant.service.AssistantEvents;
import com.personal.dashboard.assistant.service.AssistantMcpService;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.github.dto.GithubDto;
import com.personal.dashboard.github.service.GithubService;
import com.personal.dashboard.notes.service.NoteMarkdownConverter;
import com.personal.dashboard.notes.service.NoteService;
import com.personal.dashboard.planner.service.PlannerService;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MCP names use the same GitHub service as REST and mark merge as destructive. */
class GithubMcpToolTest {
  private final GithubService github = mock(GithubService.class);
  private final ObjectMapper json = new ObjectMapper();
  private final AssistantMcpService mcp =
      new AssistantMcpService(
          mock(PlannerService.class),
          mock(CatalogService.class),
          mock(NoteService.class),
          mock(NoteMarkdownConverter.class),
          mock(AssistantEvents.class),
          mock(Validator.class),
          json,
          github);

  @Test
  void requiredToolsAreDiscoverableWithStructuredSchemas() {
    List<String> names = mcp.tools().stream().map(tool -> (String) tool.get("name")).toList();

    for (String name :
        List.of(
            "github.list_organizations",
            "github.list_repositories",
            "github.get_repository",
            "github.get_issue",
            "github.get_pull_request",
            "github.get_workflow_runs",
            "github.get_development_context")) assertTrue(names.contains(name), name);
    Map<String, Object> merge =
        mcp.tools().stream()
            .filter(tool -> tool.get("name").equals("github.merge_pull_request"))
            .findFirst()
            .orElseThrow();
    assertEquals(true, ((Map<?, ?>) merge.get("annotations")).get("destructiveHint"));
  }

  @Test
  void developmentContextDelegatesToGithubService() throws Exception {
    var context =
        new GithubDto.DevelopmentContext(
            null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    when(github.developmentContext("alice/repo")).thenReturn(context);

    var result =
        mcp.call(
            "github.get_development_context", json.readTree("{\"repository\":\"alice/repo\"}"));

    assertEquals(context, result.get("context"));
    verify(github).developmentContext("alice/repo");
  }

  @Test
  void workflowRunIdsSupportGitHubLongIdentifiers() throws Exception {
    when(github.workflowRun("alice/repo", 5000000000L))
        .thenReturn(mock(GithubDto.WorkflowRun.class));
    mcp.call(
        "github.get_workflow_run",
        json.readTree("{\"repository\":\"alice/repo\",\"runId\":5000000000}"));

    verify(github).workflowRun("alice/repo", 5000000000L);
  }
}
