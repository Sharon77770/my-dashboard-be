package com.personal.dashboard.catalog.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.dto.*;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SshDeviceServiceTest {
  @Test
  void tailscaleEnrollmentConnectsToResolvedAddressAndPersistsOriginalNameAndMode() {
    var catalog = mock(CatalogService.class);
    var ssh = mock(SshAdapter.class);
    var network = mock(com.personal.dashboard.global.integration.DeviceNetworkAdapter.class);
    when(catalog.devices()).thenReturn(List.of());
    when(network.resolve(
            "workstation", com.personal.dashboard.catalog.entity.NetworkMode.TAILSCALE))
        .thenReturn("100.64.1.2");
    when(ssh.discover("100.64.1.2", 22, "tester", "fixture-password", ""))
        .thenReturn(new SshAdapter.DiscoveredHost("SHA256:fixture", "/home/tester"));
    new SshDeviceService(catalog, ssh, network)
        .connect(
            new SshDeviceRequest(
                "ssh tester@workstation",
                "fixture-password",
                "",
                com.personal.dashboard.catalog.entity.NetworkMode.TAILSCALE));
    var saved = ArgumentCaptor.forClass(DeviceRequest.class);
    verify(catalog).saveDevice(isNull(), saved.capture());
    assertThat(saved.getValue().host()).isEqualTo("workstation");
    assertThat(saved.getValue().networkMode())
        .isEqualTo(com.personal.dashboard.catalog.entity.NetworkMode.TAILSCALE);
  }

  @Test
  void parsesPortBeforeOrAfterDestinationAndIpv6() {
    assertThat(SshDeviceService.parse("ssh -p 2222 tester@host"))
        .isEqualTo(new SshDeviceService.Target("tester", "host", 2222));
    assertThat(SshDeviceService.parse("ssh tester@host -p2222"))
        .isEqualTo(new SshDeviceService.Target("tester", "host", 2222));
    assertThat(SshDeviceService.parse("ssh tester@[::1]"))
        .isEqualTo(new SshDeviceService.Target("tester", "::1", 22));
  }

  @Test
  void rejectsShellCommandsUnsupportedOptionsAndInvalidPorts() {
    for (String command :
        List.of(
            "ssh user@host;id",
            "ssh user@host whoami",
            "ssh -oProxyCommand=id user@host",
            "ssh $(id)@host",
            "ssh user@host -p 0",
            "ssh user@host -p 65536",
            "ssh user@host -p 22 -p 23",
            "ssh host",
            "ssh -i key user@host")) {
      assertThatThrownBy(() -> SshDeviceService.parse(command))
          .isInstanceOf(WorkspaceException.class);
    }
  }

  @Test
  void discoversKeyAndHomeBeforeSavingEncryptedDeviceThroughCatalog() {
    var catalog = mock(CatalogService.class);
    var ssh = mock(SshAdapter.class);
    when(catalog.devices()).thenReturn(List.of());
    when(ssh.discover("host", 22, "tester", "test-password", ""))
        .thenReturn(new SshAdapter.DiscoveredHost("SHA256:verified", "/home/tester"));
    new SshDeviceService(
            catalog, ssh, new com.personal.dashboard.global.integration.DeviceNetworkAdapter())
        .connect(new SshDeviceRequest("ssh tester@host", "test-password", ""));
    var saved = ArgumentCaptor.forClass(DeviceRequest.class);
    verify(catalog).saveDevice(isNull(), saved.capture());
    assertThat(saved.getValue().fingerprint()).isEqualTo("SHA256:verified");
    assertThat(saved.getValue().rootPath()).isEqualTo("/home/tester");
    assertThat(saved.getValue().name()).isEqualTo("tester@host");
  }

  @Test
  void existingHostIsPinnedAndDeviceIdAndRemoteSettingsArePreserved() {
    var catalog = mock(CatalogService.class);
    var ssh = mock(SshAdapter.class);
    when(catalog.devices())
        .thenReturn(
            List.of(
                new DeviceView(
                    "saved",
                    "Existing",
                    "host",
                    22,
                    "tester",
                    true,
                    "SHA256:pinned",
                    "/srv",
                    "RDP",
                    3389,
                    "remote",
                    true,
                    "",
                    "",
                    false)));
    when(ssh.discover("host", 22, "tester", "test-password", "SHA256:pinned"))
        .thenReturn(new SshAdapter.DiscoveredHost("SHA256:pinned", "/home/tester"));
    new SshDeviceService(
            catalog, ssh, new com.personal.dashboard.global.integration.DeviceNetworkAdapter())
        .connect(new SshDeviceRequest("ssh tester@host", "test-password", null));
    var saved = ArgumentCaptor.forClass(DeviceRequest.class);
    verify(catalog).saveDevice(eq("saved"), saved.capture());
    assertThat(saved.getValue().rootPath()).isEqualTo("/srv");
    assertThat(saved.getValue().remoteProtocol()).isEqualTo("RDP");
    assertThat(saved.getValue().remotePassword()).isEmpty();
  }

  @Test
  void failedConnectionNeverCreatesDevice() {
    var catalog = mock(CatalogService.class);
    var ssh = mock(SshAdapter.class);
    when(catalog.devices()).thenReturn(List.of());
    when(ssh.discover(anyString(), anyInt(), anyString(), anyString(), anyString()))
        .thenThrow(new WorkspaceException(502, "Connection failed"));
    assertThatThrownBy(
            () ->
                new SshDeviceService(
                        catalog,
                        ssh,
                        new com.personal.dashboard.global.integration.DeviceNetworkAdapter())
                    .connect(new SshDeviceRequest("ssh tester@localhost", "test-password", null)))
        .isInstanceOf(WorkspaceException.class);
    verify(catalog, never()).saveDevice(any(), any());
  }
}
