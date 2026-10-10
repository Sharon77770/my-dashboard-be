package com.personal.dashboard.communication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.adapter.GmailSyncAdapter;
import com.personal.dashboard.communication.dto.CommunicationDto;
import com.personal.dashboard.communication.repository.CommunicationRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.realtime.service.WorkspaceEvents;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** HTTP work precedes the short atomic cache/checkpoint commit, preserving resumability. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class GmailSyncService {
  public record Progress(
      String phase, int processed, boolean hasMore, boolean reset, long synchronizedAt) {}

  private final CommunicationRepository repository;
  private final CommunicationTokens tokens;
  private final GmailSyncAdapter adapter;
  private final ObjectMapper json;
  private final WorkspaceEvents events;
  private final TransactionTemplate transaction;

  public GmailSyncService(
      CommunicationRepository repository,
      CommunicationTokens tokens,
      GmailSyncAdapter adapter,
      ObjectMapper json,
      WorkspaceEvents events,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.repository = repository;
    this.tokens = tokens;
    this.adapter = adapter;
    this.json = json;
    this.events = events;
    this.transaction = new TransactionTemplate(manager);
  }

  public synchronized Progress synchronize(String id) {
    var account =
        repository.accounts().stream()
            .filter(item -> item.id().equals(id) && item.provider().equals("GMAIL"))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(404, "Gmail 계정을 찾을 수 없습니다."));
    GmailSyncAdapter.Cursor cursor = null;
    try {
      var stored = repository.cursor(id, "@gmail-sync");
      if (stored.isPresent()) cursor = json.readValue(stored.get(), GmailSyncAdapter.Cursor.class);
    } catch (Exception exception) {
      throw new WorkspaceException(500, "동기화 상태를 읽지 못했습니다.");
    }
    var batch = adapter.fetch(tokens.accessToken(account), cursor);
    transaction.executeWithoutResult(
        status -> {
          if (repository.accounts().stream().noneMatch(item -> item.id().equals(id)))
            throw new WorkspaceException(404, "연결 해제된 계정입니다.");
          if (batch.reset()) repository.clearMessages(id);
          for (var message : batch.messages()) {
            var view =
                new CommunicationDto.MessageView(
                    id,
                    "GMAIL",
                    message.id(),
                    message.conversationId(),
                    message.sender(),
                    message.text(),
                    message.timestamp(),
                    message.threadId(),
                    message.unread(),
                    message.attachments().stream()
                        .map(
                            file ->
                                new CommunicationDto.AttachmentView(
                                    file.id(), file.name(), file.mediaType(), file.size()))
                        .toList());
            repository.cache(
                id, message.conversationId(), message.id(), message.timestamp(), encode(view));
          }
          for (String deleted : batch.deleted()) repository.removeMessage(id, deleted);
          repository.cursor(id, "@gmail-sync", encode(batch.cursor()));
        });
    events.changed("communications");
    return new Progress(
        batch.cursor().phase(),
        batch.messages().size(),
        batch.hasMore(),
        batch.reset(),
        System.currentTimeMillis());
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw new WorkspaceException(500, "동기화 상태를 저장하지 못했습니다.");
    }
  }
}
