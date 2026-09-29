package com.personal.dashboard.github.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.WorkspaceException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/** Executes fixed GitHub CLI read operations as the dashboard server account. */
@Component
public class GithubCliAdapter {
  private static final int OUTPUT_LIMIT = 1024 * 1024;
  private final ObjectMapper json;

  public GithubCliAdapter(ObjectMapper json) {
    this.json = json;
  }

  public boolean authenticated() {
    return execute(List.of("auth", "status", "--active", "--hostname", "github.com"), true) != null;
  }

  public JsonNode repositories() {
    return execute(
        List.of(
            "repo",
            "list",
            "--limit",
            "50",
            "--json",
            "nameWithOwner,description,url,isPrivate,isArchived,isFork,updatedAt"),
        false);
  }

  public JsonNode repositories(String owner) {
    return execute(
        List.of(
            "repo",
            "list",
            owner,
            "--limit",
            "100",
            "--json",
            "nameWithOwner,description,url,isPrivate,isArchived,isFork,updatedAt"),
        false);
  }

  public JsonNode pullRequests(String repository) {
    return execute(
        List.of(
            "pr",
            "list",
            "--repo",
            repository,
            "--limit",
            "50",
            "--json",
            "number,title,state,url,updatedAt,isDraft"),
        false);
  }

  public JsonNode issues(String repository) {
    return execute(
        List.of(
            "issue",
            "list",
            "--repo",
            repository,
            "--limit",
            "50",
            "--json",
            "number,title,state,url,updatedAt"),
        false);
  }

  /** Executes one bounded GitHub REST read through the authenticated server gh account. */
  public JsonNode api(String endpoint) {
    return execute(List.of("api", "--hostname", "github.com", endpoint), false);
  }

  /** Sends JSON on stdin so issue or review text never appears in process arguments. */
  public JsonNode apiWrite(String method, String endpoint, JsonNode body) {
    try {
      return execute(
          List.of("api", "--hostname", "github.com", "--method", method, "--input", "-", endpoint),
          false,
          json.writeValueAsBytes(body));
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new WorkspaceException(400, "GitHub 요청 본문을 만들지 못했습니다.");
    }
  }

  /** Calls a fixed DELETE endpoint without sending a request body. */
  public void apiDelete(String endpoint) {
    execute(List.of("api", "--hostname", "github.com", "--method", "DELETE", endpoint), false);
  }

  /** Returns bounded failed-step logs as plain text; gh owns log archive decoding. */
  public String failedWorkflowLogs(String repository, long runId) {
    byte[] output =
        run(
            List.of("run", "view", Long.toString(runId), "--repo", repository, "--log-failed"),
            false,
            null);
    return new String(output, StandardCharsets.UTF_8);
  }

  private JsonNode execute(List<String> arguments, boolean statusOnly) {
    return execute(arguments, statusOnly, null);
  }

  private JsonNode execute(List<String> arguments, boolean statusOnly, byte[] input) {
    byte[] output = run(arguments, statusOnly, input);
    if (output == null) return null;
    if (statusOnly || output.length == 0) return json.createObjectNode();
    try {
      return json.readTree(new String(output, StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new WorkspaceException(502, "GitHub 응답 형식을 확인할 수 없습니다.");
    }
  }

  private byte[] run(List<String> arguments, boolean statusOnly, byte[] input) {
    String home = System.getenv("HOME");
    if (home == null || home.isBlank()) throw new WorkspaceException(503, "서버 HOME 설정을 확인해 주세요.");
    Path executable = Path.of(home, ".local", "bin", "gh");
    if (!executable.toFile().canExecute()) {
      if (statusOnly) return null;
      throw new WorkspaceException(503, "GitHub CLI가 준비되지 않았습니다. GitHub 로그인을 시작해 주세요.");
    }
    List<String> command = new ArrayList<>();
    command.add(executable.toString());
    command.addAll(arguments);
    Process process = null;
    try {
      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      builder
          .environment()
          .keySet()
          .removeIf(key -> !Set.of("PATH", "HOME", "LANG", "LC_ALL", "TMPDIR").contains(key));
      builder.environment().put("GH_PROMPT_DISABLED", "1");
      builder.environment().put("NO_COLOR", "1");
      process = builder.start();
      Process running = process;
      if (input != null) {
        try (var outputStream = running.getOutputStream()) {
          outputStream.write(input);
        }
      }
      FutureTask<byte[]> reader =
          new FutureTask<>(
              () -> {
                try (var processOutput = running.getInputStream()) {
                  return processOutput.readNBytes(OUTPUT_LIMIT + 1);
                }
              });
      Thread.ofVirtual().start(reader);
      byte[] output = reader.get(30, TimeUnit.SECONDS);
      if (output.length > OUTPUT_LIMIT)
        throw new WorkspaceException(502, "GitHub 응답 크기가 제한을 초과했습니다.");
      if (!running.waitFor(1, TimeUnit.SECONDS))
        throw new WorkspaceException(504, "GitHub CLI 응답 시간이 초과되었습니다.");
      if (statusOnly) return running.exitValue() == 0 ? new byte[0] : null;
      if (running.exitValue() != 0)
        throw new WorkspaceException(502, "GitHub 요청에 실패했습니다. 로그인 및 네트워크 상태를 확인해 주세요.");
      return output;
    } catch (IOException exception) {
      throw new WorkspaceException(502, "GitHub CLI를 실행하지 못했습니다.");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new WorkspaceException(502, "GitHub 요청이 중단되었습니다.");
    } catch (ExecutionException exception) {
      throw new WorkspaceException(502, "GitHub CLI 응답을 읽지 못했습니다.");
    } catch (TimeoutException exception) {
      throw new WorkspaceException(504, "GitHub CLI 응답 시간이 초과되었습니다.");
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }
}
