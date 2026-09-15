package com.personal.dashboard.global.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.entity.NetworkMode;
import com.personal.dashboard.global.WorkspaceException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class DeviceNetworkAdapterTest {
  @Test
  void defaultRoutingDoesNotRequireTailscaleOrResolveDns() throws Exception {
    var adapter = spy(new DeviceNetworkAdapter());
    assertThat(adapter.resolve("ordinary-host", NetworkMode.DIRECT)).isEqualTo("ordinary-host");
    verify(adapter, never()).available();
    verify(adapter, never()).addresses(anyString());
  }

  @Test
  void disconnectedTailscaleFailsBeforeAnyDestinationLookup() throws Exception {
    var adapter = spy(new DeviceNetworkAdapter());
    doReturn(false).when(adapter).available();
    assertThatThrownBy(() -> adapter.resolve("100.64.1.2", NetworkMode.TAILSCALE))
        .isInstanceOf(WorkspaceException.class)
        .hasMessageContaining("Tailscale");
    verify(adapter, never()).addresses(anyString());
  }

  @Test
  void magicDnsSelectsOnlyTailnetAddressesWithoutDirectFallback() throws Exception {
    var adapter = spy(new DeviceNetworkAdapter());
    doReturn(true).when(adapter).available();
    doReturn(
            new InetAddress[] {
              InetAddress.getByName("192.168.1.20"), InetAddress.getByName("100.100.1.20")
            })
        .when(adapter)
        .addresses("workstation");
    assertThat(adapter.resolve("workstation", NetworkMode.TAILSCALE)).isEqualTo("100.100.1.20");
    doReturn(new InetAddress[] {InetAddress.getByName("192.168.1.20")})
        .when(adapter)
        .addresses("workstation");
    assertThatThrownBy(() -> adapter.resolve("workstation", NetworkMode.TAILSCALE))
        .isInstanceOf(WorkspaceException.class)
        .hasMessageContaining("Tailscale IP");
  }

  @Test
  void validatesIpv4AndIpv6PrefixBoundaries() throws Exception {
    for (String address : new String[] {"100.64.0.1", "100.127.255.254", "fd7a:115c:a1e0::123"})
      assertThat(DeviceNetworkAdapter.isTailnet(InetAddress.getByName(address))).isTrue();
    for (String address :
        new String[] {"100.63.255.255", "100.128.0.1", "127.0.0.1", "::1", "fd7a:115c:a1e1::1"})
      assertThat(DeviceNetworkAdapter.isTailnet(InetAddress.getByName(address))).isFalse();
  }
}
