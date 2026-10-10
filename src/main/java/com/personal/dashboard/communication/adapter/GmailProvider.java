package com.personal.dashboard.communication.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.dashboard.communication.domain.Communication.*;
import com.personal.dashboard.global.WorkspaceException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

/** Gmail threads and plain-text MIME messages through the documented Gmail v1 API. */
@Component
public class GmailProvider implements CommunicationProvider {
  private static final String ROOT = "https://gmail.googleapis.com/gmail/v1/users/me";
  private final ProviderHttpClient http;

  public GmailProvider(ProviderHttpClient http) {
    this.http = http;
  }

  public ProviderId id() {
    return ProviderId.GMAIL;
  }

  private JsonNode get(String token, String path) {
    return http.get(ROOT + path, "Bearer " + token);
  }

  public Identity connect(String token) {
    var profile = get(token, "/profile");
    String email = profile.path("emailAddress").asText();
    if (email.isBlank()) throw new WorkspaceException(502, "Gmail 계정 정보를 확인하지 못했습니다.");
    return new Identity(
        email,
        email,
        Set.of(
            Capability.READ,
            Capability.SEND,
            Capability.REPLY,
            Capability.THREADS,
            Capability.SEARCH,
            Capability.UPLOAD,
            Capability.ATTACHMENTS,
            Capability.READ_STATE,
            Capability.LABELS));
  }

  public Page<Conversation> listConversations(String token, String cursor, String query) {
    var response =
        get(
            token,
            "/threads?maxResults=25&pageToken="
                + ProviderHttpClient.encode(cursor)
                + "&q="
                + ProviderHttpClient.encode(query));
    List<Conversation> conversations = new ArrayList<>();
    for (var item : response.path("threads"))
      conversations.add(
          new Conversation(
              item.path("id").asText(),
              item.path("snippet").asText("메일 대화"),
              "MAIL",
              item.path("snippet").asText(),
              null,
              null));
    return new Page<>(conversations, response.path("nextPageToken").asText(""));
  }

  public Page<Message> fetchMessages(String token, String conversationId, String cursor) {
    var response =
        get(token, "/threads/" + ProviderHttpClient.encode(conversationId) + "?format=full");
    List<Message> messages = new ArrayList<>();
    for (var item : response.path("messages")) messages.add(normalize(item));
    return new Page<>(messages, "");
  }

  @Override
  public Page<Message> searchMessages(String token, String query, String cursor) {
    var response =
        get(
            token,
            "/messages?maxResults=10&pageToken="
                + ProviderHttpClient.encode(cursor)
                + "&q="
                + ProviderHttpClient.encode(query));
    Map<String, Message> messages = new LinkedHashMap<>();
    for (var item : response.path("messages")) {
      String id = item.path("id").asText();
      if (id.isBlank()) throw new WorkspaceException(502, "Gmail 검색 응답의 메시지 ID를 확인할 수 없습니다.");
      if (messages.containsKey(id)) continue;
      var message =
          normalize(get(token, "/messages/" + ProviderHttpClient.encode(id) + "?format=full"));
      if (!id.equals(message.id()) || message.conversationId().isBlank())
        throw new WorkspaceException(502, "Gmail 검색 메시지의 원본 ID를 확인할 수 없습니다.");
      messages.put(id, message);
    }
    return new Page<>(
        new ArrayList<>(messages.values()), response.path("nextPageToken").asText(""));
  }

  public static Message normalize(JsonNode item) {
    var payload = item.path("payload");
    List<Attachment> attachments = new ArrayList<>();
    attachments(payload, attachments);
    boolean unread = false;
    for (var label : item.path("labelIds")) if ("UNREAD".equals(label.asText())) unread = true;
    return new Message(
        item.path("id").asText(),
        item.path("threadId").asText(),
        header(payload, "From"),
        text(payload),
        item.has("internalDate") ? item.path("internalDate").asLong() : null,
        item.path("threadId").asText(),
        unread,
        attachments);
  }

  private static String header(JsonNode payload, String name) {
    for (var h : payload.path("headers"))
      if (name.equalsIgnoreCase(h.path("name").asText())) return h.path("value").asText();
    return "";
  }

  private static String text(JsonNode part) {
    String mime = part.path("mimeType").asText();
    if ("text/plain".equals(mime) || "text/html".equals(mime)) {
      try {
        String decoded =
            new String(
                Base64.getUrlDecoder().decode(part.path("body").path("data").asText()),
                StandardCharsets.UTF_8);
        return "text/html".equals(mime) ? org.jsoup.Jsoup.parse(decoded).text() : decoded;
      } catch (IllegalArgumentException ignored) {
        return "";
      }
    }
    // Prefer plain text in multipart/alternative so the same mail is not shown twice.
    if ("multipart/alternative".equals(mime))
      for (var child : part.path("parts"))
        if ("text/plain".equals(child.path("mimeType").asText())) return text(child);
    StringJoiner result = new StringJoiner("\n");
    for (var child : part.path("parts"))
      if (child.path("filename").asText().isBlank()) {
        String value = text(child);
        if (!value.isBlank()) result.add(value);
      }
    return result.toString();
  }

  public List<Label> labels(String token) {
    List<Label> labels = new ArrayList<>();
    for (var label : get(token, "/labels").path("labels"))
      labels.add(new Label(label.path("id").asText(), label.path("name").asText()));
    return labels;
  }

  public void updateLabels(
      String token, String conversationId, List<String> add, List<String> remove) {
    http.post(
        ROOT + "/threads/" + ProviderHttpClient.encode(conversationId) + "/modify",
        "Bearer " + token,
        Map.of("addLabelIds", add, "removeLabelIds", remove));
  }

  private static void attachments(JsonNode part, List<Attachment> result) {
    if (!part.path("filename").asText().isBlank() && part.path("body").has("attachmentId"))
      result.add(
          new Attachment(
              part.path("body").path("attachmentId").asText(),
              part.path("filename").asText(),
              part.path("mimeType").asText(),
              part.path("body").path("size").asLong()));
    for (var child : part.path("parts")) attachments(child, result);
  }

  @Override
  public Send prepareSend(String token, Send message) {
    if (message.replyTo().isBlank()) return message;
    var payload = replyPayload(token, message);
    String recipient = header(payload, "Reply-To");
    if (recipient.isBlank()) recipient = header(payload, "From");
    String subject = header(payload, "Subject");
    checkHeader(recipient);
    checkHeader(subject);
    if (recipient.isBlank()) throw new WorkspaceException(400, "답장 수신자를 확인할 수 없습니다.");
    return new Send(
        message.conversationId(),
        recipient,
        subject,
        message.text(),
        message.replyTo(),
        message.attachments());
  }

  private JsonNode replyPayload(String token, Send message) {
    var original =
        get(
            token,
            "/messages/" + ProviderHttpClient.encode(message.replyTo()) + "?format=metadata");
    if (!original.path("threadId").asText().equals(message.conversationId()))
      throw new WorkspaceException(400, "답장 원본 대화가 일치하지 않습니다.");
    return original.path("payload");
  }

  public Message sendMessage(String token, Send message) {
    String recipient = message.recipient(), subject = message.subject(), replyHeaders = "";
    if (!message.replyTo().isBlank()) {
      var payload = replyPayload(token, message);
      recipient = header(payload, "Reply-To");
      if (recipient.isBlank()) recipient = header(payload, "From");
      subject = header(payload, "Subject");
      if (!recipient.equals(message.recipient()) || !subject.equals(message.subject()))
        throw new WorkspaceException(409, "승인한 답장 대상 또는 제목이 변경되었습니다. 내용을 다시 확인해 주세요.");
      String reference = header(payload, "Message-ID");
      checkHeader(reference);
      if (!reference.isBlank())
        replyHeaders = "In-Reply-To: " + reference + "\r\nReferences: " + reference + "\r\n";
    }
    checkHeader(recipient);
    checkHeader(subject);
    if (recipient.isBlank()) throw new WorkspaceException(400, "메일 수신자를 입력해 주세요.");
    String raw =
        "To: "
            + recipient
            + "\r\nSubject: =?UTF-8?B?"
            + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8))
            + "?=\r\n"
            + replyHeaders
            + "MIME-Version: 1.0\r\n"
            + mime(message);
    Map<String, String> body = new HashMap<>();
    body.put(
        "raw",
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
    if (!message.replyTo().isBlank()) body.put("threadId", message.conversationId());
    var sent = http.post(ROOT + "/messages/send", "Bearer " + token, body);
    return new Message(
        sent.path("id").asText(),
        sent.path("threadId").asText(),
        "",
        message.text(),
        null,
        sent.path("threadId").asText(),
        false,
        List.of());
  }

  private static String mime(Send message) {
    String text =
        "Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
            + Base64.getMimeEncoder()
                .encodeToString(message.text().getBytes(StandardCharsets.UTF_8))
            + "\r\n";
    if (message.attachments().isEmpty()) return text;
    String boundary = "workspace_" + UUID.randomUUID().toString().replace("-", "");
    StringBuilder body =
        new StringBuilder(
            "Content-Type: multipart/mixed; boundary=\""
                + boundary
                + "\"\r\n\r\n--"
                + boundary
                + "\r\n"
                + text);
    for (var file : message.attachments())
      body.append("--")
          .append(boundary)
          .append("\r\nContent-Type: ")
          .append(file.mediaType())
          .append("\r\nContent-Disposition: attachment; filename*=UTF-8''")
          .append(ProviderHttpClient.encode(file.name()).replace("+", "%20"))
          .append("\r\nContent-Transfer-Encoding: base64\r\n\r\n")
          .append(Base64.getMimeEncoder().encodeToString(file.data()))
          .append("\r\n");
    return body.append("--").append(boundary).append("--\r\n").toString();
  }

  private static void checkHeader(String value) {
    if (value.contains("\r") || value.contains("\n"))
      throw new WorkspaceException(400, "메일 헤더에 줄바꿈을 사용할 수 없습니다.");
  }

  public byte[] downloadAttachment(
      String token, String conversationId, String messageId, String attachmentId) {
    var result =
        get(
            token,
            "/messages/"
                + ProviderHttpClient.encode(messageId)
                + "/attachments/"
                + ProviderHttpClient.encode(attachmentId));
    try {
      return Base64.getUrlDecoder().decode(result.path("data").asText());
    } catch (IllegalArgumentException exception) {
      throw new WorkspaceException(502, "첨부파일을 읽지 못했습니다.");
    }
  }
}
