package com.personal.dashboard.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.studio.adapter.StudioAdapter;
import com.personal.dashboard.studio.adapter.StudioBrowserAdapter;
import com.personal.dashboard.studio.dto.*;
import jakarta.validation.Validator;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/** Executes Codex tools against the initiating project, never model-selected devices or roots. */
@Service
public class StudioToolService {
  private final StudioAdapter adapter;
  private final CatalogService catalog;
  private final StudioBrowserService browser;
  private final StudioApiService api;
  private final ObjectMapper json;
  private final Validator validator;

  public StudioToolService(
      StudioAdapter adapter,
      CatalogService catalog,
      StudioBrowserService browser,
      StudioApiService api,
      ObjectMapper json,
      Validator validator) {
    this.adapter = adapter;
    this.catalog = catalog;
    this.browser = browser;
    this.api = api;
    this.json = json;
    this.validator = validator;
  }

  /** Called only from an authenticated Studio job; all other job entry points reject tool calls. */
  public Object execute(String owner, StudioDto.Request project, StudioAdapter.ToolCall call)
      throws Exception {
    if (!project.action().equals("codex-run")
        || project.args() == null
        || !Set.of("workspace-write", "danger-full-access")
            .contains(Objects.toString(project.args().mode(), "")))
      throw new WorkspaceException(403, "실시간 프로젝트 도구는 파일 수정 허용 모드에서 사용할 수 있습니다.");
    if (!(call.arguments() instanceof ObjectNode original) || original.size() > 12)
      throw new WorkspaceException(400, "도구 입력을 확인하세요.");
    ObjectNode args = original.deepCopy();
    args.put("deviceId", project.deviceId());
    args.put("root", project.root());
    return switch (call.name()) {
      case "studio_process" -> {
        String action = args.path("action").asText();
        if (!Set.of("list", "ports", "logs", "stop", "restart").contains(action))
          throw new WorkspaceException(400, "지원하지 않는 프로세스 동작입니다.");
        yield run(
                project,
                action.equals("ports") ? "ports" : "run-" + action,
                Map.of("path", args.path("id").asText()))
            .tools();
      }
      case "studio_api" -> {
        requireProjectUrl(project, args.path("url").asText());
        if (!args.has("bodyType")) args.put("bodyType", "none");
        var input = validated(json.treeToValue(args, StudioApiDto.Request.class));
        yield api.send(input);
      }
      case "studio_browser" -> {
        var input = validated(json.treeToValue(args, StudioBrowserDto.Input.class));
        if (input.action().equals("open")) requireProjectUrl(project, input.url());
        else {
          var state =
              browser.action(
                  owner,
                  new StudioBrowserDto.Input(
                      project.deviceId(),
                      project.root(),
                      "snapshot",
                      null,
                      null,
                      null,
                      null,
                      null));
          requireProjectUrl(project, Objects.toString(state.get("url"), ""));
        }
        var result = new LinkedHashMap<>(browser.toolAction(owner, input));
        result.remove("image");
        yield result;
      }
      default -> throw new WorkspaceException(400, "지원하지 않는 Studio 도구입니다.");
    };
  }

  private <T> T validated(T input) {
    if (!validator.validate(input).isEmpty()) throw new WorkspaceException(400, "도구 입력 범위를 확인하세요.");
    return input;
  }

  /** Only a currently listening socket owned by a process in this project is eligible. */
  private void requireProjectUrl(StudioDto.Request project, String url) throws Exception {
    var uri = StudioBrowserAdapter.validate(url);
    if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())
        || url.contains("{{"))
      throw new WorkspaceException(403, "현재 프로젝트의 loopback URL만 도구에서 사용할 수 있습니다.");
    int port = uri.getPort() < 0 ? (uri.getScheme().equals("https") ? 443 : 80) : uri.getPort();
    var ports = run(project, "ports", Map.of()).tools().ports();
    if (ports == null || ports.stream().noneMatch(item -> item.project() && item.port() == port))
      throw new WorkspaceException(403, "현재 프로젝트가 사용하는 listening port가 아닙니다.");
  }

  private StudioDto.Result run(StudioDto.Request project, String action, Map<String, Object> args)
      throws Exception {
    var execution = new StudioAdapter.Execution();
    var result = new AtomicReference<StudioDto.Result>();
    try {
      adapter.execute(
          catalog.requireDevice(project.deviceId()),
          new StudioDto.Request(
              project.deviceId(),
              project.root(),
              action,
              json.convertValue(args, StudioDto.Args.class)),
          execution,
          message -> {
            if (message.error() != null) throw new WorkspaceException(502, message.error());
            if (message.result() != null) result.set(message.result());
          });
      if (result.get() == null) throw new WorkspaceException(502, "프로젝트 도구 응답을 받지 못했습니다.");
      return result.get();
    } finally {
      execution.cancel();
    }
  }
}
