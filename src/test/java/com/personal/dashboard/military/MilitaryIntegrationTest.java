package com.personal.dashboard.military;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "DASHBOARD_AUTH_ID=military-test",
      "DASHBOARD_AUTH_PASSWORD=military-test-only",
      "workspace.root=./target/military-files",
      "workspace.key-path=./target/military.key",
      "DASHBOARD_DB_PATH=./target/military-test.db"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MilitaryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  JsonNode call(String method, String path, Object body, int expected) throws Exception {
    var request =
        request(HttpMethod.valueOf(method), "/api/v1" + path)
            .with(user("military-test").roles("OWNER"))
            .with(csrf());
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    var text =
        mvc.perform(request)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return text.isBlank() ? null : json.readTree(text);
  }

  Map<String, Object> profile() {
    return new HashMap<>(
        Map.of(
            "nickname",
            "My service",
            "serviceType",
            "ARMY",
            "enlistmentDate",
            "2026-01-01",
            "calendarEnabled",
            true,
            "leaveAllowance",
            30,
            "revision",
            0));
  }

  Map<String, Object> event(String start, String end) {
    return new HashMap<>(
        Map.of(
            "title", "Leave", "kind", "LEAVE", "startDate", start, "endDate", end, "revision", 0));
  }

  Map<String, Object> ordinary() {
    return Map.of(
        "title",
        "Personal event",
        "start",
        "2026-01-01T00:00:00",
        "end",
        "2026-01-02T00:00:00",
        "allDay",
        true,
        "color",
        "#7597eb");
  }

  @Test
  void requiresOwnerAndCsrfAndHasAnExplicitEmptyState() throws Exception {
    mvc.perform(get("/api/v1/military")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/military").with(user("viewer").roles("VIEWER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/military/profile")
                .with(user("owner").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(profile())))
        .andExpect(status().isForbidden());
    assertThat(call("GET", "/military", null, 200).path("profile").isNull()).isTrue();
    call("POST", "/military/events", event("2026-02-01", "2026-02-02"), 404);
  }

  @Test
  void projectsLinkedEventsWithoutCopiesAndDeletingProfilePreservesOrdinaryCalendar()
      throws Exception {
    var saved = call("PUT", "/military/profile", profile(), 200);
    assertThat(saved.path("profile").path("dischargeDate").asText()).isEqualTo("2027-06-30");
    assertThat(saved.path("profile").path("estimatedDischarge").asBoolean()).isTrue();
    assertThat(saved.path("timeZone").asText()).isEqualTo("Asia/Seoul");
    var personal = call("POST", "/calendar/events", ordinary(), 201);
    var leave = call("POST", "/military/events", event("2026-02-27", "2026-03-02"), 201);
    assertThat(leave.path("leaveDays").asInt()).isEqualTo(4);
    var projected = call("GET", "/calendar/events?from=2026-03-02&to=2026-03-03", null, 200);
    assertThat(projected).hasSize(1);
    assertThat(projected.get(0).path("source").asText()).isEqualTo("MILITARY");
    assertThat(projected.get(0).path("sourceId").asText()).isEqualTo(leave.path("id").asText());
    assertThat(projected.get(0).path("end").asText()).startsWith("2026-03-03T00:00");
    assertThat(call("GET", "/calendar/events?from=2026-03-03&to=2026-03-04", null, 200)).isEmpty();
    String linked = projected.get(0).path("id").asText();
    call("PUT", "/calendar/events/" + linked, ordinary(), 409);
    call("DELETE", "/calendar/events/" + linked, null, 409);
    var updated = profile();
    updated.put("revision", 1);
    updated.put("calendarEnabled", false);
    call("PUT", "/military/profile", updated, 200);
    assertThat(call("GET", "/calendar/events?from=2026-01-01&to=2026-04-01", null, 200)).hasSize(1);
    assertThat(call("GET", "/military", null, 200).path("events")).hasSize(1);
    call("DELETE", "/military/profile?revision=1", null, 409);
    call("DELETE", "/military/profile?revision=2", null, 204);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM military_events", Integer.class)).isZero();
    var remaining = call("GET", "/calendar/events?from=2026-01-01&to=2026-04-01", null, 200);
    assertThat(remaining.get(0).path("id")).isEqualTo(personal.path("id"));
    assertThat(call("GET", "/military", null, 200).path("profile").isNull()).isTrue();
  }

  @Test
  void protectsRevisionsLeaveOverlapAndProfileDateChanges() throws Exception {
    call("PUT", "/military/profile", profile(), 200);
    call("PUT", "/military/profile", profile(), 409);
    var input = event("2026-02-01", "2026-02-03");
    input.put("leaveDays", 2);
    var saved = call("POST", "/military/events", input, 201);
    String path = "/military/events/" + saved.path("id").asText();
    call("POST", "/military/events", event("2026-02-03", "2026-02-04"), 400);
    call("PUT", path, input, 409);
    input.put("revision", 1);
    input.put("title", "Adjusted leave");
    call("PUT", path, input, 200);
    call("DELETE", path + "?revision=1", null, 409);
    var shorten = profile();
    shorten.put("revision", 1);
    shorten.put("dischargeDate", "2026-01-31");
    call("PUT", "/military/profile", shorten, 400);
    assertThat(call("GET", "/military", null, 200).path("events").get(0).path("title").asText())
        .isEqualTo("Adjusted leave");
    call("DELETE", path + "?revision=2", null, 204);
    call("POST", "/military/events", event("2025-12-31", "2026-01-02"), 400);
    var invalid = event("2026-02-01", "2026-02-02");
    invalid.put("kind", "TRAINING");
    invalid.put("leaveDays", 1);
    call("POST", "/military/events", invalid, 400);
  }

  @Test
  void validatesPresetOverridesAndExplicitPromotionDates() throws Exception {
    var input = profile();
    input.put("serviceType", "CUSTOM");
    call("PUT", "/military/profile", input, 400);
    input.put("serviceType", "ARMY");
    input.put("enlistmentDate", "2019-01-01");
    call("PUT", "/military/profile", input, 400);
    input = profile();
    input.put("privateFirstDate", "2026-04-01");
    input.put("corporalDate", "2026-03-01");
    call("PUT", "/military/profile", input, 400);
    input.put("corporalDate", "2026-10-01");
    input.put("sergeantDate", "2027-04-01");
    var saved = call("PUT", "/military/profile", input, 200);
    assertThat(saved.path("milestones")).hasSize(5);
    input.put("revision", 1);
    input.put("serviceType", "SOCIAL_SERVICE");
    call("PUT", "/military/profile", input, 400);
    input.remove("privateFirstDate");
    input.remove("corporalDate");
    input.remove("sergeantDate");
    input.put("dischargeDate", "2027-02-01");
    saved = call("PUT", "/military/profile", input, 200);
    assertThat(saved.path("profile").path("estimatedDischarge").asBoolean()).isFalse();
    assertThat(saved.path("profile").path("dischargeDate").asText()).isEqualTo("2027-02-01");
  }

  @Test
  void leaveSummarySeparatesStartedAndPlannedActualDeductions() throws Exception {
    var today = java.time.LocalDate.now(com.personal.dashboard.military.domain.MilitaryDates.ZONE);
    var input = profile();
    input.put("serviceType", "CUSTOM");
    input.put("enlistmentDate", today.minusDays(10).toString());
    input.put("dischargeDate", today.plusDays(90).toString());
    input.put("leaveAllowance", 10);
    call("PUT", "/military/profile", input, 200);
    call(
        "POST",
        "/military/events",
        event(today.minusDays(2).toString(), today.minusDays(1).toString()),
        201);
    var planned = event(today.plusDays(3).toString(), today.plusDays(7).toString());
    planned.put("leaveDays", 1);
    call("POST", "/military/events", planned, 201);
    var leave = call("GET", "/military", null, 200).path("leave");
    assertThat(leave.path("used").asInt()).isEqualTo(2);
    assertThat(leave.path("planned").asInt()).isEqualTo(1);
    assertThat(leave.path("remaining").asInt()).isEqualTo(7);
  }
}
