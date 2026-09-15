package com.personal.dashboard.tailscale.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.tailscale.dto.TailscaleView;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Calls a narrow authenticated loopback facade, never the privileged daemon socket. */
@Component
public class TailscaleAdapter {
  private final ObjectMapper json;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

  public TailscaleAdapter(ObjectMapper json) {
    this.json = json;
  }

  public TailscaleView invoke(String method) {
    try {
      String token = Files.readString(Path.of("/run/dashboard-tailscale/bridge-token")).trim();
      var request =
          HttpRequest.newBuilder(
                  URI.create(
                      "http://127.0.0.1:41113/" + (method.equals("GET") ? "status" : "login")))
              .timeout(Duration.ofSeconds(25))
              .header("Authorization", "Bearer " + token)
              .method(method, HttpRequest.BodyPublishers.noBody())
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new WorkspaceException(502, "Tailscale 연결 상태를 확인할 수 없습니다. 잠시 후 다시 시도하세요.");
      return json.readValue(response.body(), TailscaleView.class);
    } catch (WorkspaceException error) {
      throw error;
    } catch (HttpTimeoutException error) {
      throw new WorkspaceException(502, "Tailscale 응답 시간이 초과되었습니다.");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new WorkspaceException(502, "Tailscale 요청이 중단되었습니다.");
    } catch (Exception error) {
      throw new WorkspaceException(
          503, "Tailscale 관리 서비스가 준비되지 않았습니다. 최신 Docker Compose로 실행해 주세요.");
    }
  }
}
