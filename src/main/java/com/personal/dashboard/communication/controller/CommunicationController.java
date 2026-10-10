package com.personal.dashboard.communication.controller;

import com.personal.dashboard.communication.domain.Communication.ProviderId;
import com.personal.dashboard.communication.dto.CommunicationDto.*;
import com.personal.dashboard.communication.service.*;
import com.personal.dashboard.global.WorkspaceException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** OWNER/CSRF browser contract; approval cannot be submitted through the MCP bearer endpoint. */
@RestController
@RequestMapping("/api/v1/communications")
public class CommunicationController {
  private final CommunicationService service;
  private final CommunicationOAuthService oauth;

  public CommunicationController(CommunicationService service, CommunicationOAuthService oauth) {
    this.service = service;
    this.oauth = oauth;
  }

  @GetMapping("/providers")
  public List<Provider> providers() {
    return oauth.providers();
  }

  @GetMapping("/accounts")
  public List<Account> accounts() {
    return service.accounts();
  }

  @PostMapping("/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public Account connect(@Valid @RequestBody ConnectionRequest request) {
    if (!"DISCORD".equals(request.provider()))
      throw new WorkspaceException(400, "Gmail과 Slack은 OAuth로 연결해 주세요.");
    return service.connect(ProviderId.DISCORD, request.token(), "", 0, Set.of());
  }

  @DeleteMapping("/accounts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnect(@PathVariable String id) {
    service.disconnect(id);
  }

  @PostMapping("/oauth")
  public Authorization authorize(@Valid @RequestBody OAuthRequest request, HttpSession session) {
    ProviderId provider;
    try {
      provider = ProviderId.valueOf(request.provider());
    } catch (IllegalArgumentException exception) {
      throw new WorkspaceException(400, "Provider를 확인해 주세요.");
    }
    return oauth.start(provider, session.getId());
  }

  @GetMapping("/oauth/callback")
  public ResponseEntity<Void> callback(
      @RequestParam String state,
      @RequestParam(required = false) String code,
      HttpSession session) {
    oauth.finish(state, code, session.getId());
    return ResponseEntity.status(303)
        .header("Location", "/")
        .header("Cache-Control", "no-store")
        .header("Referrer-Policy", "no-referrer")
        .build();
  }

  @GetMapping("/accounts/{id}/conversations")
  public Page<ConversationView> conversations(
      @PathVariable String id,
      @RequestParam(defaultValue = "") String cursor,
      @RequestParam(defaultValue = "") String query) {
    return service.conversations(id, cursor, query);
  }

  @GetMapping("/accounts/{id}/messages")
  public Page<MessageView> messages(
      @PathVariable String id,
      @RequestParam String conversationId,
      @RequestParam(defaultValue = "") String cursor) {
    return service.messages(id, conversationId, cursor);
  }

  @GetMapping("/accounts/{id}/thread-messages")
  public Page<MessageView> thread(
      @PathVariable String id,
      @RequestParam String conversationId,
      @RequestParam String threadId,
      @RequestParam(defaultValue = "") String cursor) {
    return service.thread(id, conversationId, threadId, cursor);
  }

  @GetMapping("/accounts/{id}/labels")
  public List<LabelView> labels(@PathVariable String id) {
    return service.labels(id);
  }

  @PostMapping("/accounts/{id}/labels")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void labels(@PathVariable String id, @Valid @RequestBody LabelRequest request) {
    service.updateLabels(id, request);
  }

  @GetMapping("/accounts/{id}/cached-messages")
  public List<MessageView> cached(@PathVariable String id, @RequestParam String conversationId) {
    return service.cachedMessages(id, conversationId);
  }

  @GetMapping("/search")
  public List<MessageView> search(@RequestParam String query) {
    return service.search(query);
  }

  @GetMapping("/accounts/{id}/message-search")
  public Page<MessageView> searchProvider(
      @PathVariable String id,
      @RequestParam String query,
      @RequestParam(defaultValue = "") String cursor) {
    return service.searchProvider(id, query, cursor);
  }

  @GetMapping("/accounts/{id}/participants")
  public Page<ParticipantView> participants(
      @PathVariable String id,
      @RequestParam String conversationId,
      @RequestParam(defaultValue = "") String cursor) {
    return service.participants(id, conversationId, cursor);
  }

  @GetMapping("/actions")
  public List<Action> actions() {
    return service.actions();
  }

  @PostMapping("/accounts/{id}/actions")
  @ResponseStatus(HttpStatus.CREATED)
  public Action prepare(@PathVariable String id, @Valid @RequestBody SendRequest request) {
    return service.prepare(id, request);
  }

  @PostMapping("/accounts/{id}/message-actions")
  @ResponseStatus(HttpStatus.CREATED)
  public Action mutation(@PathVariable String id, @Valid @RequestBody MutationRequest request) {
    return service.prepareMutation(id, request);
  }

  @PostMapping("/actions/{id}/confirmation")
  public Action confirm(@PathVariable String id, HttpSession session) {
    return service.confirm(id, session.getId());
  }

  @DeleteMapping("/actions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@PathVariable String id) {
    service.cancel(id);
  }

  @GetMapping("/accounts/{id}/attachment")
  public ResponseEntity<byte[]> attachment(
      @PathVariable String id,
      @RequestParam String conversationId,
      @RequestParam String messageId,
      @RequestParam String attachmentId) {
    var download = service.attachment(id, conversationId, messageId, attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            "Content-Disposition",
            ContentDisposition.attachment()
                .filename(download.name(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(download.bytes());
  }
}
