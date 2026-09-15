package com.personal.dashboard.catalog.entity;

/** Persisted server-browser launch target. */
public record ApplicationRecord(String id, String name, String url, boolean pinned) {}
