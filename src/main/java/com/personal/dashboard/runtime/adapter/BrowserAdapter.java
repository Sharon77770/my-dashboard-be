package com.personal.dashboard.runtime.adapter;

import com.personal.dashboard.global.WorkspaceException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Launches a browser tab through a server-only Chromium DevTools endpoint. */
@Component
public class BrowserAdapter {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public void open(String host, int port, String url) {
    try {
      // Chromium accepts an IP/localhost Host header; Docker service names are resolved
      // server-side.
      URI endpoint =
          new URI(
              "http",
              null,
              InetAddress.getByName(host).getHostAddress(),
              port,
              "/json/new",
              null,
              null);
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(
                      endpoint
                          + "?"
                          + URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8)))
              .timeout(Duration.ofSeconds(10))
              .PUT(HttpRequest.BodyPublishers.noBody())
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() != 200) throw new IllegalStateException();
    } catch (Exception exception) {
      throw new WorkspaceException(502, "브라우저 서버에 연결하지 못했습니다. 실행 상태와 원격 디버깅 포트를 확인해 주세요.");
    }
  }
}
