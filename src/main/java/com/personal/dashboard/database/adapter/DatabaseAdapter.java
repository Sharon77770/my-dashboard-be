package com.personal.dashboard.database.adapter;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.database.dto.DatabaseDto;
import com.personal.dashboard.database.entity.DatabaseConnection;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Isolates JDBC dialect, metadata, bounded results and connection lifecycle from use cases. */
@Component
public class DatabaseAdapter {
  private static final Logger log = LoggerFactory.getLogger(DatabaseAdapter.class);
  private final CredentialVault vault;
  private final CatalogService catalog;
  private final DatabaseTargetAdapter targets;

  public DatabaseAdapter(
      CredentialVault vault, CatalogService catalog, DatabaseTargetAdapter targets) {
    this.vault = vault;
    this.catalog = catalog;
    this.targets = targets;
  }

  public Connection open(DatabaseConnection item) throws SQLException {
    var endpoint =
        DatabaseTargetAdapter.DIRECT.equals(item.targetMode())
            ? new DatabaseTargetAdapter.Endpoint(item.host(), item.port(), null, null, null)
            : targets.open(item);
    try {
      Connection connection = openAt(item, endpoint.host(), endpoint.port());
      return (Connection)
          java.lang.reflect.Proxy.newProxyInstance(
              Connection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                try {
                  return method.invoke(connection, args);
                } catch (java.lang.reflect.InvocationTargetException error) {
                  throw error.getCause();
                } finally {
                  if (method.getName().equals("close") || method.getName().equals("abort"))
                    endpoint.close();
                }
              });
    } catch (SQLException | RuntimeException error) {
      endpoint.close();
      throw error;
    }
  }

  private Connection openAt(DatabaseConnection item, String address, int port) throws SQLException {
    String url;
    Properties properties = new Properties();
    if (item.type().equals("SQLITE")) {
      try {
        Path root = Path.of(catalog.requireDevice("local").rootPath()).toRealPath();
        if (item.databaseName().contains("?") || item.databaseName().contains("#"))
          throw new WorkspaceException(400, "SQLite 파일 경로를 확인해 주세요.");
        Path file = Path.of(item.databaseName()).toRealPath();
        if (!file.startsWith(root) || !Files.isRegularFile(file))
          throw new WorkspaceException(400, "서버 파일 영역의 SQLite 파일을 선택해 주세요.");
        url = "jdbc:sqlite:" + file;
      } catch (java.io.IOException | java.nio.file.InvalidPathException exception) {
        throw new WorkspaceException(400, "SQLite 파일을 찾을 수 없습니다.");
      }
    } else {
      String host = address;
      String database = item.databaseName();
      if (!host.matches("[A-Za-z0-9_.:-]{1,255}") || !database.matches("[A-Za-z0-9_-]{1,100}"))
        throw new WorkspaceException(400, "호스트와 데이터베이스 이름을 확인해 주세요.");
      if (item.type().equals("POSTGRESQL")) {
        url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        properties.setProperty("readOnlyMode", "transaction");
        properties.setProperty("sslmode", item.sslMode().equals("REQUIRE") ? "require" : "disable");
      } else if (item.type().equals("MYSQL")) {
        url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "20000");
        properties.setProperty("readOnlyPropagatesToServer", "true");
        properties.setProperty(
            "sslMode", item.sslMode().equals("REQUIRE") ? "REQUIRED" : "DISABLED");
      } else if (item.type().equals("MARIADB")) {
        url = "jdbc:mariadb://" + host + ":" + port + "/" + database;
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "20000");
        properties.setProperty("allowLocalInfile", "false");
        properties.setProperty(
            "sslMode", item.sslMode().equals("REQUIRE") ? "verify-full" : "disable");
      } else throw new WorkspaceException(400, "지원하지 않는 데이터베이스입니다.");
      properties.setProperty("user", item.username());
      properties.setProperty("password", vault.decrypt(item.credentialCipher()));
    }
    Connection connection = DriverManager.getConnection(url, properties);
    try {
      if (item.accessMode().equals("READ_ONLY")) {
        if (!item.type().equals("SQLITE")) connection.setReadOnly(true);
        if (item.type().equals("SQLITE")) {
          try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only=ON");
          }
        }
      }
      return connection;
    } catch (SQLException | RuntimeException exception) {
      try {
        connection.close();
      } catch (SQLException closeFailure) {
        exception.addSuppressed(closeFailure);
      }
      throw exception;
    }
  }

  public DatabaseDto.TestResult test(DatabaseConnection item) {
    long start = System.nanoTime();
    try (Connection connection = open(item)) {
      return new DatabaseDto.TestResult(
          true,
          connection.getMetaData().getDatabaseProductName()
              + " "
              + connection.getMetaData().getDatabaseProductVersion(),
          elapsed(start),
          "");
    } catch (SQLException exception) {
      log.warn(
          "Database connection test failed: type={}, sqlState={}, vendorCode={}",
          item.type(),
          exception.getSQLState(),
          exception.getErrorCode());
      return new DatabaseDto.TestResult(false, "", elapsed(start), errorType(exception));
    } catch (WorkspaceException exception) {
      return new DatabaseDto.TestResult(
          false,
          "",
          elapsed(start),
          item.type().equals("SQLITE") ? "FILE_UNAVAILABLE" : "TARGET_UNAVAILABLE");
    }
  }

  public List<DatabaseDto.Schema> schemas(DatabaseConnection item) throws SQLException {
    if (item.type().equals("SQLITE") || List.of("MYSQL", "MARIADB").contains(item.type())) {
      try (Connection ignored = open(item)) {
        return List.of(
            new DatabaseDto.Schema(item.type().equals("SQLITE") ? "main" : item.databaseName()));
      }
    }
    try (Connection connection = open(item);
        ResultSet result = connection.getMetaData().getSchemas()) {
      List<DatabaseDto.Schema> values = new ArrayList<>();
      while (result.next()) values.add(new DatabaseDto.Schema(result.getString("TABLE_SCHEM")));
      if (values.isEmpty())
        values.add(
            new DatabaseDto.Schema(item.type().equals("SQLITE") ? "main" : item.databaseName()));
      return values;
    }
  }

  public List<DatabaseDto.Table> tables(DatabaseConnection item, String schema)
      throws SQLException {
    requireSchema(item, schema);
    try (Connection connection = open(item);
        ResultSet result =
            connection
                .getMetaData()
                .getTables(
                    catalogName(item),
                    schemaName(item, schema),
                    "%",
                    new String[] {"TABLE", "VIEW"})) {
      List<DatabaseDto.Table> values = new ArrayList<>();
      while (result.next())
        values.add(
            new DatabaseDto.Table(
                schema, result.getString("TABLE_NAME"), result.getString("TABLE_TYPE")));
      return values;
    }
  }

  public List<DatabaseDto.Function> functions(DatabaseConnection item, String schema)
      throws SQLException {
    requireSchema(item, schema);
    if (item.type().equals("SQLITE")) return List.of();
    try (Connection connection = open(item);
        ResultSet result =
            connection
                .getMetaData()
                .getFunctions(catalogName(item), schemaName(item, schema), "%")) {
      List<DatabaseDto.Function> values = new ArrayList<>();
      while (result.next() && values.size() < 200)
        values.add(new DatabaseDto.Function(schema, result.getString("FUNCTION_NAME")));
      return values;
    } catch (SQLFeatureNotSupportedException exception) {
      return List.of();
    }
  }

  public DatabaseDto.TableDetail describe(DatabaseConnection item, String schema, String table)
      throws SQLException {
    requireTable(item, schema, table);
    try (Connection connection = open(item)) {
      DatabaseMetaData metadata = connection.getMetaData();
      String catalogName = catalogName(item);
      String schemaName = schemaName(item, schema);
      Set<String> keys = new HashSet<>();
      try (ResultSet result = metadata.getPrimaryKeys(catalogName, schemaName, table)) {
        while (result.next()) keys.add(result.getString("COLUMN_NAME"));
      }
      List<DatabaseDto.Column> columns = new ArrayList<>();
      try (ResultSet result = metadata.getColumns(catalogName, schemaName, table, "%")) {
        while (result.next())
          columns.add(
              new DatabaseDto.Column(
                  result.getString("COLUMN_NAME"),
                  result.getString("TYPE_NAME"),
                  result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                  keys.contains(result.getString("COLUMN_NAME"))));
      }
      List<DatabaseDto.ForeignKey> foreignKeys = new ArrayList<>();
      try (ResultSet result = metadata.getImportedKeys(catalogName, schemaName, table)) {
        while (result.next())
          foreignKeys.add(
              new DatabaseDto.ForeignKey(
                  result.getString("FKCOLUMN_NAME"),
                  result.getString("PKTABLE_SCHEM"),
                  result.getString("PKTABLE_NAME"),
                  result.getString("PKCOLUMN_NAME")));
      }
      List<DatabaseDto.Index> indexes = new ArrayList<>();
      try (ResultSet result = metadata.getIndexInfo(catalogName, schemaName, table, false, true)) {
        while (result.next())
          if (result.getString("COLUMN_NAME") != null)
            indexes.add(
                new DatabaseDto.Index(
                    result.getString("INDEX_NAME"),
                    result.getString("COLUMN_NAME"),
                    !result.getBoolean("NON_UNIQUE")));
      }
      return new DatabaseDto.TableDetail(
          new DatabaseDto.Table(schema, table, "TABLE"), columns, foreignKeys, indexes);
    }
  }

  public DatabaseDto.Page rows(
      DatabaseConnection item,
      String schema,
      String table,
      int page,
      int size,
      String sort,
      String direction,
      String filterColumn,
      String filter)
      throws SQLException {
    DatabaseDto.TableDetail detail = describe(item, schema, table);
    Set<String> columns = new HashSet<>();
    detail.columns().forEach(column -> columns.add(column.name()));
    if (sort != null && !sort.isBlank() && !columns.contains(sort))
      throw new WorkspaceException(400, "정렬 컬럼을 확인해 주세요.");
    if (filterColumn != null && !filterColumn.isBlank() && !columns.contains(filterColumn))
      throw new WorkspaceException(400, "필터 컬럼을 확인해 주세요.");
    if (!List.of("ASC", "DESC").contains(direction.toUpperCase(Locale.ROOT)))
      throw new WorkspaceException(400, "정렬 방향을 확인해 주세요.");
    try (Connection connection = open(item)) {
      String quoted = qualified(connection, item, schema, table);
      String where =
          filterColumn == null || filterColumn.isBlank()
              ? ""
              : " WHERE CAST(" + quote(connection, filterColumn) + " AS VARCHAR(1000)) LIKE ?";
      List<Map<String, Object>> rows = new ArrayList<>();
      int[] remaining = {1_048_576};
      String sql =
          "SELECT * FROM "
              + quoted
              + where
              + (sort == null || sort.isBlank()
                  ? ""
                  : " ORDER BY "
                      + quote(connection, sort)
                      + " "
                      + direction.toUpperCase(Locale.ROOT))
              + " LIMIT ? OFFSET ?";
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setQueryTimeout(15);
        statement.setMaxFieldSize(16_000);
        int index = 1;
        if (!where.isEmpty()) statement.setString(index++, "%" + filter + "%");
        statement.setInt(index++, size);
        statement.setInt(index, page * size);
        try (ResultSet result = statement.executeQuery()) {
          while (result.next()) rows.add(row(result, remaining));
        }
      }
      long total = -1;
      try (PreparedStatement statement =
          connection.prepareStatement("SELECT COUNT(*) FROM " + quoted + where)) {
        statement.setQueryTimeout(3);
        if (!where.isEmpty()) statement.setString(1, "%" + filter + "%");
        try (ResultSet result = statement.executeQuery()) {
          if (result.next()) total = result.getLong(1);
        }
      } catch (SQLException ignored) {
        /* Counting a large table is optional. */
      }
      return new DatabaseDto.Page(
          detail.columns().stream().limit(64).map(DatabaseDto.Column::name).toList(),
          rows,
          total,
          page,
          size);
    }
  }

  public DatabaseDto.QueryResult execute(
      DatabaseConnection item, String id, String sql, java.util.function.Consumer<Statement> active)
      throws SQLException {
    long start = System.nanoTime();
    try (Connection connection = open(item);
        Statement statement = connection.createStatement()) {
      if (item.accessMode().equals("READ_ONLY") && !item.type().equals("SQLITE"))
        connection.setAutoCommit(false);
      if (item.accessMode().equals("READ_ONLY") && item.type().equals("MARIADB"))
        statement.execute("START TRANSACTION READ ONLY");
      statement.setQueryTimeout(20);
      statement.setMaxRows(200);
      statement.setMaxFieldSize(16_000);
      active.accept(statement);
      boolean query = statement.execute(sql);
      if (query) {
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        int[] remaining = {1_048_576};
        try (ResultSet result = statement.getResultSet()) {
          ResultSetMetaData metadata = result.getMetaData();
          for (int index = 1; index <= Math.min(metadata.getColumnCount(), 64); index++)
            columns.add(metadata.getColumnLabel(index));
          while (result.next() && rows.size() < 200) rows.add(row(result, remaining));
        }
        return new DatabaseDto.QueryResult(
            id, "SUCCEEDED", "QUERY", columns, rows, 0, elapsed(start), "");
      }
      return new DatabaseDto.QueryResult(
          id,
          "SUCCEEDED",
          "MUTATION",
          List.of(),
          List.of(),
          statement.getUpdateCount(),
          elapsed(start),
          "");
    } finally {
      active.accept(null);
    }
  }

  private DatabaseDto.Table requireTable(DatabaseConnection item, String schema, String table)
      throws SQLException {
    if (table == null
        || table.length() > 128
        || table.isBlank()
        || table.chars().anyMatch(Character::isISOControl))
      throw new WorkspaceException(400, "Schema/Table 이름을 확인해 주세요.");
    return tables(item, schema).stream()
        .filter(value -> value.name().equals(table))
        .findFirst()
        .orElseThrow(() -> new WorkspaceException(404, "테이블을 찾을 수 없습니다."));
  }

  private void requireSchema(DatabaseConnection item, String schema) throws SQLException {
    if (schema == null
        || schema.isBlank()
        || schema.length() > 128
        || schema.chars().anyMatch(Character::isISOControl))
      throw new WorkspaceException(400, "Schema 이름을 확인해 주세요.");
    if (schemas(item).stream().noneMatch(value -> value.name().equals(schema)))
      throw new WorkspaceException(404, "Schema를 찾을 수 없습니다.");
  }

  private String catalogName(DatabaseConnection item) {
    return List.of("MYSQL", "MARIADB").contains(item.type()) ? item.databaseName() : null;
  }

  private String schemaName(DatabaseConnection item, String schema) {
    return item.type().equals("POSTGRESQL") ? schema : null;
  }

  private String qualified(
      Connection connection, DatabaseConnection item, String schema, String table)
      throws SQLException {
    return item.type().equals("SQLITE")
        ? quote(connection, table)
        : quote(connection, schema) + "." + quote(connection, table);
  }

  private String quote(Connection connection, String value) throws SQLException {
    String mark = connection.getMetaData().getIdentifierQuoteString().trim();
    return mark + value.replace(mark, mark + mark) + mark;
  }

  private Map<String, Object> row(ResultSet result, int[] remaining) throws SQLException {
    Map<String, Object> values = new LinkedHashMap<>();
    ResultSetMetaData metadata = result.getMetaData();
    for (int index = 1; index <= Math.min(metadata.getColumnCount(), 64); index++) {
      int type = metadata.getColumnType(index);
      Object value;
      if (List.of(Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB).contains(type)) {
        try (java.io.InputStream stream = result.getBinaryStream(index)) {
          value = stream == null ? null : "[binary data]";
        } catch (java.io.IOException exception) {
          throw new SQLException("Binary result could not be closed", exception);
        }
      } else if (remaining[0] <= 0) value = "[result limit reached]";
      else if (List.of(
              Types.CHAR,
              Types.VARCHAR,
              Types.LONGVARCHAR,
              Types.NCHAR,
              Types.NVARCHAR,
              Types.LONGNVARCHAR,
              Types.CLOB,
              Types.NCLOB,
              Types.OTHER)
          .contains(type)) {
        try (java.io.Reader reader = result.getCharacterStream(index)) {
          if (reader == null) value = null;
          else {
            char[] buffer = new char[Math.min(16_000, remaining[0]) + 1];
            int count = 0;
            while (count < buffer.length) {
              int read = reader.read(buffer, count, buffer.length - count);
              if (read <= 0) break;
              count += read;
            }
            value =
                new String(buffer, 0, Math.min(count, buffer.length - 1))
                    + (count == buffer.length ? "…" : "");
          }
        } catch (java.io.IOException exception) {
          throw new SQLException("Result could not be read", exception);
        }
      } else value = result.getObject(index);
      remaining[0] -= Math.min(remaining[0], value == null ? 4 : value.toString().length());
      values.put(metadata.getColumnLabel(index), value);
    }
    return values;
  }

  public String errorType(SQLException error) {
    String state = Objects.toString(error.getSQLState(), "");
    if (error instanceof SQLTimeoutException || state.equals("HYT00") || state.equals("HYT01"))
      return "CONNECTION_TIMEOUT";
    for (Throwable cause = error; cause != null; cause = cause.getCause())
      if (cause instanceof javax.net.ssl.SSLException) return "TLS_ERROR";
    if (state.startsWith("28")) return "AUTHENTICATION_FAILED";
    if (state.equals("3D000") || error.getErrorCode() == 1049) return "DATABASE_NOT_FOUND";
    if (state.equals("42501") || error.getErrorCode() == 1044 || error.getErrorCode() == 1142)
      return "PERMISSION_DENIED";
    if (state.startsWith("08")) return "HOST_UNREACHABLE";
    if (state.equals("57014")) return "QUERY_TIMEOUT_OR_CANCELLED";
    return "DATABASE_ERROR";
  }

  private long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000;
  }
}
