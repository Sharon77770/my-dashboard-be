package com.personal.dashboard.communication.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/** Profile metadata and observed accessible text, never cookies or guessed messages. */
public final class BridgeDto {
  private BridgeDto() {}

  public record Profile(String id, String provider, String label) {}

  public record Create(
      @NotBlank @Pattern(regexp = "GMAIL|SLACK|DISCORD|KAKAOTALK") String provider,
      @NotBlank @Size(max = 80) String label) {}

  public record Status(boolean configured, String limitation) {}

  public record WindowsNode(String role, String name, boolean canInvoke, boolean canSetValue) {}

  public record WindowsSnapshot(
      String state,
      String loginState,
      boolean structuredMessages,
      String revision,
      List<WindowsNode> nodes) {}

  public record Node(String role, String name) {}

  public record Snapshot(String state, List<Node> nodes, boolean structuredMessages) {}
}
