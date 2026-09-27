package com.personal.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies separated service-key ingestion and OWNER analytics contracts against SQLite. */
@SpringBootTest(
    properties = {
      "DASHBOARD_AUTH_ID=telemetry-test",
      "DASHBOARD_AUTH_PASSWORD=telemetry-test-only",
      "workspace.root=./target/telemetry-files",
      "workspace.key-path=./target/telemetry-key",
      "DASHBOARD_DB_PATH=./target/telemetry-test.db"
    })
@AutoConfigureMockMvc
class TelemetryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private JsonNode create() throws Exception {
    String body =
        json.writeValueAsString(
            Map.of(
                "name",
                "Fixture API",
                "description",
                "test fixture",
                "serviceType",
                "Backend API"));
    String response =
        mvc.perform(
                post("/api/v1/telemetry/services")
                    .with(user("telemetry-test").roles("OWNER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response);
  }

  @Test
  void serviceKeyIngestionAggregatesEventsUsersGaugesAndRotation() throws Exception {
    JsonNode service = create();
    String id = service.path("serviceId").asText(), key = service.path("apiKey").asText();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT api_key_hash FROM telemetry_services WHERE id=?", String.class, id))
        .isNotEqualTo(key);
    String batch =
        json.writeValueAsString(
            Map.of(
                "events",
                java.util.List.of(
                    Map.of(
                        "type",
                        "request",
                        "properties",
                        Map.of(
                            "endpoint",
                            "/health",
                            "method",
                            "GET",
                            "status",
                            200,
                            "latencyMs",
                            10)),
                    Map.of(
                        "type",
                        "request",
                        "properties",
                        Map.of(
                            "endpoint",
                            "/health",
                            "method",
                            "GET",
                            "status",
                            500,
                            "latencyMs",
                            30)),
                    Map.of("type", "user_activity", "anonymousUserId", "pseudonymous-fixture")),
                "gauges",
                java.util.List.of(Map.of("name", "active_users", "value", 4))));
    mvc.perform(
            post("/api/v1/telemetry/batch")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(batch))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events").value(3))
        .andExpect(jsonPath("$.gauges").value(1));
    String oldRequest =
        json.writeValueAsString(
            Map.of(
                "type", "request",
                "timestamp", java.time.Instant.now().minusSeconds(86_460).toString(),
                "properties",
                    Map.of("endpoint", "/old", "method", "GET", "status", 200, "latencyMs", 999)));
    mvc.perform(
            post("/api/v1/telemetry/events")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(oldRequest))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/telemetry/services/" + id + "/analytics")
                .with(user("telemetry-test").roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.requests").value(2))
        .andExpect(jsonPath("$.summary.requestsLastMinute").value(2))
        .andExpect(jsonPath("$.summary.requestsToday").value(2))
        .andExpect(jsonPath("$.summary.errors").value(1))
        .andExpect(jsonPath("$.summary.uniqueUsers").value(1))
        .andExpect(jsonPath("$.summary.peakConcurrentUsers").value(4))
        .andExpect(jsonPath("$.summary.gauges.active_users").value(4))
        .andExpect(jsonPath("$.summary.p50LatencyMs").value(10))
        .andExpect(jsonPath("$.summary.p95LatencyMs").value(30));
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT SUM(request_count) FROM service_metrics_daily WHERE service_id=?",
                Integer.class,
                id))
        .isEqualTo(3);
    mvc.perform(
            post("/api/v1/telemetry/events")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/telemetry/events")
                .header("Authorization", "Bearer dash_sk_invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    String replacement =
        mvc.perform(
                post("/api/v1/telemetry/services/" + id + "/key")
                    .with(user("telemetry-test").roles("OWNER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String newKey = json.readTree(replacement).path("apiKey").asText();
    mvc.perform(
            post("/api/v1/telemetry/events")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"request\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            delete("/api/v1/telemetry/services/" + id + "/key")
                .with(user("telemetry-test").roles("OWNER"))
                .with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/telemetry/events")
                .header("Authorization", "Bearer " + newKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"request\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ingestionIsPublicOnlyWithAValidServiceKeyAndRejectsOversizedRequests() throws Exception {
    mvc.perform(get("/api/v1/telemetry/services")).andExpect(status().isUnauthorized());
    JsonNode service = create();
    String key = service.path("apiKey").asText();
    mvc.perform(
            post("/api/v1/telemetry/events")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(" ".repeat(65_537)))
        .andExpect(status().isPayloadTooLarge());
  }
}
