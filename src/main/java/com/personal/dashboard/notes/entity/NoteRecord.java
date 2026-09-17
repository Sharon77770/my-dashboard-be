package com.personal.dashboard.notes.entity;

import com.personal.dashboard.notes.domain.NoteKind;

/** SQLite representation, deliberately separate from HTTP views. */
public record NoteRecord(
    String id,
    String parentId,
    NoteKind kind,
    String title,
    String icon,
    String content,
    long revision,
    long createdAt,
    long updatedAt) {}
