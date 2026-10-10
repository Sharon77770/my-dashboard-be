package com.personal.dashboard.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Clipboard contents are transient input, never returned or logged. */
public record RemoteClipboardRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotNull
        @Size(max = 32000)
        @Pattern(regexp = "[^\\x00]*")
        String text) {
  @Override
  public String toString() {
    return "RemoteClipboardRequest[redacted]";
  }
}
