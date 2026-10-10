package com.personal.dashboard.communication.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.dto.BridgeDto;
import com.personal.dashboard.global.WorkspaceException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Token-protected fixed local broker; public callers cannot provide hosts, URLs or CDP commands.
 */
@Component
public class BrowserBridgeAdapter {
  private final ObjectMapper json;
  private final String host;
  private final Path tokenPath;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public BrowserBridgeAdapter(
      ObjectMapper json,
      @Value("${workspace.browser-host:localhost}") String host,
      @Value("${workspace.communication-bridge-token-path:/run/communication-bridge/token}")
          String tokenPath) {
    this.json = json;
    this.host = host;
    this.tokenPath = Path.of(tokenPath);
  }

  public boolean configured() {
    return !readToken().isEmpty();
  }

  private String readToken() {
    try {
      if (!Files.isRegularFile(tokenPath, LinkOption.NOFOLLOW_LINKS)) return "";
      try (var stream = Files.newInputStream(tokenPath, LinkOption.NOFOLLOW_LINKS)) {
        String value = new String(stream.readNBytes(65), StandardCharsets.US_ASCII);
        return value.matches("[a-f0-9]{64}") ? value : "";
      }
    } catch (Exception exception) {
      return "";
    }
  }

  public int start(String id, String provider) {
    var result = request(provider, "POST", id, "", Map.of("provider", provider));
    int port = result.path("vncPort").asInt();
    if (provider.equals("KAKAOTALK") ? port < 5920 || port > 5923 : port < 5910 || port > 5913)
      throw new WorkspaceException(502, "Bridge 화면 포트를 확인하지 못했습니다.");
    return port;
  }

  public void stop(String id, String provider) {
    request(provider, "POST", id, "/stop", null);
  }

  public void delete(String id, String provider) {
    request(provider, "DELETE", id, "", null);
  }

  public BridgeDto.Snapshot snapshot(String id, String provider) {
    var result = request(provider, "GET", id, "/snapshot", null);
    try {
      return json.treeToValue(result, BridgeDto.Snapshot.class);
    } catch (Exception exception) {
      throw new WorkspaceException(502, "Bridge 상태를 읽지 못했습니다.");
    }
  }

  private com.fasterxml.jackson.databind.JsonNode request(
      String provider, String method, String id, String suffix, Object body) {
    String token = readToken();
    if (token.isEmpty()) throw new WorkspaceException(409, "브라우저가 아직 준비되지 않았습니다. 잠시 후 다시 시도해 주세요.");
    if (!id.matches("[a-f0-9-]{36}")) throw new WorkspaceException(400, "프로필 ID를 확인해 주세요.");
    try {
      var request =
          HttpRequest.newBuilder(
                  new URI(
                      "http",
                      null,
                      host,
                      provider.equals("KAKAOTALK") ? 9225 : 9224,
                      "/profiles/" + id + suffix,
                      null,
                      null))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        byte[] bytes = stream.readNBytes(1048577);
        if (response.statusCode() != 200 || bytes.length > 1048576)
          throw new WorkspaceException(502, "원격 앱 Bridge를 사용할 수 없습니다. 서버 설정과 실행 상태를 확인해 주세요.");
        return json.readTree(bytes);
      }
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new WorkspaceException(502, "Bridge 연결이 중단되었습니다.");
    } catch (Exception exception) {
      throw new WorkspaceException(502, "Bridge 서버에 연결하지 못했습니다.");
    }
  }
}
