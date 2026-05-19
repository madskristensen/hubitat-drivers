# Tank — Driver Developer

**Status:** v0.2.0 polish complete on 3 community driver forks (2026-05-18). All Trinity deferred-backlog items applied. Awaiting Mads's hardware pick for next build target.

---
**2026-05-18 Team Update (Scribe):** Bosch Home Connect verdict = install craigde/hubitat-homeconnect-v3 community driver. OAuth Authorization Code Grant works in Hubitat via parent App cloud callback URI — pattern unblocks future cloud-OAuth drivers.
## Team Update — PurpleAir Cloud Driver INSTALL Verdict (2026-05-18)

**From:** Cypher audit (2026-05-18T23:06:44-07:00) → Merged into canonical decisions ledger (scribe)

**Verdict:** INSTALL (88/100 Trinity rubric score).

pfmiller0's `PurpleAir AQI Virtual Sensor.groovy` is the cloud-API driver that prior search missed. It targets `api.purpleair.com/v1/sensors` (not local `http://<ip>/json`), requires no hardware, accepts the user's API key via `X-API-Key` header, supports geolocation-based multi-sensor averaging OR a specific sensor index, and implements full EPA Barkjohn 2021 AQI correction for wildfire smoke.

**Scoring:**
- Local vs. cloud: 10/10 (cloud REST, stable)
- Mads can test: 15/15 (no hardware needed; any public sensor or hub location)
- User demand: 15/15 (PNW wildfire season, neighbor sensor use case)
- Sandbox-safe: 15/15 (asynchttpGet only, no crypto/reflection)
- Vendor stability: 15/15 (API key monetized, documented, stable)
- Effort: 10/10 (zero — copy via importUrl or HPM)
- Maintenance: 8/10 (last commit 2025-06-18; responsive but not 2026-active)
- **Total: 88/100** → INSTALL

**Impact:** This is the 5th consecutive "install community driver" verdict in a row. **Mads's complete stack now has zero open BUILD candidates.**

**Audit lesson:** Prior search ("PurpleAir Hubitat driver") returned zero. Follow repo search with code search (`code:"api.purpleair.com" lang:groovy`) before claiming greenfield. User-pointed URLs override search conclusions.

---

## Team Update — 3 Community Driver Code-Quality Audits (2026-05-18)

**From:** Trinity audit + Scribe merge (2026-05-18T23:45:00Z)

**Verdicts:**
- **pfmiller0 PurpleAir AQI:** 3 bugs, PRs upstream (maintainer active June 2025; conversion string BLOCKERs + failCount backoff MAJOR)
- **GvnCampbell Fully Kiosk:** FORK to `drivers/fully-kiosk/` (silent 4.5y; password leaks in debug logs, no emitIfChanged in poll = 5760+ events/day)
- **djdizzyd Honeywell T6 Pro:** FORK to `drivers/honeywell-t6-pro/` (silent 4y; BLOCKER txtEnable never declared + currentValue() missing attribute argument breaks fan-state detection — affects Mads's installed thermostats today)

**Tank's next actions:** (1) PRs upstream to pfmiller for conversion + backoff bugs (~60–90 line diff, low review friction), (2) fork to `drivers/honeywell-t6-pro/` — ~15 line fix, BLOCKER affecting installed devices, (3) fork to `drivers/fully-kiosk/` — rewrite logger + hygiene + password type, 2 instances in production. Audit files in decisions.md with exact line citations.

---

## Tank Work — PurpleAir AQI Fork + PR Draft (2026-05-18T17:15:27-07:00)

**Task:** Apply Trinity's 3 audit fixes to pfmiller0's PurpleAir AQI Virtual Sensor, place in repo as a local-test fork, and produce a PR-ready draft.

**Files created:** `drivers/purpleair-aqi/` (4 files)
- `purpleair-aqi.groovy` — patched driver (upstream v1.3.2 + 3 fixes)
- `packageManifest.json` — HPM manifest v0.1.0
- `README.md` — install steps, what's fixed, upstream PR plan, attribution
- `UPSTREAM-PR-DRAFT.md` — ready-to-paste GitHub PR description with before/after code snippets and test instructions per fix

**3 fixes applied (minimal diff — PR-ready):**
1. `apply_conversion()`: `"AQ and U"` → `"AQ&U"` — AQ&U was dead code
2. `sensorCheck()`: `"lrapa"` → `"LRAPA"`, `"woodsmoke"` → `"Woodsmoke"` — case mismatch caused wrong PM2.5 field (`pm2.5` instead of `pm2.5_cf_1`)
3. `httpResponse()`: `state.failCount?:0 + 1` → `(state.failCount ?: 0) + 1` — precedence bug; failCount never incremented

**No git commit.** Coordinator handles git after all 3 Tank instances finish.
**Fork policy:** TEMPORARY — delete once pfmiller0 merges the upstream PR.

---

## Tank Work — Fully Kiosk Browser Controller Fork (2026-05-18T17:15:27-07:00)

**Task:** Apply Trinity's 4 audit fixes to GvnCampbell's Fully Kiosk Browser Controller (v1.41), place in repo as permanent fork.

**Source:** `https://raw.githubusercontent.com/GvnCampbell/Hubitat/master/Drivers/FullyKioskBrowserController.groovy` (last commit 2021-11-20, confirmed 4.5+ years silent via `gh api`).

**Files created:** `drivers/fully-kiosk/` (3 files)
- `fully-kiosk.groovy` — fork with 4 Trinity audit fixes
- `packageManifest.json` — HPM manifest v0.1.0
- `README.md` — install steps, what's fixed, upstream status, attribution

**4 fixes applied:**
1. **Security (lines 109, 438–441, 545–549):** `serverPassword` pref changed from `type:"string"` to `type:"password"`; password masked in debug logs via `.replaceAll(/(?i)password=[^&]+/, 'password=***')` on the URI before logging in both `sendCommandPost()` and `refresh()`
2. **Event hygiene (lines 449–461):** `refreshCallback()` 4 x `sendEvent` replaced with `emitIfChanged()` calls; helper added at line 567 — prevents 5,760+ unchanged events/day at 1-min poll cadence
3. **descriptionText (lines 197–247):** All `sendEvent` calls in `parse()`, `motion()`, and `acceleration()` now include `descriptionText: "${device.displayName} ${attribute} is ${value}"`
4. **Logger (lines 124–125, 586–595):** Replaced inverted multi-level `loggingLevel` enum (where `debug` > `trace` verbosity was backwards) with standard Hubitat `logEnable` bool; all trace/debug output gated by `logEnable`; info/warn/error always emit

**Security note:** Password was in query string `uri` field (not `body`), so adapted Trinity's mask pattern to `safeParams.uri = safeParams.uri?.replaceAll(...)` rather than `safeParams.body`.

**No git commit.** Coordinator handles git after all 3 Tank instances finish.

---

## Tank Work — Fully Kiosk Browser Controller v0.2.0 Polish (2026-05-18T17:36:00-07:00)

**Task:** Apply Trinity's 5 deferred backlog items (C1–C5) from trinity-post-fork-code-review.md. v0.2.0 bump.

**UUID generated:** `ead8e688-5b88-48b7-97f9-16b0d921bd2f` — replaces placeholder `a1b2c3d4-...` in packageManifest.json.

**Changes applied:**
- **C1 (descriptionText on checkInterval):** 3 sites updated — `parse()` default case, `updateDeviceDataCallback()`, `sendCommandCallback()` — all now include `descriptionText: "${device.displayName} checkInterval is 120"`.
- **C2 (logsOff auto-disable):** `logEnable` defaultValue flipped `true` → `false`. `logsOff()` method added (exact Gemstone pattern). Wired into `installed()` and `updated()` via `if (logEnable) runIn(1800, "logsOff")`.
- **C3 (Security note in README):** New "Security note" section added between "What's fixed" and "Upstream status" — explains FKB protocol requires password as URL query param; that's FKB's API design; documents mitigations (trusted LAN, dedicated password, `type:"password"` pref, log masking).
- **C4 (checkInterval 60→120):** All 3 sendEvent sites changed from `value:60` to `value:120`. Rationale: 2× polling cadence — single missed poll no longer marks device offline.
- **C5 (setLevel event to callback):** `setLevel()` no longer calls `setScreenBrightness()` + immediately fires `sendEvent`. Instead, directly calls `asynchttpPost("setLevelCallback", ...)` with `[level: level]` as data. New `setLevelCallback()` fires the `level` event only on HTTP 200. Security: URI logs in `setLevel()` use the same `replaceAll(/(?i)password=[^&]+/, 'password=***')` masking as `sendCommandPost()`.
- **setLevelCallback** also emits `checkInterval: 120` with descriptionText (consistent with other HTTP success callbacks).

**Pre-existing items left alone:** `setScreenBrightness()` still exists as standalone command (correct — used directly from dashboards). `updated()` calling `initialize()` pattern preserved (no `unschedule()` before `initialize()` needed as `initialize()` already calls `unschedule()`).

**No git commit.** Coordinator handles git.

---

## Learnings - Honeywell T6 Pro Fork (2026-05-18T17:15:27-07:00)

Forked djdizzyd's Advanced Honeywell T6 Pro Thermostat into drivers/honeywell-t6-pro/. Fix surface was 3 targeted changes: (1) added missing txtEnable preference declaration (BLOCKER - had been permanently false/silent for 4+ years), (2) fixed device.currentValue=="cooling" to device.currentValue("thermostatOperatingState")=="cooling" in two locations - zwaveEvent(ThermostatFanStateReport) line 533 and zwaveEvent(BasicSet) lines 556-558 - (MAJOR - method reference never equals a string, silently broke fan-state correction logic), (3) added unschedule("syncClock") at top of configure() to prevent zombie scheduler accumulation on repeated manual configure invocations (MAJOR).

**Reusable fork-cleanup pattern:** See .squad/skills/hubitat-fork-cleanup-pattern/ for the general approach used here - preserve original copyright verbatim, add fork attribution block above it, apply only audited fixes with inline FIX comments citing severity, create packageManifest.json using Daikin as template, write README with What's Fixed table.

---

## Honeywell T6 Pro v0.3.0 (2026-05-18T18:05:00-07:00)

Applied exactly 3 changes per Cypher's survey: (1) Pick #1 — Added `attribute "thermostatFanState", "string"` to `definition()` block (line 57); wired `eventProcess(name:"thermostatFanState", ...)` in `zwaveEvent(ThermostatFanStateReport)` (line 571) — `thermostatFanState` is not exposed by any existing capability so explicit `attribute` declaration was needed. (2) Pick #2 — cases 10 and 11 in `zwaveEvent(NotificationReport)` type-8 branch (lines 234–243) now emit `log.warn` + idempotency-gated `sendEvent` for `battery` at 10% / 1% — does not double-fire with `BatteryReport`'s own `eventProcess()`-based path. (3) Pick #3 — `043:2` → `0x43:2` in `CMD_CLASS_VERS` (line 77), fixing Groovy octal parse that was registering Scene Controller Config (0x23) instead of Thermostat Setpoint (0x43). No other entries in the map had leading-zero octal issues. v0.4.0 candidate noted: `zwaveEvent(ThermostatFanStateReport)` emits `thermostatFanState` but does not log `if (txtEnable)` around the descriptionText — minor inconsistency with temperature/humidity handlers added in v0.2.0.

---



Applied all 5 Trinity deferred-backlog items (C1–C5) plus namespace/author alignment and style cleanup. C1: added `descriptionText` to temperature and humidity `eventProcess()` calls in `zwaveEvent(SensorMultilevelReport)`, including `if (txtEnable) log.info` for consistency with battery events. C2: replaced string equality in `eventProcess()` with BigDecimal compare (matching the canonical `emitIfChanged` pattern from sunstat-thermostat-child) — prevents `68` vs `68.0` false-positive events from Z-Wave float responses. C3: removed dead `sendToDevice(zwave.configurationV1.configurationGet(parameterNumber: 52))` from `zwaveEvent(ThermostatFanStateReport)` — parameter 52 is not in configParams, saving 2 wasted Z-Wave frames per fan-state event. C4: added `descriptionText` to both `sendEvent` calls in `initializeVars()`. C5: removed `runIn(10, "syncClock")` from `refresh()` — the 3-hour repeating schedule already handles clock sync. Namespace switched from `djdizzyd` to `mads` in both the driver `definition()` and `packageManifest.json`; author field cleaned to `"Mads Kristensen"`. Added `Initialize` capability and `initialize()` lifecycle hook for hub-restart-safe scheduling. Driver header updated to v0.2.0 changelog format matching Daikin/SunStat style. README restructured with What It Is / Install / Capabilities / Changelog / Attribution / License sections matching daikin-wifi/README.md template.

---

## PurpleAir AQI Virtual Sensor v0.2.0 Polish Pass (2026-05-18T17:36:00-07:00)

**Task:** Apply all 5 Trinity deferred-backlog items (C1–C5), namespace/author/attribution alignment, style alignment to repo conventions, and v0.2.0 bump. This fork is now permanent — "PR-bound staging" constraint removed per directive 17-31.

**UUID generated:** `71f1eb74-4302-4a96-9c4d-20391936bf94` — replaces slug `"purpleair-aqi-virtual-sensor"` in packageManifest.json (C4).

**Changes applied:**
- **C1 (emitIfChanged):** Replaced all 4 `sendEvent` calls in `httpResponse()` (`aqi`, `category`, `conversion`, `sites`) with `emitIfChanged()` calls. Eliminates ~35,040 duplicate events/year at default 1-hour cadence. Helper added matching the canonical repo pattern (BigDecimal compare with string fallback).
- **C2 (sites descriptionText):** Both branches (`size()==1` and multi-sensor) now use `${device.displayName} sites is/averaged from...` matching `hubitat-event-hygiene` skill standard.
- **C3 (quota warning):** `update_interval` preference `description` field now warns that the "1 min" option generates ~43,800 API requests/month per sensor — near the free-tier limit.
- **C4 (UUID):** `packageManifest.json` `id` field changed from slug to UUID `71f1eb74-4302-4a96-9c4d-20391936bf94`.
- **C5 (IQAir → PurpleAir):** `parse()` log prefix fixed; gated with `if (logEnable)`.
- **Namespace/author:** `namespace: "mads"`, `author: "Mads Kristensen"` in both driver `definition()` and `packageManifest.json`. pfmiller0 credit preserved verbatim in driver's original copyright block.
- **importUrl:** Updated to `madskristensen/hubitat-drivers` repo URL.
- **logsOff:** `debugMode` renamed to `logEnable`; `logsOff()` added; `updated()` schedules `runIn(1800, "logsOff")` when `logEnable` is true.
- **lastActivity (Pattern B):** `attribute "lastActivity", "string"` declared; `touchActivity()` helper added; called after every successful `httpResponse()` parse (cloud REST = Pattern B per healthcheck-vs-lastactivity skill).
- **Sentinel guards:** Restructured sensor loop — `rawPm25?.isNumber()` guard wraps all `.toFloat()` parses on `pm2.5_atm`/`pm2.5_cf_1` field. Old code called `.toFloat()` before the null check, which could crash on faulted sensors returning null or non-numeric values.
- **DRIVER_VERSION:** Updated to `"0.2.0"`, field renamed from `VERSION` to `DRIVER_VERSION` (repo convention).
- **UPSTREAM-PR-DRAFT.md:** Status note added at top — de-prioritized; 3-bug fixes remain cherry-pickable; body intact.
- **README:** Fully rewritten to match daikin-wifi/README.md structure (What It Is / Capabilities / Setup / HPM install / Polling Architecture / What's Fixed / Known Limitations / Attribution / License / Changelog).

**No git commit.** Coordinator handles git.


