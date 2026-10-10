package com.personal.dashboard.communication.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.adapter.DiscordProvider;
import com.personal.dashboard.communication.adapter.SlackProvider;
import com.personal.dashboard.communication.domain.Communication.Message;
import com.personal.dashboard.communication.dto.CommunicationDto;
import com.personal.dashboard.communication.entity.CommunicationRecords.AccountRecord;
import com.personal.dashboard.communication.repository.CommunicationRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.realtime.service.WorkspaceEvents;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signed Slack ingestion and trusted bot gateway callbacks. No provider event can authorize a
 * write.
 */
@Service
public class CommunicationEventService {
  public record Acknowledgment(String challenge, boolean accepted) {}

  private final CommunicationRepository repository;
  private final ObjectMapper json;
  private final WorkspaceEvents events;
  private final String signingSecret;

  public CommunicationEventService(
      CommunicationRepository repository,
      ObjectMapper json,
      WorkspaceEvents events,
      @Value("${COMMUNICATION_SLACK_SIGNING_SECRET:}") String signingSecret) {
    this.repository = repository;
    this.json = json;
    this.events = events;
    this.signingSecret = signingSecret;
  }

  @Transactional
  public Acknowledgment slack(String timestamp, String signature, byte[] body) {
    verify(timestamp, signature, body);
    JsonNode input;
    try {
      input = json.readTree(body);
    } catch (Exception exception) {
      throw new WorkspaceException(400, "이벤트 형식을 확인해 주세요.");
    }
    if (input == null) throw new WorkspaceException(400, "이벤트가 비어 있습니다.");
    if ("url_verification".equals(input.path("type").asText()))
      return new Acknowledgment(input.path("challenge").asText(), true);
    String eventId = input.path("event_id").asText();
    if (eventId.isBlank() || eventId.length() > 200)
      throw new WorkspaceException(400, "이벤트 ID를 확인해 주세요.");
    if (!repository.receiveEvent("SLACK:" + eventId)) return new Acknowledgment("", true);
    String team = input.path("team_id").asText();
    var event = input.path("event");
    String channel = event.path("channel").asText();
    if ("message".equals(event.path("type").asText()) && !channel.isBlank())
      for (var account : repository.accounts())
        if (account.provider().equals("SLACK") && account.externalId().startsWith(team + ":")) {
          if ("message_deleted".equals(event.path("subtype").asText()))
            repository.removeMessage(account.id(), channel, event.path("deleted_ts").asText());
          else
            cache(
                account,
                SlackProvider.normalize(
                    channel, event.has("message") ? event.path("message") : event));
        }
    events.changed("communications");
    return new Acknowledgment("", true);
  }

  @Transactional
  public void discord(String accountId, String type, JsonNode data) {
    var account =
        repository.accounts().stream()
            .filter(item -> item.id().equals(accountId) && item.provider().equals("DISCORD"))
            .findFirst()
            .orElse(null);
    if (account == null) return;
    String channel = data.path("channel_id").asText();
    if (channel.isBlank()) return;
    if ("MESSAGE_DELETE".equals(type))
      repository.removeMessage(accountId, channel, data.path("id").asText());
    else if ("MESSAGE_CREATE".equals(type))
      cache(account, DiscordProvider.normalize(channel, data));
    // Partial UPDATE events invalidate REST; absent content is never used to erase a cached
    // message.
    events.changed("communications");
  }

  private void cache(AccountRecord account, Message item) {
    if (item.id().isBlank()) return;
    var view =
        new CommunicationDto.MessageView(
            account.id(),
            account.provider(),
            item.id(),
            item.conversationId(),
            item.sender(),
            item.text(),
            item.timestamp(),
            item.threadId(),
            item.unread(),
            item.attachments().stream()
                .map(
                    file ->
                        new CommunicationDto.AttachmentView(
                            file.id(), file.name(), file.mediaType(), file.size()))
                .toList(),
            item.reactions().stream()
                .map(
                    reaction ->
                        new CommunicationDto.ReactionView(
                            reaction.key(), reaction.label(), reaction.count()))
                .toList());
    try {
      repository.cache(
          account.id(),
          item.conversationId(),
          item.id(),
          item.timestamp(),
          json.writeValueAsString(view));
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new WorkspaceException(500, "이벤트 저장에 실패했습니다.");
    }
  }

  private void verify(String timestamp, String signature, byte[] body) {
    if (body.length > 1048576) throw new WorkspaceException(413, "이벤트 크기 한도를 초과했습니다.");
    try {
      if (signingSecret.isBlank()
          || timestamp == null
          || signature == null
          || !timestamp.matches("[0-9]{1,12}")
          || Math.abs(System.currentTimeMillis() / 1000 - Long.parseLong(timestamp)) > 300)
        throw new IllegalArgumentException();
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update(("v0:" + timestamp + ":").getBytes(StandardCharsets.UTF_8));
      String expected = "v0=" + HexFormat.of().formatHex(mac.doFinal(body));
      if (!MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII),
          signature.getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
    } catch (Exception exception) {
      throw new WorkspaceException(403, "이벤트 서명을 확인할 수 없습니다.");
    }
  }
}
