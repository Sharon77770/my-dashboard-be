package com.personal.dashboard.planner.service;

import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.military.service.MilitaryService;
import com.personal.dashboard.planner.dto.PlannerDto.*;
import com.personal.dashboard.planner.entity.PlannerRecords.*;
import com.personal.dashboard.planner.repository.PlannerRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns event interval validation and semester-local course conflict rules. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class PlannerService {
  private final PlannerRepository repository;
  private final MilitaryService military;

  public PlannerService(PlannerRepository repository, MilitaryService military) {
    this.repository = repository;
    this.military = military;
  }

  private String text(String value) {
    return value == null ? "" : value.trim();
  }

  private void require(boolean valid, String message) {
    if (!valid) throw new WorkspaceException(400, message);
  }

  private void found(boolean exists) {
    if (!exists) throw new WorkspaceException(404, "일정 또는 시간표를 찾을 수 없습니다.");
  }

  public List<EventView> events(LocalDate from, LocalDate to) {
    require(to.isAfter(from) && ChronoUnit.DAYS.between(from, to) <= 366, "조회 기간은 1~366일이어야 합니다.");
    return java.util.stream.Stream.concat(
            repository.events(from.atStartOfDay(), to.atStartOfDay()).stream().map(this::view),
            military.calendarEvents(from, to).stream())
        .sorted(Comparator.comparing(EventView::start).thenComparing(EventView::id))
        .toList();
  }

  @Transactional
  public EventView saveEvent(String id, EventRequest input) {
    militaryEventGuard(id);
    if (id != null) found(repository.eventExists(id));
    require(input.end().isAfter(input.start()), "종료는 시작 이후여야 합니다.");
    require(
        input.start().getYear() >= 1900 && input.end().getYear() <= 2200,
        "일정 연도는 1900~2200 범위여야 합니다.");
    require(
        input.start().getSecond() == 0
            && input.start().getNano() == 0
            && input.end().getSecond() == 0
            && input.end().getNano() == 0,
        "시간은 분 단위로 입력해 주세요.");
    require(
        !input.allDay()
            || (input.start().toLocalTime().equals(LocalTime.MIDNIGHT)
                && input.end().toLocalTime().equals(LocalTime.MIDNIGHT)),
        "종일 일정은 날짜 경계로 입력해 주세요.");
    var event =
        new EventRecord(
            id == null ? UUID.randomUUID().toString() : id,
            text(input.title()),
            input.start(),
            input.end(),
            input.allDay(),
            text(input.location()),
            text(input.notes()),
            input.color());
    repository.saveEvent(event);
    return view(event);
  }

  private EventView view(EventRecord event) {
    return new EventView(
        event.id(),
        event.title(),
        event.start(),
        event.end(),
        event.allDay(),
        event.location(),
        event.notes(),
        event.color(),
        "CALENDAR",
        null);
  }

  public List<TermView> terms() {
    return repository.terms().stream().map(this::view).toList();
  }

  private TermView view(TermRecord term) {
    return new TermView(term.id(), term.name(), term.start(), term.end());
  }

  private TermRecord term(String id) {
    return repository.terms().stream()
        .filter(t -> t.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new WorkspaceException(404, "시간표를 찾을 수 없습니다."));
  }

  @Transactional
  public TermView saveTerm(String id, TermRequest input) {
    if (id != null) term(id);
    require(
        !input.end().isBefore(input.start())
            && ChronoUnit.DAYS.between(input.start(), input.end()) <= 366,
        "학기 기간은 시작일부터 최대 366일입니다.");
    require(
        input.start().getYear() >= 1900 && input.end().getYear() <= 2200,
        "학기 연도는 1900~2200 범위여야 합니다.");
    var saved =
        new TermRecord(
            id == null ? UUID.randomUUID().toString() : id,
            text(input.name()),
            input.start(),
            input.end());
    repository.saveTerm(saved);
    return view(saved);
  }

  public TimetableView timetable(String id) {
    var term = term(id);
    var courses = repository.courses(id);
    return new TimetableView(
        view(term),
        courses.stream().map(this::view).toList(),
        courses.stream().mapToInt(CourseRecord::credits).sum());
  }

  private CourseView view(CourseRecord course) {
    return new CourseView(
        course.id(),
        course.termId(),
        course.title(),
        course.professor(),
        course.location(),
        course.credits(),
        course.color(),
        course.notes(),
        course.meetings().stream().map(m -> new MeetingView(m.day(), m.start(), m.end())).toList());
  }

  @Transactional
  public CourseView saveCourse(String termId, String id, CourseRequest input) {
    term(termId);
    var existing = repository.courses(termId);
    if (id != null) found(existing.stream().anyMatch(c -> c.id().equals(id)));
    var meetings =
        input.meetings().stream().map(m -> new MeetingRecord(m.day(), m.start(), m.end())).toList();
    for (int index = 0; index < meetings.size(); index++) {
      var meeting = meetings.get(index);
      require(
          meeting.end().isAfter(meeting.start()), "수업 종료는 시작 이후여야 합니다. 자정을 넘는 수업은 나누어 입력해 주세요.");
      require(
          meeting.start().getSecond() == 0
              && meeting.start().getNano() == 0
              && meeting.end().getSecond() == 0
              && meeting.end().getNano() == 0,
          "수업 시간은 분 단위로 입력해 주세요.");
      for (int other = 0; other < index; other++)
        require(!overlaps(meeting, meetings.get(other)), "입력한 수업 시간끼리 겹칩니다.");
      for (var course : existing)
        if (!course.id().equals(id))
          for (var slot : course.meetings())
            if (overlaps(meeting, slot))
              throw new WorkspaceException(409, "기존 수업과 시간이 겹칩니다: " + course.title());
    }
    var course =
        new CourseRecord(
            id == null ? UUID.randomUUID().toString() : id,
            termId,
            text(input.title()),
            text(input.professor()),
            text(input.location()),
            input.credits(),
            input.color(),
            text(input.notes()),
            meetings);
    repository.saveCourse(course);
    return view(course);
  }

  private boolean overlaps(MeetingRecord left, MeetingRecord right) {
    return left.day() == right.day()
        && left.start().isBefore(right.end())
        && right.start().isBefore(left.end());
  }

  @Transactional
  public void deleteEvent(String id) {
    militaryEventGuard(id);
    found(repository.deleteEvent(id) > 0);
  }

  private void militaryEventGuard(String id) {
    if (id != null && id.startsWith("military:"))
      throw new WorkspaceException(409, "병역 일정은 병역 캘린더에서 수정·삭제해 주세요.");
  }

  @Transactional
  public void deleteTerm(String id) {
    found(repository.deleteTerm(id) > 0);
  }

  @Transactional
  public void deleteCourse(String termId, String id) {
    found(repository.deleteCourse(termId, id) > 0);
  }
}
