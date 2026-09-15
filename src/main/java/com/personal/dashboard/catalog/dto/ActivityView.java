package com.personal.dashboard.catalog.dto;

/** Recent resource opening, excluding commands, passwords and file contents. */
public record ActivityView(
    String id, String kind, String targetId, String label, String path, long occurredAt) {}
