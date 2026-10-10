package com.personal.dashboard.communication.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;

/**
 * Bot-only Gateway v10, heartbeat ACK, sequence resume and bounded reconnect. Never sends messages.
 */
@Component
public class DiscordGatewayAdapter {
  private final ObjectMapper json;
  private final ProviderHttpClient http;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final Map<String, Connection> connections = new ConcurrentHashMap<>();

  public record State(String accountId, String state) {}

  public DiscordGatewayAdapter(ObjectMapper json, ProviderHttpClient http) {
    this.json = json;
    this.http = http;
  }

  public void retain(Set<String> ids) {
    connections
        .entrySet()
        .removeIf(
            entry -> {
              if (ids.contains(entry.getKey())) return false;
              entry.getValue().close();
              return true;
            });
  }

  public void connect(String id, String token, BiConsumer<String, JsonNode> receive) {
    connections.compute(
        id,
        (key, current) -> {
          if (current != null && current.token.equals(token)) return current;
          if (current != null) current.close();
          return new Connection(token, receive);
        });
  }

  public List<State> states() {
    return connections.entrySet().stream()
        .map(entry -> new State(entry.getKey(), entry.getValue().state))
        .toList();
  }

  public void tick() {
    for (var connection : connections.values()) connection.tick();
  }

  @PreDestroy
  public void close() {
    connections.values().forEach(Connection::close);
    connections.clear();
  }

  final class Connection implements WebSocket.Listener {
    private final String token;
    private final BiConsumer<String, JsonNode> receive;
    private final StringBuilder buffer = new StringBuilder();
    private volatile WebSocket socket;
    private volatile boolean connecting, stopped, acknowledged = true;
    private volatile long nextConnect, heartbeatAt, interval = 45000;
    private volatile Long sequence;
    private volatile String session = "",
        endpoint = "wss://gateway.discord.gg/?v=10&encoding=json",
        state = "DISCONNECTED";
    private int failures;

    Connection(String token, BiConsumer<String, JsonNode> receive) {
      this.token = token;
      this.receive = receive;
    }

    synchronized void tick() {
      long now = System.currentTimeMillis();
      if (stopped) return;
      if (socket == null) {
        if (!connecting && now >= nextConnect) start();
        return;
      }
      if (heartbeatAt > 0 && now >= heartbeatAt) {
        if (!acknowledged) {
          retry();
          return;
        }
        heartbeat();
      }
    }

    private void start() {
      connecting = true;
      state = "CONNECTING";
      CompletableFuture.runAsync(
          () -> {
            try {
              if (session.isBlank()) {
                var info = http.get("https://discord.com/api/v10/gateway/bot", "Bot " + token);
                var limit = info.path("session_start_limit");
                if (limit.has("remaining") && limit.path("remaining").asInt() == 0) {
                  nextConnect =
                      System.currentTimeMillis()
                          + Math.max(60000, limit.path("reset_after").asLong());
                  state = "RATE_LIMITED";
                  connecting = false;
                  return;
                }
              }
              if (stopped) {
                connecting = false;
                return;
              }
              client
                  .newWebSocketBuilder()
                  .connectTimeout(Duration.ofSeconds(10))
                  .buildAsync(URI.create(endpoint), this)
                  .whenComplete(
                      (value, error) -> {
                        if (error != null) retry();
                      });
            } catch (Exception exception) {
              retry();
            }
          });
    }

    @Override
    public synchronized void onOpen(WebSocket value) {
      if (stopped) {
        value.abort();
        return;
      }
      socket = value;
      connecting = false;
      heartbeatAt = 0;
      acknowledged = true;
      value.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket value, CharSequence text, boolean last) {
      synchronized (this) {
        if (value != socket) return CompletableFuture.completedFuture(null);
        buffer.append(text);
        if (buffer.length() > 1048576) {
          buffer.setLength(0);
          retry();
          return CompletableFuture.completedFuture(null);
        }
        if (last) {
          String input = buffer.toString();
          buffer.setLength(0);
          try {
            event(json.readTree(input));
          } catch (Exception exception) {
            retry();
          }
        }
      }
      value.request(1);
      return CompletableFuture.completedFuture(null);
    }

    private void event(JsonNode event) {
      if (event.hasNonNull("s")) sequence = event.path("s").asLong();
      switch (event.path("op").asInt(-1)) {
        case 10 -> {
          interval =
              Math.max(
                  1000, Math.min(120000, event.path("d").path("heartbeat_interval").asLong(45000)));
          heartbeatAt = System.currentTimeMillis() + Math.max(1, (long) (Math.random() * interval));
          if (!session.isBlank() && sequence != null)
            send(6, Map.of("token", token, "session_id", session, "seq", sequence));
          else
            send(
                2,
                Map.of(
                    "token",
                    token,
                    "intents",
                    33281,
                    "properties",
                    Map.of(
                        "os",
                        "linux",
                        "browser",
                        "personal-workspace",
                        "device",
                        "personal-workspace")));
        }
        case 11 -> acknowledged = true;
        case 1 -> heartbeat();
        case 7 -> retry();
        case 9 -> {
          if (!event.path("d").asBoolean()) {
            session = "";
            sequence = null;
            endpoint = "wss://gateway.discord.gg/?v=10&encoding=json";
          }
          retry();
        }
        case 0 -> {
          String type = event.path("t").asText();
          var data = event.path("d");
          if (type.equals("READY")) {
            session = data.path("session_id").asText();
            String resume = data.path("resume_gateway_url").asText();
            URI uri = URI.create(resume);
            if (!"wss".equals(uri.getScheme())
                || uri.getUserInfo() != null
                || uri.getPort() != -1
                || uri.getHost() == null
                || !uri.getHost().matches("gateway[-a-z0-9]*\\.discord\\.gg"))
              throw new IllegalArgumentException();
            endpoint = resume + (resume.contains("?") ? "&" : "?") + "v=10&encoding=json";
            state = "CONNECTED";
            failures = 0;
          }
          if (type.equals("RESUMED")) {
            state = "CONNECTED";
            failures = 0;
          }
          if (Set.of(
                  "MESSAGE_CREATE",
                  "MESSAGE_UPDATE",
                  "MESSAGE_DELETE",
                  "MESSAGE_REACTION_ADD",
                  "MESSAGE_REACTION_REMOVE")
              .contains(type)) receive.accept(type, data);
        }
        default -> {}
      }
    }

    private void heartbeat() {
      acknowledged = false;
      heartbeatAt = System.currentTimeMillis() + interval;
      send(1, sequence);
    }

    private void send(int operation, Object data) {
      try {
        var frame = json.createObjectNode().put("op", operation);
        frame.set("d", json.valueToTree(data));
        var current = socket;
        if (current != null)
          current
              .sendText(json.writeValueAsString(frame), true)
              .whenComplete(
                  (ignored, error) -> {
                    if (error != null) retry();
                  });
      } catch (Exception exception) {
        retry();
      }
    }

    private synchronized void retry() {
      if (stopped) return;
      var previous = socket;
      socket = null;
      connecting = false;
      heartbeatAt = 0;
      buffer.setLength(0);
      state = "RECONNECTING";
      nextConnect =
          System.currentTimeMillis() + Math.min(60000, 5000L * (1L << Math.min(failures++, 4)));
      if (previous != null) previous.abort();
    }

    @Override
    public synchronized CompletionStage<?> onClose(WebSocket value, int code, String reason) {
      if (value != socket) return CompletableFuture.completedFuture(null);
      if (Set.of(4004, 4010, 4011, 4012, 4013, 4014).contains(code)) {
        state = "AUTH_OR_INTENT_REQUIRED";
        stopped = true;
        socket = null;
        connecting = false;
      } else {
        if (code == 4007 || code == 4009) {
          session = "";
          sequence = null;
        }
        retry();
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket value, Throwable error) {
      if (value == socket || socket == null) retry();
    }

    synchronized void close() {
      stopped = true;
      state = "CLOSED";
      var previous = socket;
      socket = null;
      if (previous != null) previous.abort();
    }
  }
}
