package com.personal.dashboard.communication.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.dashboard.communication.domain.Communication.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Slack bot OAuth scopes constrain channel membership and message operations. */
@Component
public class SlackProvider implements CommunicationProvider {
  private final ProviderHttpClient http;

  public SlackProvider(ProviderHttpClient http) {
    this.http = http;
  }

  public ProviderId id() {
    return ProviderId.SLACK;
  }

  private JsonNode get(String token, String method) {
    return http.get("https://slack.com/api/" + method, "Bearer " + token);
  }

  public Identity connect(String token) {
    var identity = get(token, "auth.test");
    return new Identity(
        identity.path("team_id").asText() + ":" + identity.path("user_id").asText(),
        identity.path("team").asText() + " · " + identity.path("user").asText(),
        Set.of(
            Capability.THREADS,
            Capability.PARTICIPANTS,
            Capability.READ,
            Capability.SEND,
            Capability.REPLY,
            Capability.EDIT,
            Capability.DELETE,
            Capability.REACTIONS,
            Capability.ATTACHMENTS,
            Capability.UPLOAD));
  }

  public Page<Conversation> listConversations(String token, String cursor, String query) {
    var response =
        get(
            token,
            "conversations.list?exclude_archived=true&limit=100&types=public_channel,private_channel,im,mpim&cursor="
                + ProviderHttpClient.encode(cursor));
    List<Conversation> items = new ArrayList<>();
    for (var channel : response.path("channels")) {
      String title =
          channel.path("name").asText(channel.path("user").asText(channel.path("id").asText()));
      if (!query.isBlank()
          && !title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
      items.add(
          new Conversation(
              channel.path("id").asText(),
              title,
              channel.path("is_im").asBoolean()
                  ? "DM"
                  : channel.path("is_mpim").asBoolean() ? "GROUP" : "CHANNEL",
              "",
              null,
              null));
    }
    return new Page<>(items, response.path("response_metadata").path("next_cursor").asText(""));
  }

  @Override
  public Page<Participant> participants(String token, String conversationId, String cursor) {
    var response =
        get(
            token,
            "conversations.members?limit=100&channel="
                + ProviderHttpClient.encode(conversationId)
                + "&cursor="
                + ProviderHttpClient.encode(cursor));
    Map<String, Participant> members = new LinkedHashMap<>();
    for (var member : response.path("members")) {
      if (!member.isTextual() || member.asText().isBlank()) continue;
      members.put(member.asText(), new Participant(member.asText(), member.asText()));
    }
    return new Page<>(
        new ArrayList<>(members.values()),
        response.path("response_metadata").path("next_cursor").asText(""));
  }

  public Page<Message> fetchMessages(String token, String conversationId, String cursor) {
    var response =
        get(
            token,
            "conversations.history?limit=15&channel="
                + ProviderHttpClient.encode(conversationId)
                + "&cursor="
                + ProviderHttpClient.encode(cursor));
    List<Message> items = new ArrayList<>();
    for (var message : response.path("messages")) items.add(normalize(conversationId, message));
    return new Page<>(items, response.path("response_metadata").path("next_cursor").asText(""));
  }

  public Page<Message> fetchThread(
      String token, String conversationId, String threadId, String cursor) {
    var channel =
        get(token, "conversations.info?channel=" + ProviderHttpClient.encode(conversationId))
            .path("channel");
    if (!channel.path("is_im").asBoolean() && !channel.path("is_mpim").asBoolean())
      throw new com.personal.dashboard.global.WorkspaceException(
          409, "Slack Bot의 스레드 읽기는 DM/그룹 DM만 지원합니다. 채널 스레드는 원본 앱에서 확인해 주세요.");
    var response =
        get(
            token,
            "conversations.replies?limit=15&channel="
                + ProviderHttpClient.encode(conversationId)
                + "&ts="
                + ProviderHttpClient.encode(threadId)
                + "&cursor="
                + ProviderHttpClient.encode(cursor));
    List<Message> messages = new ArrayList<>();
    for (var message : response.path("messages")) messages.add(normalize(conversationId, message));
    return new Page<>(messages, response.path("response_metadata").path("next_cursor").asText(""));
  }

  public static Message normalize(String conversationId, JsonNode message) {
    List<Attachment> attachments = new ArrayList<>();
    for (var file : message.path("files"))
      attachments.add(
          new Attachment(
              file.path("id").asText(),
              file.path("name").asText(),
              file.path("mimetype").asText(),
              file.path("size").asLong()));
    Long timestamp = null;
    try {
      timestamp =
          new java.math.BigDecimal(message.path("ts").asText())
              .multiply(java.math.BigDecimal.valueOf(1000))
              .longValueExact();
    } catch (ArithmeticException | NumberFormatException ignored) {
      try {
        timestamp =
            new java.math.BigDecimal(message.path("ts").asText()).movePointRight(3).longValue();
      } catch (NumberFormatException ignoredAgain) {
      }
    }
    List<Reaction> reactions = new ArrayList<>();
    for (var reaction : message.path("reactions")) {
      String name = reaction.path("name").asText();
      if (name.isBlank()) continue;
      var count = reaction.path("count");
      reactions.add(
          new Reaction(
              name,
              name,
              count.isIntegralNumber() && count.canConvertToLong() && count.asLong() >= 0
                  ? count.asLong()
                  : null));
    }
    return new Message(
        message.path("ts").asText(),
        conversationId,
        message.path("user").asText(message.path("bot_id").asText()),
        message.path("text").asText(),
        timestamp,
        message.path("thread_ts").asText(),
        null,
        attachments,
        reactions);
  }

  public byte[] downloadAttachment(
      String token, String conversationId, String messageId, String attachmentId) {
    var file =
        get(token, "files.info?file=" + ProviderHttpClient.encode(attachmentId)).path("file");
    return http.download(
        file.path("url_private_download").asText(file.path("url_private").asText()),
        "Bearer " + token,
        "files.slack.com");
  }

  public Message mutate(String token, Mutation mutation) {
    String method =
        switch (mutation.kind()) {
          case EDIT -> "chat.update";
          case DELETE -> "chat.delete";
          case REACTION -> "reactions.add";
          default -> throw new IllegalArgumentException();
        };
    Map<String, Object> body = new HashMap<>();
    body.put("channel", mutation.conversationId());
    body.put(mutation.kind() == ActionKind.REACTION ? "timestamp" : "ts", mutation.messageId());
    if (mutation.kind() == ActionKind.EDIT) body.put("text", mutation.text());
    if (mutation.kind() == ActionKind.REACTION) body.put("name", mutation.reaction());
    var result = http.post("https://slack.com/api/" + method, "Bearer " + token, body);
    if (mutation.kind() == ActionKind.EDIT)
      return normalize(mutation.conversationId(), result.path("message"));
    return new Message(
        mutation.messageId(), mutation.conversationId(), "", "", null, "", null, List.of());
  }

  private Message sendFiles(String token, Send message) {
    List<Map<String, String>> uploaded = new ArrayList<>();
    List<Attachment> attachments = new ArrayList<>();
    for (var file : message.attachments()) {
      var grant =
          http.request(
              "POST",
              "https://slack.com/api/files.getUploadURLExternal",
              "Bearer " + token,
              "application/x-www-form-urlencoded",
              ProviderHttpClient.form(
                  Map.of("filename", file.name(), "length", Integer.toString(file.data().length))));
      String id = grant.path("file_id").asText();
      if (id.isBlank())
        throw new com.personal.dashboard.global.WorkspaceException(502, "Slack 파일 ID를 확인하지 못했습니다.");
      http.uploadSlack(grant.path("upload_url").asText(), file.data());
      uploaded.add(Map.of("id", id, "title", file.name()));
      attachments.add(new Attachment(id, file.name(), file.mediaType(), file.data().length));
    }
    Map<String, Object> completion = new HashMap<>();
    completion.put("files", uploaded);
    completion.put("channel_id", message.conversationId());
    completion.put("initial_comment", message.text());
    if (!message.replyTo().isBlank()) completion.put("thread_ts", message.replyTo());
    http.post("https://slack.com/api/files.completeUploadExternal", "Bearer " + token, completion);
    var info =
        get(token, "files.info?file=" + ProviderHttpClient.encode(uploaded.getFirst().get("id")))
            .path("file");
    String timestamp = "";
    for (String visibility : List.of("public", "private"))
      for (var share : info.path("shares").path(visibility).path(message.conversationId())) {
        timestamp = share.path("ts").asText();
        if (!timestamp.isBlank()) break;
      }
    // A successful upload without a reported message identity is uncertain; never invent an ID or
    // resend.
    if (timestamp.isBlank())
      throw new com.personal.dashboard.global.WorkspaceException(
          502, "Slack 파일은 업로드되었지만 메시지 ID를 확인하지 못했습니다. 원본 앱을 확인하고 중복 전송하지 마세요.");
    Long time = null;
    try {
      time = new java.math.BigDecimal(timestamp).movePointRight(3).longValue();
    } catch (NumberFormatException ignored) {
    }
    return new Message(
        timestamp,
        message.conversationId(),
        "",
        message.text(),
        time,
        message.replyTo(),
        null,
        attachments);
  }

  public Message sendMessage(String token, Send message) {
    if (!message.attachments().isEmpty()) return sendFiles(token, message);
    Map<String, Object> body = new HashMap<>();
    body.put("channel", message.conversationId());
    body.put("text", message.text());
    body.put("unfurl_links", false);
    body.put("unfurl_media", false);
    if (!message.replyTo().isBlank()) body.put("thread_ts", message.replyTo());
    var sent = http.post("https://slack.com/api/chat.postMessage", "Bearer " + token, body);
    return normalize(sent.path("channel").asText(), sent.path("message"));
  }
}
