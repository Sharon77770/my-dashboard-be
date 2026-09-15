package com.personal.dashboard.tailscale.controller;

import com.personal.dashboard.tailscale.dto.TailscaleView;
import com.personal.dashboard.tailscale.service.TailscaleService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tailscale")
public class TailscaleController {
  private final TailscaleService service;

  public TailscaleController(TailscaleService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<TailscaleView> status() {
    return response(service.status());
  }

  @PostMapping("/login")
  public ResponseEntity<TailscaleView> login() {
    return response(service.login());
  }

  @DeleteMapping("/login")
  public ResponseEntity<TailscaleView> logout() {
    return response(service.logout());
  }

  private ResponseEntity<TailscaleView> response(TailscaleView value) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
  }
}
