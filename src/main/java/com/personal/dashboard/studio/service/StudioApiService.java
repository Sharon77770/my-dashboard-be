package com.personal.dashboard.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.files.adapter.FileAdapter;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.studio.adapter.StudioAdapter;
import com.personal.dashboard.studio.dto.StudioApiDto.*;
import com.personal.dashboard.studio.dto.StudioDto;
import com.personal.dashboard.studio.repository.StudioApiRepository;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Resolves encrypted environment values server-side; helper requests stay outside job history. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class StudioApiService {
  private final CatalogService catalog;
  private final FileAdapter files;
  private final StudioAdapter adapter;
  private final ObjectMapper json;
  private final StudioApiRepository repository;

  public StudioApiService(
      CatalogService catalog,
      FileAdapter files,
      StudioAdapter adapter,
      ObjectMapper json,
      StudioApiRepository repository) {
    this.catalog = catalog;
    this.files = files;
    this.adapter = adapter;
    this.json = json;
    this.repository = repository;
  }

  private String root(String device, String root) {
    try {
      return files.projectDirectory(catalog.requireDevice(device), root);
    } catch (java.io.IOException error) {
      throw new WorkspaceException(400, "프로젝트 폴더를 확인하세요.");
    }
  }

  public Exchange send(Request input) {
    String root = root(input.deviceId(), input.root());
    var environment = repository.read(input.deviceId(), root).environment();
    try {
      // Replace individual text values, never JSON source: quotes/newlines in a secret remain data.
      var tree = json.valueToTree(input);
      resolve(tree, environment);
      var resolved = json.treeToValue(tree, Request.class);
      com.personal.dashboard.studio.adapter.StudioBrowserAdapter.validate(resolved.url());
      var args =
          json.convertValue(
              Map.of("content", json.writeValueAsString(resolved)), StudioDto.Args.class);
      var execution = new StudioAdapter.Execution();
      var result = new AtomicReference<Response>();
      try {
        adapter.execute(
            catalog.requireDevice(input.deviceId()),
            new StudioDto.Request(input.deviceId(), root, "api-request", args),
            execution,
            message -> {
              if (message.error() != null)
                throw new WorkspaceException(
                    message.status() == null ? 502 : message.status(), message.error());
              if (message.result() != null) result.set(message.result().api());
            });
      } finally {
        execution.cancel();
      }
      if (result.get() == null) throw new WorkspaceException(502, "API 응답을 받지 못했습니다.");
      var stored =
          new com.personal.dashboard.studio.entity.StudioApiExchange(
              UUID.randomUUID().toString(),
              System.currentTimeMillis(),
              input.method(),
              input.url(),
              result.get().status(),
              json.writeValueAsString(input),
              json.writeValueAsString(result.get()));
      repository.update(
          input.deviceId(),
          root,
          state -> {
            var history = new ArrayList<>(state.history());
            history.addFirst(stored);
            while (history.size() > 30) history.removeLast();
            return new StudioApiRepository.State(state.environment(), history);
          });
      return new Exchange(stored.id(), redact(result.get(), resolved, environment));
    } catch (WorkspaceException error) {
      throw error;
    } catch (Exception error) {
      throw new WorkspaceException(400, "API 요청 값을 확인하세요.");
    }
  }

  private Response redact(Response response, Request request, Map<String, String> environment) {
    var secrets = new ArrayList<>(environment.values());
    if (request.auth() != null && request.auth().value() != null) {
      secrets.add(request.auth().value());
      if (request.auth().type().equals("basic"))
        secrets.add(
            Base64.getEncoder()
                .encodeToString(
                    (request.auth().username() + ":" + request.auth().value())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    if (request.headers() != null)
      for (var header : request.headers())
        if (header
            .name()
            .toLowerCase(Locale.ROOT)
            .matches(".*(authorization|cookie|key|token|secret).*")) secrets.add(header.value());
    java.util.function.UnaryOperator<String> scrub =
        value -> {
          for (String secret : secrets)
            if (secret != null && !secret.isEmpty()) value = value.replace(secret, "[redacted]");
          return value;
        };
    var headers =
        response.headers().stream()
            .map(
                field ->
                    new Field(
                        field.name(),
                        field
                                .name()
                                .toLowerCase(Locale.ROOT)
                                .matches(".*(authorization|cookie|key|token|secret).*")
                            ? "[redacted]"
                            : scrub.apply(field.value()),
                        null))
            .toList();
    return new Response(
        response.status(),
        headers,
        scrub.apply(response.body()),
        response.base64(),
        response.latency(),
        response.size(),
        response.truncated());
  }

  private void resolve(
      com.fasterxml.jackson.databind.JsonNode node, Map<String, String> environment) {
    if (node.isObject()) {
      var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
      var fields = new ArrayList<String>();
      object.fieldNames().forEachRemaining(fields::add);
      for (String field : fields) {
        var value = object.get(field);
        if (value.isTextual()) object.put(field, substitute(value.asText(), environment));
        else resolve(value, environment);
      }
    } else if (node.isArray()) for (var value : node) resolve(value, environment);
  }

  private String substitute(String text, Map<String, String> environment) {
    var matcher = Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*)}}").matcher(text);
    var result = new StringBuilder();
    while (matcher.find()) {
      String value = environment.get(matcher.group(1));
      if (value == null) throw new WorkspaceException(400, "환경변수를 먼저 등록하세요: " + matcher.group(1));
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    if (result.length() > 1048576) throw new WorkspaceException(413, "치환된 요청이 너무 큽니다.");
    return result.toString();
  }

  public List<History> history(Project project) {
    String root = root(project.deviceId(), project.root());
    return repository.read(project.deviceId(), root).history().stream()
        .map(
            item ->
                new History(
                    item.id(), item.time(), item.method(), safeUrl(item.url()), item.status()))
        .toList();
  }

  private String safeUrl(String value) {
    int query = value.indexOf('?');
    return query < 0 ? value : value.substring(0, query) + "?…";
  }

  public Exchange replay(Replay replay) {
    String root = root(replay.deviceId(), replay.root());
    var stored =
        repository.read(replay.deviceId(), root).history().stream()
            .filter(item -> item.id().equals(replay.id()))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(404, "API 기록을 찾을 수 없습니다."));
    try {
      return send(json.readValue(stored.requestJson(), Request.class));
    } catch (WorkspaceException error) {
      throw error;
    } catch (Exception error) {
      throw new WorkspaceException(500, "저장된 API 요청을 읽지 못했습니다.");
    }
  }

  public List<String> environment(Project project) {
    String root = root(project.deviceId(), project.root());
    return List.copyOf(repository.read(project.deviceId(), root).environment().keySet());
  }

  public void environment(Environment input) {
    String root = root(input.deviceId(), input.root());
    repository.update(
        input.deviceId(),
        root,
        state -> {
          var values = new LinkedHashMap<>(state.environment());
          input
              .values()
              .forEach(
                  (key, value) -> {
                    if (value == null) values.remove(key);
                    else values.put(key, value);
                  });
          if (values.size() > 50) throw new WorkspaceException(400, "프로젝트 환경변수는 최대 50개입니다.");
          return new StudioApiRepository.State(values, state.history());
        });
  }
}
