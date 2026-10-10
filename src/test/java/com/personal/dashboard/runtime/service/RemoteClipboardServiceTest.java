package com.personal.dashboard.runtime.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.*;
import com.personal.dashboard.runtime.dto.SessionRequest;
import org.junit.jupiter.api.Test;

/** No clipboard write may escape its login session or managed VNC identity. */
class RemoteClipboardServiceTest {
  @Test
  void sendsUnicodeOnlyToAttachedOwnedManagedDesktop() {
    var catalog = mock(CatalogService.class);
    var device =
        new DeviceRecord(
            "remote", "Linux", "host", 22, "user", "", "", "/", "VNC", 5920, "", "", "", "", false);
    when(catalog.requireDevice("remote")).thenReturn(device);
    var runtimes = new RuntimeService(catalog, mock(BrowserAdapter.class), "localhost", 9223, 5901);
    var setup = mock(DesktopSetupAdapter.class);
    var clipboard = mock(DesktopClipboardAdapter.class);
    var service = new RemoteClipboardService(runtimes, setup, clipboard);
    var view = runtimes.create(new SessionRequest("REMOTE", "remote", 800, 600), "owner");
    assertThatThrownBy(() -> service.send(view.id(), "other", "text"))
        .isInstanceOf(WorkspaceException.class);
    assertThatThrownBy(() -> service.send(view.id(), "owner", "text"))
        .isInstanceOf(WorkspaceException.class);
    verifyNoInteractions(clipboard);
    runtimes.attach(view.id(), "owner");
    assertThat(service.send(view.id(), "owner", "text").delivered()).isFalse();
    when(setup.managed(device))
        .thenReturn(new DesktopSetupAdapter.Managed("identity", "cipher", 5921));
    assertThat(service.send(view.id(), "owner", "text").delivered()).isFalse();
    verifyNoInteractions(clipboard);
    when(setup.managed(device))
        .thenReturn(new DesktopSetupAdapter.Managed("identity", "cipher", 5920));
    assertThat(service.send(view.id(), "owner", "한글\n😀").delivered()).isTrue();
    verify(clipboard).send(device, 5920, "한글\n😀");
    doThrow(new WorkspaceException(502, "failed")).when(clipboard).send(device, 5920, "retry");
    assertThatThrownBy(() -> service.send(view.id(), "owner", "retry"))
        .isInstanceOf(WorkspaceException.class);
    runtimes.closeOwned(view.id(), "owner");
  }
}
