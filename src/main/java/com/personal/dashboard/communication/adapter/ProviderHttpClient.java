package com.personal.dashboard.communication.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.WorkspaceException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Fixed-origin HTTP boundary: bounded bodies, no redirects, no raw upstream error disclosure. */
@Component
public class ProviderHttpClient {
  private static final Set<String> HOSTS =
      Set.of("gmail.googleapis.com", "oauth2.googleapis.com", "slack.com", "discord.com");
  private final HttpClient client;
  private final ObjectMapper json;

  @Autowired
  public ProviderHttpClient(ObjectMapper json) {
    this(
        json,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  ProviderHttpClient(ObjectMapper json, HttpClient client) {
    this.json = json;
    this.client = client;
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
        if (status >= 400
            && status < 500
            && Set.of("oauth2.googleapis.com", "gmail.googleapis.com").contains(uri.getHost())) {
          WorkspaceException failure = googleFailure(uri, status, stream.readNBytes(16385));
          if (failure != null) throw failure;
        }
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

  /**
   * Only known Google error identifiers select local text; upstream messages may contain secrets.
   */
  private WorkspaceException googleFailure(URI uri, int status, byte[] bytes) {
    JsonNode error = json.createObjectNode();
    if (bytes.length <= 16384) {
      try {
        JsonNode response = json.readTree(bytes);
        if (response != null) error = response.path("error");
      } catch (java.io.IOException ignored) {
        // Non-JSON or truncated failures still receive a safe, stage-specific fallback.
      }
    }
    if ("oauth2.googleapis.com".equals(uri.getHost())) {
      return switch (error.asText()) {
        case "invalid_client", "unauthorized_client" ->
            new WorkspaceException(
                409,
                "Google OAuth 클라이언트 인증에 실패했습니다. 서버의 COMMUNICATION_GOOGLE_CLIENT_ID와 CLIENT_SECRET이 같은 웹 애플리케이션 클라이언트의 값인지 확인하고, 설정 반영 후 Gmail 연결을 새로 시작해 주세요.");
        case "invalid_grant" ->
            new WorkspaceException(
                409,
                "Google 인증 코드 또는 갱신 토큰이 만료·취소되었거나 이미 사용되었습니다. 콜백 URL을 새로고침하지 말고 Gmail 연결을 새로 시작해 주세요. 반복되면 OAuth Redirect URI 설정을 확인해 주세요.");
        case "redirect_uri_mismatch" ->
            new WorkspaceException(
                409,
                "Google OAuth Redirect URI가 일치하지 않습니다. 서버의 COMMUNICATION_GOOGLE_REDIRECT_URI와 Google Cloud 웹 클라이언트의 승인된 리디렉션 URI를 일치시켜 주세요.");
        default ->
            status == 400 || status == 401 || status == 403
                ? new WorkspaceException(
                    409,
                    "Google OAuth 토큰 교환에 실패했습니다. 서버 OAuth 클라이언트와 Redirect URI 설정을 확인한 뒤 Gmail 연결을 새로 시작해 주세요.")
                : null;
      };
    }
    Set<String> reasons = new HashSet<>();
    for (var entry : error.path("errors")) reasons.add(entry.path("reason").asText());
    for (var entry : error.path("details")) reasons.add(entry.path("reason").asText());
    if (status == 403) {
      if (reasons.contains("accessNotConfigured") || reasons.contains("SERVICE_DISABLED"))
        return new WorkspaceException(
            409,
            "OAuth 클라이언트가 속한 Google Cloud 프로젝트에서 Gmail API가 사용 설정되어 있지 않습니다. 해당 프로젝트의 API 및 서비스에서 Gmail API를 사용 설정하고 잠시 후 Gmail 연결을 새로 시작해 주세요.");
      if (reasons.contains("domainPolicy"))
        return new WorkspaceException(
            409,
            "Google Workspace 조직 정책이 Gmail API 접근을 차단했습니다. 조직 관리자에게 이 앱의 Gmail 접근 허용을 요청해 주세요.");
      if (reasons.contains("insufficientPermissions")
          || reasons.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT"))
        return new WorkspaceException(
            409, "Gmail 접근 권한이 부족합니다. Gmail 연결을 새로 시작하고 요청한 메일 권한을 허용해 주세요.");
      if (!Collections.disjoint(
          reasons,
          Set.of(
              "rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded", "quotaExceeded")))
        return new WorkspaceException(
            429, "Gmail API 요청 한도에 도달했습니다. Google Cloud 프로젝트의 할당량을 확인하고 잠시 후 다시 시도해 주세요.");
      return new WorkspaceException(
          409, "Gmail API 접근이 거부되었습니다. OAuth 클라이언트 프로젝트의 Gmail API 사용 설정, 메일 권한 및 조직 정책을 확인해 주세요.");
    }
    if (status == 401)
      return new WorkspaceException(409, "Gmail API 인증이 만료되었거나 유효하지 않습니다. Gmail 연결을 새로 시작해 주세요.");
    return null;
  }
}
