package com.personal.dashboard.runtime.service;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.dto.AuthenticationBrowserRequest;
import com.personal.dashboard.runtime.dto.SessionView;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Selects trusted destinations for the persistent server browser without moving credentials. */
@Service
public class AuthenticationBrowserService {
  private static final Map<String, String> HOMES =
      Map.of(
          "GOOGLE", "https://accounts.google.com/",
          "GITHUB", "https://github.com/login",
          "CODEX", "https://chatgpt.com/auth/login");
  private final CatalogService catalog;
  private final RuntimeService runtimes;

  public AuthenticationBrowserService(CatalogService catalog, RuntimeService runtimes) {
    this.catalog = catalog;
    this.runtimes = runtimes;
  }

  /** Browser preferences do not redirect authentication onto the current public PC. */
  @PreAuthorize("hasRole('OWNER')")
  public SessionView open(AuthenticationBrowserRequest request, String ownerId) {
    String destination = null;
    String label = "인증 브라우저";
    if ("APP".equals(request.provider())) {
      if (request.url() != null || request.applicationId() == null) throw invalid();
      var app = catalog.requireApplication(request.applicationId());
      destination = app.url();
      label = app.name() + " · 인증 브라우저";
    } else {
      if (request.applicationId() != null) throw invalid();
      if ("BROWSER".equals(request.provider())) {
        if (request.url() != null) throw invalid();
      } else {
        destination = request.url() == null ? HOMES.get(request.provider()) : request.url();
        validate(request.provider(), destination);
      }
    }
    return runtimes.createBrowserSession(
        destination, label, ownerId, request.width(), request.height());
  }

  /** Rejects lookalike hosts, credentials and non-HTTPS URLs before reaching Chromium. */
  private void validate(String provider, String value) {
    try {
      URI uri = URI.create(value);
      Set<String> hosts =
          switch (provider) {
            case "GOOGLE" -> Set.of("accounts.google.com");
            case "GITHUB" -> Set.of("github.com");
            case "CODEX" -> Set.of("auth.openai.com", "chatgpt.com");
            default -> Set.of();
          };
      if (!"https".equals(uri.getScheme())
          || uri.getUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)
          || !hosts.contains(uri.getHost() == null ? "" : uri.getHost())) throw invalid();
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw invalid();
    }
  }

  private WorkspaceException invalid() {
    return new WorkspaceException(400, "지원하는 인증 페이지 또는 등록된 앱을 선택해 주세요.");
  }
}
