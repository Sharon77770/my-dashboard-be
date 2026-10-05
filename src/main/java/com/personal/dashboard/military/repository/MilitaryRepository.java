package com.personal.dashboard.military.repository;

import com.personal.dashboard.military.domain.*;
import com.personal.dashboard.military.entity.MilitaryRecords.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized persistence and compare-and-swap revisions for one personal service profile. */
@Repository
public class MilitaryRepository {
  private final JdbcTemplate jdbc;

  public MilitaryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private LocalDate date(String value) {
    return value == null ? null : LocalDate.parse(value);
  }

  private String text(LocalDate value) {
    return value == null ? null : value.toString();
  }

  public Optional<ProfileRecord> profile() {
    return jdbc
        .query(
            "SELECT * FROM military_profile WHERE id=1",
            (r, i) ->
                new ProfileRecord(
                    r.getString("nickname"),
                    ServiceType.valueOf(r.getString("service_type")),
                    date(r.getString("enlistment_date")),
                    date(r.getString("discharge_override")),
                    date(r.getString("private_first_date")),
                    date(r.getString("corporal_date")),
                    date(r.getString("sergeant_date")),
                    (Integer) r.getObject("leave_allowance"),
                    r.getBoolean("calendar_enabled"),
                    r.getLong("revision")))
        .stream()
        .findFirst();
  }

  public int saveProfile(ProfileRecord profile, long expectedRevision) {
    return jdbc.update(
        "INSERT INTO military_profile(id,nickname,service_type,enlistment_date,discharge_override,private_first_date,corporal_date,sergeant_date,leave_allowance,calendar_enabled,revision) VALUES (1,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET nickname=excluded.nickname,service_type=excluded.service_type,enlistment_date=excluded.enlistment_date,discharge_override=excluded.discharge_override,private_first_date=excluded.private_first_date,corporal_date=excluded.corporal_date,sergeant_date=excluded.sergeant_date,leave_allowance=excluded.leave_allowance,calendar_enabled=excluded.calendar_enabled,revision=excluded.revision WHERE military_profile.revision=?",
        profile.nickname(),
        profile.serviceType().name(),
        text(profile.enlistmentDate()),
        text(profile.dischargeOverride()),
        text(profile.privateFirstDate()),
        text(profile.corporalDate()),
        text(profile.sergeantDate()),
        profile.leaveAllowance(),
        profile.calendarEnabled(),
        profile.revision(),
        expectedRevision);
  }

  public List<EventRecord> events() {
    return jdbc.query(
        "SELECT * FROM military_events ORDER BY start_date,id",
        (r, i) ->
            new EventRecord(
                r.getString("id"),
                MilitaryEventKind.valueOf(r.getString("kind")),
                r.getString("title"),
                date(r.getString("start_date")),
                date(r.getString("end_date")),
                r.getInt("leave_days"),
                r.getString("notes"),
                r.getLong("revision")));
  }

  public int saveEvent(EventRecord event, long expectedRevision) {
    return jdbc.update(
        "INSERT INTO military_events(id,kind,title,start_date,end_date,leave_days,notes,revision) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET kind=excluded.kind,title=excluded.title,start_date=excluded.start_date,end_date=excluded.end_date,leave_days=excluded.leave_days,notes=excluded.notes,revision=excluded.revision WHERE military_events.revision=?",
        event.id(),
        event.kind().name(),
        event.title(),
        text(event.startDate()),
        text(event.endDate()),
        event.leaveDays(),
        event.notes(),
        event.revision(),
        expectedRevision);
  }

  public int deleteProfile(long revision) {
    return jdbc.update("DELETE FROM military_profile WHERE id=1 AND revision=?", revision);
  }

  public int deleteEvent(String id, long revision) {
    return jdbc.update("DELETE FROM military_events WHERE id=? AND revision=?", id, revision);
  }
}
