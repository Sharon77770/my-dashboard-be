package com.personal.dashboard.realtime;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.personal.dashboard.realtime.service.WorkspaceEvents;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "DASHBOARD_AUTH_ID=realtime-test", "DASHBOARD_AUTH_PASSWORD=realtime-test-password",
      "workspace.root=./target/realtime-files", "workspace.key-path=./target/realtime.key",
      "DASHBOARD_DB_PATH=./target/realtime-test.db", "server.servlet.session.cookie.secure=false"
    })
class WorkspaceRealtimeIntegrationTest {
  @LocalServerPort int port;
  @Autowired WorkspaceEvents events;
  @Autowired ObjectMapper json;

  URI http(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  URI socket() {
    return URI.create("ws://localhost:" + port + "/ws/workspace");
  }

  static class Inbox implements WebSocket.Listener {
    final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    final CompletableFuture<Integer> closed = new CompletableFuture<>();
    final StringBuilder text = new StringBuilder();

    public void onOpen(WebSocket socket) {
      socket.request(1);
    }

    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
      text.append(data);
      if (last) {
        frames.add(text.toString());
        text.setLength(0);
      }
      socket.request(1);
      return null;
    }

    public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
      closed.complete(code);
      return null;
    }

    JsonNode next(ObjectMapper json) throws Exception {
      String frame = frames.poll(5, TimeUnit.SECONDS);
      assertThat(frame).isNotNull();
      return json.readTree(frame);
    }
  }

  HttpClient login() throws Exception {
    var client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    String html =
        client
            .send(
                HttpRequest.newBuilder(http("/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString())
            .body();
    String csrf = Jsoup.parse(html).selectFirst("input[name=_csrf]").val();
    var response =
        client.send(
            HttpRequest.newBuilder(http("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "id=realtime-test&password=realtime-test-password&_csrf="
                            + URLEncoder.encode(csrf, java.nio.charset.StandardCharsets.UTF_8)))
                .build(),
            HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location").orElse("")).doesNotContain("error");
    return client;
  }

  @Test
  void realSocketDeliversRestChangesAndLogoutClosesConnection() throws Exception {
    var client = login();
    var inbox = new Inbox();
    var connection =
        client.newWebSocketBuilder().buildAsync(socket(), inbox).get(5, TimeUnit.SECONDS);
    try {
      assertThat(inbox.next(json).path("type").asText()).isEqualTo("ready");
      var home =
          Jsoup.parse(
              client
                  .send(
                      HttpRequest.newBuilder(http("/")).GET().build(),
                      HttpResponse.BodyHandlers.ofString())
                  .body());
      String csrf = home.selectFirst("meta[name=csrf-token]").attr("content"),
          header = home.selectFirst("meta[name=csrf-header]").attr("content");
      var write =
          client.send(
              HttpRequest.newBuilder(http("/api/v1/preferences"))
                  .header(header, csrf)
                  .header("Content-Type", "application/json")
                  .PUT(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"theme\":\"dark\",\"compact\":true,\"terminalFont\":13,\"clipMinutes\":60}"))
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      assertThat(write.statusCode()).isEqualTo(204);
      assertThat(inbox.next(json).path("topics").toString()).contains("workspace");
      client.send(
          HttpRequest.newBuilder(http("/logout"))
              .header(header, csrf)
              .POST(HttpRequest.BodyPublishers.noBody())
              .build(),
          HttpResponse.BodyHandlers.discarding());
      assertThat(inbox.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    } finally {
      connection.abort();
    }
  }

  @Test
  void anonymousAndCrossOriginHandshakesAreDenied() throws Exception {
    try (var anonymous = HttpClient.newHttpClient();
        var client = login()) {
      assertThatThrownBy(
              () -> anonymous.newWebSocketBuilder().buildAsync(socket(), new Inbox()).join())
          .hasCauseInstanceOf(WebSocketHandshakeException.class);
      assertThatThrownBy(
              () ->
                  client
                      .newWebSocketBuilder()
                      .header("Origin", "https://untrusted.example")
                      .buildAsync(socket(), new Inbox())
                      .join())
          .hasCauseInstanceOf(WebSocketHandshakeException.class);
    }
  }

  @Test
  void inboundCommandsAreRejectedAndPrivateJobsNeverReachAnotherLogin() throws Exception {
    try (var first = login();
        var second = login()) {
      var a = new Inbox();
      var b = new Inbox();
      var wa = first.newWebSocketBuilder().buildAsync(socket(), a).get(5, TimeUnit.SECONDS);
      var wb = second.newWebSocketBuilder().buildAsync(socket(), b).get(5, TimeUnit.SECONDS);
      try {
        a.next(json);
        b.next(json);
        var cookies = (CookieManager) first.cookieHandler().orElseThrow();
        String owner =
            cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .findFirst()
                .orElseThrow()
                .getValue();
        events.jobChanged(owner, "private-job");
        events.flush();
        assertThat(a.next(json).path("jobs").toString()).contains("private-job");
        assertThat(b.next(json).path("jobs").toString()).doesNotContain("private-job");
        wa.sendText("{\"command\":\"forbidden\"}", true).join();
        assertThat(a.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
      } finally {
        wa.abort();
        wb.abort();
      }
    }
  }
}
