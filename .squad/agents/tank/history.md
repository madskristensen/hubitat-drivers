# Tank — Driver Developer

**Status:** Shipped 3 community driver forks + 2 polish versions (2026-05-18 through 2026-05-19). T6 Pro v0.4.0 live on Mads's Downstairs thermostat. PurpleAir, Fully Kiosk, T6 Pro all permanent forks (PurpleAir has PR-draft staging ready). Stack complete; awaiting next hardware target.

## 2026-05-18 — 3 Trinity Audits Complete + Forks Shipped

**Audit verdicts merged to decisions.md:**
- **PurpleAir AQI (pfmiller0):** INSTALL 88/100; 3 bug fixes ready as PR-draft (AQ&U string, LRAPA/Woodsmoke case, failCount precedence)
- **Fully Kiosk (GvnCampbell):** FORK; 4 major fixes (password security, emitIfChanged, descriptionText, logger inversion bug)
- **T6 Pro (djdizzyd):** FORK; 3 critical fixes (txtEnable BLOCKER, fan-state nil-dereference, octal CMD_CLASS_VERS bug)

Mads's complete stack now has **zero open BUILD candidates**.

## 2026-05-18 — Shipped Forks

### PurpleAir AQI v0.1.0 (3 bug fixes)
- Fixed `apply_conversion()` "AQ and U" → "AQ&U" dead-code check
- Fixed `sensorCheck()` case mismatch: "lrapa"/"woodsmoke" → "LRAPA"/"Woodsmoke" (was fetching wrong PM2.5 field)
- Fixed `httpResponse()` failCount precedence: `(state.failCount ?: 0) + 1` (exponential backoff was broken)
- Status: TEMPORARY fork; delete once upstream merges PR

### Fully Kiosk v0.1.0 (4 critical fixes)
- Password security: `type:"string"` → `type:"password"` + URI masking in logs
- Event hygiene: `emitIfChanged()` on 4 attributes; prevents 5,760+ unnecessary events/day
- descriptionText: Added to parse(), motion(), acceleration() handlers
- Logger: Replaced backwards multi-level enum with standard `logEnable` bool
- Status: PERMANENT fork; GvnCampbell silent 4.5 years

### Fully Kiosk v0.2.0 (5 Trinity deferred items)
- descriptionText on checkInterval events (3 sites)
- logsOff auto-disable 30-min pattern (Gemstone standard)
- Security doc in README
- checkInterval 60→120 (2× polling cadence for health robustness)
- setLevel now async; emits only on HTTP 200

### Honeywell T6 Pro v0.1.0 (3 critical fixes) [djdizzyd fork]
- Added missing `txtEnable` preference (had been permanently false, breaking all info logs)
- Fixed `device.currentValue=="cooling"` → `device.currentValue("thermostatOperatingState")=="cooling"` in 2 locations (method reference never equals string; broke fan-state logic)
- Added `unschedule("syncClock")` to configure() (prevented scheduler zombie accumulation)
- Namespace: `djdizzyd` → `mads`

### Honeywell T6 Pro v0.3.0 (3 Cypher survey picks)
- Emit `thermostatFanState` attribute (data already parsed, never exposed)
- Battery-low notification (events 10/11 of type-8; was silently discarded)
- Fixed octal: `043:2` → `0x43:2` in CMD_CLASS_VERS (was reading 0x23 Scene Controller instead of 0x43 Thermostat Setpoint)
- Full namespace/author alignment; `Initialize` capability + lifecycle hooks; README restructure to repo template

### Honeywell T6 Pro v0.5.0 (syncClock UX)
- Replaced `runEvery3Hours("syncClock")` with `schedule("0 0 4 * * ?", "syncClock")` in `configure()`, `updated()`, and `initialize()` — 24× fewer Z-Wave frames
- Removed `command "syncClock"` declaration — dead UI button since auto-sync already handles all cases
- `void syncClock()` method body preserved; still called by `schedule()` and by `runIn(10,"syncClock")` in `configure()`
- Post-update timing subtlety: if user runs `updated()` at 4:30am, next sync is 23.5h later — "Configure" button is the documented escape hatch for immediate sync

### Honeywell T6 Pro v0.4.0 (3 additive Cypher picks) ✅ SHIPPED LIVE
- descriptionText on 3 event handlers (thermostatOperatingState, thermostatFanMode, thermostatMode)
- thermostatFanState type "string" → "enum" with 8 values
- Notification type 9 handler added (else if branch for System events; handles idle/hw-failure/sw-failure with log.warn)
- Running live on Mads's Downstairs thermostat

### PurpleAir v0.2.0 (5 Trinity deferred items + full alignment)
- emitIfChanged on 4 sendEvent calls (removes ~35,040 duplicate events/year)
- sites descriptionText with proper formatting
- Quota warning in polling preference
- UUID generation for HPM packageManifest
- lastActivity + touchActivity (Pattern B healthcheck)
- Namespace/author: `pfmiller0` → `mads`; attribution preserved
- logsOff auto-disable (logEnable bool)
- Null guards on PM2.5 float parses
- README rebuilt to repo template (What It Is / Capabilities / HPM / Architecture / Fixes / Attribution)

**Key learnings:**
- Fork-cleanup pattern: preserve copyright verbatim, add attribution block, apply audited fixes only with FIX comments citing severity, use Daikin/SunStat templates for packageManifest.json and README
- Groovy numeric literals: `043` is **octal** (= 0x23), not `0x43` — always use `0x` prefix for hex
- Z-Wave device config: fingerprint `inClusters` is authoritative; CMD_CLASS_VERS can have author assumptions — cross-check both sources
- UX pattern: emitIfChanged + descriptionText + lastActivity (Pattern B for cloud, Pattern A for LAN ping) is the canonical hygiene standard across the repo


## 2026-05-18 — Fully Kiosk v0.3.0 shipped (7 Cypher picks)

Closed the HA integration gap with all 7 Cypher-ranked v0.3.0 picks in a single additive pass. Pick #1 (brightness BUG FIX): `setLevel()` now converts 0–100 → `Math.round(level.toBigDecimal() * 2.55).toInteger()` clamped 0–255 before sending to FKB, and `refreshCallback()` reads back raw 0–255 and divides by 2.55 to emit the correct 0–100 SwitchLevel value — both with null guards. Pick #2: added 6 emitIfChanged attributes in refreshCallback from the existing deviceInfo payload — zero extra HTTP calls: `charging` (from `plugged`), `screensaverActive` (from `isInScreensaver`), `batteryTemperature` (null-guarded), `foregroundApp` (null-guarded), `screenOrientation` (mapped 0/1 → "portrait"/"landscape"), `kioskMode`. Pick #3: added `capability "Notification"` + `void deviceNotification(String)` + `setOverlayMessage(String)` both routing to `cmd=setOverlayMessage` with URL-encoded text. Pick #4: 8 utility commands (toBackground, clearCache, forceSleep, exitApp, lockKiosk, unlockKiosk, enableLockedMode, disableLockedMode) — all thin `sendCommandPost` one-liners. Pick #5: `playVideo(url)` and `stopVideo()`. Pick #6: `enableMotionDetection()` / `disableMotionDetection()` delegate to the existing `setBooleanSetting("motionDetection", true/false)`. Pick #7: all 4 `sendEvent([name:"checkInterval"...])` sites replaced with `emitIfChanged()` — value is always 120 so only the first emit per session fires. One discrepancy vs Cypher's spec: Cypher listed `motionDetectionEnabled` as a Pick #2 candidate but that field requires `listSettings` (not in `deviceInfo`), so `kioskMode` from `deviceInfo.kioskMode` was substituted as the 6th attribute (matching Cypher's own verdict table). Net LOC delta +85 (Cypher estimated ~116–136; difference because command stubs are 3 lines each and checkInterval fix was net-neutral).


## 2026-05-18 — Fully Kiosk v0.4.0 shipped (MQTT subscriber, opt-in)

Added ~195 LOC of MQTT support as an additive, preference-gated layer. Architecture decisions:

- **Opt-in gate:** all MQTT logic is gated on `settings.mqttBroker?.trim()` in `initialize()`. Empty preference = exact v0.3.0 behavior, zero regression risk.
- **parse() routing:** MQTT description strings start with `"mqtt"` in Hubitat; LAN HTTP push strings do not. Added a `description?.startsWith("mqtt")` check at the top of `parse()` to route to `parseMqttMessage()` before the existing LAN path.
- **Lifecycle:** `mqttConnect()` in `initialize()` when broker is set; `mqttDisconnect()` in `updated()` (before re-init) and `uninstalled()` (new method added). Disconnect-before-reconnect pattern ensures broker URL/credential changes take effect.
- **LWT topic:** `{prefix}/hubitat/state` with `"offline"` (retained, QoS 1). `"online"` published on successful connect.
- **Poll cadence:** `runEvery5Minutes("refresh")` when MQTT broker is configured (scheduled optimistically in `initialize()`); `mqttClientStatus()` confirms 5-min on connect, falls back to 1-min on disconnect.
- **Reconnect:** exponential backoff via `state.mqttRetryDelay`: 10 → 20 → 40 → 80 → 160 → 300 (capped). Reset on successful connect.
- **MQTT-pushed attributes:** `switch` (screenOn/screenOff), `motion` (motionDetected), `charging` (pluggedAC/unpluggedAC), `battery` (batteryLevel), `foregroundApp` — via `handleFkEvent()`. ALL v0.3.0 attributes additionally via `handleFkDeviceInfo()` from `{prefix}/deviceInfo/{deviceID}` payloads.
- **Poll-only fallback:** `level` (brightness), `currentPageUrl`, `screensaverActive`, `batteryTemperature`, `screenOrientation`, `kioskMode` also covered by `handleFkDeviceInfo()` from MQTT — no attributes are polling-only when MQTT is connected and FKB publishes `deviceInfo`.
- **FK deviceID caveat:** FKB's MQTT topic includes its own `deviceID` segment which may differ from Hubitat's MAC-based `deviceNetworkId`. Subscribed to `{prefix}/#` (wildcard) to avoid hardcoding the deviceID format. Documented: use a unique `mqttTopicPrefix` per tablet (e.g. `fully-bathroom`) when multiple tablets share the same broker.
- **Password masking:** broker URL masked via `replaceAll(/:\/\/[^:@\/]+:)[^@\/]+(@)/, '$1***$2')` before logging.
- **New skill:** extracted reusable pattern to `.squad/skills/hubitat-mqtt-subscriber-driver/SKILL.md`.
- **Scope discipline:** ONLY modified `drivers/fully-kiosk/fully-kiosk.groovy`, `drivers/fully-kiosk/packageManifest.json`, `drivers/fully-kiosk/README.md`, and this history file. No T6, no PurpleAir, no other files touched. If I ever find myself reading or considering edits to files outside `drivers/fully-kiosk/`, I will stop and re-read the scope warning.


