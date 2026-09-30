package com.personal.dashboard.assistant.controller;

import com.personal.dashboard.assistant.dto.WorkspaceMemoryDto.*;
import com.personal.dashboard.assistant.service.WorkspaceMemoryService;
import com.personal.dashboard.planner.dto.PlannerDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Owner session and CSRF protected management for shared Assistant memory. */
@RestController
@RequestMapping("/api/v1/assistant/memories")
public class WorkspaceMemoryController {
  private final WorkspaceMemoryService service;

  public WorkspaceMemoryController(WorkspaceMemoryService service) {
    this.service = service;
  }

  @GetMapping
  public Page search(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String type,
      @RequestParam(defaultValue = "") String confidence,
      @RequestParam(defaultValue = "") String scope,
      @RequestParam(defaultValue = "") String serviceId,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "25") int limit) {
    return service.search(query, status, type, confidence, scope, serviceId, offset, limit);
  }

  @GetMapping("/{id}")
  public View get(@PathVariable String id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public View create(@Valid @RequestBody Input input) {
    return service.create(input, true);
  }

  @PutMapping("/{id}")
  public View update(@PathVariable String id, @Valid @RequestBody Input input) {
    return service.update(id, input);
  }

  @PostMapping("/{id}/archive")
  public View archive(@PathVariable String id) {
    return service.status(id, "ARCHIVED");
  }

  @PostMapping("/{id}/restore")
  public View restore(@PathVariable String id) {
    return service.status(id, "ACTIVE");
  }

  @PostMapping("/{id}/pin")
  public View pin(@PathVariable String id) {
    return service.pin(id, true);
  }

  @DeleteMapping("/{id}/pin")
  public View unpin(@PathVariable String id) {
    return service.pin(id, false);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    service.delete(id);
  }

  @GetMapping("/preferences")
  public Preferences preferences() {
    return service.preferences();
  }

  @PutMapping("/preferences")
  public Preferences preferences(@RequestBody Preferences input) {
    return service.preferences(input);
  }

  @PostMapping("/{id}/supersede")
  public View supersede(@PathVariable String id, @Valid @RequestBody Input input) {
    return service.supersede(id, input);
  }

  @PostMapping("/{id}/promote/calendar")
  public View calendar(@PathVariable String id, @Valid @RequestBody PlannerDto.EventRequest input) {
    return service.promoteCalendar(id, input);
  }

  public record NotePromotionRequest(String title) {}

  @PostMapping("/{id}/promote/note")
  public View note(@PathVariable String id, @RequestBody NotePromotionRequest input) {
    return service.promoteNote(id, input.title());
  }

  public record NotesPromotionRequest(java.util.List<String> ids, String title) {}

  @PostMapping("/promotions/note")
  public NotePromotion notes(@RequestBody NotesPromotionRequest input) {
    return service.promoteNotes(input.ids(), input.title());
  }
}
