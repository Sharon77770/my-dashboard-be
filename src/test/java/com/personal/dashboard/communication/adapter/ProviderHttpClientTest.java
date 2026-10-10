package com.personal.dashboard.communication.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.global.WorkspaceException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Real HTTP error mapping with synthetic Google responses; no account or network access. */
class ProviderHttpClientTest {
  private final HttpClient transport = mock(HttpClient.class);
  private final ProviderHttpClient client = new ProviderHttpClient(new ObjectMapper(), transport);

  @SuppressWarnings("unchecked")
  private void response(int status, String body) throws Exception {
    HttpResponse<InputStream> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body())
        .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    when(transport.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);
  }

  @ParameterizedTest
  @CsvSource({
    "accessNotConfigured, 409, Gmail API가 사용 설정",
    "SERVICE_DISABLED, 409, Gmail API가 사용 설정",
    "domainPolicy, 409, 조직 정책",
    "insufficientPermissions, 409, 권한이 부족",
    "ACCESS_TOKEN_SCOPE_INSUFFICIENT, 409, 권한이 부족",
    "rateLimitExceeded, 429, 요청 한도",
    "userRateLimitExceeded, 429, 요청 한도",
    "dailyLimitExceeded, 429, 요청 한도"
  })
  void distinguishesGmailFailuresWithoutDisclosingUpstreamText(
      String reason, int status, String message) throws Exception {
    String location = reason.contains("_") ? "details" : "errors";
    response(
        403,
        "{\"error\":{\"message\":\"private-token\",\""
            + location
            + "\":[{\"reason\":\""
            + reason
            + "\",\"metadata\":{\"consumer\":\"private-project\"}}]}}");
    assertThatThrownBy(
            () ->
                client.get(
                    "https://gmail.googleapis.com/gmail/v1/users/me/profile", "Bearer fixture"))
        .isInstanceOfSatisfying(
            WorkspaceException.class,
            failure -> {
              assertThat(failure.status()).isEqualTo(status);
              assertThat(failure.getMessage())
                  .contains(message)
                  .doesNotContain("private-token", "private-project");
            });
    verify(transport, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @ParameterizedTest
  @CsvSource({
    "invalid_client, 401, CLIENT_SECRET",
    "invalid_grant, 400, 새로 시작",
    "redirect_uri_mismatch, 400, Redirect URI"
  })
  void distinguishesTokenExchangeFailures(String error, int status, String message)
      throws Exception {
    response(status, "{\"error\":\"" + error + "\",\"error_description\":\"private-code\"}");
    assertThatThrownBy(
            () ->
                client.formPost(
                    "https://oauth2.googleapis.com/token", java.util.Map.of("code", "fixture")))
        .isInstanceOfSatisfying(
            WorkspaceException.class,
            failure -> {
              assertThat(failure.status()).isEqualTo(409);
              assertThat(failure.getMessage()).contains(message).doesNotContain("private-code");
            });
  }

  @Test
  void malformedAndOversizedErrorsUseSafeFallback() throws Exception {
    for (String body : new String[] {"<html>private-token</html>", "x".repeat(20000), "null"}) {
      response(403, body);
      assertThatThrownBy(
              () ->
                  client.get(
                      "https://gmail.googleapis.com/gmail/v1/users/me/profile", "Bearer fixture"))
          .isInstanceOf(WorkspaceException.class)
          .hasMessageContaining("Gmail API 접근이 거부")
          .hasMessageNotContaining("private-token");
    }
  }

  @Test
  void successfulProfileAndOtherProvidersKeepTheirContracts() throws Exception {
    response(200, "{\"emailAddress\":\"fixture@example.test\"}");
    assertThat(
            client
                .get("https://gmail.googleapis.com/gmail/v1/users/me/profile", "Bearer fixture")
                .path("emailAddress")
                .asText())
        .isEqualTo("fixture@example.test");
    response(403, "{\"error\":{\"errors\":[{\"reason\":\"accessNotConfigured\"}]}}");
    assertThatThrownBy(() -> client.get("https://slack.com/api/auth.test", "Bearer fixture"))
        .hasMessage("Provider 인증 또는 권한을 확인하고 다시 연결해 주세요.");
  }
}
