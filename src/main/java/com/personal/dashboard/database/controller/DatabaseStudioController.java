package com.personal.dashboard.database.controller;

import com.personal.dashboard.database.dto.DatabaseDto;
import com.personal.dashboard.database.service.DatabaseStudioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** OWNER session and CSRF protected Database Studio API. */
@RestController
@RequestMapping("/api/v1/databases")
@PreAuthorize("hasRole('OWNER')")
public class DatabaseStudioController {
  private final DatabaseStudioService studio;

  public DatabaseStudioController(DatabaseStudioService studio) {
    this.studio = studio;
  }

  @GetMapping
  public List<DatabaseDto.ConnectionView> list() {
    return studio.list();
  }

  @PostMapping
  public ResponseEntity<DatabaseDto.ConnectionView> create(
      @Valid @RequestBody DatabaseDto.ConnectionRequest request) {
    return ResponseEntity.status(201).body(studio.save(null, request));
  }

  @GetMapping("/{id}")
  public DatabaseDto.ConnectionView get(@PathVariable String id) {
    return studio.get(id);
  }

  @PutMapping("/{id}")
  public DatabaseDto.ConnectionView update(
      @PathVariable String id, @Valid @RequestBody DatabaseDto.ConnectionRequest request) {
    return studio.save(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    studio.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/test")
  public DatabaseDto.TestResult test(@PathVariable String id) {
    return studio.test(id);
  }

  @PostMapping("/test")
  public DatabaseDto.TestResult testDraft(
      @RequestParam(required = false) String id,
      @Valid @RequestBody DatabaseDto.ConnectionRequest request) {
    return studio.testDraft(id, request);
  }

  @GetMapping("/{id}/schemas")
  public List<DatabaseDto.Schema> schemas(@PathVariable String id) {
    return studio.schemas(id);
  }

  @GetMapping("/{id}/tables")
  public List<DatabaseDto.Table> tables(@PathVariable String id, @RequestParam String schema) {
    return studio.tables(id, schema);
  }

  @GetMapping("/{id}/functions")
  public List<DatabaseDto.Function> functions(
      @PathVariable String id, @RequestParam String schema) {
    return studio.functions(id, schema);
  }

  @GetMapping("/{id}/tables/{table}")
  public DatabaseDto.TableDetail describe(
      @PathVariable String id, @PathVariable String table, @RequestParam String schema) {
    return studio.describe(id, schema, table);
  }

  @GetMapping("/{id}/tables/{table}/rows")
  public DatabaseDto.Page rows(
      @PathVariable String id,
      @PathVariable String table,
      @RequestParam String schema,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(defaultValue = "") String sort,
      @RequestParam(defaultValue = "ASC") String direction,
      @RequestParam(defaultValue = "") String filterColumn,
      @RequestParam(defaultValue = "") String filter) {
    return studio.rows(id, schema, table, page, size, sort, direction, filterColumn, filter);
  }

  @PostMapping("/{id}/query")
  public ResponseEntity<DatabaseDto.QueryResult> run(
      @PathVariable String id, @Valid @RequestBody DatabaseDto.QueryRequest request) {
    return ResponseEntity.accepted().body(studio.run(id, request));
  }

  @GetMapping("/{id}/query/{executionId}")
  public DatabaseDto.QueryResult result(@PathVariable String id, @PathVariable String executionId) {
    return studio.result(id, executionId);
  }

  @PostMapping("/{id}/query/{executionId}/cancel")
  public DatabaseDto.QueryResult cancel(@PathVariable String id, @PathVariable String executionId) {
    return studio.cancel(id, executionId);
  }

  @GetMapping("/history")
  public List<DatabaseDto.History> history() {
    return studio.history();
  }

  @GetMapping("/{id}/favorites")
  public List<DatabaseDto.Favorite> favorites(@PathVariable String id) {
    return studio.favorites(id);
  }

  @PostMapping("/{id}/favorites")
  public ResponseEntity<DatabaseDto.Favorite> favorite(
      @PathVariable String id, @Valid @RequestBody DatabaseDto.FavoriteRequest request) {
    return ResponseEntity.status(201).body(studio.favorite(id, request));
  }

  @DeleteMapping("/{id}/favorites/{favoriteId}")
  public ResponseEntity<Void> deleteFavorite(
      @PathVariable String id, @PathVariable String favoriteId) {
    studio.deleteFavorite(id, favoriteId);
    return ResponseEntity.noContent().build();
  }
}
