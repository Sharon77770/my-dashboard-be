package com.personal.dashboard.realtime;

import static org.assertj.core.api.Assertions.*;

import com.personal.dashboard.realtime.controller.WorkspaceChangeFilter;
import com.personal.dashboard.realtime.service.WorkspaceEvents;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class WorkspaceEventsTest {
  @Test
  void coalescesTopicsAndKeepsJobIdsInsideTheirOwnerSession() {
    var bus = new WorkspaceEvents();
    var first = new ArrayList<WorkspaceEvents.Frame>();
    var second = new ArrayList<WorkspaceEvents.Frame>();
    bus.subscribe("a", "owner-a", first::add);
    bus.subscribe("b", "owner-b", second::add);
    assertThat(first.getFirst().type()).isEqualTo("ready");
    bus.changed("notes");
    bus.changed("notes", "calendar");
    bus.jobChanged("owner-a", "private-job");
    bus.flush();
    assertThat(first.getLast().topics()).containsExactlyInAnyOrder("notes", "calendar");
    assertThat(first.getLast().jobs()).containsExactly("private-job");
    assertThat(second.getLast().jobs()).isEmpty();
    assertThat(second.getLast().revision()).isEqualTo(first.getLast().revision());
    assertThat(first.getLast().revision()).isGreaterThan(first.getFirst().revision());
    bus.flush();
    assertThat(first).hasSize(2);
    bus.unsubscribe("a");
    bus.changed("workspace");
    bus.flush();
    assertThat(first).hasSize(2);
  }

  @Test
  void emitsOnlySuccessfulChangesAndExcludesReadOnlyDatabaseTests() throws Exception {
    var bus = new WorkspaceEvents();
    var frames = new ArrayList<WorkspaceEvents.Frame>();
    bus.subscribe("a", "owner", frames::add);
    var filter = new WorkspaceChangeFilter(bus);
    for (String path :
        List.of(
            "/api/v1/databases/test",
            "/api/v1/databases/db/test",
            "/api/v1/databases/db/query",
            "/api/v1/devices/d/codex/jobs")) {
      filter.doFilter(
          new MockHttpServletRequest("POST", path),
          new MockHttpServletResponse(),
          (request, response) -> {});
      bus.flush();
    }
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/notes"),
        new MockHttpServletResponse(),
        (request, response) -> {});
    filter.doFilter(
        new MockHttpServletRequest("PUT", "/api/v1/notes/id"),
        new MockHttpServletResponse(),
        (request, response) ->
            ((jakarta.servlet.http.HttpServletResponse) response).setStatus(409));
    bus.flush();
    assertThat(frames).hasSize(1);
    filter.doFilter(
        new MockHttpServletRequest("PUT", "/api/v1/notes/id"),
        new MockHttpServletResponse(),
        (request, response) -> {});
    bus.flush();
    assertThat(frames.getLast().topics()).containsExactly("notes");
  }

  @Test
  void militaryWritesInvalidateBothCalendarViews() throws Exception {
    var bus = new WorkspaceEvents();
    var frames = new java.util.ArrayList<WorkspaceEvents.Frame>();
    bus.subscribe("a", "owner", frames::add);
    new WorkspaceChangeFilter(bus)
        .doFilter(
            new MockHttpServletRequest("PUT", "/api/v1/military/profile"),
            new MockHttpServletResponse(),
            (request, response) -> {});
    bus.flush();
    assertThat(frames.getLast().topics()).containsExactlyInAnyOrder("military", "calendar");
  }
}
