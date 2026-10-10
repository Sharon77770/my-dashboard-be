package com.personal.dashboard.communication.domain;

import java.util.List;
import java.util.Set;

/** Provider-neutral facts. Unknown timestamps and read state remain null. */
public final class Communication {
  private Communication() {}

  public enum ProviderId {
    GMAIL,
    SLACK,
    DISCORD,
    KAKAOTALK
  }

  public enum Capability {
    READ,
    SEND,
    REPLY,
    THREADS,
    SEARCH,
    ATTACHMENTS,
    UPLOAD,
    EDIT,
    DELETE,
    REACTIONS,
    READ_STATE,
    PARTICIPANTS,
    LABELS,
    REMOTE_SCREEN
  }

  public enum ActionKind {
    SEND,
    EDIT,
    DELETE,
    REACTION
  }

  public record Mutation(
      ActionKind kind, String conversationId, String messageId, String text, String reaction) {}

  public enum ActionState {
    PENDING,
    SENDING,
    SENT,
    UNKNOWN,
    CANCELLED
  }

  public record Label(String id, String name) {}

  public record Identity(String id, String label, Set<Capability> capabilities) {}

  public record Conversation(
      String id, String title, String kind, String preview, Long updatedAt, Boolean unread) {}

  public record Attachment(String id, String name, String mediaType, long size) {}

  public record Participant(String id, String label) {}

  public record Reaction(String key, String label, Long count) {}

  public record Message(
      String id,
      String conversationId,
      String sender,
      String text,
      Long timestamp,
      String threadId,
      Boolean unread,
      List<Attachment> attachments,
      List<Reaction> reactions) {
    public Message {
      reactions = reactions == null ? List.of() : List.copyOf(reactions);
    }

    public Message(
        String id,
        String conversationId,
        String sender,
        String text,
        Long timestamp,
        String threadId,
        Boolean unread,
        List<Attachment> attachments) {
      this(id, conversationId, sender, text, timestamp, threadId, unread, attachments, List.of());
    }
  }

  public record Page<T>(List<T> items, String nextCursor) {}

  public record Upload(String name, String mediaType, byte[] data) {
    @Override
    public String toString() {
      return "Upload[content redacted]";
    }
  }

  public record Send(
      String conversationId,
      String recipient,
      String subject,
      String text,
      String replyTo,
      List<Upload> attachments) {
    public Send(
        String conversationId, String recipient, String subject, String text, String replyTo) {
      this(conversationId, recipient, subject, text, replyTo, List.of());
    }
  }

  public record SyncCursor(String value, long synchronizedAt) {}

  public record RuntimeConnection(String accountId, String mode, String state, String sessionId) {}
}
