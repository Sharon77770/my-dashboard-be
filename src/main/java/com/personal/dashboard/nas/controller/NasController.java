package com.personal.dashboard.nas.controller;

import com.personal.dashboard.nas.dto.NasSettings;
import com.personal.dashboard.nas.service.NasSettingsService;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cloud/nas")
public class NasController {
  private final NasSettingsService service;

  public NasController(NasSettingsService service) {
    this.service = service;
  }

  @GetMapping
  public NasSettings settings(Principal principal) {
    return service.settings();
  }
}
