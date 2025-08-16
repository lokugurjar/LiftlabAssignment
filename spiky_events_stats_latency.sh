#!/bin/bash
set -u

# ========= Config =========
BACKEND_URL="${BACKEND_URL:-http://localhost:8080/events}"

BASE_RPS="${BASE_RPS:-30}"
BASE_SEC="${BASE_SEC:-8}"
SPIKE_RPS="${SPIKE_RPS:-150}"      # set > APP_RATE_LIMIT_PER_SEC to trigger 429s
SPIKE_SEC="${SPIKE_SEC:-3}"
COOLDOWN_RPS="${COOLDOWN_RPS:-10}"
COOLDOWN_SEC="${COOLDOWN_SEC:-4}"
JITTER_PCT="${JITTER_PCT:-10}"     # +/-% jitter each second

USERS=("usr_101" "usr_102" "usr_103" "usr_104" "usr_105" "usr_106" "usr_107")
PAGES=("/" "/home" "/products/electronics" "/products/fashion" "/products/books" "/cart" "/checkout")

# ========= Portable time helpers (macOS/BSD/Linux) =========
now_ms() {
  python3 - <<'PY'
import time
print(int(time.time()*1000))
PY
}

sleep_ms() {
  python3 - "$1" <<'PY'
import sys, time
ms = float(sys.argv[1])
time.sleep(ms/1000.0)
PY
}

# ========= Helpers =========
rand() { echo $((RANDOM)); }
pick_user() { echo "${USERS[$(( $(rand) % ${#USERS[@]} ))]}"; }
pick_page() { echo "${PAGES[$(( $(rand) % ${#PAGES[@]} ))]}"; }

send_event_bg() {
  local user="$1" page="$2" out_file="$3"
  local session="sess_$(( (RANDOM<<16) ^ RANDOM ))"
  local ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

  # Output format: "<code> <time_total>"
  curl -s -o /dev/null -w "%{http_code} %{time_total}\n" -X POST "$BACKEND_URL" \
    -H "Content-Type: application/json" \
    -d "{\"timestamp\":\"$ts\",\"user_id\":\"$user\",\"event_type\":\"page_view\",\"page_url\":\"$page\",\"session_id\":\"$session\"}" \
    >> "$out_file" &
}

calc_latency_stats() {
  # Reads file lines: "<code> <sec>"
  # Prints: avg_ms p95_ms for 202 only
  local f="$1"
  awk '
    $1=="202" {
      ms = $2 * 1000.0
      times[n++] = ms
      sum += ms
    }
    END {
      if (n==0) { printf("0 0\n"); exit }
      avg = sum / n
      # insertion sort (n small per second)
      for (i=0;i<n;i++) for (j=i+1;j<n;j++) if (times[j]<times[i]) { t=times[i]; times[i]=times[j]; times[j]=t }
      n95 = int((0.95*n)+0.999999) - 1; if (n95<0) n95=0; if (n95>=n) n95=n-1
      printf("%.1f %.1f\n", avg, times[n95])
    }
  ' "$f"
}

one_second_burst() {
  local target_rps="$1"
  local phase="$2"

  # Jitter
  if [ "$JITTER_PCT" -gt 0 ]; then
    local jitter=$(( ( ( $(rand) % (2*JITTER_PCT+1) ) - JITTER_PCT ) ))
    target_rps=$(( target_rps + (target_rps * jitter / 100) ))
    ((target_rps < 0)) && target_rps=0
  fi

  local out_file; out_file="$(mktemp)"

  local start_ms; start_ms="$(now_ms)"

  for ((i=1; i<=target_rps; i++)); do
    send_event_bg "$(pick_user)" "$(pick_page)" "$out_file"
  done
  wait

  # counts
  local ok_count limit_count other_count
  ok_count=$(awk '$1=="202"{c++} END{print c+0}' "$out_file")
  limit_count=$(awk '$1=="429"{c++} END{print c+0}' "$out_file")
  other_count=$(awk '$1!="202" && $1!="429"{c++} END{print c+0}' "$out_file")

  # latency for OK only
  read avg_ms p95_ms < <(calc_latency_stats "$out_file")
  rm -f "$out_file"

  local end_ms; end_ms="$(now_ms)"
  local elapsed=$((end_ms - start_ms))
  local sleep_rem=$((1000 - elapsed))
  (( sleep_rem > 0 )) && sleep_ms "$sleep_rem"

  local now_str; now_str=$(date +"%Y-%m-%d %H:%M:%S")
  printf "[%s] phase=%-8s | OK: %-4d | 429: %-4d | Other: %-4d | RPS Target: %-4d | avg(ms): %6.1f | p95(ms): %6.1f\n" \
    "$now_str" "$phase" "$ok_count" "$limit_count" "$other_count" "$target_rps" "$avg_ms" "$p95_ms"
}

run_phase() {
  local rps="$1" secs="$2" label="$3"
  echo "[phase] $label — ${rps} rps for ${secs}s"
  for ((s=1; s<=secs; s++)); do
    one_second_burst "$rps" "$label"
  done
}

echo "Spiky generator w/ stats+latency (portable) -> BASE ${BASE_RPS}rps/${BASE_SEC}s, SPIKE ${SPIKE_RPS}rps/${SPIKE_SEC}s, COOLDOWN ${COOLDOWN_RPS}rps/${COOLDOWN_SEC}s (jitter +/-${JITTER_PCT}%)"
echo "Target: $BACKEND_URL — Ctrl+C to stop."

while true; do
  run_phase "$BASE_RPS"     "$BASE_SEC"     "base"
  run_phase "$SPIKE_RPS"    "$SPIKE_SEC"    "SPIKE"
  run_phase "$COOLDOWN_RPS" "$COOLDOWN_SEC" "cooldown"
done
