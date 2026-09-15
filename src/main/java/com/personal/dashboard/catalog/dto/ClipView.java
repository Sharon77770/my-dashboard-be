package com.personal.dashboard.catalog.dto;

/** Server clipboard item; expired rows are excluded from all reads. */
public record ClipView(String id, String content, long expiresAt) {}
