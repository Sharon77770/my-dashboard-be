package com.personal.dashboard.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.database.adapter.DatabaseAdapter;
import com.personal.dashboard.database.entity.DatabaseConnection;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SQLite integration proves metadata, pagination, bounds, and server read-only enforcement. */
class DatabaseAdapterTest {
  @TempDir Path root;

  @Test
  void postgresMysqlAndMariaDbDriversAreAvailable() throws Exception {
    assertNotNull(java.sql.DriverManager.getDriver("jdbc:postgresql://localhost:5432/sample"));
    assertNotNull(java.sql.DriverManager.getDriver("jdbc:mysql://localhost:3306/sample"));
    assertNotNull(java.sql.DriverManager.getDriver("jdbc:mariadb://localhost:3306/sample"));
  }

  @Test
  void timeoutIsMappedWithoutRawDriverMessage() {
    DatabaseAdapter adapter =
        new DatabaseAdapter(
            mock(CredentialVault.class),
            mock(CatalogService.class),
            mock(com.personal.dashboard.database.adapter.DatabaseTargetAdapter.class));
    assertEquals(
        "CONNECTION_TIMEOUT",
        adapter.errorType(new java.sql.SQLTimeoutException("secret host name")));
  }

  @Test
  void browsesExistingFileAndRejectsWritesOnReadOnlyConnection() throws Exception {
    Path file = Files.createFile(root.resolve("sample.db"));
    CatalogService catalog = mock(CatalogService.class);
    DeviceRecord local = mock(DeviceRecord.class);
    when(local.rootPath()).thenReturn(root.toString());
    when(catalog.requireDevice("local")).thenReturn(local);
    DatabaseAdapter adapter =
        new DatabaseAdapter(
            mock(CredentialVault.class),
            catalog,
            mock(com.personal.dashboard.database.adapter.DatabaseTargetAdapter.class));
    DatabaseConnection write = connection(file, "READ_WRITE");
    try (Connection connection = adapter.open(write);
        var statement = connection.createStatement()) {
      statement.execute("CREATE TABLE entries (id INTEGER PRIMARY KEY, name TEXT, payload BLOB)");
      statement.execute("INSERT INTO entries VALUES (1, 'alpha', x'ABCD'), (2, 'beta', x'ABCD')");
    }
    assertTrue(adapter.schemas(write).stream().anyMatch(schema -> schema.name().equals("main")));
    assertTrue(
        adapter.tables(write, "main").stream().anyMatch(table -> table.name().equals("entries")));
    assertTrue(
        adapter.describe(write, "main", "entries").columns().stream()
            .anyMatch(column -> column.primaryKey()));
    var page = adapter.rows(write, "main", "entries", 0, 1, "id", "DESC", "name", "a");
    assertEquals(1, page.rows().size());
    assertEquals(2, page.total());
    assertEquals("[binary data]", page.rows().getFirst().get("payload"));
    assertThrows(
        WorkspaceException.class,
        () ->
            adapter.rows(write, "main", "entries", 0, 1, "id; DROP TABLE entries", "ASC", "", ""));
    assertThrows(
        Exception.class,
        () ->
            adapter.execute(
                connection(file, "READ_ONLY"), "id", "DELETE FROM entries", value -> {}));
  }

  @Test
  void fileOutsideRegisteredServerRootIsRejected() throws Exception {
    Path file = Files.createTempFile("outside-db", ".db");
    try {
      CatalogService catalog = mock(CatalogService.class);
      DeviceRecord local = mock(DeviceRecord.class);
      when(local.rootPath()).thenReturn(root.toString());
      when(catalog.requireDevice("local")).thenReturn(local);
      DatabaseAdapter adapter =
          new DatabaseAdapter(
              mock(CredentialVault.class),
              catalog,
              mock(com.personal.dashboard.database.adapter.DatabaseTargetAdapter.class));
      assertThrows(WorkspaceException.class, () -> adapter.open(connection(file, "READ_ONLY")));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private DatabaseConnection connection(Path file, String mode) {
    return new DatabaseConnection(
        "id",
        "DB",
        "SQLITE",
        "",
        0,
        file.toString(),
        "",
        "",
        "DISABLE",
        mode,
        java.util.Map.of(),
        1,
        1);
  }
}
