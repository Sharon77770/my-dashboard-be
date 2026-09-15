package com.personal.dashboard.planner.controller;

import com.personal.dashboard.planner.dto.PlannerDto.*;
import com.personal.dashboard.planner.service.PlannerService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** OWNER/CSRF protected calendar and timetable HTTP resources. */
@RestController
@RequestMapping("/api/v1")
public class PlannerController {
  private final PlannerService service;

  public PlannerController(PlannerService service) {
    this.service = service;
  }

  @GetMapping("/calendar/events")
  public List<EventView> events(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return service.events(from, to);
  }

  @PostMapping("/calendar/events")
  @ResponseStatus(HttpStatus.CREATED)
  public EventView createEvent(@Valid @RequestBody EventRequest input) {
    return service.saveEvent(null, input);
  }

  @PutMapping("/calendar/events/{id}")
  public EventView updateEvent(@PathVariable String id, @Valid @RequestBody EventRequest input) {
    return service.saveEvent(id, input);
  }

  @DeleteMapping("/calendar/events/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteEvent(@PathVariable String id) {
    service.deleteEvent(id);
  }

  @GetMapping("/timetables")
  public List<TermView> terms() {
    return service.terms();
  }

  @PostMapping("/timetables")
  @ResponseStatus(HttpStatus.CREATED)
  public TermView createTerm(@Valid @RequestBody TermRequest input) {
    return service.saveTerm(null, input);
  }

  @PutMapping("/timetables/{id}")
  public TermView updateTerm(@PathVariable String id, @Valid @RequestBody TermRequest input) {
    return service.saveTerm(id, input);
  }

  @DeleteMapping("/timetables/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTerm(@PathVariable String id) {
    service.deleteTerm(id);
  }

  @GetMapping("/timetables/{id}")
  public TimetableView timetable(@PathVariable String id) {
    return service.timetable(id);
  }

  @PostMapping("/timetables/{termId}/courses")
  @ResponseStatus(HttpStatus.CREATED)
  public CourseView createCourse(
      @PathVariable String termId, @Valid @RequestBody CourseRequest input) {
    return service.saveCourse(termId, null, input);
  }

  @PutMapping("/timetables/{termId}/courses/{id}")
  public CourseView updateCourse(
      @PathVariable String termId,
      @PathVariable String id,
      @Valid @RequestBody CourseRequest input) {
    return service.saveCourse(termId, id, input);
  }

  @DeleteMapping("/timetables/{termId}/courses/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteCourse(@PathVariable String termId, @PathVariable String id) {
    service.deleteCourse(termId, id);
  }
}
