package com.personal.dashboard.runtime.service;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.*;
import com.personal.dashboard.runtime.dto.*;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Owns runtime handles, login-session ownership, connection lifetime and app execution selection.
 */
@Service
public class RuntimeService {
  public static class RuntimeSession {
    public final String id = UUID.randomUUID().toString();
    public final String ownerId;
    public final DeviceRecord device;
    public final String kind, label;
    public final int width, height;
    public final long createdAt = System.currentTimeMillis();
    public volatile AutoCloseable connection;
    public volatile boolean attached;

    RuntimeSession(
        String ownerId, DeviceRecord device, String kind, String label, int width, int height) {
      this.ownerId = ownerId;
      this.device = device;
      this.kind = kind;
      this.label = label;
      this.width = width;
      this.height = height;
    }
  }

  private final ConcurrentMap<String, RuntimeSession> sessions = new ConcurrentHashMap<>();
  private final CatalogService catalog;
  private final BrowserAdapter browser;
  private final String browserHost;
  private final int browserPort, browserVncPort;
  private final String wineHost;
  private final int wineVncPort;

  public RuntimeService(
      CatalogService catalog,
      BrowserAdapter browser,
      @Value("${workspace.browser-host:localhost}") String browserHost,
      @Value("${workspace.browser-port:9223}") int browserPort,
      @Value("${workspace.browser-vnc-port:5901}") int browserVncPort,
      @Value("${workspace.wine-host:localhost}") String wineHost,
      @Value("${workspace.wine-vnc-port:5902}") int wineVncPort) {
    this.catalog = catalog;
    this.browser = browser;
    this.browserHost = browserHost;
    this.browserPort = browserPort;
    this.browserVncPort = browserVncPort;
    this.wineHost = wineHost;
    this.wineVncPort = wineVncPort;
  }

  @PreAuthorize("hasRole('OWNER')")
  public synchronized SessionView create(SessionRequest request, String ownerId) {
    if (sessions.size() >= 12)
      throw new WorkspaceException(409, "열린 실행 탭을 닫은 후 다시 시도해 주세요. 최대 12개 세션을 지원합니다.");
    DeviceRecord device;
    String label;
    if (request.kind().equals("DESKTOP")) {
      if (!request.targetId().equals("kakaotalk"))
        throw new WorkspaceException(400, "지원하지 않는 데스크톱 앱입니다.");
      device =
          new DeviceRecord(
              "kakaotalk",
              "카카오톡",
              wineHost,
              22,
              "",
              "",
              "",
              "/",
              "VNC",
              wineVncPort,
              "",
              "",
              "",
              "",
              false);
      label = "카카오톡";
      catalog.record("DESKTOP", "kakaotalk", label, "");
    } else if (request.kind().equals("APP")) {
      var app = catalog.requireApplication(request.targetId());
      var settings = catalog.browserSettings();
      catalog.record("APP", app.id(), app.name(), "");
      if (settings.mode().equals("CLIENT"))
        return new SessionView("", "CLIENT", app.name(), app.url());
      if (settings.mode().equals("REMOTE")) {
        device = catalog.requireDevice(settings.deviceId());
        browser.open(catalog.connectionHost(device), settings.debugPort(), app.url());
      } else {
        device =
            new DeviceRecord(
                "browser",
                "서버 브라우저",
                browserHost,
                22,
                "",
                "",
                "",
                "/",
                "VNC",
                browserVncPort,
                "",
                "",
                "",
                "",
                false);
        browser.open(browserHost, browserPort, app.url());
      }
      label = app.name();
    } else {
      device = catalog.requireDevice(request.targetId());
      label = device.name() + (request.kind().equals("TERMINAL") ? " · 터미널" : " · 원격");
      if (request.kind().equals("REMOTE") && device.remoteProtocol().equals("NONE"))
        throw new WorkspaceException(400, "장비의 원격 프로토콜을 먼저 설정해 주세요.");
      catalog.record(request.kind(), device.id(), label, "");
    }
    RuntimeSession runtime =
        new RuntimeSession(
            ownerId, device, request.kind(), label, request.width(), request.height());
    sessions.put(runtime.id, runtime);
    return new SessionView(runtime.id, runtime.kind, runtime.label, "");
  }

  public synchronized RuntimeSession attach(String id, String ownerId) {
    RuntimeSession runtime = owned(id, ownerId);
    if (runtime.attached) throw new WorkspaceException(409, "이미 연결된 세션입니다.");
    runtime.attached = true;
    return runtime;
  }

  public RuntimeSession owned(String id, String ownerId) {
    RuntimeSession runtime = sessions.get(id);
    if (runtime == null || !runtime.ownerId.equals(ownerId))
      throw new WorkspaceException(404, "실행 세션을 찾을 수 없습니다.");
    return runtime;
  }

  public void closeOwned(String id, String ownerId) {
    owned(id, ownerId);
    close(id);
  }

  public synchronized void close(String id) {
    RuntimeSession runtime = sessions.remove(id);
    if (runtime != null && runtime.connection != null)
      try {
        runtime.connection.close();
      } catch (Exception ignored) {
      }
  }

  public synchronized boolean bind(RuntimeSession runtime, AutoCloseable connection)
      throws Exception {
    if (sessions.get(runtime.id) != runtime) {
      connection.close();
      return false;
    }
    runtime.connection = connection;
    return true;
  }

  public void closeOwner(String ownerId) {
    sessions.values().stream()
        .filter(runtime -> runtime.ownerId.equals(ownerId))
        .map(runtime -> runtime.id)
        .toList()
        .forEach(this::close);
  }

  public void expirePending() {
    sessions.values().stream()
        .filter(
            runtime -> !runtime.attached && System.currentTimeMillis() - runtime.createdAt > 60000)
        .map(runtime -> runtime.id)
        .toList()
        .forEach(this::close);
  }

  @PreDestroy
  public void shutdown() {
    List.copyOf(sessions.keySet()).forEach(this::close);
  }
}
