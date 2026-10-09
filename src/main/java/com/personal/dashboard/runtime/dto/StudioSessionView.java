package com.personal.dashboard.runtime.dto;

/** Login-owned retained PTY metadata; no credentials or terminal output are exposed. */
public record StudioSessionView(String id, String deviceId, String root, boolean attached) {}
