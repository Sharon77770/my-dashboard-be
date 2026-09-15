package com.personal.dashboard.health.dto;

/** Public liveness contract; UP means the HTTP application is responding. */
public record HealthResponse(String status) {}
