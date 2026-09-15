package com.personal.dashboard.files.dto;

/** A root-relative file listing; symbolic links cannot be traversed outside the configured root. */
public record FileEntry(String name, String path, boolean directory, long size, long modifiedAt) {}
