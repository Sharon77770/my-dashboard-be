package com.personal.dashboard.communication.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;

/** Public contracts deliberately exclude credential and raw upstream fields. */
public final class CommunicationDto {
  private CommunicationDto() {}

  public record LabelView(String id, String name) {}

  public record LabelRequest(
      @NotBlank @Size(max = 256) String conversationId,
      @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> add,
      @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> remove) {}

  public record Account(String id, String provider, String label, Set<String> capabilities) {}

  public record Provider(String id, boolean configured, String mode, String limitation) {}

  public record ConnectionRequest(
      @NotBlank @Size(max = 20) String provider, @NotBlank @Size(max = 8192) String token) {}

  public record OAuthRequest(@NotBlank @Size(max = 20) String provider) {}

  public record Authorization(String url) {}

  public record ConversationView(
      String accountId,
      String provider,
      String id,
      String title,
      String kind,
      String preview,
      Long updatedAt,
      Boolean unread) {}

  public record AttachmentView(String id, String name, String mediaType, long size) {}

  public record ParticipantView(String id, String label) {}

  public record ReactionView(String key, String label, Long count) {}

  public record MessageView(
      String accountId,
      String provider,
      String id,
      String conversationId,
      String sender,
      String text,
      Long timestamp,
      String threadId,
      Boolean unread,
      List<AttachmentView> attachments,
      List<ReactionView> reactions) {
    public MessageView {
      reactions = reactions == null ? List.of() : List.copyOf(reactions);
    }

    public MessageView(
        String accountId,
        String provider,
        String id,
        String conversationId,
        String sender,
        String text,
        Long timestamp,
        String threadId,
        Boolean unread,
        List<AttachmentView> attachments) {
      this(
          accountId,
          provider,
          id,
          conversationId,
          sender,
          text,
          timestamp,
          threadId,
          unread,
          attachments,
          List.of());
    }
  }

  public record Page<T>(List<T> items, String nextCursor) {}

  public record Upload(
      @NotBlank @Size(max = 180) String name,
      @NotBlank @Size(max = 100) String mediaType,
      @NotBlank @Size(max = 7000000) String data) {}

  public record SendRequest(
      @Size(max = 256) String conversationId,
      @Size(max = 320) String recipient,
      @Size(max = 300) String subject,
      @NotBlank @Size(max = 32000) String text,
      @Size(max = 256) String replyTo,
      @Size(max = 5) List<@jakarta.validation.Valid Upload> attachments) {
    public SendRequest(
        String conversationId, String recipient, String subject, String text, String replyTo) {
      this(conversationId, recipient, subject, text, replyTo, List.of());
    }
  }

  public record MutationRequest(
      @NotBlank @Pattern(regexp = "EDIT|DELETE|REACTION") String operation,
      @NotBlank @Size(max = 256) String conversationId,
      @NotBlank @Size(max = 256) String messageId,
      @Size(max = 32000) String text,
      @Size(max = 100) String reaction) {}

  public record Action(
      String id,
      String accountId,
      String provider,
      String accountLabel,
      String state,
      long expiresAt,
      SendRequest message,
      String resultId,
      String operation,
      MutationRequest mutation) {}
}
