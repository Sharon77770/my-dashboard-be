package com.personal.dashboard.database.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.dashboard.database.dto.DatabaseDto;
import com.personal.dashboard.database.entity.DatabaseConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists connection metadata and bounded SQL history in the workspace SQLite database. */
@Repository
@DependsOn("workspaceSchema")
public class DatabaseRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public DatabaseRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<DatabaseConnection> connections() {
    return jdbc.query(
        "SELECT * FROM database_connections ORDER BY name", (row, index) -> connection(row));
  }

  public Optional<DatabaseConnection> connection(String id) {
    return jdbc
        .query("SELECT * FROM database_connections WHERE id=?", (row, index) -> connection(row), id)
        .stream()
        .findFirst();
  }

  private DatabaseConnection connection(java.sql.ResultSet row) throws java.sql.SQLException {
    return new DatabaseConnection(
        row.getString("id"),
        row.getString("name"),
        row.getString("type"),
        row.getString("host"),
        row.getInt("port"),
        row.getString("database_name"),
        row.getString("username"),
        row.getString("credential_cipher"),
        row.getString("ssl_mode"),
        row.getString("access_mode"),
        metadata(row.getString("metadata")),
        row.getLong("created_at"),
        row.getLong("updated_at"));
  }

  public void save(DatabaseConnection value) {
    jdbc.update(
        "INSERT INTO database_connections(id,name,type,host,port,database_name,username,credential_cipher,ssl_mode,access_mode,metadata,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,type=excluded.type,host=excluded.host,port=excluded.port,database_name=excluded.database_name,username=excluded.username,credential_cipher=excluded.credential_cipher,ssl_mode=excluded.ssl_mode,access_mode=excluded.access_mode,metadata=excluded.metadata,updated_at=excluded.updated_at",
        value.id(),
        value.name(),
        value.type(),
        value.host(),
        value.port(),
        value.databaseName(),
        value.username(),
        value.credentialCipher(),
        value.sslMode(),
        value.accessMode(),
        serialize(value.metadata()),
        value.createdAt(),
        value.updatedAt());
  }

  private Map<String, String> metadata(String value) throws java.sql.SQLException {
    try {
      return json.readValue(value, new TypeReference<Map<String, String>>() {});
    } catch (JsonProcessingException exception) {
      throw new java.sql.SQLException("Invalid saved database metadata", exception);
    }
  }

  private String serialize(Map<String, String> value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Invalid database metadata", exception);
    }
  }

  public void delete(String id) {
    jdbc.update("DELETE FROM database_connections WHERE id=?", id);
  }

  public void history(DatabaseDto.History value) {
    jdbc.update(
        "INSERT INTO database_query_history(id,connection_id,connection_name,sql_text,executed_at,duration_ms,result_type,success,error_type) VALUES(?,?,?,?,?,?,?,?,?)",
        value.id(),
        value.connectionId(),
        value.connectionName(),
        value.sql(),
        value.timestamp(),
        value.durationMs(),
        value.resultType(),
        value.success(),
        value.errorType());
  }

  public List<DatabaseDto.History> history() {
    return jdbc.query(
        "SELECT * FROM database_query_history ORDER BY executed_at DESC LIMIT 100",
        (row, index) ->
            new DatabaseDto.History(
                row.getString("id"),
                row.getString("connection_id"),
                row.getString("connection_name"),
                row.getString("sql_text"),
                row.getLong("executed_at"),
                row.getLong("duration_ms"),
                row.getString("result_type"),
                row.getInt("success") == 1,
                row.getString("error_type")));
  }

  public DatabaseDto.Favorite favorite(
      String connectionId, String id, DatabaseDto.FavoriteRequest request) {
    long now = System.currentTimeMillis();
    jdbc.update(
        "INSERT INTO database_query_favorites(id,connection_id,name,sql_text,created_at) VALUES(?,?,?,?,?)",
        id,
        connectionId,
        request.name().trim(),
        request.sql(),
        now);
    return new DatabaseDto.Favorite(id, connectionId, request.name().trim(), request.sql(), now);
  }

  public List<DatabaseDto.Favorite> favorites(String connectionId) {
    return jdbc.query(
        "SELECT * FROM database_query_favorites WHERE connection_id=? ORDER BY created_at DESC",
        (row, index) ->
            new DatabaseDto.Favorite(
                row.getString("id"),
                row.getString("connection_id"),
                row.getString("name"),
                row.getString("sql_text"),
                row.getLong("created_at")),
        connectionId);
  }

  public int deleteFavorite(String connectionId, String id) {
    return jdbc.update(
        "DELETE FROM database_query_favorites WHERE connection_id=? AND id=?", connectionId, id);
  }
}
