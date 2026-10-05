package com.personal.dashboard.realtime.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.realtime.service.WorkspaceEvents;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

/**
 * Read-only, same-origin, session-owned transport. No commands or subscriptions arrive from
 * clients.
 */
@Component
public class WorkspaceSocketHandler extends TextWebSocketHandler {
  private record Binding(WebSocketSession socket, HttpSession session, String owner) {}

  private final Map<String, Binding> bindings = new ConcurrentHashMap<>();
  private final WorkspaceEvents events;
  private final ObjectMapper json;

  public WorkspaceSocketHandler(WorkspaceEvents events, ObjectMapper json) {
    this.events = events;
    this.json = json;
  }

  private boolean authorized(HttpSession session, String owner) {
    try {
      if (session == null || !session.getId().equals(owner)) return false;
      var context =
          (SecurityContext)
              session.getAttribute(
                  HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
      Authentication auth = context == null ? null : context.getAuthentication();
      return auth != null
          && auth.isAuthenticated()
          && auth.getAuthorities().stream()
              .anyMatch(role -> role.getAuthority().equals("ROLE_OWNER"));
    } catch (IllegalStateException exception) {
      return false;
    }
  }

  @Override
  public synchronized void afterConnectionEstablished(WebSocketSession original) throws Exception {
    var session = (HttpSession) original.getAttributes().get("workspace.session");
    String owner = (String) original.getAttributes().get("HTTP.SESSION.ID");
    if (!authorized(session, owner)) {
      original.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    if (bindings.size() >= 64
        || bindings.values().stream().filter(value -> value.owner().equals(owner)).count() >= 8) {
      original.close(new CloseStatus(1013, "connection limit"));
      return;
    }
    var socket = new ConcurrentWebSocketSessionDecorator(original, 2000, 65536);
    socket.setTextMessageSizeLimit(1024);
    bindings.put(socket.getId(), new Binding(socket, session, owner));
    events.subscribe(socket.getId(), owner, frame -> send(socket.getId(), frame));
  }

  private void send(String id, WorkspaceEvents.Frame frame) {
    var binding = bindings.get(id);
    if (binding == null) return;
    if (!authorized(binding.session(), binding.owner())) {
      close(id, CloseStatus.POLICY_VIOLATION);
      return;
    }
    try {
      binding.socket().sendMessage(new TextMessage(json.writeValueAsString(frame)));
    } catch (Exception exception) {
      close(id, CloseStatus.SERVER_ERROR);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
    close(socket.getId(), CloseStatus.POLICY_VIOLATION);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
    bindings.remove(socket.getId());
    events.unsubscribe(socket.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession socket, Throwable error) {
    close(socket.getId(), CloseStatus.SERVER_ERROR);
  }

  private void close(String id, CloseStatus status) {
    events.unsubscribe(id);
    var binding = bindings.remove(id);
    if (binding != null)
      try {
        binding.socket().close(status);
      } catch (Exception ignored) {
      }
  }

  public void closeOwner(String owner) {
    bindings.values().stream()
        .filter(value -> value.owner().equals(owner))
        .toList()
        .forEach(value -> close(value.socket().getId(), CloseStatus.POLICY_VIOLATION));
  }

  @Scheduled(fixedDelay = 10000)
  public void validateSessions() {
    for (var binding : bindings.values())
      if (!authorized(binding.session(), binding.owner()))
        close(binding.socket().getId(), CloseStatus.POLICY_VIOLATION);
  }
}
