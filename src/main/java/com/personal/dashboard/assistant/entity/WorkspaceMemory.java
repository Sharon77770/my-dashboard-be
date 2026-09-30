package com.personal.dashboard.assistant.entity;

/** Persisted Workspace Memory state, separate from Assistant API contracts. */
public record WorkspaceMemory(
    String id,
    String content,
    String type,
    String confidence,
    String scope,
    String status,
    String importance,
    String tags,
    String timeHint,
    String relatedServiceId,
    String relatedProject,
    String sourceType,
    String sourceThreadId,
    String sourceDescription,
    boolean pinned,
    boolean manuallyCreated,
    long createdAt,
    long updatedAt,
    long lastAccessedAt,
    int accessCount,
    Long expiresAt,
    String supersededBy,
    String promotedTargetType,
    String promotedTargetId) {}
