package com.personal.dashboard.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personal.dashboard.database.adapter.DatabaseAdapter;
import com.personal.dashboard.database.dto.DatabaseDto;
import com.personal.dashboard.database.entity.DatabaseConnection;
import com.personal.dashboard.database.repository.DatabaseRepository;
import com.personal.dashboard.database.service.DatabaseStudioService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Checks storage secrecy and server-side SQL permission decisions. */
class DatabaseStudioServiceTest {
  private final DatabaseRepository repository = mock(DatabaseRepository.class);
  private final DatabaseAdapter adapter = mock(DatabaseAdapter.class);
  private final CredentialVault vault = mock(CredentialVault.class);
  private final DatabaseStudioService studio =
      new DatabaseStudioService(repository, adapter, vault);

  @AfterEach
  void close() {
    studio.close();
  }

  @Test
  void credentialIsEncryptedAndNeverReturned() {
    when(vault.encrypt("example-secret")).thenReturn("ciphertext");
    DatabaseDto.ConnectionView view =
        studio.save(
            null,
            new DatabaseDto.ConnectionRequest(
                "Production",
                "POSTGRESQL",
                "db.example",
                5432,
                "production",
                "owner",
                "example-secret",
                "REQUIRE",
                "READ_ONLY",
                java.util.Map.of("team", "operations")));
    assertTrue(view.passwordConfigured());
    assertFalse(view.toString().contains("example-secret"));
    assertFalse(view.toString().contains("ciphertext"));
    assertEquals("operations", view.metadata().get("team"));
    verify(repository).save(argThat(value -> value.credentialCipher().equals("ciphertext")));
  }

  @Test
  void credentialLikeMetadataKeyIsRejectedBeforeStorage() {
    when(vault.encrypt("example-secret")).thenReturn("ciphertext");
    assertEquals(
        400,
        assertThrows(
                WorkspaceException.class,
                () ->
                    studio.save(
                        null,
                        new DatabaseDto.ConnectionRequest(
                            "Production",
                            "POSTGRESQL",
                            "db.example",
                            5432,
                            "production",
                            "owner",
                            "example-secret",
                            "REQUIRE",
                            "READ_ONLY",
                            java.util.Map.of("api_token", "secret"))))
            .status());
    verify(repository, never()).save(any());
  }

  @Test
  void draftConnectionTestDoesNotPersistCredential() {
    when(vault.encrypt("example-secret")).thenReturn("ciphertext");
    when(adapter.test(any())).thenReturn(new DatabaseDto.TestResult(true, "PostgreSQL", 12, ""));
    var result =
        studio.testDraft(
            null,
            new DatabaseDto.ConnectionRequest(
                "Draft",
                "POSTGRESQL",
                "db.example",
                5432,
                "production",
                "owner",
                "example-secret",
                "REQUIRE",
                "READ_ONLY",
                java.util.Map.of()));
    assertTrue(result.connected());
    verify(adapter).test(argThat(value -> value.credentialCipher().equals("ciphertext")));
    verify(repository, never()).save(any());
  }

  @Test
  void readOnlyBlocksMutationBeforeJdbc() {
    when(repository.connection("id")).thenReturn(Optional.of(connection("READ_ONLY")));
    assertEquals(
        403,
        assertThrows(
                WorkspaceException.class,
                () -> studio.run("id", new DatabaseDto.QueryRequest("DELETE FROM records", true)))
            .status());
    verifyNoInteractions(adapter);
  }

  @Test
  void dangerousMutationRequiresServerConfirmation() {
    when(repository.connection("id")).thenReturn(Optional.of(connection("READ_WRITE")));
    assertEquals(
        409,
        assertThrows(
                WorkspaceException.class,
                () ->
                    studio.run(
                        "id",
                        new DatabaseDto.QueryRequest("UPDATE records SET text='WHERE'", false)))
            .status());
    assertEquals(
        409,
        assertThrows(
                WorkspaceException.class,
                () -> studio.run("id", new DatabaseDto.QueryRequest("DROP TABLE records", false)))
            .status());
    verifyNoInteractions(adapter);
  }

  @Test
  void malformedConnectionHostCannotBecomeJdbcUrl() {
    assertEquals(
        400,
        assertThrows(
                WorkspaceException.class,
                () ->
                    studio.save(
                        null,
                        new DatabaseDto.ConnectionRequest(
                            "Bad",
                            "MYSQL",
                            "host?x=1",
                            3306,
                            "sample",
                            "user",
                            "password",
                            "DISABLE",
                            "READ_ONLY",
                            java.util.Map.of())))
            .status());
  }

  @Test
  void completedQueryRecordsHistoryWithoutCredential() throws Exception {
    when(repository.connection("id")).thenReturn(Optional.of(connection("READ_WRITE")));
    when(adapter.execute(any(), anyString(), anyString(), any()))
        .thenAnswer(
            call ->
                new DatabaseDto.QueryResult(
                    call.getArgument(1),
                    "SUCCEEDED",
                    "QUERY",
                    java.util.List.of("id"),
                    java.util.List.of(),
                    0,
                    7,
                    ""));
    var started = studio.run("id", new DatabaseDto.QueryRequest("SELECT id FROM entries", false));
    verify(repository, timeout(2000))
        .history(
            argThat(
                value ->
                    value.success()
                        && value.sql().equals("SELECT id FROM entries")
                        && value.durationMs() == 7));
    assertEquals("SUCCEEDED", studio.result("id", started.executionId()).state());
  }

  @Test
  void sensitiveSqlIsNotStoredInFavoritesOrHistory() throws Exception {
    when(repository.connection("id")).thenReturn(Optional.of(connection("READ_WRITE")));
    String sql = "SELECT password FROM users";
    assertEquals(
        400,
        assertThrows(
                WorkspaceException.class,
                () -> studio.favorite("id", new DatabaseDto.FavoriteRequest("unsafe", sql)))
            .status());
    verify(repository, never()).favorite(anyString(), anyString(), any());
    when(adapter.execute(any(), anyString(), anyString(), any()))
        .thenAnswer(
            call ->
                new DatabaseDto.QueryResult(
                    call.getArgument(1),
                    "SUCCEEDED",
                    "QUERY",
                    java.util.List.of(),
                    java.util.List.of(),
                    0,
                    1,
                    ""));
    studio.run("id", new DatabaseDto.QueryRequest(sql, false));
    verify(repository, timeout(2000))
        .history(argThat(value -> value.sql().equals("[sensitive query omitted]")));
  }

  @Test
  void cancelSignalsActiveStatement() throws Exception {
    when(repository.connection("id")).thenReturn(Optional.of(connection("READ_WRITE")));
    var active = new java.util.concurrent.CountDownLatch(1);
    var statement = mock(java.sql.Statement.class);
    when(adapter.execute(any(), anyString(), anyString(), any()))
        .thenAnswer(
            call -> {
              java.util.function.Consumer<java.sql.Statement> callback = call.getArgument(3);
              callback.accept(statement);
              active.countDown();
              try {
                Thread.sleep(5000);
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
              callback.accept(null);
              return new DatabaseDto.QueryResult(
                  call.getArgument(1),
                  "SUCCEEDED",
                  "QUERY",
                  java.util.List.of(),
                  java.util.List.of(),
                  0,
                  1,
                  "");
            });
    var started = studio.run("id", new DatabaseDto.QueryRequest("SELECT id FROM entries", false));
    assertTrue(active.await(2, java.util.concurrent.TimeUnit.SECONDS));
    assertEquals("CANCELLED", studio.cancel("id", started.executionId()).state());
    verify(statement).cancel();
  }

  private DatabaseConnection connection(String mode) {
    return new DatabaseConnection(
        "id",
        "DB",
        "SQLITE",
        "",
        0,
        "/tmp/sample.db",
        "",
        "",
        "DISABLE",
        mode,
        java.util.Map.of(),
        1,
        1);
  }
}
