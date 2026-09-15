package com.personal.dashboard.nas.controller;

import com.personal.dashboard.nas.dto.DavDto.Settings;
import com.personal.dashboard.nas.service.DavService;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cloud/nas")
public class NasController {
  private final DavService service;

  public NasController(DavService service) {
    this.service = service;
  }

  @GetMapping
  public Settings settings(Principal principal) {
    return service.settings(principal.getName());
  }
}
