package com.personal.dashboard.planner.entity;

import java.time.*;
import java.util.List;

/** Persistence records, never serialized directly by the planner controller. */
public final class PlannerRecords {
  private PlannerRecords() {}

  public record EventRecord(
      String id,
      String title,
      LocalDateTime start,
      LocalDateTime end,
      boolean allDay,
      String location,
      String notes,
      String color) {}

  public record TermRecord(String id, String name, LocalDate start, LocalDate end) {}

  public record MeetingRecord(int day, LocalTime start, LocalTime end) {}

  public record CourseRecord(
      String id,
      String termId,
      String title,
      String professor,
      String location,
      int credits,
      String color,
      String notes,
      List<MeetingRecord> meetings) {}
}
