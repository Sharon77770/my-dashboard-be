package com.personal.dashboard.communication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.adapter.ProviderHttpClient;
import com.personal.dashboard.communication.entity.CommunicationRecords.AccountRecord;
import com.personal.dashboard.communication.repository.CommunicationRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Refresh tokens never leave the server; refresh failure requires reconnect instead of silent
 * retries.
 */
@Service
public class CommunicationTokens {
  public record Credential(
      String accessToken, String refreshToken, long expiresAt, boolean reconnectRequired) {
    public Credential(String accessToken, String refreshToken, long expiresAt) {
      this(accessToken, refreshToken, expiresAt, false);
    }

    @Override
    public String toString() {
      return "Credential[redacted]";
    }
  }

  private final CommunicationRepository repository;
  private final CredentialVault vault;
  private final ObjectMapper json;
  private final ProviderHttpClient http;
  private final Environment env;

  public CommunicationTokens(
      CommunicationRepository repository,
      CredentialVault vault,
      ObjectMapper json,
      ProviderHttpClient http,
      Environment env) {
    this.repository = repository;
    this.vault = vault;
    this.json = json;
    this.http = http;
    this.env = env;
  }

  public synchronized String accessToken(AccountRecord snapshot) {
    var account =
        repository.accounts().stream()
            .filter(item -> item.id().equals(snapshot.id()))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(404, "연결 해제된 계정입니다."));
    try {
      var credential = json.readValue(vault.decrypt(account.credentialCipher()), Credential.class);
      if (credential.reconnectRequired()
          || credential.accessToken() == null
          || credential.accessToken().isBlank()) throw reconnect();
      if (credential.expiresAt() == 0
          || credential.expiresAt() > System.currentTimeMillis() + 60000)
        return credential.accessToken();
      if (credential.refreshToken() == null || credential.refreshToken().isBlank())
        throw reconnect();
      boolean gmail = account.provider().equals("GMAIL");
      if (!gmail && !account.provider().equals("SLACK")) throw reconnect();
      String prefix = gmail ? "COMMUNICATION_GOOGLE_" : "COMMUNICATION_SLACK_";
      String clientId = env.getProperty(prefix + "CLIENT_ID", ""),
          clientSecret = env.getProperty(prefix + "CLIENT_SECRET", "");
      if (clientId.isBlank() || clientSecret.isBlank())
        throw new WorkspaceException(409, "OAuth Client 서버 설정을 확인해 주세요.");
      // Claim before the one-use exchange. Crash/ambiguous failure requires reconnect,
      // rather than replaying a refresh token that the provider may already have consumed.
      String claimed =
          vault.encrypt(
              json.writeValueAsString(
                  new Credential(
                      credential.accessToken(),
                      credential.refreshToken(),
                      credential.expiresAt(),
                      true)));
      if (!repository.replaceCredential(account.id(), account.credentialCipher(), claimed))
        throw reconnect();
      try {
        var response =
            http.formPost(
                gmail
                    ? "https://oauth2.googleapis.com/token"
                    : "https://slack.com/api/oauth.v2.access",
                Map.of(
                    "grant_type",
                    "refresh_token",
                    "refresh_token",
                    credential.refreshToken(),
                    "client_id",
                    clientId,
                    "client_secret",
                    clientSecret));
        String access = response.path("access_token").asText();
        String refresh =
            response.path("refresh_token").asText(gmail ? credential.refreshToken() : "");
        var lifetime = response.path("expires_in");
        if (access.isBlank()
            || refresh.isBlank()
            || !lifetime.isIntegralNumber()
            || !lifetime.canConvertToLong()
            || lifetime.asLong() <= 0) throw reconnect();
        long expires =
            Math.addExact(System.currentTimeMillis(), Math.multiplyExact(lifetime.asLong(), 1000));
        String replacement =
            vault.encrypt(json.writeValueAsString(new Credential(access, refresh, expires)));
        if (!repository.replaceCredential(account.id(), claimed, replacement)) throw reconnect();
        return access;
      } catch (WorkspaceException exception) {
        // An explicit rate-limit rejection did not issue replacement credentials.
        if (exception.status() == 429)
          repository.replaceCredential(account.id(), claimed, account.credentialCipher());
        throw exception;
      }
    } catch (WorkspaceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw reconnect();
    }
  }

  private static WorkspaceException reconnect() {
    return new WorkspaceException(409, "인증 갱신 상태를 확인할 수 없습니다. 계정을 다시 연결해 주세요.");
  }
}
