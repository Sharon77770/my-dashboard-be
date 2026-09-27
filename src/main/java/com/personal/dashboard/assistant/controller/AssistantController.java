package com.personal.dashboard.assistant.controller;

import com.personal.dashboard.studio.dto.AssistantDto;
import com.personal.dashboard.studio.dto.StudioDto.JobView;
import com.personal.dashboard.studio.dto.StudioDto.Request;
import com.personal.dashboard.studio.service.StudioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** OWNER and CSRF protected jobs for the server-hosted floating assistant. */
@RestController
@RequestMapping("/api/v1/assistant/jobs")
public class AssistantController {
  private final StudioService jobs;

  public AssistantController(StudioService jobs) {
    this.jobs = jobs;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public JobView start(HttpSession session, @Valid @RequestBody Request input) {
    return jobs.startAssistant(session.getId(), input);
  }

  @GetMapping("/{id}")
  public JobView get(HttpSession session, @PathVariable String id) {
    return jobs.get(session.getId(), id);
  }

  @PostMapping("/{id}/inputs")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void input(
      HttpSession session,
      @PathVariable String id,
      @Valid @RequestBody AssistantDto.Control input) {
    jobs.control(session.getId(), id, input);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(HttpSession session, @PathVariable String id) {
    jobs.cancel(session.getId(), id);
  }
}
