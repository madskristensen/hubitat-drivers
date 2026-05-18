# Tank — Driver Developer

**Status:** Reskilled on Daikin v0.1.0–v0.1.7 release arc (2026-05-18).  
**Next:** v0.1.8 planning and feature expansion for Daikin BRP069B.

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
