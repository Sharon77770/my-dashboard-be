package com.personal.dashboard.communication.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.dashboard.communication.domain.Communication.*;
import com.personal.dashboard.global.WorkspaceException;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Official bot-only REST bridge. User tokens, self-bots and private client protocols are rejected.
 */
@Component
public class DiscordProvider implements CommunicationProvider {
  private final ProviderHttpClient http;

  public DiscordProvider(ProviderHttpClient http) {
    this.http = http;
  }

  public ProviderId id() {
    return ProviderId.DISCORD;
  }

  private JsonNode get(String token, String path) {
    return http.get("https://discord.com/api/v10" + path, "Bot " + token);
  }

  public Identity connect(String token) {
    var user = get(token, "/users/@me");
    if (!user.path("bot").asBoolean()) throw new WorkspaceException(400, "Discord Bot 계정만 지원합니다.");
    return new Identity(
        user.path("id").asText(),
        user.path("username").asText(),
        Set.of(
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
    Map<String, Conversation> items = new LinkedHashMap<>();
    var guilds =
        get(token, "/users/@me/guilds?limit=10&after=" + ProviderHttpClient.encode(cursor));
    String next = "";
    for (var guild : guilds) {
      next = guild.path("id").asText();
      String guildPath = "/guilds/" + ProviderHttpClient.encode(next);
      Map<String, String> parents = new HashMap<>();
      for (var channel : get(token, guildPath + "/channels")) {
        parents.put(channel.path("id").asText(), channel.path("name").asText());
        int type = channel.path("type").asInt(-1);
        if (type == 0 || type == 5)
          addConversation(items, guild.path("name").asText(), channel, "CHANNEL", query);
      }
      // Forum posts are threads, not message-bearing forum containers. Use only the
      // official active-thread response; never synthesize private or archived threads.
      for (var thread : get(token, guildPath + "/threads/active").path("threads")) {
        int type = thread.path("type").asInt(-1);
        if (type != 10 && type != 11 && type != 12) continue;
        String parent = parents.get(thread.path("parent_id").asText());
        String prefix = guild.path("name").asText() + (parent == null ? "" : " / " + parent);
        addConversation(items, prefix, thread, "THREAD", query);
      }
    }
    return new Page<>(new ArrayList<>(items.values()), guilds.size() == 10 ? next : "");
  }

  private void addConversation(
      Map<String, Conversation> items, String prefix, JsonNode channel, String kind, String query) {
    String id = channel.path("id").asText();
    String title = prefix + " / " + channel.path("name").asText();
    if (id.isBlank()
        || (!query.isBlank()
            && !title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))) return;
    items.put(id, new Conversation(id, title, kind, "", null, null));
  }

  public Page<Message> fetchMessages(String token, String conversationId, String cursor) {
    var response =
        get(
            token,
            "/channels/"
                + ProviderHttpClient.encode(conversationId)
                + "/messages?limit=50"
                + (cursor.isBlank() ? "" : "&before=" + ProviderHttpClient.encode(cursor)));
    List<Message> items = new ArrayList<>();
    for (var message : response) items.add(normalize(conversationId, message));
    return new Page<>(items, items.size() == 50 ? items.getLast().id() : "");
  }

  public static Message normalize(String conversationId, JsonNode item) {
    List<Attachment> attachments = new ArrayList<>();
    for (var file : item.path("attachments"))
      attachments.add(
          new Attachment(
              file.path("id").asText(),
              file.path("filename").asText(),
              file.path("content_type").asText("application/octet-stream"),
              file.path("size").asLong()));
    Long timestamp = null;
    try {
      timestamp = java.time.Instant.parse(item.path("timestamp").asText()).toEpochMilli();
    } catch (java.time.format.DateTimeParseException ignored) {
    }
    List<Reaction> reactions = new ArrayList<>();
    for (var reaction : item.path("reactions")) {
      String id = reaction.path("emoji").path("id").asText("");
      String name = reaction.path("emoji").path("name").asText("");
      if (id.isBlank() && name.isBlank()) continue;
      var count = reaction.path("count");
      reactions.add(
          new Reaction(
              id.isBlank() ? name : id,
              name.isBlank() ? id : name,
              count.isIntegralNumber() && count.canConvertToLong() && count.asLong() >= 0
                  ? count.asLong()
                  : null));
    }
    return new Message(
        item.path("id").asText(),
        conversationId,
        item.path("author").path("username").asText(),
        item.path("content").asText(),
        timestamp,
        item.path("message_reference").path("message_id").asText(),
        null,
        attachments,
        reactions);
  }

  public byte[] downloadAttachment(
      String token, String conversationId, String messageId, String attachmentId) {
    var message =
        get(
            token,
            "/channels/"
                + ProviderHttpClient.encode(conversationId)
                + "/messages/"
                + ProviderHttpClient.encode(messageId));
    for (var file : message.path("attachments"))
      if (file.path("id").asText().equals(attachmentId))
        return http.download(file.path("url").asText(), "", "cdn.discordapp.com");
    throw new WorkspaceException(404, "첨부파일을 찾을 수 없습니다.");
  }

  public Message mutate(String token, Mutation mutation) {
    String url =
        "https://discord.com/api/v10/channels/"
            + ProviderHttpClient.encode(mutation.conversationId())
            + "/messages/"
            + ProviderHttpClient.encode(mutation.messageId());
    if (mutation.kind() == ActionKind.EDIT) {
      if (mutation.text().length() > 2000)
        throw new com.personal.dashboard.global.WorkspaceException(
            400, "Discord 메시지는 2,000자 이하여야 합니다.");
      return normalize(
          mutation.conversationId(),
          http.request(
              "PATCH",
              url,
              "Bot " + token,
              "application/json",
              Map.of("content", mutation.text(), "allowed_mentions", Map.of("parse", List.of()))));
    }
    if (mutation.kind() == ActionKind.DELETE) http.request("DELETE", url, "Bot " + token, "", null);
    else if (mutation.kind() == ActionKind.REACTION)
      http.request(
          "PUT",
          url
              + "/reactions/"
              + ProviderHttpClient.encode(mutation.reaction()).replace("+", "%20")
              + "/@me",
          "Bot " + token,
          "",
          null);
    else throw new IllegalArgumentException();
    return new Message(
        mutation.messageId(), mutation.conversationId(), "", "", null, "", null, List.of());
  }

  public Message sendMessage(String token, Send message) {
    if (message.text().length() > 2000)
      throw new WorkspaceException(400, "Discord 메시지는 2,000자 이하여야 합니다.");
    Map<String, Object> body = new HashMap<>();
    body.put("content", message.text());
    body.put("allowed_mentions", Map.of("parse", List.of()));
    if (!message.replyTo().isBlank())
      body.put(
          "message_reference", Map.of("message_id", message.replyTo(), "fail_if_not_exists", true));
    if (!message.attachments().isEmpty()) {
      List<Map<String, Object>> files = new ArrayList<>();
      for (int index = 0; index < message.attachments().size(); index++)
        files.add(Map.of("id", index, "filename", message.attachments().get(index).name()));
      body.put("attachments", files);
      return normalize(
          message.conversationId(),
          http.multipart(
              "https://discord.com/api/v10/channels/"
                  + ProviderHttpClient.encode(message.conversationId())
                  + "/messages",
              "Bot " + token,
              body,
              message.attachments()));
    }
    return normalize(
        message.conversationId(),
        http.post(
            "https://discord.com/api/v10/channels/"
                + ProviderHttpClient.encode(message.conversationId())
                + "/messages",
            "Bot " + token,
            body));
  }
}
