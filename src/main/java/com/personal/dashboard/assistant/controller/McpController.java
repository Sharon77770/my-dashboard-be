package com.personal.dashboard.assistant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.assistant.service.AssistantMcpService;
import com.personal.dashboard.assistant.service.McpAccess;
import com.personal.dashboard.global.WorkspaceException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** Stateless Streamable HTTP MCP endpoint, protected by a private bearer token. */
@RestController
public class McpController {
  private static final Set<String> PROTOCOLS = Set.of("2025-03-26", "2025-06-18", "2025-11-25");
  private final McpAccess access;
  private final AssistantMcpService service;
  private final ObjectMapper json;

  public McpController(McpAccess access, AssistantMcpService service, ObjectMapper json) {
    this.access = access;
    this.service = service;
    this.json = json;
  }

  @PostMapping(path = "/api/v1/mcp", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> post(
      @RequestHeader(value = "Accept", required = false) String accept,
      HttpServletRequest request,
      @RequestBody JsonNode input) {
    if (!authorized(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    if (!sameOrigin(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    if (accept != null
        && !accept.contains(MediaType.APPLICATION_JSON_VALUE)
        && !accept.contains("*/*")) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    JsonNode id = input.get("id");
    String method = input.path("method").asText();
    if (method.isBlank() || !input.path("jsonrpc").asText().equals("2.0"))
      return jsonError(id, -32600, "Invalid JSON-RPC request");
    if (id == null) return ResponseEntity.accepted().build();
    if (method.equals("initialize")) return initialize(id, input.path("params"));
    if (method.equals("ping")) return result(id, json.createObjectNode());
    if (method.equals("tools/list"))
      return result(id, json.valueToTree(java.util.Map.of("tools", service.tools())));
    if (method.equals("tools/call")) return callTool(id, input.path("params"));
    return jsonError(id, -32601, "Method not found");
  }

  private ResponseEntity<JsonNode> initialize(JsonNode id, JsonNode params) {
    String requested = params.path("protocolVersion").asText();
    String version = PROTOCOLS.contains(requested) ? requested : "2025-03-26";
    var reply =
        json.valueToTree(
            java.util.Map.of(
                "protocolVersion",
                version,
                "capabilities",
                java.util.Map.of("tools", java.util.Map.of("listChanged", false)),
                "serverInfo",
                java.util.Map.of("name", "personal-dashboard", "version", "1.0.0"),
                "instructions",
                "Use dashboard tools to open app pages, manage calendar events and write notebook"
                    + " notes. Confirm ambiguous dates before creating events."));
    return result(id, reply);
  }

  private ResponseEntity<JsonNode> callTool(JsonNode id, JsonNode params) {
    var previous = SecurityContextHolder.getContext();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            "dashboard-mcp", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    SecurityContextHolder.setContext(context);
    try {
      String name = params.path("name").asText();
      var value = service.call(name, params.path("arguments"));
      var response = json.createObjectNode();
      response
          .putArray("content")
          .addObject()
          .put("type", "text")
          .put("text", json.writeValueAsString(value));
      response.set("structuredContent", json.valueToTree(value));
      response.put("isError", false);
      return result(id, response);
    } catch (WorkspaceException
        | IllegalArgumentException
        | java.time.DateTimeException exception) {
      return toolError(id, exception.getMessage());
    } catch (Exception exception) {
      return toolError(id, "도구를 실행하지 못했습니다. 입력값과 대시보드 상태를 확인해 주세요.");
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }

  private ResponseEntity<JsonNode> toolError(JsonNode id, String message) {
    var response = json.createObjectNode();
    response.putArray("content").addObject().put("type", "text").put("text", message);
    response.put("isError", true);
    return result(id, response);
  }

  private boolean authorized(HttpServletRequest request) {
    String value = request.getHeader(HttpHeaders.AUTHORIZATION);
    return value != null && value.startsWith("Bearer ") && access.accepts(value.substring(7));
  }

  private boolean sameOrigin(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin == null || origin.isBlank()) return true;
    try {
      URI value = URI.create(origin);
      int originPort =
          value.getPort() < 0 ? (value.getScheme().equals("https") ? 443 : 80) : value.getPort();
      int requestPort = request.getServerPort();
      return value.getScheme().equalsIgnoreCase(request.getScheme())
          && value.getHost().equalsIgnoreCase(request.getServerName())
          && originPort == requestPort;
    } catch (Exception exception) {
      return false;
    }
  }

  private ResponseEntity<JsonNode> result(JsonNode id, JsonNode value) {
    var body = json.createObjectNode().put("jsonrpc", "2.0");
    body.set("id", id.deepCopy());
    body.set("result", value);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
  }

  private ResponseEntity<JsonNode> jsonError(JsonNode id, int code, String message) {
    var body = json.createObjectNode().put("jsonrpc", "2.0");
    body.set("id", id == null ? json.nullNode() : id.deepCopy());
    body.putObject("error").put("code", code).put("message", message);
    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(body);
  }
}
