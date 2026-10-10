package com.personal.dashboard.communication.service;

import com.personal.dashboard.communication.adapter.ProviderHttpClient;
import com.personal.dashboard.communication.domain.Communication.ProviderId;
import com.personal.dashboard.communication.dto.CommunicationDto;
import com.personal.dashboard.global.WorkspaceException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Single-use OAuth state belongs to the initiating browser session and expires after ten minutes.
 */
@Service
@PreAuthorize("hasRole('OWNER')")
public class CommunicationOAuthService {
  private record Pending(ProviderId provider, String session, long expiresAt) {}

  private final Map<String, Pending> pending = new ConcurrentHashMap<>();
  private final Environment env;
  private final ProviderHttpClient http;
  private final CommunicationService service;

  public CommunicationOAuthService(
      Environment env, ProviderHttpClient http, CommunicationService service) {
    this.env = env;
    this.http = http;
    this.service = service;
  }

  public List<CommunicationDto.Provider> providers() {
    return List.of(
        new CommunicationDto.Provider(
            "GMAIL",
            configured(ProviderId.GMAIL),
            "OAUTH",
            "Gmail API · 읽기/전송 권한 및 OAuth Client 필요"),
        new CommunicationDto.Provider(
            "SLACK", configured(ProviderId.SLACK), "OAUTH", "승인된 Bot 채널·DM만 접근, Provider 요청 한도 적용"),
        new CommunicationDto.Provider(
            "DISCORD", true, "BOT_TOKEN", "Bot 전용 · 메시지 본문 Intent 필요 · 개인 계정 DM 미지원"),
        new CommunicationDto.Provider(
            "KAKAOTALK", false, "WINDOWS_AGENT", "Windows 접근성 기술 검증 필요 · 일반 채팅 접근 미확인"));
  }

  public CommunicationDto.Authorization start(ProviderId provider, String session) {
    if (!configured(provider))
      throw new WorkspaceException(409, "서버 OAuth Client와 Redirect URI를 설정해 주세요.");
    pending.entrySet().removeIf(item -> item.getValue().expiresAt() < System.currentTimeMillis());
    if (pending.size() >= 32) throw new WorkspaceException(429, "진행 중인 인증이 많습니다. 잠시 후 다시 시도해 주세요.");
    byte[] nonce = new byte[32];
    new SecureRandom().nextBytes(nonce);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    pending.put(state, new Pending(provider, session, System.currentTimeMillis() + 600000));
    boolean gmail = provider == ProviderId.GMAIL;
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("client_id", setting(provider, "CLIENT_ID"));
    fields.put("redirect_uri", redirect(provider));
    fields.put("state", state);
    if (gmail) {
      fields.put("response_type", "code");
      fields.put("scope", "https://www.googleapis.com/auth/gmail.modify");
      fields.put("access_type", "offline");
      fields.put("prompt", "consent");
    } else
      fields.put(
          "scope",
          "channels:read,groups:read,im:read,mpim:read,channels:history,groups:history,im:history,mpim:history,chat:write,reactions:write,files:read,files:write");
    return new CommunicationDto.Authorization(
        (gmail
                ? "https://accounts.google.com/o/oauth2/v2/auth?"
                : "https://slack.com/oauth/v2/authorize?")
            + ProviderHttpClient.form(fields));
  }

  public void finish(String state, String code, String session) {
    Pending flow = pending.get(state);
    if (flow == null
        || !flow.session().equals(session)
        || flow.expiresAt() < System.currentTimeMillis()
        || !pending.remove(state, flow))
      throw new WorkspaceException(403, "인증 요청이 만료되었거나 다른 브라우저에서 시작되었습니다.");
    if (code == null || code.isBlank() || code.length() > 4096)
      throw new WorkspaceException(400, "인증이 취소되었거나 코드가 없습니다.");
    boolean gmail = flow.provider() == ProviderId.GMAIL;
    var response =
        http.formPost(
            gmail ? "https://oauth2.googleapis.com/token" : "https://slack.com/api/oauth.v2.access",
            Map.of(
                "grant_type",
                "authorization_code",
                "code",
                code,
                "client_id",
                setting(flow.provider(), "CLIENT_ID"),
                "client_secret",
                setting(flow.provider(), "CLIENT_SECRET"),
                "redirect_uri",
                redirect(flow.provider())));
    long expires =
        response.has("expires_in")
            ? System.currentTimeMillis() + response.path("expires_in").asLong() * 1000
            : 0;
    service.connect(
        flow.provider(),
        response.path("access_token").asText(),
        response.path("refresh_token").asText(),
        expires,
        new HashSet<>(Arrays.asList(response.path("scope").asText().split("[ ,]+"))));
  }

  private boolean configured(ProviderId provider) {
    return (provider == ProviderId.GMAIL || provider == ProviderId.SLACK)
        && !setting(provider, "CLIENT_ID").isBlank()
        && !setting(provider, "CLIENT_SECRET").isBlank()
        && !setting(provider, "REDIRECT_URI").isBlank();
  }

  private String setting(ProviderId provider, String suffix) {
    return env.getProperty(
        (provider == ProviderId.GMAIL ? "COMMUNICATION_GOOGLE_" : "COMMUNICATION_SLACK_") + suffix,
        "");
  }

  private String redirect(ProviderId provider) {
    String value = setting(provider, "REDIRECT_URI");
    try {
      URI uri = URI.create(value);
      if (uri.getUserInfo() != null
          || uri.getFragment() != null
          || uri.getQuery() != null
          || !("https".equals(uri.getScheme())
              || ("http".equals(uri.getScheme())
                  && Set.of("localhost", "127.0.0.1").contains(uri.getHost())))
          || !uri.getPath().endsWith("/api/v1/communications/oauth/callback"))
        throw new IllegalArgumentException();
      return value;
    } catch (Exception exception) {
      throw new WorkspaceException(409, "OAuth Redirect URI 설정을 확인해 주세요.");
    }
  }
}
