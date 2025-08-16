package com.example.analytics.controller;

import com.example.analytics.dto.EventDTO;
import com.example.analytics.service.AnalyticsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ApiController {

  private final AnalyticsService service;

  @Value("${app.active-user-window-min}") private long auWin;
  @Value("${app.pageview-window-min}") private long pvWin;
  @Value("${app.active-session-window-min}") private long asWin;

  public ApiController(AnalyticsService service) { this.service = service; }

  @GetMapping("/health")
  public Map<String, Object> health() { return Map.of("status", "ok"); }

  @PostMapping(path = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> ingest(@Valid @RequestBody EventDTO dto) {
    if (!service.allowEvent()) throw new TooManyRequestsException("rate_limit_exceeded");
    service.processEvent(dto.getTimestamp(), dto.getUser_id(), dto.getEvent_type(), dto.getPage_url(), dto.getSession_id());
    return Map.of("status", "accepted");
  }

  @GetMapping("/metrics/active_users")
  public Map<String, Object> activeUsers() { return Map.of("window_minutes", auWin, "active_users", service.getActiveUsers()); }

  @GetMapping("/metrics/top_pages")
  public Map<String, Object> topPages() { return Map.of("window_minutes", pvWin, "top_pages", service.getTopPages()); }

  @GetMapping("/metrics/active_sessions")
  public Map<String, Object> activeSessions(@RequestParam("user_id") String userId) {
    if (userId == null || userId.isBlank()) {
      return Map.of("error", "missing_user_id", "message", "provide user_id query param");
    }
    return Map.of("window_minutes", asWin, "user_id", userId, "active_sessions", service.getActiveSessionsForUser(userId));
  }

  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public static class TooManyRequestsException extends RuntimeException { public TooManyRequestsException(String m) { super(m); } }
}
