package com.personal.dashboard.communication.service;

import com.personal.dashboard.communication.adapter.DiscordGatewayAdapter;
import com.personal.dashboard.communication.repository.CommunicationRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Optional account reconciliation; a missing bot or gateway never blocks other apps. */
@Service
public class CommunicationGatewayService {
  private final boolean enabled;
  private final CommunicationRepository repository;
  private final CommunicationTokens tokens;
  private final DiscordGatewayAdapter gateway;
  private final CommunicationEventService events;

  public record Status(boolean enabled, List<DiscordGatewayAdapter.State> accounts) {}

  public CommunicationGatewayService(
      @Value("${COMMUNICATION_DISCORD_GATEWAY_ENABLED:false}") boolean enabled,
      CommunicationRepository repository,
      CommunicationTokens tokens,
      DiscordGatewayAdapter gateway,
      CommunicationEventService events) {
    this.enabled = enabled;
    this.repository = repository;
    this.tokens = tokens;
    this.gateway = gateway;
    this.events = events;
  }

  @Scheduled(fixedDelay = 10000, initialDelay = 15000)
  public void reconcile() {
    if (!enabled) return;
    Set<String> ids = new HashSet<>();
    for (var account : repository.accounts())
      if (account.provider().equals("DISCORD")) {
        ids.add(account.id());
        try {
          gateway.connect(
              account.id(),
              tokens.accessToken(account),
              (type, data) -> events.discord(account.id(), type, data));
        } catch (Exception ignored) {
        }
      }
    gateway.retain(ids);
  }

  @Scheduled(fixedDelay = 1000, initialDelay = 15000)
  public void tick() {
    if (enabled) gateway.tick();
  }

  @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER')")
  public Status status() {
    return new Status(enabled, gateway.states());
  }
}
