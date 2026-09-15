package com.personal.dashboard.planner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

/** Public planner contracts; local date-times use the user's displayed wall-clock time. */
public final class PlannerDto {
  private PlannerDto() {}

  public record EventRequest(
      @NotBlank @Size(max = 120) String title,
      @NotNull LocalDateTime start,
      @NotNull LocalDateTime end,
      boolean allDay,
      @Size(max = 200) String location,
      @Size(max = 4000) String notes,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String color) {}

  public record EventView(
      String id,
      String title,
      LocalDateTime start,
      LocalDateTime end,
      boolean allDay,
      String location,
      String notes,
      String color) {}

  public record TermRequest(
      @NotBlank @Size(max = 80) String name, @NotNull LocalDate start, @NotNull LocalDate end) {}

  public record TermView(String id, String name, LocalDate start, LocalDate end) {}

  public record MeetingRequest(
      @Min(1) @Max(7) int day, @NotNull LocalTime start, @NotNull LocalTime end) {}

  public record MeetingView(int day, LocalTime start, LocalTime end) {}

  public record CourseRequest(
      @NotBlank @Size(max = 120) String title,
      @Size(max = 120) String professor,
      @Size(max = 200) String location,
      @Min(0) @Max(30) int credits,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String color,
      @Size(max = 4000) String notes,
      @NotNull @Size(min = 1, max = 21) List<@NotNull @Valid MeetingRequest> meetings) {}

  public record CourseView(
      String id,
      String termId,
      String title,
      String professor,
      String location,
      int credits,
      String color,
      String notes,
      List<MeetingView> meetings) {}

  public record TimetableView(TermView term, List<CourseView> courses, int totalCredits) {}
}
