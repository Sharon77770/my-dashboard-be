package com.personal.dashboard.global.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.entity.NetworkMode;
import com.personal.dashboard.catalog.repository.CatalogRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import java.net.InetAddress;
import java.util.List;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.DirectConnection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SshJumpNetworkTest {
  @Test
  void tailscaleBridgeThenDirectTargetUsesSeparateNetworksAndRemoteDns() throws Exception {
    var network = spy(new DeviceNetworkAdapter());
    doReturn(true).when(network).available();
    doReturn(new InetAddress[] {InetAddress.getByName("100.64.1.2")})
        .when(network)
        .addresses("bridge.tailnet");
    var adapter =
        new SshAdapter(mock(CredentialVault.class), network, mock(CatalogRepository.class));
    var bridge = mock(SSHClient.class);
    var target = mock(SSHClient.class);
    var channel = mock(DirectConnection.class);
    when(bridge.newDirectConnection("private-host", 2222)).thenReturn(channel);

    ReflectionTestUtils.invokeMethod(
        adapter,
        "openInto",
        bridge,
        device("bridge", "bridge.tailnet", 22, NetworkMode.TAILSCALE),
        "fixture-password",
        null,
        null);
    ReflectionTestUtils.invokeMethod(
        adapter,
        "openInto",
        target,
        device("target", "private-host", 2222, NetworkMode.DIRECT),
        "fixture-password",
        bridge,
        null);

    verify(bridge).connect("100.64.1.2", 22);
    verify(bridge).newDirectConnection("private-host", 2222);
    verify(target).connectVia(channel);
    verify(target, never()).connect(anyString(), anyInt());
    verify(network, times(1)).available();
    verify(network, never()).addresses("private-host");
    verify(bridge).addHostKeyVerifier("SHA256:fixture");
    verify(target).addHostKeyVerifier("SHA256:fixture");
    verify(target).authPassword("tester", "fixture-password");
  }

  @Test
  void unavailableTailscaleBridgeNeverConnectsDirectly() throws Exception {
    var network = spy(new DeviceNetworkAdapter());
    doReturn(false).when(network).available();
    var adapter =
        new SshAdapter(mock(CredentialVault.class), network, mock(CatalogRepository.class));
    var bridge = mock(SSHClient.class);

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    adapter,
                    "openInto",
                    bridge,
                    device("bridge", "bridge.tailnet", 22, NetworkMode.TAILSCALE),
                    "fixture-password",
                    null,
                    null))
        .isInstanceOf(WorkspaceException.class);
    verify(bridge, never()).connect(anyString(), anyInt());
  }

  private DeviceRecord device(String id, String host, int port, NetworkMode mode) {
    return new DeviceRecord(
        id,
        id,
        host,
        port,
        "tester",
        "cipher",
        "SHA256:fixture",
        "/home/tester",
        "NONE",
        3389,
        "",
        "",
        "",
        "",
        false,
        mode,
        List.of());
  }
}
