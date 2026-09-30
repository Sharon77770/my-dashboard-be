package com.personal.dashboard.services.controller;

import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.dto.ServiceOnboardingDto;
import com.personal.dashboard.services.service.ServiceOnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** OWNER session and CSRF protected browser review and approval of Assistant drafts. */
@RestController
@RequestMapping("/api/v1/assistant/service-drafts")
@PreAuthorize("hasRole('OWNER')")
public class ServiceOnboardingController {
  private final ServiceOnboardingService onboarding;

  public ServiceOnboardingController(ServiceOnboardingService onboarding) {
    this.onboarding = onboarding;
  }

  @GetMapping("/thread/{threadId}")
  public ResponseEntity<ServiceOnboardingDto.Draft> thread(@PathVariable String threadId) {
    var draft = onboarding.forThread(threadId);
    return draft == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(draft);
  }

  @GetMapping("/{id}")
  public ServiceOnboardingDto.Draft get(@PathVariable String id) {
    return onboarding.get(id);
  }

  @GetMapping("/thread/{threadId}/resources")
  public ServiceOnboardingDto.Discovery resources(@PathVariable String threadId) {
    return onboarding.snapshot(threadId);
  }

  @PutMapping("/{id}")
  public ServiceOnboardingDto.Draft update(
      @PathVariable String id, @RequestBody ServiceOnboardingDto.DraftUpdate input) {
    return onboarding.update(id, input);
  }

  @PostMapping("/{id}/approve")
  public ServiceOnboardingDto.Draft approve(
      @PathVariable String id, @RequestBody ServiceOnboardingDto.Revision input) {
    return onboarding.approve(id, input.revision());
  }

  @PostMapping("/{id}/commit")
  public ServiceDto.View commit(
      @PathVariable String id, @RequestBody ServiceOnboardingDto.Revision input) {
    return onboarding.commit(id, input.revision());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> cancel(@PathVariable String id) {
    onboarding.cancel(id);
    return ResponseEntity.noContent().build();
  }
}
