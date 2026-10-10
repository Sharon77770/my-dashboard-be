package com.personal.dashboard.runtime.controller;

import com.personal.dashboard.runtime.dto.AuthenticationBrowserRequest;
import com.personal.dashboard.runtime.dto.SessionView;
import com.personal.dashboard.runtime.service.AuthenticationBrowserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Creates OWNER-only, CSRF-protected remote views bound to the current dashboard login. */
@RestController
@RequestMapping("/api/v1/authentication-browser/sessions")
public class AuthenticationBrowserController {
  private final AuthenticationBrowserService service;

  public AuthenticationBrowserController(AuthenticationBrowserService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<SessionView> create(
      @Valid @RequestBody AuthenticationBrowserRequest request, HttpSession session) {
    return ResponseEntity.status(201).body(service.open(request, session.getId()));
  }
}
