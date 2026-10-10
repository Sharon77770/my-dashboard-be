package com.personal.dashboard.runtime.service;

import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.DesktopClipboardAdapter;
import com.personal.dashboard.runtime.adapter.DesktopSetupAdapter;
import com.personal.dashboard.runtime.dto.RemoteClipboardView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Authorizes clipboard writes against an attached session and its managed desktop identity. */
@Service
public class RemoteClipboardService {
  private final RuntimeService runtimes;
  private final DesktopSetupAdapter setup;
  private final DesktopClipboardAdapter clipboard;

  public RemoteClipboardService(
      RuntimeService runtimes, DesktopSetupAdapter setup, DesktopClipboardAdapter clipboard) {
    this.runtimes = runtimes;
    this.setup = setup;
    this.clipboard = clipboard;
  }

  @PreAuthorize("hasRole('OWNER')")
  public RemoteClipboardView send(String id, String ownerId, String text) {
    var runtime = runtimes.owned(id, ownerId);
    if (!runtime.kind.equals("REMOTE") || !runtime.attached)
      throw new WorkspaceException(409, "연결된 원격 화면에서 다시 시도하세요.");
    if (!runtime.device.remoteProtocol().equals("VNC")) return new RemoteClipboardView(false);
    var managed = setup.managed(runtime.device);
    if (managed == null || managed.port() != runtime.device.remotePort())
      return new RemoteClipboardView(false);
    clipboard.send(runtime.device, managed.port(), text);
    return new RemoteClipboardView(true);
  }
}
