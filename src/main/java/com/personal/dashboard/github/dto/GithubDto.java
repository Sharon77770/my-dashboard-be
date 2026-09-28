package com.personal.dashboard.github.dto;

/** Public response shapes for server GitHub reads. */
public final class GithubDto {
  private GithubDto() {}

  public record Status(boolean authenticated) {}

  public record Repository(
      String nameWithOwner, String description, String url, boolean isPrivate, String updatedAt) {}

  public record PullRequest(
      int number, String title, String state, String url, String updatedAt, boolean isDraft) {}

  public record Issue(int number, String title, String state, String url, String updatedAt) {}
}
