# Tank — Driver Developer

**Status:** Reskilled on Daikin v0.1.0–v0.1.7 release arc (2026-05-18). Driver-opportunity shortlist + fit rubric now available (Cypher + Trinity, 2026-05-18).  
**Next:** Await Mads's hardware pick from top 5 (Tesla WC Gen 3, Tibber, Enphase, Reolink, Mitsubishi CN105). v0.1.8 planning deferred.

---

## Team Updates — Daikin WiFi Driver Assessment (2026-05-18)

### Cypher Audit (Upstream PR Assessment)

Inspected `eriktack/hubitat-daikin-wifi` PRs #2 & #3 for v0.1.1+ applicability:

- **PR #2 (Dashboard Tiles, 2023-03-19):** Uses broken escaped-quote JSON workaround. Our v0.1.0 solves correctly via `JsonOutput.toJson()`. Skip for v0.1.1.
- **PR #3 (EZ Dashboard, 2024-06-26):** Adds JSON_OBJECT attribute type declarations + optional setter methods. Not critical for v0.1.1; viable as v0.1.2 candidate if users report rendering issues (~1.5 hrs). Current v0.1.7 has no JSON_OBJECT attributes; defer.
- **Clean-room boundary locked:** Cannot implement PR #3 code due to clean-room policy.

### Trinity Research (Daikin Driver Assessment)

Completed assessment of `eriktack/hubitat-daikin-wifi` upstream. **Decision: Fork into this repo as v0.1.0.**

**Key findings:**
- **Root bug (line 466):** `otemp="-"` (Daikin sentinel) hits `Double.parseDouble("-")` → `NumberFormatException` every poll when sensor unavailable. Fix: guard with `.isNumber()` before parse. Pattern consistent across all numeric fields.
- **Critical capability gap:** `supportedThermostatModes` never declared — breaks Rule Machine thermostat mode dropdowns. Declare in `installed()` + `updated()`.
- **Missing lifecycle:** No `initialize()` (polling dies on hub restart); energy endpoints over-polled (1440x/day, should be ~48x on 30-min schedule).
- **Event hygiene:** All 66 events lack `descriptionText`, no `lastActivity`, no `HealthCheck`.
- **Upstream:** Effectively abandoned (2021 last commit, issues disabled, 2 PRs unreviewed 1–2 years).

---

## Daikin BRP069B Protocol Reference (Project-Specific Knowledge)

### Endpoints Implemented (7 of 28 total)

| Endpoint | Purpose | Cadence | Notes |
|----------|---------|---------|-------|
| `/aircon/get_control_info` | Polling | 1–30 min (user-configurable) | Mode, setpoint, fan rate, swing mode, power state |
| `/aircon/set_control_info` | Write | On-demand | 6-param envelope: pow, mode, stemp, f_rate, f_dir, shum |
| `/aircon/get_sensor_info` | Polling | 1–30 min | Indoor/outdoor/humidity temps (sentinel-guarded) |
| `/aircon/get_week_power_ex` | Polling | 30 min (fixed) | Weekly energy (kWh per day) |
| `/aircon/get_year_power_ex` | Polling | 30 min (fixed) | Yearly energy (this/prev year, heat/cool split) |
| `/aircon/get_special_mode` | Polling | 30 min (fixed) | Econo/powerful mode flags; firmware-variable (may 404 on some units) |
| `/aircon/set_special_mode` | Write | On-demand | Enable/disable econo or powerful mode |

### Sentinel-Value Pattern

Daikin returns `"-"` in numeric fields when the sensor is unavailable (e.g., unit lacks humidity sensor, outdoor temperature not available):

`
ret=OK,htemp=22.5,otemp=-,hhum=-,stemp=20.0
`

**Guard every numeric parse with `.isNumber()` before `Double.parseDouble()`, `Integer.parseInt()`, etc.**

Fields affected:
- `otemp` (outdoor temperature) — **very common**; many units lack outdoor sensor
- `hhum` (indoor humidity) — **very common**; most units lack humidity sensor
- `htemp` (indoor temperature) — rare
- `stemp` (setpoint) — only in fan/dry modes where heating/cooling is inactive

**See skill: hubitat-sentinel-value-guards** for the full pattern and safe parsing helpers.

### Mode-to-Setpoint Mapping

Daikin modes (wire value → Hubitat enum):
- `pow=0` → `thermostatMode = "off"`
- `mode=0` → `thermostatMode = "heat"`
- `mode=1` → `thermostatMode = "cool"`
- `mode=2` → `thermostatMode = "auto"`
- `mode=3` → `thermostatMode = "dry"` (fan-only circulation, no heating/cooling)
- `mode=4` → `thermostatMode = "fan"` (fan circulation only)

**thermostatOperatingState mapping (v0.1.7 correction):**
- Heat mode → `"heating"`
- Cool mode → `"cooling"`
- Auto mode → `"idle"` (or "pending heat"/"pending cool" if real-time power monitoring available)
- Dry mode → `"fan only"` (**not** "drying"; dry mode runs fan without active heating/cooling)
- Fan mode → `"fan only"`
- Off → `"idle"`

**See skill: hubitat-thermostat-capability-enums** for valid Hubitat enum values and capacity declaration.

### F_dir (Swing Mode) Mapping

- `f_dir=0` → `"off"` (fixed vane position)
- `f_dir=1` → `"vertical"` (up/down swing)
- `f_dir=2` → `"horizontal"` (left/right swing)
- `f_dir=3` → `"3d"` (combined both axes)

### Special Mode Flags (get_special_mode)

**Wire format:**
`
ret=OK,adv=2-fff10000,...
`

Parse the `adv` field: split on dash, take leading numeric token.
- `dv=""` or `dv=0` → neither mode active
- `dv=2` → econo mode (energy-saving)
- `dv=12` → powerful mode (boost)

**Compound strings:** Some firmware returns `adv=2-fff10000`. Always split and take first token.

**Set special mode params:**
- Enable econo: `?set_spmode=1&spmode_kind=2`
- Enable powerful: `?set_spmode=1&spmode_kind=12`
- Disable: `?set_spmode=0&spmode_kind=<kind>` (kind must match what was enabled)

**v0.1.7 note:** Endpoint may return 404 on some firmware variants. Use graceful degradation: set `state.specialModeUnsupported` flag on first 404; guard both poll and command paths.

### Energy Polling Rationale

- **Week power (`get_week_power_ex`):** Polled every 30 min. Data includes daily kWh (today + yesterday). Changes once per day; 30-min cadence is overkill but harmless for low-data-volume deployments.
- **Year power (`get_year_power_ex`):** Polled every 30 min. Data includes this year (heat/cool split) and previous year. Changes hourly max. 30-min cadence adequate.
- **Why not hourly?** Daikin limit is ~1440 API calls/day (rate-limited device). Hourly polling = 48 calls/day for energy endpoints; user-configurable control polling can consume additional budget. 30-min fixed cadence balances freshness vs. quota.
- **When device is off:** Skip `refreshEnergy()` call entirely (saves ~96 API calls/day during extended off state).

### Temperature Unit Handling

- **API:** Always Celsius (wire format)
- **Hubitat display:** User can set preference to Celsius or Fahrenheit
- **tempUp/tempDown step size:**
  - Celsius: 0.5°C per step
  - Fahrenheit: 1°F per step

**Conversion:** Perform all range checks and clamping in Celsius (5–40 °C safe envelope per community reverse-engineering), then convert to user's preferred unit for display.

### Safe Setpoint Envelope

- **Outer boundary:** 5–40 °C (sentinel-checking guard zone)
- **Practical heating:** 10–30 °C (per community docs)
- **Practical cooling:** 18–32 °C (per community docs)
- **Clamp logic:** Check outer boundary first; then apply mode-specific tighter bounds as secondary guard.

---

## Daikin v0.1.0–v0.1.7 Shipped Versions Summary

| Version | Shipped | Key Changes |
|---------|---------|---|
| v0.1.0 | 2026-05-18 | Clean-room implementation; sentinel guards; polling structure; lifecycle |
| v0.1.1 | 2026-05-18 | Groovy property shadowing fix; runEvery* pattern |
| v0.1.2 | 2026-05-18 | Switched to asynchttpGet (HubAction Map forms broken) |
| v0.1.3 | 2026-05-18 | Swing mode support (f_dir mapping) |
| v0.1.4 | 2026-05-18 | Special mode polling; get_model_info cache; event hygiene audit |
| v0.1.5 | 2026-05-18 | Groovy GString typo fix; log interpolation fix |
| v0.1.6 | 2026-05-18 | Setpoint null guards; setpointDisplay attribute; energy poll guard |
| v0.1.7 | 2026-05-18 | Endpoint graceful degradation (404 handling); energy field corrections; mode-aware tempUp/tempDown; thermostat enum fix |

All 8 versions validated on Mads's real BRP069B hardware (firmware version pending Mads documentation).

---

## Skills Created / Updated (2026-05-18 Reskill)

### New Skills

1. **hubitat-sentinel-value-guards** (medium confidence)
   - Pattern for safely guarding numeric parses against sentinel values ("-" in Daikin, probe-disconnect thresholds in Watts Home, etc.)
   - Cross-driver applicable to any protocol using placeholder/sentinel values
   - Source: Daikin v0.1.0–v0.1.7 production validation

2. **hubitat-mode-aware-setpoint-commands** (low confidence)
   - tempUp/tempDown logic that adjusts active setpoint(s) based on thermostat mode
   - Pattern: Heat→heating only, Cool→cooling only, Auto→both, Off/Dry/Fan→no-op
   - Source: Daikin v0.1.7 implementation

3. **hubitat-thermostat-capability-enums** (low confidence)
   - Valid Hubitat enum values for thermostat attributes (especially thermostatOperatingState)
   - Correction: "drying" is invalid; use "fan only" for dry mode
   - Source: Daikin v0.1.7 fix

### Skills Bumped

1. **hubitat-endpoint-graceful-degradation** (low → medium)
   - Validated in Daikin v0.1.7 production: get_special_mode 404 on Mads's real hardware
   - Pattern applies to any optional endpoint across firmware variants

### Skills Confirmed (No Change)

1. **hubitat-asynchttpget-pattern** (medium) — stable across all 8 Daikin versions; corrected team memo that claimed it was "cloud-only"
2. **hubitat-hubaction-constructors** (medium) — confirmed unreliable; documented asynchttpGet as the correct LAN HTTP pattern
3. **groovy-gstring-pitfalls** (low) — validated v0.1.5 hotfix
4. **hubitat-sentinel-value-guards** (high) — already existed at high confidence; Daikin is another validation point
5. **hubitat-event-hygiene** (high) — applied throughout Daikin driver; no issues found
6. **hubitat-healthcheck-vs-lastactivity** — Pattern A (HealthCheck + ping) not applicable to Daikin (no persistent LAN socket); not used
7. **tuya-local-groovy** (high) — Not directly used in Daikin (Daikin uses HTTP, not Tuya raw socket); pattern reference only

---

## v0.1.8+ Roadmap

**Deferred features (not in v0.1.0–v0.1.7 scope):**
- On-device timer support (deferred → use Hubitat rules engine)
- Parent/child multi-unit architecture (deferred → Mads has single unit)
- EZ Dashboard JSON_OBJECT attributes (deferred → no user reports; Cypher PR #3 candidate if needed)
- Bosch Home Connect integration (separate driver scope)

**Next priorities for v0.1.8:**
- Real-hardware field testing (Mads validation)
- Community user feedback integration
- Additional Daikin model compatibility (if users report protocol variations)
- Performance profiling (API quota, polling efficiency)

---

## Reskill Completion Notes

**Ephemeral learnings archived:** Version-specific hotfix notes, in-flight debug diagnostics, and version-bump procedures moved to history-archive.md.

**Durable patterns captured:** 3 new skills created; 2 existing skills bumped. Protocol reference preserved in this section for v0.1.8+ work.

**File metrics:**
- Before reskill: history.md = 21.5 KB (truncated)
- After reskill: history.md = ~7 KB (lean active record)
- Archived: history-archive.md appended with Daikin v0.1.0–v0.1.7 summary
- Skills: 5 touched/created; 3 new, 1 bumped (low→medium), 1 reviewed (high, no change)

**2026-05-18 Team Update:** MyQ feasibility research completed; verdict = build ratgdo ESPHome HTTP driver (local, no cloud). Cypher confirmed ratgdo firmware actively maintained; Trinity sketched architecture. Awaiting Mads hardware decision. See .squad/decisions.md for full report.

**2026-05-18 Team Update (Scribe merge):** Rainbird verdict = install MHedish community driver; do not build competing version. AES/CBC + javax.crypto confirmed sandbox-safe for any future encrypted-local driver. Bosch Home Connect identified as next greenfield opportunity.

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

## Learnings - Honeywell T6 Pro Fork (2026-05-18T17:15:27-07:00)

Forked djdizzyd's Advanced Honeywell T6 Pro Thermostat into drivers/honeywell-t6-pro/. Fix surface was 3 targeted changes: (1) added missing txtEnable preference declaration (BLOCKER - had been permanently false/silent for 4+ years), (2) fixed device.currentValue=="cooling" to device.currentValue("thermostatOperatingState")=="cooling" in two locations - zwaveEvent(ThermostatFanStateReport) line 533 and zwaveEvent(BasicSet) lines 556-558 - (MAJOR - method reference never equals a string, silently broke fan-state correction logic), (3) added unschedule("syncClock") at top of configure() to prevent zombie scheduler accumulation on repeated manual configure invocations (MAJOR).

**Reusable fork-cleanup pattern:** See .squad/skills/hubitat-fork-cleanup-pattern/ for the general approach used here - preserve original copyright verbatim, add fork attribution block above it, apply only audited fixes with inline FIX comments citing severity, create packageManifest.json using Daikin as template, write README with What's Fixed table.
