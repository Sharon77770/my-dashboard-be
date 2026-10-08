package com.personal.dashboard.studio.entity;

/** Persistence-only record inside a vault-encrypted project file. */
public record StudioApiExchange(
    String id,
    long time,
    String method,
    String url,
    int status,
    String requestJson,
    String responseJson) {}
