package com.personal.dashboard.github.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Public response shapes for server GitHub reads. */
public final class GithubDto {
  private GithubDto() {}

  public record Status(boolean authenticated) {}

  public record Repository(
      String nameWithOwner,
      String description,
      String url,
      boolean isPrivate,
      boolean isArchived,
      boolean isFork,
      String updatedAt) {}

  public record PullRequest(
      int number, String title, String state, String url, String updatedAt, boolean isDraft) {}

  public record Issue(int number, String title, String state, String url, String updatedAt) {}

  public record Owner(String login, String type, String avatarUrl, String url) {}

  public record Organization(
      String login,
      String name,
      String description,
      String url,
      int publicRepositories,
      Integer totalPrivateRepositories) {}

  public record RepositoryDetail(
      String fullName,
      String description,
      String homepage,
      String url,
      boolean isPrivate,
      boolean isArchived,
      boolean isFork,
      String defaultBranch,
      List<String> topics,
      String language,
      int openIssues,
      String updatedAt) {}

  public record IssueDetail(
      int number,
      String title,
      String state,
      String url,
      String body,
      String author,
      String updatedAt,
      List<String> labels,
      List<String> assignees,
      String milestone) {}

  public record PullRequestDetail(
      int number,
      String title,
      String state,
      String url,
      String body,
      String author,
      String base,
      String head,
      String headSha,
      boolean isDraft,
      Boolean mergeable,
      String updatedAt) {}

  public record WorkflowRun(
      long id,
      String name,
      String status,
      String conclusion,
      String branch,
      String event,
      String commit,
      String url,
      String createdAt,
      String updatedAt) {}

  public record DevelopmentContext(
      RepositoryDetail repository,
      List<Branch> branches,
      List<Commit> recentCommits,
      List<PullRequest> openPullRequests,
      List<Issue> openIssues,
      List<WorkflowRun> recentWorkflowRuns,
      List<Release> recentReleases) {}

  public record OwnerOverview(
      Owner owner,
      List<Repository> repositories,
      List<Issue> openIssues,
      List<PullRequest> openPullRequests,
      Integer memberCount,
      List<Activity> recentActivity) {}

  public record Activity(
      String type, String actor, String repository, String action, String url, String createdAt) {}

  public record Member(String login, String avatarUrl, String url) {}

  public record Team(
      String name,
      String slug,
      String description,
      String url,
      String privacy,
      String permission) {}

  public record FileEntry(String path, String type, String sha, long size, String url) {}

  public record FileContent(String path, String sha, long size, String encoding, String content) {}

  public record Commit(String sha, String message, String author, String date, String url) {}

  public record ChangedFile(
      String filename,
      String status,
      int additions,
      int deletions,
      int changes,
      String patch,
      String url) {}

  public record Workflow(long id, String name, String path, String state, String url) {}

  public record WorkflowStep(String name, String status, String conclusion, int number) {}

  public record WorkflowJob(
      long id,
      String name,
      String status,
      String conclusion,
      String url,
      List<WorkflowStep> steps) {}

  public record WorkflowArtifact(
      long id, String name, long size, boolean expired, String createdAt, String expiresAt) {}

  public record Release(
      long id,
      String name,
      String tag,
      String body,
      String url,
      boolean isDraft,
      boolean isPrerelease,
      String publishedAt,
      List<ReleaseAsset> assets) {}

  public record ReleaseAsset(String name, long size, String contentType, String downloadUrl) {}

  public record CreateIssue(
      @NotBlank @Size(max = 256) String title, @Size(max = 60000) String body) {}

  public record UpdateIssue(
      String title,
      String body,
      String state,
      List<String> labels,
      List<String> assignees,
      Integer milestone) {}

  public record Comment(@NotBlank @Size(max = 60000) String body) {}

  public record CreatePullRequest(
      @NotBlank @Size(max = 256) String title,
      @NotBlank @Size(max = 200) String base,
      @NotBlank @Size(max = 200) String head,
      @Size(max = 60000) String body,
      boolean draft) {}

  public record UpdatePullRequest(String title, String body, String state, String base) {}

  public record CreateRepository(String name, String description, boolean isPrivate) {}

  public record UpdateRepository(String description, String homepage, List<String> topics) {}

  public record Review(@NotBlank String event, @Size(max = 60000) String body) {}

  public record WorkflowAnalysis(
      WorkflowRun run, List<WorkflowJob> failedJobs, String failedLogs) {}

  public record MyWork(List<Issue> assignedIssues, List<PullRequest> reviewRequests) {}

  public record SearchResult(List<Issue> issues, List<PullRequest> pullRequests) {}

  public record DiscussionComment(
      String author, String body, String path, String url, String createdAt) {}

  public record CheckRun(String name, String status, String conclusion, String url) {}

  public record PullRequestContext(
      PullRequestDetail pullRequest,
      List<ChangedFile> files,
      List<Commit> commits,
      List<DiscussionComment> conversation,
      List<DiscussionComment> reviewComments,
      List<CheckRun> checks) {}

  public record Branch(String name, String sha, boolean isProtected) {}

  public record Tag(String name, String sha) {}

  public record Contributor(String login, int contributions, String url) {}

  public record DispatchWorkflow(String ref, java.util.Map<String, String> inputs) {}

  public record CreateRelease(
      String tag,
      String name,
      String body,
      boolean draft,
      boolean prerelease,
      boolean generateNotes) {}

  /** Only provided fields are changed on an existing release. */
  public record UpdateRelease(String tag, String name, String body) {}
}
