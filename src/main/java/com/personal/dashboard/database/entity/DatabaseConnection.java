package com.personal.dashboard.database.entity;

import java.util.Map;

/** Stored connection metadata. Ciphertext never crosses the application boundary. */
public record DatabaseConnection(
    String id,
    String name,
    String type,
    String host,
    int port,
    String databaseName,
    String username,
    String credentialCipher,
    String sslMode,
    String accessMode,
    Map<String, String> metadata,
    long createdAt,
    long updatedAt) {}
