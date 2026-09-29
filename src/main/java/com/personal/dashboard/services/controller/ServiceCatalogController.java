package com.personal.dashboard.services.controller;

import com.personal.dashboard.services.dto.ServiceDto;
import com.personal.dashboard.services.service.ServiceCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Session protected Service Catalog API. */
@RestController
@RequestMapping("/api/v1/services")
@PreAuthorize("hasRole('OWNER')")
public class ServiceCatalogController {
  private final ServiceCatalogService services;

  public ServiceCatalogController(ServiceCatalogService services) {
    this.services = services;
  }

  @GetMapping
  public List<ServiceDto.View> list() {
    return services.list();
  }

  @PostMapping
  public ResponseEntity<ServiceDto.View> create(@Valid @RequestBody ServiceDto.Request request) {
    return ResponseEntity.status(201).body(services.save(null, request));
  }

  @GetMapping("/{id}")
  public ServiceDto.View get(@PathVariable String id) {
    return services.get(id);
  }

  @PutMapping("/{id}")
  public ServiceDto.View update(
      @PathVariable String id, @Valid @RequestBody ServiceDto.Request request) {
    return services.save(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    services.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/resources")
  public List<ServiceDto.Resource> resources(@PathVariable String id) {
    return services.resources(id);
  }

  @PostMapping("/{id}/resources")
  public ResponseEntity<ServiceDto.Resource> bind(
      @PathVariable String id, @Valid @RequestBody ServiceDto.ResourceRequest request) {
    return ResponseEntity.status(201).body(services.bind(id, request));
  }

  @DeleteMapping("/{id}/resources/{resourceId}")
  public ResponseEntity<Void> unbind(@PathVariable String id, @PathVariable String resourceId) {
    services.unbind(id, resourceId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/health")
  public ServiceDto.Health health(@PathVariable String id) {
    return services.health(id);
  }

  @GetMapping("/{id}/context")
  public ServiceDto.Context context(@PathVariable String id) {
    return services.context(id);
  }

  @GetMapping("/{id}/activity")
  public List<ServiceDto.Activity> activity(@PathVariable String id) {
    return services.activity(id);
  }
}
