package com.personal.dashboard.catalog.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.dto.DeviceRequest;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.entity.NetworkMode;
import com.personal.dashboard.catalog.repository.CatalogRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.DeviceNetworkAdapter;
import com.personal.dashboard.global.integration.SshAdapter;
import com.personal.dashboard.global.security.CredentialVault;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogJumpProxyTest {
  @TempDir Path root;
  private CatalogRepository repository;
  private SshAdapter ssh;
  private CatalogService catalog;
  private DeviceRecord bridge;

  @BeforeEach
  void setUp() throws Exception {
    repository = mock(CatalogRepository.class);
    ssh = mock(SshAdapter.class);
    var vault = mock(CredentialVault.class);
    when(vault.encrypt(anyString())).thenReturn("cipher");
    catalog =
        new CatalogService(repository, vault, ssh, new DeviceNetworkAdapter(), root.toString());
    clearInvocations(repository);
    bridge = device("bridge", "bridge.tailnet", NetworkMode.TAILSCALE, List.of());
    when(repository.device("bridge")).thenReturn(Optional.of(bridge));
    when(repository.devices()).thenReturn(List.of(bridge));
  }

  @Test
  void newDirectTargetDiscoversThroughTailscaleBridge() {
    when(ssh.discover("private-host", 22, "tester", "fixture-password", "", List.of(bridge)))
        .thenReturn(new SshAdapter.DiscoveredHost("SHA256:verified", "/home/tester"));

    var saved = catalog.saveDevice(null, request(List.of("bridge")));

    assertThat(saved.networkMode()).isEqualTo(NetworkMode.DIRECT);
    assertThat(saved.jumpDeviceIds()).containsExactly("bridge");
    assertThat(saved.fingerprint()).isEqualTo("SHA256:verified");
    verify(ssh).discover("private-host", 22, "tester", "fixture-password", "", List.of(bridge));
    verify(ssh, never()).discover(anyString(), anyInt(), anyString(), anyString(), anyString());
  }

  @Test
  void changedHostUsesPreservedJumpChainWhenRequestOmitsIt() {
    when(repository.device("target"))
        .thenReturn(
            Optional.of(device("target", "old-host", NetworkMode.DIRECT, List.of("bridge"))));
    when(ssh.discover("private-host", 22, "tester", "fixture-password", "", List.of(bridge)))
        .thenReturn(new SshAdapter.DiscoveredHost("SHA256:verified", "/home/tester"));

    assertThat(catalog.saveDevice("target", request(null)).jumpDeviceIds())
        .containsExactly("bridge");
    verify(ssh).discover("private-host", 22, "tester", "fixture-password", "", List.of(bridge));
  }

  @Test
  void failedBridgeEnrollmentDoesNotSaveOrRetryDirectly() {
    when(ssh.discover("private-host", 22, "tester", "fixture-password", "", List.of(bridge)))
        .thenThrow(new WorkspaceException(502, "Bridge unavailable"));

    assertThatThrownBy(() -> catalog.saveDevice(null, request(List.of("bridge"))))
        .isInstanceOf(WorkspaceException.class);
    verify(repository, never()).save(any(DeviceRecord.class));
    verify(ssh, never()).discover(anyString(), anyInt(), anyString(), anyString(), anyString());
  }

  private DeviceRequest request(List<String> jumps) {
    return new DeviceRequest(
        "Target",
        "private-host",
        22,
        "tester",
        "fixture-password",
        null,
        "/home/tester",
        "NONE",
        3389,
        "",
        "",
        "",
        "",
        false,
        NetworkMode.DIRECT,
        jumps);
  }

  private DeviceRecord device(String id, String host, NetworkMode mode, List<String> jumps) {
    return new DeviceRecord(
        id,
        id,
        host,
        22,
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
        jumps);
  }
}
