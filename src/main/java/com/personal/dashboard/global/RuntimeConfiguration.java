package com.personal.dashboard.global;

import com.personal.dashboard.runtime.controller.RuntimeSocketHandler;
import com.personal.dashboard.runtime.service.RuntimeService;
import jakarta.servlet.http.*;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * Same-origin WebSocket endpoints and logout/expiry cleanup for server-owned runtime connections.
 */
@Configuration
@EnableWebSocket
@EnableScheduling
public class RuntimeConfiguration implements WebSocketConfigurer {
  private final RuntimeSocketHandler handler;

  public RuntimeConfiguration(RuntimeSocketHandler handler) {
    this.handler = handler;
  }

  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/ws/runtime/*")
        .addInterceptors(new HttpSessionHandshakeInterceptor());
  }

  @Scheduled(fixedDelay = 15000)
  public void cleanup() {
    handler.validateSessions();
  }

  @Bean
  ServletListenerRegistrationBean<RuntimeSessionListener> runtimeCleanup(RuntimeService service) {
    return new ServletListenerRegistrationBean<>(new RuntimeSessionListener(service));
  }
}
