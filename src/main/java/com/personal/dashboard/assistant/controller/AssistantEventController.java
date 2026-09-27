package com.personal.dashboard.assistant.controller;

import com.personal.dashboard.assistant.dto.AssistantEvent;
import com.personal.dashboard.assistant.service.AssistantEvents;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Delivers browser navigation requests emitted by Codex MCP tools. */
@RestController
@RequestMapping("/api/v1/assistant/events")
public class AssistantEventController {
  private final AssistantEvents events;

  public AssistantEventController(AssistantEvents events) {
    this.events = events;
  }

  @GetMapping
  public List<AssistantEvent> events(@RequestParam(defaultValue = "0") long after) {
    return events.after(Math.max(0, after));
  }
}
