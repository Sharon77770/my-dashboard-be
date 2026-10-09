package com.personal.dashboard.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.database.adapter.DatabaseAdapter;
import com.personal.dashboard.database.entity.DatabaseConnection;
import com.personal.dashboard.global.security.CredentialVault;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Optional live JDBC checks run against disposable PostgreSQL/MySQL servers. */
class DatabaseRemoteAdapterTest {
  @TempDir Path directory;

  @Test
  void postgresMetadataAndReadOnlyTransaction() throws Exception {
    verifyServer("POSTGRESQL", "DATABASE_STUDIO_PG_PORT", 5432, "public");
  }

  @Test
  void mysqlMetadataAndReadOnlyTransaction() throws Exception {
    verifyServer("MYSQL", "DATABASE_STUDIO_MYSQL_PORT", 3306, "studio");
  }

  @Test
  void mariaDbDriverPathAgainstMysqlServer() throws Exception {
    verifyServer("MARIADB", "DATABASE_STUDIO_MYSQL_PORT", 3306, "studio");
  }

  private void verifyServer(String type, String portVariable, int defaultPort, String schema)
      throws Exception {
    String portText = System.getenv(portVariable);
    String password = System.getenv("DATABASE_STUDIO_TEST_PASSWORD");
    assumeTrue(portText != null && password != null, "Disposable DB server is not configured");
    int port = portText.isBlank() ? defaultPort : Integer.parseInt(portText);
    CredentialVault vault = new CredentialVault(directory.resolve("key").toString());
    DatabaseAdapter adapter =
        new DatabaseAdapter(
            vault,
            mock(CatalogService.class),
            mock(com.personal.dashboard.database.adapter.DatabaseTargetAdapter.class));
    DatabaseConnection write =
        new DatabaseConnection(
            "test",
            "Test",
            type,
            "127.0.0.1",
            port,
            "studio",
            "studio",
            vault.encrypt(password),
            "DISABLE",
            "READ_WRITE",
            java.util.Map.of(),
            1,
            1);
    assertTrue(adapter.test(write).connected());
    adapter.execute(
        write,
        "create",
        "CREATE TABLE IF NOT EXISTS studio_records (id INTEGER PRIMARY KEY, name VARCHAR(50))",
        value -> {});
    adapter.execute(write, "clear", "DELETE FROM studio_records", value -> {});
    adapter.execute(
        write, "insert", "INSERT INTO studio_records (id,name) VALUES (1,'alpha')", value -> {});
    assertTrue(adapter.schemas(write).stream().anyMatch(value -> value.name().equals(schema)));
    assertTrue(
        adapter.tables(write, schema).stream()
            .anyMatch(value -> value.name().equals("studio_records")));
    assertEquals(
        List.of("id", "name"),
        adapter.describe(write, schema, "studio_records").columns().stream()
            .map(value -> value.name())
            .toList());
    assertEquals(
        1, adapter.rows(write, schema, "studio_records", 0, 25, "id", "ASC", "", "").total());
    DatabaseConnection readOnly =
        new DatabaseConnection(
            write.id(),
            write.name(),
            write.type(),
            write.host(),
            write.port(),
            write.databaseName(),
            write.username(),
            write.credentialCipher(),
            write.sslMode(),
            "READ_ONLY",
            write.metadata(),
            write.createdAt(),
            write.updatedAt());
    assertThrows(
        SQLException.class,
        () -> adapter.execute(readOnly, "write", "DELETE FROM studio_records", value -> {}));
  }
}
