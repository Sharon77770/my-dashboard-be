package com.personal.dashboard.runtime.service;

import com.personal.dashboard.catalog.dto.DeviceRequest;
import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.service.CatalogService;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.runtime.adapter.*;
import com.personal.dashboard.runtime.dto.DesktopSetupView;
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

  public synchronized DesktopSetupView start(String id) {
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
    Thread.ofVirtual().start(new DelegatingSecurityContextRunnable(() -> execute(device)));
    return initial;
  }

  private void execute(DeviceRecord device) {
    try {
      var managed = setup.managed(device);
      if (!device.remoteProtocol().equals("NONE")
          && (managed == null
              || managed.port() != device.remotePort()
              || !device.remoteProtocol().equals("VNC"))) {
        try {
          var connection = remote.open(device, 1280, 800);
          connection.close();
          jobs.put(device.id(), new DesktopSetupView("READY", "기존 원격 데스크톱 연결을 확인했습니다."));
          return;
        } catch (Exception error) {
          throw new WorkspaceException(
              409,
              "기존 원격 설정을 보존했습니다. 장비 설정의 주소·포트·VNC/RDP 비밀번호와 서버 실행 상태를 확인하세요. 새 가상 화면을 만들려면 원격 화면을 미사용으로 설정하세요.");
        }
      }
      var outcome = setup.configure(device);
      if (!outcome.code().equals("READY")) {
        jobs.put(device.id(), new DesktopSetupView("BLOCKED", guidance(outcome.code())));
        return;
      }
      if (!catalog.requireDevice(device.id()).equals(device))
        throw new WorkspaceException(409, "구성 중 장비 설정이 변경되었습니다. 현재 설정을 확인하고 다시 시도하세요.");
      catalog.saveDevice(
          device.id(),
          new DeviceRequest(
              device.name(),
              device.host(),
              device.sshPort(),
              device.username(),
              "",
              device.fingerprint(),
              device.rootPath(),
              "VNC",
              outcome.port(),
              "",
              setup.password(setup.managed(device)),
              device.mac(),
              device.broadcast(),
              device.pinned(),
              device.networkMode()));
      {
        var connection = remote.open(catalog.requireDevice(device.id()), 1280, 800);
        connection.close();
        jobs.put(
            device.id(),
            new DesktopSetupView("READY", "SSH 터널로 가상 데스크톱 연결을 확인했습니다. 실제 모니터와 별도 화면입니다."));
      }
    } catch (WorkspaceException error) {
      jobs.put(device.id(), new DesktopSetupView("BLOCKED", error.getMessage()));
    } catch (Exception error) {
      jobs.put(
          device.id(),
          new DesktopSetupView(
              "BLOCKED", "연결 검증에 실패했습니다. guacd 실행 상태와 SSH 포워딩 허용 여부를 확인한 뒤 다시 시도하세요."));
    }
  }

  static String guidance(String code) {
    return switch (code) {
      case "MACOS" ->
          "macOS는 시스템 설정에서 화면 공유와 접근 권한을 직접 허용해야 합니다. 허용 후 장비 설정에 VNC 포트·인증정보를 입력하고 다시 연결하세요.";
      case "UNSUPPORTED_OS" ->
          "이 OS 또는 SSH 셸에서는 자동 설치를 지원하지 않습니다. Windows는 VNC 서버를 설치하거나 지원 에디션의 RDP를 활성화하고 장비 설정에 등록하세요. Windows Home·모바일 등은 지원 서버가 별도로 필요합니다.";
      case "ADMIN_REQUIRED" ->
          "설치 권한이 없습니다. 관리자가 python3, tigervnc-standalone-server, tigervnc-tools, openbox, xterm, xfonts-base를 설치하거나 해당 계정에 필요한 비대화형 sudo 권한을 제공한 후 다시 시도하세요.";
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
