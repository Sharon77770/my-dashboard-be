package com.personal.dashboard.runtime.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.runtime.adapter.*;
import com.personal.dashboard.runtime.service.RuntimeService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.apache.guacamole.protocol.GuacamoleInstruction;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

/**
 * Thin WebSocket transport for xterm input and Guacamole instructions; resources belong to
 * RuntimeService.
 */
@Component
public class RuntimeSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
  private record Binding(
      WebSocketSession socket,
      RuntimeService.RuntimeSession runtime,
      TerminalAdapter.Connection terminal,
      org.apache.guacamole.net.GuacamoleSocket remote) {}

  private final RuntimeService service;
  private final TerminalAdapter terminals;
  private final RemoteAdapter remotes;
  private final ObjectMapper mapper;
  private final ConcurrentMap<String, Binding> bindings = new ConcurrentHashMap<>();

  public RuntimeSocketHandler(
      RuntimeService service,
      TerminalAdapter terminals,
      RemoteAdapter remotes,
      ObjectMapper mapper) {
    this.service = service;
    this.terminals = terminals;
    this.remotes = remotes;
    this.mapper = mapper;
  }

  public java.util.List<String> getSubProtocols() {
    return java.util.List.of("guacamole");
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession socket) {
    Thread.startVirtualThread(() -> connect(socket));
  }

  private void connect(WebSocketSession original) {
    WebSocketSession socket = new ConcurrentWebSocketSessionDecorator(original, 10000, 1048576);
    String path = socket.getUri().getPath();
    String id = path.substring(path.lastIndexOf('/') + 1);
    boolean attached = false;
    try {
      String owner = (String) socket.getAttributes().get("HTTP.SESSION.ID");
      var runtime = service.attach(id, owner);
      attached = true;
      socket.setTextMessageSizeLimit(65536);
      if (runtime.kind.equals("TERMINAL")) {
        var terminal = terminals.open(runtime.device);
        if (!service.bind(
            runtime,
            () -> {
              terminal.close();
              socket.close();
            })) return;
        if (!socket.isOpen()) return;
        bindings.put(socket.getId(), new Binding(socket, runtime, terminal, null));
        try (Reader reader = new InputStreamReader(terminal.output(), StandardCharsets.UTF_8)) {
          char[] buffer = new char[4096];
          int count;
          while (socket.isOpen() && (count = reader.read(buffer)) != -1)
            socket.sendMessage(new TextMessage(new String(buffer, 0, count)));
        }
      } else {
        var remote = remotes.open(runtime.device, runtime.width, runtime.height);
        if (!service.bind(
            runtime,
            () -> {
              remote.close();
              socket.close();
            })) return;
        if (!socket.isOpen()) return;
        bindings.put(socket.getId(), new Binding(socket, runtime, null, remote));
        socket.sendMessage(new TextMessage(new GuacamoleInstruction("", id).toString()));
        char[] instruction;
        while (socket.isOpen() && (instruction = remote.getReader().read()) != null)
          socket.sendMessage(new TextMessage(new String(instruction)));
      }
    } catch (Exception exception) {
      try {
        if (socket.isOpen()) socket.close(new CloseStatus(1011, "서버 연결 실패 또는 종료. 장비 설정을 확인하세요."));
      } catch (Exception ignored) {
      }
    } finally {
      bindings.remove(socket.getId());
      if (attached) service.close(id);
      try {
        socket.close();
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession socket, TextMessage message) throws Exception {
    Binding binding = bindings.get(socket.getId());
    if (binding == null) return;
    service.owned(binding.runtime.id, (String) socket.getAttributes().get("HTTP.SESSION.ID"));
    if (binding.terminal != null) {
      var input = mapper.readTree(message.getPayload());
      if (input.path("type").asText().equals("resize"))
        binding.terminal.resize(
            Math.clamp(input.path("columns").asInt(), 20, 300),
            Math.clamp(input.path("rows").asInt(), 5, 120));
      else if (input.path("type").asText().equals("input")) {
        binding
            .terminal
            .input()
            .write(input.path("data").asText().getBytes(StandardCharsets.UTF_8));
        binding.terminal.input().flush();
      }
    } else if (message.getPayload().startsWith("0.,")) binding.socket.sendMessage(message);
    else binding.remote.getWriter().write(message.getPayload().toCharArray());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
    Binding binding = bindings.remove(socket.getId());
    if (binding != null) service.close(binding.runtime.id);
  }

  public void validateSessions() {
    service.expirePending();
    for (Binding binding : bindings.values())
      try {
        service.owned(binding.runtime.id, binding.runtime.ownerId);
      } catch (Exception exception) {
        service.close(binding.runtime.id);
      }
  }
}
