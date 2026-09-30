package com.personal.dashboard.catalog.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.CommandAdapter;
import org.junit.jupiter.api.Test;

/** Docker log reads use a fixed command and reject shell syntax in container names. */
class DeviceOperationsLogsTest {
  @Test
  void readsOnlyRecentLogsForValidatedContainer() {
    CatalogService catalog = mock(CatalogService.class);
    CommandAdapter commands = mock(CommandAdapter.class);
    DeviceRecord device = mock(DeviceRecord.class);
    when(catalog.requireDevice("server")).thenReturn(device);
    DeviceOperations operations = new DeviceOperations(catalog, commands);

    operations.dockerLogs("server", "api-1");

    verify(commands).execute(device, "docker logs --tail 200 --timestamps api-1");
    assertThrows(
        WorkspaceException.class, () -> operations.dockerLogs("server", "api;cat /etc/passwd"));
    verifyNoMoreInteractions(commands);
  }
}
