package com.personal.dashboard.runtime.dto;

/** Read-only recommendation. No upstream output or credentials are exposed. */
public record DesktopSetupPlan(
    String mode,
    String title,
    String message,
    String actionLabel,
    boolean canStart,
    boolean requiresPassword) {}
