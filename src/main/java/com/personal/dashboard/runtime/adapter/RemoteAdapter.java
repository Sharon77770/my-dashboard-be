package com.personal.dashboard.runtime.adapter;

import com.personal.dashboard.catalog.entity.DeviceRecord;
import com.personal.dashboard.global.WorkspaceException;
import com.personal.dashboard.global.security.CredentialVault;
import org.apache.guacamole.net.*;
import org.apache.guacamole.protocol.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Connects to guacd; RDP/VNC credentials never appear in browser tunnel messages. */
@Component
public class RemoteAdapter {
  private final CredentialVault vault;
  private final com.personal.dashboard.global.integration.DeviceNetworkAdapter network;
  private final String host;
  private final int port;
  private final DesktopSetupAdapter setup;
  private final com.personal.dashboard.global.integration.SshAdapter ssh;

  public RemoteAdapter(
      CredentialVault vault,
      com.personal.dashboard.global.integration.DeviceNetworkAdapter network,
      DesktopSetupAdapter setup,
      com.personal.dashboard.global.integration.SshAdapter ssh,
      @Value("${workspace.guacd-host:localhost}") String host,
      @Value("${workspace.guacd-port:4822}") int port) {
    this.vault = vault;
    this.network = network;
    this.setup = setup;
    this.ssh = ssh;
    this.host = host;
    this.port = port;
  }

  public GuacamoleSocket open(DeviceRecord device, int width, int height) {
    GuacamoleSocket socket = null;
    net.schmizz.sshj.SSHClient sshClient = null;
    java.net.ServerSocket listener = null;
    try {
      if (device.remoteProtocol().equals("NONE"))
        throw new WorkspaceException(400, "장비의 RDP 또는 VNC 접속을 설정해 주세요.");
      GuacamoleConfiguration configuration = new GuacamoleConfiguration();
      configuration.setProtocol(device.remoteProtocol().toLowerCase(java.util.Locale.ROOT));
      configuration.setParameter("hostname", network.resolve(device));
      configuration.setParameter("port", Integer.toString(device.remotePort()));
      configuration.setParameter("username", device.remoteUsername());
      configuration.setParameter("password", vault.decrypt(device.remotePasswordCipher()));
      var managed = setup.managed(device);
      if (managed != null
          && managed.port() == device.remotePort()
          && device.remoteProtocol().equals("VNC")) {
        sshClient = ssh.connect(device);
        sshClient.setTimeout(0);
        listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        var forwarding =
            sshClient.newLocalPortForwarder(
                new net.schmizz.sshj.connection.channel.direct.Parameters(
                    "127.0.0.1", listener.getLocalPort(), "127.0.0.1", managed.port()),
                listener);
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    forwarding.listen();
                  } catch (java.io.IOException ignored) {
                  }
                });
        configuration.setParameter("hostname", "127.0.0.1");
        configuration.setParameter("port", Integer.toString(listener.getLocalPort()));
        configuration.setParameter("password", setup.password(managed));
      }
      configuration.setParameter("resize-method", "display-update");
      configuration.setParameter("enable-wallpaper", "false");
      GuacamoleClientInformation information = new GuacamoleClientInformation();
      information.setOptimalScreenWidth(width);
      information.setOptimalScreenHeight(height);
      information.setOptimalResolution(96);
      information.getImageMimetypes().add("image/png");
      information.getImageMimetypes().add("image/jpeg");
      socket = new InetGuacamoleSocket(host, port);
      var configured = new ConfiguredGuacamoleSocket(socket, configuration, information);
      if (sshClient == null) return configured;
      final var tunnelClient = sshClient;
      final var tunnelListener = listener;
      return new GuacamoleSocket() {
        public String getProtocol() {
          return configured.getProtocol();
        }

        public org.apache.guacamole.io.GuacamoleReader getReader() {
          return configured.getReader();
        }

        public org.apache.guacamole.io.GuacamoleWriter getWriter() {
          return configured.getWriter();
        }

        public boolean isOpen() {
          return configured.isOpen() && tunnelClient.isConnected();
        }

        public void close() throws org.apache.guacamole.GuacamoleException {
          try {
            configured.close();
          } finally {
            try {
              tunnelListener.close();
            } catch (java.io.IOException ignored) {
            }
            try {
              tunnelClient.close();
            } catch (java.io.IOException ignored) {
            }
          }
        }
      };
    } catch (Exception exception) {
      if (listener != null)
        try {
          listener.close();
        } catch (java.io.IOException ignored) {
        }
      if (sshClient != null)
        try {
          sshClient.close();
        } catch (java.io.IOException ignored) {
        }
      if (socket != null)
        try {
          socket.close();
        } catch (Exception ignored) {
        }
      if (exception instanceof WorkspaceException failure) throw failure;
      throw new WorkspaceException(502, "원격 연결 실패: guacd·장비 주소·인증정보·RDP 인증서를 확인해 주세요.");
    }
  }
}
