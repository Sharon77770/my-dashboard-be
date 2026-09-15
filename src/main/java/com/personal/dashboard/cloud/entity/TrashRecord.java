package com.personal.dashboard.cloud.entity;

/** Private on-disk trash metadata; not an HTTP DTO. */
public record TrashRecord(String path, long deletedAt) {}
