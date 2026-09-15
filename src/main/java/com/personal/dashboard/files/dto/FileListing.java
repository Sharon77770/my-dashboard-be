package com.personal.dashboard.files.dto;

import java.util.List;

/** Canonical workspace-relative directory contents. */
public record FileListing(String path, List<FileEntry> entries) {}
