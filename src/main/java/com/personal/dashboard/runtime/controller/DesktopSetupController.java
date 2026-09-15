package com.personal.dashboard.runtime.controller;

import com.personal.dashboard.runtime.dto.DesktopSetupView;
import com.personal.dashboard.runtime.service.DesktopSetupService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices/{id}/remote-setup")
public class DesktopSetupController {
  private final DesktopSetupService service;

  public DesktopSetupController(DesktopSetupService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DesktopSetupView start(@PathVariable String id) {
    return service.start(id);
  }

  @GetMapping
  public DesktopSetupView status(@PathVariable String id) {
    return service.status(id);
  }
}
