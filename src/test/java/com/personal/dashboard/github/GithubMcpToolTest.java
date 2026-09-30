package com.personal.dashboard.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.assistant.service.AssistantEvents;
import com.personal.dashboard.assistant.service.AssistantMcpService;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.database.service.DatabaseStudioService;
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
  @Test
  void serviceBuilderToolsExposeDraftAndApprovalBoundary() {
    assertTrue(
        mcp.tools().stream()
            .map(item -> item.get("name"))
            .toList()
            .containsAll(
                List.of(
                    "discover_service_resources",
                    "create_service_draft",
                    "update_service_draft",
                    "get_service_draft",
                    "cancel_service_draft",
                    "commit_service_draft")));
    var commit =
        mcp.tools().stream()
            .filter(item -> item.get("name").equals("commit_service_draft"))
            .findFirst()
            .orElseThrow();
    assertEquals(true, ((Map<?, ?>) commit.get("annotations")).get("destructiveHint"));
  }

  private final GithubService github = mock(GithubService.class);
  private final PlannerService planner = mock(PlannerService.class);
  private final DatabaseStudioService databases = mock(DatabaseStudioService.class);
  private final ObjectMapper json = new ObjectMapper();
  private final AssistantMcpService mcp =
      new AssistantMcpService(
          planner,
          mock(CatalogService.class),
          mock(NoteService.class),
          mock(NoteMarkdownConverter.class),
          mock(AssistantEvents.class),
          mock(Validator.class),
          json,
          github,
          mock(com.personal.dashboard.services.service.ServiceCatalogService.class),
          databases,
          mock(com.personal.dashboard.services.service.ServiceOnboardingService.class),
          mock(com.personal.dashboard.assistant.service.WorkspaceMemoryService.class));

  @Test
  void databaseToolsAreReadOnlyAndUseStudioService() {
    for (String name :
        List.of(
            "list_database_connections",
            "get_database_metadata",
            "list_database_tables",
            "describe_database_table")) {
      var registered =
          mcp.tools().stream()
              .filter(item -> item.get("name").equals(name))
              .findFirst()
              .orElseThrow();
      assertEquals(true, ((Map<?, ?>) registered.get("annotations")).get("readOnlyHint"));
    }
    var tool =
        mcp.tools().stream()
            .filter(item -> item.get("name").equals("list_database_connections"))
            .findFirst()
            .orElseThrow();
    assertEquals(true, ((Map<?, ?>) tool.get("annotations")).get("readOnlyHint"));
    when(databases.list()).thenReturn(List.of());
    assertEquals(
        List.of(),
        mcp.call("list_database_connections", json.createObjectNode()).get("connections"));
    verify(databases).list();
    assertFalse(
        mcp.tools().stream().anyMatch(item -> item.get("name").equals("query_database_write")));
  }

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
            "github.get_development_context",
            "github.update_repository",
            "github.update_release",
            "github.request_delete_repository",
            "github.delete_repository",
            "github.request_delete_release",
            "github.delete_release",
            "update_calendar_event",
            "delete_calendar_event",
            "update_note_metadata",
            "replace_note_text",
            "delete_note")) assertTrue(names.contains(name), name);
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

  @Test
  void calendarUpdateAndDeleteDelegateToPlannerService() throws Exception {
    when(planner.saveEvent(eq("event-1"), any()))
        .thenReturn(mock(com.personal.dashboard.planner.dto.PlannerDto.EventView.class));
    mcp.call(
        "update_calendar_event",
        json.readTree(
            "{\"id\":\"event-1\",\"title\":\"회의\",\"start\":\"2026-10-01T09:00\",\"end\":\"2026-10-01T10:00\",\"allDay\":false,\"location\":\"\",\"notes\":\"\",\"color\":\"#6b8afd\"}"));
    verify(planner).saveEvent(eq("event-1"), any());
    assertEquals(
        true,
        mcp.call("delete_calendar_event", json.readTree("{\"id\":\"event-1\"}")).get("deleted"));
    verify(planner).deleteEvent("event-1");
  }
}
