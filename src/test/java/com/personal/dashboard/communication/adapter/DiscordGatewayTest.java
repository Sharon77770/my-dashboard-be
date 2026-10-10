package com.personal.dashboard.communication.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Exercises received Gateway frames and outgoing Identify/Resume without a live bot connection. */
class DiscordGatewayTest {
  @Test
  void identifiesHeartbeatsAndResumesWithLastSequence() throws Exception {
    var json = new ObjectMapper();
    var adapter = new DiscordGatewayAdapter(json, mock(ProviderHttpClient.class));
    List<String> received = new ArrayList<>();
    var connection =
        adapter
        .new Connection(
            "fixture-bot", (type, data) -> received.add(type + ":" + data.path("id").asText()));
    WebSocket first = mock(WebSocket.class);
    when(first.sendText(anyString(), eq(true)))
        .thenReturn(CompletableFuture.completedFuture(first));
    connection.onOpen(first);
    connection.onText(first, "{\"op\":10,\"d\":{\"heartbeat_interval\":45000}}", true);
    var frame = org.mockito.ArgumentCaptor.forClass(CharSequence.class);
    verify(first).sendText(frame.capture(), eq(true));
    assertThat(json.readTree(frame.getValue().toString()).path("op").asInt()).isEqualTo(2);
    connection.onText(
        first,
        "{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"session_id\":\"fixture-session\",\"resume_gateway_url\":\"wss://gateway.discord.gg\"}}",
        true);
    connection.onText(
        first,
        "{\"op\":0,\"s\":2,\"t\":\"MESSAGE_CREATE\",\"d\":{\"id\":\"m1\",\"channel_id\":\"c1\"}}",
        true);
    assertThat(received).containsExactly("MESSAGE_CREATE:m1");
    connection.onText(first, "{\"op\":1}", true);
    connection.onText(first, "{\"op\":11}", true);
    connection.onClose(first, 1000, "");
    WebSocket second = mock(WebSocket.class);
    when(second.sendText(anyString(), eq(true)))
        .thenReturn(CompletableFuture.completedFuture(second));
    connection.onOpen(second);
    connection.onText(second, "{\"op\":10,\"d\":{\"heartbeat_interval\":45000}}", true);
    verify(second).sendText(frame.capture(), eq(true));
    var resume = json.readTree(frame.getValue().toString());
    assertThat(resume.path("op").asInt()).isEqualTo(6);
    assertThat(resume.path("d").path("seq").asInt()).isEqualTo(2);
    connection.onClose(second, 4014, "");
    WebSocket blocked = mock(WebSocket.class);
    connection.onOpen(blocked);
    verify(blocked).abort();
    connection.close();
  }

  @Test
  void refusesUntrustedResumeHost() {
    var adapter = new DiscordGatewayAdapter(new ObjectMapper(), mock(ProviderHttpClient.class));
    var connection = adapter.new Connection("fixture", (type, data) -> {});
    WebSocket socket = mock(WebSocket.class);
    connection.onOpen(socket);
    connection.onText(
        socket,
        "{\"op\":0,\"t\":\"READY\",\"d\":{\"session_id\":\"session\",\"resume_gateway_url\":\"wss://evil.example\"}}",
        true);
    verify(socket).abort();
    connection.close();
  }
}
