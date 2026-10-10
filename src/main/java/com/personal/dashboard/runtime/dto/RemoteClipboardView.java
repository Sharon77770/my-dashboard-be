package com.personal.dashboard.runtime.dto;

/** False asks the client to use the existing RDP/VNC clipboard stream. */
public record RemoteClipboardView(boolean delivered) {}
