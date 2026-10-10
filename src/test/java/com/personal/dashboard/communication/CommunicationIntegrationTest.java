package com.personal.dashboard.communication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.adapter.*;
import com.personal.dashboard.communication.dto.CommunicationDto.SendRequest;
import com.personal.dashboard.communication.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Mock upstream, real SQLite and HTTP security: not live provider proof. */
@SpringBootTest(
    properties = {
      "DASHBOARD_AUTH_ID=communication-test",
      "DASHBOARD_AUTH_PASSWORD=communication-test-only",
      "DASHBOARD_DB_PATH=./target/communication-test.db",
      "workspace.key-path=./target/communication-test.key",
      "workspace.root=./target/communication-files",
      "COMMUNICATION_SLACK_SIGNING_SECRET=fixture-signing-secret",
      "COMMUNICATION_SLACK_CLIENT_ID=fixture",
      "COMMUNICATION_SLACK_CLIENT_SECRET=fixture",
      "COMMUNICATION_GOOGLE_CLIENT_ID=fixture",
      "COMMUNICATION_GOOGLE_CLIENT_SECRET=fixture",
      "COMMUNICATION_GOOGLE_REDIRECT_URI=http://localhost/api/v1/communications/oauth/callback"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CommunicationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired CommunicationService service;
  @Autowired GmailSyncService gmailSync;
  @Autowired CommunicationMcpTools mcp;
  @Autowired CommunicationTokens tokens;
  @Autowired com.personal.dashboard.communication.repository.CommunicationRepository repository;
  @Autowired com.personal.dashboard.global.security.CredentialVault vault;
  @MockitoBean ProviderHttpClient http;

  @BeforeEach
  void setup() throws Exception {
    jdbc.update("DELETE FROM communication_accounts");
    when(http.get(eq("https://discord.com/api/v10/users/@me"), anyString()))
        .thenReturn(json.readTree("{\"id\":\"bot1\",\"username\":\"Fixture Bot\",\"bot\":true}"));
    when(http.get(contains("/channels/channel1/messages"), anyString()))
        .thenReturn(
            json.readTree(
                "[{\"id\":\"m2\",\"content\":\"second\",\"timestamp\":\"2026-01-01T12:01:00Z\"},{\"id\":\"m1\",\"content\":\"first\",\"timestamp\":\"2026-01-01T12:00:00Z\"},{\"id\":\"m1\",\"content\":\"deduplicated\",\"timestamp\":\"2026-01-01T12:00:00Z\"}]"));
    when(http.post(contains("/channels/channel1/messages"), anyString(), any()))
        .thenReturn(json.readTree("{\"id\":\"sent1\",\"content\":\"approved\"}"));
  }

  private String connect() throws Exception {
    var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
    String response =
        mvc.perform(
                post("/api/v1/communications/accounts")
                    .with(user("owner").roles("OWNER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"provider\":\"DISCORD\",\"token\":\"fixture-secret\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).doesNotContain("fixture-secret", "credential");
    org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
    return json.readTree(response).path("id").asText();
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void gmailReplyShowsExactDestinationAndRejectsChangedDestinationAfterApproval() throws Exception {
    when(http.get(endsWith("/profile"), anyString()))
        .thenReturn(json.readTree("{\"emailAddress\":\"fixture@example.test\"}"));
    var original =
        json.readTree(
            """
        {"threadId":"thread1","payload":{"headers":[
          {"name":"From","value":"sender@example.test"},
          {"name":"Reply-To","value":"reply@example.test"},
          {"name":"Subject","value":"Original subject"},
          {"name":"Message-ID","value":"<original@example.test>"}]}}
        """);
    when(http.get(endsWith("/messages/original?format=metadata"), anyString()))
        .thenReturn(original);
    String id =
        service
            .connect(
                com.personal.dashboard.communication.domain.Communication.ProviderId.GMAIL,
                "fixture",
                "",
                0,
                Set.of("https://www.googleapis.com/auth/gmail.modify"))
            .id();
    var action =
        service.prepare(id, new SendRequest("thread1", "", "", "approved body", "original"));
    assertThat(action.message().recipient()).isEqualTo("reply@example.test");
    assertThat(action.message().subject()).isEqualTo("Original subject");
    verify(http, never()).post(endsWith("/messages/send"), anyString(), any());
    var changed = original.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            changed.path("payload").path("headers").get(1))
        .put("value", "changed@example.test");
    when(http.get(endsWith("/messages/original?format=metadata"), anyString())).thenReturn(changed);
    assertThatThrownBy(() -> service.confirm(action.id(), "browser"))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    verify(http, never()).post(endsWith("/messages/send"), anyString(), any());
    assertThat(service.actions().getFirst().state()).isEqualTo("UNKNOWN");
    when(http.get(endsWith("/messages/original?format=metadata"), anyString()))
        .thenReturn(original);
    when(http.post(endsWith("/messages/send"), anyString(), any()))
        .thenReturn(json.readTree("{\"id\":\"sent\",\"threadId\":\"thread1\"}"));
    var approved =
        service.prepare(id, new SendRequest("thread1", "", "", "approved body", "original"));
    service.confirm(approved.id(), "browser");
    var body = org.mockito.ArgumentCaptor.forClass(Object.class);
    verify(http).post(endsWith("/messages/send"), anyString(), body.capture());
    String raw =
        new String(
            Base64.getUrlDecoder().decode((String) ((Map<?, ?>) body.getValue()).get("raw")),
            java.nio.charset.StandardCharsets.UTF_8);
    assertThat(raw)
        .contains("To: reply@example.test", "In-Reply-To: <original@example.test>")
        .doesNotContain("changed@example.test");
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void providerSearchPreservesOriginalIdsPagingAndSharedMcpScope() throws Exception {
    when(http.get(endsWith("/profile"), anyString()))
        .thenReturn(json.readTree("{\"emailAddress\":\"fixture@example.test\"}"));
    String id =
        service
            .connect(
                com.personal.dashboard.communication.domain.Communication.ProviderId.GMAIL,
                "fixture",
                "",
                0,
                Set.of("https://www.googleapis.com/auth/gmail.modify"))
            .id();
    String root = "https://gmail.googleapis.com/gmail/v1/users/me";
    when(http.get(eq(root + "/messages?maxResults=10&pageToken=&q=is%3Aunread"), anyString()))
        .thenReturn(
            json.readTree(
                "{\"messages\":[{\"id\":\"m1\"},{\"id\":\"m1\"}],\"nextPageToken\":\"next+page\"}"));
    when(http.get(
            eq(root + "/messages?maxResults=10&pageToken=next%2Bpage&q=is%3Aunread"), anyString()))
        .thenReturn(json.readTree("{\"messages\":[]}"));
    when(http.get(eq(root + "/messages/m1?format=full"), anyString()))
        .thenReturn(
            json.readTree(
                """
        {"id":"m1","threadId":"original-thread","internalDate":"1700000000000","labelIds":["UNREAD"],
         "payload":{"mimeType":"text/plain","body":{"data":"SGVsbG8"},"headers":[{"name":"From","value":"fixture@example.test"}]}}
        """));
    var ownerContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
    mvc.perform(
            get("/api/v1/communications/accounts/" + id + "/message-search")
                .param("query", "is:unread"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].conversationId").value("original-thread"))
        .andExpect(jsonPath("$.items[0].id").value("m1"))
        .andExpect(jsonPath("$.nextCursor").value("next+page"));
    org.springframework.security.core.context.SecurityContextHolder.setContext(ownerContext);
    verify(http).get(eq(root + "/messages/m1?format=full"), anyString());
    assertThat(service.cachedMessages(id, "original-thread")).hasSize(1);
    var result =
        mcp.call(
            "communication_search_messages",
            json.valueToTree(Map.of("accountId", id, "query", "is:unread", "cursor", "next+page")));
    assertThat(result.get("untrustedExternalContent")).isEqualTo(true);
    assertThat(
            ((com.personal.dashboard.communication.dto.CommunicationDto.Page<?>)
                    result.get("result"))
                .items())
        .isEmpty();
    mvc.perform(
            get("/api/v1/communications/accounts/" + id + "/message-search").param("query", " "))
        .andExpect(status().isBadRequest());
    String discord = connect();
    mvc.perform(
            get("/api/v1/communications/accounts/" + discord + "/message-search")
                .param("query", "test"))
        .andExpect(status().isConflict());
    verify(http, never()).post(anyString(), anyString(), any());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void reactionFactsSurviveServiceAndCacheMapping() throws Exception {
    String id = connect();
    when(http.get(contains("/channels/channel1/messages"), anyString()))
        .thenReturn(
            json.readTree(
                """
        [{"id":"m1","content":"fixture","reactions":[{"emoji":{"id":"emoji1","name":"party"},"count":7}]}]
        """));
    var message = service.messages(id, "channel1", "").items().getFirst();
    assertThat(message.reactions().getFirst().count()).isEqualTo(7L);
    assertThat(service.cachedMessages(id, "channel1").getFirst().reactions().getFirst().key())
        .isEqualTo("emoji1");
    assertThat(message.unread()).isNull();
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void slackParticipantsRespectScopesAndPreserveIdsAndPaging() throws Exception {
    when(http.get(endsWith("auth.test"), anyString()))
        .thenReturn(json.readTree("{\"ok\":true,\"team_id\":\"T1\",\"user_id\":\"U1\"}"));
    var provider = com.personal.dashboard.communication.domain.Communication.ProviderId.SLACK;
    String id = service.connect(provider, "fixture", "", 0, Set.of("channels:read")).id();
    when(http.get(contains("conversations.members?limit=100&channel=C1&cursor="), anyString()))
        .thenReturn(
            json.readTree(
                "{\"ok\":true,\"members\":[\"U1\",\"U2\",\"U1\"],\"response_metadata\":{\"next_cursor\":\"next\"}}"));
    var page = service.participants(id, "C1", "");
    assertThat(page.items()).extracting(item -> item.id()).containsExactly("U1", "U2");
    assertThat(page.items().getFirst().label()).isEqualTo("U1");
    assertThat(page.nextCursor()).isEqualTo("next");
    when(http.get(endsWith("conversations.members?limit=100&channel=C1&cursor=next"), anyString()))
        .thenReturn(json.readTree("{\"ok\":true,\"members\":[\"U3\"]}"));
    var result =
        mcp.call(
            "communication_list_participants",
            json.valueToTree(Map.of("accountId", id, "conversationId", "C1", "cursor", "next")));
    assertThat(
            ((com.personal.dashboard.communication.dto.CommunicationDto.Page<?>)
                    result.get("result"))
                .items())
        .hasSize(1);
    assertThat(service.connect(provider, "fixture", "", 0, Set.of()).capabilities())
        .doesNotContain("PARTICIPANTS");
    assertThatThrownBy(() -> service.participants(id, "C1", ""))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    verify(http, times(2)).get(contains("conversations.members"), anyString());
  }

  private com.personal.dashboard.communication.entity.CommunicationRecords.AccountRecord
      expiredAccount(String provider, String refresh) throws Exception {
    when(http.get(endsWith("/profile"), anyString()))
        .thenReturn(json.readTree("{\"emailAddress\":\"fixture@example.test\"}"));
    when(http.get(endsWith("auth.test"), anyString()))
        .thenReturn(json.readTree("{\"team_id\":\"T1\",\"user_id\":\"U1\"}"));
    String id =
        service
            .connect(
                com.personal.dashboard.communication.domain.Communication.ProviderId.valueOf(
                    provider),
                "old-fixture-access",
                refresh,
                1,
                Set.of())
            .id();
    return repository.accounts().stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private CommunicationTokens.Credential storedCredential() throws Exception {
    return json.readValue(
        vault.decrypt(repository.accounts().getFirst().credentialCipher()),
        CommunicationTokens.Credential.class);
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void gmailRefreshRetainsRefreshTokenAndIgnoresStaleAccountSnapshot() throws Exception {
    var stale = expiredAccount("GMAIL", "fixture-refresh");
    when(http.formPost(eq("https://oauth2.googleapis.com/token"), anyMap()))
        .thenReturn(json.readTree("{\"access_token\":\"new-fixture-access\",\"expires_in\":3600}"));
    assertThat(tokens.accessToken(stale)).isEqualTo("new-fixture-access");
    assertThat(tokens.accessToken(stale)).isEqualTo("new-fixture-access");
    verify(http).formPost(eq("https://oauth2.googleapis.com/token"), anyMap());
    assertThat(storedCredential().refreshToken()).isEqualTo("fixture-refresh");
    assertThat(storedCredential().reconnectRequired()).isFalse();
    assertThat(repository.accounts().getFirst().credentialCipher())
        .doesNotContain("fixture-refresh", "new-fixture-access");
    assertThat(storedCredential().toString())
        .doesNotContain("fixture-refresh", "new-fixture-access");
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void concurrentSlackRefreshRotatesOnceAndUsesNewRefreshTokenNextTime() throws Exception {
    var stale = expiredAccount("SLACK", "refresh-zero");
    when(http.formPost(eq("https://slack.com/api/oauth.v2.access"), anyMap()))
        .thenReturn(
            json.readTree(
                "{\"access_token\":\"access-one\",\"refresh_token\":\"refresh-one\",\"expires_in\":43200}"),
            json.readTree(
                "{\"access_token\":\"access-two\",\"refresh_token\":\"refresh-two\",\"expires_in\":43200}"));
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> tokens.accessToken(stale));
      var second = executor.submit(() -> tokens.accessToken(stale));
      assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("access-one");
      assertThat(second.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("access-one");
    }
    verify(http).formPost(eq("https://slack.com/api/oauth.v2.access"), anyMap());
    var current = repository.accounts().getFirst();
    assertThat(
            repository.replaceCredential(
                current.id(),
                current.credentialCipher(),
                vault.encrypt(
                    json.writeValueAsString(
                        new CommunicationTokens.Credential("access-one", "refresh-one", 1)))))
        .isTrue();
    assertThat(tokens.accessToken(stale)).isEqualTo("access-two");
    verify(http)
        .formPost(
            eq("https://slack.com/api/oauth.v2.access"),
            argThat(form -> "refresh-one".equals(form.get("refresh_token"))));
    assertThat(storedCredential().refreshToken()).isEqualTo("refresh-two");
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void disconnectDuringRefreshCannotResurrectAccount() throws Exception {
    var stale = expiredAccount("GMAIL", "fixture-refresh");
    when(http.formPost(anyString(), anyMap()))
        .thenAnswer(
            call -> {
              repository.deleteAccount(stale.id());
              return json.readTree("{\"access_token\":\"new-access\",\"expires_in\":3600}");
            });
    assertThatThrownBy(() -> tokens.accessToken(stale))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    assertThat(repository.accounts()).isEmpty();
    assertThatThrownBy(() -> tokens.accessToken(stale))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    verify(http).formPost(anyString(), anyMap());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void reconnectDuringRefreshKeepsNewLoginCredential() throws Exception {
    var stale = expiredAccount("GMAIL", "fixture-refresh");
    when(http.formPost(anyString(), anyMap()))
        .thenAnswer(
            call -> {
              service.connect(
                  com.personal.dashboard.communication.domain.Communication.ProviderId.GMAIL,
                  "reauthenticated-access",
                  "new-login-refresh",
                  0,
                  Set.of());
              return json.readTree("{\"access_token\":\"old-login-result\",\"expires_in\":3600}");
            });
    assertThatThrownBy(() -> tokens.accessToken(stale))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    assertThat(tokens.accessToken(stale)).isEqualTo("reauthenticated-access");
    assertThat(storedCredential().refreshToken()).isEqualTo("new-login-refresh");
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void ambiguousRefreshFailureRequiresReconnectWithoutReplay() throws Exception {
    var stale = expiredAccount("GMAIL", "fixture-refresh");
    when(http.formPost(anyString(), anyMap()))
        .thenThrow(
            new com.personal.dashboard.global.WorkspaceException(502, "fixture network failure"));
    assertThatThrownBy(() -> tokens.accessToken(stale))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    assertThat(storedCredential().reconnectRequired()).isTrue();
    assertThatThrownBy(() -> tokens.accessToken(stale))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    verify(http).formPost(anyString(), anyMap());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void explicitRefreshRateLimitCanBeRetriedOnNextRequest() throws Exception {
    var stale = expiredAccount("GMAIL", "fixture-refresh");
    when(http.formPost(anyString(), anyMap()))
        .thenThrow(new com.personal.dashboard.global.WorkspaceException(429, "fixture rate limit"))
        .thenReturn(json.readTree("{\"access_token\":\"new-access\",\"expires_in\":3600}"));
    assertThatThrownBy(() -> tokens.accessToken(stale))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    assertThat(storedCredential().reconnectRequired()).isFalse();
    assertThat(tokens.accessToken(stale)).isEqualTo("new-access");
    verify(http, times(2)).formPost(anyString(), anyMap());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void malformedRefreshResponseDoesNotInventExpiry() throws Exception {
    var stale = expiredAccount("GMAIL", "fixture-refresh");
    when(http.formPost(anyString(), anyMap()))
        .thenReturn(json.readTree("{\"access_token\":\"new-access\",\"expires_in\":-1}"));
    assertThatThrownBy(() -> tokens.accessToken(stale))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    assertThat(storedCredential().reconnectRequired()).isTrue();
    assertThat(storedCredential().accessToken()).isEqualTo("old-fixture-access");
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void missingRefreshTokenDoesNotCallProvider() throws Exception {
    var stale = expiredAccount("GMAIL", "");
    assertThatThrownBy(() -> tokens.accessToken(stale))
        .isInstanceOf(com.personal.dashboard.global.WorkspaceException.class);
    verify(http, never()).formPost(anyString(), anyMap());
  }

  @Test
  void authenticationAndCsrfAreRequired() throws Exception {
    mvc.perform(get("/api/v1/communications/accounts")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/communications/accounts").with(user("owner").roles("OWNER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/communications/accounts").with(user("viewer").roles("VIEWER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void deduplicatesOrdersEncryptsAndDisconnects() throws Exception {
    String id = connect();
    assertThat(
            jdbc.queryForObject(
                "SELECT credential_cipher FROM communication_accounts WHERE id=?",
                String.class,
                id))
        .doesNotContain("fixture-secret");
    for (int i = 0; i < 2; i++)
      mvc.perform(
              get("/api/v1/communications/accounts/" + id + "/messages")
                  .with(user("owner").roles("OWNER"))
                  .param("conversationId", "channel1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(2))
          .andExpect(jsonPath("$.items[0].id").value("m1"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_messages", Integer.class))
        .isEqualTo(2);
    mvc.perform(
            delete("/api/v1/communications/accounts/" + id)
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isNoContent());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM communication_messages", Integer.class))
        .isZero();
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void approvalIsRequiredAndConsumedExactlyOnce() throws Exception {
    String id = connect();
    var result =
        mcp.call(
            "communication_send_message",
            json.readTree(
                "{\"accountId\":\""
                    + id
                    + "\",\"conversationId\":\"channel1\",\"text\":\"approved\"}"));
    verify(http, never()).post(anyString(), anyString(), any());
    String action = json.valueToTree(result.get("result")).path("id").asText();
    mvc.perform(post("/api/v1/communications/actions/" + action + "/confirmation"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/communications/actions/" + action + "/confirmation")
                .with(csrf())
                .session(new MockHttpSession()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SENT"));
    mvc.perform(post("/api/v1/communications/actions/" + action + "/confirmation").with(csrf()))
        .andExpect(status().isConflict());
    verify(http, times(1)).post(anyString(), anyString(), any());
    assertThatThrownBy(() -> mcp.call("communication_confirm", json.createObjectNode()))
        .hasMessageContaining("지원하지");
  }

  @Test
  @WithMockUser(username = "dashboard-mcp", roles = "OWNER")
  void mcpCannotForgeBrowserApproval() throws Exception {
    String id = connect();
    var action = service.prepare(id, new SendRequest("channel1", "", "", "test", ""));
    assertThatThrownBy(() -> service.confirm(action.id(), "invented-session"))
        .hasMessageContaining("대시보드");
    verify(http, never()).post(anyString(), anyString(), any());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void uncertainSendIsNeverAutomaticallyRetried() throws Exception {
    String id = connect();
    var action = service.prepare(id, new SendRequest("channel1", "", "", "test", ""));
    when(http.post(anyString(), anyString(), any()))
        .thenThrow(new com.personal.dashboard.global.WorkspaceException(502, "fixture timeout"));
    assertThatThrownBy(() -> service.confirm(action.id(), "browser"))
        .hasMessageContaining("fixture timeout");
    assertThat(service.actions().getFirst().state()).isEqualTo("UNKNOWN");
    assertThatThrownBy(() -> service.confirm(action.id(), "browser")).hasMessageContaining("이미 처리");
    verify(http, times(1)).post(anyString(), anyString(), any());
  }

  @Test
  void oauthStateBelongsToInitiatingSession() throws Exception {
    var session = new MockHttpSession();
    String response =
        mvc.perform(
                post("/api/v1/communications/oauth")
                    .with(user("owner").roles("OWNER"))
                    .with(csrf())
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"provider\":\"GMAIL\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String target = json.readTree(response).path("url").asText();
    String state =
        java.net.URLDecoder.decode(
            target.split("state=")[1].split("&")[0], java.nio.charset.StandardCharsets.UTF_8);
    mvc.perform(
            get("/api/v1/communications/oauth/callback")
                .with(user("owner").roles("OWNER"))
                .session(new MockHttpSession())
                .param("state", state)
                .param("code", "fake"))
        .andExpect(status().isForbidden());
    verify(http, never()).formPost(anyString(), any());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void mutationNeedsApprovalAndCannotReplay() throws Exception {
    String id = connect();
    when(http.request(eq("DELETE"), contains("/messages/m1"), anyString(), anyString(), isNull()))
        .thenReturn(json.createObjectNode());
    var action =
        service.prepareMutation(
            id,
            new com.personal.dashboard.communication.dto.CommunicationDto.MutationRequest(
                "DELETE", "channel1", "m1", "", ""));
    verify(http, never()).request(anyString(), anyString(), anyString(), anyString(), any());
    assertThat(action.operation()).isEqualTo("DELETE");
    assertThat(action.mutation().messageId()).isEqualTo("m1");
    service.confirm(action.id(), "browser");
    verify(http, times(1))
        .request(eq("DELETE"), contains("/messages/m1"), anyString(), anyString(), isNull());
    assertThatThrownBy(() -> service.confirm(action.id(), "browser")).hasMessageContaining("이미 처리");
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void gmailAttachmentsAreValidatedEncryptedAndBoundToApproval() throws Exception {
    when(http.get(endsWith("/profile"), anyString()))
        .thenReturn(json.readTree("{\"emailAddress\":\"fixture@example.test\"}"));
    when(http.post(endsWith("/messages/send"), anyString(), any()))
        .thenReturn(json.readTree("{\"id\":\"gmail-sent\",\"threadId\":\"thread1\"}"));
    String id =
        service
            .connect(
                com.personal.dashboard.communication.domain.Communication.ProviderId.GMAIL,
                "fixture-google",
                "",
                0,
                Set.of("https://www.googleapis.com/auth/gmail.modify"))
            .id();
    var file =
        new com.personal.dashboard.communication.dto.CommunicationDto.Upload(
            "note.txt",
            "text/plain",
            Base64.getEncoder()
                .encodeToString(
                    "fixture file content".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    var action =
        service.prepare(
            id,
            new SendRequest("", "recipient@example.test", "subject", "body", "", List.of(file)));
    assertThat(action.message().attachments().getFirst().data()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT payload_cipher FROM communication_actions WHERE id=?",
                String.class,
                action.id()))
        .doesNotContain(file.data());
    verify(http, never()).post(endsWith("/messages/send"), anyString(), any());
    service.confirm(action.id(), "browser");
    var body = org.mockito.ArgumentCaptor.forClass(Object.class);
    verify(http).post(endsWith("/messages/send"), anyString(), body.capture());
    String raw =
        new String(
            Base64.getUrlDecoder().decode((String) ((Map<?, ?>) body.getValue()).get("raw")),
            java.nio.charset.StandardCharsets.UTF_8);
    assertThat(raw).contains("multipart/mixed", "filename*=UTF-8''note.txt", file.data());
    var invalid =
        new com.personal.dashboard.communication.dto.CommunicationDto.Upload(
            "../secret.txt", "text/plain", file.data());
    assertThatThrownBy(
            () ->
                service.prepare(
                    id,
                    new SendRequest(
                        "", "recipient@example.test", "subject", "body", "", List.of(invalid))))
        .hasMessageContaining("첨부파일");
    var wrongType =
        new com.personal.dashboard.communication.dto.CommunicationDto.Upload(
            "image.png", "image/png", file.data());
    assertThatThrownBy(
            () ->
                service.prepare(
                    id,
                    new SendRequest(
                        "", "recipient@example.test", "subject", "body", "", List.of(wrongType))))
        .hasMessageContaining("형식");
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void gmailLabelsUseGrantedScopeAndOriginalIds() throws Exception {
    when(http.get(endsWith("/profile"), anyString()))
        .thenReturn(json.readTree("{\"emailAddress\":\"labels@example.test\"}"));
    when(http.get(endsWith("/labels"), anyString()))
        .thenReturn(json.readTree("{\"labels\":[{\"id\":\"UNREAD\",\"name\":\"UNREAD\"}]}"));
    String id =
        service
            .connect(
                com.personal.dashboard.communication.domain.Communication.ProviderId.GMAIL,
                "fixture-google",
                "",
                0,
                Set.of("https://www.googleapis.com/auth/gmail.modify"))
            .id();
    service.updateLabels(
        id,
        new com.personal.dashboard.communication.dto.CommunicationDto.LabelRequest(
            "thread1", List.of(), List.of("UNREAD")));
    verify(http)
        .post(
            endsWith("/threads/thread1/modify"),
            anyString(),
            eq(Map.of("addLabelIds", List.of(), "removeLabelIds", List.of("UNREAD"))));
    assertThatThrownBy(
            () ->
                service.updateLabels(
                    id,
                    new com.personal.dashboard.communication.dto.CommunicationDto.LabelRequest(
                        "thread1", List.of("unknown"), List.of())))
        .hasMessageContaining("라벨");
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "<b>Hello</b><script>steal()</script>"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var message =
        GmailProvider.normalize(
            json.readTree(
                "{\"id\":\"g1\",\"payload\":{\"mimeType\":\"text/html\",\"body\":{\"data\":\""
                    + encoded
                    + "\"}}}"));
    assertThat(message.text()).isEqualTo("Hello");
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void slackEventsRequireFreshSignatureAndDeduplicate() throws Exception {
    when(http.get(endsWith("auth.test"), anyString()))
        .thenReturn(
            json.readTree(
                "{\"ok\":true,\"team_id\":\"T1\",\"user_id\":\"B1\",\"team\":\"Fixture\",\"user\":\"Bot\"}"));
    String accountId =
        service
            .connect(
                com.personal.dashboard.communication.domain.Communication.ProviderId.SLACK,
                "fixture-slack",
                "",
                0,
                Set.of("chat:write"))
            .id();
    String eventId = UUID.randomUUID().toString();
    String body =
        "{\"type\":\"event_callback\",\"event_id\":\""
            + eventId
            + "\",\"team_id\":\"T1\",\"event\":{\"type\":\"message\",\"channel\":\"C1\",\"ts\":\"1700000000.123456\",\"text\":\"external event\"}}";
    String timestamp = Long.toString(System.currentTimeMillis() / 1000);
    javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(
        new javax.crypto.spec.SecretKeySpec(
            "fixture-signing-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "HmacSHA256"));
    String signature =
        "v0="
            + HexFormat.of()
                .formatHex(
                    mac.doFinal(
                        ("v0:" + timestamp + ":" + body)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    mvc.perform(
            post("/api/v1/communications/events/slack")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/v1/communications/events/slack")
                  .header("X-Slack-Request-Timestamp", timestamp)
                  .header("X-Slack-Signature", signature)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM communication_messages WHERE account_id=?",
                Integer.class,
                accountId))
        .isEqualTo(1);
    mvc.perform(
            post("/api/v1/communications/events/slack")
                .header("X-Slack-Request-Timestamp", "1")
                .header("X-Slack-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/communications/events/slack")
                .header("X-Slack-Request-Timestamp", timestamp)
                .header("X-Slack-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body + " "))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void gmailSyncResumesAndResetsExpiredHistory() throws Exception {
    when(http.get(endsWith("/profile"), anyString()))
        .thenReturn(
            json.readTree("{\"emailAddress\":\"sync@example.test\",\"historyId\":\"100\"}"));
    when(http.get(contains("/messages?maxResults=10"), anyString()))
        .thenReturn(json.readTree("{\"messages\":[{\"id\":\"mail1\"}]}"));
    when(http.get(contains("/messages/mail1?format=full"), anyString()))
        .thenReturn(json.readTree("{\"id\":\"mail1\",\"threadId\":\"thread1\",\"payload\":{}}"));
    when(http.get(contains("/history?"), anyString()))
        .thenReturn(
            json.readTree(
                "{\"historyId\":\"101\",\"history\":[{\"messagesDeleted\":[{\"message\":{\"id\":\"mail1\"}}]}]}"));
    String id =
        service
            .connect(
                com.personal.dashboard.communication.domain.Communication.ProviderId.GMAIL,
                "fixture",
                "",
                0,
                Set.of("https://www.googleapis.com/auth/gmail.modify"))
            .id();
    assertThat(gmailSync.synchronize(id).hasMore()).isTrue();
    assertThat(service.cachedMessages(id, "thread1")).hasSize(1);
    assertThat(gmailSync.synchronize(id).hasMore()).isFalse();
    assertThat(service.cachedMessages(id, "thread1")).isEmpty();
    when(http.get(contains("/history?"), anyString()))
        .thenThrow(
            new com.personal.dashboard.global.WorkspaceException(404, "expired fixture history"));
    assertThat(gmailSync.synchronize(id).reset()).isTrue();
    assertThat(service.cachedMessages(id, "thread1")).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM communication_cursors WHERE account_id=? AND conversation_id='@gmail-sync'",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void slackFileUploadRequiresApprovalAndUsesOfficialCompletion() throws Exception {
    when(http.get(endsWith("auth.test"), anyString()))
        .thenReturn(
            json.readTree(
                "{\"ok\":true,\"team_id\":\"T1\",\"user_id\":\"B1\",\"team\":\"Fixture\",\"user\":\"Bot\"}"));
    when(http.request(
            eq("POST"), endsWith("files.getUploadURLExternal"), anyString(), anyString(), any()))
        .thenReturn(
            json.readTree(
                "{\"ok\":true,\"file_id\":\"F1\",\"upload_url\":\"https://files.slack.com/upload/v1/fixture\"}"));
    when(http.post(endsWith("files.completeUploadExternal"), anyString(), any()))
        .thenReturn(json.readTree("{\"ok\":true}"));
    when(http.get(contains("files.info?file=F1"), anyString()))
        .thenReturn(
            json.readTree(
                "{\"file\":{\"shares\":{\"private\":{\"C1\":[{\"ts\":\"1700000000.123456\"}]}}}}"));
    String id =
        service
            .connect(
                com.personal.dashboard.communication.domain.Communication.ProviderId.SLACK,
                "fixture-slack",
                "",
                0,
                Set.of("chat:write", "files:read", "files:write"))
            .id();
    var file =
        new com.personal.dashboard.communication.dto.CommunicationDto.Upload(
            "note.txt", "text/plain", "aGVsbG8=");
    var action =
        service.prepare(id, new SendRequest("C1", "", "", "file caption", "", List.of(file)));
    verify(http, never()).uploadSlack(anyString(), any());
    var sent = service.confirm(action.id(), "browser");
    assertThat(sent.resultId()).isEqualTo("1700000000.123456");
    verify(http)
        .uploadSlack(
            eq("https://files.slack.com/upload/v1/fixture"),
            eq("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    verify(http)
        .post(
            endsWith("files.completeUploadExternal"),
            anyString(),
            argThat(body -> ((Map<?, ?>) body).get("channel_id").equals("C1")));
  }

  @Test
  @WithMockUser(roles = "OWNER")
  void discordAttachmentUsesMultipartOnlyAfterApproval() throws Exception {
    String id = connect();
    when(http.multipart(contains("/channels/channel1/messages"), anyString(), anyMap(), anyList()))
        .thenReturn(json.readTree("{\"id\":\"multipart1\",\"content\":\"caption\"}"));
    var action =
        service.prepare(
            id,
            new SendRequest(
                "channel1",
                "",
                "",
                "caption",
                "",
                List.of(
                    new com.personal.dashboard.communication.dto.CommunicationDto.Upload(
                        "note.txt", "text/plain", "aGVsbG8="))));
    verify(http, never()).multipart(anyString(), anyString(), anyMap(), anyList());
    assertThat(service.confirm(action.id(), "browser").resultId()).isEqualTo("multipart1");
    verify(http)
        .multipart(
            anyString(),
            startsWith("Bot "),
            argThat(body -> body.containsKey("attachments")),
            anyList());
  }

  @Test
  void optionalBridgeAbsenceAndInvalidInputsDoNotBreakAccounts() throws Exception {
    mvc.perform(get("/api/v1/communications/bridge").with(user("owner").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(false));
    mvc.perform(
            post("/api/v1/communications/bridge/profiles")
                .with(user("owner").roles("OWNER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"SHELL\",\"label\":\"invalid\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/communications/windows/local/observations")
                .with(user("owner").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/communications/accounts").with(user("owner").roles("OWNER")))
        .andExpect(status().isOk());
  }

  @Test
  void normalizersPreserveProviderFactsAndUnknowns() throws Exception {
    var gmail =
        GmailProvider.normalize(
            json.readTree(
                "{\"id\":\"g1\",\"threadId\":\"t1\",\"labelIds\":[\"UNREAD\"],\"payload\":{\"mimeType\":\"text/plain\",\"body\":{\"data\":\"aGVsbG8\"}}}"));
    assertThat(gmail.text()).isEqualTo("hello");
    assertThat(gmail.unread()).isTrue();
    assertThat(gmail.timestamp()).isNull();
    var slack =
        SlackProvider.normalize(
            "c1", json.readTree("{\"ts\":\"1700000000.123456\",\"text\":\"hello\"}"));
    assertThat(slack.timestamp()).isEqualTo(1700000000123L);
    assertThat(slack.unread()).isNull();
    var discord =
        DiscordProvider.normalize("c2", json.readTree("{\"id\":\"d1\",\"content\":\"\"}"));
    assertThat(discord.text()).isEmpty();
    assertThat(discord.timestamp()).isNull();
  }
}
