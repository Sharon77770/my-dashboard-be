package com.personal.dashboard.catalog.controller;

import com.personal.dashboard.catalog.dto.*;
import com.personal.dashboard.catalog.service.SshDeviceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Authenticated command-based device enrollment; execution belongs to the service and adapter. */
@RestController
@RequestMapping("/api/v1/devices/ssh")
public class SshDeviceController {
  private final SshDeviceService service;

  public SshDeviceController(SshDeviceService service) {
    this.service = service;
  }

  @PostMapping
  public DeviceView connect(@Valid @RequestBody SshDeviceRequest request) {
    return service.connect(request);
  }
}
