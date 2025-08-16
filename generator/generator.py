import os, time, random, uuid, requests
from datetime import datetime, timezone

TARGET_URL = os.getenv("TARGET_URL", "http://localhost:8080/events")
RPS = float(os.getenv("RPS", "20"))
USERS = [u.strip() for u in os.getenv("USERS", "usr_101,usr_102,usr_103").split(",") if u.strip()]
PAGES = [p.strip() for p in os.getenv("PAGES", "/,/home,/products/electronics,/cart,/checkout").split(",") if p.strip()]
SESSION_VARIANCE = float(os.getenv("SESSION_VARIANCE", "0.3"))

print(f"[generator] Target={TARGET_URL} | RPS={RPS} | Users={len(USERS)} | Pages={len(PAGES)}")

current_sessions = {u: f"sess_{uuid.uuid4().hex[:8]}" for u in USERS}

def make_event(user_id: str):
    if random.random() < SESSION_VARIANCE:
        current_sessions[user_id] = f"sess_{uuid.uuid4().hex[:8]}"
    session_id = current_sessions[user_id]
    page_url = random.choice(PAGES)
    ts = datetime.now(timezone.utc).isoformat()
    return {
        "timestamp": ts,
        "user_id": user_id,
        "event_type": "page_view",
        "page_url": page_url,
        "session_id": session_id,
    }

def main():
    interval = 1.0 / RPS if RPS > 0 else 0.05
    while True:
        user_id = random.choice(USERS)
        ev = make_event(user_id)
        try:
            resp = requests.post(TARGET_URL, json=ev, timeout=3)
            if resp.status_code >= 400:
                print("[generator] Failed:", resp.status_code, resp.text[:200])
        except Exception as e:
            print("[generator] Error:", e)
        time.sleep(interval)

if __name__ == "__main__":
    main()
