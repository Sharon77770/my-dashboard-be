package com.personal.dashboard.tailscale.dto;

import java.util.List;

/** Minimal owner-only projection; no daemon keys or peer inventory. */
public record TailscaleView(
    String state,
    String hostname,
    List<String> ips,
    String loginUrl,
    boolean pending,
    String error) {}
