package com.example.analytics.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

  private final StringRedisTemplate redis;

  @Value("${app.rate-limit-per-sec:100}") private int limitPerSec;
  @Value("${app.active-user-window-min}") private long activeUserWinMin;
  @Value("${app.active-session-window-min}") private long activeSessionWinMin;
  @Value("${app.pageview-window-min}") private int pageviewWinMin;

  private static final String KEY_ACTIVE_USERS = "au:z";
  private static final String KEY_RATE = "rl:z";

  public AnalyticsService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  private String sessionsKey(String userId) { return "sessions:%s:z".formatted(userId); }

  private String pageviewKey(ZonedDateTime zdt) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    return "pv:%s".formatted(zdt.format(fmt));
  }

  private long epochMs(Instant t) { return t.toEpochMilli(); }

  private ZonedDateTime parseTimestamp(String iso) {
    try {
      return ZonedDateTime.parse(iso, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(ZoneOffset.UTC);
    } catch (Exception ex) {
      LocalDateTime ldt = LocalDateTime.parse(iso, DateTimeFormatter.ISO_DATE_TIME);
      return ldt.atZone(ZoneOffset.UTC);
    }
  }

  public boolean allowEvent() {
    if (limitPerSec <= 0) return true;
    long nowMs = Instant.now().toEpochMilli();
    long windowStart = nowMs - 1000;
    redis.opsForZSet().removeRangeByScore(KEY_RATE, Double.NEGATIVE_INFINITY, windowStart);
    Long count = redis.opsForZSet().zCard(KEY_RATE);
    if (count != null && count >= limitPerSec) return false;
    redis.opsForZSet().add(KEY_RATE, Long.toString(nowMs), nowMs);
    return true;
  }

  public void upsertActiveUser(String userId, ZonedDateTime eventTime) {
    long cutoff = epochMs(eventTime.minusMinutes(activeUserWinMin).toInstant());
    redis.opsForZSet().removeRangeByScore(KEY_ACTIVE_USERS, Double.NEGATIVE_INFINITY, cutoff);
    redis.opsForZSet().add(KEY_ACTIVE_USERS, userId, epochMs(eventTime.toInstant()));
  }

  public void upsertActiveSession(String userId, String sessionId, ZonedDateTime eventTime) {
    String key = sessionsKey(userId);
    long cutoff = epochMs(eventTime.minusMinutes(activeSessionWinMin).toInstant());
    redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff);
    redis.opsForZSet().add(key, sessionId, epochMs(eventTime.toInstant()));
    redis.expire(key, java.time.Duration.ofMinutes(activeSessionWinMin * 120));
  }

  public void incrPageview(String pageUrl, ZonedDateTime eventTime) {
    ZonedDateTime bucket = eventTime.truncatedTo(ChronoUnit.MINUTES);
    String key = pageviewKey(bucket);
    redis.opsForHash().increment(key, pageUrl, 1);
    redis.expire(key, java.time.Duration.ofMinutes(pageviewWinMin * 120L));
  }

  public int getActiveUsers() {
    long cutoff = epochMs(Instant.now().minus(activeUserWinMin, ChronoUnit.MINUTES));
    redis.opsForZSet().removeRangeByScore(KEY_ACTIVE_USERS, Double.NEGATIVE_INFINITY, cutoff);
    Long n = redis.opsForZSet().count(KEY_ACTIVE_USERS, cutoff, Double.POSITIVE_INFINITY);
    return n == null ? 0 : n.intValue();
  }

  public int getActiveSessionsForUser(String userId) {
    long cutoff = epochMs(Instant.now().minus(activeSessionWinMin, ChronoUnit.MINUTES));
    String key = sessionsKey(userId);
    redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff);
    Long n = redis.opsForZSet().count(key, cutoff, Double.POSITIVE_INFINITY);
    return n == null ? 0 : n.intValue();
  }

  public List<Map<String, Object>> getTopPages() {
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Map<String, Integer> agg = new HashMap<>();
    for (int i = 0; i < pageviewWinMin; i++) {
      ZonedDateTime b = now.minusMinutes(i).truncatedTo(ChronoUnit.MINUTES);
      String key = pageviewKey(b);
      Map<Object, Object> map = redis.opsForHash().entries(key);
      for (Map.Entry<Object, Object> e : map.entrySet()) {
        String url = e.getKey().toString();
        int cnt = Integer.parseInt(e.getValue().toString());
        agg.put(url, agg.getOrDefault(url, 0) + cnt);
      }
    }
    return agg.entrySet().stream()
      .sorted((a,b) -> Integer.compare(b.getValue(), a.getValue()))
      .limit(5)
      .map(en -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page_url", en.getKey());
        m.put("views", en.getValue());
        return m;
      })
      .collect(Collectors.toList());
  }

  public void processEvent(String timestamp, String userId, String eventType, String pageUrl, String sessionId) {
    ZonedDateTime zdt = parseTimestamp(timestamp);
    upsertActiveUser(userId, zdt);
    upsertActiveSession(userId, sessionId, zdt);
    if ("page_view".equalsIgnoreCase(eventType)) {
      incrPageview(pageUrl, zdt);
    }
  }
}
