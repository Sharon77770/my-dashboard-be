package com.personal.dashboard.communication.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.communication.adapter.*;
import com.personal.dashboard.communication.domain.Communication.*;
import com.personal.dashboard.communication.dto.CommunicationDto;
import com.personal.dashboard.communication.entity.CommunicationRecords.*;
import com.personal.dashboard.communication.repository.CommunicationRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import com.personal.dashboard.realtime.service.WorkspaceEvents;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Shared REST/MCP use cases. Only browser confirmation executes immutable pending actions. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class CommunicationService {
  private record PendingContent(
      ActionKind operation,
      CommunicationDto.SendRequest message,
      CommunicationDto.MutationRequest mutation) {}

  private final CommunicationRepository repository;
  private final CredentialVault vault;
  private final ObjectMapper json;
  private final Map<ProviderId, CommunicationProvider> providers = new EnumMap<>(ProviderId.class);
  private final WorkspaceEvents events;
  private final CommunicationTokens tokens;
  private final Validator validator;

  public CommunicationService(
      CommunicationRepository repository,
      CredentialVault vault,
      ObjectMapper json,
      List<CommunicationProvider> providers,
      WorkspaceEvents events,
      CommunicationTokens tokens,
      Validator validator) {
    this.repository = repository;
    this.vault = vault;
    this.json = json;
    this.events = events;
    this.tokens = tokens;
    this.validator = validator;
    providers.forEach(provider -> this.providers.put(provider.id(), provider));
  }

  public List<CommunicationDto.Account> accounts() {
    return repository.accounts().stream().map(this::view).toList();
  }

  public synchronized CommunicationDto.Account connect(
      ProviderId id, String token, String refresh, long expiresAt, Set<String> grantedScopes) {
    if (token == null || token.isBlank() || token.length() > 8192)
      throw new WorkspaceException(400, "유효한 연결 인증이 필요합니다.");
    var identity = provider(id).connect(token);
    Set<Capability> capabilities = new HashSet<>(identity.capabilities());
    if (id == ProviderId.GMAIL
        && !grantedScopes.contains("https://www.googleapis.com/auth/gmail.send")
        && !grantedScopes.contains("https://www.googleapis.com/auth/gmail.modify")) {
      capabilities.remove(Capability.SEND);
      capabilities.remove(Capability.REPLY);
      capabilities.remove(Capability.UPLOAD);
    }
    if (id == ProviderId.GMAIL
        && !grantedScopes.contains("https://www.googleapis.com/auth/gmail.modify")) {
      capabilities.remove(Capability.READ_STATE);
      capabilities.remove(Capability.LABELS);
    }
    if (id == ProviderId.SLACK && !grantedScopes.contains("chat:write")) {
      capabilities.remove(Capability.SEND);
      capabilities.remove(Capability.REPLY);
    }
    if (id == ProviderId.SLACK && !grantedScopes.contains("chat:write")) {
      capabilities.remove(Capability.EDIT);
      capabilities.remove(Capability.DELETE);
    }
    if (id == ProviderId.SLACK && !grantedScopes.contains("reactions:write"))
      capabilities.remove(Capability.REACTIONS);
    if (id == ProviderId.SLACK && !grantedScopes.contains("files:read"))
      capabilities.remove(Capability.ATTACHMENTS);
    if (id == ProviderId.SLACK && !grantedScopes.contains("files:write"))
      capabilities.remove(Capability.UPLOAD);
    if (id == ProviderId.SLACK
        && Collections.disjoint(
            grantedScopes, Set.of("channels:read", "groups:read", "im:read", "mpim:read")))
      capabilities.remove(Capability.PARTICIPANTS);
    var existing =
        repository.accounts().stream()
            .filter(
                account ->
                    account.provider().equals(id.name())
                        && account.externalId().equals(identity.id()))
            .findFirst();
    String accountId =
        existing.map(AccountRecord::id).orElseGet(() -> UUID.randomUUID().toString());
    var credential = new CommunicationTokens.Credential(token, refresh, expiresAt);
    var record =
        new AccountRecord(
            accountId,
            id.name(),
            identity.id(),
            identity.label(),
            vault.encrypt(encode(credential)),
            capabilities.stream()
                .map(Enum::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(",")),
            System.currentTimeMillis());
    repository.saveAccount(record);
    events.changed("communications");
    return view(record);
  }

  public synchronized void disconnect(String id) {
    account(id);
    repository.deleteAccount(id);
    events.changed("communications");
  }

  public CommunicationDto.Page<CommunicationDto.ConversationView> conversations(
      String id, String cursor, String query) {
    checkLength(cursor, 2048);
    checkLength(query, 500);
    var account = account(id);
    var page = provider(account).listConversations(token(account), cursor, query);
    return new CommunicationDto.Page<>(
        page.items().stream()
            .map(
                item ->
                    new CommunicationDto.ConversationView(
                        id,
                        account.provider(),
                        item.id(),
                        item.title(),
                        item.kind(),
                        item.preview(),
                        item.updatedAt(),
                        item.unread()))
            .toList(),
        page.nextCursor());
  }

  public CommunicationDto.Page<CommunicationDto.MessageView> messages(
      String id, String conversationId, String cursor) {
    checkId(conversationId);
    checkLength(cursor, 2048);
    var account = account(id);
    var page = provider(account).fetchMessages(token(account), conversationId, cursor);
    var messages =
        page.items().stream()
            .map(item -> messageView(account, item))
            .collect(
                java.util.stream.Collectors.toMap(
                    CommunicationDto.MessageView::id,
                    item -> item,
                    (left, right) -> right,
                    LinkedHashMap::new));
    List<CommunicationDto.MessageView> items = new ArrayList<>(messages.values());
    items.sort(
        Comparator.comparing(
                CommunicationDto.MessageView::timestamp,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CommunicationDto.MessageView::id));
    synchronized (this) {
      account(id);
      for (var item : items)
        repository.cache(id, conversationId, item.id(), item.timestamp(), encode(item));
      repository.cursor(id, conversationId, page.nextCursor());
    }
    return new CommunicationDto.Page<>(items, page.nextCursor());
  }

  public CommunicationDto.Page<CommunicationDto.MessageView> thread(
      String id, String conversationId, String threadId, String cursor) {
    checkId(conversationId);
    checkId(threadId);
    checkLength(cursor, 2048);
    var account = account(id);
    var page = provider(account).fetchThread(token(account), conversationId, threadId, cursor);
    var items =
        page.items().stream()
            .map(message -> messageView(account, message))
            .sorted(
                Comparator.comparing(
                        CommunicationDto.MessageView::timestamp,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CommunicationDto.MessageView::id))
            .toList();
    synchronized (this) {
      account(id);
      for (var item : items)
        repository.cache(id, conversationId, item.id(), item.timestamp(), encode(item));
    }
    return new CommunicationDto.Page<>(items, page.nextCursor());
  }

  public List<CommunicationDto.LabelView> labels(String id) {
    var account = account(id);
    return provider(account).labels(token(account)).stream()
        .map(label -> new CommunicationDto.LabelView(label.id(), label.name()))
        .toList();
  }

  public void updateLabels(String id, CommunicationDto.LabelRequest request) {
    if (request == null || !validator.validate(request).isEmpty())
      throw new WorkspaceException(400, "라벨 입력을 확인해 주세요.");
    var account = account(id);
    if (!view(account).capabilities().contains("LABELS"))
      throw new WorkspaceException(409, "라벨 변경 권한으로 계정을 다시 연결해 주세요.");
    var permitted =
        provider(account).labels(token(account)).stream()
            .map(Label::id)
            .collect(java.util.stream.Collectors.toSet());
    if (!permitted.containsAll(request.add()) || !permitted.containsAll(request.remove()))
      throw new WorkspaceException(400, "원본 계정의 라벨을 선택해 주세요.");
    provider(account)
        .updateLabels(token(account), request.conversationId(), request.add(), request.remove());
    events.changed("communications");
  }

  public List<CommunicationDto.MessageView> cachedMessages(String id, String conversationId) {
    account(id);
    checkId(conversationId);
    return repository.cachedMessages(id, conversationId).stream()
        .map(value -> decode(value, CommunicationDto.MessageView.class))
        .toList();
  }

  public CommunicationDto.Page<CommunicationDto.ParticipantView> participants(
      String id, String conversationId, String cursor) {
    checkId(conversationId);
    checkLength(cursor, 2048);
    var account = account(id);
    if (!view(account).capabilities().contains("PARTICIPANTS"))
      throw new WorkspaceException(409, "이 연결은 참여자 목록 조회를 지원하지 않습니다.");
    var page = provider(account).participants(token(account), conversationId, cursor);
    return new CommunicationDto.Page<>(
        page.items().stream()
            .map(item -> new CommunicationDto.ParticipantView(item.id(), item.label()))
            .toList(),
        page.nextCursor());
  }

  public CommunicationDto.Page<CommunicationDto.MessageView> searchProvider(
      String id, String query, String cursor) {
    if (query == null || query.isBlank()) throw new WorkspaceException(400, "검색어를 입력해 주세요.");
    checkLength(query, 500);
    checkLength(cursor, 2048);
    var account = account(id);
    if (!view(account).capabilities().contains("SEARCH"))
      throw new WorkspaceException(409, "이 연결은 서버 메시지 검색을 지원하지 않습니다. 저장된 메시지 검색을 사용해 주세요.");
    var page = provider(account).searchMessages(token(account), query, cursor);
    Map<String, CommunicationDto.MessageView> unique = new LinkedHashMap<>();
    for (var item : page.items()) {
      var view = messageView(account, item);
      unique.put(view.conversationId() + ":" + view.id(), view);
    }
    synchronized (this) {
      account(id);
      for (var item : unique.values())
        repository.cache(id, item.conversationId(), item.id(), item.timestamp(), encode(item));
    }
    return new CommunicationDto.Page<>(new ArrayList<>(unique.values()), page.nextCursor());
  }

  public List<CommunicationDto.MessageView> search(String query) {
    if (query == null || query.isBlank()) throw new WorkspaceException(400, "검색어를 입력해 주세요.");
    checkLength(query, 500);
    return repository.search(query).stream()
        .map(value -> decode(value, CommunicationDto.MessageView.class))
        .toList();
  }

  public synchronized CommunicationDto.Action prepare(
      String id, CommunicationDto.SendRequest request) {
    if (request == null || !validator.validate(request).isEmpty())
      throw new WorkspaceException(400, "메시지 입력을 확인해 주세요.");
    var account = account(id);
    if (!view(account).capabilities().contains("SEND"))
      throw new WorkspaceException(409, "이 계정에는 메시지 전송 권한이 없습니다.");
    var normalized =
        new CommunicationDto.SendRequest(
            clean(request.conversationId()),
            clean(request.recipient()),
            clean(request.subject()),
            request.text(),
            clean(request.replyTo()),
            request.attachments() == null ? List.of() : List.copyOf(request.attachments()));
    var attachments = CommunicationAttachments.decode(normalized.attachments());
    if (!attachments.isEmpty() && !view(account).capabilities().contains("UPLOAD"))
      throw new WorkspaceException(409, "이 Provider의 파일 전송은 아직 지원하지 않습니다.");
    if (!"GMAIL".equals(account.provider()) && normalized.conversationId().isBlank())
      throw new WorkspaceException(400, "대화를 선택해 주세요.");
    if ("GMAIL".equals(account.provider())
        && normalized.recipient().isBlank()
        && normalized.replyTo().isBlank()) throw new WorkspaceException(400, "메일 수신자를 입력해 주세요.");
    if (normalized.recipient().contains("\n")
        || normalized.recipient().contains("\r")
        || normalized.subject().contains("\n")
        || normalized.subject().contains("\r"))
      throw new WorkspaceException(400, "메일 헤더 줄바꿈은 허용되지 않습니다.");
    var prepared =
        provider(account)
            .prepareSend(
                token(account),
                new Send(
                    normalized.conversationId(),
                    normalized.recipient(),
                    normalized.subject(),
                    normalized.text(),
                    normalized.replyTo(),
                    attachments));
    normalized =
        new CommunicationDto.SendRequest(
            normalized.conversationId(),
            prepared.recipient(),
            prepared.subject(),
            normalized.text(),
            normalized.replyTo(),
            normalized.attachments());
    if (!validator.validate(normalized).isEmpty())
      throw new WorkspaceException(400, "답장 대상 또는 제목의 길이를 확인해 주세요.");
    repository.cleanup();
    if (repository.actions().stream().filter(action -> action.state().equals("PENDING")).count()
        >= 50) throw new WorkspaceException(429, "대기 중인 전송 요청을 정리해 주세요.");
    var action =
        new PendingActionRecord(
            UUID.randomUUID().toString(),
            id,
            vault.encrypt(encode(new PendingContent(ActionKind.SEND, normalized, null))),
            ActionState.PENDING.name(),
            System.currentTimeMillis() + 600000,
            "");
    repository.createAction(action);
    events.changed("communications");
    return actionView(action);
  }

  public synchronized CommunicationDto.Action prepareMutation(
      String id, CommunicationDto.MutationRequest request) {
    if (request == null || !validator.validate(request).isEmpty())
      throw new WorkspaceException(400, "메시지 작업 입력을 확인해 주세요.");
    var account = account(id);
    ActionKind kind = ActionKind.valueOf(request.operation());
    String capability = kind == ActionKind.REACTION ? "REACTIONS" : kind.name();
    if (!view(account).capabilities().contains(capability))
      throw new WorkspaceException(409, "해당 메시지 작업 권한이 없습니다.");
    String text = clean(request.text()), reaction = clean(request.reaction());
    if ((kind == ActionKind.EDIT && text.isBlank())
        || (kind == ActionKind.REACTION && reaction.isBlank()))
      throw new WorkspaceException(400, "수정 내용 또는 리액션을 입력해 주세요.");
    repository.cleanup();
    if (repository.actions().size() >= 50) throw new WorkspaceException(429, "대기 작업을 정리해 주세요.");
    var snapshot =
        new CommunicationDto.MutationRequest(
            kind.name(), request.conversationId(), request.messageId(), text, reaction);
    var action =
        new PendingActionRecord(
            UUID.randomUUID().toString(),
            id,
            vault.encrypt(encode(new PendingContent(kind, null, snapshot))),
            ActionState.PENDING.name(),
            System.currentTimeMillis() + 600000,
            "");
    repository.createAction(action);
    events.changed("communications");
    return actionView(action);
  }

  public List<CommunicationDto.Action> actions() {
    return repository.actions().stream().map(this::actionView).toList();
  }

  /** The OWNER browser POST is the approval itself; MCP has no execution or approval tool. */
  public synchronized CommunicationDto.Action confirm(String id, String browserSession) {
    if (org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()
            == null
        || "dashboard-mcp"
            .equals(
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getName())
        || browserSession == null
        || browserSession.isBlank())
      throw new WorkspaceException(403, "대시보드에서 전송 내용을 확인하고 승인해 주세요.");
    var action =
        repository.actions().stream()
            .filter(item -> item.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(404, "전송 요청이 없거나 만료되었습니다."));
    if (!repository.claim(id)) throw new WorkspaceException(409, "이미 처리한 전송 요청입니다. 중복 전송하지 않습니다.");
    var account = account(action.accountId());
    var content = decode(vault.decrypt(action.payloadCipher()), PendingContent.class);
    try {
      Message sent;
      if (content.operation() == ActionKind.SEND) {
        var request = content.message();
        sent =
            provider(account)
                .sendMessage(
                    token(account),
                    new Send(
                        request.conversationId(),
                        request.recipient(),
                        request.subject(),
                        request.text(),
                        request.replyTo(),
                        CommunicationAttachments.decode(request.attachments())));
      } else {
        var mutation = content.mutation();
        sent =
            provider(account)
                .mutate(
                    token(account),
                    new Mutation(
                        content.operation(),
                        mutation.conversationId(),
                        mutation.messageId(),
                        mutation.text(),
                        mutation.reaction()));
      }
      if (sent.id().isBlank()) throw new WorkspaceException(502, "전송 결과를 확인하지 못했습니다.");
      repository.finish(id, ActionState.SENT.name(), sent.id());
      if (content.operation() == ActionKind.DELETE)
        repository.removeMessage(account.id(), sent.conversationId(), sent.id());
      else if (content.operation() == ActionKind.SEND || content.operation() == ActionKind.EDIT) {
        var view = messageView(account, sent);
        repository.cache(
            account.id(), sent.conversationId(), sent.id(), sent.timestamp(), encode(view));
      }
    } catch (RuntimeException exception) {
      repository.finish(id, ActionState.UNKNOWN.name(), "");
      throw exception;
    } finally {
      events.changed("communications");
    }
    return actions().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
  }

  public void cancel(String id) {
    repository.cancel(id);
    events.changed("communications");
  }

  public record AttachmentDownload(String name, byte[] bytes) {}

  public AttachmentDownload attachment(
      String id, String conversationId, String messageId, String attachmentId) {
    checkId(messageId);
    checkLength(attachmentId, 1024);
    var account = account(id);
    checkId(conversationId);
    var cached = repository.cachedMessage(id, conversationId, messageId);
    var candidates =
        cached.isPresent()
            ? List.of(decode(cached.get(), CommunicationDto.MessageView.class))
            : messages(id, conversationId, "").items();
    var attachment =
        candidates.stream()
            .filter(message -> message.id().equals(messageId))
            .flatMap(message -> message.attachments().stream())
            .filter(item -> item.id().equals(attachmentId))
            .findFirst()
            .orElseThrow(() -> new WorkspaceException(404, "첨부파일을 찾을 수 없습니다."));
    if (attachment.size() > 5 * 1024 * 1024)
      throw new WorkspaceException(413, "첨부파일 다운로드 한도는 5 MiB입니다.");
    byte[] result =
        provider(account)
            .downloadAttachment(token(account), conversationId, messageId, attachmentId);
    if (result.length > 5 * 1024 * 1024)
      throw new WorkspaceException(413, "첨부파일 다운로드 한도는 5 MiB입니다.");
    StringBuilder filename = new StringBuilder();
    attachment
        .name()
        .codePoints()
        .limit(180)
        .forEach(
            point ->
                filename.appendCodePoint(
                    Character.isISOControl(point) || point == '/' || point == '\\' ? '_' : point));
    return new AttachmentDownload(
        filename.isEmpty() ? "attachment.bin" : filename.toString(), result);
  }

  private CommunicationDto.MessageView messageView(AccountRecord account, Message item) {
    return new CommunicationDto.MessageView(
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
  }

  private CommunicationDto.Action actionView(PendingActionRecord action) {
    var account = account(action.accountId());
    return new CommunicationDto.Action(
        action.id(),
        account.id(),
        account.provider(),
        account.label(),
        action.state(),
        action.expiresAt(),
        actionMessage(action),
        action.resultId(),
        decode(vault.decrypt(action.payloadCipher()), PendingContent.class).operation().name(),
        decode(vault.decrypt(action.payloadCipher()), PendingContent.class).mutation());
  }

  private CommunicationDto.SendRequest actionMessage(PendingActionRecord action) {
    var content = decode(vault.decrypt(action.payloadCipher()), PendingContent.class);
    if (content.message() == null) {
      var mutation = content.mutation();
      return new CommunicationDto.SendRequest(
          mutation.conversationId(),
          "",
          mutation.operation(),
          mutation.text().isBlank() ? mutation.reaction() : mutation.text(),
          mutation.messageId(),
          List.of());
    }
    var input = content.message();
    return new CommunicationDto.SendRequest(
        input.conversationId(),
        input.recipient(),
        input.subject(),
        input.text(),
        input.replyTo(),
        input.attachments() == null
            ? List.of()
            : input.attachments().stream()
                .map(file -> new CommunicationDto.Upload(file.name(), file.mediaType(), ""))
                .toList());
  }

  private CommunicationDto.Account view(AccountRecord account) {
    return new CommunicationDto.Account(
        account.id(),
        account.provider(),
        account.label(),
        account.capabilities().isBlank()
            ? Set.of()
            : Set.copyOf(Arrays.asList(account.capabilities().split(","))));
  }

  private AccountRecord account(String id) {
    return repository.accounts().stream()
        .filter(account -> account.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new WorkspaceException(404, "연결된 계정을 찾을 수 없습니다."));
  }

  private CommunicationProvider provider(AccountRecord account) {
    return provider(ProviderId.valueOf(account.provider()));
  }

  private CommunicationProvider provider(ProviderId id) {
    var provider = providers.get(id);
    if (provider == null) throw new WorkspaceException(409, "이 Provider는 아직 구조화된 메시지를 지원하지 않습니다.");
    return provider;
  }

  private synchronized String token(AccountRecord account) {
    return tokens.accessToken(account(account.id()));
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private static void checkId(String value) {
    if (value == null || value.isBlank()) throw new WorkspaceException(400, "리소스 ID가 필요합니다.");
    checkLength(value, 256);
  }

  private static void checkLength(String value, int max) {
    if (value == null || value.length() > max) throw new WorkspaceException(400, "입력 길이를 확인해 주세요.");
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw new WorkspaceException(500, "메시지 데이터를 저장하지 못했습니다.");
    }
  }

  private <T> T decode(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (Exception exception) {
      throw new WorkspaceException(500, "저장된 메시지 데이터를 읽지 못했습니다.");
    }
  }
}
