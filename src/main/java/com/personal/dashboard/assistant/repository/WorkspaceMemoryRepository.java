package com.personal.dashboard.assistant.repository;

import com.personal.dashboard.assistant.dto.WorkspaceMemoryDto.Preferences;
import com.personal.dashboard.assistant.entity.WorkspaceMemory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQLite storage and bounded searches for Assistant memories. */
@Repository
public class WorkspaceMemoryRepository {
  private final JdbcTemplate jdbc;

  public WorkspaceMemoryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private WorkspaceMemory map(ResultSet r, int row) throws SQLException {
    long expiryValue = r.getLong("expires_at");
    Long expiry = r.wasNull() ? null : expiryValue;
    return new WorkspaceMemory(
        r.getString("id"),
        r.getString("content"),
        r.getString("type"),
        r.getString("confidence"),
        r.getString("scope"),
        r.getString("status"),
        r.getString("importance"),
        r.getString("tags"),
        r.getString("time_hint"),
        r.getString("related_service_id"),
        r.getString("related_project"),
        r.getString("source_type"),
        r.getString("source_thread_id"),
        r.getString("source_description"),
        r.getInt("pinned") != 0,
        r.getInt("manually_created") != 0,
        r.getLong("created_at"),
        r.getLong("updated_at"),
        r.getLong("last_accessed_at"),
        r.getInt("access_count"),
        expiry,
        r.getString("superseded_by"),
        r.getString("promoted_target_type"),
        r.getString("promoted_target_id"));
  }

  public Optional<WorkspaceMemory> find(String id) {
    return jdbc.query("SELECT * FROM workspace_memories WHERE id=?", this::map, id).stream()
        .findFirst();
  }

  public void insert(WorkspaceMemory m) {
    jdbc.update(
        "INSERT INTO workspace_memories VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        m.id(),
        m.content(),
        m.type(),
        m.confidence(),
        m.scope(),
        m.status(),
        m.importance(),
        m.tags(),
        m.timeHint(),
        m.relatedServiceId(),
        m.relatedProject(),
        m.sourceType(),
        m.sourceThreadId(),
        m.sourceDescription(),
        m.pinned() ? 1 : 0,
        m.manuallyCreated() ? 1 : 0,
        m.createdAt(),
        m.updatedAt(),
        m.lastAccessedAt(),
        m.accessCount(),
        m.expiresAt(),
        m.supersededBy(),
        m.promotedTargetType(),
        m.promotedTargetId());
  }

  public void update(WorkspaceMemory m) {
    jdbc.update(
        "UPDATE workspace_memories SET content=?,type=?,confidence=?,scope=?,status=?,importance=?,tags=?,time_hint=?,related_service_id=?,related_project=?,source_type=?,source_thread_id=?,source_description=?,pinned=?,manually_created=?,updated_at=?,last_accessed_at=?,access_count=?,expires_at=?,superseded_by=?,promoted_target_type=?,promoted_target_id=? WHERE id=?",
        m.content(),
        m.type(),
        m.confidence(),
        m.scope(),
        m.status(),
        m.importance(),
        m.tags(),
        m.timeHint(),
        m.relatedServiceId(),
        m.relatedProject(),
        m.sourceType(),
        m.sourceThreadId(),
        m.sourceDescription(),
        m.pinned() ? 1 : 0,
        m.manuallyCreated() ? 1 : 0,
        m.updatedAt(),
        m.lastAccessedAt(),
        m.accessCount(),
        m.expiresAt(),
        m.supersededBy(),
        m.promotedTargetType(),
        m.promotedTargetId(),
        m.id());
  }

  public void delete(String id) {
    jdbc.update("DELETE FROM workspace_memories WHERE id=?", id);
  }

  public List<WorkspaceMemory> search(
      String text,
      String status,
      String type,
      String confidence,
      String scope,
      String serviceId,
      int limit,
      int offset) {
    String sql =
        "SELECT * FROM workspace_memories WHERE (?='' OR content LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\' OR time_hint LIKE ? ESCAPE '\\' OR related_project LIKE ? ESCAPE '\\') "
            + "AND (?='' OR status=?) AND (?='' OR type=?) AND (?='' OR confidence=?) AND (?='' OR scope=?) "
            + "AND (?='' OR related_service_id=?) ORDER BY pinned DESC,updated_at DESC LIMIT ? OFFSET ?";
    String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    return jdbc.query(
        sql,
        this::map,
        text,
        pattern,
        pattern,
        pattern,
        pattern,
        status,
        status,
        type,
        type,
        confidence,
        confidence,
        scope,
        scope,
        serviceId,
        serviceId,
        limit,
        offset);
  }

  public List<WorkspaceMemory> retrievalCandidates(String query, String serviceId, int limit) {
    String sql =
        "SELECT * FROM workspace_memories WHERE status='ACTIVE' AND (expires_at IS NULL OR expires_at>?) "
            + "AND (?='' OR content LIKE ? ESCAPE '\\' OR tags LIKE ? ESCAPE '\\' OR related_project LIKE ? ESCAPE '\\' OR related_service_id=?) "
            + "ORDER BY updated_at DESC LIMIT ?";
    String pattern =
        "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    return jdbc.query(
        sql,
        this::map,
        System.currentTimeMillis(),
        query,
        pattern,
        pattern,
        pattern,
        serviceId,
        limit);
  }

  public List<WorkspaceMemory> maintenanceCandidates(int limit) {
    return jdbc.query(
        "SELECT * FROM workspace_memories WHERE status IN ('ACTIVE','STALE','ARCHIVED','EXPIRED') ORDER BY updated_at LIMIT ?",
        this::map,
        limit);
  }

  public void accessed(String id, long now) {
    jdbc.update(
        "UPDATE workspace_memories SET last_accessed_at=?,access_count=access_count+1 WHERE id=?",
        now,
        id);
  }

  public Preferences preferences() {
    return jdbc.queryForObject(
        "SELECT * FROM workspace_memory_preferences WHERE id=1",
        (r, i) ->
            new Preferences(
                r.getInt("auto_archive") != 0,
                r.getInt("auto_delete") != 0,
                r.getInt("protect_manual") != 0,
                r.getInt("tentative_days"),
                r.getInt("possibility_days"),
                r.getInt("follow_up_days"),
                r.getInt("archived_days"),
                r.getLong("last_cleanup_at")));
  }

  public void preferences(Preferences p) {
    jdbc.update(
        "UPDATE workspace_memory_preferences SET auto_archive=?,auto_delete=?,protect_manual=?,tentative_days=?,possibility_days=?,follow_up_days=?,archived_days=? WHERE id=1",
        p.autoArchive() ? 1 : 0,
        p.autoDelete() ? 1 : 0,
        p.protectManual() ? 1 : 0,
        p.tentativeDays(),
        p.possibilityDays(),
        p.followUpDays(),
        p.archivedDays());
  }

  public void cleanupTime(long now) {
    jdbc.update("UPDATE workspace_memory_preferences SET last_cleanup_at=? WHERE id=1", now);
  }
}
