package com.personal.dashboard.assistant.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Owns the bearer credential shared only by the embedded Codex process and the MCP endpoint. */
@Component
public final class McpAccess {
  private final String token;
  private final String url;

  public McpAccess(Environment environment) {
    String configured = environment.getProperty("DASHBOARD_MCP_TOKEN", "").trim();
    if (!configured.isEmpty() && configured.length() < 32)
      throw new IllegalStateException("DASHBOARD_MCP_TOKEN must contain at least 32 characters");
    if (configured.isEmpty()) {
      byte[] random = new byte[32];
      new SecureRandom().nextBytes(random);
      configured = HexFormat.of().formatHex(random);
    }
    token = configured;
    url = "http://127.0.0.1:" + environment.getProperty("server.port", "8080") + "/api/v1/mcp";
  }

  public String token() {
    return token;
  }

  public String url() {
    return url;
  }

  public boolean accepts(String candidate) {
    return candidate != null
        && MessageDigest.isEqual(
            token.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
