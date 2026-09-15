package com.personal.dashboard.studio;

import com.personal.dashboard.studio.service.StudioService;
import jakarta.servlet.http.*;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.*;

/** Disconnection and logout cleanup for remote development commands. */
@Configuration
public class StudioConfiguration {
  @Bean
  ServletListenerRegistrationBean<Cleanup> studioCleanup(StudioService service) {
    return new ServletListenerRegistrationBean<>(new Cleanup(service));
  }

  static final class Cleanup implements HttpSessionListener, HttpSessionIdListener {
    private final StudioService service;

    Cleanup(StudioService service) {
      this.service = service;
    }

    public void sessionDestroyed(HttpSessionEvent event) {
      service.closeOwner(event.getSession().getId());
    }

    public void sessionIdChanged(HttpSessionEvent event, String oldId) {
      service.closeOwner(oldId);
    }
  }
}
