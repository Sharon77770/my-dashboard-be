package com.personal.dashboard.communication.controller;

import com.personal.dashboard.communication.service.CommunicationGatewayService;
import org.springframework.web.bind.annotation.*;

/** Safe connection metadata only, never gateway session IDs or tokens. */
@RestController
public class CommunicationGatewayController {
  private final CommunicationGatewayService service;

  public CommunicationGatewayController(CommunicationGatewayService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/communications/gateway")
  public CommunicationGatewayService.Status status() {
    return service.status();
  }
}
