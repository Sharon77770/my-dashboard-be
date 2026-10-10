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
    public String root;
    public volatile long detachedAt;
    public volatile RetainedTerminal terminal;

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

  public RuntimeService(
      CatalogService catalog,
      BrowserAdapter browser,
      @Value("${workspace.browser-host:localhost}") String browserHost,
      @Value("${workspace.browser-port:9223}") int browserPort,
      @Value("${workspace.browser-vnc-port:5901}") int browserVncPort) {
    this.catalog = catalog;
    this.browser = browser;
    this.browserHost = browserHost;
    this.browserPort = browserPort;
    this.browserVncPort = browserVncPort;
  }

  @PreAuthorize("hasRole('OWNER')")
  public synchronized SessionView create(SessionRequest request, String ownerId) {
    if (!Set.of("TERMINAL", "REMOTE", "APP").contains(request.kind()))
      throw new WorkspaceException(400, "지원하지 않는 실행 유형입니다.");
    if (sessions.size() >= 12)
      throw new WorkspaceException(409, "열린 실행 탭을 닫은 후 다시 시도해 주세요. 최대 12개 세션을 지원합니다.");
    DeviceRecord device;
    String label;
    if (request.kind().equals("APP")) {
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
    runtime.root = request.kind().equals("TERMINAL") ? request.root() : null;
    return new SessionView(runtime.id, runtime.kind, runtime.label, "");
  }

  /** Closing this view disconnects VNC while Chromium's persistent profile remains intact. */
  @PreAuthorize("hasRole('OWNER')")
  public synchronized SessionView createBrowserSession(
      String url, String label, String ownerId, int width, int height) {
    expirePending();
    if (sessions.size() >= 12)
      throw new WorkspaceException(409, "열린 실행 탭을 닫은 후 다시 시도해 주세요. 최대 12개 세션을 지원합니다.");
    DeviceRecord device =
        new DeviceRecord(
            "browser",
            "인증 브라우저",
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
    if (url != null) browser.open(browserHost, browserPort, url);
    RuntimeSession runtime = new RuntimeSession(ownerId, device, "APP", label, width, height);
    sessions.put(runtime.id, runtime);
    return new SessionView(runtime.id, runtime.kind, runtime.label, "");
  }

  /** Dedicated profile desktop, fixed broker port range and ordinary runtime ownership. */
  @PreAuthorize("hasRole('OWNER')")
  public synchronized SessionView createCommunicationBrowserSession(
      String profileId, String label, int port, String ownerId) {
    expirePending();
    if (!((port >= 5910 && port <= 5913) || (port >= 5920 && port <= 5923))
        || !profileId.matches("[a-f0-9-]{36}"))
      throw new WorkspaceException(400, "Bridge 세션을 확인해 주세요.");
    if (sessions.size() >= 12) throw new WorkspaceException(409, "열린 실행 탭을 닫아 주세요.");
    var device =
        new DeviceRecord(
            "communication:" + profileId,
            label,
            browserHost,
            22,
            "",
            "",
            "",
            "/",
            "VNC",
            port,
            "",
            "",
            "",
            "",
            false);
    var session = new RuntimeSession(ownerId, device, "APP", label, 1600, 900);
    sessions.put(session.id, session);
    return new SessionView(session.id, session.kind, label, "");
  }

  @PreAuthorize("hasRole('OWNER')")
  public synchronized void closeCommunicationBrowserSessions(String profileId) {
    sessions.values().stream()
        .filter(session -> session.device.id().equals("communication:" + profileId))
        .toList()
        .forEach(session -> close(session.id));
  }

  public synchronized RuntimeSession attach(String id, String ownerId) {
    RuntimeSession runtime = owned(id, ownerId);
    if (runtime.attached) throw new WorkspaceException(409, "이미 연결된 세션입니다.");
    runtime.attached = true;
    return runtime;
  }

  /** Restores discoverable Studio handles only within the current authenticated login. */
  @PreAuthorize("hasRole('OWNER')")
  public synchronized List<StudioSessionView> studioSessions(
      String ownerId, String deviceId, String root) {
    expirePending();
    return sessions.values().stream()
        .filter(
            runtime ->
                runtime.ownerId.equals(ownerId)
                    && runtime.root != null
                    && runtime.device.id().equals(deviceId)
                    && runtime.root.equals(root)
                    && runtime.terminal != null)
        .sorted(Comparator.comparingLong(runtime -> runtime.createdAt))
        .map(
            runtime ->
                new StudioSessionView(
                    runtime.id, runtime.device.id(), runtime.root, runtime.attached))
        .toList();
  }

  public RuntimeSession owned(String id, String ownerId) {
    RuntimeSession runtime = sessions.get(id);
    if (runtime == null || !runtime.ownerId.equals(ownerId))
      throw new WorkspaceException(404, "실행 세션을 찾을 수 없습니다.");
    return runtime;
  }

  /**
   * Retains only Studio project shells, bounded by the login session and a 30 minute idle lease.
   */
  public synchronized void detachTerminal(RuntimeSession runtime) {
    if (sessions.get(runtime.id) != runtime) return;
    if (runtime.terminal != null) runtime.terminal.detach();
    runtime.attached = false;
    runtime.detachedAt = System.currentTimeMillis();
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
            runtime ->
                !runtime.attached
                    && (runtime.terminal == null
                        ? System.currentTimeMillis() - runtime.createdAt > 60000
                        : System.currentTimeMillis() - runtime.detachedAt > 1800000))
        .map(runtime -> runtime.id)
        .toList()
        .forEach(this::close);
  }

  @PreDestroy
  public void shutdown() {
    List.copyOf(sessions.keySet()).forEach(this::close);
  }
}
