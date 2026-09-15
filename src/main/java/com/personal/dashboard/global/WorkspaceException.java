package com.personal.dashboard.global;

/** A safe user-facing failure; external exception details never reach HTTP clients. */
public class WorkspaceException extends RuntimeException {
  private final int status;

  public WorkspaceException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
