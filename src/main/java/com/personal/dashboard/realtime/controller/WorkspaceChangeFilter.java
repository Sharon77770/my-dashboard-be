package com.personal.dashboard.realtime.controller;

import com.personal.dashboard.realtime.service.WorkspaceEvents;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Successful REST/MCP writes invalidate projections only after the request has completed. */
@Component
public class WorkspaceChangeFilter extends OncePerRequestFilter {
  private final WorkspaceEvents events;

  public WorkspaceChangeFilter(WorkspaceEvents events) {
    this.events = events;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(request, response);
    if (!Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())
        || response.getStatus() < 200
        || response.getStatus() >= 300) return;
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith("/api/v1/")
        || path.contains("/codex/jobs")
        || path.contains("/query")
        || path.matches("/api/v1/databases(?:/[^/]+)?/test")) return;
    String topic = path.substring(8).split("/", 2)[0];
    switch (topic) {
      case "military" -> events.changed("military", "calendar");
      case "mcp" ->
          events.changed(
              "workspace",
              "notes",
              "calendar",
              "timetables",
              "services",
              "telemetry",
              "databases",
              "github",
              "memory");
      case "devices",
              "applications",
              "bookmarks",
              "clips",
              "preferences",
              "browser",
              "runtime",
              "sessions" ->
          events.changed("workspace", "devices", "services");
      case "notes",
              "calendar",
              "timetables",
              "services",
              "telemetry",
              "databases",
              "github",
              "cloud" ->
          events.changed(topic);
      case "assistant" -> {
        if (path.contains("/memories")) events.changed("memory");
      }
      default -> {}
    }
  }
}
