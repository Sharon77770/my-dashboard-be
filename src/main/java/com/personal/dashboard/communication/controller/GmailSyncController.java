package com.personal.dashboard.communication.controller;

import com.personal.dashboard.communication.service.GmailSyncService;
import org.springframework.web.bind.annotation.*;

/** One resumable sync page per explicit OWNER/CSRF request. */
@RestController
public class GmailSyncController {
  private final GmailSyncService service;

  public GmailSyncController(GmailSyncService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/communications/accounts/{id}/synchronizations")
  public GmailSyncService.Progress synchronize(@PathVariable String id) {
    return service.synchronize(id);
  }
}
