package com.personal.dashboard.studio.controller;

import com.personal.dashboard.studio.dto.StudioApiDto.*;
import com.personal.dashboard.studio.service.StudioApiService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/studio/api")
public class StudioApiController {
  private final StudioApiService service;

  public StudioApiController(StudioApiService service) {
    this.service = service;
  }

  @PostMapping("/send")
  public Exchange send(@Valid @RequestBody Request input) {
    return service.send(input);
  }

  @PostMapping("/history")
  public List<History> history(@Valid @RequestBody Project project) {
    return service.history(project);
  }

  @PostMapping("/replay")
  public Exchange replay(@Valid @RequestBody Replay input) {
    return service.replay(input);
  }

  @PostMapping("/environment/read")
  public List<String> environment(@Valid @RequestBody Project project) {
    return service.environment(project);
  }

  @PostMapping("/environment")
  public void saveEnvironment(@Valid @RequestBody Environment input) {
    service.environment(input);
  }
}
