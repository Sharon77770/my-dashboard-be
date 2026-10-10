package com.personal.dashboard.communication.service;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.communication.adapter.WindowsAgentAdapter;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.dto.*;
import com.personal.dashboard.runtime.service.RuntimeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Optional registered Windows device; no dependency is added to dashboard startup. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class WindowsBridgeService {
  private final CatalogService catalog;
  private final WindowsAgentAdapter agent;
  private final RuntimeService runtime;

  public WindowsBridgeService(
      CatalogService catalog, WindowsAgentAdapter agent, RuntimeService runtime) {
    this.catalog = catalog;
    this.agent = agent;
    this.runtime = runtime;
  }

  public com.personal.dashboard.communication.dto.BridgeDto.WindowsSnapshot snapshot(
      String deviceId) {
    if ("local".equals(deviceId)) throw new WorkspaceException(400, "등록된 Windows SSH 장비를 선택해 주세요.");
    return agent.snapshot(catalog.requireDevice(deviceId));
  }

  public SessionView screen(String deviceId, String owner) {
    if ("local".equals(deviceId)) throw new WorkspaceException(400, "등록된 Windows 장비를 선택해 주세요.");
    return runtime.create(new SessionRequest("REMOTE", deviceId, 1600, 900), owner);
  }
}
