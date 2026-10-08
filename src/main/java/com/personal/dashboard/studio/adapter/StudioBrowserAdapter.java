package com.personal.dashboard.studio.adapter;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Isolated tabs on the existing server Chromium, with optional verified SSH forwarding. */
@Component
public class StudioBrowserAdapter {
  private final ObjectMapper json;
  private final SshAdapter ssh;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public StudioBrowserAdapter(ObjectMapper json, SshAdapter ssh) {
    this.json = json;
    this.ssh = ssh;
  }

  public final class Page implements AutoCloseable, WebSocket.Listener {
    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final List<String> errors = new ArrayList<>(), failures = new ArrayList<>();
    private final StringBuilder fragments = new StringBuilder();
    private WebSocket socket;
    private URI endpoint;
    private String target;
    private net.schmizz.sshj.SSHClient connection;
    private ServerSocket listener;

    private final Map<String, String> forwards = new LinkedHashMap<>();
    private final List<AutoCloseable> tunnels = new ArrayList<>();

    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      synchronized (fragments) {
        fragments.append(data);
        if (fragments.length() > 8000000) {
          ws.abort();
          failPending();
          fragments.setLength(0);
          return null;
        }
        if (last) {
          try {
            JsonNode event = json.readTree(fragments.toString());
            if (event.has("id")) {
              var future = pending.remove(event.path("id").asInt());
              if (future != null) future.complete(event);
            } else {
              String method = event.path("method").asText();
              JsonNode params = event.path("params");
              if (method.equals("Runtime.exceptionThrown"))
                add(
                    errors,
                    params
                        .path("exceptionDetails")
                        .path("exception")
                        .path("description")
                        .asText(params.path("exceptionDetails").path("text").asText()));
              if (method.equals("Runtime.consoleAPICalled")
                  && Set.of("error", "warning").contains(params.path("type").asText())) {
                StringBuilder text = new StringBuilder();
                for (JsonNode argument : params.path("args"))
                  text.append(argument.path("value").asText(argument.path("description").asText()))
                      .append(' ');
                add(errors, text.toString());
              }
              if (method.equals("Network.loadingFailed"))
                add(
                    failures,
                    params.path("type").asText() + ": " + params.path("errorText").asText());
              if (method.equals("Network.responseReceived")
                  && params.path("response").path("status").asInt() >= 400) {
                var response = params.path("response");
                add(
                    failures,
                    response.path("status").asInt() + " " + safeUrl(response.path("url").asText()));
              }
            }
          } catch (Exception ignored) {
            failPending();
          }
          fragments.setLength(0);
        }
      }
      ws.request(1);
      return CompletableFuture.completedFuture(null);
    }

    public void onError(WebSocket ws, Throwable error) {
      failPending();
    }

    public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
      failPending();
      return CompletableFuture.completedFuture(null);
    }

    private void failPending() {
      pending
          .values()
          .forEach(
              future ->
                  future.completeExceptionally(new IllegalStateException("Browser disconnected")));
      pending.clear();
    }

    private void add(List<String> list, String value) {
      synchronized (list) {
        if (list.size() == 50) list.removeFirst();
        list.add(value.substring(0, Math.min(value.length(), 2000)));
      }
    }

    private List<String> copy(List<String> list) {
      synchronized (list) {
        return List.copyOf(list);
      }
    }

    public JsonNode command(String method, Map<String, ?> args) {
      int id = sequence.incrementAndGet();
      var future = new CompletableFuture<JsonNode>();
      pending.put(id, future);
      try {
        socket
            .sendText(
                json.writeValueAsString(Map.of("id", id, "method", method, "params", args)), true)
            .get(5, TimeUnit.SECONDS);
        JsonNode response = future.get(10, TimeUnit.SECONDS);
        if (response.has("error")) throw new IllegalStateException();
        return response.path("result");
      } catch (Exception error) {
        throw new WorkspaceException(
            502, "Chromium " + method + " 명령을 완료하지 못했습니다. 브라우저 연결을 다시 여세요.");
      } finally {
        pending.remove(id);
      }
    }

    public Map<String, Object> snapshot() {
      var state =
          command(
              "Runtime.evaluate",
              Map.of(
                  "expression",
                  "JSON.stringify({url:location.href,title:document.title,text:(document.body?.innerText||'').slice(0,24000)})",
                  "returnByValue",
                  true));
      try {
        ObjectNode value =
            (ObjectNode) json.readTree(state.path("result").path("value").asText("{}"));
        String url = value.path("url").asText();
        for (var forward : forwards.entrySet())
          if (url.startsWith(forward.getValue() + "/") || url.equals(forward.getValue())) {
            url = forward.getKey() + url.substring(forward.getValue().length());
            break;
          }
        return Map.of(
            "url",
            url,
            "title",
            value.path("title").asText(),
            "text",
            value.path("text").asText(),
            "consoleErrors",
            copy(errors),
            "networkFailures",
            copy(failures),
            "image",
            command(
                    "Page.captureScreenshot",
                    Map.of("format", "jpeg", "quality", 70, "captureBeyondViewport", false))
                .path("data")
                .asText(),
            "width",
            1200,
            "height",
            720);
      } catch (WorkspaceException error) {
        throw error;
      } catch (Exception error) {
        throw new WorkspaceException(502, "브라우저 화면을 읽지 못했습니다.");
      }
    }

    public void navigate(DeviceRecord device, String url) {
      URI uri = validate(url);
      String origin = uri.getScheme() + "://" + uri.getRawAuthority();
      if (forwards.containsKey(origin)) {
        command(
            "Page.navigate", Map.of("url", forwards.get(origin) + url.substring(origin.length())));
        return;
      }
      if (!device.id().equals("local")
          && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())) {
        try {
          connection = ssh.connect(device);
          listener = new ServerSocket(0, 20, InetAddress.getLoopbackAddress());
          tunnels.add(listener);
          tunnels.add(connection);
          final var server = listener;
          final var sshClient = connection;
          int targetPort =
              uri.getPort() < 0 ? (uri.getScheme().equals("https") ? 443 : 80) : uri.getPort();
          var forwarder =
              connection.newLocalPortForwarder(
                  new net.schmizz.sshj.connection.channel.direct.Parameters(
                      "127.0.0.1", server.getLocalPort(), "127.0.0.1", targetPort),
                  server);
          Thread forwardingThread =
              Thread.ofVirtual()
                  .unstarted(
                      () -> {
                        // A target connection can fail during a dev-server restart. Keep the
                        // listening socket alive so subsequent requests can reach the new process.
                        while (!server.isClosed()
                            && !Thread.currentThread().isInterrupted()
                            && sshClient.isConnected()) {
                          try {
                            forwarder.listen();
                          } catch (java.io.IOException ignored) {
                          }
                        }
                      });
          tunnels.add(
              0,
              () -> {
                forwardingThread.interrupt();
                server.close();
              });
          forwardingThread.start();
          URI forwarded =
              new URI(
                  uri.getScheme(),
                  null,
                  "127.0.0.1",
                  server.getLocalPort(),
                  uri.getPath(),
                  uri.getQuery(),
                  uri.getFragment());
          forwards.put(origin, uri.getScheme() + "://127.0.0.1:" + server.getLocalPort());
          url = forwarded.toString();
        } catch (Exception error) {
          throw new WorkspaceException(502, "SSH 미리보기 터널을 연결하지 못했습니다.");
        }
      }
      synchronized (errors) {
        errors.clear();
      }
      synchronized (failures) {
        failures.clear();
      }
      var result = command("Page.navigate", Map.of("url", url));
      if (result.has("errorText"))
        throw new WorkspaceException(
            502, "미리보기 URL에 연결하지 못했습니다: " + result.path("errorText").asText());
    }

    public void history(int delta) {
      var history = command("Page.getNavigationHistory", Map.of());
      int index = history.path("currentIndex").asInt() + delta;
      if (index >= 0 && index < history.path("entries").size())
        command(
            "Page.navigateToHistoryEntry",
            Map.of("entryId", history.path("entries").get(index).path("id").asInt()));
    }

    public void close() {
      if (socket != null) socket.abort();
      failPending();
      for (var tunnel : tunnels)
        try {
          tunnel.close();
        } catch (Exception ignored) {
        }
      try {
        if (listener != null) listener.close();
        if (connection != null) connection.close();
      } catch (Exception ignored) {
      }
      try {
        if (target != null)
          http.send(
              HttpRequest.newBuilder(endpoint.resolve("/json/close/" + target))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      } catch (Exception ignored) {
      }
    }
  }

  public Page create(String host, int port) {
    Page page = new Page();
    try {
      page.endpoint =
          new URI(
              "http", null, InetAddress.getByName(host).getHostAddress(), port, "/", null, null);
      var result =
          http.send(
              HttpRequest.newBuilder(page.endpoint.resolve("/json/new?about:blank"))
                  .timeout(Duration.ofSeconds(5))
                  .PUT(HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (result.statusCode() != 200) throw new IllegalStateException();
      var target = json.readTree(result.body());
      page.target = target.path("id").asText();
      page.socket =
          http.newWebSocketBuilder()
              .connectTimeout(Duration.ofSeconds(5))
              .buildAsync(URI.create(target.path("webSocketDebuggerUrl").asText()), page)
              .get(8, TimeUnit.SECONDS);
      page.command("Page.enable", Map.of());
      page.command("Runtime.enable", Map.of());
      page.command("Network.enable", Map.of());
      page.command(
          "Emulation.setDeviceMetricsOverride",
          Map.of("width", 1200, "height", 720, "deviceScaleFactor", 1, "mobile", false));
      return page;
    } catch (Exception error) {
      page.close();
      throw new WorkspaceException(502, "기존 Chromium 서버에 연결하지 못했습니다. 서버 브라우저의 CDP 설정을 확인하세요.");
    }
  }

  public static URI validate(String value) {
    try {
      URI uri = URI.create(value);
      if (!Set.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getRawUserInfo() != null
          || uri.getPort() > 65535) throw new IllegalArgumentException();
      return uri;
    } catch (Exception error) {
      throw new WorkspaceException(400, "사용자 정보가 없는 HTTP 또는 HTTPS URL을 입력하세요.");
    }
  }

  private static String safeUrl(String value) {
    try {
      var uri = URI.create(value);
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
          .toString();
    } catch (Exception error) {
      return "request";
    }
  }
}
