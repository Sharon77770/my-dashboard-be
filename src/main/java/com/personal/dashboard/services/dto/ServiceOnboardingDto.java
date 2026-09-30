package com.personal.dashboard.services.dto;

import java.util.List;
import java.util.Map;

/** Safe, temporary Service Builder contracts. Resource metadata is untrusted data. */
public final class ServiceOnboardingDto {
  private ServiceOnboardingDto() {}

  public record Candidate(
      String type,
      String reference,
      String deviceId,
      String displayName,
      String confidence,
      String reason,
      boolean selected,
      boolean requiresConfirmation,
      String composeProject,
      String composeService,
      String image,
      String state,
      String containerId,
      String ports,
      String workingDirectory) {}

  public record Discovery(
      List<Candidate> candidates,
      List<ExistingService> services,
      Map<String, String> sources,
      List<String> questions) {}

  public record ExistingService(
      String id, String name, String environment, List<ResourceLink> resources) {}

  public record ResourceLink(String type, String reference, String deviceId) {}

  public record Draft(
      String id,
      String threadId,
      String serviceId,
      String name,
      String description,
      String environment,
      String status,
      long revision,
      long serviceUpdatedAt,
      List<Candidate> candidates,
      List<String> questions,
      long updatedAt,
      List<ResourceLink> excludedResources) {
    public Draft(
        String id,
        String threadId,
        String serviceId,
        String name,
        String description,
        String environment,
        String status,
        long revision,
        long serviceUpdatedAt,
        List<Candidate> candidates,
        List<String> questions,
        long updatedAt) {
      this(
          id,
          threadId,
          serviceId,
          name,
          description,
          environment,
          status,
          revision,
          serviceUpdatedAt,
          candidates,
          questions,
          updatedAt,
          List.of());
    }
  }

  public record DraftRequest(
      String threadId,
      String serviceId,
      String name,
      String description,
      String environment,
      List<ServiceDto.ResourceRequest> resources) {}

  public record DraftUpdate(
      long revision,
      String name,
      String description,
      String environment,
      List<ServiceDto.ResourceRequest> resources,
      List<ResourceLink> excludedResources) {
    public DraftUpdate(
        long revision,
        String name,
        String description,
        String environment,
        List<ServiceDto.ResourceRequest> resources) {
      this(revision, name, description, environment, resources, null);
    }
  }

  public record Revision(long revision) {}
}
