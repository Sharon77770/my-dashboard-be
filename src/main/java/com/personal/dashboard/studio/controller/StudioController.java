package com.personal.dashboard.studio.controller;

import com.personal.dashboard.studio.dto.AssistantDto;
import com.personal.dashboard.studio.dto.StudioDto.*;
import com.personal.dashboard.studio.service.StudioService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Same-origin OWNER and CSRF protected asynchronous SSH operations. */
@RestController
@RequestMapping("/api/v1/studio/jobs")
public class StudioController {
  private final StudioService service;

  public StudioController(StudioService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public JobView start(HttpSession session, @Valid @RequestBody Request input) {
    return service.start(session.getId(), input);
  }

  @GetMapping("/{id}")
  public JobView get(HttpSession session, @PathVariable String id) {
    return service.get(session.getId(), id);
  }

  @PostMapping("/{id}/inputs")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void input(
      HttpSession session,
      @PathVariable String id,
      @Valid @RequestBody AssistantDto.Control input) {
    service.control(session.getId(), id, input);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(HttpSession session, @PathVariable String id) {
    service.cancel(session.getId(), id);
  }
}
