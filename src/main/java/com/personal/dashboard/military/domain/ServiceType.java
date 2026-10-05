package com.personal.dashboard.military.domain;

/** Current MMA service-duration presets; CUSTOM always requires a user supplied end date. */
public enum ServiceType {
  ARMY("육군", 18),
  NAVY("해군", 20),
  AIR_FORCE("공군", 21),
  MARINES("해병대", 18),
  SOCIAL_SERVICE("사회복무요원", 21),
  CUSTOM("직접 설정", 0);

  private final String label;
  private final int months;

  ServiceType(String label, int months) {
    this.label = label;
    this.months = months;
  }

  public String label() {
    return label;
  }

  public int months() {
    return months;
  }

  public boolean soldier() {
    return this != SOCIAL_SERVICE && this != CUSTOM;
  }
}
