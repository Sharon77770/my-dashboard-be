package com.personal.dashboard.catalog.dto;

/** Restorable tab metadata. Reopening a runtime creates a new connection. */
public record TabView(
    String id, String kind, String targetId, String path, String title, boolean pinned) {}
