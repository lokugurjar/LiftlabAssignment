# Realtime E-commerce Analytics

## Overview
This project implements a realtime analytics dashboard for e-commerce activity using:
- **Spring Boot** backend
- **Redis** for event aggregation and rate limiting
- **Docker Compose** for local development
- Optional **traffic generator** and a **spiky load testing script**

It demonstrates:
- Event ingestion via REST API (`/events`)
- Active user/session tracking
- Top pages analytics
- Sliding-window rate limiting at high traffic volumes
- A simple HTML dashboard to view metrics

---

## Architecture Overview

[Architecture Overview](arch.png)

**Flow (left → right):**
- **Dashboard UI** (browser) polls `/metrics/*` every 30s to render KPIs.
- **Load Testing Script** (`spiky_events_stats_latency.sh`) and **Traffic Generator** (Docker) push events to **`POST /events`**.
- **Spring Boot Backend** validates & rate-limits requests, then updates **Redis**:
    - ZSETs: active users/sessions (sliding windows)
    - HASH minute buckets: page views
- Metrics endpoints read aggregated counts back from Redis.

---

## Prerequisites
- Docker & Docker Compose
- (Optional for testing script) `bash`, `curl`, `python3`

---

## Run

```bash
docker compose up --build
```

- Dashboard: [http://localhost:8080](http://localhost:8080)

### Optional: Run traffic generator in Docker
```bash
docker compose --profile gen up --build generator
```

---

## API Endpoints

### 1. `POST /events`
Accepts an analytics event.

**Example Request**
```bash
curl -X POST http://localhost:8080/events   -H "Content-Type: application/json"   -d '{
    "timestamp": "2025-08-16T14:30:00Z",
    "user_id": "usr_789",
    "event_type": "page_view",
    "page_url": "/products/electronics",
    "session_id": "sess_456"
  }'
```

**Example Response**
```json
{
  "status": "accepted"
}
```

---

### 2. `GET /metrics/active_users`
Returns the number of unique active users in the last N minutes (default 5).

**Example**
```bash
curl -s http://localhost:8080/metrics/active_users
```
**Example Response**
```json
{
  "active_users": 12
}
```

---

### 3. `GET /metrics/top_pages`
Returns the most visited pages.

**Example**
```bash
curl -s http://localhost:8080/metrics/top_pages
```
**Example Response**
```json
{
  "top_pages": [
    { "page": "/products/electronics", "views": 45 },
    { "page": "/cart", "views": 30 }
  ]
}
```

---

### 4. `GET /metrics/active_sessions?user_id=<id>`
Returns active sessions for a specific user.

**Example**
```bash
curl -s "http://localhost:8080/metrics/active_sessions?user_id=usr_101"
```
**Example Response**
```json
{
  "active_sessions": [
    "sess_123",
    "sess_456"
  ]
}
```

---

## Local Load Testing

We provide a **spiky traffic generator** to simulate realistic user behavior with bursts and cooldowns.

### 1. Save the script
Save the `spiky_events_stats_latency.sh` file from `scripts/` folder (or create it) and make it executable:
```bash
chmod +x scripts/spiky_events_stats_latency.sh
```

### 2. Run the script
```bash
./scripts/spiky_events_stats_latency.sh
```

### 3. Example Output
```
[phase] base — 30 rps for 8s
[2025-08-16 22:15:01] phase=base     | OK: 30   | 429: 0    | Other: 0    | RPS Target: 30  | avg(ms):   12.4 | p95(ms):   18.2
[phase] SPIKE — 150 rps for 3s
[2025-08-16 22:15:09] phase=SPIKE    | OK: 100  | 429: 50   | Other: 0    | RPS Target: 150 | avg(ms):   19.7 | p95(ms):   31.2
[phase] cooldown — 10 rps for 4s
[2025-08-16 22:15:13] phase=cooldown | OK: 10   | 429: 0    | Other: 0    | RPS Target: 10  | avg(ms):   11.5 | p95(ms):   13.4
```

**Script features:**
- Sends bursts of traffic in three phases:
  - **Base** load
  - **Spike** (burst) load
  - **Cooldown** load
- Prints per-second:
  - OK count (HTTP 202)
  - Rate-limited count (HTTP 429)
  - Other errors
  - Average latency (ms) for OK responses
  - p95 latency (ms) for OK responses
- Adjustable parameters via environment variables:
  ```bash
  BASE_RPS=40 BASE_SEC=10 SPIKE_RPS=200 SPIKE_SEC=5 COOLDOWN_RPS=8 COOLDOWN_SEC=5 JITTER_PCT=15 ./scripts/spiky_events_stats_latency.sh
  ```

---

## Testing Steps Summary

1. **Start services:**
   ```bash
   docker compose up --build
   ```

2. **Send a single event:**
   ```bash
   curl -X POST http://localhost:8080/events      -H "Content-Type: application/json"      -d '{"timestamp":"2025-08-16T14:30:00Z","user_id":"usr_789","event_type":"page_view","page_url":"/products/electronics","session_id":"sess_456"}'
   ```

3. **Fetch metrics:**
   ```bash
   curl -s http://localhost:8080/metrics/active_users
   curl -s http://localhost:8080/metrics/top_pages
   curl -s "http://localhost:8080/metrics/active_sessions?user_id=usr_789"
   ```

4. **Run the spiky load generator** to simulate high-traffic scenarios and view rate limiting and latency behavior.

---

## Future Improvements

1. **Streaming ingestion** via Kafka/Pulsar to decouple producers and consumers; support replay & long-lived retention.
2. **Observability** with Prometheus metrics (JVM + app) and Grafana dashboards; structured JSON logging.
3. **Horizontal scaling** on Kubernetes (HPA for backend; Redis Sentinel/Cluster for HA & capacity).
4. **Security**: auth tokens/api-keys for `/events`, rate-limiting per API key/user/IP, TLS everywhere.
5. **Persistent analytics store** (PostgreSQL/ClickHouse) to materialize aggregates for long-term queries.
6. **Richer dashboard**: React/Next.js charts, filters by time range/user, and page details with timeseries.
7. **Resilience**: backpressure & circuit breaking, dead-letter handling for invalid events, retries with jitter.
8. **Data quality**: schema evolution checks, event versioning, and automated validation in CI.

---
