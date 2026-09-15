package com.personal.dashboard.global;

import com.personal.dashboard.runtime.service.RuntimeService;
import jakarta.servlet.http.*;

/** Ends runtime handles on logout, expiry and reauthentication session-ID rotation. */
public class RuntimeSessionListener implements HttpSessionListener, HttpSessionIdListener {
  private final RuntimeService service;

  public RuntimeSessionListener(RuntimeService service) {
    this.service = service;
  }

  @Override
  public void sessionDestroyed(HttpSessionEvent event) {
    service.closeOwner(event.getSession().getId());
  }

  @Override
  public void sessionIdChanged(HttpSessionEvent event, String oldSessionId) {
    service.closeOwner(oldSessionId);
  }
}
