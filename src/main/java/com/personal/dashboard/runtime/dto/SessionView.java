package com.personal.dashboard.runtime.dto;

/** Session handle; URL is populated only for explicitly selected client-browser mode. */
public record SessionView(String id, String kind, String label, String url) {}
