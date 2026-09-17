package com.personal.dashboard.notes.repository;

import com.personal.dashboard.notes.domain.NoteKind;
import com.personal.dashboard.notes.entity.NoteRecord;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Parameterized persistence; conditional writes provide optimistic concurrency control. */
@Repository
public class NoteRepository {
  private final JdbcTemplate jdbc;
  private static final RowMapper<NoteRecord> MAPPER =
      (r, i) ->
          new NoteRecord(
              r.getString("id"),
              r.getString("parent_id"),
              NoteKind.valueOf(r.getString("kind")),
              r.getString("title"),
              r.getString("icon"),
              r.getString("content"),
              r.getLong("revision"),
              r.getLong("created_at"),
              r.getLong("updated_at"));

  public NoteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<NoteRecord> list() {
    return jdbc.query(
        "SELECT id,parent_id,kind,title,icon,'[]' AS content,revision,created_at,updated_at FROM note_entries ORDER BY kind,title COLLATE NOCASE,id",
        MAPPER);
  }

  public Optional<NoteRecord> find(String id) {
    return jdbc.query("SELECT * FROM note_entries WHERE id=?", MAPPER, id).stream().findFirst();
  }

  public void insert(NoteRecord note) {
    jdbc.update(
        "INSERT INTO note_entries(id,parent_id,kind,title,icon,content,revision,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
        note.id(),
        note.parentId(),
        note.kind().name(),
        note.title(),
        note.icon(),
        note.content(),
        note.revision(),
        note.createdAt(),
        note.updatedAt());
  }

  public int metadata(
      String id, String parent, String title, String icon, long revision, long updated) {
    return jdbc.update(
        "UPDATE note_entries SET parent_id=?,title=?,icon=?,revision=revision+1,updated_at=? WHERE id=? AND revision=?",
        parent,
        title,
        icon,
        updated,
        id,
        revision);
  }

  public int content(String id, String content, long revision, long updated) {
    return jdbc.update(
        "UPDATE note_entries SET content=?,revision=revision+1,updated_at=? WHERE id=? AND revision=?",
        content,
        updated,
        id,
        revision);
  }

  public int delete(String id, long revision) {
    return jdbc.update("DELETE FROM note_entries WHERE id=? AND revision=?", id, revision);
  }

  public void insertImage(String id, String documentId, String mediaType, byte[] data) {
    jdbc.update(
        "INSERT INTO note_images(id,document_id,media_type,data,created_at) VALUES(?,?,?,?,?)",
        id,
        documentId,
        mediaType,
        data,
        System.currentTimeMillis());
  }

  public record ImageRecord(String documentId, String mediaType, byte[] data) {}

  public Optional<ImageRecord> image(String id) {
    return jdbc
        .query(
            "SELECT document_id,media_type,data FROM note_images WHERE id=?",
            (r, i) -> new ImageRecord(r.getString(1), r.getString(2), r.getBytes(3)),
            id)
        .stream()
        .findFirst();
  }

  public boolean ownsImage(String documentId, String id) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM note_images WHERE id=? AND document_id=?",
            Integer.class,
            id,
            documentId)
        > 0;
  }
}
