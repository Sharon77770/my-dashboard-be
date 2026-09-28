package com.personal.dashboard.github.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.github.adapter.GithubCliAdapter;
import com.personal.dashboard.github.dto.GithubDto;
import com.personal.dashboard.global.WorkspaceException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Provides the dashboard's bounded GitHub read use cases. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class GithubService {
  private final GithubCliAdapter cli;
  private final ObjectMapper json;

  public GithubService(GithubCliAdapter cli, ObjectMapper json) {
    this.cli = cli;
    this.json = json;
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
