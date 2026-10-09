package com.personal.dashboard.database.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

/** Public Database Studio contracts; credential input has no response counterpart. */
public final class DatabaseDto {
  private DatabaseDto() {}

  public record ConnectionRequest(
      @NotBlank @Size(max = 100) String name,
      @NotBlank String type,
      @Size(max = 255) String host,
      Integer port,
      @NotBlank @Size(max = 500) String databaseName,
      @Size(max = 100) String username,
      @Size(max = 500) String credential,
      @NotBlank String sslMode,
      @NotBlank String accessMode,
      Map<String, String> metadata,
      @Pattern(regexp = "DIRECT|DEVICE|DOCKER") String targetMode,
      @Size(max = 100) String deviceId,
      @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}|^$") String containerId) {
    public ConnectionRequest(
        String name,
        String type,
        String host,
        Integer port,
        String databaseName,
        String username,
        String credential,
        String sslMode,
        String accessMode,
        Map<String, String> metadata) {
      this(
          name,
          type,
          host,
          port,
          databaseName,
          username,
          credential,
          sslMode,
          accessMode,
          metadata,
          "DIRECT",
          "",
          "");
    }
  }

  public record ConnectionView(
      String id,
      String name,
      String type,
      String host,
      int port,
      String databaseName,
      String username,
      boolean passwordConfigured,
      String sslMode,
      String accessMode,
      Map<String, String> metadata,
      long createdAt,
      long updatedAt,
      String targetMode,
      String deviceId,
      String containerId) {
    public ConnectionView(
        String id,
        String name,
        String type,
        String host,
        int port,
        String databaseName,
        String username,
        boolean passwordConfigured,
        String sslMode,
        String accessMode,
        Map<String, String> metadata,
        long createdAt,
        long updatedAt) {
      this(
          id,
          name,
          type,
          host,
          port,
          databaseName,
          username,
          passwordConfigured,
          sslMode,
          accessMode,
          metadata,
          createdAt,
          updatedAt,
          "DIRECT",
          "",
          "");
    }
  }

  public record Container(String id, String name, String image, String ports) {}

  public record TestResult(boolean connected, String version, long latencyMs, String errorType) {}

  public record Schema(String name) {}

  public record Table(String schema, String name, String kind) {}

  public record Function(String schema, String name) {}

  public record Column(String name, String type, boolean nullable, boolean primaryKey) {}

  public record ForeignKey(
      String column, String targetSchema, String targetTable, String targetColumn) {}

  public record Index(String name, String column, boolean unique) {}

  public record TableDetail(
      Table table, List<Column> columns, List<ForeignKey> foreignKeys, List<Index> indexes) {}

  public record Page(
      List<String> columns, List<Map<String, Object>> rows, long total, int page, int size) {}

  public record QueryRequest(@NotBlank @Size(max = 100000) String sql, boolean confirmed) {}

  public record QueryResult(
      String executionId,
      String state,
      String resultType,
      List<String> columns,
      List<Map<String, Object>> rows,
      int affectedRows,
      long durationMs,
      String errorType) {}

  public record History(
      String id,
      String connectionId,
      String connectionName,
      String sql,
      long timestamp,
      long durationMs,
      String resultType,
      boolean success,
      String errorType) {}

  public record Favorite(String id, String connectionId, String name, String sql, long createdAt) {}

  public record FavoriteRequest(
      @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100000) String sql) {}
}
