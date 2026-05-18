---
name: "cloud-killed-api-evaluation"
description: "How to evaluate whether a cloud IoT API is permanently dead vs temporarily broken, and what to check before writing a new driver."
domain: "protocol"
confidence: "high"
source: "earned — MyQ/Chamberlain feasibility research, 2026-05-18"
---

# Skill: Cloud-Killed API Evaluation Framework

Use this skill when a user asks about writing a Hubitat driver for a device whose cloud API has been revoked, broken, or rate-limited to death by the manufacturer.

---

## The Question You Must Answer First

> "Is the API closed by policy or broken by accident?"

| Signal | Broken by accident | Closed by policy |
|---|---|---|
| Company public statement | None | CTO/PR statement with reason |
| Developer page | Exists, possibly stale | 404 or "authorized partners only" |
| Community projects | Patching | Retired, forking to hardware alternatives |
| Time pattern | Intermittent breaks with repairs | Continuous escalation → final block |
| Partner program | Open/free | Commercial fee required |

**MyQ is the canonical "closed by policy" case** (October 2023, formal CTO statement, partner fee required, HA removed integration, homebridge-myq retired).

---

## Evaluation Checklist (Run in Order)

### 1. Official developer page
- `<brand>.com/developer`, `<brand>.com/api`, `<brand>.com/partners`
- HTTP 404 = dead. Active registration page = viable (check requirements).
- Look for partner program pricing — if it costs money, individuals cannot participate.

### 2. Home Assistant integration status
- `home-assistant.io/integrations/<name>`
- If the integration page says "removed" or "deprecated" with a hardware alternative → permanently dead.
- If it says "warning: may break" → temporarily broken, still live.

### 3. Community library commit recency
- Check `last commit` on the canonical Python library (e.g., `pymyq`, `python-ring-doorbell`).
- If the last commit is >12 months ago AND open issues report auth failures with no responses → likely dead.
- Check whether maintainers have redirected to a hardware alternative in the README.

### 4. Cat-and-mouse history pattern
- Search GitHub issues for repeated "broken again" reports with dates.
- If the breakage interval is shrinking (months → weeks → days) → company is actively fighting back. Dead end approaching.
- If intervals are stable (broken once, fixed once, stable for a year) → accidental, not policy.

### 5. Check for local hardware alternatives
When cloud is dead, the community almost always converges on local hardware:
- Garage doors: ratgdo / Konnected GDO blaQ (ESPHome)
- Locks: Nuki, August (some have local BLE/Z-Wave)
- Smart plugs: Shelly, Tasmota devices
- Cameras: ONVIF cameras with local RTSP

Hardware alternatives expose **documented local REST APIs** that are:
- Not subject to manufacturer API policy changes
- Not quota-limited
- No auth overhead (usually)
- Appropriate for HealthCheck Pattern A (local TCP)

---

## If the API is dead and no local hardware exists

Apply the `hubitat-iot-consumer-auth-dead-ends` skill:
1. Check if a consumer REST polling endpoint exists (not just SSE/WebSocket)
2. Check if auth requires a browser (CAPTCHA, OAuth PKCE redirect)
3. If yes to either → driver is not feasible on Hubitat without a sidecar service

---

## Reference Case: MyQ (Chamberlain/LiftMaster)

**Verdict as of 2026-05-18:** Cloud API permanently closed.

- CTO statement: October 25, 2023 — chamberlaingroup.com/press/…
- HA integration: Removed December 2023
- homebridge-myq: Officially retired (hjdhjd/homebridge-myq README)
- pymyq: Likely non-functional (arraylabs/pymyq)
- Developer page: HTTP 404
- Partner program: Commercial fee required
- **Community answer:** ratgdo ESPHome firmware (active, April 2026)
- **Local API:** ESPHome REST on port 80 — cover/binary_sensor/light domains

---

## ESPHome REST API Pattern (For Any ESPHome Device)

When a hardware alternative runs ESPHome firmware, the REST API is standardized and stable.

**Entity domains:** `sensor`, `binary_sensor`, `switch`, `light`, `cover`, `fan`, `select`

**Read state (GET):**
```
GET http://<ip>/<domain>/<entity_name>
→ {"id": "cover/Garage Door", "state": "OPEN", "value": 1.0, "current_operation": "IDLE"}
```

**Write/command (POST):**
```
POST http://<ip>/cover/Garage Door/open    → opens
POST http://<ip>/cover/Garage Door/close   → closes
POST http://<ip>/cover/Garage Door/stop    → stops
POST http://<ip>/switch/Dehumidifier/turn_on
POST http://<ip>/light/Main Light/turn_on?brightness=128
```

**Note on entity name format (as of ESPHome 2026.8.0):**
The `id` field in REST responses will switch to `domain/entity_name` format in ESPHome 2026.8.0, replacing legacy `domain-object_id` format. Any driver reading the `id` field should be aware of this migration.

**Not usable from Hubitat:** SSE stream at `/events` (streaming HTTP; Hubitat sandbox blocks persistent connections).

**Poll pattern:** `asynchttpGet` at 5-second intervals. HealthCheck Pattern A (full HealthCheck + ping() because it's local LAN).
