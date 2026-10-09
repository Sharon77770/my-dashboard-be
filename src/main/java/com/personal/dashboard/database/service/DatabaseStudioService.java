package com.personal.dashboard.database.service;

import com.personal.dashboard.database.adapter.DatabaseAdapter;
import com.personal.dashboard.database.adapter.DatabaseTargetAdapter;
import com.personal.dashboard.database.dto.DatabaseDto;
import com.personal.dashboard.database.entity.DatabaseConnection;
import com.personal.dashboard.database.repository.DatabaseRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import jakarta.annotation.PreDestroy;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Coordinates safe database browsing and SQL execution with the existing credential vault. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class DatabaseStudioService {
  private static final Logger log = LoggerFactory.getLogger(DatabaseStudioService.class);
  private final DatabaseRepository repository;
  private final DatabaseAdapter adapter;
  private final CredentialVault vault;
  private final DatabaseTargetAdapter targets;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8));
  private final ConcurrentMap<String, Execution> executions = new ConcurrentHashMap<>();

  private static final class Execution {
    final String connectionId;
    final AtomicReference<Statement> statement = new AtomicReference<>();
    volatile DatabaseDto.QueryResult result;
    volatile Future<?> task;
    volatile boolean cancelled;

    Execution(String connectionId, String id) {
      this.connectionId = connectionId;
      this.result = new DatabaseDto.QueryResult(id, "RUNNING", "", List.of(), List.of(), 0, 0, "");
    }
  }

  public DatabaseStudioService(
      DatabaseRepository repository,
      DatabaseAdapter adapter,
      CredentialVault vault,
      DatabaseTargetAdapter targets) {
    this.repository = repository;
    this.adapter = adapter;
    this.vault = vault;
    this.targets = targets;
  }

  public List<DatabaseDto.Container> containers(String deviceId) {
    return targets.containers(deviceId);
  }

  public List<DatabaseDto.ConnectionView> list() {
    return repository.connections().stream().map(this::view).toList();
  }

  public DatabaseDto.ConnectionView get(String id) {
    return view(require(id));
  }

  public DatabaseDto.ConnectionView save(String id, DatabaseDto.ConnectionRequest request) {
    DatabaseConnection value = draft(id, request);
    repository.save(value);
    return view(value);
  }

  public DatabaseDto.TestResult testDraft(String id, DatabaseDto.ConnectionRequest request) {
    return adapter.test(draft(id, request));
  }

  private DatabaseConnection draft(String id, DatabaseDto.ConnectionRequest request) {
    String type = request.type().toUpperCase(Locale.ROOT);
    String mode = request.accessMode().toUpperCase(Locale.ROOT);
    String ssl = request.sslMode().toUpperCase(Locale.ROOT);
    if (!List.of("POSTGRESQL", "MYSQL", "MARIADB", "SQLITE").contains(type)
        || !List.of("READ_ONLY", "READ_WRITE").contains(mode)
        || !List.of("DISABLE", "REQUIRE").contains(ssl))
      throw new WorkspaceException(400, "DB 종류, 접근 모드 또는 SSL 모드를 확인해 주세요.");
    DatabaseConnection old = id == null ? null : require(id);
    String targetMode = Objects.toString(request.targetMode(), "DIRECT");
    String deviceId = Objects.toString(request.deviceId(), "").trim();
    String containerId = Objects.toString(request.containerId(), "").trim();
    targets.validate(targetMode, deviceId, containerId, type);
    String host = Objects.toString(request.host(), "").trim();
    if (targetMode.equals("DOCKER") || (targetMode.equals("DEVICE") && host.isEmpty()))
      host = "127.0.0.1";
    String database = request.databaseName().trim();
    String username = Objects.toString(request.username(), "").trim();
    int port =
        request.port() == null
            ? (type.equals("POSTGRESQL") ? 5432 : type.equals("SQLITE") ? 0 : 3306)
            : request.port();
    if (type.equals("SQLITE")) {
      if (!host.isEmpty() || !username.isEmpty() || port != 0 || !ssl.equals("DISABLE"))
        throw new WorkspaceException(400, "SQLite에는 서버 접속 설정을 사용하지 않습니다.");
    } else if (!host.matches("[A-Za-z0-9_.:-]{1,255}")
        || !database.matches("[A-Za-z0-9_-]{1,100}")
        || port < 1
        || port > 65535) throw new WorkspaceException(400, "호스트, 포트, 데이터베이스 이름을 확인해 주세요.");
    String cipher =
        request.credential() == null || request.credential().isEmpty()
            ? old == null ? "" : old.credentialCipher()
            : vault.encrypt(request.credential());
    if (type.equals("SQLITE")) cipher = "";
    if (!type.equals("SQLITE") && cipher.isEmpty())
      throw new WorkspaceException(400, "비밀번호를 입력해 주세요.");
    Map<String, String> metadata = request.metadata() == null ? Map.of() : request.metadata();
    if (metadata.size() > 20) throw new WorkspaceException(400, "메타데이터 항목은 최대 20개입니다.");
    for (var entry : metadata.entrySet()) {
      if (entry.getKey() == null
          || !entry.getKey().matches("[A-Za-z0-9_-]{1,40}")
          || entry.getKey().matches("(?i).*(password|secret|token|credential|key).*")
          || entry.getValue() == null
          || entry.getValue().length() > 200
          || entry.getValue().chars().anyMatch(Character::isISOControl))
        throw new WorkspaceException(400, "메타데이터 키와 값을 확인해 주세요.");
    }
    long now = System.currentTimeMillis();
    DatabaseConnection value =
        new DatabaseConnection(
            id == null ? UUID.randomUUID().toString() : id,
            request.name().trim(),
            type,
            host,
            port,
            database,
            username,
            cipher,
            ssl,
            mode,
            Map.copyOf(metadata),
            old == null ? now : old.createdAt(),
            now,
            targetMode,
            deviceId,
            containerId);
    return value;
  }

  public void delete(String id) {
    require(id);
    if (executions.values().stream()
        .anyMatch(value -> value.connectionId.equals(id) && value.result.state().equals("RUNNING")))
      throw new WorkspaceException(409, "실행 중인 쿼리를 먼저 취소해 주세요.");
    repository.delete(id);
  }

  public DatabaseDto.TestResult test(String id) {
    return adapter.test(require(id));
  }

  public List<DatabaseDto.Schema> schemas(String id) {
    return call(() -> adapter.schemas(require(id)));
  }

  public List<DatabaseDto.Table> tables(String id, String schema) {
    return call(() -> adapter.tables(require(id), schema));
  }

  public List<DatabaseDto.Function> functions(String id, String schema) {
    return call(() -> adapter.functions(require(id), schema));
  }

  public DatabaseDto.TableDetail describe(String id, String schema, String table) {
    return call(() -> adapter.describe(require(id), schema, table));
  }

  public DatabaseDto.Page rows(
      String id,
      String schema,
      String table,
      int page,
      int size,
      String sort,
      String direction,
      String filterColumn,
      String filter) {
    if (page < 0 || page > 100000 || size < 1 || size > 200 || filter.length() > 200)
      throw new WorkspaceException(400, "페이지 크기 또는 필터를 확인해 주세요.");
    return call(
        () ->
            adapter.rows(
                require(id), schema, table, page, size, sort, direction, filterColumn, filter));
  }

  /** Starts a bounded worker so the browser can poll and cancel by execution ID. */
  public DatabaseDto.QueryResult run(String id, DatabaseDto.QueryRequest request) {
    DatabaseConnection connection = require(id);
    String sql = checkedSql(connection, request);
    if (executions.values().stream().filter(value -> value.result.state().equals("RUNNING")).count()
        >= 10) throw new WorkspaceException(429, "동시에 실행할 수 있는 쿼리 수를 초과했습니다.");
    String executionId = UUID.randomUUID().toString();
    Execution execution = new Execution(id, executionId);
    executions.put(executionId, execution);
    try {
      execution.task = executor.submit(() -> execute(connection, sql, executionId, execution));
    } catch (RejectedExecutionException exception) {
      executions.remove(executionId);
      throw new WorkspaceException(429, "쿼리 실행 대기열이 가득 찼습니다.");
    }
    return execution.result;
  }

  public DatabaseDto.QueryResult result(String id, String executionId) {
    Execution execution = executions.get(executionId);
    if (execution == null || !execution.connectionId.equals(id))
      throw new WorkspaceException(404, "쿼리 실행을 찾을 수 없습니다.");
    return execution.result;
  }

  public DatabaseDto.QueryResult cancel(String id, String executionId) {
    Execution execution = executions.get(executionId);
    if (execution == null || !execution.connectionId.equals(id))
      throw new WorkspaceException(404, "쿼리 실행을 찾을 수 없습니다.");
    execution.cancelled = true;
    Statement statement = execution.statement.get();
    if (statement != null)
      try {
        statement.cancel();
      } catch (SQLException ignored) {
        /* Connection closes in worker. */
      }
    Future<?> task = execution.task;
    if (task != null) task.cancel(true);
    execution.result =
        new DatabaseDto.QueryResult(executionId, "CANCELLED", "", List.of(), List.of(), 0, 0, "");
    return execution.result;
  }

  public List<DatabaseDto.History> history() {
    return repository.history();
  }

  public List<DatabaseDto.Favorite> favorites(String id) {
    require(id);
    return repository.favorites(id);
  }

  public DatabaseDto.Favorite favorite(String id, DatabaseDto.FavoriteRequest request) {
    require(id);
    if (sensitiveSql(request.sql()))
      throw new WorkspaceException(400, "민감한 SQL은 즐겨찾기로 저장할 수 없습니다.");
    return repository.favorite(id, UUID.randomUUID().toString(), request);
  }

  public void deleteFavorite(String id, String favoriteId) {
    require(id);
    if (repository.deleteFavorite(id, favoriteId) == 0)
      throw new WorkspaceException(404, "즐겨찾기를 찾을 수 없습니다.");
  }

  private void execute(DatabaseConnection connection, String sql, String id, Execution execution) {
    long start = System.nanoTime();
    DatabaseDto.QueryResult value;
    try {
      value = adapter.execute(connection, id, sql, execution.statement::set);
    } catch (SQLException exception) {
      log.warn(
          "Database query failed: type={}, sqlState={}, vendorCode={}",
          connection.type(),
          exception.getSQLState(),
          exception.getErrorCode());
      value =
          new DatabaseDto.QueryResult(
              id,
              "FAILED",
              "",
              List.of(),
              List.of(),
              0,
              (System.nanoTime() - start) / 1_000_000,
              adapter.errorType(exception));
    } catch (RuntimeException exception) {
      value =
          new DatabaseDto.QueryResult(
              id,
              "FAILED",
              "",
              List.of(),
              List.of(),
              0,
              (System.nanoTime() - start) / 1_000_000,
              "DATABASE_ERROR");
    }
    if (execution.cancelled)
      value =
          new DatabaseDto.QueryResult(
              id, "CANCELLED", "", List.of(), List.of(), 0, value.durationMs(), "");
    execution.result = value;
    String historySql = sensitiveSql(sql) ? "[sensitive query omitted]" : sql;
    repository.history(
        new DatabaseDto.History(
            UUID.randomUUID().toString(),
            connection.id(),
            connection.name(),
            historySql,
            System.currentTimeMillis(),
            value.durationMs(),
            value.resultType(),
            value.state().equals("SUCCEEDED"),
            value.errorType()));
    if (executions.size() > 100)
      executions.entrySet().removeIf(entry -> !entry.getValue().result.state().equals("RUNNING"));
  }

  /** One statement only; a conservative read-only allowlist supplements server DB permissions. */
  private String checkedSql(DatabaseConnection connection, DatabaseDto.QueryRequest request) {
    String sql = request.sql().trim();
    if (sql.isEmpty() || sql.contains("--") || sql.contains("/*") || sql.contains("*/"))
      throw new WorkspaceException(400, "주석 없는 SQL 한 문장을 입력해 주세요.");
    sql = sql.replaceFirst(";\\s*$", "").trim();
    if (sql.contains(";")) throw new WorkspaceException(400, "SQL은 한 문장씩 실행해 주세요.");
    String upper =
        sql.replaceAll("'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"", "''").toUpperCase(Locale.ROOT);
    if (connection.accessMode().equals("READ_ONLY")
        && (!upper.startsWith("SELECT ")
            || upper.matches("(?s).*\\b(INTO|FOR\\s+UPDATE|LOCK)\\b.*")))
      throw new WorkspaceException(403, "읽기 전용 연결에서는 SELECT만 실행할 수 있습니다.");
    boolean dangerous = upper.matches("(?s).*\\b(DROP|TRUNCATE|ALTER|DELETE|UPDATE)\\b.*");
    if (dangerous && !request.confirmed())
      throw new WorkspaceException(409, "이 SQL은 실행 확인이 필요합니다.");
    return sql;
  }

  private boolean sensitiveSql(String sql) {
    return sql.matches("(?is).*(password|secret|token|credential|identified\\s+by).*");
  }

  private DatabaseConnection require(String id) {
    return repository
        .connection(id)
        .orElseThrow(() -> new WorkspaceException(404, "데이터베이스 연결을 찾을 수 없습니다."));
  }

  private DatabaseDto.ConnectionView view(DatabaseConnection item) {
    return new DatabaseDto.ConnectionView(
        item.id(),
        item.name(),
        item.type(),
        item.host(),
        item.port(),
        item.databaseName(),
        item.username(),
        !item.credentialCipher().isEmpty(),
        item.sslMode(),
        item.accessMode(),
        item.metadata(),
        item.createdAt(),
        item.updatedAt(),
        item.targetMode(),
        item.deviceId(),
        item.containerId());
  }

  private <T> T call(SqlCall<T> operation) {
    try {
      return operation.run();
    } catch (SQLException exception) {
      throw new WorkspaceException(502, "DB 조회에 실패했습니다: " + adapter.errorType(exception));
    }
  }

  private interface SqlCall<T> {
    T run() throws SQLException;
  }

  @PreDestroy
  public void close() {
    executions
        .values()
        .forEach(
            value -> {
              Statement statement = value.statement.get();
              if (statement != null)
                try {
                  statement.cancel();
                } catch (SQLException ignored) {
                }
            });
    executor.shutdownNow();
  }
}
