package com.personal.dashboard.assistant.service;

import com.personal.dashboard.assistant.dto.AssistantEvent;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** Keeps a small in-memory feed for the signed-in browser while Codex invokes MCP tools. */
@Service
public class AssistantEvents {
  private final AtomicLong sequence = new AtomicLong();
  private final ArrayDeque<AssistantEvent> events = new ArrayDeque<>();

  public synchronized AssistantEvent navigate(String route) {
    return publish(route, null, "페이지를 열었습니다.");
  }

  public synchronized AssistantEvent openApplication(String id) {
    return publish(null, id, "앱을 열었습니다.");
  }

  private AssistantEvent publish(String route, String applicationId, String message) {
    var event = new AssistantEvent(sequence.incrementAndGet(), route, applicationId, message);
    while (events.size() >= 100) events.removeFirst();
    events.addLast(event);
    return event;
  }

  public synchronized List<AssistantEvent> after(long cursor) {
    return events.stream().filter(event -> event.sequence() > cursor).toList();
  }
}
