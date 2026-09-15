package com.personal.dashboard.health.controller;

import com.personal.dashboard.health.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides public process liveness without account or infrastructure details. */
@RestController
public class HealthController {

  /** Liveness does not promise database or external-service readiness. */
  @GetMapping("/health")
  public ResponseEntity<HealthResponse> health() {
    return ResponseEntity.ok(new HealthResponse("UP"));
  }
}
