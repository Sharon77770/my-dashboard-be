package com.personal.dashboard.realtime.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Coalesces UI invalidations; payloads contain no resource bodies or credentials. */
@Service
public class WorkspaceEvents {
  public record Frame(
      String type, String epoch, long revision, Set<String> topics, Set<String> jobs) {}

  private record Subscriber(String owner, Consumer<Frame> send) {}

  private final String epoch = UUID.randomUUID().toString();
  private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
  private final Set<String> pending = new HashSet<>();
  private final Map<String, Set<String>> jobs = new HashMap<>();
  private long revision;
  private long lastHeartbeat = System.currentTimeMillis();

  public synchronized void changed(String... topics) {
    pending.addAll(Arrays.asList(topics));
  }

  public synchronized void jobChanged(String owner, String id) {
    if (subscribers.values().stream().noneMatch(value -> value.owner().equals(owner))) return;
    jobs.computeIfAbsent(owner, ignored -> new HashSet<>()).add(id);
  }

  public synchronized void subscribe(String id, String owner, Consumer<Frame> send) {
    subscribers.put(id, new Subscriber(owner, send));
    send.accept(new Frame("ready", epoch, revision, Set.of(), Set.of()));
  }

  public void unsubscribe(String id) {
    subscribers.remove(id);
  }

  @Scheduled(fixedDelay = 200)
  public synchronized void flush() {
    boolean heartbeat = System.currentTimeMillis() - lastHeartbeat >= 20000;
    if (pending.isEmpty() && jobs.isEmpty() && !heartbeat) return;
    if (heartbeat) lastHeartbeat = System.currentTimeMillis();
    long current = ++revision;
    Set<String> topics = Set.copyOf(pending);
    for (var subscriber : subscribers.values()) {
      try {
        subscriber
            .send()
            .accept(
                new Frame(
                    heartbeat ? "heartbeat" : "changed",
                    epoch,
                    current,
                    topics,
                    Set.copyOf(jobs.getOrDefault(subscriber.owner(), Set.of()))));
      } catch (RuntimeException ignored) {
        /* The transport closes failed connections. */
      }
    }
    pending.clear();
    jobs.clear();
  }
}
