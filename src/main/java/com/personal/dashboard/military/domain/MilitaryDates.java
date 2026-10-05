package com.personal.dashboard.military.domain;

import java.time.*;
import java.time.temporal.ChronoUnit;

/** Pure date calculations. A service day is a Seoul calendar day, including the final day. */
public final class MilitaryDates {
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  public enum Status {
    UPCOMING,
    SERVING,
    COMPLETED
  }

  public record Progress(
      Status status,
      long startsAt,
      long endsAt,
      long totalDays,
      long elapsedDays,
      long remainingDays,
      long serviceDay,
      long daysToDischarge,
      double percent,
      long nextDayAt) {}

  private MilitaryDates() {}

  /** Month-based estimate; a missing corresponding day ends on the destination month's last day. */
  public static LocalDate estimate(LocalDate start, int months) {
    if (months < 1) throw new IllegalArgumentException("Service duration must be positive");
    LocalDate corresponding = start.plusMonths(months);
    return corresponding.getDayOfMonth() == start.getDayOfMonth()
        ? corresponding.minusDays(1)
        : corresponding;
  }

  public static Progress progress(LocalDate start, LocalDate discharge, Instant now) {
    LocalDate today = now.atZone(ZONE).toLocalDate();
    long startsAt = start.atStartOfDay(ZONE).toInstant().toEpochMilli();
    long endsAt = discharge.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
    long total = ChronoUnit.DAYS.between(start, discharge) + 1;
    long elapsed = Math.clamp(ChronoUnit.DAYS.between(start, today), 0L, total);
    Status status =
        today.isBefore(start)
            ? Status.UPCOMING
            : today.isAfter(discharge) ? Status.COMPLETED : Status.SERVING;
    double percent =
        Math.clamp((now.toEpochMilli() - startsAt) * 100.0 / (endsAt - startsAt), 0.0, 100.0);
    return new Progress(
        status,
        startsAt,
        endsAt,
        total,
        elapsed,
        total - elapsed,
        status == Status.UPCOMING ? 0 : Math.min(total, elapsed + 1),
        ChronoUnit.DAYS.between(today, discharge),
        percent,
        today.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli());
  }
}
