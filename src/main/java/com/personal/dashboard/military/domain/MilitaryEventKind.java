package com.personal.dashboard.military.domain;

/** Only LEAVE entries consume a user-entered leave budget. */
public enum MilitaryEventKind {
  LEAVE,
  TRAINING,
  DUTY,
  OTHER
}
