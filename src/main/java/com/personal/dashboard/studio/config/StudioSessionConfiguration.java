package com.personal.dashboard.studio.config;

import com.personal.dashboard.studio.service.StudioBrowserService;
import jakarta.servlet.http.*;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.*;

/** Project previews follow the existing login-session lifetime. */
@Configuration
public class StudioSessionConfiguration {
  private static final class Cleanup implements HttpSessionListener, HttpSessionIdListener {
    private final StudioBrowserService service;

    Cleanup(StudioBrowserService service) {
      this.service = service;
    }

    public void sessionDestroyed(HttpSessionEvent event) {
      service.closeOwner(event.getSession().getId());
    }

    public void sessionIdChanged(HttpSessionEvent event, String previous) {
      service.closeOwner(previous);
    }
  }

  @Bean
  ServletListenerRegistrationBean<Cleanup> studioPreviewCleanup(StudioBrowserService service) {
    return new ServletListenerRegistrationBean<>(new Cleanup(service));
  }
}
