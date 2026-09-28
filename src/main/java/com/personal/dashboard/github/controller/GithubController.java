package com.personal.dashboard.github.controller;

import com.personal.dashboard.github.dto.GithubDto;
import com.personal.dashboard.github.service.GithubApprovalService;
import com.personal.dashboard.github.service.GithubService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** OWNER session API for GitHub data read through the server's gh account. */
@RestController
@RequestMapping("/api/v1/github")
public class GithubController {
  private final GithubService service;

  public GithubController(GithubService service) {
    this.service = service;
  }

  @GetMapping("/status")
  public GithubDto.Status status() {
    return service.status();
  }

  @GetMapping("/repositories")
  public List<GithubDto.Repository> repositories() {
    return service.repositories();
  }

  @GetMapping("/pull-requests")
  public List<GithubDto.PullRequest> pullRequests(@RequestParam String repository) {
    return service.pullRequests(repository);
  }

  @GetMapping("/issues")
  public List<GithubDto.Issue> issues(@RequestParam String repository) {
    return service.issues(repository);
  }

  @GetMapping("/owners")
  public List<GithubDto.Owner> owners() {
    return service.owners();
  }

  @GetMapping("/organizations")
  public List<GithubDto.Owner> organizations() {
    return service.organizations();
  }

  @GetMapping("/organizations/{owner}")
  public GithubDto.Organization organization(
      @org.springframework.web.bind.annotation.PathVariable String owner) {
    return service.organization(owner);
  }

  @GetMapping("/organizations/{owner}/members")
  public List<GithubDto.Member> members(
      @org.springframework.web.bind.annotation.PathVariable String owner) {
    return service.organizationMembers(owner);
  }

  @GetMapping("/organizations/{owner}/teams")
  public List<GithubDto.Team> teams(
      @org.springframework.web.bind.annotation.PathVariable String owner) {
    return service.organizationTeams(owner);
  }

  @GetMapping("/owners/{owner}/repositories")
  public List<GithubDto.Repository> ownerRepositories(
      @org.springframework.web.bind.annotation.PathVariable String owner) {
    return service.repositories(owner);
  }

  @PostMapping("/owners/{owner}/repositories")
  @ResponseStatus(HttpStatus.CREATED)
  public GithubDto.RepositoryDetail createRepository(
      @org.springframework.web.bind.annotation.PathVariable String owner,
      @RequestBody GithubDto.CreateRepository request) {
    return service.createRepository(owner, request);
  }

  @GetMapping("/owners/{owner}/overview")
  public GithubDto.OwnerOverview overview(
      @org.springframework.web.bind.annotation.PathVariable String owner) {
    return service.ownerOverview(owner);
  }

  @GetMapping("/owners/{owner}/activity")
  public List<GithubDto.Activity> recentActivity(
      @org.springframework.web.bind.annotation.PathVariable String owner) {
    return service.recentActivity(owner);
  }

  @GetMapping("/owners/{owner}/issues")
  public List<GithubDto.Issue> ownerIssues(
      @org.springframework.web.bind.annotation.PathVariable String owner,
      @RequestParam(defaultValue = "open") String state,
      @RequestParam(defaultValue = "all") String role,
      @RequestParam(required = false) String repository,
      @RequestParam(required = false) String label) {
    return service.filterIssues(owner, state, role, repository, label);
  }

  @GetMapping("/owners/{owner}/pull-requests")
  public List<GithubDto.PullRequest> ownerPullRequests(
      @org.springframework.web.bind.annotation.PathVariable String owner,
      @RequestParam(defaultValue = "open") String state,
      @RequestParam(required = false) String repository) {
    return service.filterPullRequests(owner, state, repository);
  }

  @GetMapping("/owners/{owner}/my-work")
  public GithubDto.MyWork myWork(
      @org.springframework.web.bind.annotation.PathVariable String owner) {
    return service.myWork(owner);
  }

  @GetMapping("/owners/{owner}/search")
  public GithubDto.SearchResult search(
      @org.springframework.web.bind.annotation.PathVariable String owner,
      @RequestParam String query) {
    return service.searchAcrossOwner(owner, query);
  }

  @GetMapping("/repositories/detail")
  public GithubDto.RepositoryDetail repository(@RequestParam String repository) {
    return service.repository(repository);
  }

  @PatchMapping("/repositories")
  public GithubDto.RepositoryDetail updateRepository(
      @RequestParam String repository, @RequestBody GithubDto.UpdateRepository request) {
    return service.updateRepository(repository, request);
  }

  @GetMapping("/repositories/branches")
  public List<GithubDto.Branch> branches(@RequestParam String repository) {
    return service.branches(repository);
  }

  @GetMapping("/repositories/tags")
  public List<GithubDto.Tag> tags(@RequestParam String repository) {
    return service.tags(repository);
  }

  @GetMapping("/repositories/contributors")
  public List<GithubDto.Contributor> contributors(@RequestParam String repository) {
    return service.contributors(repository);
  }

  @GetMapping("/repositories/languages")
  public java.util.Map<String, Integer> languages(@RequestParam String repository) {
    return service.languages(repository);
  }

  @GetMapping("/repositories/tree")
  public List<GithubDto.FileEntry> tree(@RequestParam String repository, @RequestParam String ref) {
    return service.tree(repository, ref);
  }

  @GetMapping("/repositories/file")
  public GithubDto.FileContent file(
      @RequestParam String repository, @RequestParam String path, @RequestParam String ref) {
    return service.file(repository, path, ref);
  }

  @GetMapping("/repositories/commits")
  public List<GithubDto.Commit> commits(@RequestParam String repository) {
    return service.commits(repository);
  }

  @GetMapping("/repositories/commits/{sha}")
  public GithubDto.Commit commit(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable String sha) {
    return service.commit(repository, sha);
  }

  @GetMapping("/repositories/commits/{sha}/files")
  public List<GithubDto.ChangedFile> commitDiff(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable String sha) {
    return service.commitDiff(repository, sha);
  }

  @GetMapping("/issues/detail")
  public GithubDto.IssueDetail issue(@RequestParam String repository, @RequestParam int number) {
    return service.issue(repository, number);
  }

  @PostMapping("/issues")
  @ResponseStatus(HttpStatus.CREATED)
  public GithubDto.IssueDetail createIssue(
      @RequestParam String repository, @Valid @RequestBody GithubDto.CreateIssue request) {
    return service.createIssue(repository, request);
  }

  @PatchMapping("/issues/{number}")
  public GithubDto.IssueDetail updateIssue(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable int number,
      @RequestBody GithubDto.UpdateIssue request) {
    return service.updateIssue(repository, number, request);
  }

  @PostMapping("/issues/{number}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public GithubDto.IssueDetail commentIssue(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable int number,
      @Valid @RequestBody GithubDto.Comment request) {
    return service.commentIssue(repository, number, request);
  }

  @GetMapping("/pull-requests/detail")
  public GithubDto.PullRequestDetail pullRequest(
      @RequestParam String repository, @RequestParam int number) {
    return service.pullRequest(repository, number);
  }

  @PostMapping("/pull-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public GithubDto.PullRequestDetail createPullRequest(
      @RequestParam String repository, @Valid @RequestBody GithubDto.CreatePullRequest request) {
    return service.createPullRequest(repository, request);
  }

  @PatchMapping("/pull-requests/{number}")
  public GithubDto.PullRequestDetail updatePullRequest(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable int number,
      @RequestBody GithubDto.UpdatePullRequest request) {
    return service.updatePullRequest(repository, number, request);
  }

  @PostMapping("/pull-requests/{number}/reviews")
  @ResponseStatus(HttpStatus.CREATED)
  public GithubDto.PullRequestDetail reviewPullRequest(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable int number,
      @Valid @RequestBody GithubDto.Review request) {
    return service.reviewPullRequest(repository, number, request);
  }

  @GetMapping("/approvals")
  public List<GithubApprovalService.Approval> pendingApprovals() {
    return service.pendingApprovals();
  }

  @PostMapping("/approvals/{id}")
  public GithubApprovalService.Approval approveDangerousAction(
      @org.springframework.web.bind.annotation.PathVariable String id) {
    return service.approveDangerousAction(id);
  }

  @GetMapping("/pull-requests/files")
  public List<GithubDto.ChangedFile> pullRequestFiles(
      @RequestParam String repository, @RequestParam int number) {
    return service.pullRequestFiles(repository, number);
  }

  @GetMapping("/pull-requests/context")
  public GithubDto.PullRequestContext pullRequestContext(
      @RequestParam String repository, @RequestParam int number) {
    return service.pullRequestContext(repository, number);
  }

  @GetMapping("/actions/workflows")
  public List<GithubDto.Workflow> workflows(@RequestParam String repository) {
    return service.workflows(repository);
  }

  @GetMapping("/actions/runs")
  public List<GithubDto.WorkflowRun> workflowRuns(@RequestParam String repository) {
    return service.workflowRuns(repository);
  }

  @GetMapping("/actions/runs/{runId}")
  public GithubDto.WorkflowRun workflowRun(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long runId) {
    return service.workflowRun(repository, runId);
  }

  @GetMapping("/actions/runs/{runId}/logs")
  public java.util.Map<String, String> workflowLogs(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long runId) {
    return java.util.Map.of("failedLogs", service.workflowLogs(repository, runId));
  }

  @GetMapping("/actions/runs/{runId}/analysis")
  public GithubDto.WorkflowAnalysis workflowAnalysis(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long runId) {
    return service.analyzeFailedWorkflow(repository, runId);
  }

  @GetMapping("/actions/runs/{runId}/jobs")
  public List<GithubDto.WorkflowJob> workflowJobs(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long runId) {
    return service.workflowJobs(repository, runId);
  }

  @GetMapping("/actions/runs/{runId}/artifacts")
  public List<GithubDto.WorkflowArtifact> workflowArtifacts(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long runId) {
    return service.workflowArtifacts(repository, runId);
  }

  @PostMapping("/actions/runs/{runId}/rerun")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void rerunWorkflow(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long runId) {
    service.rerunWorkflow(repository, runId);
  }

  @PostMapping("/actions/runs/{runId}/cancel")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void cancelWorkflow(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long runId) {
    service.cancelWorkflow(repository, runId);
  }

  @PostMapping("/actions/workflows/{workflowId}/dispatches")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void dispatchWorkflow(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long workflowId,
      @RequestBody GithubDto.DispatchWorkflow request) {
    service.dispatchWorkflow(repository, workflowId, request);
  }

  @GetMapping("/releases")
  public List<GithubDto.Release> releases(@RequestParam String repository) {
    return service.releases(repository);
  }

  @GetMapping("/releases/{releaseId}")
  public GithubDto.Release release(
      @RequestParam String repository,
      @org.springframework.web.bind.annotation.PathVariable long releaseId) {
    return service.release(repository, releaseId);
  }

  @PostMapping("/releases")
  @ResponseStatus(HttpStatus.CREATED)
  public GithubDto.Release createRelease(
      @RequestParam String repository, @RequestBody GithubDto.CreateRelease request) {
    return service.createRelease(repository, request);
  }

  @GetMapping("/repositories/context")
  public GithubDto.DevelopmentContext developmentContext(@RequestParam String repository) {
    return service.developmentContext(repository);
  }
}
