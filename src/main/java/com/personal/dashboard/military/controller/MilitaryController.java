package com.personal.dashboard.military.controller;

import com.personal.dashboard.military.dto.MilitaryDto.*;
import com.personal.dashboard.military.service.MilitaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** OWNER session and CSRF protected personal military calendar resources. */
@RestController
@RequestMapping("/api/v1/military")
public class MilitaryController {
  private final MilitaryService service;

  public MilitaryController(MilitaryService service) {
    this.service = service;
  }

  @GetMapping
  public Dashboard dashboard() {
    return service.dashboard();
  }

  @PutMapping("/profile")
  public Dashboard save(@Valid @RequestBody ProfileRequest input) {
    return service.saveProfile(input);
  }

  @DeleteMapping("/profile")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteProfile(@RequestParam long revision) {
    service.deleteProfile(revision);
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.CREATED)
  public EventView create(@Valid @RequestBody EventRequest input) {
    return service.saveEvent(null, input);
  }

  @PutMapping("/events/{id}")
  public EventView update(@PathVariable String id, @Valid @RequestBody EventRequest input) {
    return service.saveEvent(id, input);
  }

  @DeleteMapping("/events/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteEvent(@PathVariable String id, @RequestParam long revision) {
    service.deleteEvent(id, revision);
  }
}
