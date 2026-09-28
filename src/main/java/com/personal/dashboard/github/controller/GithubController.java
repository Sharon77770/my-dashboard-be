package com.personal.dashboard.github.controller;

import com.personal.dashboard.github.dto.GithubDto;
import com.personal.dashboard.github.service.GithubService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
