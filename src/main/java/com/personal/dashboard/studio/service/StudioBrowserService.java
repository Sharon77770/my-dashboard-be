package com.personal.dashboard.studio.service;

import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.files.adapter.FileAdapter;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.studio.adapter.StudioBrowserAdapter;
import com.personal.dashboard.studio.dto.StudioBrowserDto.Input;
import jakarta.annotation.PreDestroy;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Session-owned project tabs; project switches preserve other tabs without sharing control. */
@Service
public class StudioBrowserService {
  private record Key(String owner, String device, String root) {}

  private static final class Entry {
    final StudioBrowserAdapter.Page page;
    long touched = System.currentTimeMillis();

    Entry(StudioBrowserAdapter.Page page) {
      this.page = page;
    }
  }

  private final Map<Key, Entry> pages = new HashMap<>();
  private final CatalogService catalog;
  private final FileAdapter files;
  private final StudioBrowserAdapter adapter;
  private final String host;
  private final int port;

  public StudioBrowserService(
      CatalogService catalog,
      FileAdapter files,
      StudioBrowserAdapter adapter,
      @Value("${workspace.browser-host:localhost}") String host,
      @Value("${workspace.browser-port:9223}") int port) {
    this.catalog = catalog;
    this.files = files;
    this.adapter = adapter;
    this.host = host;
    this.port = port;
  }

  @PreAuthorize("hasRole('OWNER')")
  public synchronized Map<String, Object> action(String owner, Input input) {
    return action(owner, input, false);
  }

  @PreAuthorize("hasRole('OWNER')")
  public synchronized Map<String, Object> toolAction(String owner, Input input) {
    return action(owner, input, true);
  }

  private Map<String, Object> action(String owner, Input input, boolean projectOnly) {
    var device = catalog.requireDevice(input.deviceId());
    var key = new Key(owner, device.id(), input.root());
    var entry = pages.get(key);
    if (entry != null && !entry.page.connected()) {
      entry.page.close();
      pages.remove(key);
      entry = null;
    }
    if (input.action().equals("close")) {
      if (entry != null) {
        entry.page.close();
        pages.remove(key);
      }
      return Map.of("ok", true);
    }
    if (entry == null) {
      if (!input.action().equals("open")) throw new WorkspaceException(409, "미리보기 URL을 먼저 여세요.");
      StudioBrowserAdapter.validate(input.url());
      try {
        files.projectDirectory(device, input.root());
      } catch (java.io.IOException error) {
        throw new WorkspaceException(400, "프로젝트 폴더를 확인하세요.");
      }
      if (pages.size() >= 8)
        throw new WorkspaceException(429, "열린 브라우저 패널을 닫아 주세요. 최대 8개까지 지원합니다.");
      entry = new Entry(adapter.create(host, port));
      pages.put(key, entry);
    }
    entry.touched = System.currentTimeMillis();
    var page = entry.page;
    if (projectOnly || input.action().equals("open")) page.projectOnly(projectOnly);
    switch (input.action()) {
      case "open" -> page.navigate(device, input.url());
      case "back" -> page.history(-1);
      case "forward" -> page.history(1);
      case "reload" -> page.navigateCommand("Page.reload", Map.of());
      case "click" -> {
        if (input.x() == null || input.y() == null)
          throw new WorkspaceException(400, "클릭 좌표가 필요합니다.");
        page.command(
            "Input.dispatchMouseEvent",
            Map.of(
                "type",
                "mousePressed",
                "x",
                input.x(),
                "y",
                input.y(),
                "button",
                "left",
                "clickCount",
                1));
        page.command(
            "Input.dispatchMouseEvent",
            Map.of(
                "type",
                "mouseReleased",
                "x",
                input.x(),
                "y",
                input.y(),
                "button",
                "left",
                "clickCount",
                1));
      }
      case "text" ->
          page.command(
              "Input.insertText", Map.of("text", Objects.requireNonNullElse(input.text(), "")));
      case "key" -> {
        if (!Set.of(
                "Enter",
                "Tab",
                "Backspace",
                "Escape",
                "ArrowUp",
                "ArrowDown",
                "ArrowLeft",
                "ArrowRight")
            .contains(input.text())) throw new WorkspaceException(400, "지원하지 않는 키입니다.");
        page.command("Input.dispatchKeyEvent", Map.of("type", "keyDown", "key", input.text()));
        page.command("Input.dispatchKeyEvent", Map.of("type", "keyUp", "key", input.text()));
      }
      case "scroll" ->
          page.command(
              "Input.dispatchMouseEvent",
              Map.of(
                  "type",
                  "mouseWheel",
                  "x",
                  600,
                  "y",
                  360,
                  "deltaX",
                  0,
                  "deltaY",
                  Objects.requireNonNullElse(input.delta(), 0)));
      case "snapshot" -> {}
      default -> throw new WorkspaceException(400, "지원하지 않는 브라우저 동작입니다.");
    }
    return page.snapshot();
  }

  public synchronized void closeOwner(String owner) {
    var iterator = pages.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (entry.getKey().owner().equals(owner)) {
        entry.getValue().page.close();
        iterator.remove();
      }
    }
  }

  @Scheduled(fixedDelay = 60000)
  public synchronized void expire() {
    var iterator = pages.values().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (entry.touched < System.currentTimeMillis() - 1800000) {
        entry.page.close();
        iterator.remove();
      }
    }
  }

  @PreDestroy
  public synchronized void close() {
    pages.values().forEach(entry -> entry.page.close());
    pages.clear();
  }
}
