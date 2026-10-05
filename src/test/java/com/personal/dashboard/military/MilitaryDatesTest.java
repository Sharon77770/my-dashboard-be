package com.personal.dashboard.military;

import static org.assertj.core.api.Assertions.*;

import com.personal.dashboard.military.domain.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class MilitaryDatesTest {
  @Test
  void presetsUseCalendarMonthsAndIncludeTheEntryDay() {
    LocalDate start = LocalDate.of(2026, 1, 1);
    assertThat(MilitaryDates.estimate(start, ServiceType.ARMY.months()))
        .isEqualTo(LocalDate.of(2027, 6, 30));
    assertThat(MilitaryDates.estimate(start, ServiceType.MARINES.months()))
        .isEqualTo(LocalDate.of(2027, 6, 30));
    assertThat(MilitaryDates.estimate(start, ServiceType.NAVY.months()))
        .isEqualTo(LocalDate.of(2027, 8, 31));
    assertThat(MilitaryDates.estimate(start, ServiceType.AIR_FORCE.months()))
        .isEqualTo(LocalDate.of(2027, 9, 30));
    assertThat(MilitaryDates.estimate(start, ServiceType.SOCIAL_SERVICE.months()))
        .isEqualTo(LocalDate.of(2027, 9, 30));
  }

  @Test
  void monthEndAndLeapDayDoNotDropAnAdditionalDay() {
    assertThat(MilitaryDates.estimate(LocalDate.of(2023, 8, 31), 6))
        .isEqualTo(LocalDate.of(2024, 2, 29));
    assertThat(MilitaryDates.estimate(LocalDate.of(2024, 8, 31), 18))
        .isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(MilitaryDates.estimate(LocalDate.of(2024, 2, 29), 12))
        .isEqualTo(LocalDate.of(2025, 2, 28));
    assertThat(MilitaryDates.estimate(LocalDate.of(2026, 3, 5), 18))
        .isEqualTo(LocalDate.of(2027, 9, 4));
  }

  @Test
  void seoulMidnightAndInclusiveDischargeDayDefineProgress() {
    LocalDate day = LocalDate.of(2026, 10, 5);
    var before = MilitaryDates.progress(day, day, Instant.parse("2026-10-04T14:59:59Z"));
    assertThat(before.status()).isEqualTo(MilitaryDates.Status.UPCOMING);
    assertThat(before.percent()).isZero();
    assertThat(before.serviceDay()).isZero();
    var start = MilitaryDates.progress(day, day, Instant.parse("2026-10-04T15:00:00Z"));
    assertThat(start.status()).isEqualTo(MilitaryDates.Status.SERVING);
    assertThat(start.serviceDay()).isEqualTo(1);
    assertThat(start.daysToDischarge()).isZero();
    assertThat(start.totalDays()).isEqualTo(1);
    var noon = MilitaryDates.progress(day, day, Instant.parse("2026-10-05T03:00:00Z"));
    assertThat(noon.percent()).isEqualTo(50);
    var after = MilitaryDates.progress(day, day, Instant.parse("2026-10-05T15:00:00Z"));
    assertThat(after.status()).isEqualTo(MilitaryDates.Status.COMPLETED);
    assertThat(after.percent()).isEqualTo(100);
    assertThat(after.remainingDays()).isZero();
    assertThat(after.elapsedDays()).isEqualTo(1);
  }
}
