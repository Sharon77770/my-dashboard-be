package com.personal.dashboard.catalog.controller;

import com.personal.dashboard.catalog.dto.*;
import com.personal.dashboard.catalog.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Metadata API and explicit device operations; all routes require OWNER authentication. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final CatalogService service;
  private final DeviceOperations devices;

  public CatalogController(CatalogService service, DeviceOperations devices) {
    this.service = service;
    this.devices = devices;
  }

  public record Output(String output) {}

  public record DockerRequest(@NotBlank String container, @NotBlank String action) {}

  @GetMapping("/workspace")
  public WorkspaceView state() {
    return service.workspace();
  }

  @PutMapping("/tabs")
  public ResponseEntity<Void> tabs(@Valid @RequestBody TabLayout request) {
    service.tabs(request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/search")
  public List<ActivityView> search(@RequestParam(defaultValue = "") String query) {
    return service.search(query);
  }

  @PostMapping("/devices")
  public ResponseEntity<DeviceView> createDevice(@Valid @RequestBody DeviceRequest request) {
    return ResponseEntity.status(201).body(service.saveDevice(null, request));
  }

  @PutMapping("/devices/{id}")
  public DeviceView updateDevice(
      @PathVariable String id, @Valid @RequestBody DeviceRequest request) {
    return service.saveDevice(id, request);
  }

  @DeleteMapping("/devices/{id}")
  public ResponseEntity<Void> deleteDevice(@PathVariable String id) {
    service.deleteDevice(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/devices/{id}/status")
  public DeviceStatus status(@PathVariable String id) {
    return devices.status(id);
  }

  @GetMapping("/devices/{id}/docker")
  public Output docker(@PathVariable String id) {
    return new Output(devices.inspect(id, "docker"));
  }

  @GetMapping("/devices/{id}/gpu")
  public Output gpu(@PathVariable String id) {
    return new Output(devices.inspect(id, "gpu"));
  }

  @PostMapping("/devices/{id}/docker")
  public Output dockerAction(@PathVariable String id, @Valid @RequestBody DockerRequest request) {
    return new Output(devices.docker(id, request.container(), request.action()));
  }

  @PostMapping("/devices/{id}/wake")
  public ResponseEntity<Void> wake(@PathVariable String id) {
    devices.wake(id);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/applications")
  public ResponseEntity<ApplicationView> createApp(@Valid @RequestBody ApplicationRequest request) {
    return ResponseEntity.status(201).body(service.saveApplication(null, request));
  }

  @PutMapping("/applications/{id}")
  public ApplicationView updateApp(
      @PathVariable String id, @Valid @RequestBody ApplicationRequest request) {
    return service.saveApplication(id, request);
  }

  @DeleteMapping("/applications/{id}")
  public ResponseEntity<Void> deleteApp(@PathVariable String id) {
    service.deleteApplication(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/clips")
  public ResponseEntity<ClipView> createClip(@Valid @RequestBody ClipRequest request) {
    return ResponseEntity.status(201).body(service.saveClip(request));
  }

  @DeleteMapping("/clips/{id}")
  public ResponseEntity<Void> deleteClip(@PathVariable String id) {
    service.deleteClip(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/bookmarks")
  public ResponseEntity<Void> bookmark(@Valid @RequestBody BookmarkRequest request) {
    service.saveBookmark(request);
    return ResponseEntity.status(201).build();
  }

  @DeleteMapping("/bookmarks/{id}")
  public ResponseEntity<Void> deleteBookmark(@PathVariable String id) {
    service.deleteBookmark(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/preferences")
  public ResponseEntity<Void> preferences(@Valid @RequestBody PreferencesRequest request) {
    service.preferences(
        new Preferences(
            request.theme(), request.compact(), request.terminalFont(), request.clipMinutes()));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/browser-settings")
  public ResponseEntity<Void> browserSettings(@Valid @RequestBody BrowserSettingsRequest request) {
    service.browserSettings(
        new BrowserSettings(request.mode(), request.deviceId(), request.debugPort()));
    return ResponseEntity.noContent().build();
  }
}
