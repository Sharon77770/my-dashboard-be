package com.personal.dashboard.military.service;

import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.military.domain.*;
import com.personal.dashboard.military.dto.MilitaryDto.*;
import com.personal.dashboard.military.entity.MilitaryRecords.*;
import com.personal.dashboard.military.repository.MilitaryRepository;
import com.personal.dashboard.planner.dto.PlannerDto;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personal service timeline, revision checks and calendar projection share one domain boundary. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class MilitaryService {
  private final MilitaryRepository repository;
  private static final List<Source> SOURCES =
      List.of(
          new Source(
              "병무청 · 현역병 복무기간",
              "https://www.mma.go.kr/board/boardView.do?gesipan_id=317&gsgeul_no=1501414&mc=mma0002306",
              LocalDate.of(2026, 10, 5)),
          new Source(
              "병무청 · 사회복무요원 소집제도",
              "https://www.mma.go.kr/contents.do?mc=mma0000744&num=1",
              LocalDate.of(2026, 10, 5)),
          new Source(
              "군돌이 · 기능 참고",
              "https://play.google.com/store/apps/details?hl=ko&id=com.goondori",
              LocalDate.of(2026, 10, 5)));

  public MilitaryService(MilitaryRepository repository) {
    this.repository = repository;
  }

  private void require(boolean valid, String message) {
    if (!valid) throw new WorkspaceException(400, message);
  }

  private void revision(boolean valid) {
    if (!valid) throw new WorkspaceException(409, "다른 곳에서 변경되었습니다. 최신 내용을 확인한 뒤 다시 저장해 주세요.");
  }

  private ProfileRecord profile() {
    return repository
        .profile()
        .orElseThrow(() -> new WorkspaceException(404, "복무 정보를 먼저 등록해 주세요."));
  }

  private LocalDate discharge(ProfileRecord profile) {
    return profile.dischargeOverride() != null
        ? profile.dischargeOverride()
        : MilitaryDates.estimate(profile.enlistmentDate(), profile.serviceType().months());
  }

  private ProfileView view(ProfileRecord profile) {
    return new ProfileView(
        profile.nickname(),
        profile.serviceType(),
        profile.enlistmentDate(),
        discharge(profile),
        profile.dischargeOverride() == null,
        profile.privateFirstDate(),
        profile.corporalDate(),
        profile.sergeantDate(),
        profile.leaveAllowance(),
        profile.calendarEnabled(),
        profile.revision());
  }

  private EventView view(EventRecord event) {
    return new EventView(
        event.id(),
        event.kind(),
        event.title(),
        event.startDate(),
        event.endDate(),
        event.leaveDays(),
        event.notes(),
        event.revision());
  }

  @Transactional(readOnly = true)
  public Dashboard dashboard() {
    Instant now = Instant.now();
    LocalDate today = now.atZone(MilitaryDates.ZONE).toLocalDate();
    var profile = repository.profile().orElse(null);
    var options =
        Arrays.stream(ServiceType.values())
            .map(
                type ->
                    new ServiceOption(
                        type, type.label(), type.months() == 0 ? null : type.months()))
            .toList();
    if (profile == null)
      return new Dashboard(
          null,
          null,
          null,
          List.of(),
          List.of(),
          new LeaveSummary(null, 0, 0, null),
          options,
          SOURCES,
          now,
          MilitaryDates.ZONE.getId());
    var events = repository.events();
    int used =
        events.stream()
            .filter(event -> !event.startDate().isAfter(today))
            .mapToInt(EventRecord::leaveDays)
            .sum();
    int planned =
        events.stream()
            .filter(event -> event.startDate().isAfter(today))
            .mapToInt(EventRecord::leaveDays)
            .sum();
    return new Dashboard(
        view(profile),
        new ProgressView(MilitaryDates.progress(profile.enlistmentDate(), discharge(profile), now)),
        rank(profile, today),
        milestones(profile, today),
        events.stream().map(this::view).toList(),
        new LeaveSummary(
            profile.leaveAllowance(),
            used,
            planned,
            profile.leaveAllowance() == null ? null : profile.leaveAllowance() - used - planned),
        options,
        SOURCES,
        now,
        MilitaryDates.ZONE.getId());
  }

  /** Profile changes never silently truncate previously recorded leave or training. */
  @Transactional
  public Dashboard saveProfile(ProfileRequest input) {
    var previous = repository.profile();
    revision(
        previous.map(value -> value.revision() == input.revision()).orElse(input.revision() == 0));
    require(
        input.enlistmentDate().getYear() >= 1900 && input.enlistmentDate().getYear() <= 2199,
        "입대 연도는 1900~2199년 사이로 입력해 주세요.");
    require(
        input.dischargeDate() != null || input.serviceType() != ServiceType.CUSTOM,
        "직접 설정은 전역·소집해제일을 입력해 주세요.");
    require(
        input.dischargeDate() != null || !input.enlistmentDate().isBefore(LocalDate.of(2022, 1, 1)),
        "2022년 이전 복무는 실제 전역·소집해제일을 입력해 주세요.");
    var next =
        new ProfileRecord(
            input.nickname().trim(),
            input.serviceType(),
            input.enlistmentDate(),
            input.dischargeDate(),
            input.privateFirstDate(),
            input.corporalDate(),
            input.sergeantDate(),
            input.leaveAllowance(),
            input.calendarEnabled(),
            input.revision() + 1);
    LocalDate end = discharge(next);
    require(
        next.enlistmentDate().getYear() >= 1900
            && end.getYear() <= 2199
            && !end.isBefore(next.enlistmentDate())
            && ChronoUnit.DAYS.between(next.enlistmentDate(), end) <= 14610,
        "복무기간은 1900~2199년 사이, 입대일부터 최대 40년 이내로 입력해 주세요.");
    validatePromotions(next, end);
    for (var event : repository.events())
      require(
          !event.startDate().isBefore(next.enlistmentDate()) && !event.endDate().isAfter(end),
          "변경할 복무기간 밖에 기존 일정이 있습니다. 해당 일정을 먼저 수정해 주세요.");
    revision(repository.saveProfile(next, input.revision()) == 1);
    return dashboard();
  }

  private void validatePromotions(ProfileRecord profile, LocalDate end) {
    LocalDate previous = profile.enlistmentDate();
    for (LocalDate date :
        Arrays.asList(profile.privateFirstDate(), profile.corporalDate(), profile.sergeantDate())) {
      if (date == null) continue;
      require(profile.serviceType().soldier(), "일병·상병·병장 진급일은 현역병 복무 유형에만 입력할 수 있습니다.");
      require(date.isAfter(previous) && !date.isAfter(end), "진급일은 입대 후부터 전역일까지, 계급 순서대로 입력해 주세요.");
      previous = date;
    }
  }

  private String rank(ProfileRecord profile, LocalDate today) {
    if (today.isBefore(profile.enlistmentDate())) return "입대 전";
    if (today.isAfter(discharge(profile))) return "복무 완료";
    if (!profile.serviceType().soldier())
      return profile.serviceType() == ServiceType.SOCIAL_SERVICE ? "사회복무요원" : "복무 중";
    if (profile.sergeantDate() != null && !today.isBefore(profile.sergeantDate())) return "병장";
    if (profile.corporalDate() != null && !today.isBefore(profile.corporalDate())) return "상병";
    if (profile.privateFirstDate() != null)
      return today.isBefore(profile.privateFirstDate()) ? "이병" : "일병";
    return "진급일 미등록";
  }

  private List<Milestone> milestones(ProfileRecord profile, LocalDate today) {
    var values = new ArrayList<Milestone>();
    values.add(milestone("enlistment", "입대·소집", "ENLISTMENT", profile.enlistmentDate(), today));
    if (profile.privateFirstDate() != null)
      values.add(
          milestone("private-first", "일병 진급", "PROMOTION", profile.privateFirstDate(), today));
    if (profile.corporalDate() != null)
      values.add(milestone("corporal", "상병 진급", "PROMOTION", profile.corporalDate(), today));
    if (profile.sergeantDate() != null)
      values.add(milestone("sergeant", "병장 진급", "PROMOTION", profile.sergeantDate(), today));
    values.add(
        milestone(
            "discharge",
            profile.serviceType() == ServiceType.SOCIAL_SERVICE ? "소집해제" : "전역",
            "DISCHARGE",
            discharge(profile),
            today));
    return List.copyOf(values);
  }

  private Milestone milestone(
      String id, String title, String kind, LocalDate date, LocalDate today) {
    return new Milestone(
        id, title, kind, date, ChronoUnit.DAYS.between(today, date), !today.isBefore(date));
  }

  @Transactional
  public EventView saveEvent(String id, EventRequest input) {
    var profile = profile();
    var events = repository.events();
    EventRecord previous =
        id == null
            ? null
            : events.stream()
                .filter(event -> event.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new WorkspaceException(404, "병역 일정을 찾을 수 없습니다."));
    revision(previous == null ? input.revision() == 0 : input.revision() == previous.revision());
    require(previous != null || events.size() < 1000, "병역 일정은 최대 1,000개까지 등록할 수 있습니다.");
    require(
        !input.endDate().isBefore(input.startDate())
            && !input.startDate().isBefore(profile.enlistmentDate())
            && !input.endDate().isAfter(discharge(profile)),
        "일정은 복무기간 안에서 종료일이 시작일보다 빠르지 않게 입력해 주세요.");
    long days = ChronoUnit.DAYS.between(input.startDate(), input.endDate()) + 1;
    require(days <= 366, "일정 하나의 기간은 최대 366일입니다.");
    int leaveDays =
        input.leaveDays() == null
            ? (input.kind() == MilitaryEventKind.LEAVE ? (int) days : 0)
            : input.leaveDays();
    require(
        leaveDays >= 0
            && leaveDays <= days
            && (input.kind() == MilitaryEventKind.LEAVE || leaveDays == 0),
        "휴가 차감일수는 휴가 기간 이내이며 휴가 일정에만 입력할 수 있습니다.");
    if (input.kind() == MilitaryEventKind.LEAVE)
      require(
          events.stream()
              .noneMatch(
                  event ->
                      !event.id().equals(id)
                          && event.kind() == MilitaryEventKind.LEAVE
                          && !event.endDate().isBefore(input.startDate())
                          && !event.startDate().isAfter(input.endDate())),
          "이미 등록한 휴가와 날짜가 겹칩니다.");
    var next =
        new EventRecord(
            id == null ? UUID.randomUUID().toString() : id,
            input.kind(),
            input.title().trim(),
            input.startDate(),
            input.endDate(),
            leaveDays,
            input.notes() == null ? "" : input.notes().trim(),
            input.revision() + 1);
    revision(repository.saveEvent(next, input.revision()) == 1);
    return view(next);
  }

  @Transactional
  public void deleteEvent(String id, long expectedRevision) {
    require(expectedRevision >= 1, "일정 버전을 입력해 주세요.");
    profile();
    if (repository.events().stream().noneMatch(event -> event.id().equals(id)))
      throw new WorkspaceException(404, "병역 일정을 찾을 수 없습니다.");
    revision(repository.deleteEvent(id, expectedRevision) == 1);
  }

  @Transactional
  public void deleteProfile(long expectedRevision) {
    require(expectedRevision >= 1, "복무 정보 버전을 입력해 주세요.");
    profile();
    revision(repository.deleteProfile(expectedRevision) == 1);
  }

  /** A read projection avoids duplicate calendar rows and stale events after a profile change. */
  @Transactional(readOnly = true)
  public List<PlannerDto.EventView> calendarEvents(LocalDate from, LocalDate to) {
    var profile = repository.profile().orElse(null);
    if (profile == null || !profile.calendarEnabled()) return List.of();
    var milestones =
        milestones(profile, LocalDate.now(MilitaryDates.ZONE)).stream()
            .map(
                item -> calendarEvent(item.id(), item.title(), item.date(), item.date(), "", true));
    var entries =
        repository.events().stream()
            .map(
                event ->
                    calendarEvent(
                        event.id(),
                        event.title(),
                        event.startDate(),
                        event.endDate(),
                        event.notes(),
                        false));
    return Stream.concat(milestones, entries)
        .filter(
            event ->
                event.start().isBefore(to.atStartOfDay())
                    && event.end().isAfter(from.atStartOfDay()))
        .toList();
  }

  private PlannerDto.EventView calendarEvent(
      String id, String title, LocalDate start, LocalDate end, String notes, boolean milestone) {
    return new PlannerDto.EventView(
        "military:" + id,
        "[병역] " + title,
        start.atStartOfDay(),
        end.plusDays(1).atStartOfDay(),
        true,
        "",
        notes,
        "#7597eb",
        "MILITARY",
        milestone ? null : id);
  }
}
