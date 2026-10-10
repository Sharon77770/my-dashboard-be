package com.personal.dashboard.runtime.controller;

import com.personal.dashboard.runtime.dto.*;
import com.personal.dashboard.runtime.service.DesktopSetupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** OWNER-only preparation, read-only recommendations and scoped connection settings. */
@RestController
@RequestMapping("/api/v1/devices/{id}/remote-setup")
public class DesktopSetupController {
  private final DesktopSetupService service;

  public DesktopSetupController(DesktopSetupService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DesktopSetupView start(
      @PathVariable String id, @Valid @RequestBody(required = false) DesktopSetupRequest request) {
    return service.start(id, request == null ? new DesktopSetupRequest(null) : request);
  }

  @GetMapping
  public DesktopSetupView status(@PathVariable String id) {
    return service.status(id);
  }

  @GetMapping("/plan")
  public DesktopSetupPlan plan(@PathVariable String id) {
    return service.plan(id);
  }

  @PutMapping("/connection")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void connection(
      @PathVariable String id, @Valid @RequestBody DesktopConnectionRequest request) {
    service.connection(id, request);
  }
}
