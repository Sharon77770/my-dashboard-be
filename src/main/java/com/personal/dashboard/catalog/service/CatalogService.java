package com.personal.dashboard.catalog.service;

import com.personal.dashboard.catalog.dto.*;
import com.personal.dashboard.catalog.entity.*;
import com.personal.dashboard.catalog.repository.CatalogRepository;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.integration.SshAdapter;
import com.personal.dashboard.global.security.CredentialVault;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates and persists personal workspace resources without exposing stored credentials. */
@Service
@DependsOn("workspaceSchema")
public class CatalogService {
  private final CatalogRepository repository;
  private final CredentialVault vault;
  private final SshAdapter ssh;
  private final com.personal.dashboard.global.integration.DeviceNetworkAdapter network;

  public CatalogService(
      CatalogRepository repository,
      CredentialVault vault,
      SshAdapter ssh,
      com.personal.dashboard.global.integration.DeviceNetworkAdapter network,
      @Value("${workspace.root:./data/files}") String root)
      throws Exception {
    this.repository = repository;
    this.vault = vault;
    this.ssh = ssh;
    this.network = network;
    Files.createDirectories(Path.of(root));
    repository.save(
        new DeviceRecord(
            "local",
            "Dashboard Server",
            "localhost",
            22,
            "",
            "",
            "",
            Path.of(root).toAbsolutePath().normalize().toString(),
            "NONE",
            3389,
            "",
            "",
            "",
            "",
            true));
  }

  public DeviceRecord requireDevice(String id) {
    return repository.device(id).orElseThrow(() -> new WorkspaceException(404, "장비를 찾을 수 없습니다."));
  }

  public String connectionHost(DeviceRecord device) {
    return network.resolve(device);
  }

  public ApplicationRecord requireApplication(String id) {
    return repository.applications().stream()
        .filter(app -> app.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new WorkspaceException(404, "앱을 찾을 수 없습니다."));
  }

  public List<DeviceView> devices() {
    return repository.devices().stream().map(this::view).toList();
  }

  private DeviceView view(DeviceRecord device) {
    return new DeviceView(
        device.id(),
        device.name(),
        device.host(),
        device.sshPort(),
        device.username(),
        !device.passwordCipher().isEmpty(),
        device.fingerprint(),
        device.rootPath(),
        device.remoteProtocol(),
        device.remotePort(),
        device.remoteUsername(),
        !device.remotePasswordCipher().isEmpty(),
        device.mac(),
        device.broadcast(),
        device.pinned(),
        device.networkMode(),
        device.jumpDeviceIds());
  }

  @PreAuthorize("hasRole('OWNER')")
  @Transactional
  public DeviceView saveDevice(String id, DeviceRequest request) {
    DeviceRecord old = id == null ? null : requireDevice(id);
    if ("local".equals(id)) throw new WorkspaceException(400, "기본 서버의 실행 경로는 환경변수로 설정합니다.");
    if (request.host().contains("/")
        || request.host().contains(" ")
        || request.host().startsWith("-"))
      throw new WorkspaceException(400, "호스트 이름 또는 IP 주소를 입력해 주세요.");
    if (!blank(request.fingerprint()).isEmpty()
        && !request.fingerprint().matches("SHA256:[A-Za-z0-9+/]{43}=?"))
      throw new WorkspaceException(400, "SSH 지문은 SHA256 형식으로 입력해 주세요.");
    if (!blank(request.mac()).isEmpty()
        && !request.mac().matches("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}"))
      throw new WorkspaceException(400, "MAC 주소 형식을 확인해 주세요.");
    if (!request.rootPath().startsWith("/") || request.rootPath().contains("\u0000"))
      throw new WorkspaceException(400, "SFTP 루트는 절대 경로여야 합니다.");
    List<String> jumpDeviceIds = resolveJumpDeviceIds(request, old);
    validateJumpDeviceIds(id, jumpDeviceIds);
    DeviceRecord device =
        new DeviceRecord(
            id == null ? UUID.randomUUID().toString() : id,
            request.name(),
            request.host(),
            request.sshPort(),
            blank(request.username()),
            secret(request.password(), old == null ? "" : old.passwordCipher()),
            resolveFingerprint(request, old, jumpDeviceIds),
            request.rootPath(),
            request.remoteProtocol(),
            request.remotePort(),
            blank(request.remoteUsername()),
            secret(request.remotePassword(), old == null ? "" : old.remotePasswordCipher()),
            blank(request.mac()),
            blank(request.broadcast()),
            request.pinned(),
            request.networkMode() == null
                ? (old == null ? NetworkMode.DIRECT : old.networkMode())
                : request.networkMode(),
            jumpDeviceIds);
    repository.save(device);
    return view(device);
  }

  private List<String> resolveJumpDeviceIds(DeviceRequest request, DeviceRecord old) {
    return request.jumpDeviceIds() == null
        ? (old == null ? List.of() : old.jumpDeviceIds())
        : List.copyOf(request.jumpDeviceIds());
  }

  private void validateJumpDeviceIds(String targetId, List<String> jumpDeviceIds) {
    if (jumpDeviceIds.size() > 5) throw new WorkspaceException(400, "점프 프록시는 최대 5개까지 지정할 수 있습니다.");
    if (new HashSet<>(jumpDeviceIds).size() != jumpDeviceIds.size())
      throw new WorkspaceException(400, "점프 프록시는 중복해서 지정할 수 없습니다.");
    for (String jumpId : jumpDeviceIds) {
      if (jumpId == null || jumpId.isBlank() || "local".equals(jumpId) || jumpId.equals(targetId))
        throw new WorkspaceException(400, "유효한 원격 점프 장비를 선택해 주세요.");
      DeviceRecord jump = requireDevice(jumpId);
      if (jump.username().isBlank()
          || jump.passwordCipher().isBlank()
          || jump.fingerprint().isBlank())
        throw new WorkspaceException(400, "점프 장비는 SSH 계정, 비밀번호, 호스트 키 지문이 등록되어 있어야 합니다.");
      if (!jump.jumpDeviceIds().isEmpty())
        throw new WorkspaceException(400, "점프 장비 자체에는 다른 점프 프록시를 설정할 수 없습니다.");
    }
  }

  /** Missing UI fingerprints preserve the same host key or enroll a newly selected SSH host. */
  private String resolveFingerprint(
      DeviceRequest request, DeviceRecord old, List<String> jumpDeviceIds) {
    if (request.fingerprint() != null) return request.fingerprint();
    if (old != null
        && old.host().equalsIgnoreCase(request.host())
        && old.sshPort() == request.sshPort()
        && !old.fingerprint().isBlank()) return old.fingerprint();
    if (blank(request.username()).isBlank()) return "";
    var known =
        repository.devices().stream()
            .filter(
                device ->
                    !device.id().equals("local")
                        && device.host().equalsIgnoreCase(request.host())
                        && device.sshPort() == request.sshPort())
            .map(DeviceRecord::fingerprint)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    if (known.size() > 1)
      throw new WorkspaceException(409, "같은 호스트의 저장된 SSH 키가 서로 다릅니다. 기존 장비 설정을 확인해 주세요.");
    String password = request.password();
    if (password == null || password.isEmpty())
      password = old == null ? "" : vault.decrypt(old.passwordCipher());
    String host =
        network.resolve(
            request.host(),
            request.networkMode() == null && old != null
                ? old.networkMode()
                : request.networkMode());
    String expected = known.isEmpty() ? "" : known.getFirst();
    // Enrollment must use the same ordered route as subsequent SSH connections.
    var discovered =
        jumpDeviceIds.isEmpty()
            ? ssh.discover(host, request.sshPort(), request.username(), password, expected)
            : ssh.discover(
                host,
                request.sshPort(),
                request.username(),
                password,
                expected,
                jumpDeviceIds.stream().map(this::requireDevice).toList());
    return discovered.fingerprint();
  }

  private String secret(String raw, String previous) {
    return raw == null || raw.isEmpty() ? previous : vault.encrypt(raw);
  }

  private String blank(String value) {
    return value == null ? "" : value;
  }

  @PreAuthorize("hasRole('OWNER')")
  @Transactional
  public void deleteDevice(String id) {
    if (id.equals("local")) throw new WorkspaceException(400, "기본 서버는 삭제할 수 없습니다.");
    requireDevice(id);
    repository.deleteDevice(id);
  }

  public List<ApplicationView> applications() {
    return repository.applications().stream()
        .map(app -> new ApplicationView(app.id(), app.name(), app.url(), app.pinned()))
        .toList();
  }

  @PreAuthorize("hasRole('OWNER')")
  @Transactional
  public ApplicationView saveApplication(String id, ApplicationRequest request) {
    if (id != null) requireApplication(id);
    URI uri = URI.create(request.url());
    if (!Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null)
      throw new WorkspaceException(400, "인증정보가 포함되지 않은 HTTP(S) URL을 입력해 주세요.");
    ApplicationRecord app =
        new ApplicationRecord(
            id == null ? UUID.randomUUID().toString() : id,
            request.name(),
            uri.toString(),
            request.pinned());
    repository.save(app);
    return new ApplicationView(app.id(), app.name(), app.url(), app.pinned());
  }

  @PreAuthorize("hasRole('OWNER')")
  public void deleteApplication(String id) {
    repository.deleteApplication(id);
  }

  public List<ClipView> clips() {
    return repository.clips();
  }

  @PreAuthorize("hasRole('OWNER')")
  public ClipView saveClip(ClipRequest request) {
    ClipView clip =
        new ClipView(
            UUID.randomUUID().toString(),
            request.content(),
            System.currentTimeMillis() + request.minutes() * 60000L);
    repository.saveClip(clip);
    return clip;
  }

  @PreAuthorize("hasRole('OWNER')")
  public void deleteClip(String id) {
    repository.deleteClip(id);
  }

  public List<BookmarkView> bookmarks() {
    return repository.bookmarks();
  }

  @PreAuthorize("hasRole('OWNER')")
  public void saveBookmark(BookmarkRequest request) {
    requireDevice(request.deviceId());
    if (!request.path().startsWith("/") || request.path().contains(".."))
      throw new WorkspaceException(400, "즐겨찾기 경로를 확인해 주세요.");
    repository.saveBookmark(
        new BookmarkView(UUID.randomUUID().toString(), request.deviceId(), request.path()));
  }

  @PreAuthorize("hasRole('OWNER')")
  public void deleteBookmark(String id) {
    repository.deleteBookmark(id);
  }

  public List<ActivityView> activity() {
    return repository.activity();
  }

  public void record(String kind, String target, String label, String path) {
    repository.activity(kind, target, label, path);
  }

  public Preferences preferences() {
    return repository.preferences();
  }

  public WorkspaceView workspace() {
    return new WorkspaceView(
        devices(),
        applications(),
        clips(),
        bookmarks(),
        activity(),
        preferences(),
        browserSettings(),
        repository.tabs());
  }

  @PreAuthorize("hasRole('OWNER')")
  @Transactional
  public void tabs(TabLayout layout) {
    if (layout.tabs().stream().map(TabRequest::id).distinct().count() != layout.tabs().size())
      throw new WorkspaceException(400, "중복된 탭입니다.");
    repository.tabs(layout.tabs());
  }

  public List<ActivityView> search(String query) {
    String needle = query.strip().toLowerCase(java.util.Locale.ROOT);
    if (needle.length() > 200) throw new WorkspaceException(400, "검색어는 200자 이하로 입력해 주세요.");
    List<ActivityView> results = new ArrayList<>();
    for (DeviceView device : devices()) {
      for (String kind : List.of("TERMINAL", "FILES", "REMOTE", "DOCKER", "GPU")) {
        if (kind.equals("REMOTE") && device.remoteProtocol().equals("NONE")) continue;
        results.add(
            new ActivityView(
                device.id() + kind, kind, device.id(), device.name() + " · " + kind, "/", 0));
      }
    }
    for (ApplicationView app : applications())
      results.add(new ActivityView(app.id(), "APP", app.id(), app.name(), "", 0));
    for (BookmarkView bookmark : bookmarks())
      results.add(
          new ActivityView(
              bookmark.id(),
              "FILES",
              bookmark.deviceId(),
              requireDevice(bookmark.deviceId()).name() + " · " + bookmark.path(),
              bookmark.path(),
              0));
    return results.stream()
        .filter(result -> result.label().toLowerCase(java.util.Locale.ROOT).contains(needle))
        .limit(50)
        .toList();
  }

  public BrowserSettings browserSettings() {
    return repository.browserSettings();
  }

  @PreAuthorize("hasRole('OWNER')")
  public void browserSettings(BrowserSettings settings) {
    if (settings.mode().equals("REMOTE")
        && !requireDevice(settings.deviceId()).remoteProtocol().equals("VNC"))
      throw new WorkspaceException(400, "원격 브라우저 서버는 VNC 장비를 선택해 주세요.");
    repository.browserSettings(settings);
  }

  @PreAuthorize("hasRole('OWNER')")
  public void preferences(Preferences preferences) {
    repository.preferences(preferences);
  }
}
