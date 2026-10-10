package com.personal.dashboard.communication.service;

import com.personal.dashboard.communication.adapter.BrowserBridgeAdapter;
import com.personal.dashboard.communication.dto.BridgeDto;
import com.personal.dashboard.communication.repository.BridgeRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.dto.SessionView;
import com.personal.dashboard.runtime.service.RuntimeService;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** OWNER bridge profile lifecycle; remote controls reuse session-owned Guacamole. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class BrowserBridgeService {
  private final BridgeRepository repository;
  private final BrowserBridgeAdapter adapter;
  private final RuntimeService runtime;

  public BrowserBridgeService(
      BridgeRepository repository, BrowserBridgeAdapter adapter, RuntimeService runtime) {
    this.repository = repository;
    this.adapter = adapter;
    this.runtime = runtime;
  }

  public BridgeDto.Status status() {
    return new BridgeDto.Status(
        adapter.configured(),
        "Slack·Discord는 웹 화면, 카카오톡은 Wine 앱 화면에서 직접 로그인하고 조작합니다. 화면 모드는 통합 메시지 검색·AI 조회 대상이 아닙니다.");
  }

  public List<BridgeDto.Profile> profiles() {
    return repository.profiles().stream()
        .map(item -> new BridgeDto.Profile(item.id(), item.provider(), item.label()))
        .toList();
  }

  public synchronized BridgeDto.Profile create(BridgeDto.Create request) {
    if (!adapter.configured())
      throw new WorkspaceException(409, "브라우저가 아직 준비되지 않았습니다. 잠시 후 다시 시도해 주세요.");
    if (repository.profiles().size() >= 16) throw new WorkspaceException(409, "프로필은 최대 16개입니다.");
    var record =
        new BridgeRepository.ProfileRecord(
            UUID.randomUUID().toString(), request.provider(), request.label());
    repository.save(record);
    return new BridgeDto.Profile(record.id(), record.provider(), record.label());
  }

  public synchronized SessionView open(String id, String owner) {
    var profile = require(id);
    int port = adapter.start(id, profile.provider());
    return runtime.createCommunicationBrowserSession(id, profile.label(), port, owner);
  }

  public BridgeDto.Snapshot snapshot(String id) {
    var profile = require(id);
    return adapter.snapshot(id, profile.provider());
  }

  public synchronized void stop(String id) {
    var profile = require(id);
    runtime.closeCommunicationBrowserSessions(id);
    adapter.stop(id, profile.provider());
  }

  public synchronized void delete(String id) {
    var profile = require(id);
    runtime.closeCommunicationBrowserSessions(id);
    adapter.delete(id, profile.provider());
    repository.delete(id);
  }

  private BridgeRepository.ProfileRecord require(String id) {
    return repository.profiles().stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new WorkspaceException(404, "브라우저 프로필을 찾을 수 없습니다."));
  }
}
