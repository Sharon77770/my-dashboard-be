package com.personal.dashboard.studio.controller;

import com.personal.dashboard.studio.dto.StudioBrowserDto.Input;
import com.personal.dashboard.studio.service.StudioBrowserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/studio/browser")
public class StudioBrowserController {
  private final StudioBrowserService service;

  public StudioBrowserController(StudioBrowserService service) {
    this.service = service;
  }

  @PostMapping
  public Map<String, Object> action(HttpSession session, @Valid @RequestBody Input input) {
    return service.action(session.getId(), input);
  }
}
