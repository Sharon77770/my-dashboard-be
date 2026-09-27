package com.personal.dashboard.studio;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.studio.adapter.StudioAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/** Covers the helper-to-HTTP projection that previously discarded MCP discovery fields. */
@JsonTest
class AssistantConnectionJsonTest {
  @Autowired ObjectMapper json;

  @Test
  void preservesMcpToolIdentityInStreamedEvents() throws Exception {
    var frame =
        json.readTree(
            """
        {"assistant":{"sequence":1,"kind":"item","item":{
          "id":"call-1","type":"mcpToolCall","status":"completed",
          "server":"personal-dashboard","tool":"list_apps"}}}
        """);
    var message = json.treeToValue(frame, StudioAdapter.Message.class);
    var item = json.valueToTree(message).path("assistant").path("item");
    assertThat(item.path("server").asText()).isEqualTo("personal-dashboard");
    assertThat(item.path("tool").asText()).isEqualTo("list_apps");
  }

  @ParameterizedTest
  @CsvSource({"connected,''", "NULL,''", "failed,Tool discovery failed"})
  void preservesMcpDiscoveryAcrossAdapterAndBrowserResponse(String state, String error)
      throws Exception {
    var connection = json.createObjectNode();
    connection.put("name", "personal-dashboard");
    connection.put("status", "bearerToken");
    if (state.equals("NULL")) connection.putNull("runtimeStatus");
    else connection.put("runtimeStatus", state);
    connection.putArray("tools").add("list_calendar_events");
    connection.put("error", error);
    var frame = json.createObjectNode();
    frame.putObject("result").putObject("assistant").putArray("connections").add(connection);

    var message = json.treeToValue(frame, StudioAdapter.Message.class);
    var response = json.valueToTree(message.result()).path("assistant").path("connections").get(0);

    assertThat(response.path("tools").path(0).asText()).isEqualTo("list_calendar_events");
    assertThat(response.path("runtimeStatus")).isEqualTo(connection.path("runtimeStatus"));
    assertThat(response.path("error").asText()).isEqualTo(error);
  }
}
