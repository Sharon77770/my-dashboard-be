package com.personal.dashboard.catalog.dto;

/** Actual measurements; unavailable metrics use null and never fabricated values. */
public record DeviceStatus(
    String state, Double cpu, Double memory, Double disk, String details, long checkedAt) {}
