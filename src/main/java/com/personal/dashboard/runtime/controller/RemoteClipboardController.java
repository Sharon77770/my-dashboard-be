package com.personal.dashboard.runtime.controller;

import com.personal.dashboard.runtime.dto.RemoteClipboardRequest;
import com.personal.dashboard.runtime.dto.RemoteClipboardView;
import com.personal.dashboard.runtime.service.RemoteClipboardService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Session ownership and the existing OWNER/CSRF boundary protect clipboard writes. */
@RestController
@RequestMapping("/api/v1/sessions")
public class RemoteClipboardController {
  private final RemoteClipboardService service;

  public RemoteClipboardController(RemoteClipboardService service) {
    this.service = service;
  }

  @PostMapping("/{id}/clipboard")
  public RemoteClipboardView send(
      @PathVariable String id,
      @Valid @RequestBody RemoteClipboardRequest request,
      HttpSession session) {
    return service.send(id, session.getId(), request.text());
  }
}
