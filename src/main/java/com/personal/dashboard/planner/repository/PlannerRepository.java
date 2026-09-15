package com.personal.dashboard.planner.repository;

import com.personal.dashboard.planner.entity.PlannerRecords.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Parameterized SQLite persistence for independent events, semesters, courses and meeting slots.
 */
@Repository
public class PlannerRepository {
  private final JdbcTemplate jdbc;

  public PlannerRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<EventRecord> events(LocalDateTime from, LocalDateTime to) {
    return jdbc.query(
        "SELECT * FROM calendar_events WHERE starts_at < ? AND ends_at > ? ORDER BY starts_at,id",
        (r, i) ->
            new EventRecord(
                r.getString("id"),
                r.getString("title"),
                LocalDateTime.parse(r.getString("starts_at")),
                LocalDateTime.parse(r.getString("ends_at")),
                r.getBoolean("all_day"),
                r.getString("location"),
                r.getString("notes"),
                r.getString("color")),
        to.toString(),
        from.toString());
  }

  public boolean eventExists(String id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM calendar_events WHERE id=?", Integer.class, id)
        > 0;
  }

  public void saveEvent(EventRecord event) {
    jdbc.update(
        "INSERT INTO calendar_events VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title=excluded.title,starts_at=excluded.starts_at,ends_at=excluded.ends_at,all_day=excluded.all_day,location=excluded.location,notes=excluded.notes,color=excluded.color",
        event.id(),
        event.title(),
        event.start().toString(),
        event.end().toString(),
        event.allDay(),
        event.location(),
        event.notes(),
        event.color());
  }

  public int deleteEvent(String id) {
    return jdbc.update("DELETE FROM calendar_events WHERE id=?", id);
  }

  public List<TermRecord> terms() {
    return jdbc.query(
        "SELECT * FROM timetable_terms ORDER BY starts_on DESC,id",
        (r, i) ->
            new TermRecord(
                r.getString("id"),
                r.getString("name"),
                LocalDate.parse(r.getString("starts_on")),
                LocalDate.parse(r.getString("ends_on"))));
  }

  public void saveTerm(TermRecord term) {
    jdbc.update(
        "INSERT INTO timetable_terms VALUES (?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,starts_on=excluded.starts_on,ends_on=excluded.ends_on",
        term.id(),
        term.name(),
        term.start().toString(),
        term.end().toString());
  }

  public int deleteTerm(String id) {
    return jdbc.update("DELETE FROM timetable_terms WHERE id=?", id);
  }

  public List<CourseRecord> courses(String termId) {
    var courses =
        jdbc.query(
            "SELECT * FROM timetable_courses WHERE term_id=? ORDER BY title,id",
            (r, i) ->
                new CourseRecord(
                    r.getString("id"),
                    r.getString("term_id"),
                    r.getString("title"),
                    r.getString("professor"),
                    r.getString("location"),
                    r.getInt("credits"),
                    r.getString("color"),
                    r.getString("notes"),
                    List.of()),
            termId);
    return courses.stream()
        .map(
            c ->
                new CourseRecord(
                    c.id(),
                    c.termId(),
                    c.title(),
                    c.professor(),
                    c.location(),
                    c.credits(),
                    c.color(),
                    c.notes(),
                    jdbc.query(
                        "SELECT * FROM timetable_meetings WHERE course_id=? ORDER BY day,starts_at",
                        (r, i) ->
                            new MeetingRecord(
                                r.getInt("day"),
                                LocalTime.parse(r.getString("starts_at")),
                                LocalTime.parse(r.getString("ends_at"))),
                        c.id())))
        .toList();
  }

  public void saveCourse(CourseRecord course) {
    jdbc.update(
        "INSERT INTO timetable_courses VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title=excluded.title,professor=excluded.professor,location=excluded.location,credits=excluded.credits,color=excluded.color,notes=excluded.notes",
        course.id(),
        course.termId(),
        course.title(),
        course.professor(),
        course.location(),
        course.credits(),
        course.color(),
        course.notes());
    jdbc.update("DELETE FROM timetable_meetings WHERE course_id=?", course.id());
    for (var meeting : course.meetings())
      jdbc.update(
          "INSERT INTO timetable_meetings(course_id,day,starts_at,ends_at) VALUES (?,?,?,?)",
          course.id(),
          meeting.day(),
          meeting.start().toString(),
          meeting.end().toString());
  }

  public int deleteCourse(String termId, String id) {
    return jdbc.update("DELETE FROM timetable_courses WHERE term_id=? AND id=?", termId, id);
  }
}
