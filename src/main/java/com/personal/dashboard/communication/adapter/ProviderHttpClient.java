package com.personal.dashboard.communication.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.WorkspaceException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Fixed-origin HTTP boundary: bounded bodies, no redirects, no raw upstream error disclosure. */
@Component
public class ProviderHttpClient {
  private static final Set<String> HOSTS =
      Set.of("gmail.googleapis.com", "oauth2.googleapis.com", "slack.com", "discord.com");
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final ObjectMapper json;

  public ProviderHttpClient(ObjectMapper json) {
    this.json = json;
  }

  public static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  public static String form(Map<String, String> fields) {
    return fields.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(java.util.stream.Collectors.joining("&"));
  }

  public JsonNode get(String url, String authorization) {
    return request("GET", url, authorization, "", null);
  }

  public JsonNode post(String url, String authorization, Object body) {
    return request("POST", url, authorization, "application/json", body);
  }

  public JsonNode formPost(String url, Map<String, String> fields) {
    return request("POST", url, "", "application/x-www-form-urlencoded", form(fields));
  }

  public JsonNode multipart(
      String url,
      String authorization,
      Map<String, Object> payload,
      java.util.List<com.personal.dashboard.communication.domain.Communication.Upload> files) {
    try {
      String boundary = "workspace_" + UUID.randomUUID().toString().replace("-", "");
      var bytes = new java.io.ByteArrayOutputStream();
      bytes.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n"
                  + json.writeValueAsString(payload)
                  + "\r\n")
              .getBytes(StandardCharsets.UTF_8));
      for (int index = 0; index < files.size(); index++) {
        var file = files.get(index);
        String name = file.name().replace("\"", "_").replace("\\", "_");
        bytes.write(
            ("--"
                    + boundary
                    + "\r\nContent-Disposition: form-data; name=\"files["
                    + index
                    + "]\"; filename=\""
                    + name
                    + "\"\r\nContent-Type: "
                    + file.mediaType()
                    + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        bytes.write(file.data());
        bytes.write("\r\n".getBytes(StandardCharsets.US_ASCII));
      }
      bytes.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
      return request(
          "POST",
          url,
          authorization,
          "multipart/form-data; boundary=" + boundary,
          bytes.toByteArray());
    } catch (java.io.IOException exception) {
      throw new WorkspaceException(400, "첨부파일을 준비하지 못했습니다.");
    }
  }

  public void uploadSlack(String url, byte[] bytes) {
    try {
      URI uri = URI.create(url);
      if (!"https".equals(uri.getScheme())
          || !"files.slack.com".equals(uri.getHost())
          || uri.getUserInfo() != null
          || uri.getPort() != -1
          || !uri.getPath().startsWith("/upload/v1/"))
        throw new WorkspaceException(400, "파일 업로드 주소를 확인해 주세요.");
      var request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(25))
              .header("Content-Type", "application/octet-stream")
              .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        stream.readNBytes(1024);
        if (response.statusCode() < 200 || response.statusCode() >= 300)
          throw new WorkspaceException(502, "Slack 파일 업로드에 실패했습니다.");
      }
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new WorkspaceException(502, "파일 업로드가 중단되었습니다.");
    } catch (Exception exception) {
      throw new WorkspaceException(502, "파일 업로드에 연결하지 못했습니다.");
    }
  }

  public byte[] download(String url, String authorization, String expectedHost) {
    try {
      URI uri = URI.create(url);
      if (!Set.of("files.slack.com", "cdn.discordapp.com", "media.discordapp.net")
              .contains(expectedHost)
          || !expectedHost.equals(uri.getHost())
          || !"https".equals(uri.getScheme())
          || uri.getUserInfo() != null
          || uri.getPort() != -1) throw new WorkspaceException(400, "첨부파일 주소를 확인해 주세요.");
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25));
      if (!authorization.isBlank()) request.header("Authorization", authorization);
      var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        if (response.statusCode() != 200) throw new WorkspaceException(502, "첨부파일을 가져오지 못했습니다.");
        byte[] bytes = stream.readNBytes(5 * 1024 * 1024 + 1);
        if (bytes.length > 5 * 1024 * 1024) throw new WorkspaceException(413, "첨부파일은 최대 5 MiB입니다.");
        return bytes;
      }
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new WorkspaceException(502, "첨부파일 연결이 중단되었습니다.");
    } catch (Exception exception) {
      throw new WorkspaceException(502, "첨부파일에 연결하지 못했습니다.");
    }
  }

  public JsonNode request(
      String method, String url, String authorization, String contentType, Object body) {
    try {
      URI uri = URI.create(url);
      if (!"https".equals(uri.getScheme())
          || !HOSTS.contains(uri.getHost())
          || uri.getUserInfo() != null
          || uri.getPort() != -1) throw new WorkspaceException(400, "허용되지 않은 Provider 주소입니다.");
      var request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(25))
              .header("Accept", "application/json");
      if (!authorization.isBlank()) request.header("Authorization", authorization);
      if (body != null) request.header("Content-Type", contentType);
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : body instanceof byte[]
                  ? HttpRequest.BodyPublishers.ofByteArray((byte[]) body)
                  : HttpRequest.BodyPublishers.ofString(
                      body instanceof String ? (String) body : json.writeValueAsString(body)));
      var response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        int status = response.statusCode();
        if (status == 401 || status == 403)
          throw new WorkspaceException(409, "Provider 인증 또는 권한을 확인하고 다시 연결해 주세요.");
        if (status == 404) throw new WorkspaceException(404, "Provider 리소스를 찾을 수 없습니다.");
        if (status == 429)
          throw new WorkspaceException(429, "Provider 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
        if (status < 200 || status >= 300)
          throw new WorkspaceException(502, "Provider 요청이 실패했습니다. 연결 상태를 확인해 주세요.");
        if (status == 204) return json.createObjectNode();
        byte[] bytes = stream.readNBytes(8 * 1024 * 1024 + 1);
        if (bytes.length > 8 * 1024 * 1024)
          throw new WorkspaceException(413, "Provider 응답 크기 한도를 초과했습니다.");
        JsonNode result = json.readTree(bytes);
        if (result == null) throw new WorkspaceException(502, "Provider 응답이 비어 있습니다.");
        if (result.has("ok") && !result.path("ok").asBoolean()) {
          if ("ratelimited".equals(result.path("error").asText()))
            throw new WorkspaceException(429, "Slack 요청 한도에 도달했습니다.");
          throw new WorkspaceException(409, "Slack 권한 또는 요청 상태를 확인해 주세요.");
        }
        return result;
      }
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new WorkspaceException(502, "Provider 연결이 중단되었습니다.");
    } catch (Exception exception) {
      throw new WorkspaceException(502, "Provider 연결을 완료하지 못했습니다.");
    }
  }
}
