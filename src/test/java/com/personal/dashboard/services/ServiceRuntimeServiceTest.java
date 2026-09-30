package com.personal.dashboard.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.dto.DeviceStatus;
import com.personal.dashboard.catalog.service.DeviceOperations;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.service.ServiceCatalogService;
import com.personal.dashboard.services.service.ServiceRuntimeService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runtime reads and actions stay scoped to saved Service bindings. */
class ServiceRuntimeServiceTest {
  private final ServiceCatalogService catalog = mock(ServiceCatalogService.class);
  private final DeviceOperations devices = mock(DeviceOperations.class);
  private final ServiceRuntimeService runtime =
      new ServiceRuntimeService(catalog, devices, new ObjectMapper());
  private final ServiceDto.Resource device =
      new ServiceDto.Resource("device-binding", "service", "DEVICE", "server", "", "", 1, false);
  private final ServiceDto.Resource container =
      new ServiceDto.Resource(
          "container-binding", "service", "DOCKER_CONTAINER", "api", "server", "", 1, false);

  @Test
  void snapshotsIncludeLiveMetricsAndContainerState() {
    when(catalog.resources("service")).thenReturn(List.of(device, container));
    when(devices.status("server"))
        .thenReturn(new DeviceStatus("ONLINE", 42.0, 36.0, 18.0, "SSH Linux 계측", 123));
    when(devices.containers("server"))
        .thenReturn("{\"Names\":\"api\",\"Status\":\"Up 2 hours\",\"Image\":\"api:v1\"}\n");

    var snapshots = runtime.snapshots("service");

    assertEquals(42.0, snapshots.getFirst().cpu());
    assertEquals(18.0, snapshots.getFirst().disk());
    assertEquals("RUNNING", snapshots.get(1).state());
    assertEquals("api:v1", snapshots.get(1).image());
  }

  @Test
  void logsAndActionsUseSavedContainerTarget() {
    when(catalog.resources("service")).thenReturn(List.of(device, container));
    when(devices.dockerLogs("server", "api")).thenReturn("recent log");

    assertEquals("recent log", runtime.logs("service", "container-binding").output());
    runtime.action("service", "container-binding", new ServiceDto.RuntimeActionRequest("restart"));

    verify(devices).dockerLogs("server", "api");
    verify(devices).docker("server", "api", "restart");
    assertThrows(WorkspaceException.class, () -> runtime.logs("service", "device-binding"));
    assertThrows(WorkspaceException.class, () -> runtime.logs("service", "unbound"));
    verifyNoMoreInteractions(devices);
  }

  @Test
  void deletedDeviceBlocksContainerOperations() {
    var orphan =
        new ServiceDto.Resource(
            "container-binding", "service", "DOCKER_CONTAINER", "api", "server", "", 1, true);
    when(catalog.resources("service")).thenReturn(List.of(orphan));

    assertEquals("UNKNOWN", runtime.snapshots("service").getFirst().state());
    assertThrows(
        WorkspaceException.class,
        () ->
            runtime.action(
                "service", "container-binding", new ServiceDto.RuntimeActionRequest("stop")));
    verifyNoInteractions(devices);
  }
}
