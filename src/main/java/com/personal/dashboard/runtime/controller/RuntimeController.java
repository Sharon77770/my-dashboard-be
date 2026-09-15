package com.personal.dashboard.runtime.controller;

import com.personal.dashboard.runtime.dto.*;
import com.personal.dashboard.runtime.service.RuntimeService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Creates CSRF-protected runtime handles and binds them to the current login session. */
@RestController
@RequestMapping("/api/v1/sessions")
public class RuntimeController {
  private final RuntimeService service;

  public RuntimeController(RuntimeService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<SessionView> create(
      @Valid @RequestBody SessionRequest request, HttpSession session) {
    return ResponseEntity.status(201).body(service.create(request, session.getId()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> close(@PathVariable String id, HttpSession session) {
    service.closeOwned(id, session.getId());
    return ResponseEntity.noContent().build();
  }
}
