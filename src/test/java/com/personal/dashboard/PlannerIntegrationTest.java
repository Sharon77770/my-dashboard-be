package com.personal.dashboard;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises persistent planner contracts, range boundaries, authorization and meeting conflicts.
 */
@SpringBootTest(
    properties = {
      "DASHBOARD_AUTH_ID=planner-test",
      "DASHBOARD_AUTH_PASSWORD=planner-test-only",
      "workspace.root=./target/planner-files",
      "workspace.key-path=./target/planner-key",
      "DASHBOARD_DB_PATH=./target/planner-test.db"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PlannerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  private JsonNode request(String method, String path, Object body, int status) throws Exception {
    var request =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), "/api/v1" + path)
            .with(user("planner-test").roles("OWNER"))
            .with(csrf());
    if (body != null)
      request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    String content =
        mvc.perform(request)
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return content.isBlank() ? null : mapper.readTree(content);
  }

  private Map<String, Object> event(String start, String end, boolean allDay) {
    return new HashMap<>(
        Map.of(
            "title",
            "기말 과제",
            "start",
            start,
            "end",
            end,
            "allDay",
            allDay,
            "color",
            "#7597eb",
            "notes",
            "제출 자료",
            "location",
            "도서관"));
  }

  private Map<String, Object> course(int day, String start, String end) {
    return new HashMap<>(
        Map.of(
            "title",
            "자료구조",
            "professor",
            "교수",
            "location",
            "공학관 201",
            "credits",
            3,
            "color",
            "#72b99a",
            "meetings",
            List.of(Map.of("day", day, "start", start, "end", end))));
  }

  private String term() throws Exception {
    return request(
            "POST",
            "/timetables",
            Map.of("name", "2026 2학기", "start", "2026-09-01", "end", "2026-12-31"),
            201)
        .get("id")
        .asText();
  }

  @Test
  void permissionsAndCsrfAreRequired() throws Exception {
    request("GET", "/calendar/events?from=not-a-date&to=2026-10-01", null, 400);
    request("GET", "/calendar/events", null, 400);
    mvc.perform(get("/api/v1/timetables")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/timetables").with(user("guest").roles("GUEST")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/timetables")
                .with(user("planner-test").roles("OWNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void calendarPersistsUpdatesAndUsesExclusiveEnd() throws Exception {
    var created =
        request(
            "POST", "/calendar/events", event("2026-10-01T00:00", "2026-10-03T00:00", true), 201);
    String id = created.get("id").asText();
    try {
      assertThat(
              request("GET", "/calendar/events?from=2026-10-02&to=2026-10-03", null, 200)
                  .toString())
          .contains(id);
      assertThat(
              request("GET", "/calendar/events?from=2026-10-03&to=2026-10-04", null, 200)
                  .toString())
          .doesNotContain(id);
      var update = event("2026-10-05T14:00", "2026-10-05T15:30", false);
      update.put("title", "수정된 일정");
      assertThat(request("PUT", "/calendar/events/" + id, update, 200).get("title").asText())
          .isEqualTo("수정된 일정");
      request(
          "POST", "/calendar/events", event("2026-10-01T15:00", "2026-10-01T14:00", false), 400);
      request("POST", "/calendar/events", event("2026-10-01T15:00", "2026-10-02T14:00", true), 400);
      request("GET", "/calendar/events?from=2026-10-02&to=2026-10-01", null, 400);
    } finally {
      request("DELETE", "/calendar/events/" + id, null, 204);
    }
    request(
        "PUT", "/calendar/events/" + id, event("2026-10-01T00:00", "2026-10-02T00:00", true), 404);
  }

  @Test
  void multipleMeetingsCreditsConflictAndCascade() throws Exception {
    String term = term();
    String base = "/timetables/" + term;
    try {
      var input = course(1, "09:00", "10:30");
      input.put(
          "meetings",
          List.of(
              Map.of("day", 1, "start", "09:00", "end", "10:30"),
              Map.of("day", 7, "start", "13:00", "end", "14:00")));
      String id = request("POST", base + "/courses", input, 201).get("id").asText();
      var view = request("GET", base, null, 200);
      assertThat(view.get("totalCredits").asInt()).isEqualTo(3);
      assertThat(view.get("courses").get(0).get("meetings").size()).isEqualTo(2);
      request("POST", base + "/courses", course(1, "10:00", "11:00"), 409);
      String next =
          request("POST", base + "/courses", course(1, "10:30", "11:30"), 201).get("id").asText();
      request("PUT", base + "/courses/" + next, course(7, "13:30", "14:30"), 409);
      request("PUT", base + "/courses/" + id, input, 200);
      request("DELETE", base + "/courses/" + next, null, 204);
      assertThat(request("GET", base, null, 200).get("totalCredits").asInt()).isEqualTo(3);
      request("POST", base + "/courses", course(8, "09:00", "10:00"), 400);
      request("POST", base + "/courses", course(2, "10:00", "09:00"), 400);
    } finally {
      request("DELETE", base, null, 204);
    }
    request("GET", base, null, 404);
  }

  @Test
  void semestersAreIsolatedAndOwnOverlapsRejected() throws Exception {
    String first = term(), second = term();
    try {
      var input = course(6, "09:00", "10:00");
      String id =
          request("POST", "/timetables/" + first + "/courses", input, 201).get("id").asText();
      request("POST", "/timetables/" + second + "/courses", input, 201);
      request("PUT", "/timetables/" + second + "/courses/" + id, input, 404);
      input.put(
          "meetings",
          List.of(
              Map.of("day", 2, "start", "09:00", "end", "11:00"),
              Map.of("day", 2, "start", "10:00", "end", "12:00")));
      request("POST", "/timetables/" + first + "/courses", input, 400);
    } finally {
      request("DELETE", "/timetables/" + first, null, 204);
      request("DELETE", "/timetables/" + second, null, 204);
    }
  }
}
