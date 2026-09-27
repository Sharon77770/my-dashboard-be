package com.personal.dashboard.telemetry.controller;

import com.personal.dashboard.telemetry.dto.TelemetryDto.*;
import com.personal.dashboard.telemetry.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Service-key authenticated ingestion endpoints, separated from dashboard management routes. */
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryIngestionController {
  private final TelemetryService service;

  public TelemetryIngestionController(TelemetryService service) {
    this.service = service;
  }

  @PostMapping("/events")
  public Ingested event(
      jakarta.servlet.http.HttpServletRequest request, @Valid @RequestBody EventRequest body) {
    return service.event((String) request.getAttribute("telemetryServiceId"), body);
  }

  @PostMapping("/gauges")
  public Ingested gauge(
      jakarta.servlet.http.HttpServletRequest request, @Valid @RequestBody GaugeRequest body) {
    return service.gauge((String) request.getAttribute("telemetryServiceId"), body);
  }

  @PostMapping("/batch")
  public Ingested batch(
      jakarta.servlet.http.HttpServletRequest request, @Valid @RequestBody BatchRequest body) {
    return service.batch((String) request.getAttribute("telemetryServiceId"), body);
  }
}
