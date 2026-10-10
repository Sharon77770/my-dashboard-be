package com.personal.dashboard.communication.adapter;

import com.personal.dashboard.communication.domain.Communication.Message;
import com.personal.dashboard.global.WorkspaceException;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Restartable bounded full scan then Gmail history pages. Expired history starts a new baseline.
 */
@Component
public class GmailSyncAdapter {
  public record Cursor(String phase, String checkpoint, String pageToken) {}

  public record Batch(
      List<Message> messages,
      List<String> deleted,
      Cursor cursor,
      boolean hasMore,
      boolean reset) {}

  private static final String ROOT = "https://gmail.googleapis.com/gmail/v1/users/me";
  private final ProviderHttpClient http;

  public GmailSyncAdapter(ProviderHttpClient http) {
    this.http = http;
  }

  private com.fasterxml.jackson.databind.JsonNode get(String token, String path) {
    return http.get(ROOT + path, "Bearer " + token);
  }

  public Batch fetch(String token, Cursor cursor) {
    if (cursor == null) {
      String history = get(token, "/profile").path("historyId").asText();
      if (history.isBlank()) throw new WorkspaceException(502, "Gmail 동기화 기준을 확인하지 못했습니다.");
      cursor = new Cursor("FULL", history, "");
    }
    if (cursor.phase().equals("FULL")) return full(token, cursor, false);
    try {
      var response =
          get(
              token,
              "/history?maxResults=10&startHistoryId="
                  + ProviderHttpClient.encode(cursor.checkpoint())
                  + "&pageToken="
                  + ProviderHttpClient.encode(cursor.pageToken()));
      Set<String> changed = new LinkedHashSet<>(), deleted = new HashSet<>();
      for (var history : response.path("history")) {
        for (var message : history.path("messages")) changed.add(message.path("id").asText());
        for (var item : history.path("messagesDeleted"))
          deleted.add(item.path("message").path("id").asText());
      }
      changed.removeAll(deleted);
      if (changed.size() > 100)
        throw new WorkspaceException(413, "Gmail 변경 페이지가 너무 큽니다. 원본 서비스 상태를 확인해 주세요.");
      List<Message> messages = read(token, changed, deleted);
      String next = response.path("nextPageToken").asText();
      String checkpoint =
          next.isBlank()
              ? response.path("historyId").asText(cursor.checkpoint())
              : cursor.checkpoint();
      return new Batch(
          messages,
          List.copyOf(deleted),
          new Cursor("HISTORY", checkpoint, next),
          !next.isBlank(),
          false);
    } catch (WorkspaceException exception) {
      if (exception.status() != 404) throw exception;
      String baseline = get(token, "/profile").path("historyId").asText();
      return full(token, new Cursor("FULL", baseline, ""), true);
    }
  }

  private Batch full(String token, Cursor cursor, boolean reset) {
    var response =
        get(
            token,
            "/messages?maxResults=10&includeSpamTrash=true&pageToken="
                + ProviderHttpClient.encode(cursor.pageToken()));
    Set<String> ids = new LinkedHashSet<>(), deleted = new HashSet<>();
    for (var item : response.path("messages")) ids.add(item.path("id").asText());
    List<Message> messages = read(token, ids, deleted);
    String next = response.path("nextPageToken").asText();
    return new Batch(
        messages,
        List.copyOf(deleted),
        new Cursor(next.isBlank() ? "HISTORY" : "FULL", cursor.checkpoint(), next),
        true,
        reset);
  }

  private List<Message> read(String token, Set<String> ids, Set<String> deleted) {
    List<Message> messages = new ArrayList<>();
    for (String id : ids) {
      if (id.isBlank()) continue;
      try {
        messages.add(
            GmailProvider.normalize(
                get(token, "/messages/" + ProviderHttpClient.encode(id) + "?format=full")));
      } catch (WorkspaceException exception) {
        if (exception.status() != 404) throw exception;
        deleted.add(id);
      }
    }
    return messages;
  }
}
