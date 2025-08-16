package com.example.analytics.error;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {
  private final String error;
  private final String message;
  private final Instant timestamp = Instant.now();
  private final List<String> details;
  public ErrorResponse(String error, String message) { this(error, message, null); }
  public ErrorResponse(String error, String message, List<String> details) { this.error = error; this.message = message; this.details = details; }
  public String getError() { return error; }
  public String getMessage() { return message; }
  public Instant getTimestamp() { return timestamp; }
  public List<String> getDetails() { return details; }
}
