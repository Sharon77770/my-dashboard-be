package com.personal.dashboard.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.github.dto.GithubDto;
import com.personal.dashboard.github.service.GithubService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.notes.domain.NoteKind;
import com.personal.dashboard.notes.dto.NoteDto;
import com.personal.dashboard.notes.service.NoteMarkdownConverter;
import com.personal.dashboard.notes.service.NoteService;
import com.personal.dashboard.planner.dto.PlannerDto.EventRequest;
import com.personal.dashboard.planner.service.PlannerService;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Exposes a narrow, validated subset of dashboard use cases through Model Context Protocol tools.
 */
@Service
public class AssistantMcpService {
  private static final Set<String> ROUTES =
      Set.of(
          "home",
          "calendar",
          "timetable",
          "notes",
          "cloud",
          "files",
          "devices",
          "apps",
          "logs",
          "terminal",
          "remote",
          "studio",
          "github",
          "recent",
          "clipboard");
  private final PlannerService planner;
  private final CatalogService catalog;
  private final NoteService notes;
  private final NoteMarkdownConverter markdown;
  private final AssistantEvents events;
  private final Validator validator;
  private final ObjectMapper json;
  private final GithubService github;

  public AssistantMcpService(
      PlannerService planner,
      CatalogService catalog,
      NoteService notes,
      NoteMarkdownConverter markdown,
      AssistantEvents events,
      Validator validator,
      ObjectMapper json,
      GithubService github) {
    this.planner = planner;
    this.catalog = catalog;
    this.notes = notes;
    this.markdown = markdown;
    this.events = events;
    this.validator = validator;
    this.json = json;
    this.github = github;
  }

  public List<Map<String, Object>> tools() {
    return List.of(
        tool(
            "github_status",
            "Check whether the dashboard server gh CLI is authenticated.",
            schema(List.of(), Map.of()),
            true),
        tool(
            "list_github_repositories",
            "List up to 50 repositories owned by the authenticated GitHub user.",
            schema(List.of(), Map.of()),
            true),
        tool(
            "list_github_pull_requests",
            "List up to 50 open pull requests in a GitHub repository.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "list_github_issues",
            "List up to 50 open issues in a GitHub repository.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.list_owners",
            "List the signed-in user and accessible organizations.",
            schema(List.of(), Map.of()),
            true),
        tool(
            "github.list_organizations",
            "List organizations visible to the server GitHub account.",
            schema(List.of(), Map.of()),
            true),
        tool(
            "github.get_organization",
            "Get organization details.",
            schema(List.of("owner"), Map.of("owner", string(39))),
            true),
        tool(
            "github.list_repositories",
            "List repositories for a GitHub owner.",
            schema(List.of("owner"), Map.of("owner", string(39))),
            true),
        tool(
            "github.get_repository",
            "Get repository metadata and status.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.create_repository",
            "Create a repository for an accessible owner. WRITE operation.",
            schema(
                List.of("owner", "name"),
                Map.of(
                    "owner",
                    string(39),
                    "name",
                    string(100),
                    "description",
                    string(1000),
                    "isPrivate",
                    Map.of("type", "boolean"))),
            false),
        tool(
            "github.update_repository",
            "Update repository description, homepage, or topics. WRITE operation.",
            schema(
                List.of("repository"),
                Map.of(
                    "repository",
                    string(201),
                    "description",
                    string(1000),
                    "homepage",
                    string(2000),
                    "topics",
                    Map.of("type", "array", "items", string(100), "maxItems", 20))),
            false),
        tool(
            "github.request_archive",
            "Request dashboard approval to archive one repository.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            false),
        dangerousTool(
            "github.archive_repository",
            "Archive a repository after dashboard browser approval.",
            schema(
                List.of("repository", "approvalId"),
                Map.of("repository", string(201), "approvalId", string(36)))),
        tool(
            "github.list_branches",
            "List repository branches.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.list_tags",
            "List repository tags.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.list_contributors",
            "List repository contributors.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.get_languages",
            "Get repository language bytes.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.get_issue",
            "Get an issue including body, labels, assignees and milestone.",
            schema(
                List.of("repository", "number"),
                Map.of("repository", string(201), "number", integer())),
            true),
        tool(
            "github.list_issues",
            "List repository issues in open, closed or all state.",
            schema(
                List.of("repository"),
                Map.of(
                    "repository",
                    string(201),
                    "state",
                    enumeration(Set.of("open", "closed", "all")))),
            true),
        tool(
            "github.search_owner_issues",
            "Filter owner issues by role, repository, label and state.",
            schema(
                List.of("owner"),
                Map.of(
                    "owner",
                    string(39),
                    "state",
                    enumeration(Set.of("open", "closed", "all")),
                    "role",
                    enumeration(Set.of("all", "assigned", "created", "mentioned")),
                    "repository",
                    string(201),
                    "label",
                    string(100))),
            true),
        tool(
            "github.get_pull_request",
            "Get a pull request with base, head and mergeability.",
            schema(
                List.of("repository", "number"),
                Map.of("repository", string(201), "number", integer())),
            true),
        tool(
            "github.list_pull_requests",
            "List repository pull requests in open, closed or all state.",
            schema(
                List.of("repository"),
                Map.of(
                    "repository",
                    string(201),
                    "state",
                    enumeration(Set.of("open", "closed", "all")))),
            true),
        tool(
            "github.search_owner_pull_requests",
            "Filter owner pull requests by state and repository.",
            schema(
                List.of("owner"),
                Map.of(
                    "owner",
                    string(39),
                    "state",
                    enumeration(Set.of("open", "closed", "all")),
                    "repository",
                    string(201))),
            true),
        tool(
            "github.get_workflow_runs",
            "Get recent workflow runs for a repository.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.get_development_context",
            "Get repository, open issues and PRs, and CI runs together.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.get_org_overview",
            "Get owner repositories and open work overview.",
            schema(List.of("owner"), Map.of("owner", string(39))),
            true),
        tool(
            "github.get_recent_activity",
            "Get recent visible user or organization events.",
            schema(List.of("owner"), Map.of("owner", string(39))),
            true),
        tool(
            "github.get_my_work",
            "Get assigned issues and requested PR reviews for an owner.",
            schema(List.of("owner"), Map.of("owner", string(39))),
            true),
        tool(
            "github.search_across_org",
            "Search issues and pull requests across an owner.",
            schema(List.of("owner", "query"), Map.of("owner", string(39), "query", string(100))),
            true),
        tool(
            "github.find_related_issues",
            "Find repository issues related to a phrase.",
            schema(
                List.of("repository", "query"),
                Map.of("repository", string(201), "query", string(100))),
            true),
        tool(
            "github.get_repo_health",
            "Get repository open work and recent CI runs.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.list_org_members",
            "List visible organization members.",
            schema(List.of("owner"), Map.of("owner", string(39))),
            true),
        tool(
            "github.list_org_teams",
            "List visible organization teams.",
            schema(List.of("owner"), Map.of("owner", string(39))),
            true),
        tool(
            "github.get_tree",
            "List a repository tree at a branch, tag or commit.",
            schema(
                List.of("repository", "ref"),
                Map.of("repository", string(201), "ref", string(200))),
            true),
        tool(
            "github.get_file",
            "Read a bounded UTF-8 repository file.",
            schema(
                List.of("repository", "path", "ref"),
                Map.of("repository", string(201), "path", string(500), "ref", string(200))),
            true),
        tool(
            "github.get_commit",
            "Get commit metadata.",
            schema(
                List.of("repository", "sha"), Map.of("repository", string(201), "sha", string(40))),
            true),
        tool(
            "github.get_commit_diff",
            "Get changed files and patches for a commit.",
            schema(
                List.of("repository", "sha"), Map.of("repository", string(201), "sha", string(40))),
            true),
        tool(
            "github.get_pull_request_diff",
            "Get changed files and patches for a pull request.",
            schema(
                List.of("repository", "number"),
                Map.of("repository", string(201), "number", integer())),
            true),
        tool(
            "github.get_pr_context",
            "Get PR details, files, commits, conversation, review comments and checks.",
            schema(
                List.of("repository", "number"),
                Map.of("repository", string(201), "number", integer())),
            true),
        tool(
            "github.list_workflows",
            "List repository workflows.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.get_workflow_jobs",
            "Get jobs and steps for a workflow run.",
            schema(
                List.of("repository", "runId"),
                Map.of("repository", string(201), "runId", integer())),
            true),
        tool(
            "github.list_workflow_artifacts",
            "List workflow run artifact metadata.",
            schema(
                List.of("repository", "runId"),
                Map.of("repository", string(201), "runId", integer())),
            true),
        tool(
            "github.get_workflow_run",
            "Get status and commit of a workflow run.",
            schema(
                List.of("repository", "runId"),
                Map.of("repository", string(201), "runId", integer())),
            true),
        tool(
            "github.get_workflow_logs",
            "Get bounded failed-step logs for a workflow run.",
            schema(
                List.of("repository", "runId"),
                Map.of("repository", string(201), "runId", integer())),
            true),
        tool(
            "github.analyze_failed_workflow",
            "Get failed jobs, steps and logs together.",
            schema(
                List.of("repository", "runId"),
                Map.of("repository", string(201), "runId", integer())),
            true),
        tool(
            "github.list_releases",
            "List recent repository releases.",
            schema(List.of("repository"), Map.of("repository", string(201))),
            true),
        tool(
            "github.get_release",
            "Get release notes and asset metadata.",
            schema(
                List.of("repository", "releaseId"),
                Map.of("repository", string(201), "releaseId", integer())),
            true),
        tool(
            "github.create_issue",
            "Create an issue in a repository. WRITE operation.",
            schema(
                List.of("repository", "title"),
                Map.of("repository", string(201), "title", string(256), "body", string(60000))),
            false),
        tool(
            "github.update_issue",
            "Update title, body, state, labels, assignees or milestone. WRITE operation.",
            schema(
                List.of("repository", "number"),
                Map.of(
                    "repository",
                    string(201),
                    "number",
                    integer(),
                    "title",
                    string(256),
                    "body",
                    string(60000),
                    "state",
                    enumeration(Set.of("open", "closed")),
                    "labels",
                    Map.of("type", "array", "items", string(100), "maxItems", 20),
                    "assignees",
                    Map.of("type", "array", "items", string(100), "maxItems", 20),
                    "milestone",
                    integer())),
            false),
        tool(
            "github.comment_issue",
            "Add a comment to an issue. WRITE operation.",
            schema(
                List.of("repository", "number", "body"),
                Map.of("repository", string(201), "number", integer(), "body", string(60000))),
            false),
        tool(
            "github.create_pull_request",
            "Create a pull request. WRITE operation.",
            schema(
                List.of("repository", "title", "base", "head"),
                Map.of(
                    "repository",
                    string(201),
                    "title",
                    string(256),
                    "base",
                    string(200),
                    "head",
                    string(200),
                    "body",
                    string(60000),
                    "draft",
                    Map.of("type", "boolean"))),
            false),
        tool(
            "github.update_pull_request",
            "Update PR title, body, state or base. WRITE operation.",
            schema(
                List.of("repository", "number"),
                Map.of(
                    "repository",
                    string(201),
                    "number",
                    integer(),
                    "title",
                    string(256),
                    "body",
                    string(60000),
                    "state",
                    enumeration(Set.of("open", "closed")),
                    "base",
                    string(200))),
            false),
        tool(
            "github.review_pull_request",
            "Submit a pull request review. WRITE operation.",
            schema(
                List.of("repository", "number", "event"),
                Map.of(
                    "repository",
                    string(201),
                    "number",
                    integer(),
                    "event",
                    enumeration(Set.of("COMMENT", "APPROVE", "REQUEST_CHANGES")),
                    "body",
                    string(60000))),
            false),
        tool(
            "github.rerun_workflow",
            "Rerun a workflow run. WRITE operation.",
            schema(
                List.of("repository", "runId"),
                Map.of("repository", string(201), "runId", integer())),
            false),
        tool(
            "github.cancel_workflow",
            "Cancel an active workflow run. WRITE operation.",
            schema(
                List.of("repository", "runId"),
                Map.of("repository", string(201), "runId", integer())),
            false),
        tool(
            "github.dispatch_workflow",
            "Dispatch a workflow on a ref with string inputs. WRITE operation.",
            schema(
                List.of("repository", "workflowId", "ref"),
                Map.of(
                    "repository",
                    string(201),
                    "workflowId",
                    integer(),
                    "ref",
                    string(200),
                    "inputs",
                    Map.of(
                        "type",
                        "object",
                        "additionalProperties",
                        string(1000),
                        "maxProperties",
                        25))),
            false),
        tool(
            "github.create_release",
            "Create a GitHub release. WRITE operation.",
            schema(
                List.of("repository", "tag"),
                Map.of(
                    "repository",
                    string(201),
                    "tag",
                    string(100),
                    "name",
                    string(256),
                    "body",
                    string(60000),
                    "draft",
                    Map.of("type", "boolean"),
                    "prerelease",
                    Map.of("type", "boolean"),
                    "generateNotes",
                    Map.of("type", "boolean"))),
            false),
        tool(
            "github.request_merge",
            "Request dashboard approval for merging one PR. No merge occurs yet.",
            schema(
                List.of("repository", "number"),
                Map.of("repository", string(201), "number", integer())),
            false),
        dangerousTool(
            "github.merge_pull_request",
            "Merge the exact PR after dashboard browser approval.",
            schema(
                List.of("repository", "number", "approvalId"),
                Map.of("repository", string(201), "number", integer(), "approvalId", string(36)))),
        tool(
            "open_page",
            "Open a dashboard page in the user's browser.",
            schema(List.of("route"), Map.of("route", enumeration(ROUTES))),
            false),
        tool(
            "list_apps",
            "List the user's registered external applications.",
            schema(List.of(), Map.of()),
            true),
        tool(
            "open_app",
            "Open a registered external application using the user's browser settings.",
            schema(List.of("id"), Map.of("id", string(36))),
            false),
        tool(
            "list_calendar_events",
            "List events already stored in this dashboard's calendar; no external calendar connection is required. "
                + "Use ISO dates and a half-open local date range [from, to). For October of year YYYY, "
                + "use from=YYYY-10-01 and to=YYYY-11-01. The to date is excluded. "
                + "Always call this tool when the user asks to see or explain calendar events; "
                + "an empty events array means there are no saved events in that range.",
            schema(List.of("from", "to"), Map.of("from", date(), "to", date())),
            true),
        tool(
            "create_calendar_event",
            "Create a calendar event. start and end are local ISO date-times; use midnight"
                + " boundaries for all-day events.",
            schema(
                List.of("title", "start", "end"),
                Map.of(
                    "title", string(120),
                    "start", dateTime(),
                    "end", dateTime(),
                    "allDay", Map.of("type", "boolean"),
                    "location", string(200),
                    "notes", string(4000),
                    "color", Map.of("type", "string", "pattern", "^#[0-9a-fA-F]{6}$"))),
            false),
        tool(
            "list_notes",
            "List notebook folders and documents with their IDs and parent folders.",
            schema(List.of(), Map.of()),
            true),
        tool(
            "read_note",
            "Read a notebook document and its editable content blocks.",
            schema(List.of("id"), Map.of("id", string(36))),
            true),
        tool(
            "create_note_folder",
            "Create a notebook folder under the selected parent, or at the notebook root.",
            schema(List.of("title"), Map.of("title", string(200), "parentId", nullableString(36))),
            false),
        tool(
            "create_note",
            "Create a notebook document from Markdown under the selected parent folder.",
            schema(
                List.of("title", "text"),
                Map.of(
                    "title", string(200), "text", string(100000), "parentId", nullableString(36))),
            false),
        tool(
            "append_note",
            "Append Markdown blocks to an existing notebook document using its current revision.",
            schema(
                List.of("id", "text"),
                Map.of("id", string(36), "text", string(100000), "revision", integer())),
            false));
  }

  public Map<String, Object> call(String name, JsonNode args) {
    if (args == null || !args.isObject())
      throw new WorkspaceException(400, "MCP 도구 입력은 JSON 객체여야 합니다.");
    return switch (name) {
      case "open_page" -> openPage(args);
      case "github_status" -> Map.of("authenticated", github.status().authenticated());
      case "list_github_repositories" -> Map.of("repositories", github.repositories());
      case "list_github_pull_requests" ->
          Map.of("pullRequests", github.pullRequests(requiredText(args, "repository", 201)));
      case "list_github_issues" ->
          Map.of("issues", github.issues(requiredText(args, "repository", 201)));
      case "github.list_owners" -> Map.of("owners", github.owners());
      case "github.list_organizations" -> Map.of("organizations", github.organizations());
      case "github.get_organization" ->
          Map.of("organization", github.organization(requiredText(args, "owner", 39)));
      case "github.list_repositories" ->
          Map.of("repositories", github.repositories(requiredText(args, "owner", 39)));
      case "github.get_repository" ->
          Map.of("repository", github.repository(requiredText(args, "repository", 201)));
      case "github.create_repository" ->
          Map.of(
              "repository",
              github.createRepository(
                  requiredText(args, "owner", 39),
                  new GithubDto.CreateRepository(
                      requiredText(args, "name", 100),
                      optionalText(args, "description", 1000),
                      args.path("isPrivate").asBoolean(false))));
      case "github.update_repository" ->
          Map.of(
              "repository",
              github.updateRepository(
                  requiredText(args, "repository", 201),
                  new GithubDto.UpdateRepository(
                      optionalText(args, "description", 1000),
                      optionalText(args, "homepage", 2000),
                      optionalStringList(args, "topics"))));
      case "github.request_archive" ->
          Map.of(
              "approval", github.requestArchiveRepository(requiredText(args, "repository", 201)));
      case "github.archive_repository" ->
          Map.of(
              "repository",
              github.archiveRepository(
                  requiredText(args, "repository", 201), requiredText(args, "approvalId", 36)));
      case "github.list_branches" ->
          Map.of("branches", github.branches(requiredText(args, "repository", 201)));
      case "github.list_tags" -> Map.of("tags", github.tags(requiredText(args, "repository", 201)));
      case "github.list_contributors" ->
          Map.of("contributors", github.contributors(requiredText(args, "repository", 201)));
      case "github.get_languages" ->
          Map.of("languages", github.languages(requiredText(args, "repository", 201)));
      case "github.get_issue" ->
          Map.of(
              "issue",
              github.issue(requiredText(args, "repository", 201), requiredNumber(args, "number")));
      case "github.list_issues" ->
          Map.of(
              "issues",
              github.listRepositoryIssues(
                  requiredText(args, "repository", 201), optionalText(args, "state", 6)));
      case "github.search_owner_issues" ->
          Map.of(
              "issues",
              github.filterIssues(
                  requiredText(args, "owner", 39),
                  optionalText(args, "state", 6),
                  optionalText(args, "role", 9),
                  optionalText(args, "repository", 201),
                  optionalText(args, "label", 100)));
      case "github.get_pull_request" ->
          Map.of(
              "pullRequest",
              github.pullRequest(
                  requiredText(args, "repository", 201), requiredNumber(args, "number")));
      case "github.list_pull_requests" ->
          Map.of(
              "pullRequests",
              github.listRepositoryPullRequests(
                  requiredText(args, "repository", 201), optionalText(args, "state", 6)));
      case "github.search_owner_pull_requests" ->
          Map.of(
              "pullRequests",
              github.filterPullRequests(
                  requiredText(args, "owner", 39),
                  optionalText(args, "state", 6),
                  optionalText(args, "repository", 201)));
      case "github.get_workflow_runs" ->
          Map.of("workflowRuns", github.workflowRuns(requiredText(args, "repository", 201)));
      case "github.get_development_context" ->
          Map.of("context", github.developmentContext(requiredText(args, "repository", 201)));
      case "github.get_org_overview" ->
          Map.of("overview", github.ownerOverview(requiredText(args, "owner", 39)));
      case "github.get_recent_activity" ->
          Map.of("activity", github.recentActivity(requiredText(args, "owner", 39)));
      case "github.get_my_work" -> Map.of("work", github.myWork(requiredText(args, "owner", 39)));
      case "github.search_across_org" ->
          Map.of(
              "results",
              github.searchAcrossOwner(
                  requiredText(args, "owner", 39), requiredText(args, "query", 100)));
      case "github.find_related_issues" ->
          Map.of(
              "issues",
              github.findRelatedIssues(
                  requiredText(args, "repository", 201), requiredText(args, "query", 100)));
      case "github.get_repo_health" ->
          Map.of("health", github.developmentContext(requiredText(args, "repository", 201)));
      case "github.list_org_members" ->
          Map.of("members", github.organizationMembers(requiredText(args, "owner", 39)));
      case "github.list_org_teams" ->
          Map.of("teams", github.organizationTeams(requiredText(args, "owner", 39)));
      case "github.get_tree" ->
          Map.of(
              "tree",
              github.tree(requiredText(args, "repository", 201), requiredText(args, "ref", 200)));
      case "github.get_file" ->
          Map.of(
              "file",
              github.file(
                  requiredText(args, "repository", 201),
                  requiredText(args, "path", 500),
                  requiredText(args, "ref", 200)));
      case "github.get_commit" ->
          Map.of(
              "commit",
              github.commit(requiredText(args, "repository", 201), requiredText(args, "sha", 40)));
      case "github.get_commit_diff" ->
          Map.of(
              "files",
              github.commitDiff(
                  requiredText(args, "repository", 201), requiredText(args, "sha", 40)));
      case "github.get_pull_request_diff" ->
          Map.of(
              "files",
              github.pullRequestFiles(
                  requiredText(args, "repository", 201), requiredNumber(args, "number")));
      case "github.get_pr_context" ->
          Map.of(
              "context",
              github.pullRequestContext(
                  requiredText(args, "repository", 201), requiredNumber(args, "number")));
      case "github.list_workflows" ->
          Map.of("workflows", github.workflows(requiredText(args, "repository", 201)));
      case "github.get_workflow_jobs" ->
          Map.of(
              "jobs",
              github.workflowJobs(
                  requiredText(args, "repository", 201), requiredLong(args, "runId")));
      case "github.list_workflow_artifacts" ->
          Map.of(
              "artifacts",
              github.workflowArtifacts(
                  requiredText(args, "repository", 201), requiredLong(args, "runId")));
      case "github.get_workflow_run" ->
          Map.of(
              "run",
              github.workflowRun(
                  requiredText(args, "repository", 201), requiredLong(args, "runId")));
      case "github.get_workflow_logs" ->
          Map.of(
              "failedLogs",
              github.workflowLogs(
                  requiredText(args, "repository", 201), requiredLong(args, "runId")));
      case "github.analyze_failed_workflow" ->
          Map.of(
              "analysis",
              github.analyzeFailedWorkflow(
                  requiredText(args, "repository", 201), requiredLong(args, "runId")));
      case "github.list_releases" ->
          Map.of("releases", github.releases(requiredText(args, "repository", 201)));
      case "github.get_release" ->
          Map.of(
              "release",
              github.release(
                  requiredText(args, "repository", 201), requiredLong(args, "releaseId")));
      case "github.create_issue" ->
          Map.of(
              "issue",
              github.createIssue(
                  requiredText(args, "repository", 201),
                  new GithubDto.CreateIssue(
                      requiredText(args, "title", 256), optionalText(args, "body", 60000))));
      case "github.update_issue" ->
          Map.of(
              "issue",
              github.updateIssue(
                  requiredText(args, "repository", 201),
                  requiredNumber(args, "number"),
                  new GithubDto.UpdateIssue(
                      optionalText(args, "title", 256),
                      optionalText(args, "body", 60000),
                      optionalText(args, "state", 6),
                      optionalStringList(args, "labels"),
                      optionalStringList(args, "assignees"),
                      args.hasNonNull("milestone") ? requiredNumber(args, "milestone") : null)));
      case "github.comment_issue" ->
          Map.of(
              "issue",
              github.commentIssue(
                  requiredText(args, "repository", 201),
                  requiredNumber(args, "number"),
                  new GithubDto.Comment(requiredText(args, "body", 60000))));
      case "github.create_pull_request" ->
          Map.of(
              "pullRequest",
              github.createPullRequest(
                  requiredText(args, "repository", 201),
                  new GithubDto.CreatePullRequest(
                      requiredText(args, "title", 256),
                      requiredText(args, "base", 200),
                      requiredText(args, "head", 200),
                      optionalText(args, "body", 60000),
                      args.path("draft").asBoolean(false))));
      case "github.update_pull_request" ->
          Map.of(
              "pullRequest",
              github.updatePullRequest(
                  requiredText(args, "repository", 201),
                  requiredNumber(args, "number"),
                  new GithubDto.UpdatePullRequest(
                      optionalText(args, "title", 256),
                      optionalText(args, "body", 60000),
                      optionalText(args, "state", 6),
                      optionalText(args, "base", 200))));
      case "github.review_pull_request" ->
          Map.of(
              "pullRequest",
              github.reviewPullRequest(
                  requiredText(args, "repository", 201),
                  requiredNumber(args, "number"),
                  new GithubDto.Review(
                      requiredText(args, "event", 30), optionalText(args, "body", 60000))));
      case "github.rerun_workflow" -> {
        github.rerunWorkflow(requiredText(args, "repository", 201), requiredLong(args, "runId"));
        yield Map.of("rerunRequested", true);
      }
      case "github.cancel_workflow" -> {
        github.cancelWorkflow(requiredText(args, "repository", 201), requiredLong(args, "runId"));
        yield Map.of("cancelRequested", true);
      }
      case "github.dispatch_workflow" -> {
        github.dispatchWorkflow(
            requiredText(args, "repository", 201),
            requiredLong(args, "workflowId"),
            new GithubDto.DispatchWorkflow(
                requiredText(args, "ref", 200), optionalStringMap(args, "inputs")));
        yield Map.of("dispatchRequested", true);
      }
      case "github.create_release" ->
          Map.of(
              "release",
              github.createRelease(
                  requiredText(args, "repository", 201),
                  new GithubDto.CreateRelease(
                      requiredText(args, "tag", 100), optionalText(args, "name", 256),
                      optionalText(args, "body", 60000), args.path("draft").asBoolean(false),
                      args.path("prerelease").asBoolean(false),
                          args.path("generateNotes").asBoolean(false))));
      case "github.request_merge" ->
          Map.of(
              "approval",
              github.requestMergePullRequest(
                  requiredText(args, "repository", 201), requiredNumber(args, "number")));
      case "github.merge_pull_request" -> {
        github.mergePullRequest(
            requiredText(args, "repository", 201),
            requiredNumber(args, "number"),
            requiredText(args, "approvalId", 36));
        yield Map.of("merged", true);
      }
      case "list_apps" -> Map.of("applications", catalog.applications());
      case "open_app" -> openApplication(args);
      case "list_calendar_events" ->
          Map.of(
              "events",
              planner.events(
                  LocalDate.parse(requiredText(args, "from", 10)),
                  LocalDate.parse(requiredText(args, "to", 10))));
      case "create_calendar_event" -> createEvent(args);
      case "list_notes" -> Map.of("entries", notes.entries());
      case "read_note" -> {
        var document = notes.document(requiredText(args, "id", 36));
        yield Map.of("entry", document.entry(), "blocks", document.blocks());
      }
      case "create_note_folder" -> createFolder(args);
      case "create_note" -> createNote(args);
      case "append_note" -> appendNote(args);
      default -> throw new WorkspaceException(404, "지원하지 않는 MCP 도구입니다.");
    };
  }

  private Map<String, Object> openPage(JsonNode args) {
    String route = requiredText(args, "route", 40);
    if (!ROUTES.contains(route)) throw new WorkspaceException(400, "열 수 없는 대시보드 페이지입니다.");
    var event = events.navigate(route);
    return Map.of("route", route, "opened", true, "sequence", event.sequence());
  }

  private Map<String, Object> openApplication(JsonNode args) {
    String id = requiredText(args, "id", 36);
    var app = catalog.requireApplication(id);
    var event = events.openApplication(id);
    return Map.of(
        "id",
        app.id(),
        "name",
        app.name(),
        "url",
        app.url(),
        "opened",
        true,
        "sequence",
        event.sequence());
  }

  private Map<String, Object> createEvent(JsonNode args) {
    String title = requiredText(args, "title", 120);
    var input =
        new EventRequest(
            title,
            LocalDateTime.parse(requiredText(args, "start", 32)),
            LocalDateTime.parse(requiredText(args, "end", 32)),
            args.path("allDay").asBoolean(false),
            optionalText(args, "location", 200),
            optionalText(args, "notes", 4000),
            args.hasNonNull("color") ? requiredText(args, "color", 7) : "#6b8afd");
    validate(input);
    return Map.of("event", planner.saveEvent(null, input));
  }

  private Map<String, Object> createFolder(JsonNode args) {
    var input =
        new NoteDto.Create(
            NoteKind.FOLDER,
            optionalText(args, "parentId", 36),
            requiredText(args, "title", 200),
            "📁",
            json.createArrayNode());
    validate(input);
    return Map.of("folder", notes.create(input).entry());
  }

  private Map<String, Object> createNote(JsonNode args) {
    var input =
        new NoteDto.Create(
            NoteKind.DOCUMENT,
            optionalText(args, "parentId", 36),
            requiredText(args, "title", 200),
            "📝",
            markdown.blocks(requiredText(args, "text", 100000)));
    validate(input);
    var created = notes.create(input);
    return Map.of("entry", created.entry(), "blocks", created.blocks());
  }

  private Map<String, Object> appendNote(JsonNode args) {
    String id = requiredText(args, "id", 36);
    if (!args.path("revision").canConvertToLong() || args.path("revision").asLong() < 0)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: revision");
    var document = notes.document(id);
    if (args.path("revision").asLong() != document.entry().revision())
      throw new WorkspaceException(409, "메모가 다른 곳에서 변경되었습니다. 최신 내용을 다시 읽어 주세요.");
    var combined = json.createArrayNode();
    if (document.blocks().isArray())
      document.blocks().forEach(block -> combined.add(block.deepCopy()));
    markdown
        .blocks(requiredText(args, "text", 100000))
        .forEach(block -> combined.add(block.deepCopy()));
    var saved = notes.save(id, new NoteDto.Content(combined, document.entry().revision()));
    return Map.of("entry", saved, "appended", true);
  }

  private <T> void validate(T input) {
    var violations = validator.validate(input);
    if (!violations.isEmpty())
      throw new WorkspaceException(
          400, "입력값을 확인해 주세요: " + violations.iterator().next().getPropertyPath());
  }

  private String requiredText(JsonNode args, String name, int limit) {
    JsonNode value = args.get(name);
    if (value == null
        || !value.isTextual()
        || value.asText().isBlank()
        || value.asText().length() > limit)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
    return value.asText();
  }

  private String optionalText(JsonNode args, String name, int limit) {
    JsonNode value = args.get(name);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual() || value.asText().length() > limit)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
    return value.asText();
  }

  private int requiredNumber(JsonNode args, String name) {
    JsonNode value = args.get(name);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
    return value.asInt();
  }

  private long requiredLong(JsonNode args, String name) {
    JsonNode value = args.get(name);
    if (value == null
        || !value.isIntegralNumber()
        || !value.canConvertToLong()
        || value.asLong() < 1) throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
    return value.asLong();
  }

  private List<String> optionalStringList(JsonNode args, String name) {
    JsonNode value = args.get(name);
    if (value == null || value.isNull()) return null;
    if (!value.isArray() || value.size() > 20)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
    List<String> names = new ArrayList<>();
    value.forEach(
        item -> {
          if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 100)
            throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
          names.add(item.asText());
        });
    return names;
  }

  private Map<String, String> optionalStringMap(JsonNode args, String name) {
    JsonNode value = args.get(name);
    if (value == null || value.isNull()) return Map.of();
    if (!value.isObject() || value.size() > 25)
      throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
    Map<String, String> values = new LinkedHashMap<>();
    value
        .fields()
        .forEachRemaining(
            entry -> {
              if (entry.getKey().isBlank()
                  || entry.getKey().length() > 100
                  || !entry.getValue().isTextual()
                  || entry.getValue().asText().length() > 1000)
                throw new WorkspaceException(400, "입력값을 확인해 주세요: " + name);
              values.put(entry.getKey(), entry.getValue().asText());
            });
    return values;
  }

  private Map<String, Object> tool(
      String name, String description, Map<String, Object> schema, boolean readOnly) {
    return Map.of(
        "name", name,
        "description", description,
        "inputSchema", schema,
        "annotations",
            Map.of(
                "title",
                name.replace('_', ' '),
                "readOnlyHint",
                readOnly,
                "destructiveHint",
                false,
                "openWorldHint",
                false));
  }

  private Map<String, Object> dangerousTool(
      String name, String description, Map<String, Object> schema) {
    return Map.of(
        "name", name,
        "description", description,
        "inputSchema", schema,
        "annotations",
            Map.of(
                "title",
                name.replace('_', ' '),
                "readOnlyHint",
                false,
                "destructiveHint",
                true,
                "openWorldHint",
                false));
  }

  private Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
    return Map.of(
        "type",
        "object",
        "properties",
        properties,
        "required",
        required,
        "additionalProperties",
        false);
  }

  private Map<String, Object> string(int max) {
    return Map.of("type", "string", "maxLength", max);
  }

  private Map<String, Object> nullableString(int max) {
    return Map.of("type", List.of("string", "null"), "maxLength", max);
  }

  private Map<String, Object> date() {
    return Map.of("type", "string", "format", "date");
  }

  private Map<String, Object> dateTime() {
    return Map.of("type", "string", "format", "date-time");
  }

  private Map<String, Object> integer() {
    return Map.of("type", "integer", "minimum", 0);
  }

  private Map<String, Object> enumeration(Set<String> values) {
    return Map.of("type", "string", "enum", values.stream().sorted().toList());
  }
}
