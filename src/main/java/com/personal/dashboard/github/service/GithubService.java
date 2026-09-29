package com.personal.dashboard.github.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.github.adapter.GithubCliAdapter;
import com.personal.dashboard.github.dto.GithubDto;
import com.personal.dashboard.global.WorkspaceException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Provides the dashboard's bounded GitHub read use cases. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class GithubService {
  private final GithubCliAdapter cli;
  private final ObjectMapper json;
  private final GithubApprovalService approvals;

  public GithubService(GithubCliAdapter cli, ObjectMapper json, GithubApprovalService approvals) {
    this.cli = cli;
    this.json = json;
    this.approvals = approvals;
  }

  public GithubDto.Status status() {
    return new GithubDto.Status(cli.authenticated());
  }

  public List<GithubDto.Repository> repositories() {
    requireAuthentication();
    return convert(cli.repositories(), new TypeReference<>() {});
  }

  public List<GithubDto.PullRequest> pullRequests(String repository) {
    requireAuthentication();
    return convert(cli.pullRequests(requireRepository(repository)), new TypeReference<>() {});
  }

  public List<GithubDto.Issue> issues(String repository) {
    requireAuthentication();
    return convert(cli.issues(requireRepository(repository)), new TypeReference<>() {});
  }

  /** The authenticated user and their visible organizations are the GitHub navigation roots. */
  public List<GithubDto.Owner> owners() {
    requireAuthentication();
    JsonNode viewer = cli.api("user");
    List<GithubDto.Owner> owners = new ArrayList<>();
    owners.add(
        new GithubDto.Owner(
            text(viewer, "login"), "USER", text(viewer, "avatar_url"), text(viewer, "html_url")));
    for (JsonNode organization : requireArray(cli.api("user/orgs?per_page=100")))
      owners.add(
          new GithubDto.Owner(
              text(organization, "login"),
              "ORGANIZATION",
              text(organization, "avatar_url"),
              text(organization, "html_url")));
    return owners;
  }

  public List<GithubDto.Owner> organizations() {
    return owners().stream().filter(owner -> owner.type().equals("ORGANIZATION")).toList();
  }

  public GithubDto.Organization organization(String owner) {
    requireAuthentication();
    JsonNode data = cli.api("orgs/" + requireOwner(owner));
    return new GithubDto.Organization(
        text(data, "login"),
        text(data, "name"),
        text(data, "description"),
        text(data, "html_url"),
        data.path("public_repos").asInt(),
        data.hasNonNull("total_private_repos") ? data.get("total_private_repos").asInt() : null);
  }

  public List<GithubDto.Member> organizationMembers(String owner) {
    requireAuthentication();
    List<GithubDto.Member> members = new ArrayList<>();
    for (JsonNode member :
        requireArray(cli.api("orgs/" + requireOwner(owner) + "/members?per_page=100")))
      members.add(
          new GithubDto.Member(
              text(member, "login"), text(member, "avatar_url"), text(member, "html_url")));
    return members;
  }

  public List<GithubDto.Team> organizationTeams(String owner) {
    requireAuthentication();
    List<GithubDto.Team> teams = new ArrayList<>();
    for (JsonNode team :
        requireArray(cli.api("orgs/" + requireOwner(owner) + "/teams?per_page=100")))
      teams.add(
          new GithubDto.Team(
              text(team, "name"),
              text(team, "slug"),
              text(team, "description"),
              text(team, "html_url"),
              text(team, "privacy"),
              text(team, "permission")));
    return teams;
  }

  public List<GithubDto.Repository> repositories(String owner) {
    requireAuthentication();
    return convert(cli.repositories(requireOwner(owner)), new TypeReference<>() {});
  }

  public GithubDto.RepositoryDetail repository(String repository) {
    requireAuthentication();
    JsonNode data = cli.api("repos/" + requireRepository(repository));
    List<String> topics = new ArrayList<>();
    data.path("topics").forEach(topic -> topics.add(topic.asText()));
    return new GithubDto.RepositoryDetail(
        text(data, "full_name"),
        text(data, "description"),
        text(data, "homepage"),
        text(data, "html_url"),
        data.path("private").asBoolean(),
        data.path("archived").asBoolean(),
        data.path("fork").asBoolean(),
        text(data, "default_branch"),
        topics,
        text(data, "language"),
        data.path("open_issues_count").asInt(),
        text(data, "updated_at"));
  }

  public GithubDto.RepositoryDetail createRepository(
      String owner, GithubDto.CreateRepository request) {
    requireAuthentication();
    String validatedOwner = requireOwner(owner);
    String name = requireText(request.name(), 100, "저장소 이름");
    requireRepository(validatedOwner + "/" + name);
    GithubDto.Owner selected =
        owners().stream()
            .filter(item -> item.login().equalsIgnoreCase(validatedOwner))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(400, "접근 가능한 Owner를 선택해 주세요."));
    var body = json.createObjectNode();
    body.put("name", name);
    body.put("description", optionalText(request.description(), 1000, "설명"));
    body.put("private", request.isPrivate());
    String endpoint =
        selected.type().equals("ORGANIZATION") ? "orgs/" + validatedOwner + "/repos" : "user/repos";
    JsonNode created = cli.apiWrite("POST", endpoint, body);
    return repository(text(created, "full_name"));
  }

  public GithubDto.RepositoryDetail updateRepository(
      String repository, GithubDto.UpdateRepository request) {
    requireAuthentication();
    String target = requireRepository(repository);
    var body = json.createObjectNode();
    if (request.description() != null)
      body.put("description", optionalText(request.description(), 1000, "설명"));
    if (request.homepage() != null) {
      String homepage = optionalText(request.homepage(), 2000, "홈페이지");
      if (!homepage.isEmpty() && !homepage.matches("https?://[^\\s]+"))
        throw new WorkspaceException(400, "홈페이지 URL을 확인해 주세요.");
      body.put("homepage", homepage);
    }
    if (request.topics() != null)
      body.set("topics", json.valueToTree(requireNames(request.topics(), "topic")));
    if (body.isEmpty()) throw new WorkspaceException(400, "변경할 저장소 필드가 없습니다.");
    cli.apiWrite("PATCH", "repos/" + target, body);
    return repository(target);
  }

  public GithubApprovalService.Approval requestArchiveRepository(String repository) {
    requireAuthentication();
    String target = requireRepository(repository);
    repository(target);
    return approvals.requestArchive(target);
  }

  public GithubDto.RepositoryDetail archiveRepository(String repository, String approvalId) {
    requireAuthentication();
    String target = requireRepository(repository);
    approvals.consumeArchive(approvalId, target);
    cli.apiWrite("PATCH", "repos/" + target, json.createObjectNode().put("archived", true));
    return repository(target);
  }

  /** Repository deletion requires an exact, short-lived browser approval. */
  public GithubApprovalService.Approval requestDeleteRepository(String repository) {
    requireAuthentication();
    String target = requireRepository(repository);
    repository(target);
    return approvals.requestDeleteRepository(target);
  }

  public void deleteRepository(String repository, String approvalId) {
    requireAuthentication();
    String target = requireRepository(repository);
    approvals.consumeDeleteRepository(approvalId, target);
    cli.apiDelete("repos/" + target);
  }

  public List<GithubDto.Branch> branches(String repository) {
    requireAuthentication();
    List<GithubDto.Branch> branches = new ArrayList<>();
    for (JsonNode branch :
        requireArray(cli.api("repos/" + requireRepository(repository) + "/branches?per_page=100")))
      branches.add(
          new GithubDto.Branch(
              text(branch, "name"),
              text(branch.path("commit"), "sha"),
              branch.path("protected").asBoolean()));
    return branches;
  }

  public List<GithubDto.Tag> tags(String repository) {
    requireAuthentication();
    List<GithubDto.Tag> tags = new ArrayList<>();
    for (JsonNode tag :
        requireArray(cli.api("repos/" + requireRepository(repository) + "/tags?per_page=100")))
      tags.add(new GithubDto.Tag(text(tag, "name"), text(tag.path("commit"), "sha")));
    return tags;
  }

  public List<GithubDto.Contributor> contributors(String repository) {
    requireAuthentication();
    List<GithubDto.Contributor> contributors = new ArrayList<>();
    for (JsonNode user :
        requireArray(
            cli.api("repos/" + requireRepository(repository) + "/contributors?per_page=100")))
      contributors.add(
          new GithubDto.Contributor(
              text(user, "login"), user.path("contributions").asInt(), text(user, "html_url")));
    return contributors;
  }

  public java.util.Map<String, Integer> languages(String repository) {
    requireAuthentication();
    JsonNode data = cli.api("repos/" + requireRepository(repository) + "/languages");
    if (!data.isObject()) throw new WorkspaceException(502, "GitHub 응답 형식을 확인할 수 없습니다.");
    java.util.Map<String, Integer> languages = new java.util.LinkedHashMap<>();
    data.fields()
        .forEachRemaining(entry -> languages.put(entry.getKey(), entry.getValue().asInt()));
    return languages;
  }

  public List<GithubDto.FileEntry> tree(String repository, String ref) {
    requireAuthentication();
    JsonNode data =
        cli.api(
            "repos/"
                + requireRepository(repository)
                + "/git/trees/"
                + requireRef(ref)
                + "?recursive=1");
    List<GithubDto.FileEntry> files = new ArrayList<>();
    for (JsonNode file : requireArray(data.path("tree")))
      files.add(
          new GithubDto.FileEntry(
              text(file, "path"),
              text(file, "type"),
              text(file, "sha"),
              file.path("size").asLong(),
              text(file, "url")));
    return files;
  }

  public GithubDto.FileContent file(String repository, String path, String ref) {
    requireAuthentication();
    JsonNode data =
        cli.api(
            "repos/"
                + requireRepository(repository)
                + "/contents/"
                + requirePath(path)
                + "?ref="
                + requireRef(ref));
    if (!"file".equals(text(data, "type")) || !"base64".equals(text(data, "encoding")))
      throw new WorkspaceException(400, "일반 텍스트 파일만 조회할 수 있습니다.");
    try {
      byte[] bytes = Base64.getMimeDecoder().decode(text(data, "content"));
      if (bytes.length > 512 * 1024) throw new WorkspaceException(502, "파일 크기가 제한을 초과했습니다.");
      return new GithubDto.FileContent(
          text(data, "path"),
          text(data, "sha"),
          data.path("size").asLong(),
          "utf-8",
          new String(bytes, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException exception) {
      throw new WorkspaceException(502, "GitHub 파일 내용을 해석하지 못했습니다.");
    }
  }

  public List<GithubDto.Commit> commits(String repository) {
    requireAuthentication();
    List<GithubDto.Commit> commits = new ArrayList<>();
    for (JsonNode commit :
        requireArray(cli.api("repos/" + requireRepository(repository) + "/commits?per_page=30")))
      commits.add(mapCommit(commit));
    return commits;
  }

  public GithubDto.Commit commit(String repository, String sha) {
    requireAuthentication();
    return mapCommit(
        cli.api("repos/" + requireRepository(repository) + "/commits/" + requireSha(sha)));
  }

  public List<GithubDto.ChangedFile> commitDiff(String repository, String sha) {
    requireAuthentication();
    JsonNode data =
        cli.api("repos/" + requireRepository(repository) + "/commits/" + requireSha(sha));
    return changedFiles(data.path("files"));
  }

  public List<GithubDto.ChangedFile> pullRequestFiles(String repository, int number) {
    requireAuthentication();
    return changedFiles(
        cli.api(
            "repos/"
                + requireRepository(repository)
                + "/pulls/"
                + requireNumber(number)
                + "/files?per_page=100"));
  }

  public GithubDto.PullRequestContext pullRequestContext(String repository, int number) {
    GithubDto.PullRequestDetail detail = pullRequest(repository, number);
    String prefix = "repos/" + requireRepository(repository);
    String requestPath = prefix + "/pulls/" + requireNumber(number);
    List<GithubDto.Commit> commits = new ArrayList<>();
    for (JsonNode commit : requireArray(cli.api(requestPath + "/commits?per_page=100")))
      commits.add(mapCommit(commit));
    List<GithubDto.DiscussionComment> conversation =
        mapComments(cli.api(prefix + "/issues/" + number + "/comments?per_page=100"));
    List<GithubDto.DiscussionComment> reviewComments =
        mapComments(cli.api(requestPath + "/comments?per_page=100"));
    List<GithubDto.CheckRun> checks = new ArrayList<>();
    JsonNode checkResponse =
        cli.api(prefix + "/commits/" + requireSha(detail.headSha()) + "/check-runs?per_page=100");
    for (JsonNode check : requireArray(checkResponse.path("check_runs")))
      checks.add(
          new GithubDto.CheckRun(
              text(check, "name"),
              text(check, "status"),
              text(check, "conclusion"),
              text(check, "html_url")));
    return new GithubDto.PullRequestContext(
        detail,
        pullRequestFiles(repository, number),
        commits,
        conversation,
        reviewComments,
        checks);
  }

  private List<GithubDto.DiscussionComment> mapComments(JsonNode data) {
    List<GithubDto.DiscussionComment> comments = new ArrayList<>();
    for (JsonNode comment : requireArray(data))
      comments.add(
          new GithubDto.DiscussionComment(
              text(comment.path("user"), "login"),
              text(comment, "body"),
              text(comment, "path"),
              text(comment, "html_url"),
              text(comment, "created_at")));
    return comments;
  }

  private List<GithubDto.ChangedFile> changedFiles(JsonNode data) {
    List<GithubDto.ChangedFile> files = new ArrayList<>();
    for (JsonNode file : requireArray(data))
      files.add(
          new GithubDto.ChangedFile(
              text(file, "filename"),
              text(file, "status"),
              file.path("additions").asInt(),
              file.path("deletions").asInt(),
              file.path("changes").asInt(),
              text(file, "patch"),
              text(file, "blob_url")));
    return files;
  }

  private GithubDto.Commit mapCommit(JsonNode data) {
    JsonNode details = data.path("commit");
    return new GithubDto.Commit(
        text(data, "sha"),
        text(details, "message"),
        text(details.path("author"), "name"),
        text(details.path("author"), "date"),
        text(data, "html_url"));
  }

  public GithubDto.IssueDetail issue(String repository, int number) {
    requireAuthentication();
    JsonNode data =
        cli.api("repos/" + requireRepository(repository) + "/issues/" + requireNumber(number));
    List<String> labels = new ArrayList<>();
    data.path("labels").forEach(label -> labels.add(text(label, "name")));
    List<String> assignees = new ArrayList<>();
    data.path("assignees").forEach(assignee -> assignees.add(text(assignee, "login")));
    return new GithubDto.IssueDetail(
        data.path("number").asInt(),
        text(data, "title"),
        text(data, "state"),
        text(data, "html_url"),
        text(data, "body"),
        text(data.path("user"), "login"),
        text(data, "updated_at"),
        labels,
        assignees,
        text(data.path("milestone"), "title"));
  }

  public GithubDto.IssueDetail createIssue(String repository, GithubDto.CreateIssue request) {
    requireAuthentication();
    var body = json.createObjectNode();
    body.put("title", requireText(request.title(), 256, "제목"));
    body.put("body", optionalText(request.body(), 60000, "본문"));
    JsonNode created =
        cli.apiWrite("POST", "repos/" + requireRepository(repository) + "/issues", body);
    return issue(repository, created.path("number").asInt());
  }

  public GithubDto.IssueDetail updateIssue(
      String repository, int number, GithubDto.UpdateIssue request) {
    requireAuthentication();
    String target = requireRepository(repository);
    requireNumber(number);
    var body = json.createObjectNode();
    if (request.title() != null) body.put("title", requireText(request.title(), 256, "제목"));
    if (request.body() != null) body.put("body", optionalText(request.body(), 60000, "본문"));
    if (request.state() != null) body.put("state", requireIssueState(request.state()));
    if (request.labels() != null)
      body.set("labels", json.valueToTree(requireNames(request.labels(), "label")));
    if (request.assignees() != null)
      body.set("assignees", json.valueToTree(requireNames(request.assignees(), "assignee")));
    if (request.milestone() != null) body.put("milestone", requireNumber(request.milestone()));
    if (body.isEmpty()) throw new WorkspaceException(400, "변경할 이슈 필드가 없습니다.");
    cli.apiWrite("PATCH", "repos/" + target + "/issues/" + number, body);
    return issue(target, number);
  }

  public GithubDto.IssueDetail commentIssue(
      String repository, int number, GithubDto.Comment request) {
    requireAuthentication();
    var body = json.createObjectNode();
    body.put("body", requireText(request.body(), 60000, "댓글"));
    cli.apiWrite(
        "POST",
        "repos/" + requireRepository(repository) + "/issues/" + requireNumber(number) + "/comments",
        body);
    return issue(repository, number);
  }

  public GithubDto.PullRequestDetail pullRequest(String repository, int number) {
    requireAuthentication();
    JsonNode data =
        cli.api("repos/" + requireRepository(repository) + "/pulls/" + requireNumber(number));
    return new GithubDto.PullRequestDetail(
        data.path("number").asInt(),
        text(data, "title"),
        text(data, "state"),
        text(data, "html_url"),
        text(data, "body"),
        text(data.path("user"), "login"),
        text(data.path("base"), "ref"),
        text(data.path("head"), "ref"),
        text(data.path("head"), "sha"),
        data.path("draft").asBoolean(),
        data.hasNonNull("mergeable") ? data.get("mergeable").asBoolean() : null,
        text(data, "updated_at"));
  }

  public GithubDto.PullRequestDetail createPullRequest(
      String repository, GithubDto.CreatePullRequest request) {
    requireAuthentication();
    var body = json.createObjectNode();
    body.put("title", requireText(request.title(), 256, "제목"));
    body.put("base", requireText(request.base(), 200, "base"));
    body.put("head", requireText(request.head(), 200, "head"));
    body.put("body", optionalText(request.body(), 60000, "본문"));
    body.put("draft", request.draft());
    JsonNode created =
        cli.apiWrite("POST", "repos/" + requireRepository(repository) + "/pulls", body);
    return pullRequest(repository, created.path("number").asInt());
  }

  public GithubDto.PullRequestDetail updatePullRequest(
      String repository, int number, GithubDto.UpdatePullRequest request) {
    requireAuthentication();
    String target = requireRepository(repository);
    requireNumber(number);
    var body = json.createObjectNode();
    if (request.title() != null) body.put("title", requireText(request.title(), 256, "제목"));
    if (request.body() != null) body.put("body", optionalText(request.body(), 60000, "본문"));
    if (request.state() != null) body.put("state", requireIssueState(request.state()));
    if (request.base() != null) body.put("base", requireText(request.base(), 200, "base"));
    if (body.isEmpty()) throw new WorkspaceException(400, "변경할 PR 필드가 없습니다.");
    cli.apiWrite("PATCH", "repos/" + target + "/pulls/" + number, body);
    return pullRequest(target, number);
  }

  public GithubDto.PullRequestDetail reviewPullRequest(
      String repository, int number, GithubDto.Review request) {
    requireAuthentication();
    if (!List.of("COMMENT", "APPROVE", "REQUEST_CHANGES").contains(request.event()))
      throw new WorkspaceException(400, "리뷰 종류를 확인해 주세요.");
    var body = json.createObjectNode();
    body.put("event", request.event());
    body.put("body", optionalText(request.body(), 60000, "리뷰 본문"));
    cli.apiWrite(
        "POST",
        "repos/" + requireRepository(repository) + "/pulls/" + requireNumber(number) + "/reviews",
        body);
    return pullRequest(repository, number);
  }

  public GithubApprovalService.Approval requestMergePullRequest(String repository, int number) {
    requireAuthentication();
    String target = requireRepository(repository);
    requireNumber(number);
    pullRequest(target, number);
    return approvals.requestMerge(target, number);
  }

  public void mergePullRequest(String repository, int number, String approvalId) {
    requireAuthentication();
    String target = requireRepository(repository);
    requireNumber(number);
    approvals.consumeMerge(approvalId, target, number);
    cli.apiWrite("PUT", "repos/" + target + "/pulls/" + number + "/merge", json.createObjectNode());
  }

  public List<GithubApprovalService.Approval> pendingApprovals() {
    return approvals.pending();
  }

  public GithubApprovalService.Approval approveDangerousAction(String id) {
    return approvals.approve(id);
  }

  public List<GithubDto.WorkflowRun> workflowRuns(String repository) {
    requireAuthentication();
    JsonNode data = cli.api("repos/" + requireRepository(repository) + "/actions/runs?per_page=30");
    List<GithubDto.WorkflowRun> runs = new ArrayList<>();
    for (JsonNode run : requireArray(data.path("workflow_runs"))) runs.add(mapWorkflowRun(run));
    return runs;
  }

  public List<GithubDto.WorkflowArtifact> workflowArtifacts(String repository, long runId) {
    requireAuthentication();
    JsonNode data =
        cli.api(
            "repos/"
                + requireRepository(repository)
                + "/actions/runs/"
                + requireId(runId)
                + "/artifacts?per_page=100");
    List<GithubDto.WorkflowArtifact> artifacts = new ArrayList<>();
    for (JsonNode artifact : requireArray(data.path("artifacts")))
      artifacts.add(
          new GithubDto.WorkflowArtifact(
              artifact.path("id").asLong(),
              text(artifact, "name"),
              artifact.path("size_in_bytes").asLong(),
              artifact.path("expired").asBoolean(),
              text(artifact, "created_at"),
              text(artifact, "expires_at")));
    return artifacts;
  }

  public GithubDto.WorkflowRun workflowRun(String repository, long runId) {
    requireAuthentication();
    return mapWorkflowRun(
        cli.api("repos/" + requireRepository(repository) + "/actions/runs/" + requireId(runId)));
  }

  public String workflowLogs(String repository, long runId) {
    requireAuthentication();
    return cli.failedWorkflowLogs(requireRepository(repository), requireId(runId));
  }

  public GithubDto.WorkflowAnalysis analyzeFailedWorkflow(String repository, long runId) {
    GithubDto.WorkflowRun run = workflowRun(repository, runId);
    List<GithubDto.WorkflowJob> failed =
        workflowJobs(repository, runId).stream()
            .filter(job -> "failure".equals(job.conclusion()))
            .toList();
    return new GithubDto.WorkflowAnalysis(
        run, failed, "failure".equals(run.conclusion()) ? workflowLogs(repository, runId) : "");
  }

  private GithubDto.WorkflowRun mapWorkflowRun(JsonNode run) {
    return new GithubDto.WorkflowRun(
        run.path("id").asLong(),
        text(run, "name"),
        text(run, "status"),
        text(run, "conclusion"),
        text(run, "head_branch"),
        text(run, "event"),
        text(run, "head_sha"),
        text(run, "html_url"),
        text(run, "created_at"),
        text(run, "updated_at"));
  }

  public List<GithubDto.Workflow> workflows(String repository) {
    requireAuthentication();
    List<GithubDto.Workflow> workflows = new ArrayList<>();
    JsonNode data =
        cli.api("repos/" + requireRepository(repository) + "/actions/workflows?per_page=100");
    for (JsonNode workflow : requireArray(data.path("workflows")))
      workflows.add(
          new GithubDto.Workflow(
              workflow.path("id").asLong(),
              text(workflow, "name"),
              text(workflow, "path"),
              text(workflow, "state"),
              text(workflow, "html_url")));
    return workflows;
  }

  public List<GithubDto.WorkflowJob> workflowJobs(String repository, long runId) {
    requireAuthentication();
    JsonNode data =
        cli.api(
            "repos/"
                + requireRepository(repository)
                + "/actions/runs/"
                + requireId(runId)
                + "/jobs?per_page=100");
    List<GithubDto.WorkflowJob> jobs = new ArrayList<>();
    for (JsonNode job : requireArray(data.path("jobs"))) {
      List<GithubDto.WorkflowStep> steps = new ArrayList<>();
      job.path("steps")
          .forEach(
              step ->
                  steps.add(
                      new GithubDto.WorkflowStep(
                          text(step, "name"),
                          text(step, "status"),
                          text(step, "conclusion"),
                          step.path("number").asInt())));
      jobs.add(
          new GithubDto.WorkflowJob(
              job.path("id").asLong(),
              text(job, "name"),
              text(job, "status"),
              text(job, "conclusion"),
              text(job, "html_url"),
              steps));
    }
    return jobs;
  }

  public void rerunWorkflow(String repository, long runId) {
    requireAuthentication();
    cli.apiWrite(
        "POST",
        "repos/" + requireRepository(repository) + "/actions/runs/" + requireId(runId) + "/rerun",
        json.createObjectNode());
  }

  public void cancelWorkflow(String repository, long runId) {
    requireAuthentication();
    cli.apiWrite(
        "POST",
        "repos/" + requireRepository(repository) + "/actions/runs/" + requireId(runId) + "/cancel",
        json.createObjectNode());
  }

  public void dispatchWorkflow(
      String repository, long workflowId, GithubDto.DispatchWorkflow request) {
    requireAuthentication();
    String target = requireRepository(repository);
    requireId(workflowId);
    var body = json.createObjectNode();
    body.put("ref", requireText(request.ref(), 200, "ref"));
    java.util.Map<String, String> inputs =
        request.inputs() == null ? java.util.Map.of() : request.inputs();
    if (inputs.size() > 25) throw new WorkspaceException(400, "Workflow 입력이 너무 많습니다.");
    inputs.forEach(
        (key, value) -> {
          requireText(key, 100, "입력 이름");
          if (value == null || value.length() > 1000)
            throw new WorkspaceException(400, "Workflow 입력값을 확인해 주세요.");
        });
    body.set("inputs", json.valueToTree(inputs));
    cli.apiWrite(
        "POST", "repos/" + target + "/actions/workflows/" + workflowId + "/dispatches", body);
  }

  public List<GithubDto.Release> releases(String repository) {
    requireAuthentication();
    List<GithubDto.Release> releases = new ArrayList<>();
    for (JsonNode release :
        requireArray(cli.api("repos/" + requireRepository(repository) + "/releases?per_page=30")))
      releases.add(mapRelease(release));
    return releases;
  }

  public GithubDto.Release release(String repository, long releaseId) {
    requireAuthentication();
    if (releaseId < 1) throw new WorkspaceException(400, "Release ID를 확인해 주세요.");
    return mapRelease(cli.api("repos/" + requireRepository(repository) + "/releases/" + releaseId));
  }

  public GithubDto.Release createRelease(String repository, GithubDto.CreateRelease request) {
    requireAuthentication();
    String target = requireRepository(repository);
    var body = json.createObjectNode();
    body.put("tag_name", requireText(request.tag(), 100, "Tag"));
    body.put("name", optionalText(request.name(), 256, "Release 이름"));
    body.put("body", optionalText(request.body(), 60000, "Release notes"));
    body.put("draft", request.draft());
    body.put("prerelease", request.prerelease());
    body.put("generate_release_notes", request.generateNotes());
    JsonNode release = cli.apiWrite("POST", "repos/" + target + "/releases", body);
    return mapRelease(release);
  }

  /** Updates only the supplied release metadata through the server gh account. */
  public GithubDto.Release updateRelease(
      String repository, long releaseId, GithubDto.UpdateRelease request) {
    requireAuthentication();
    String target = requireRepository(repository);
    if (releaseId < 1) throw new WorkspaceException(400, "Release ID를 확인해 주세요.");
    var body = json.createObjectNode();
    if (request.tag() != null) body.put("tag_name", requireText(request.tag(), 100, "Tag"));
    if (request.name() != null) body.put("name", optionalText(request.name(), 256, "Release 이름"));
    if (request.body() != null)
      body.put("body", optionalText(request.body(), 60000, "Release notes"));
    if (body.isEmpty()) throw new WorkspaceException(400, "변경할 Release 정보를 입력해 주세요.");
    return mapRelease(cli.apiWrite("PATCH", "repos/" + target + "/releases/" + releaseId, body));
  }

  /** Release deletion leaves the Git tag in place. */
  public GithubApprovalService.Approval requestDeleteRelease(String repository, long releaseId) {
    release(repository, releaseId);
    return approvals.requestDeleteRelease(requireRepository(repository), releaseId);
  }

  public void deleteRelease(String repository, long releaseId, String approvalId) {
    requireAuthentication();
    String target = requireRepository(repository);
    if (releaseId < 1) throw new WorkspaceException(400, "Release ID를 확인해 주세요.");
    approvals.consumeDeleteRelease(approvalId, target, releaseId);
    cli.apiDelete("repos/" + target + "/releases/" + releaseId);
  }

  private GithubDto.Release mapRelease(JsonNode release) {
    List<GithubDto.ReleaseAsset> assets = new ArrayList<>();
    for (JsonNode asset : requireArray(release.path("assets")))
      assets.add(
          new GithubDto.ReleaseAsset(
              text(asset, "name"),
              asset.path("size").asLong(),
              text(asset, "content_type"),
              text(asset, "browser_download_url")));
    return new GithubDto.Release(
        release.path("id").asLong(),
        text(release, "name"),
        text(release, "tag_name"),
        text(release, "body"),
        text(release, "html_url"),
        release.path("draft").asBoolean(),
        release.path("prerelease").asBoolean(),
        text(release, "published_at"),
        assets);
  }

  public List<GithubDto.Issue> ownerIssues(String owner) {
    return filterIssues(owner, "open", "all", null, null);
  }

  public List<GithubDto.PullRequest> ownerPullRequests(String owner) {
    return filterPullRequests(owner, "open", null);
  }

  public List<GithubDto.Issue> filterIssues(
      String owner, String state, String role, String repository, String label) {
    requireAuthentication();
    String scope =
        repository == null
            ? ownerQualifier(owner)
            : "repo:" + requireOwnedRepository(owner, repository);
    String query = scope + " is:issue" + stateQualifier(state);
    query +=
        switch (role == null ? "all" : role) {
          case "all" -> "";
          case "assigned" -> " assignee:@me";
          case "created" -> " author:@me";
          case "mentioned" -> " mentions:@me";
          default -> throw new WorkspaceException(400, "이슈 사용자 필터를 확인해 주세요.");
        };
    if (label != null) query += " label:\"" + requireSearchTerm(label, "label") + "\"";
    return searchIssues(query);
  }

  public List<GithubDto.PullRequest> filterPullRequests(
      String owner, String state, String repository) {
    requireAuthentication();
    String scope =
        repository == null
            ? ownerQualifier(owner)
            : "repo:" + requireOwnedRepository(owner, repository);
    return searchPullRequests(scope + " is:pr" + stateQualifier(state));
  }

  public List<GithubDto.Issue> listRepositoryIssues(String repository, String state) {
    requireAuthentication();
    return searchIssues(
        "repo:" + requireRepository(repository) + " is:issue" + stateQualifier(state));
  }

  public List<GithubDto.PullRequest> listRepositoryPullRequests(String repository, String state) {
    requireAuthentication();
    return searchPullRequests(
        "repo:" + requireRepository(repository) + " is:pr" + stateQualifier(state));
  }

  private String stateQualifier(String state) {
    return switch (state == null ? "open" : state) {
      case "open" -> " is:open";
      case "closed" -> " is:closed";
      case "all" -> "";
      default -> throw new WorkspaceException(400, "상태 필터를 확인해 주세요.");
    };
  }

  private String requireOwnedRepository(String owner, String repository) {
    String target = requireRepository(repository);
    if (!target.substring(0, target.indexOf('/')).equalsIgnoreCase(requireOwner(owner)))
      throw new WorkspaceException(400, "Owner에 속한 저장소를 선택해 주세요.");
    return target;
  }

  private String requireSearchTerm(String value, String label) {
    String term = requireText(value, 100, label).trim();
    if (term.contains("\"") || term.contains("\\"))
      throw new WorkspaceException(400, label + "에 따옴표와 역슬래시를 사용할 수 없습니다.");
    return term;
  }

  public GithubDto.MyWork myWork(String owner) {
    requireAuthentication();
    String scope = ownerQualifier(owner);
    return new GithubDto.MyWork(
        searchIssues(scope + " is:issue is:open assignee:@me"),
        searchPullRequests(scope + " is:pr is:open review-requested:@me"));
  }

  public GithubDto.SearchResult searchAcrossOwner(String owner, String query) {
    requireAuthentication();
    String term = requireText(query, 100, "검색어").trim();
    if (term.contains("\"") || term.contains("\\"))
      throw new WorkspaceException(400, "검색어에 따옴표와 역슬래시를 사용할 수 없습니다.");
    String scope = ownerQualifier(owner) + " \"" + term + "\"";
    return new GithubDto.SearchResult(
        searchIssues(scope + " is:issue"), searchPullRequests(scope + " is:pr"));
  }

  public List<GithubDto.Issue> findRelatedIssues(String repository, String query) {
    requireAuthentication();
    String target = requireRepository(repository);
    String term = requireText(query, 100, "검색어").trim();
    if (term.contains("\"") || term.contains("\\"))
      throw new WorkspaceException(400, "검색어에 따옴표와 역슬래시를 사용할 수 없습니다.");
    return searchIssues("repo:" + target + " is:issue \"" + term + "\"");
  }

  private List<GithubDto.PullRequest> searchPullRequests(String query) {
    JsonNode data =
        cli.api(
            "search/issues?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&per_page=100");
    List<GithubDto.PullRequest> requests = new ArrayList<>();
    for (JsonNode item : requireArray(data.path("items")))
      requests.add(
          new GithubDto.PullRequest(
              item.path("number").asInt(),
              text(item, "title"),
              text(item, "state"),
              text(item, "html_url"),
              text(item, "updated_at"),
              item.path("draft").asBoolean()));
    return requests;
  }

  public GithubDto.DevelopmentContext developmentContext(String repository) {
    GithubDto.RepositoryDetail detail = repository(repository);
    return new GithubDto.DevelopmentContext(
        detail,
        branches(repository),
        commits(repository),
        pullRequests(repository),
        issues(repository),
        workflowRuns(repository),
        releases(repository));
  }

  public GithubDto.OwnerOverview ownerOverview(String owner) {
    GithubDto.Owner selected =
        owners().stream()
            .filter(item -> item.login().equalsIgnoreCase(owner))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(400, "접근 가능한 Owner를 선택해 주세요."));
    Integer memberCount = null;
    if (selected.type().equals("ORGANIZATION")) {
      try {
        memberCount = organizationMembers(owner).size();
      } catch (WorkspaceException exception) {
        // Member visibility depends on the server gh account's organization permissions.
      }
    }
    List<GithubDto.Activity> activity = null;
    try {
      activity = recentActivity(owner);
    } catch (WorkspaceException exception) {
      // Events permissions and temporary upstream failures should not hide the core overview.
    }
    return new GithubDto.OwnerOverview(
        selected,
        repositories(owner),
        ownerIssues(owner),
        ownerPullRequests(owner),
        memberCount,
        activity);
  }

  public List<GithubDto.Activity> recentActivity(String owner) {
    requireAuthentication();
    String validated = requireOwner(owner);
    GithubDto.Owner selected =
        owners().stream()
            .filter(item -> item.login().equalsIgnoreCase(validated))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(400, "접근 가능한 Owner를 선택해 주세요."));
    String endpoint =
        (selected.type().equals("ORGANIZATION")
                ? "orgs/" + validated + "/events"
                : "users/" + validated + "/events")
            + "?per_page=30";
    List<GithubDto.Activity> activity = new ArrayList<>();
    for (JsonNode event : requireArray(cli.api(endpoint))) {
      String repo = text(event.path("repo"), "name");
      String url = text(event.path("payload").path("pull_request"), "html_url");
      if (url == null) url = text(event.path("payload").path("issue"), "html_url");
      if (url == null && repo != null) url = "https://github.com/" + repo;
      activity.add(
          new GithubDto.Activity(
              text(event, "type"),
              text(event.path("actor"), "login"),
              repo,
              text(event.path("payload"), "action"),
              url,
              text(event, "created_at")));
    }
    return activity;
  }

  private String ownerQualifier(String owner) {
    String validated = requireOwner(owner);
    GithubDto.Owner selected =
        owners().stream()
            .filter(item -> item.login().equalsIgnoreCase(validated))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(400, "접근 가능한 Owner를 선택해 주세요."));
    return (selected.type().equals("ORGANIZATION") ? "org:" : "user:") + validated;
  }

  private List<GithubDto.Issue> searchIssues(String query) {
    JsonNode data =
        cli.api(
            "search/issues?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&per_page=100");
    List<GithubDto.Issue> issues = new ArrayList<>();
    for (JsonNode item : requireArray(data.path("items")))
      issues.add(
          new GithubDto.Issue(
              item.path("number").asInt(),
              text(item, "title"),
              text(item, "state"),
              text(item, "html_url"),
              text(item, "updated_at")));
    return issues;
  }

  private JsonNode requireArray(JsonNode data) {
    if (data == null || !data.isArray())
      throw new WorkspaceException(502, "GitHub 응답 형식을 확인할 수 없습니다.");
    return data;
  }

  private String text(JsonNode data, String field) {
    return data == null || data.path(field).isNull() || data.path(field).isMissingNode()
        ? null
        : data.path(field).asText();
  }

  private String requireOwner(String owner) {
    if (owner == null || !owner.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}"))
      throw new WorkspaceException(400, "GitHub Owner 이름을 확인해 주세요.");
    return owner;
  }

  private int requireNumber(int number) {
    if (number < 1) throw new WorkspaceException(400, "번호는 1 이상이어야 합니다.");
    return number;
  }

  private long requireId(long id) {
    if (id < 1) throw new WorkspaceException(400, "ID는 1 이상이어야 합니다.");
    return id;
  }

  private String requireSha(String sha) {
    if (sha == null || !sha.matches("[a-fA-F0-9]{7,40}"))
      throw new WorkspaceException(400, "커밋 SHA를 확인해 주세요.");
    return sha;
  }

  private String requireRef(String ref) {
    if (ref == null
        || !ref.matches("[A-Za-z0-9_.\\/-]{1,200}")
        || ref.contains("..")
        || ref.startsWith("/")
        || ref.endsWith("/")) throw new WorkspaceException(400, "브랜치 또는 태그를 확인해 주세요.");
    return URLEncoder.encode(ref, StandardCharsets.UTF_8);
  }

  private String requirePath(String path) {
    if (path == null
        || path.length() > 500
        || path.startsWith("/")
        || path.endsWith("/")
        || path.contains("..")
        || !path.matches("[A-Za-z0-9_./ -]+")) throw new WorkspaceException(400, "파일 경로를 확인해 주세요.");
    return java.util.Arrays.stream(path.split("/"))
        .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
        .collect(java.util.stream.Collectors.joining("/"));
  }

  private String requireText(String value, int limit, String label) {
    if (value == null || value.isBlank() || value.length() > limit)
      throw new WorkspaceException(400, label + "을(를) 확인해 주세요.");
    return value;
  }

  private String requireIssueState(String state) {
    if (!List.of("open", "closed").contains(state))
      throw new WorkspaceException(400, "상태는 open 또는 closed여야 합니다.");
    return state;
  }

  private List<String> requireNames(List<String> names, String label) {
    if (names.size() > 20) throw new WorkspaceException(400, label + " 목록이 너무 깁니다.");
    return names.stream().map(name -> requireText(name, 100, label)).toList();
  }

  private String optionalText(String value, int limit, String label) {
    if (value != null && value.length() > limit)
      throw new WorkspaceException(400, label + " 길이를 확인해 주세요.");
    return value == null ? "" : value;
  }

  private <T> List<T> convert(JsonNode response, TypeReference<List<T>> type) {
    if (response == null || !response.isArray())
      throw new WorkspaceException(502, "GitHub 응답 형식을 확인할 수 없습니다.");
    try {
      return json.convertValue(response, type);
    } catch (IllegalArgumentException exception) {
      throw new WorkspaceException(502, "GitHub 응답 형식을 확인할 수 없습니다.");
    }
  }

  private void requireAuthentication() {
    if (!cli.authenticated()) throw new WorkspaceException(409, "서버 GitHub 로그인이 필요합니다.");
  }

  private String requireRepository(String repository) {
    if (repository == null
        || !repository.matches("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")
        || repository.contains(".."))
      throw new WorkspaceException(400, "저장소는 owner/name 형식으로 입력해 주세요.");
    return repository;
  }
}
