package com.personal.dashboard.realtime;

import com.personal.dashboard.realtime.controller.WorkspaceSocketHandler;
import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/** Shares the existing HTTP session and default same-origin handshake restrictions. */
@Configuration
public class WorkspaceRealtimeConfiguration implements WebSocketConfigurer {
  private final WorkspaceSocketHandler handler;

  public WorkspaceRealtimeConfiguration(WorkspaceSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/ws/workspace")
        .addInterceptors(
            new HttpSessionHandshakeInterceptor() {
              @Override
              public boolean beforeHandshake(
                  ServerHttpRequest request,
                  ServerHttpResponse response,
                  WebSocketHandler socket,
                  Map<String, Object> attributes)
                  throws Exception {
                if (!(request instanceof ServletServerHttpRequest servlet)) return false;
                var session = servlet.getServletRequest().getSession(false);
                if (session == null) return false;
                attributes.put("workspace.session", session);
                return super.beforeHandshake(request, response, socket, attributes);
              }
            });
  }

  @Bean
  ServletListenerRegistrationBean<HttpSessionEventListener> realtimeSessionCleanup() {
    return new ServletListenerRegistrationBean<>(new HttpSessionEventListener(handler));
  }

  static class HttpSessionEventListener implements HttpSessionListener, HttpSessionIdListener {
    private final WorkspaceSocketHandler handler;

    HttpSessionEventListener(WorkspaceSocketHandler handler) {
      this.handler = handler;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
      handler.closeOwner(event.getSession().getId());
    }

    @Override
    public void sessionIdChanged(HttpSessionEvent event, String oldId) {
      handler.closeOwner(oldId);
    }
  }
}
