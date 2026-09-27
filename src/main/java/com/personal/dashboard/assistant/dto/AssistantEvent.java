package com.personal.dashboard.assistant.dto;

/** Browser navigation requested by an authenticated assistant tool call. */
public record AssistantEvent(long sequence, String route, String applicationId, String message) {}
