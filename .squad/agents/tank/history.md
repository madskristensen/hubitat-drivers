## 2026-05-20 — Away Lights v0.8.1 Resource Cleanup (revised — aggressive)

**Task:** Implement resource cleanup enhancements; Mads clarified backcompat is not a priority pre-v1.0.0 — make breaking changes if needed.

**Changes made to `apps/away-lights/away-lights.groovy`:**

- **Enhancement 1 (unconditional):** `unschedule("offTimeHandler")` now fires on ANY Away-mode exit, not just when `turnOffOnHome=true`. Previously `turnOffOnHome=false` left the handler scheduled to fire and no-op at end-time — pure waste.
- **Enhancement 2 (structural fix):** Changed `else if (turnOffOnHome)` → `else` in `modeHandler`. All scheduled task cleanup (`checkAndTurnOn`, `doLightsOn`, `offTimeHandler`) and state reset now run unconditionally on Away exit. Only `lightsOff()` remains gated on `turnOffOnHome`.
- **Dropped no-op:** The `unsubscribe("modeHandler")` + `subscribe(location, "mode", modeHandler)` from the first attempt was a net no-op and removed. The mode subscription correctly stays permanent — it must be active at all times to detect Away re-entry.

**Breaking behavior change:** When `turnOffOnHome=false`, the app now always cancels pending scheduled tasks on Away exit. Previously those tasks would linger, fire at their scheduled time, and no-op. No user-visible behavior difference (lights were already staying on in both paths) — the break is CPU/memory only.

**Architecture note:** The `subscribe(location, "mode", modeHandler)` in `initialize()` must remain permanent. You cannot subscribe "only during Away mode" for the subscription that detects Away entry — it's circular. Any future value-filtered subscription support in Hubitat would allow a true implementation.

---

## 2026-05-20 — Away Lights App v0.1.0

**Task:** Convert Mads's webCoRE "Away Lights" piston into a native Hubitat app.

**Shipped:** `apps/away-lights/` — full 3-file package (away-lights.groovy, packageManifest.json, README.md). Root packageManifest.json and README.md updated.

**What it does:** Subscribes to location mode changes and two daily schedules. On Away entry, debounces for a configurable minutes before checking whether the current time falls inside [onTime, offTime). If both checks pass, turns lights on and optionally sends a push notification. At offTime it turns them off. Optional `turnOffOnHome` extinguishes the lights immediately on mode return.

**Key decisions:**
- Used `schedule(onTime, "onTimeHandler")` and `schedule(offTime, "offTimeHandler")` with Hubitat's time-preference daily-schedule form (no manual cron math needed).
- `awayDebounceMinutes` coerced to `Integer` before multiplication to avoid Groovy string-repeat bug (same lesson from PurpleAir).
- `turnOffOnHome` calls `lightsOff()` unconditionally (no `?.any {}` scan) — cheaper than a full state-scan closure; `off()` to an already-off device is a no-op at the device level.
- `lightsOn()`/`lightsOff()` use `for (def x in list)` loops instead of `.each {}` closures to avoid closure object allocation per call.
- `unschedule("doLightsOn")` added in the `turnOffOnHome` branch to cancel any pending jitter `runIn`; `doLightsOn` also guards with mode check.
- "Already in window + sunset" confirmed correct: `isInWindow()` calls `getSunriseAndSunset().sunset` and checks `now >= on`, so Away activating after sunset correctly returns true.

---

## 2026-05-18 — PurpleAir AQI v0.4.0 quality release

**Task:** Ship Trinity's 4 production bug fixes + top polish set + release-hygiene preempt in one PurpleAir v0.4.0 release.

**Shipped:**
- Fixed Groovy string-multiplication in retry math by coercing `update_interval` once before backoff calculations; guarded disabled polling so `update_interval == "0"` never schedules `runIn(0, ...)`.
- Corrected `distance2degrees()` latitude/longitude math with a pole clamp and added a zero-distance short-circuit in `sensorAverageWeighted()` so exact sensor-coordinate matches cannot emit `NaN`.
- Added canonical async HTTP error handling, refresh-on-save, `AirQuality`/`airQualityIndex`, `runEvery*` scheduling, hub temperature-scale conversion, cleaner `sites`/AQI units, and a 60-second `lastActivity` throttle.
- Flattened every PurpleAir driver-header changelog entry to one physical line so `.github/workflows/release.yml` can parse v0.4.0 release notes.

**Learnings:**
- Hubitat enum preferences stay as strings; any `settings.foo * 5` math must coerce first or Groovy repeats the string (`"60" * 5` → `"6060606060"`).
- Latitude degrees are roughly constant while longitude degrees shrink by `cos(latitude)`; swapping them silently distorts geofence boxes.
- `updated()` should refresh cloud-poll drivers immediately after re-scheduling so users see fresh data on save instead of waiting for the next interval.

---

## 2026-05-18 — Spawn tank-18 — Fully Kiosk v0.4.6

**Task:** v0.4.6 — Drop to 4-arg `interfaces.mqtt.connect()`, remove LWT locals

**Source:** Mads Kristensen — live bug: Hubitat throwing
`"No signature of method: hubitat.helper.interfaces.Mqtt.connect() is applicable for argument types:
(java.lang.String, GStringImpl, null, null, String, Integer, Boolean, String)"`.
Tank-17's 8-arg form was ALSO rejected — Hubitat's built-in interface supports the 4-arg form only.

**Changes made (drivers/fully-kiosk/ only):**

#### fully-kiosk.groovy

- **Header (lines 12–23):** Added `Version: 0.4.6 — 2026-05-18` changelog entry above 0.4.5
- **VERSION field (line 87):** `"0.4.5"` → `"0.4.6"`
- **`mqttConnect()`:** Removed 4 now-unused LWT locals (`lwtTopic`, `lwtPayload`, `lwtQos`, `lwtRetained`). Replaced 8-arg `interfaces.mqtt.connect(...)` with 4-arg form `(brokerUrl, clientId, username ?: null, password ?: null)`.
- **`mqttClientStatus()` success branch (line 854):** `interfaces.mqtt.publish("${prefix}/hubitat/state", "online", 1, true)` — **untouched**. Manual online-state publish preserved.

#### packageManifest.json

- Both `"version"` fields → `"0.4.6"`

#### README.md

- New `0.4.6` row inserted above `0.4.5` in the changelog table
- LWT limitation note added to MQTT setup section: no automatic offline publish; subscribers should treat stale state as "unknown"

**Scope compliance:** Only `drivers/fully-kiosk/` and `.squad/agents/tank/history.md` (full path) modified. ✅

---

## 2026-05-19 — Cypher flagged 3 Touchstone retry-logic improvements

**Context:** Cypher completed live troubleshooting of Mads's Touchstone fireplace outage (likely localKey rotation). Identified 3 driver defects in the retry loop that contributed to indefinite retry behavior without surfacing a clear "device unreachable" signal.

**Flagged improvements (prioritized by Tank):**

1. **retryIndex reset on heartbeat ACKs (line 869):** Reset fires on every frame including Tuya heartbeats (cmd 9) that have no AES payload. Causes infinite oscillation: 5s → 15s → (heartbeat resets to 0) → 5s → 15s... Suggested fix: only reset retryIndex when frame carries actual DP data.

2. **No retry cap:** `scheduleRetry()` retries every 30s indefinitely after permanent failures (wrong key, device removed). Suggested fix: add `state.retryCount` counter; after threshold (e.g., 10 consecutive), stop retrying, call `updateOnlineStatus("offline", "...")`, and log at `log.error` level so Mads sees clear persistent signal.

3. **No socket recycle after timeouts:** TCP half-open state never cleared after prolonged responseTimeouts. Suggested fix (optional, lower priority): after retry cap threshold, close and reopen socket before giving up.

**Decision record:** `.squad/decisions.md` 2026-05-19 entry "Driver Improvement: Retry Cap + retryIndex Reset Scope (Touchstone Fireplace)".

---

# Tank — Driver Developer

**Status:** Shipped 3 community driver forks + 2 polish versions (2026-05-18 through 2026-05-19). T6 Pro v0.4.0 live on Mads's Downstairs thermostat. PurpleAir, Fully Kiosk, T6 Pro all permanent forks (PurpleAir has PR-draft staging ready). Stack complete; awaiting next hardware target.

---

## 2026-05-18 — 3 Trinity Audits Complete + Forks Shipped

**Audit verdicts merged to decisions.md:**
- **PurpleAir AQI (pfmiller0):** INSTALL 88/100; 3 bug fixes ready as PR-draft (AQ&U string, LRAPA/Woodsmoke case, failCount precedence)
- **Fully Kiosk (GvnCampbell):** FORK; 4 major fixes (password security, emitIfChanged, descriptionText, logger inversion bug)
- **T6 Pro (djdizzyd):** FORK; 3 critical fixes (txtEnable BLOCKER, fan-state nil-dereference, octal CMD_CLASS_VERS bug)

Mads's complete stack now has **zero open BUILD candidates**.

### Fully Kiosk v0.1.0–v0.4.5 Summary (rapid iteration)

- **v0.1.0:** 4 critical fixes (password security, emitIfChanged, descriptionText, logger inversion bug)
- **v0.2.0:** 5 Trinity deferred items (descriptionText, logsOff auto-disable, security doc, checkInterval, setLevel async)
- **v0.3.0:** 7 Cypher HA integration picks (setLevel brightness fix, 6 emitIfChanged attributes, Notification + overlay, 8 utility commands, video + motion controls, checkInterval emitIfChanged)
- **v0.4.1:** 3 live tablet bugs (beep() NPE guard, 408/5xx demote to warn, setScreenBrightness removed — BREAKING)
- **v0.4.2:** Added clearOverlayMessage() command
- **v0.4.3:** 4 Cypher event-name mismatches fixed (motionDetected→onMotion, unpluggedAC→unplugged, batteryLevel→onBatteryLevelChanged, foregroundApp removed)
- **v0.4.4:** Defensive leading-slash handling (dual MQTT subscriptions + topic normalization)
- **v0.4.5:** Fixed invalid `mqtt.connect()` signature (was 5-arg with Map, switched to 8-arg with explicit LWT params)

### PurpleAir v0.1.0–v0.2.0 Summary

- **v0.1.0:** 3 critical fixes (AQ&U dead-code check, LRAPA/Woodsmoke case mismatch, failCount precedence for backoff)
- **v0.2.0:** 5 Trinity + full alignment (emitIfChanged, descriptionText, quota warning, UUID for HPM, lastActivity healthcheck, namespace alignment, logsOff auto-disable, PM2.5 null guards, README rebuild)

### Honeywell T6 Pro v0.1.0–v0.5.0 Summary + v0.4.0 LIVE

- **v0.1.0:** 3 critical fixes (txtEnable, device.currentValue fix, unschedule syncClock, namespace alignment)
- **v0.3.0:** 3 Cypher survey picks (thermostatFanState emit, battery-low notification, octal/hex fix 043→0x43)
- **v0.5.0:** syncClock UX optimization (24× fewer Z-Wave frames)
- **v0.4.0:** 3 additive Cypher picks ✅ SHIPPED LIVE (descriptionText, thermostatFanState enum, notification type 9)

**Key learnings:**
- Fork-cleanup pattern: preserve copyright verbatim, add attribution block, apply audited fixes only with FIX comments citing severity, use Daikin/SunStat templates
- Groovy numeric literals: `043` is **octal** (= 0x23), not `0x43` — always use `0x` prefix for hex
- Z-Wave device config: fingerprint `inClusters` is authoritative; CMD_CLASS_VERS can have author assumptions — cross-check both sources
- UX pattern: emitIfChanged + descriptionText + lastActivity (Pattern B for cloud, Pattern A for LAN ping) is the canonical hygiene standard
- **Scope discipline pattern (Tank-13–16):** Explicit 3-paragraph warnings work; 100% compliance when scope violations are named and emphasized

---

## 2026-05-18 — Fully Kiosk MQTT Architecture (v0.4.0)

Added ~195 LOC of MQTT support as an additive, preference-gated layer:

- **Opt-in gate:** all MQTT logic gated on `settings.mqttBroker?.trim()` in `initialize()`; empty = exact v0.3.0 behavior
- **parse() routing:** `description?.startsWith("mqtt")` routes to `parseMqttMessage()` before LAN path
- **Lifecycle:** mqttConnect() on initialize, mqttDisconnect() on updated/uninstalled
- **LWT topic:** `{prefix}/hubitat/state` with "offline" (retained, QoS 1) on disconnect, "online" on connect-success
- **Poll cadence:** `runEvery5Minutes()` when MQTT configured; 1-min fallback on disconnect
- **Reconnect:** exponential backoff (10→20→40→80→160→300s) with reset on success
- **MQTT-pushed:** switch, motion, charging, battery, foregroundApp via handleFkEvent(); all v0.3.0 attributes additionally via handleFkDeviceInfo()
- **Poll-only fallback:** level, currentPageUrl, screensaverActive, batteryTemperature, screenOrientation, kioskMode from deviceInfo
- **FK deviceID caveat:** subscribed to `{prefix}/#` wildcard to avoid hardcoding deviceID format
- **Password masking:** broker URL masked in logs before output
- **New skill:** extracted reusable pattern to `.squad/skills/hubitat-mqtt-subscriber-driver/SKILL.md`

---

## 2026-05-19 — Post-Ship Housekeeping (Scribe-14)

**Tank-17 minor bug detected & fixed:** Tank-17's history entry was written to `tank/history.md` at repo root instead of canonical `.squad/agents/tank/history.md` (likely CWD-relative path bug in Tank's MultiEdit tool under certain conditions). Scribe relocated the entry with separator, deleted stray directory, and updated decisions.md with v0.4.5 shipment note.

---

