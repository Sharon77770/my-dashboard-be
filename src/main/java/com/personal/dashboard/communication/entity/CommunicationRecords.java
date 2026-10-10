package com.personal.dashboard.communication.entity;

/** Database-only encrypted account material and immutable action snapshots. */
public final class CommunicationRecords {
  private CommunicationRecords() {}

  public record AccountRecord(
      String id,
      String provider,
      String externalId,
      String label,
      String credentialCipher,
      String capabilities,
      long createdAt) {}

  public record PendingActionRecord(
      String id,
      String accountId,
      String payloadCipher,
      String state,
      long expiresAt,
      String resultId) {}
}
