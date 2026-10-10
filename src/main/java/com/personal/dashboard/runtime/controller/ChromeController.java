package com.personal.dashboard.runtime.controller;

import com.personal.dashboard.runtime.dto.ChromeSessionRequest;
import com.personal.dashboard.runtime.dto.SessionView;
import com.personal.dashboard.runtime.service.RuntimeService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** OWNER and CSRF protected connection to the persistent server Chromium desktop. */
@RestController
@RequestMapping("/api/v1/chrome/sessions")
public class ChromeController {
  private final RuntimeService service;

  public ChromeController(RuntimeService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<SessionView> create(
      @Valid @RequestBody ChromeSessionRequest request, HttpSession session) {
    return ResponseEntity.status(201)
        .body(service.createChromeSession(session.getId(), request.width(), request.height()));
  }
}
