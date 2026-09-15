package com.personal.dashboard.runtime.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.*;
import org.apache.guacamole.net.GuacamoleSocket;
import org.junit.jupiter.api.Test;

class DesktopSetupServiceTest {
  private DeviceRecord device(String protocol) {
    return new DeviceRecord(
        "fixture",
        "Fixture",
        "localhost",
        22,
        "tester",
        "",
        "key",
        "/home/tester",
        protocol,
        5900,
        "",
        "",
        "",
        "",
        false);
  }

  private void finished(DesktopSetupService service) throws InterruptedException {
    for (int i = 0; i < 100 && service.status("fixture").state().equals("RUNNING"); i++)
      Thread.sleep(20);
    assertThat(service.status("fixture").state()).isNotEqualTo("RUNNING");
  }

  @Test
  void reusesExistingWithoutInstalling() throws Exception {
    var catalog = mock(CatalogService.class);
    var setup = mock(DesktopSetupAdapter.class);
    var remote = mock(RemoteAdapter.class);
    var device = device("VNC");
    when(catalog.requireDevice("fixture")).thenReturn(device);
    var socket = mock(GuacamoleSocket.class);
    when(remote.open(device, 1280, 800)).thenReturn(socket);
    var service = new DesktopSetupService(catalog, setup, remote);
    service.start("fixture");
    finished(service);
    assertThat(service.status("fixture").state()).isEqualTo("READY");
    verify(setup, never()).configure(any());
    verify(catalog, never()).saveDevice(any(), any());
    verify(socket).close();
  }

  @Test
  void failedExistingConnectionPreservesConfiguration() throws Exception {
    var catalog = mock(CatalogService.class);
    var setup = mock(DesktopSetupAdapter.class);
    var remote = mock(RemoteAdapter.class);
    var device = device("RDP");
    when(catalog.requireDevice("fixture")).thenReturn(device);
    when(remote.open(device, 1280, 800)).thenThrow(new WorkspaceException(502, "failure"));
    var service = new DesktopSetupService(catalog, setup, remote);
    service.start("fixture");
    finished(service);
    assertThat(service.status("fixture").state()).isEqualTo("BLOCKED");
    verify(setup, never()).configure(any());
    verify(catalog, never()).saveDevice(any(), any());
  }

  @Test
  void unsupportedOSReportsActionWithoutChangingProfile() throws Exception {
    var catalog = mock(CatalogService.class);
    var setup = mock(DesktopSetupAdapter.class);
    var remote = mock(RemoteAdapter.class);
    var device = device("NONE");
    when(catalog.requireDevice("fixture")).thenReturn(device);
    when(setup.configure(device)).thenReturn(new DesktopSetupAdapter.Outcome("MACOS", 0));
    var service = new DesktopSetupService(catalog, setup, remote);
    service.start("fixture");
    finished(service);
    assertThat(service.status("fixture").message()).contains("macOS");
    verifyNoInteractions(remote);
    verify(catalog, never()).saveDevice(any(), any());
  }
}
