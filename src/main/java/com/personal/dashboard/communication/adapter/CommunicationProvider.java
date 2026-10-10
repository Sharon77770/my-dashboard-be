package com.personal.dashboard.communication.adapter;

import com.personal.dashboard.communication.domain.Communication.*;
import com.personal.dashboard.global.WorkspaceException;

/** Official API adapters only. Unsupported operations fail closed. */
public interface CommunicationProvider {
  ProviderId id();

  Identity connect(String credential);

  Page<Conversation> listConversations(String credential, String cursor, String query);

  Page<Message> fetchMessages(String credential, String conversationId, String cursor);

  default Page<Message> searchMessages(String credential, String query, String cursor) {
    throw new WorkspaceException(409, "이 Provider의 서버 메시지 검색은 지원하지 않습니다.");
  }

  default Page<Participant> participants(String credential, String conversationId, String cursor) {
    throw new WorkspaceException(409, "이 연결은 참여자 목록 조회를 지원하지 않습니다.");
  }

  default Page<Message> fetchThread(
      String credential, String conversationId, String threadId, String cursor) {
    throw new WorkspaceException(409, "이 연결의 스레드 조회는 지원하지 않습니다.");
  }

  /** Resolve the exact destination before it is shown to the owner for approval. */
  default Send prepareSend(String credential, Send message) {
    return message;
  }

  Message sendMessage(String credential, Send message);

  default Message mutate(String credential, Mutation mutation) {
    throw new WorkspaceException(409, "이 Provider는 해당 메시지 작업을 지원하지 않습니다.");
  }

  default java.util.List<Label> labels(String credential) {
    throw new WorkspaceException(409, "이 Provider는 라벨을 지원하지 않습니다.");
  }

  default void updateLabels(
      String credential,
      String conversationId,
      java.util.List<String> add,
      java.util.List<String> remove) {
    throw new WorkspaceException(409, "이 Provider는 읽음·라벨 변경을 지원하지 않습니다.");
  }

  default byte[] downloadAttachment(
      String credential, String conversationId, String messageId, String attachmentId) {
    throw new WorkspaceException(409, "이 연결은 첨부파일 다운로드를 지원하지 않습니다.");
  }
}
