package com.personal.dashboard.runtime.dto;

/** Pollable status contains guidance only, never upstream output or credentials. */
public record DesktopSetupView(String state, String message) {}
