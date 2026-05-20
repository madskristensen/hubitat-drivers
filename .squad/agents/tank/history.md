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

## Archive: Tank-17, Tank-16, Tank-15 Detailed Spawns (2026-05-18)

### Tank-17: v0.4.5 mqtt.connect() signature fix

**Issue:** Mads reported live MQTT connection failure: `No signature of method: hubitat.helper.interfaces.Mqtt.connect() is applicable for argument types: (java.lang.String, GStringImpl, null, null, java.util.LinkedHashMap)`

**Root cause:** v0.4.0 called 5-arg form `interfaces.mqtt.connect(brokerUrl, clientId, username, password, optionsMap)` with Map for LWT; Hubitat does not support this.

**Fix:** Switched to 8-arg documented form: `interfaces.mqtt.connect(brokerUrl, clientId, username, password, lwtTopic, lwtQos, lwtRetained, lwtPayload)` with explicit LWT parameters. Behavior fully preserved.

**Scope:** fully-kiosk v0.4.5 (all 3 files: driver, manifest, README)

### Tank-16: v0.4.4 defensive leading-slash handling

**Issue:** FKB MQTT topics may have leading slash (FKB docs show `/fully/event/...` and `/fully/deviceInfo/...`); MQTT treats `/fully/` and `fully/` as separate address spaces.

**Fix:** Added second `interfaces.mqtt.subscribe("/${prefix}/#", 0)` subscribe call + leading-slash strip in parser: `if (topic.startsWith("/")) { topic = topic.substring(1) }`. Result: robust to either FKB convention.

**Scope:** fully-kiosk v0.4.4 (all 3 files)

### Tank-15: v0.4.3 MQTT event-name mismatches

**Issue:** Cypher's reality check found 4 event-name mismatches in `handleFkEvent()`:
- driver listening for `"motionDetected"` → FKB publishes `"onMotion"`
- driver listening for `"unpluggedAC"` → FKB publishes `"unplugged"`
- driver listening for `"batteryLevel"` → FKB publishes `"onBatteryLevelChanged"`
- driver listening for `"foregroundApp"` → FKB does not publish as event (attribute from deviceInfo only)

**Fix:** Renamed case statements + removed dead `foregroundApp` case. Cypher confirmed `"pluggedAC"` is correct; no rename needed.

**Scope:** fully-kiosk v0.4.3 (all 3 files)



---

## Learnings

- MQTT attempt failed across 4 iterations — Hubitat MQTT client API signatures are fragile, broker compatibility is variable. The polling architecture is the reliable path.
- Removing MQTT was the right call once it became clear the broker wasn't accepting the handshake.
- `parseJson()` must never run on blank preference text or empty async HTTP bodies in Hubitat drivers; guard `?.trim()` first, wrap JSON parsing in `try/catch`, and early-return with `log.warn` instead of crashing `refresh()`.
- Hubitat custom numeric attributes accept the UTF-8 unit string `µg/m³` cleanly; PurpleAir raw PM2.5 can emit as `pm2_5` with that exact unit.
- PurpleAir v0.3.0 can add `TemperatureMeasurement` + `RelativeHumidityMeasurement` without conflicting with the existing custom `aqi`/`category` attributes.

- Gemstone v0.4.17 stale-token fix: add `ensureSession()` before cached-state dedup so commands only early-return when the Cognito session is healthy; edits landed in `drivers/gemstone-lights/gemstone-lights.groovy` lines 206–355 (switch/level/color handlers), 802–826 (`ensureSession()` + queue gate), and 1044–1063 (shared effect activation dedup path).
- Dedup/auth lesson: in Hubitat cloud drivers, a cache-hit return must never sit above the auth/queue path, or an expired token turns user commands into silent no-ops until a non-deduped path like `refresh()` repairs the session.

## Session Arc 2026-05-19: PST02 Performance Audit (Tank #23)

**Task:** Performance audit of `drivers/philio-pst02/philio-pst02.groovy` v1.1.0 → v1.2.0.

**Changes shipped:**
- **Implicit globals eliminated:** `resync`, `refresh`, and `value` in `deviceSync()` were bare assignments (no `def`/type), creating script-level Binding entries that persist across method calls. Declared `boolean resync`, `boolean refresh`, `Integer value`, `List cmds`.
- **Implicit globals in SensorMultilevelReport:** `value` in switch cases 5 and 3 was also undeclared — declared `BigDecimal sensorValue`, `int precision`, `String unit` before the switch.
- **Typed return types:** `def setBit` → `Integer`, `def isPst02BVariant` → `boolean`, `def resolveConfigParam*` → `Integer`. Groovy can skip dynamic-dispatch overhead when return type is declared.
- **`isPst02BVariant()` called twice per resolve fn:** Cached in local `boolean isB` at top of each resolve function — avoids redundant settings/`getDataValue` reads.
- **Redundant Z-Wave roundtrip removed:** The resync block sent `configurationGet(12)` unconditionally, but the diff-check block above it already sends `configurationGet(12)` when resync=true (because `resync ||` is true). Removed the duplicate — saves one battery-expensive Z-Wave roundtrip per full resync.
- **Map allocations reduced:** `BatteryReport` and `clearTamper` use inline `sendEvent(name:..., value:..., ...)` — eliminates one `HashMap` allocation per event.
- **`Map map = [:]` typing:** All event handlers type the map variable explicitly.
- **Redundant `.toString()` in log.trace:** All `${cmd.toString()}` → `${cmd}` (Groovy calls `toString()` implicitly in GString).
- **`WakeUpIntervalReport` local:** Compute `int minutes` locally instead of `cmd.seconds / 60` inline in `state` write — avoids reading state back for the log line and fixes the "wakup" typo.

## Learnings

- In Hubitat Groovy drivers, any variable assigned without `def` or a type in a `def` method body becomes a script-level `Binding` entry, not a stack-local. This is not sandbox-blocked but wastes a map-lookup on every read/write and can hold stale values across calls.
- `isPst02BVariant()` is called from 3 resolve functions each of which may run on every wakeup. Cache it in a local `boolean isB` at the top of each function — one read instead of two.
- On battery-powered Z-Wave devices, every `configurationGet` costs battery life; never duplicate one in a "resync info reads" block if the diff-check block above it already emits the same get unconditionally when `resync == true`.
- Groovy typed return types (`Integer`, `boolean`, `List`) on frequently-called helper functions reduce dynamic dispatch overhead in the Hubitat sandbox JVM.


**Tank #20:** Fully Kiosk v0.5.0 shipped with MQTT-to-REST wording clarification. Changelog flattened to single-line format (release.yml regex requirement). Commit: 61644e4.

**Tank #21:** PurpleAir v0.3.0 shipped with parseJson guard for blank search_coords, async response guards, and new attributes (pm2_5, temperature, humidity, confidence). Commit: fd212cc.

**Tank #22:** PurpleAir v0.4.0 shipped with all 5 Trinity production bugs fixed (String-math retry, disabled-poll guard, distance2degrees pole clamp, zero-distance protection, divide-by-zero guards), plus polish: AirQuality capability, airQualityIndex attribute, runEvery* scheduling, hub temp-scale conversion, canonical async error handling, refresh-on-save, cleaner sites output, stable AQI units, 60-second lastActivity throttling. Changelog flattened. Commit: 2d62b05.

**Key Learning:** Changelog single-line format is now enforced across all drivers (required by release.yml line 136 regex).

**Deliverables:** Orchestration logs created (.squad/orchestration-log/2026-05-19-043500Z-tank-*.md)

