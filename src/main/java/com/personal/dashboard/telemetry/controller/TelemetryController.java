package com.personal.dashboard.telemetry.controller;

import com.personal.dashboard.telemetry.dto.TelemetryDto.*;
import com.personal.dashboard.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** OWNER authenticated service management and analytics API. */
@RestController
@RequestMapping("/api/v1/telemetry/services")
public class TelemetryController {
  private final TelemetryService service;

  public TelemetryController(TelemetryService service) {
    this.service = service;
  }

  @GetMapping
  public List<Summary> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ServiceView create(@Valid @RequestBody ServiceRequest request) {
    return service.create(request);
  }

  @GetMapping("/{id}")
  public ServiceView get(@PathVariable String id) {
    return service.detail(id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  @PutMapping("/{id}/enabled")
  public ServiceView enabled(@PathVariable String id, @RequestBody EnabledRequest request) {
    return service.enabled(id, request.enabled());
  }

  @PostMapping("/{id}/key")
  public ServiceView regenerate(@PathVariable String id) {
    return service.regenerate(id);
  }

  @DeleteMapping("/{id}/key")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable String id) {
    service.revoke(id);
  }

  @GetMapping("/{id}/analytics")
  public Analytics analytics(
      @PathVariable String id, @RequestParam(defaultValue = "24h") String range) {
    return service.analytics(id, range);
  }

  public record EnabledRequest(boolean enabled) {}
}
