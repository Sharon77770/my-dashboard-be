package com.personal.dashboard.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.database.adapter.*;
import com.personal.dashboard.database.entity.DatabaseConnection;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.*;
import com.personal.dashboard.global.security.CredentialVault;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseTargetAdapterTest {
  @Test
  void resolvesCurrentContainerAddressWithoutReadingEnvironment() {
    var catalog = mock(CatalogService.class);
    var commands = mock(CommandAdapter.class);
    var ssh = mock(SshAdapter.class);
    var device = mock(DeviceRecord.class);
    when(device.id()).thenReturn("local");
    when(catalog.requireDevice("local")).thenReturn(device);
    var targets = new DatabaseTargetAdapter(catalog, commands, ssh, new ObjectMapper());
    var item = connection("POSTGRESQL");
    when(commands.execute(eq(device), anyString()))
        .thenReturn(
            "{\"running\":true,\"networks\":{\"bridge\":{\"IPAddress\":\"172.20.0.2\"}}}",
            "{\"running\":true,\"networks\":{\"bridge\":{\"IPAddress\":\"172.20.0.3\"}}}");
    try (var first = targets.open(item);
        var second = targets.open(item)) {
      assertEquals("172.20.0.2", first.host());
      assertEquals("172.20.0.3", second.host());
    }
    verify(commands, times(2))
        .execute(
            eq(device),
            argThat(
                command ->
                    command.contains("--type container") && !command.contains(".Config.Env")));
    verifyNoInteractions(ssh);
  }

  @Test
  void rejectsRemoteSqliteAndShellFragmentsBeforeExternalCalls() {
    var catalog = mock(CatalogService.class);
    var commands = mock(CommandAdapter.class);
    var ssh = mock(SshAdapter.class);
    var device = mock(DeviceRecord.class);
    when(device.id()).thenReturn("local");
    when(catalog.requireDevice("local")).thenReturn(device);
    var targets = new DatabaseTargetAdapter(catalog, commands, ssh, new ObjectMapper());
    assertThrows(
        WorkspaceException.class, () -> targets.validate("DOCKER", "local", "db;id", "POSTGRESQL"));
    assertThrows(WorkspaceException.class, () -> targets.validate("DEVICE", "local", "", "SQLITE"));
    assertThrows(
        WorkspaceException.class, () -> targets.validate("DIRECT", "other", "", "POSTGRESQL"));
    when(commands.execute(eq(device), anyString()))
        .thenReturn("permission denied: cannot access Docker");
    var failure = assertThrows(WorkspaceException.class, () -> targets.containers("local"));
    assertFalse(failure.getMessage().contains("permission denied:"));
    verifyNoInteractions(ssh);
  }

  @Test
  void closesForwardingLeaseWhenJdbcSetupFails() throws Exception {
    var targets = mock(DatabaseTargetAdapter.class);
    try (var listener = new ServerSocket(0)) {
      var endpoint =
          new DatabaseTargetAdapter.Endpoint(
              "127.0.0.1", listener.getLocalPort(), null, listener, null);
      when(targets.open(any())).thenReturn(endpoint);
      var adapter =
          new DatabaseAdapter(mock(CredentialVault.class), mock(CatalogService.class), targets);
      assertThrows(WorkspaceException.class, () -> adapter.open(connection("UNSUPPORTED")));
      assertTrue(listener.isClosed());
    }
  }

  private DatabaseConnection connection(String type) {
    return new DatabaseConnection(
        "id",
        "Database",
        type,
        "127.0.0.1",
        5432,
        "studio",
        "studio",
        "cipher",
        "DISABLE",
        "READ_ONLY",
        Map.of(),
        1,
        1,
        "DOCKER",
        "local",
        "db");
  }
}
