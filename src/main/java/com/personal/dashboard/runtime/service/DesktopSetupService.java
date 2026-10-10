package com.personal.dashboard.runtime.service;

import com.personal.dashboard.catalog.dto.DeviceRequest;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.*;
import com.personal.dashboard.runtime.dto.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Service;

/** One bounded setup per device; only verified connections become READY. */
@Service
@PreAuthorize("hasRole('OWNER')")
public class DesktopSetupService {
  private final CatalogService catalog;
  private final DesktopSetupAdapter setup;
  private final RemoteAdapter remote;
  private final Map<String, DesktopSetupView> jobs = new ConcurrentHashMap<>();

  public DesktopSetupService(
      CatalogService catalog, DesktopSetupAdapter setup, RemoteAdapter remote) {
    this.catalog = catalog;
    this.setup = setup;
    this.remote = remote;
  }

  public DesktopSetupView status(String id) {
    catalog.requireDevice(id);
    return jobs.getOrDefault(id, new DesktopSetupView("IDLE", "자동 구성을 시작하세요."));
  }

  /** Inspection never runs the installer. Existing profiles remain the first choice. */
  public DesktopSetupPlan plan(String id) {
    DeviceRecord device = catalog.requireDevice(id);
    if (id.equals("local"))
      return new DesktopSetupPlan(
          "MANUAL", "SSH 장비를 추가하세요", "대시보드 기본 서버 대신 원격으로 사용할 장비를 SSH로 등록하세요.", "", false, false);
    var managed = setup.managed(device);
    if (managed != null
        && managed.port() == device.remotePort()
        && device.remoteProtocol().equals("VNC"))
      return new DesktopSetupPlan(
          "MANAGED",
          "가상 데스크톱이 준비되어 있습니다",
          "이전에 만든 화면을 확인하고, 재부팅으로 종료된 경우 다시 시작합니다. SSH로 안전하게 연결합니다.",
          "연결하기",
          true,
          false);
    if (!device.remoteProtocol().equals("NONE"))
      return new DesktopSetupPlan(
          "EXISTING",
          "저장된 " + device.remoteProtocol() + "로 연결합니다",
          "현재 원격 화면과 계정을 그대로 사용합니다. 연결이 안 되면 아래 연결 설정에서 수정할 수 있습니다.",
          "연결하기",
          true,
          false);
    String code = setup.inspect(device);
    return switch (code) {
      case "TOOLS_READY" ->
          new DesktopSetupPlan(
              "LINUX",
              "가상 데스크톱을 시작할 수 있습니다",
              "설치된 도구로 별도 작업 화면을 만듭니다. 실제 모니터 화면과는 다르며 원격 포트를 열 필요가 없습니다.",
              "화면 준비 및 연결",
              true,
              false);
      case "INSTALL", "ADMIN_REQUIRED" ->
          new DesktopSetupPlan(
              "LINUX",
              "Linux 데스크톱을 자동으로 준비합니다",
              "필요한 데스크톱·파일 관리자·원격 접속 도구를 설치합니다. 최대 12분이 걸릴 수 있으며, 설치 후 자동 연결합니다. 실제 모니터와 별도 화면입니다.",
              "설치하고 연결",
              true,
              code.equals("ADMIN_REQUIRED"));
      default ->
          new DesktopSetupPlan("MANUAL", "기존 원격 화면으로 연결하세요", guidance(code), "", false, false);
    };
  }

  /** Changes only remote fields, preserving SSH trust, secrets and jump routes. */
  public synchronized void connection(String id, DesktopConnectionRequest request) {
    DeviceRecord device = catalog.requireDevice(id);
    if (jobs.containsKey(id) && jobs.get(id).state().equals("RUNNING"))
      throw new WorkspaceException(409, "준비가 끝난 뒤 연결 설정을 변경하세요.");
    catalog.saveDevice(
        id,
        profileRequest(
            device, request.protocol(), request.port(), request.username(), request.password()));
    jobs.remove(id);
  }

  public DesktopSetupView start(String id) {
    return start(id, new DesktopSetupRequest(null));
  }

  public synchronized DesktopSetupView start(String id, DesktopSetupRequest request) {
    DeviceRecord device = catalog.requireDevice(id);
    if (id.equals("local")) throw new WorkspaceException(400, "자동 구성은 SSH로 등록한 장비를 대상으로 합니다.");
    if (jobs.containsKey(id) && jobs.get(id).state().equals("RUNNING")) return jobs.get(id);
    if (jobs.values().stream().filter(item -> item.state().equals("RUNNING")).count() >= 4)
      throw new WorkspaceException(429, "다른 장비의 구성이 끝난 뒤 시도하세요.");
    if (jobs.size() >= 64)
      jobs.entrySet().removeIf(entry -> !entry.getValue().state().equals("RUNNING"));
    var initial =
        new DesktopSetupView("RUNNING", "기존 연결과 운영체제를 확인하고 있습니다. 설치에는 최대 12분이 걸릴 수 있습니다.");
    jobs.put(id, initial);
    Thread.ofVirtual()
        .start(
            new DelegatingSecurityContextRunnable(
                () ->
                    execute(device, request.sudoPassword() == null ? "" : request.sudoPassword())));
    return initial;
  }

  private void execute(DeviceRecord device, String sudoPassword) {
    try {
      var managed = setup.managed(device);
      if (!device.remoteProtocol().equals("NONE")
          && (managed == null
              || managed.port() != device.remotePort()
              || !device.remoteProtocol().equals("VNC"))) {
        try {
          progress(device.id(), "VERIFYING");
          var connection = remote.open(device, 1280, 800);
          connection.close();
          jobs.put(device.id(), new DesktopSetupView("READY", "기존 원격 데스크톱 연결을 확인했습니다."));
          return;
        } catch (Exception error) {
          throw new WorkspaceException(
              409,
              "기존 원격 설정을 보존했습니다. 장비 설정의 주소·포트·VNC/RDP 비밀번호와 서버 실행 상태를 확인하세요. 아래 연결 설정을 수정한 뒤 다시 연결하세요. Windows 계정은 PIN 대신 계정 비밀번호를 사용합니다.");
        }
      }
      var outcome = setup.configure(device, sudoPassword, stage -> progress(device.id(), stage));
      if (!outcome.code().equals("READY")) {
        jobs.put(
            device.id(),
            new DesktopSetupView("BLOCKED", guidance(outcome.code()), "BLOCKED", outcome.code()));
        return;
      }
      if (!catalog.requireDevice(device.id()).equals(device))
        throw new WorkspaceException(409, "구성 중 장비 설정이 변경되었습니다. 현재 설정을 확인하고 다시 시도하세요.");
      var managedProfile = setup.managed(device);
      if (managedProfile == null)
        throw new WorkspaceException(502, "자동 준비 정보를 찾지 못했습니다. 다시 시도하세요.");
      var candidate =
          new DeviceRecord(
              device.id(),
              device.name(),
              device.host(),
              device.sshPort(),
              device.username(),
              device.passwordCipher(),
              device.fingerprint(),
              device.rootPath(),
              "VNC",
              outcome.port(),
              "",
              managedProfile.passwordCipher(),
              device.mac(),
              device.broadcast(),
              device.pinned(),
              device.networkMode(),
              device.jumpDeviceIds());
      progress(device.id(), "VERIFYING");
      var connection = remote.open(candidate, 1280, 800);
      connection.close();
      if (!catalog.requireDevice(device.id()).equals(device))
        throw new WorkspaceException(409, "준비 중 장비 설정이 변경되었습니다. 다시 연결해 주세요.");
      catalog.saveDevice(
          device.id(),
          profileRequest(device, "VNC", outcome.port(), "", setup.password(managedProfile)));
      jobs.put(device.id(), new DesktopSetupView("READY", "화면 준비가 끝났습니다. 가상 데스크톱으로 연결합니다."));
    } catch (WorkspaceException error) {
      jobs.put(
          device.id(),
          new DesktopSetupView("BLOCKED", error.getMessage(), "BLOCKED", "CONNECTION_FAILED"));
    } catch (Exception error) {
      jobs.put(
          device.id(),
          new DesktopSetupView(
              "BLOCKED", "연결 검증에 실패했습니다. guacd 실행 상태와 SSH 포워딩 허용 여부를 확인한 뒤 다시 시도하세요."));
    }
  }

  private DeviceRequest profileRequest(
      DeviceRecord device, String protocol, int port, String username, String password) {
    return new DeviceRequest(
        device.name(),
        device.host(),
        device.sshPort(),
        device.username(),
        "",
        device.fingerprint(),
        device.rootPath(),
        protocol,
        port,
        username,
        password,
        device.mac(),
        device.broadcast(),
        device.pinned(),
        device.networkMode(),
        device.jumpDeviceIds());
  }

  private void progress(String id, String stage) {
    String message =
        switch (stage) {
          case "INSTALLING" -> "데스크톱에 필요한 도구를 준비하고 있습니다. 처음 설치할 때는 몇 분 걸릴 수 있습니다.";
          case "STARTING" -> "작업 화면을 시작하고 있습니다.";
          case "VERIFYING" -> "화면에 실제로 접속할 수 있는지 확인하고 있습니다.";
          default -> "장비와 기존 연결을 확인하고 있습니다.";
        };
    jobs.put(id, new DesktopSetupView("RUNNING", message, stage, ""));
  }

  static String guidance(String code) {
    return switch (code) {
      case "MACOS" ->
          "macOS는 시스템 설정에서 화면 공유와 접근 권한을 직접 허용해야 합니다. 허용 후 장비 설정에 VNC 포트·인증정보를 입력하고 다시 연결하세요.";
      case "UNSUPPORTED_OS" ->
          "이 OS 또는 SSH 셸에서는 자동 설치를 지원하지 않습니다. Windows는 VNC 서버를 설치하거나 지원 에디션의 RDP를 활성화하고 장비 설정에 등록하세요. Windows Home·모바일 등은 지원 서버가 별도로 필요합니다.";
      case "ADMIN_REQUIRED" ->
          "설치 권한을 확인하지 못했습니다. SSH 계정의 sudo 비밀번호를 입력하고 다시 시도하세요. 비밀번호는 이번 설치에만 사용하며 저장하지 않습니다.";
      case "NO_SUDO" -> "이 계정에는 설치 권한이 없습니다. 관리자에게 데스크톱 도구 설치를 요청하거나, 아래에서 이미 사용 중인 원격 화면을 등록하세요.";
      case "EXISTING_VNC" -> "이미 실행 중인 VNC를 발견하여 설정을 변경하지 않았습니다. 장비 설정에서 기존 포트와 비밀번호를 등록해 연결하세요.";
      case "UNSUPPORTED_PACKAGES", "PYTHON_REQUIRED" ->
          "자동 패키지 설치는 Debian/Ubuntu apt 환경을 지원합니다. 다른 Linux는 Python 3, TigerVNC 서버·vncpasswd, Openbox, xterm을 관리자가 설치한 후 다시 시도하세요.";
      case "NO_PORT" -> "5920–5999 포트 또는 대응하는 X 디스플레이가 모두 사용 중입니다. 기존 프로세스는 종료하지 않았습니다.";
      case "BUSY" -> "같은 SSH 계정에서 다른 자동 구성이 실행 중입니다. 완료 후 다시 시도하세요.";
      case "TIMEOUT", "COMMAND_FAILED" ->
          "패키지 설치가 실패하거나 제한 시간을 초과했습니다. 저장소 네트워크·디스크 공간·패키지 관리자 잠금을 확인하고 다시 시도하세요.";
      default -> "가상 화면을 시작하지 못했습니다. 홈 디렉터리 권한·X 서버·설치 패키지를 확인하세요. 기존 원격 서비스는 변경하지 않았습니다.";
    };
  }
}
