package com.personal.dashboard.global.integration;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.catalog.entity.NetworkMode;
import com.personal.dashboard.global.WorkspaceException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Resolves device addresses on the existing shared Tailscale TUN network without direct fallback.
 */
@Component
public class DeviceNetworkAdapter {
  public String resolve(DeviceRecord device) {
    return resolve(device.host(), device.networkMode());
  }

  public String resolve(String host, NetworkMode mode) {
    if (mode == null || mode == NetworkMode.DIRECT) return host;
    if (!available())
      throw new WorkspaceException(
          502, "Tailscale이 연결되지 않았습니다. 기존 Tailscale 서비스의 로그인 상태를 확인해 주세요.");
    try {
      for (InetAddress address : addresses(host))
        if (isTailnet(address)) return address.getHostAddress();
    } catch (Exception exception) {
      throw new WorkspaceException(
          502, "Tailscale 주소를 찾을 수 없습니다. Tailscale IP 또는 MagicDNS 이름과 연결 상태를 확인해 주세요.");
    }
    throw new WorkspaceException(400, "Tailscale 접속에는 장비의 Tailscale IP 또는 MagicDNS 이름을 입력해 주세요.");
  }

  boolean available() {
    try {
      var network = NetworkInterface.getByName("tailscale0");
      if (network == null || !network.isUp()) return false;
      return network.inetAddresses().anyMatch(DeviceNetworkAdapter::isTailnet);
    } catch (Exception exception) {
      return false;
    }
  }

  InetAddress[] addresses(String host) throws Exception {
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                return InetAddress.getAllByName(host);
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            })
        .get(3, TimeUnit.SECONDS);
  }

  static boolean isTailnet(InetAddress address) {
    byte[] bytes = address.getAddress();
    return bytes.length == 4
        ? (bytes[0] & 255) == 100 && (bytes[1] & 255) >= 64 && (bytes[1] & 255) <= 127
        : bytes.length == 16
            && (bytes[0] & 255) == 0xfd
            && (bytes[1] & 255) == 0x7a
            && (bytes[2] & 255) == 0x11
            && (bytes[3] & 255) == 0x5c
            && (bytes[4] & 255) == 0xa1
            && (bytes[5] & 255) == 0xe0;
  }
}
