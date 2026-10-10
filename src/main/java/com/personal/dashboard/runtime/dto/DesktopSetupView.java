package com.personal.dashboard.runtime.dto;

/** Pollable progress and recovery code; raw command output and secrets never leave the adapter. */
public record DesktopSetupView(String state, String message, String stage, String code) {
  public DesktopSetupView(String state, String message) {
    this(state, message, state.equals("RUNNING") ? "CHECKING" : state, "");
  }
}
