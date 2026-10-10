package com.personal.dashboard.communication.controller;

import com.personal.dashboard.communication.service.WindowsBridgeService;
import com.personal.dashboard.runtime.dto.SessionView;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Read-only UIA inspection and explicit Guacamole fallback for a registered Windows device. */
@RestController
@RequestMapping("/api/v1/communications/windows/{deviceId}")
public class WindowsBridgeController {
  private final WindowsBridgeService service;

  public WindowsBridgeController(WindowsBridgeService service) {
    this.service = service;
  }

  @PostMapping("/observations")
  public com.personal.dashboard.communication.dto.BridgeDto.WindowsSnapshot snapshot(
      @PathVariable String deviceId) {
    return service.snapshot(deviceId);
  }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public SessionView screen(@PathVariable String deviceId, HttpSession session) {
    return service.screen(deviceId, session.getId());
  }
}
