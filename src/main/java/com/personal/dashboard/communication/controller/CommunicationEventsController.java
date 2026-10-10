package com.personal.dashboard.communication.controller;

import com.personal.dashboard.communication.service.CommunicationEventService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/** The only public communication endpoint: authenticates the bounded raw body by Slack HMAC. */
@RestController
public class CommunicationEventsController {
  private final CommunicationEventService service;

  public CommunicationEventsController(CommunicationEventService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/communications/events/slack")
  public CommunicationEventService.Acknowledgment slack(
      HttpServletRequest request,
      @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String timestamp,
      @RequestHeader(value = "X-Slack-Signature", required = false) String signature)
      throws IOException {
    return service.slack(timestamp, signature, request.getInputStream().readNBytes(1048577));
  }
}
