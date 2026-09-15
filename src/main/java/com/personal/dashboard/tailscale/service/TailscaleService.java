package com.personal.dashboard.tailscale.service;

import com.personal.dashboard.tailscale.adapter.TailscaleAdapter;
import com.personal.dashboard.tailscale.dto.TailscaleView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('OWNER')")
public class TailscaleService {
  private final TailscaleAdapter adapter;

  public TailscaleService(TailscaleAdapter adapter) {
    this.adapter = adapter;
  }

  public TailscaleView status() {
    return adapter.invoke("GET");
  }

  public TailscaleView login() {
    return adapter.invoke("POST");
  }

  public TailscaleView logout() {
    return adapter.invoke("DELETE");
  }
}
