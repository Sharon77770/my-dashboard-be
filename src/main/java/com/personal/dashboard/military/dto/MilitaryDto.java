package com.personal.dashboard.military.dto;

import com.personal.dashboard.military.domain.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

/** OWNER-only military calendar input and response contracts. */
public final class MilitaryDto {
  private MilitaryDto() {}

  public record ProfileRequest(
      @NotBlank @Size(max = 60) String nickname,
      @NotNull ServiceType serviceType,
      @NotNull LocalDate enlistmentDate,
      LocalDate dischargeDate,
      LocalDate privateFirstDate,
      LocalDate corporalDate,
      LocalDate sergeantDate,
      @Min(0) @Max(1000) Integer leaveAllowance,
      boolean calendarEnabled,
      @Min(0) long revision) {}

  public record ProfileView(
      String nickname,
      ServiceType serviceType,
      LocalDate enlistmentDate,
      LocalDate dischargeDate,
      boolean estimatedDischarge,
      LocalDate privateFirstDate,
      LocalDate corporalDate,
      LocalDate sergeantDate,
      Integer leaveAllowance,
      boolean calendarEnabled,
      long revision) {}

  public record EventRequest(
      @NotNull MilitaryEventKind kind,
      @NotBlank @Size(max = 120) String title,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate,
      @Min(0) @Max(366) Integer leaveDays,
      @Size(max = 2000) String notes,
      @Min(0) long revision) {}

  public record EventView(
      String id,
      MilitaryEventKind kind,
      String title,
      LocalDate startDate,
      LocalDate endDate,
      int leaveDays,
      String notes,
      long revision) {}

  public record Milestone(
      String id, String title, String kind, LocalDate date, long daysUntil, boolean reached) {}

  public record LeaveSummary(Integer allowance, int used, int planned, Integer remaining) {}

  public record ServiceOption(ServiceType id, String label, Integer months) {}

  public record Source(String title, String url, LocalDate checkedOn) {}

  public record ProgressView(
      MilitaryDates.Status status,
      long startsAt,
      long endsAt,
      long totalDays,
      long elapsedDays,
      long remainingDays,
      long serviceDay,
      long daysToDischarge,
      double percent,
      long nextDayAt) {
    public ProgressView(MilitaryDates.Progress value) {
      this(
          value.status(),
          value.startsAt(),
          value.endsAt(),
          value.totalDays(),
          value.elapsedDays(),
          value.remainingDays(),
          value.serviceDay(),
          value.daysToDischarge(),
          value.percent(),
          value.nextDayAt());
    }
  }

  public record Dashboard(
      ProfileView profile,
      ProgressView progress,
      String currentRank,
      List<Milestone> milestones,
      List<EventView> events,
      LeaveSummary leave,
      List<ServiceOption> serviceTypes,
      List<Source> sources,
      Instant serverNow,
      String timeZone) {}
}
