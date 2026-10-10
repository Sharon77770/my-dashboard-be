package com.personal.dashboard.communication.controller;

import com.personal.dashboard.communication.dto.BridgeDto;
import com.personal.dashboard.communication.service.BrowserBridgeService;
import com.personal.dashboard.runtime.dto.SessionView;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Existing OWNER, CSRF, WS origin and runtime ownership protect the bridge. */
@RestController
@RequestMapping("/api/v1/communications/bridge")
public class BrowserBridgeController {
  private final BrowserBridgeService service;

  public BrowserBridgeController(BrowserBridgeService service) {
    this.service = service;
  }

  @GetMapping
  public BridgeDto.Status status() {
    return service.status();
  }

  @GetMapping("/profiles")
  public List<BridgeDto.Profile> profiles() {
    return service.profiles();
  }

  @PostMapping("/profiles")
  @ResponseStatus(HttpStatus.CREATED)
  public BridgeDto.Profile create(@Valid @RequestBody BridgeDto.Create request) {
    return service.create(request);
  }

  @PostMapping("/profiles/{id}/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public SessionView open(@PathVariable String id, HttpSession session) {
    return service.open(id, session.getId());
  }

  @GetMapping("/profiles/{id}/snapshot")
  public BridgeDto.Snapshot snapshot(@PathVariable String id) {
    return service.snapshot(id);
  }

  @PostMapping("/profiles/{id}/stops")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void stop(@PathVariable String id) {
    service.stop(id);
  }

  @DeleteMapping("/profiles/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.delete(id);
  }
}
