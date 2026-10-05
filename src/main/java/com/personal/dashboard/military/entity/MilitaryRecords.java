package com.personal.dashboard.military.entity;

import com.personal.dashboard.military.domain.*;
import java.time.LocalDate;

/** SQLite-only records, never serialized directly by controllers. */
public final class MilitaryRecords {
  private MilitaryRecords() {}

  public record ProfileRecord(
      String nickname,
      ServiceType serviceType,
      LocalDate enlistmentDate,
      LocalDate dischargeOverride,
      LocalDate privateFirstDate,
      LocalDate corporalDate,
      LocalDate sergeantDate,
      Integer leaveAllowance,
      boolean calendarEnabled,
      long revision) {}

  public record EventRecord(
      String id,
      MilitaryEventKind kind,
      String title,
      LocalDate startDate,
      LocalDate endDate,
      int leaveDays,
      String notes,
      long revision) {}
}
