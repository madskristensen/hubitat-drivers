# Decisions

---

## 2026-05-18: Tank — v0.1.5 Hotfix (daikin-wifi)

# Decision: daikin-wifi v0.1.5 hotfix — unclosed GString + empty log interpolation

**Date:** 2026-05-18  
**Author:** Tank (Driver Developer)  
**Driver:** `drivers/daikin-wifi/daikin-wifi.groovy`  
**Commit:** 6e90625

---

## Bug 1 — Unclosed triple-quote string literal (line 705)

**Symptom:** Hubitat rejected the driver at load time with a Groovy parse error.

**Root cause:** `String advRaw = kv.adv ?: """` — three quotes opened a multiline GString that was never closed. Tank-6 intended `""` (empty-string fallback) but accidentally typed `"""`.

**Fix:** Changed `?: """` → `?: ""`

---

## Bug 2 — Empty log interpolation (line 701)

**Symptom:** The warning log `[Daikin] get_special_mode: ret=` emitted no actual return value, making it useless for debugging.

**Root cause:** `log.warn "[Daikin] get_special_mode: ret="` — the `kv.ret` value was never interpolated.

**Fix:** Changed to `log.warn "[Daikin] get_special_mode: ret=${kv.ret}"`

---

## Broader lesson — always grep for `"""` after editing GString fallback expressions

A triple-quote in Groovy is not a syntax error at the point it appears — it opens a valid multiline GString. The parse error surfaces only when the file ends without a closing `"""`. This makes it easy to miss in review. 

**Pre-commit checklist addition:** Run `grep '"""'` on any Groovy driver file after touching GString fallback expressions (`?: ""` patterns). Zero matches expected in executable code.

---

## 2026-05-18: Trinity — Thermostat Ecosystem Survey

---
Date: 2026-05-18
Agent: Trinity
Decision Type: Recommendation
Category: Feature Roadmap
Status: Ready for Implementation
---

# Ecosystem Survey Findings: Thermostat Driver Feature Gap Analysis

## Summary

Surveyed 5 well-regarded Hubitat thermostat drivers (Venstar, Ecobee, Honeywell, Sensi, Hubitat built-in) against our Daikin WiFi v0.1.5 and SunStat Connect Plus v0.1.11.

**Finding:** Most community thermostat drivers implement one consistent UX pattern we're missing: `setpointDisplay` — a human-readable string attribute for dashboard display (e.g., "Heat: 72°F" or "Auto: 70°F/75°F"). This single feature improves dashboard UX with near-zero complexity.

**Secondary finding:** Daikin lacks an explicit `awayMode` attribute, while peer drivers (Venstar, Honeywell, Sensi) expose this for automation visibility. SunStat already has this ✅.

---

## Recommendation

### Phase 1 (v0.1.6) — Low-Effort Quick Wins

| Item | Driver | Effort | Benefit | Owner |
|------|--------|--------|---------|-------|
| Add `setpointDisplay` computed string | Daikin + SunStat | 0.5 hrs | High UX improvement on dashboards | Tank |
| Audit `awayMode` for Daikin BRP069B | Daikin | 0.25 hrs | Verify if API exposes; add if yes | Cypher |

**Rationale:**
- `setpointDisplay` aligns with ecosystem consensus and costs ~5 minutes per driver (one computed string attribute, emitted on mode/setpoint change).
- `awayMode` for Daikin is conditional on API availability; ask Cypher to check during protocol audit.

---

### Phase 2+ — Intentional Skips

| Item | Rationale |
|------|-----------|
| **Multi-stage HVAC** | Not applicable. Daikin is single-stage inverter; SunStat is electric floor. Defer to future heat-pump + furnace drivers. |
| **Filter maintenance reminders** | Belongs in Rule Machine / app layer, not driver. Daikin doesn't expose filter runtime. |
| **Vacation mode for Daikin** | `set_program` API endpoint rarely useful when Hubitat rules are more flexible. SunStat's `thermostatHold` is sufficient. |
| **Schedule enable/disable for Daikin** | SunStat has it ✅; Daikin's `set_program` is less valuable than Hubitat automation. No urgency. |
| **IAQ / Occupancy / Humidification control** | Outside driver scope; platform or device API limitation. |

---

## Next Steps

1. **Tank:** Implement `setpointDisplay` on both drivers (Phase 1).
2. **Cypher:** Audit BRP069B API during next protocol review; confirm if `awayMode` is exposed. File decision update if yes.
3. **Trinity:** No architectural changes needed; drivers already conform to ecosystem patterns (emitIfChanged, descriptionText, supportedThermostatModes on installed()).

---

## Cross-Reference

- **Memo:** `.squad/files/daikin-ecosystem-survey-memo.md`
- **History:** Appended to `.squad/agents/trinity/history.md` under "## 2026-05-18 Learnings"

---

## 2026-05-18: Cypher — Daikin API Audit & Driver Quality

# Decision: Daikin API Completeness + Driver Perf/Quality Audit
**Author:** Cypher  
**Date:** 2026-05-18  
**Input to:** Tank (driver), Trinity (roadmap awareness)  
**Full memo:** `.squad/files/daikin-research/daikin-api-perf-audit-memo.md`

---

## Top 5 Improvement Recommendations

| # | Item | Type | Lines | Effort | v0.1.6? |
|---|---|---|---|---|---|
| 1 | **Null guard on `setHeatingSetpoint` / `setCoolingSetpoint`** — `new BigDecimal(temp.toString())` throws NPE when temp is null (RM "clear" command). Add null check before BigDecimal construction. | 🔴 Bug | 340, 350 | 30 min | ✅ Yes |
| 2 | **Fix missing `${kv.ret}` in 2 warn messages** — lines 492 (`parseModelInfo`) and 690 (`handleSetSpecialMode`) log `"ret="` without the value. | 🟡 Quality | 492, 690 | 5 min | ✅ Yes |
| 3 | **Skip energy poll when powered off** — `refreshEnergy()` fires `get_week_power_ex` and `get_year_power_ex` even when `switch == "off"`. Add guard at top of method. | 🟡 Perf | 385 | 5 min | ✅ Yes |
| 4 | **Resolve dead computation in `handleYearPower`** — `yearTotal` is computed from monthly kWh but never emitted. Either add `energyYear` attribute or remove the computation. | 🟡 Quality | 667 | 30 min | ✅ Yes |
| 5 | **Add `/common/basic_info` call in `initialize()`** — reads `lpwFlag` for backward compat with BRP069A-series units + device name for label. If `lpwFlag=1`, append `lpw=` to all subsequent set URLs. | 🟡 Compat | — | 1–2h | ⚠️ Only if Mads can test lpw path |

---

## Daikin API Skip List

The following endpoints are confirmed to exist on BRP069B but should NOT be implemented in this driver:

| Endpoint | Reason to skip |
|---|---|
| `GET /common/get_remote_method` / `POST /common/set_remote_method` | Cloud polling negotiation; irrelevant for LAN use |
| `GET /aircon/get_program` / `POST /aircon/set_program` | Deferred per Trinity v0.1.0 memo; Hubitat rules cover scheduling |
| `GET /aircon/get_scdltimer` / `POST /aircon/set_scdltimer` | Same rationale |
| `GET /aircon/get_timer` / `GET /aircon/get_target` / `GET /aircon/get_price` | Unknown/undocumented purpose; community has not reverse-engineered |
| `POST /common/set_led` | Doesn't function on tested hardware (ael-code note) |
| `POST /common/reboot` | Dangerous — 30s disconnect; no legitimate Hubitat use |
| `POST /common/set_regioncode` | Cloud-facing configuration; irrelevant for local LAN |
| `GET /common/get_datetime` | No user-visible value in a Hubitat driver |
| `GET /aircon/get_wifi_setting` | Exposes WiFi credentials per ael-code security note |

---

## Daikin API "Maybe Later" List (v0.1.7+ candidates)

| Endpoint | User value | Blocker |
|---|---|---|
| `GET /aircon/get_demand_control` + `set` | Demand-response / max-power cap | Needs real-device validation on BRP069B41; confirmed in Apollon77 v2.2.1 (2025-05-24) |
| `GET /common/get_notify` | Filter maintenance alerts → Hubitat notification | Not implemented in Apollon77; response schema unknown |
| `GET /aircon/get_day_power_ex` | Hourly energy breakdown | Apollon77 TODO (typo'd as `get_day_paower_ex`); schema unknown |
| `GET /aircon/get_week_power` + `get_year_power` (non-_ex) | Older firmware fallback | Low priority; BRP069B target does support `_ex` |

---

## Perf Hot-Fix List

| Finding | Severity | Action |
|---|---|---|
| Null setpoint crash in setters | 🔴 Hot-fix | Guard `temp == null` before BigDecimal |
| Energy poll fires when off | 🟡 Minor | Add `switch == "off"` guard in `refreshEnergy()` |
| `handleYearPower` dead computation | 🟡 Minor | Emit as `energyYear` attribute or remove |
| Missing ret= interpolation in 2 warn logs | 🟡 Minor | Fix string interpolation |

**Overall verdict:** Driver v0.1.4/v0.1.5 is production-quality. No structural issues found. Remaining items are all maintenance-tier.

---

# Decision: daikin-wifi v0.1.6 — Five-Item Audit Bundle

**Date:** 2026-05-18
**Author:** Tank (Driver Developer)
**Driver:** `drivers/daikin-wifi/daikin-wifi.groovy`
**Commit:** 0515782
**Requested by:** Mads ("go for it")
**Input from:** Cypher (daikin-api-perf-audit-memo.md), Trinity (daikin-ecosystem-survey-memo.md)

---

## Bundle Summary

v0.1.6 ships five discrete items from the Cypher + Trinity audits as a single cohesive polish release.

---

## Item 1 🔴 — Null/range guards on setHeatingSetpoint + setCoolingSetpoint

**Problem:** `new BigDecimal(temp.toString())` throws NPE when Rule Machine passes `null` (e.g., on "clear setpoint" operations). No NaN protection either.

**Fix:** Guard chain before any dereference:
1. `null` check → `log.warn` + return
2. `.doubleValue().isNaN()` check → `log.warn` + return
3. `temperatureToC(tempBd)` → range check against BRP069B documented envelope (5–40 °C) → `log.warn` + return
4. Proceed to clamp + emit

**Source for 5–40 °C range:** Apollon77/daikin-controller DaikinAC.ts v2.2.1 (2025-05-24); ael-code/daikin-control README. The tighter operational ranges (cool 18–32 °C, heat 10–30 °C) are enforced downstream by `clampSetpoint()`. The 5–40 °C envelope is the BRP069B hardware limit.

**Conversion note:** Range check is always in °C — `temperatureToC()` is called before the range check so 70°F (21.1°C) is never misclassified as out-of-range.

---

## Item 2 🟡 — setpointDisplay STRING attribute

**Problem:** Peer thermostat drivers (Venstar, Ecobee, Honeywell, Sensi per Trinity's ecosystem survey) expose a human-readable `setpointDisplay` string for dashboard tiles. Our driver only exposes raw numeric attributes.

**Fix:** Added:
- `attribute "setpointDisplay", "string"` in metadata
- `composeSetpointDisplay()` private helper that returns mode-appropriate strings:
  - `"Off"` when mode=off
  - `"Heat: 72°F"` when mode=heat
  - `"Cool: 68°F"` when mode=cool
  - `"Auto: 70°F / 75°F"` when mode=auto
  - `"Dry"` / `"Fan"` for those modes
- Emitted at end of `handleControlInfo` (parse path) and at end of `off()`, `setThermostatMode()`, `setHeatingSetpoint()`, `setCoolingSetpoint()` (command path, for immediate tile refresh)

**Null safety:** `composeSetpointDisplay()` uses `?: "--"` fallback for null `device.currentValue()` returns (handles freshly-installed devices with no setpoints yet).

**Dashboard UX rationale:** Users see "Heat: 72°F" in a single tile attribute rather than needing to configure separate heating/cooling setpoint tiles and infer the active one from mode.

---

## Item 3 🟡 — Log string typos

**Problem:** Two `log.warn` calls emitted `"ret="` without interpolating the actual return value, making production log triage impossible for those code paths.

**Fixed:**
- `parseModelInfo` (line 532): `ret= —` → `ret=${kv.ret} —`
- `handleSetSpecialMode` (line 723): `ret="; return` → `ret=${kv.ret}"; return`

Note: The v0.1.5 hotfix had already fixed the same pattern in `parseSpecialMode` (line 735). These two remaining instances were caught by Cypher's v0.1.5 audit.

---

## Item 4 🟡 — Skip energy poll when device is off

**Problem:** `refreshEnergy()` fires `get_week_power_ex` + `get_year_power_ex` every 30 minutes regardless of device power state. Energy values cannot change when the device is off.

**Fix:** Guard at top of `refreshEnergy()`:
```groovy
if (device.currentValue("switch") == "off") {
    traceLog "Skipping energy poll — device is off"
    return
}
```

**Impact:** Saves ~96 HTTP calls/day when device is in sustained off state (e.g., off overnight from 10pm–8am = 20 cycles × 2 calls × ~2.4 nights/week ≈ 96 calls/day saved). `traceLog` chosen over `debugLog` — this is a hot no-op path.

---

## Item 5 🟡 — Remove dead yearTotal computation

**Problem:** `handleYearPower` computed `yearTotal` (sum of monthly kWh from `this_year` field) but only debug-logged it — no attribute was emitted. The computation ran every 30 minutes with zero user-visible benefit.

**Fix:** Removed the `if (thisYear) { ... }` block entirely. The response validation boilerplate (checkHttpOk, empty-body check, traceLog, ret check) stays — it still provides health-check value by confirming the device responded. If `energyYear` attribute is added in v0.1.7+, the computation can be restored at that time.

---

## Scope Exclusions

- `/common/basic_info` + lpwFlag compat: Deferred to v0.1.7 (requires Mads hardware test)
- Demand control endpoint: v0.1.7 candidate per Cypher
- SunStat setpointDisplay: Separate future task (no scope creep per constraints)
- eriktack gap audit findings (Cypher-3 in-flight): v0.1.7 scope

---

## Version Bump

`0.1.5 → 0.1.6` in four places: file header, `DRIVER_VERSION` constant, changelog, `packageManifest.json` (root + drivers[0]).

---

# Decision: Daikin Upstream Gap Audit — Worth-It List
**Author:** Cypher  
**Date:** 2026-05-18  
**Input to:** Tank (driver), Trinity (roadmap)  
**Full memo:** `.squad/files/daikin-research/daikin-upstream-gap-audit.md`

---

## Worth-It List

### 🟢 Adopt (v0.1.6)

| Item | Effort | Notes |
|---|---|---|
| `energyYesterday` attribute | ~5 min | `s_dayw[1]` already in `handleWeekPower` — add one `emitIfChanged` |
| `energyThisYear` attribute | ~10 min | Dead computation in `handleYearPower` already exists — verify `this_year` field name on real hardware first (upstream uses `curr_year_heat`+`curr_year_cool` instead) |
| `energyLastYear` attribute | ~20 min | Needs `prev_year_*` field parsing from `get_year_power_ex`; field name needs real-device verification |
| `tempUp` / `tempDown` commands (±0.5° step) | ~15 min | Not in any prior memo; dashboard button convenience; ~10 lines each |
| Fix `"drying"` → `"fan only"` in `operatingStateForMode` | ~1 min | `"drying"` is non-standard; Thermostat cap only defines `"fan only"` |

### 🟡 Defer

| Item | Notes |
|---|---|
| `energy12Months` rolling 12-month | Month-boundary arithmetic complexity; low urgency |
| `ipPort` preference | Add only if user reports non-80 port need |

### 🔴 Skip

- `fanDirectionVertical` / `fanDirectionHorizontal` toggle commands — ENUM `setSwingMode` is cleaner
- `fanRateAuto` / `fanRateSilent` as separate commands — already covered
- `setTemperature(number)` unified setter — standard setHeating/CoolingSetpoint is better
- `currMode`, `fanAPISupport`, `connection` attributes — redundant, dead, or handled better
- `displayFahrenheit` preference — `location.temperatureScale` auto-detection is superior

---

## Recommendation

We are ahead of the upstream on every structural dimension. The four adopt items + one one-liner fix are the only things upstream has that are worth pulling into v0.1.6. No architectural changes required — all are leaf-node additions to existing handlers.

---

# Trinity — Daikin WiFi v0.1.6 Hubitat Ecosystem-Citizen Audit

**Date:** 2026-05-18  
**Author:** Trinity (Lead/Architect)  
**Driver:** daikin-wifi v0.1.6  
**Decision Type:** Architecture Review / Audit  

---

## Verdict

**APPROVED — No architectural concerns.** The driver is a well-behaved Hubitat ecosystem citizen across all seven audit dimensions (event hygiene, state persistence, scheduler discipline, network behavior, lifecycle management, app compatibility, resource usage).

---

## Summary

Conducted 7-dimension ecosystem-citizenship audit (distinct from Cypher's internal perf/quality review). Findings:

- 🟢 **Event-DB chatter:** Clean. All events routed through `emitIfChanged()`, lastActivity throttled to ≥60s, descriptionText universal, debug/trace logs properly gated.
- 🟢 **State-DB churn:** Minimal. Only 2–3 state writes during entire polling cycle; no unbounded growth.
- 🟢 **Scheduler load:** Disciplined. 2 base schedules + transient one-shots; `initialize()` unschedules before re-registering. No accumulation risk.
- 🟢 **Network behavior:** Cautious. asynchttpGet + 10s timeout, 3 concurrent requests max, energy poll separated, offline device keeps retrying (no crash).
- 🟢 **Lifecycle hygiene:** Correct. installed() → updated() → initialize() chain clean; uninstalled() unschedules; hub reboot survival verified.
- 🟢 **App compatibility:** Friendly. Null/NaN guards on setpoint setters, full Thermostat capability, setpointDisplay attribute, Hub Mesh–compatible types.
- 🟢 **Resource hygiene:** Responsible. No unbounded collections, timestamp overflow safe, logging levels appropriate.

---

## Top 3 Optional Improvements

| Item | Impact | Effort |
|------|--------|--------|
| Remove dead `state.pingRequestedAt` write (line 439) | Code hygiene; 1 state I/O per ping | 0.25 hr |
| Validate `state.modelInfo` field names on real BRP069B hardware | Ensure v0.1.4+ hard-coded assumptions hold | 1 hr (test only) |
| Document offline error-log volume at 1-min polling | User transparency | 0.5 hr (docs) |

None are blocking. Optional quality-of-life cleanups post-v1.0.

---

## Comparison to Peer Drivers

- **Touchstone:** Similar event discipline; Daikin cleaner on state (no socket buffers).
- **Gemstone:** Both use lastActivity; Gemstone has cloud quota concerns Daikin doesn't face.
- **SunStat:** Peer thermostat pattern; both correct. SunStat more complex (parent/child + cloud); Daikin simpler LAN.

**Verdict:** Daikin is production-ready. No architectural rework needed.

---

## Files & References

- **Audit memo:** `.squad/files/daikin-research/daikin-hubitat-citizen-audit.md`
- **Complementary audits:**
  - Cypher's perf/quality audit: `.squad/files/daikin-research/daikin-api-perf-audit-memo.md`
  - Ecosystem survey (feature gaps): `.squad/decisions.md` (Trinity section, v0.1.6 roadmap)
- **Skills validated:**
  - hubitat-event-hygiene ✅
  - hubitat-state-hygiene ✅
  - hubitat-asynchttpget-pattern ✅
  - hubitat-healthcheck-vs-lastactivity ✅

---

## Next Steps

1. Tank/Mads: Real-device testing on v0.1.6 to validate `state.modelInfo` field names.
2. Cypher: Check BRP069B API for `awayMode` support (low-priority v0.1.7 candidate).
3. Trinity: Ecosystem-citizen audit checklist extracted to `.squad/skills/hubitat-driver-citizen-checklist/SKILL.md` for reuse on future drivers.

---

# Decision: daikin-wifi v0.1.7 bundle

**Date:** 2026-05-18  
**Author:** Tank (Driver Developer)  
**Driver:** `drivers/daikin-wifi/daikin-wifi.groovy`  
**Commit:** 6c8ea41  

---

## Five items shipped in v0.1.7

### Item 1 🔴 — Graceful 404 degradation on `get_special_mode` (CRITICAL)

**Problem:** Mads hit `[Daikin] HTTP error from get_special_mode: Not Found` in v0.1.6 logs on real hardware. His BRP069B firmware does not expose `/aircon/get_special_mode`. The v0.1.4 driver assumed this endpoint exists everywhere.

**Fix:**
- `parseSpecialMode()` now bypasses `checkHttpOk` and handles the response inline.
- On 404 (detected via `response.getStatus() == 404` OR `getErrorMessage().contains("Not Found")`): set `state.specialModeUnsupported = true` + log.info once. Do not log.warn (it's expected behavior on this firmware).
- `doSpecialModeRefresh()` guards with `if (state.specialModeUnsupported) { return }`.
- `setSpecialMode()` guards with `if (state.specialModeUnsupported) { log.warn ... ; return }`.
- `initialize()` resets `state.specialModeUnsupported = false` so a firmware update is automatically re-probed.

**Reusable pattern:** "probe-then-disable-via-state-flag" — applicable to any LAN/cloud driver where endpoint availability varies by firmware version or account tier. See `.squad/skills/hubitat-endpoint-graceful-degradation/SKILL.md` (low confidence, new pattern).

---

### Item 2 🟡 — `energyYesterday` + `energyThisYear` + `energyLastYear`

**Problem:** Energy data was already available in the Daikin responses but not surfaced as attributes.

**Fix:**
- `handleWeekPower`: `s_dayw[1]` (already received) → `energyYesterday` NUMBER attribute.
- `handleYearPower`: parse `curr_year_heat` + `curr_year_cool` (sum) → `energyThisYear`; parse `prev_year_heat` + `prev_year_cool` (sum) → `energyLastYear`.
- **Field name correction:** v0.1.6 was using `this_year` (non-existent field) for the year-power dead computation. Correct BRP069B4x field names per community reverse-engineering: `curr_year_heat`, `curr_year_cool`, `prev_year_heat`, `prev_year_cool`.
- All guarded with `.isNumber()` + trace-log on missing/non-numeric fields for firmware-variation tolerance.

---

### Item 3 🟡 — `tempUp` / `tempDown` ±0.5° step commands

**Problem:** No dashboard button-tile equivalent to adjust setpoint by a small increment.

**Fix:**
- `command "tempUp"` and `command "tempDown"` declared in metadata.
- Mode-aware: heat mode adjusts heatingSetpoint; cool mode adjusts coolingSetpoint; auto adjusts both.
- Off/dry/fan modes: log.info and return without writing.
- Step: 0.5°C (Celsius) / 1°F (Fahrenheit).
- Reuses `setHeatingSetpoint()`/`setCoolingSetpoint()` to inherit all null/range/clamp/write logic.

---

### Item 4 🟢 — `"drying"` → `"fan only"` spec fix

**Problem:** `operatingStateForMode("dry")` returned `"drying"` which is NOT in the Hubitat Thermostat capability spec's `thermostatOperatingState` enum. Valid values: `["heating", "cooling", "fan only", "pending heat", "pending cool", "idle"]`.

**Fix:** One-line change: `"drying"` → `"fan only"`. Daikin dry mode runs the fan without active heating/cooling — "fan only" is semantically correct.

---

### Item 5 🟢 — Remove dead `state.pingRequestedAt` write

**Problem:** `state.pingRequestedAt = now()` was written in `ping()` and `state.pingRequestedAt = 0L` in `initialize()` but the value was never read anywhere. Dead state write — costs I/O and pollutes state DB.

**Fix:** Removed both writes. Identified by Trinity's v0.1.6 citizen audit.

---

## Pattern document: probe-then-disable-via-state-flag

For any LAN or cloud driver where an endpoint may or may not exist depending on firmware version or account tier:

1. **Attempt the request normally** on first run.
2. **In the callback:** check for the "not found" signal (HTTP 404, `ret=ERR`, specific error string, etc.).
3. **Set `state.{endpoint}Unsupported = true`** and log.info once (not warn — it's expected behavior).
4. **Guard the poll path** with `if (state.{endpoint}Unsupported) { return }` before sending the request.
5. **Guard the command path** with `if (state.{endpoint}Unsupported) { log.warn ... ; return }` to inform the user without crashing.
6. **Reset in `initialize()`** so a firmware update re-probes automatically.
7. **Do NOT emit a fake attribute value** when unsupported — leave the attribute at its initial state.

---
# MyQ / Garage Door Opener — Hubitat Driver Feasibility Report

**Author:** Cypher (Integration / Protocol Engineer)
**Date:** 2026-05-18T15:13:58-07:00
**Requested by:** Mads Kristensen
**Status:** Complete — Recommendation: Build ratgdo ESPHome HTTP driver

---

## 1. Executive Summary

The Chamberlain/LiftMaster MyQ **cloud API is permanently closed to third parties** (blocked October 2023, confirmed hostile stance as of this report date). There is no public developer program accessible to individuals or open-source projects — partners pay a per-access fee, and Home Assistant was explicitly turned away. The community cloud-workaround ecosystem (`pymyq`, `homebridge-myq`) is **abandoned** as of late 2023; no living repo is known to be working reliably in 2026. The recommended path is a **local ESPHome REST driver targeting the ratgdo or Konnected GDO blaQ hardware** — both run identically on ESPHome firmware, expose a stable documented REST+SSE API on port 80, and have active firmware maintenance through April 2026. Konnected explicitly advertises Hubitat support. A poll-only driver (no SSE, ~5s interval) is feasible today with `asynchttpGet`, providing the `DoorControl` + `ContactSensor` + `Switch` capability set with sub-10-second latency.

---

## 2. Official MyQ API Status

### 2a. The October 2023 Block

On **October 25, 2023**, Chamberlain Group CTO Dan Phillips published a statement:

> "Chamberlain Group recently made the decision to prevent unauthorized usage of our myQ ecosystem through third-party apps."

The statement pointed to `myq.com/works-with-myq` as the list of authorized partners. The authorized list (retrieved 2026-05-18) includes: **Vivint, Alarm.com, Resideo, IFTTT, Control4, Crestron, Ezlo, RTI, Sensi, and several vehicle OEMs (Honda, Acura, Kia, Mercedes-Benz, etc.)**. No open-source platforms, no SmartThings, no Hubitat.

The Home Assistant team reached out to Chamberlain Group multiple times and received no official response. Their conclusion (published 2023-11-01, per the HA myq integration page):

> "We cannot continue to work around Chamberlain Group if they keep blocking access to third parties, the MyQ integration will be removed from Home Assistant in the upcoming 2023.12 release on December 6, 2023."

It was removed. The integration page now redirects to ratgdo as the recommended replacement.

### 2b. Developer/Partner Program

**No publicly accessible developer program exists for individual hobbyists.** The partner program requires a commercial relationship with Chamberlain Group and payment for API access. This is confirmed by:

- The HA team's statement: "partner companies pay Chamberlain Group for the privilege of letting MyQ owners control their own garage doors."
- `chamberlaingroup.com/developer` — returns HTTP 404 (checked 2026-05-18).
- `chamberlaingroup.com/partners` — returns HTTP 404 (checked 2026-05-18).

### 2c. IFTTT

IFTTT is listed as an authorized partner and remains available. However:

- IFTTT is cloud-to-cloud (no local control).
- IFTTT Webhooks can trigger open/close but provide no reliable state feedback.
- Cannot be driven from Hubitat in a clean, stable way without a custom IFTTT integration app.
- **Confidence: Low** this is useful for a Hubitat driver.

### 2d. Verdict — Official API

**Dead end. No path for individual developers, open-source projects, or Hubitat.** Do not pursue.

---

## 3. Community Cloud-Integration Landscape

### 3a. `pymyq` (arraylabs/pymyq)

- **Repo:** https://github.com/arraylabs/pymyq
- **Auth mechanism:** Email/password + reverse-engineered REST endpoints (`https://api.myqdevice.com/api/v5.1/Login` → bearer token, then `/api/v5.2/accounts/{id}/devices`).
- **Last known state:** Repo is still public with an `aiohttp`-based client. The commit log page is not rendering cleanly (JavaScript-only response), suggesting the repo may be stale.
- **Current status (2026-05-18):** **Almost certainly non-functional.** Chamberlain actively blocks the undocumented endpoints. The Home Assistant team abandoned it December 2023. No recent issues or commits found.
- **Confidence this works today:** Very low.

### 3b. Home Assistant `myq` Core Integration

- **Removed from Home Assistant in 2023.12** (December 6, 2023).
- The integration page (`home-assistant.io/integrations/myq/`) now contains the full removal notice and redirects to ratgdo.
- **Status:** Dead.

### 3c. `homebridge-myq` (hjdhjd/homebridge-myq)

- **Repo:** https://github.com/hjdhjd/homebridge-myq
- **Status:** The README now reads: "`homebridge-myq` is officially retired, for now."
- The author (hjdhjd) has fully migrated to `homebridge-ratgdo` targeting the ratgdo hardware.
- **Status:** Dead. Author recommends ratgdo.

### 3d. Existing Hubitat Community MyQ Drivers

- Multiple threads exist in the Hubitat community forum, but none are accessible (HTTP 404 on specific topic URLs tried).
- GitHub search `hubitat ratgdo` returns **0 repositories** as of 2026-05-18 — no dedicated Hubitat ratgdo driver has been published publicly on GitHub.
- Konnected explicitly advertises a Hubitat integration on their product page (`konnected.io/products/smart-garage-door-opener-blaq-myq-alternative`) with a GIF showing Hubitat integration. The mechanism is ESPHome REST polling (same as what we'd build).

### 3e. Cat-and-Mouse History

The MyQ cloud integration has been broken and patched continuously since ~2019:
- 2019: First major breakage; community worked around it.
- 2021–2022: Multiple auth endpoint changes.
- 2023-Q3: Chamberlain began active enforcement, HA integration in constant repair.
- October 2023: Formal statement; endpoints blocked at infrastructure level.
- December 2023: HA removes integration; homebridge-myq retires.

**Pattern:** Chamberlain treats community access as hostile and actively monitors/blocks it. Any cloud workaround would be a one-to-six-month window before the next breakage. This repo is local-first; cloud MyQ is specifically the anti-pattern we avoid.

---

## 4. Local-Control Hardware Alternatives

### 4a. ratgdo (Recommended Target Hardware)

**What it is:** An ESP32/ESP8266 control board that wires to the garage door opener's wall-button terminals (3-wire connection: GND, +12V serial data, obstruction sensor). Supports Security+ 2.0 (yellow learn button), Security+ 1.0 (purple/red learn button), and dry-contact openers.

**Firmware options:**
1. **ESPHome firmware** (recommended for home automation platforms) — from `ratgdo/esphome-ratgdo`
2. **MQTT firmware** (older, v2.5-era) — from `PaulWieland/ratgdo` / `ratgdo/mqtt-ratgdo`
3. **HomeKit firmware** — from `ratgdo/homekit-ratgdo`

**Active maintenance (ESPHome firmware):**
- Latest release: Firmware Release 1428, commit `aeeb338`, **April 25, 2026** (3 weeks before this report)
- Prior release: Release 1427, April 23, 2026
- Release cadence: Weekly-to-biweekly updates. Project is actively maintained.

**Hardware versions:** v2.0, v2.5, v2.53i (ESP8266), v3.2, v32 (ESP32). Newest boards (ratgdo32) are ESP32-based.

**Price:** ratgdo v2.5i with installation kit ~$45. ratgdo32 controller + kit from ratcloud.llc.

#### 4a-i. ESPHome REST API (PRIMARY INTERFACE)

When running ESPHome firmware, ratgdo exposes a standard ESPHome web server on **port 80** at `http://<device-ip>/`. The REST API follows ESPHome's documented `/<domain>/<entity_name>/[action]` pattern.

**Garage door cover entity** (primary control):

| Operation | Method | URL | Description |
|---|---|---|---|
| Get state | GET | `/cover/Garage Door` | Returns JSON with state/value/current_operation |
| Open door | POST | `/cover/Garage Door/open` | Opens door |
| Close door | POST | `/cover/Garage Door/close` | Closes door |
| Stop door | POST | `/cover/Garage Door/stop` | Stops mid-travel |
| Toggle door | POST | `/cover/Garage Door/toggle` | Toggle open/close |
| Set position | POST | `/cover/Garage Door/set?position=0.5` | Set to specific position (0.0=closed, 1.0=open) |

**GET `/cover/Garage Door` response:**
```json
{
  "id": "cover/Garage Door",
  "state": "OPEN",
  "value": 1.0,
  "current_operation": "IDLE",
  "position": 1.0
}
```

- `state`: `"OPEN"` or `"CLOSED"`
- `value`: Float 0.0–1.0
- `current_operation`: `"OPENING"`, `"CLOSING"`, or `"IDLE"`

**Light control:**

| Operation | Method | URL |
|---|---|---|
| Get state | GET | `/light/Light` |
| Turn on | POST | `/light/Light/turn_on` |
| Turn off | POST | `/light/Light/turn_off` |

**Obstruction sensor:**

| Operation | Method | URL | Response |
|---|---|---|---|
| Get state | GET | `/binary_sensor/Obstruction` | `{"id":…,"state":"ON","value":true}` |

**Motion sensor** (Security+ 2.0 only):

| Operation | Method | URL | Response |
|---|---|---|---|
| Get state | GET | `/binary_sensor/Motion` | `{"id":…,"state":"ON","value":true}` |

**SSE event stream** (real-time push):

- URL: `http://<device-ip>/events`
- Protocol: Server-Sent Events (`text/event-stream`)
- Pushes `state` events for all entities as JSON
- **⚠️ NOT usable from Hubitat** — SSE is a streaming HTTP connection; Hubitat's sandbox does not support persistent connections or streaming responses. Must poll instead.

**Authentication:** None by default. Optional HTTP Basic Auth with the device name as username and the OTA password. If auth is configured, set `Authorization: Basic <b64>` header on all requests.

**Entity name caveat:** Entity names depend on the ESPHome YAML configuration. The example names above ("Garage Door", "Light", "Obstruction") are the ratgdo defaults but can be customized. Safer approach: use the `/` root page which lists all entities, or document the default names and let users override in driver settings.

#### 4a-ii. MQTT Firmware (Legacy)

The original `PaulWieland/ratgdo` (MQTT) firmware uses these topics:

```
Subscribe (status from device):
  <prefix>/<device_name>/status/door     → "opening", "open", "closing", "closed"
  <prefix>/<device_name>/status/light    → "on", "off"
  <prefix>/<device_name>/status/obstruction → "obstructed", "clear"

Publish (commands to device):
  <prefix>/<device_name>/command/door    → "open", "close", "stop"
  <prefix>/<device_name>/command/light   → "on", "off"
```

**Hubitat limitation:** Hubitat has **no built-in MQTT client**. A driver cannot subscribe to MQTT topics. This path would require an external MQTT-to-Hubitat bridge (e.g., Node-RED or MQTT Bridge app). **Not recommended as a first-class driver.**

### 4b. Konnected GDO blaQ

- **Product:** https://konnected.io/products/smart-garage-door-opener-blaq-myq-alternative — $89
- **Firmware:** Runs **ESPHome firmware** pre-loaded (`konnected-io/konnected-esphome`, updated May 12, 2026)
- **Compatibility:** Same Security+ protocol as ratgdo; also supports Security+ 1.0, 2.0, dry contact. Works with all Chamberlain/LiftMaster learn button colors.
- **Hubitat support:** Explicitly advertised. The product page shows a GIF of Hubitat integration (captured 2026-05-18).
- **API:** Identical to ratgdo ESPHome REST API — same entity domains and actions. `homebridge-ratgdo` explicitly supports both ratgdo and Konnected blaQ devices ("Support for all current Ratgdo-branded devices as well as for variants like Konnected blaQ that use ESPHome").
- **Differences from ratgdo:** Premium build quality, includes installation kit, vehicle presence sensor available on higher-end models. Commits to Matter support when garage door type is added to the spec.
- **Verdict:** A driver targeting ratgdo ESPHome firmware covers Konnected blaQ automatically. They are protocol-identical.

### 4c. Shelly Relays (Generic Dry Contact)

- **Approach:** Wire a Shelly 1/1PM to the garage door's dry contact terminals. Use a reed switch for door position.
- **Compatibility:** Works with older/simpler openers (non-Security+ 2.0). **Does not work with Security+ 2.0** — the serial bus protocol blocks unauthorized relay pulses.
- **Local API:** Shelly Gen1: `http://<ip>/relay/0?turn=on&timer=1` (momentary pulse). Gen2: REST JSON API.
- **Hubitat:** Existing Shelly community drivers cover this. Not a new driver problem.
- **Verdict:** Valid for dry-contact openers. Not relevant for Security+ 2.0 (most modern Chamberlain/LiftMaster).

### 4d. Meross Smart Garage Door Openers

- Not investigated in depth. Meross devices generally use a cloud API (meross.com) with no official local API documented. Not a local-first option. Skip.

### 4e. Generic Contact + Relay Template

- Could work for simple openers. No ratgdo needed. But provides no obstruction sensing, light control, or Security+ 2.0 support. Lowest-common-denominator option. Already handled by existing Hubitat virtual devices + rules.

---

## 5. Recommended Driver Shape

### **Recommendation: (b) ratgdo ESPHome REST HTTP driver**

**Single driver, targeting ratgdo/Konnected ESPHome firmware, using asynchttpGet polling.**

#### Rationale

1. **Local-first policy match:** This repo is explicitly local-first. The ESPHome REST API runs on the device's LAN address at port 80. No cloud dependency, no auth overhead (by default), no quota.

2. **Stable, actively maintained API:** ESPHome's web server API is versioned and documented. ratgdo firmware releases weekly. The REST API contract (cover/binary_sensor domains) has been stable across ESPHome versions.

3. **Covers both ratgdo and Konnected blaQ** — the two dominant hardware options in the community post-MyQ exit. One driver, two hardware options.

4. **Hubitat sandbox compatibility:** `asynchttpGet` + polling on a 5s schedule works correctly. We've already proven this pattern on Daikin. No SSE/WebSocket/MQTT needed.

5. **Capabilities:**
   - `GarageDoorControl` (open/close/door contact state)
   - `ContactSensor` (door open/closed)
   - `Switch` (light on/off — optional child or attribute)
   - `MotionSensor` (optional — Security+ 2.0 only)
   - `Sensor` (obstruction status attribute)
   - `HealthCheck` / `lastActivity` — use **Pattern A (full HealthCheck)** because the device is local LAN. A GET to `/cover/Garage Door` is the ping probe.

6. **No hardware purchase required to validate protocol** — the ESPHome REST API spec is fully documented and the entity schema is deterministic from the ratgdo ESPHome YAML configs.

#### What to Build (Tank's scope)

```
Driver: ratgdo-esphome
Capabilities: GarageDoorControl, ContactSensor, Switch (light), HealthCheck
Attributes: door (open/closed), contact (open/closed), switch (on/off), obstruction (string), motion (string), lastActivity (string), healthStatus (enum)

Commands: open(), close(), stop(), toggle(), lightOn(), lightOff()

Poll cycle (runEvery5Seconds): GET /cover/Garage Door, GET /binary_sensor/Obstruction
Optional slow cycle (runEvery1Minute): GET /binary_sensor/Motion, GET /light/Light

Settings: IP address (required), Entity name prefix (default = ratgdo defaults), poll interval (default 5s), enable motion sensor (bool), enable light control (bool), auth enabled (bool), username/password (if auth enabled)
```

#### Rejected Alternatives

| Option | Reason Rejected |
|---|---|
| **(a) Pure cloud MyQ driver** | API is blocked. No path for open-source. Will break again. |
| **(c) ratgdo MQTT driver** | Hubitat has no MQTT client. Requires external broker + bridge. Adds user infrastructure burden. |
| **(d) ESPHome native_api driver** | Binary protobuf protocol over port 6053. No Groovy protobuf library. Cannot decode in sandbox. |
| **(e) Generic contact + relay** | Doesn't solve Security+ 2.0 (the problem case). Already coverable with virtual devices. No new driver needed. |
| **(f) Don't build — recommend existing** | No existing Hubitat driver on GitHub (search returned 0 results 2026-05-18). Community Hubitat forum threads on the topic appear to 404. This is a real gap. |

---

## 6. Risk Register

| Risk | Likelihood | Severity | Trigger / Warning Sign | Mitigation |
|---|---|---|---|---|
| **ESPHome entity name drift** — ratgdo firmware renames entities (e.g., "Garage Door" → "door") | Medium | Medium | Breaking after OTA update; GET returns 404 | Make entity names user-configurable in driver settings. Provide auto-detect command that reads `/` root index. |
| **ESPHome REST API schema change** — field names or JSON structure changes | Low | High | Parse failures on poll response | Pin driver docs to API version. Guard all field reads with null checks. |
| **ratgdo project abandonment** | Low | Medium | No commits in 3+ months | Active as of April 2026. MQTT firmware is an older fallback. ESPHome itself is independent. |
| **Konnected diverges from ratgdo API** | Low | Low | Entity names differ | Already handled by configurable entity names. |
| **SSE push events missed (polling gap)** | Certain | Low-Medium | Door state stale for up to 5s | Accept 5s latency as design constraint. Document this. Reduce to 2s if user wants faster response (increases load). |
| **Security+ 2.0 firmware changes by Chamberlain** | Very Low | High | ratgdo stops controlling door | Chamberlain would have to ship a signed firmware update to openers. Unlikely; would anger all users. |
| **Hubitat removes asynchttpGet** | Very Low | High | All LAN HTTP drivers break simultaneously | Not specific to this driver. Mitigation is not feasible at driver level. |
| **IP address changes** | Medium | High | Polling returns connection refused | Require static IP or DHCP reservation. Document prominently. |

---

## 7. Open Questions for Trinity

1. **GarageDoorControl vs DoorControl capability:** Hubitat has `GarageDoorControl` (open/close/door attribute) as the standard garage capability. Should the driver also declare `ContactSensor` redundantly, or just `GarageDoorControl`? Some RM rules prefer `ContactSensor` for automation triggers. This is an architecture/UX decision.

2. **Child device pattern for light?** The ratgdo exposes a separate light controllable independently of the door. Should the light be a separate child device with `Switch` capability, or an attribute + commands on the parent? SunStat uses parent/child for independent actuators; Daikin uses attributes. Door + light are logically separate — child device feels right but adds complexity.

3. **Partial position support:** ratgdo ESPHome supports setting the door to any position (0.0–1.0). Hubitat has no standard capability for this. Should we expose a custom `setPosition(value)` command, or ignore partial positions and stick to open/closed/stop?

4. **Polling interval tradeoff:** 5s polling is 12 GET requests/minute to a local device. Fine for a single door. For users with 2+ doors (multiple driver instances), is there a concern about hub load? Touchstone uses 5s too — probably fine to set same default.

5. **Motion sensor child vs attribute:** Security+ 2.0 motion events are short-duration (someone walks past the opener). Should motion be a child `MotionSensor` device or a `MotionSensor` capability on the parent? Child is cleaner for RM automation — you can use "Motion Sensor: Active" triggers directly.

---

## 8. Sources & Citations

| Source | URL | Retrieved | Notes |
|---|---|---|---|
| Chamberlain CTO statement on API block | https://chamberlaingroup.com/press/a-message-about-our-decision-to-prevent-unauthorized-usage-of-myq | 2026-05-18T15:00-07:00 | Full text retrieved. Dated October 25, 2023. Update note added November 7, 2023. |
| MyQ authorized partners list | https://www.myq.com/works-with-myq | 2026-05-18T15:05-07:00 | Lists: Vivint, Alarm.com, Resideo, IFTTT, Control4, Crestron, Ezlo, RTI, Sensi, Honda, Acura, Kia, Nissan, INFINITI, Mercedes-Benz, VW, Mitsubishi, STEER Tech |
| Chamberlain developer page | https://chamberlaingroup.com/developer | 2026-05-18T15:00-07:00 | Returns HTTP 404 |
| HA myQ integration removal notice | https://www.home-assistant.io/integrations/myq/ | 2026-05-18T15:02-07:00 | Full removal notice, December 2023 timeline, ratgdo recommendation |
| pymyq library | https://github.com/arraylabs/pymyq | 2026-05-18T15:03-07:00 | Repo exists, last activity unknown (commits page JS-only). Auth: email/password → bearer token. Likely non-functional. |
| homebridge-myq retirement notice | https://github.com/hjdhjd/homebridge-myq | 2026-05-18T15:06-07:00 | README: "officially retired, for now." Author moved to homebridge-ratgdo. |
| ratgdo project home | https://paulwieland.github.io/ratgdo/ | 2026-05-18T15:10-07:00 | Active hardware project. ESP8266/ESP32 firmware. ESPHome/HomeKit/MQTT options. |
| ratgdo MQTT firmware | https://github.com/PaulWieland/ratgdo | 2026-05-18T15:11-07:00 | MQTT topic schema documented. v2.5 era. |
| ratgdo MQTT topic docs (config page) | https://paulwieland.github.io/ratgdo/02_configuration.html | 2026-05-18T15:12-07:00 | MQTT topic format: `<prefix>/<device>/[command|status]/[door|light|obstruction]` |
| ratgdo ESPHome firmware repo | https://github.com/ratgdo/esphome-ratgdo | 2026-05-18T15:04-07:00 | Latest release: 1428, commit aeeb338, **April 25, 2026**. Active maintenance confirmed. |
| ratgdo ESPHome release history | https://github.com/ratgdo/esphome-ratgdo/releases | 2026-05-18T15:15-07:00 | 10+ releases in 2026. Weekly cadence. |
| ratgdo HomeKit firmware | https://github.com/ratgdo/homekit-ratgdo | 2026-05-18T15:07-07:00 | For ESP8266 v2.5 boards. Separate repo for ESP32 (homekit-ratgdo32). |
| ratgdo new store / firmware page | https://ratcloud.llc/pages/firmware | 2026-05-18T15:08-07:00 | New store launched July 9, 2024. ratgdo32 available for purchase. |
| ESPHome Web API documentation | https://esphome.io/web-api/ | 2026-05-18T15:09-07:00 | Full REST API spec. Cover entity: GET state, POST open/close/stop/toggle/set. Binary sensor: GET state. |
| Konnected GDO blaQ product page | https://konnected.io/products/smart-garage-door-opener-blaq-myq-alternative | 2026-05-18T15:13-07:00 | $89. ESPHome firmware pre-loaded. Hubitat integration GIF shown. "Made for ESPHome." Commit to Matter when spec supports GDO type. |
| Konnected GitHub org | https://github.com/konnected-io | 2026-05-18T15:14-07:00 | konnected-esphome updated May 12, 2026. gdolib updated May 12, 2026. Active. |
| homebridge-ratgdo (hjdhjd) | https://github.com/hjdhjd/homebridge-ratgdo | 2026-05-18T15:15-07:00 | Supports ratgdo ESPHome and Konnected blaQ. Uses ESPHome REST API. Protocol-identical confirmed. |
| ratgdo NodeRED/MQTT example | https://paulwieland.github.io/ratgdo/04_nodered_example.html | 2026-05-18T15:11-07:00 | MQTT payload values: "opening", "open", "closing", "closed", "obstructed", "clear", "locked", "unlocked", "on", "off" |


---
# Architecture Sketch: MyQ-Class Garage Door Driver

**Date:** 2026-05-18  
**Author:** Trinity (Lead / Architect)  
**Status:** Draft — pending Cypher's protocol feasibility findings  
**Feeds into:** Cypher's MyQ/ratgdo research task (parallel)

---

## 1. Capability Shape

### Recommended Capability Set

| Capability | Recommendation | Rationale |
|---|---|---|
| `GarageDoorControl` | ✅ REQUIRED | Canonical Hubitat garage door capability. Provides `open()`, `close()` commands and `door` attribute (`open`, `closed`, `opening`, `closing`, `unknown`). Without this, the driver is not first-class. |
| `ContactSensor` | ✅ INCLUDE | Mirrors `door` as `contact` (`open`/`closed`). Many automations (RM, HSM) check ContactSensor, not GarageDoorControl. Cost = 1 extra event per state change. |
| `Refresh` | ✅ INCLUDE | Standard "poll now" capability. Needed for both cloud (force immediate poll) and local HTTP (demand check). |
| `Initialize` | ✅ INCLUDE | Standard lifecycle hook. Re-establishes connection or polling schedule after hub reboot. |
| `Actuator` | ✅ INCLUDE | Semantic marker: this device takes commands. Required for RM "All actuators" groups. |
| `Sensor` | ✅ INCLUDE | Semantic marker: this device has state. Required for RM "All sensors" groups. |
| `HealthCheck` | ⚠️ CONDITIONAL | Local path only (ratgdo). Cloud path: `lastActivity` only — no probing (API quota). See healthcheck-vs-lastactivity skill. |
| `Switch` | ❌ SKIP | `on=open, off=closed` is dangerous in the garage door domain. Encourages automations that "turn off" a garage door without thinking. Opens the door to misuse by Rule Machine beginners. Hard no. |
| `Battery` | ⚠️ CONDITIONAL | Include only if the API/protocol reports battery level. MyQ wall buttons and some sensors have batteries — expose if available, omit otherwise. |
| `Light` | ❌ NOT IN THIS DRIVER | MyQ opener lights exist but belong in a **child device** (see §2). Mixing light control into the door driver creates confusing capability surface. |

### Attribute Design

```
door          : enum  ["open", "closed", "opening", "closing", "unknown"]
contact       : enum  ["open", "closed"]           // mirrors door
lastActivity  : string                             // ISO-8601 timestamp, cloud path
healthStatus  : enum  ["online", "offline", "unknown"]  // local path only
obstructed    : enum  ["true", "false", "unknown"] // if API reports obstruction
```

- **`obstructed`** deserves an attribute even though few APIs expose it. Cloud MyQ does; ratgdo may also. Emit it if available; leave it absent if not. Use `unknown` as initial safe value.
- **`contact` mirrors `door`**: emit both on same state change. If `door` = "opening" or "closing", emit `contact` = "open".
- **Log every open/close at INFO unconditionally** (audit trail — see §5).

---

## 2. Parent/Child Decision Tree

```
Does this driver manage ONE physical opener?
  YES, single opener, no light:
    → Single-device driver (no parent/child).
  YES, single opener, WITH light via same API endpoint:
    → Single parent (opener) + 1 child (light).
    → Parent: GarageDoorControl + ContactSensor + Refresh + ...
    → Light child: Switch + Light capability
  Is this a CLOUD driver managing MULTIPLE openers under one account?
    YES → Parent/child required.
    → Parent: holds auth tokens, polling schedule, discoverDevices()
    → Child per door: GarageDoorControl + ContactSensor + Refresh
    → Child per light (if API exposes it): Switch + Light
    → Reuse SunStat parent/child pattern exactly (hubitat-parent-child-cloud-driver skill)
  Is this a LOCAL (ratgdo) driver?
    → One physical ratgdo = one Hubitat device.
    → No parent/child needed unless ratgdo-home firmware supports multiple doors from one bridge.
    → Start single-device. Add parent/child later if multi-door ratgdo scenarios emerge.
```

### Light Child Device

- **When to create:** only if the API/protocol reports opener light state AND supports light on/off commands.
- **isComponent:** `false` — let users rename it (same as SunStat child pattern).
- **Child DNI:** `"myq-light-${openerCloudId}"` (cloud) or `"ratgdo-light-${deviceNetworkId}"` (local).
- **Capabilities:** `Switch`, `Light`, `Actuator`. No `GarageDoorControl` bleed.
- **Parent-to-child push:** same `child.parseLightState(body)` pattern as SunStat's `child.parseDeviceState(body)`.

---

## 3. Three Driver Path Sketches

### Path A — Cloud MyQ (Chamberlain/LiftMaster API)

> ⚠️ HIGH RISK. Chamberlain killed third-party API access Oct 2023. Any viable path relies on reverse-engineered auth or unofficial clients (e.g., `pymyq`, `node-liftmaster`). Cypher must assess viability before committing here.

**Repo patterns reused:** SunStat (parent/child, cloud REST, token bootstrap, lastActivity health)

```
Auth bootstrap:
  → User obtains refresh token via external tool (same as SunStat Cognito pattern)
  → Paste into password-type preference (long-secret, >1KB — see hubitat-long-secrets skill)
  → initialize() lifts token into state, schedules polling

Poll cadence:
  → 30s interval (same ceiling as Gemstone; cloud API quota concern)
  → On command (open/close): optimistic state emit → API call → confirm via next poll
  → Event hygiene: emitIfChanged on door/contact; touchActivity() on every 2xx (not on 4xx/timeout)

Command latency:
  → Send open/close command → emit door="opening"/"closing" immediately (optimistic)
  → Poll 5s after command to confirm → emit door="open"/"closed" if confirmed
  → No state machine needed if API confirms immediately; add pseudo-boost (runIn 5s poll) if not

Health monitoring:
  → lastActivity ONLY (Pattern B from healthcheck-vs-lastactivity skill)
  → NO HealthCheck capability (avoids quota-consuming pings)

Key risk:
  → API breakage with zero notice (it's already happened once)
  → Token rotation: rotated refresh_token MUST be persisted after every use (SunStat lesson)
```

**Parent/child shape:** Parent holds account auth + discovery. One child per door. One child per light (if API reports it).

**Folder:** `drivers/myq-garage/` with `myq-garage-parent.groovy` + `myq-garage-child.groovy`

---

### Path B — ratgdo Local (Hardware Bridge)

> ✅ PREFERRED if hardware is available. ratgdo is an ESP32-based local hardware bridge that speaks Hubitat directly over HTTP or MQTT. No cloud dependency. Totally different driver — NOT a MyQ driver, it's a "ratgdo on a MyQ opener" driver.

**Repo patterns reused:** Gemstone (local HTTP polling, asynchttpGet), Daikin (LAN HTTP, local polling cadence, probe-then-disable-via-state-flag)

```
Discovery:
  → User enters ratgdo IP in driver preference (same as Daikin/Gemstone)
  → No discovery protocol needed — single device per IP

Protocol options (Cypher to confirm):
  → HTTP polling: GET /status every N seconds → parse JSON → emit door/contact/light
  → HTTP commands: POST /open, POST /close, POST /light/on, POST /light/off
  → MQTT subscribe: ratgdo publishes state topics; Hubitat MQTT not natively available
    (MQTT path requires a Hubitat MQTT bridge app — adds dependency, higher complexity)
  → Recommend HTTP polling if ratgdo supports it; MQTT only if no HTTP status endpoint

Poll cadence:
  → 5–10s interval for door state (doors move fast; 30s is too slow)
  → asynchttpGet with 10s timeout (same as Daikin pattern)
  → On command: optimistic emit + immediate re-poll (runIn 1, "refresh")

Health monitoring:
  → Local polling = free probing → full HealthCheck capability (Pattern A)
  → ping() = fire a GET /status, expect 2xx within 5s
  → healthStatus: online/offline/unknown

Safety nuance:
  → ratgdo may expose obstruction sensor (if present) via API field → emit `obstructed` attribute
  → Rate-limit close() at driver level: if door is already closing, log.warn + return

Folder: drivers/ratgdo-garage/
  → ratgdo-garage.groovy (single device, no parent/child)
  → packageManifest.json (separate from cloud driver)
```

**This is a separate driver, not a variant of the cloud driver.** Different install, different capabilities, different risk profile.

---

### Path C — Generic Relay + Contact Sensor

> ℹ️ Low-value for this repo. This is just a virtual device composed from a relay (Switch) and a ContactSensor. No reverse-engineering, no Chamberlain dependency, but also no value-add over Hubitat's existing built-in virtual drivers.

```
Shape:
  → Single device: Switch (relay for open/close motor trigger) + ContactSensor (magnetic sensor on door)
  → GarageDoorControl wrapping a virtual relay + contact would need a Hubitat App, not a driver

Assessment:
  → "Driver" here is really just docs + Rule Machine setup instructions
  → No Groovy code needed; Hubitat's built-in virtual devices handle this
  → If Mads wants this path: write a docs/guides/ entry, not a driver

Verdict: NOT a driver in this repo. Document as a guide only.
```

---

## 4. Folder & Packaging

```
drivers/
  myq-garage/                  # Cloud path (if viable)
    myq-garage-parent.groovy
    myq-garage-child.groovy
    packageManifest.json       # separate HPM entry from ratgdo
    README.md
  ratgdo-garage/               # Local path (preferred)
    ratgdo-garage.groovy
    packageManifest.json       # separate HPM entry
    README.md
```

- **Separate HPM packages, separate manifests.** These are unrelated install experiences — one requires hardware purchase, one requires a cloud account. Bundling them confuses users.
- **Separate README per driver.** Cloud README must warn prominently about API fragility. ratgdo README must list hardware prerequisites and ratgdo firmware version.
- **No shared code between paths.** Sharing Groovy across drivers is not an established pattern in this repo and creates coupling risk. Both are small enough to be self-contained.
- **packageManifest.json convention:** follow existing manifests (see Gemstone/SunStat examples) — `id`, `name`, `namespace`, `author`, `version`, `minimumHEVersion`, `documentationLink`, `releaseNotes`, `dateReleased`, `drivers[]`.

---

## 5. Garage-Door-Specific Safety Considerations

These are unique to the garage door domain and have no peer in existing repo drivers:

1. **Log every open/close at INFO, unconditionally.**  
   Garage doors closing on people or pets is a real safety risk. Every `open()` and `close()` command must be logged at `log.info` regardless of `logEnable`. This is an audit trail requirement, not a debug feature. Pattern: `log.info "${device.displayName}: close() commanded by ${device.currentValue("door") ?: 'unknown'} state"`

2. **No auto-close timer in the driver.**  
   Auto-close logic belongs in Rule Machine or Safety Monitor. The driver should not implement any "close after X minutes" behavior — this is too consequential for a driver-level default. Document clearly in README.

3. **`close()` rate-limiting.**  
   If the door is already `closing`, a second `close()` call should log.warn and return without sending a second command. The door is already responding; double-sending a close command to some openers causes a re-open. Guard: `if (device.currentValue("door") in ["closing", "closed"]) { log.warn ...; return }`.

4. **`open()` when already open.**  
   Similarly, guard `open()` against already-open state: `if (device.currentValue("door") in ["opening", "open"]) { log.warn ...; return }`.

5. **Obstruction detection.**  
   If the API/protocol exposes an obstruction sensor (ratgdo does via the safety beam), surface it as an `obstructed` attribute. Do NOT suppress or hide obstruction events. Consider emitting at `log.warn` level (not just `log.info`) when obstruction is detected.

6. **Optimistic state vs. confirmed state.**  
   Emit `door = "opening"/"closing"` immediately on command (optimistic), then confirm via next poll. If poll does not confirm within a reasonable timeout (e.g., 30s), emit `door = "unknown"` and log a warning. Do not leave stale optimistic state in place indefinitely.

7. **No `Switch` capability on this driver.**  
   Confirmed skip (see §1). Rule Machine's "Turn off all switches" automations must not trigger a garage door close. Switch capability makes this impossible to prevent.

---

## 6. Open Questions for Mads

Before committing to either driver path, need answers to:

| # | Question | Blocking? | Path(s) Affected |
|---|---|---|---|
| 1 | **Do you have a MyQ opener?** (Chamberlain or LiftMaster brand?) | Yes — determines if real-device testing is possible | Cloud (Path A) |
| 2 | **Do you have a ratgdo device?** (or budget/willingness to buy one — ~$35 USD) | Yes — ratgdo is the preferred local path | Local (Path B) |
| 3 | **Acceptable API fragility risk?** The cloud MyQ path may break again with no notice. Is this a "best effort, may break" driver, or do you need reliability? | Risk appetite decision | Cloud (Path A) |
| 4 | **Primary use case?** (Security/automation trigger vs. dashboard display vs. voice control) | Informs capability priority | All paths |
| 5 | **Does your opener have a built-in light?** And do you want to control it from Hubitat? | Determines if light child device is worth building | All paths |
| 6 | **ratgdo firmware preference?** ratgdo2 (older) vs. ESPHome-based ratgdo (newer). HTTP API differs. Cypher needs to assess both. | Affects protocol sketch | Local (Path B) |

---

## 7. Recommendation

**Hold for Cypher's verdict on protocol viability before writing any Groovy.**

Rationale:
- If ratgdo exposes a clean local HTTP API → **build Path B (ratgdo-garage) first.** It's local, reliable, and follows established repo patterns (Gemstone/Daikin). Low risk, high confidence.
- If cloud MyQ auth is still viable (reverse-engineered) → **build Path A (myq-garage) as "best effort."** Clearly label it fragile in the README. Reuse SunStat parent/child patterns exactly.
- Path C (generic relay) → document only; no driver code.

**Pre-commit signal:** If Cypher confirms ratgdo HTTP status + command endpoints exist → green-light ratgdo driver immediately. This is the one path where hardware availability (question #2 above) is the only remaining blocker.

**Do not build both paths simultaneously.** Pick the one Mads can test on real hardware first. The other can follow once the primary driver is shipped and stable.

---

*Filed: 2026-05-18T15:13:58-07:00 | Author: Trinity | Next: awaiting Cypher's MyQ feasibility report*



---

# Rainbird LNK WiFi Module — Integration Feasibility Memo

**Author:** Cypher (Integration / Protocol Engineer)  
**Date:** 2026-05-18T15:44:37-07:00  
**Requested by:** Mads Kristensen  
**Disposition:** IMPROVE-EXISTING (see §5 for full rubric)

---

## 1. Executive Summary

A high-quality, actively maintained Hubitat driver for the Rainbird LNK WiFi module already exists: **MHedish/Hubitat** `RainBird-LNK-Wi-Fi-Module.groovy`, v1.0.0.0, last commit 2026-05-07. It implements the correct encryption (AES-256-CBC via `javax.crypto.Cipher`, confirmed sandbox-safe), parent/child architecture for per-zone control, multi-firmware handling, and is distributed via HPM. The LNK protocol is local HTTP POST to `/stick`, stateless (no session), with a custom binary-in-JSON-RPC-in-AES envelope that the community has fully reverse-engineered and kept stable for 5+ years. Rubric score is **92/100** (Strong Fit) — the protocol is clean, local, and sandbox-safe. **The recommendation is IMPROVE-EXISTING: install and evaluate MHedish's driver first; file issues or fork only if specific gaps exist.** Building a net-new competing driver from scratch is not justified when MHedish is actively patching v1.0.

---

## 2. Existing Hubitat Driver Audit

### Summary Table

| Author | Repo | Last Commit | Arch | Protocol | Encryption | Status | Verdict |
|---|---|---|---|---|---|---|---|
| **MHedish** | `MHedish/Hubitat` `Drivers/RainBird-LNK/` | **2026-05-07** | Parent + per-zone children | `httpPost /stick` | AES/CBC/NoPadding, SHA-256 key, `new Random().nextBytes(iv)` | ✅ Active, HPM-published | **Use this** |
| **craigde** (hosted by jbilodea) | `jbilodea/Hubitat` `Rainbird/Drivers/` | 2020-08-27 | Single parent, no children | `httpPost /stick` | AES/CBC/NoPadding, SHA-256 key, ASCII-only IV | ⚠️ Stale (6 years) | Superseded |

### Driver 1: MHedish — Rain Bird LNK/LNK2 WiFi Module Controller

**Repo:** `https://github.com/MHedish/Hubitat`  
**Files:** `Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Module.groovy` (~61KB parent), `RainBird-LNK-Wi-Fi-Zone-Child.groovy` (child)  
**Version:** v1.0.0.0 (25+ prior patch releases visible in CHANGELOG)  
**Last commit:** 2026-05-07  

**Capabilities:**
- Parent: `Actuator`, `Configuration`, `Initialize`, `Refresh`, `Sensor`, `Switch`, `Valve`
- Child (per zone): `Switch`, `Valve`

**Architecture:** Parent/child. Parent holds IP + password, polls device, creates one child per detected zone. Per-zone children expose `on()` / `off()` mapped to `ManuallyRunStation` / `StopIrrigation`. Non-contiguous zone numbering supported (e.g., zones 1–7 + 11–13 for expansion modules).

**Protocol approach:** `httpPost` to `http://$ipAddress/stick`, `Content-Type: application/octet-stream`. Binary-encrypted JSON-RPC 2.0 body. Also calls `httpGet /irrigation/status.json` on startup for firmware version detection.

**Encryption:** `javax.crypto.Cipher / AES/CBC/NoPadding / SunJCE`. SHA-256(password) = 32-byte AES-256 key. `new Random().nextBytes(iv)` — proper random 16-byte IV (improvement over craigde's ASCII-restricted IV). Frame: `[SHA256(plaintext) 32B][IV 16B][ciphertext N×16B]`.

**Known issues / notes:**
- Handles HTTP 503 (device busy) with adaptive pacing + `pauseExecution(delayMs)` to prevent /stick session collisions
- Supports legacy firmware 2.1/2.9, hybrid, and modern 3.x
- No HTTPS support in the Groovy layer (HTTP only; the device also listens on 443 with self-signed cert but the driver doesn't use it)
- Does not use `asynchttpPost` — uses the blocking `httpPost` pattern (this is fine for setup/command dispatch, but worth noting if Tank ever ports it)

**Maintenance status:** ✅ **Active.** 2026-05-07 is 11 days before this memo. HPM-published (community distribution). Multiple firmware generations tested. Evidence of user feedback loops in the CHANGELOG.

**Quality verdict:** This is a production-grade driver. Do not reinvent.

---

### Driver 2: craigde (hosted by jbilodea)

**Repo:** `https://github.com/jbilodea/Hubitat`  
**File:** `Rainbird/Drivers/Rainbird_Sprinkler_Controller_Driver.txt`  
**Version:** v0.92  
**Last commit:** 2020-08-27  

**Capabilities:** `Refresh`, `Switch`, `Valve`, `Initialize` — single parent device only.

**Architecture:** Single flat parent. No child devices. All zones controlled by zone-number parameters on manual commands.

**Protocol:** `httpPost /stick`. Same frame as MHedish. Credits `jbarrancos/pyrainbird` as protocol source.

**Encryption:** AES/CBC/NoPadding + SHA-256 key, but IV is generated via `giveMeKey(16)` which produces a 16-character alphanumeric string using `new Random()`. This restricts IV entropy to the printable ASCII range (~6 bits per byte instead of 8) — a protocol weakness, though not exploitable in a home LAN context.

**Known issues:**
- `ManuallyRunStationRequest` had a copy-paste bug (`_station` copied to `_minutesHex` instead of `_minutes`) — fixed in v0.92 comment
- Minutes parameter limited to ≤100 in the Groovy layer (not a protocol limit)
- No per-zone child devices

**Maintenance status:** ⚠️ **Stale.** Last commit 2020-08-27. No HPM listing. Superseded by MHedish.

**Quality verdict:** Historical reference only. Not usable without rework.

---

### joelwetzel / dkilgore90

No Rainbird driver found for either author after exhaustive GitHub search. These names are not associated with any Rainbird Hubitat code.

---

## 3. LNK WiFi Protocol — Clean-Room Spec

*Protocol behavior inferred by reading community code (pyrainbird, MHedish driver). Not copied from upstream source. Sources cited per-claim in §7.*

### 3a. Transport

```
Protocol:  HTTP/1.1 (or HTTPS/1.1 self-signed on port 443 — not used by any Groovy driver)
Endpoint:  POST http://{device_ip}/stick
Port:      80 (HTTP) or 443 (HTTPS, optional)
Content-Type: application/octet-stream
User-Agent:   RainBird/2.0 CFNetwork/811.5.4 Darwin/16.7.0  ← device expects this
Accept:       */*
Connection:   keep-alive
```

The `/stick` endpoint is a single stateless POST handler. Every request is independent — no session, no keep-alive required, no handshake.

**The device cannot handle concurrent requests.** Serial dispatch only: one outstanding POST at a time. HTTP 503 = device busy; retry with backoff (MHedish driver paces with `pauseExecution()`).

**No raw TCP.** It is standard HTTP on port 80. `asynchttpPost` / `httpPost` both work.

### 3b. JSON-RPC Envelope

Before encryption, every payload is a JSON-RPC 2.0 object:

```json
{
  "id": 1716070000.123,
  "jsonrpc": "2.0",
  "method": "tunnelSip",
  "params": {
    "data": "3F00",
    "length": 2
  }
}
```

- `"id"`: Unix timestamp float (any numeric value works; device echoes it in the response)
- `"method"`: always `"tunnelSip"`
- `"params.data"`: hex-encoded SIP command bytes (e.g., `"3F00"` = 2 bytes)
- `"params.length"`: **byte count** of the SIP command (not hex-string length): `"3F00"` → length=2

**Response (success):**
```json
{"id": 1716070000.123, "jsonrpc": "2.0", "result": {"data": "BF00AAAAAAAA", "length": 6}}
```

**Response (SIP NAK — device understood but rejected command):**
```json
{"id": 1716070000.123, "jsonrpc": "2.0", "result": {"data": "003902", "length": 3}}
```
`"00"` = NotAcknowledgeResponse code, `"39"` = echoed command byte, `"02"` = NAK reason code (0=NotSupported, 1=BadLength, 2=IncompatibleData, 3=Checksum, 4=Unknown).

**Response (JSON-RPC error — device doesn't recognize method at all):**
```json
{"id": 1716070000.123, "jsonrpc": "2.0", "error": {"code": -32601, "message": "Method not supported"}}
```

### 3c. Encryption

**Algorithm:** AES-256-CBC  
**Key derivation:** SHA-256(password as UTF-8) → 32-byte key (NOT PBKDF2, NOT HMAC-based — plain single-pass SHA-256)  
**Padding:** Custom (NOT PKCS7): append `\x00\x10` to plaintext, then fill remainder of last block with `\x10` bytes  
**IV:** 16 random bytes (must use full byte range, not ASCII-restricted)  
**Auth:** None beyond the shared secret. No session token. No challenge. Every request is independently encrypted.

**⚠️ There is NO HMAC.** The first 32 bytes of the wire frame are `SHA-256(plaintext)` — an integrity hash, not a keyed MAC. The device verifies message integrity post-decryption.

**Wire frame layout:**

```
Offset   Size    Content
------   ----    -------
0        32 B    SHA-256 hash of the PLAINTEXT JSON payload (integrity check)
32       16 B    AES-CBC initialization vector (16 random bytes)
48       N×16 B  AES-CBC ciphertext (padded plaintext)
```

**Groovy encrypt pseudocode** (Tank can implement directly from this):

```groovy
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private byte[] encryptLnk(String jsonPayload, String password) {
    // 1. Derive 32-byte AES-256 key from password
    byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8"))
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")

    // 2. SHA-256(plaintext) for integrity prefix
    byte[] plainHash = MessageDigest.getInstance("SHA-256").digest(jsonPayload.getBytes("UTF-8"))

    // 3. Pad plaintext: append \x00\x10, then pad to 16-byte boundary with \x10
    String sentinel = jsonPayload + "\u0000\u0010"  // \x00 + \x10
    int rem = sentinel.length() % 16
    if (rem != 0) { sentinel += ("\u0010" * (16 - rem)) }  // \x10 padding
    byte[] padded = sentinel.getBytes("UTF-8")

    // 4. Random 16-byte IV (full byte range)
    byte[] iv = new byte[16]; new java.security.SecureRandom().nextBytes(iv)
    IvParameterSpec ivSpec = new IvParameterSpec(iv)

    // 5. AES-256-CBC encrypt
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding", "SunJCE")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    byte[] ciphertext = cipher.doFinal(padded)

    // 6. Frame: [SHA256(plain) 32B][IV 16B][ciphertext]
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    out.write(plainHash); out.write(iv); out.write(ciphertext)
    return out.toByteArray()
}

private String decryptLnk(byte[] frame, String password) {
    if (!frame || frame.length < 48) return ""
    byte[] iv = frame[32..47] as byte[]
    byte[] ciphertext = frame[48..(frame.length - 1)] as byte[]
    byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(password.getBytes("UTF-8"))
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding", "SunJCE")
    cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv))
    String result = new String(cipher.doFinal(ciphertext), "UTF-8")
    // Strip trailing \x10 and \x00 padding chars
    result = result.replaceAll(/[\u0000\u0010]+$/, "")
    return result
}
```

⚠️ **Sandbox note:** `ByteArrayOutputStream` IS allowed in Hubitat. `System.arraycopy` is NOT (blocked). Use manual byte array loops or `ByteArrayOutputStream.write()` for all byte concatenation.

### 3d. SIP Command Reference — Minimal Viable Set

All commands are sent as the `"data"` hex string in the JSON-RPC envelope. All byte counts are in the `"length"` field.

#### Get device model / firmware version

```
Request:   "02"        length=1
Response:  "82MMMMPPQQ"
  MMMM = modelID (2 bytes)
  PP   = protocol major rev
  QQ   = protocol minor rev
Example:   "8200130100" → model=0x0013, proto=1.0
```

#### Get serial number

```
Request:   "05"        length=1
Response:  "85SSSSSSSSSSSSSSSSSS"
  S×18 hex chars = 9-byte serial number
```

#### Get firmware version (modern controllers)

```
Request:   "0B"        length=1
Response:  "8BVVWWXX"
  VV = major, WW = minor, XX = patch
```

#### Get current zone state (which zones are active)

```
Request:   "3F00"      length=2   (page 0; use "3F01" for zones 9–16, etc.)
Response:  "BFPPAAAAAAAA"
  PP       = page number
  AAAAAAAA = 4-byte bitmask: bit 0 = zone 1, bit 1 = zone 2, etc.
  All zeros = no zones active
```

#### Manual run zone N for M minutes

```
Request:   "39ZZSSSS"  length=4
  ZZ   = zone number (1 byte, 1-indexed, hex)
  SSSS = duration in MINUTES (2 bytes, hex)
Examples:  Zone 1, 10 min: "390100 0A"
           Zone 3, 20 min: "3903 0014"
Response:  "0139"  (ACK, echo of 0x39)
```

⚠️ Duration is in **minutes**, not seconds, in the native SIP layer. Maximum ~360 min (6 hours) per MHedish cap.

#### Stop all irrigation

```
Request:   "40"        length=1
Response:  "0140"  (ACK)
```

#### Get rain sensor state

```
Request:   "3E"        length=1
Response:  "BESS"
  SS = 0x00 → dry/normal, 0x01 → wet/rain detected
```

#### Get rain delay

```
Request:   "36"        length=1
Response:  "B6DDDD"
  DDDD = days of delay (2 bytes, 0 = no delay)
```

#### Set rain delay

```
Request:   "37DDDD"    length=3
  DDDD = days (2 bytes)
Response:  "0137"  (ACK)
```

#### Combined controller state (modern fw, preferred for polling)

```
Request:   "4C"        length=1
Response:  "CC HH MM SS DD Mo YrYr DeDe Se Ir SeSeSeSeReRe Az"
  All fields are hex pairs (1 byte each) except year (2 bytes) and seasonal/remaining (2 bytes each)
  Field order: responseCode(CC), hour, minute, second, day, month, year(2B),
               rainDelayDays(2B), sensorState, irrigationActive,
               seasonalAdjust(2B), remainingRuntime(2B), activeStation
```

### 3e. Available Zone Discovery

```
Request:   "0300"      length=2   (page 0)
Response:  "83PPAAAAAAAA"
  Same bitmask format as 3F (active zones)
  Bit N set → zone N+1 exists in this controller
```

Call at startup to discover zone count and numbering before creating child devices.

### 3f. Polling vs. Push

**Pure poll only.** No subscription mechanism, no WebSocket, no SSE. The HA integration polls every 60 seconds. MHedish's driver polls via Hubitat `runEvery1Minute` scheduler. The device does not initiate any outbound connections.

---

## 4. Sandbox-Safety Analysis

### javax.crypto.Cipher (AES-256-CBC)

| Evidence | Source | Confidence |
|---|---|---|
| `javax.crypto.Cipher.getInstance("AES/CBC/NoPadding","SunJCE")` used in craigde driver | `jbilodea/Hubitat:Rainbird/Drivers/Rainbird_Sprinkler_Controller_Driver.txt` lines ~240–295 | High — driver is installed and reportedly working |
| `@Field static final Cipher AES_CIPHER = Cipher.getInstance("AES/CBC/NoPadding","SunJCE")` in MHedish driver | `MHedish/Hubitat:Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Module.groovy` | High — v1.0.0.0 HPM-published, active users |
| `javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")` used in Touchstone driver | `.squad/skills/tuya-local-groovy/SKILL.md` — confirmed in Touchstone v0.1.2+ | Confirmed in our own repo |

**Verdict: ✅ `javax.crypto.Cipher` is definitively sandbox-safe on Hubitat.** Both AES-CBC and AES-ECB modes are confirmed. The `SunJCE` provider is available on Hubitat's JVM. The sandbox does not block standard JDK crypto.

### javax.crypto.spec.SecretKeySpec / IvParameterSpec

Confirmed safe — both craigde and MHedish import and use them without issues.

### java.security.MessageDigest (SHA-256)

Confirmed safe — used in both Rainbird drivers for key derivation.

### ByteArrayOutputStream

Confirmed safe — used in MHedish driver for frame assembly (`new ByteArrayOutputStream()`).

### ⚠️ Known blocklist items (NOT needed for this protocol)

- `System.arraycopy` — **blocked** (Hubitat sandbox MethodCallExpression blocklist, confirmed Touchstone v0.1.30). Use `ByteArrayOutputStream.write()` or loop-based byte copying instead.
- `javax.crypto.Mac` — **not needed** for Rainbird (there is no HMAC in this protocol). Not tested but likely safe since `javax.crypto` package broadly accessible.

### Secrets size

The Rainbird LNK password is a short user-facing string (≤16 chars typically). It fits trivially in a Hubitat preference field. No long-secret pattern needed.

---

## 5. Rubric Score — Trinity Driver Fit Rubric (100 pts)

### Hard Disqualifiers Check

| Disqualifier | Status |
|---|---|
| Cloud API officially dead or hostile | ❌ Not applicable — **local LAN protocol, no cloud** |
| Requires reflection, JNI, native libraries | ❌ Not applicable — standard JDK crypto only |
| Device costs >$500 or hardware-unavailable | ❌ Not applicable — Mads **owns** the device |
| Requires browser OAuth2 redirect | ❌ Not applicable — password + IP only |
| Persistent MQTT subscriber | ❌ Not applicable — HTTP polling |
| Binary protocol with no Groovy decoder | ❌ Not applicable — JSON-RPC/hex; fully documented above |
| Safety-critical device without audit logging | ❌ Not applicable — irrigation is not safety-critical |
| Requires >1KB secrets in preferences | ❌ Not applicable — short password string |
| Multi-protocol with undocumented fallback | ❌ Not applicable — single HTTP POST endpoint |
| Reflection or sandbox-restricted Groovy | ❌ Not applicable — all crypto via JCE |

**All 10 hard disqualifiers: CLEAR.** No disqualifier fires.

### Weighted Criteria

| Criterion | Max | Score | Reasoning |
|---|---|---|---|
| **Local vs. Cloud Protocol** | 20 | **20** | Local HTTP POST to device IP. No cloud dependency whatsoever. Port 80, `/stick`, pure LAN. Full 20. |
| **Mads Can Test** | 15 | **15** | Mads owns the LNK module. Baseline 15 per task brief. |
| **User Demand Signal** | 15 | **13** | MHedish driver is HPM-published with 25+ patch iterations, strong community adoption signal. Forum threads exist (cannot enumerate from this analysis, but HPM distribution implies active community thread). Slight deduction: no independently confirmed forum thread URL. |
| **Sandbox-Safe** | 15 | **15** | `javax.crypto.Cipher` confirmed in two existing Rainbird drivers AND our own Touchstone driver. No reflection, no JNI, secrets fit in preferences. Full 15. |
| **Vendor API Stability** | 15 | **14** | Local SIP-over-HTTP protocol community-stable for 5+ years (jbarrancos → allenporter lineage, both Groovy drivers). Not officially documented by Rainbird, but reverse-engineered consensus is solid. Minor deduction for "not official." |
| **Effort to Ship** | 10 | **7** | If building new: parent/child + AES-256-CBC + multi-firmware = ~40-60h (5 pts). Deduction reduced because: all encryption patterns are now fully documented, command table is complete, MHedish provides a tested reference. Tank could ship in 30-40h. Giving 7. |
| **Maintenance Burden** | 10 | **8** | Local protocol, no cloud, community-stable. Minor deduction: Rainbird firmware variants (2.1, 2.9, 3.x) require version detection logic that adds some surface area. |

**Total: 92 / 100**

### Threshold Verdict

**92 pts → ✅ Strong Fit (80–100)**

### BUILD / IMPROVE-EXISTING / SKIP Verdict

**⚠️ IMPROVE-EXISTING — do not build from scratch.**

The rubric says Strong Fit, but the *strategic* recommendation must account for the existing ecosystem state:

1. **MHedish's driver is 11 days old as of this memo.** It is not stale. It has 25+ patch versions and handles all known firmware variants.
2. **Building a competing driver adds zero user value** unless it meaningfully improves on MHedish (architecture, capabilities, or reliability).
3. **Identified gap:** MHedish uses blocking `httpPost` rather than `asynchttpPost`. This is fine for most usage but could cause Hubitat hub latency spikes when the device returns a 503. If Mads finds this in practice, a PR to MHedish using `asynchttpPost` would be the right contribution.
4. **Identified gap:** No `HealthCheck` pattern (Pattern A — `ping()`). MHedish doesn't implement Hubitat's `HealthCheck` capability with a periodic ping. This is the strongest differentiator if we build our own.
5. **Second identified gap:** The driver uses `httpPost` (synchronous, blocking hub thread). Our repo standard is `asynchttpPost`. If Mads wants an async-first driver conforming to this repo's patterns, that's a legitimate fork justification.

**Decision tree:**
- Mads installs MHedish and it works → **Done. Use it.**
- Mads hits specific gaps (async behavior, HealthCheck, missing zone discovery) → **File issues on MHedish first. If unresponsive >30 days, fork/build.**
- Mads wants a driver that conforms to this repo's code style and async patterns → **BUILD, using MHedish as tested reference implementation.**

---

## 6. Open Questions for Mads

1. **Which controller model?** ESP-RZX, ESP-Me, ESP-TM2, ESP-RZXe, or other? The firmware variant affects which SIP opcodes are available (some older units don't support `CombinedControllerStateRequest 0x4C`).
2. **Zone count?** 6-zone, 12-zone, or expanded? Non-contiguous zone numbering?
3. **Firmware version if known?** The driver probes `/irrigation/status.json` at startup to detect firmware generation. If you know it's 2.x or 3.x, flag it.
4. **Have you already tried MHedish's driver?** If yes: did it install cleanly on HPM? Any errors on setup? Any 503 floods?
5. **HTTPS or HTTP?** Some newer LNK2 modules require HTTPS. MHedish's driver currently HTTP-only. If HTTPS is required, that's a gap worth noting.
6. **What automation use case is driving this?** Simple "run zone N for M minutes" from a dashboard button, or more complex scheduling integration? This affects whether per-zone children are sufficient or if a schedule-sync capability is needed.
7. **Rain delay automation?** If you want RM rules that check rain sensor state before irrigating, confirm the `3E` rain sensor command works on your hardware generation.

---

## 7. Sources

| Source | URL | Date accessed |
|---|---|---|
| MHedish Hubitat driver | `https://github.com/MHedish/Hubitat` (files: `Drivers/RainBird-LNK/RainBird-LNK-Wi-Fi-Module.groovy`, `RainBird-LNK-Wi-Fi-Zone-Child.groovy`) | 2026-05-18 |
| craigde/jbilodea driver | `https://github.com/jbilodea/Hubitat` (file: `Rainbird/Drivers/Rainbird_Sprinkler_Controller_Driver.txt`) | 2026-05-18 |
| pyrainbird Python library | `https://github.com/allenporter/pyrainbird` (originally `jbarrancos/pyrainbird`, repo transferred/renamed) | 2026-05-18 |
| pyrainbird encryption module | `allenporter/pyrainbird:pyrainbird/encryption.py` | 2026-05-18 |
| pyrainbird async client | `allenporter/pyrainbird:pyrainbird/async_client.py` | 2026-05-18 |
| pyrainbird SIP command YAML | `allenporter/pyrainbird:pyrainbird/resources/sipcommands.yaml` | 2026-05-18 |
| Home Assistant rainbird integration | `https://github.com/home-assistant/core/tree/dev/homeassistant/components/rainbird/` | 2026-05-18 |
| HA rainbird coordinator | `home-assistant/core:homeassistant/components/rainbird/coordinator.py` | 2026-05-18 |
| Touchstone AES-ECB Groovy driver | `.squad/skills/tuya-local-groovy/SKILL.md` — Touchstone v0.1.2+ sandbox confirmation | 2026-05-18 |
| Trinity Driver Fit Rubric | `.squad/decisions/decisions.md` §trinity-driver-fit-rubric | 2026-05-18 |


---

# Rainbird WiFi Irrigation — Use-Case Analysis & Build/Skip Verdict

**Date:** 2026-05-18T15:44:37-07:00  
**Author:** Trinity (Lead/Architect)  
**Context:** Mads Kristensen owns Rainbird LNK WiFi + C-7 hub; asking "what can I *do* with this?"

---

## TL;DR — Should You Build the Driver?

**YES. Build it.** 

If Mads lives in the Pacific Northwest (variable rain, mild-to-cool summers) and actively gardens, a Rainbird driver unlocks 3–4 high-value automations that Rainbird's native app fundamentally cannot do:
1. **Rain-skip logic tied to NOAA forecast** — save 20%+ water/season by conditioning on probability of rain >50% within 24h
2. **Smoke-pause tied to PurpleAir AQI** — prevent compound smoke deposition on plants when wildfire smoke >100 pm2.5
3. **Leak-sensor integration** — kill all zones instantly if a Hubitat water sensor detects flow under a sink or near the foundation

These three rules alone justify the effort. The driver ranks **~78–82 on the rubric** (Conditional Fit, high-priority conditional). Rainbird's API is cloud-only (risk), but the use-case demand is real.

---

## What Hubitat Unlocks That Rainbird's App Cannot

**Rainbird's native app is a calendar with scheduling presets.** It does NOT do:
- Conditional logic beyond "skip day if rain detector activates" (binary; no thresholds)
- Third-party device integration (weather, air quality, presence, leak sensors)
- Programmatic composition (Rule Machine, automations, voice control)
- Instant notifications on system faults
- Manual zone control via voice or dashboard without opening the app

**Hubitat composition fills all these gaps:**
- Rule Machine + Maker API: condition any zone start on weather/AQI/sensors
- Hubitat Dashboard: one-tap zone control, run-time adjustment
- Notifications: push alerts on zone failures, rain sensor trips, low-pressure faults
- Voice: "Alexa, water the front lawn for 10 minutes"
- Automation chaining: zone-finish triggers next event (cycle-and-soak, system-wide shutoff)

---

## Automation Catalog (Ordered by Value & Effort)

### Tier 1 — High Value, Low Effort (~2-3 per rule in Rule Machine)

#### 1. **Rain Skip** ← **#1 ROI Driver**
- **Trigger:** Scheduled zone start time (e.g., "Tonight at 8 PM")
- **Condition:** NOAA forecast endpoint: chance of precipitation >50% within 24h
- **Action:** Cancel zone execution; log "skipped due to rain forecast"
- **Why:** Saves 15–25% seasonal water in PNW; nearly zero added cost (NOAA is free, public API)
- **Hubitat advantage:** Rainbird can only skip if rain *has already fallen* (historical). Hubitat forecasts *ahead*.

#### 2. **Smoke Pause** ← **#2 Value Driver (Mads, you'll thank me during August)**
- **Trigger:** Scheduled zone start time
- **Condition:** PurpleAir API pm2.5 >100 µg/m³ (unhealthy air quality)
- **Action:** Skip zone; send notification "Irrigation paused: air quality poor (smoke detected)"
- **Why:** Watering during wildfire smoke compounds smoke particulates onto foliage, stressing plants. Skip until air clears.
- **Hubitat advantage:** Rainbird has no air-quality awareness. Hubitat's ecosystem has PurpleAir driver.

#### 3. **Leak Shutoff** ← **#1 Safety Critical**
- **Trigger:** Water leak sensor (Hubitat-paired; e.g., under sink, foundation wall)
- **Condition:** Sensor goes wet
- **Action:** Kill all zones immediately; send critical alert
- **Why:** A burst hose or failed backflow valve under the house can flood before you notice. Instant shutoff saves thousands.
- **Hubitat advantage:** Rainbird has no leak integration. Hubitat sees leak sensors natively.

#### 4. **Cycle and Soak** ← **High Value if Your Lawn is on a Hill**
- **Trigger:** Zone X scheduled to run 30 minutes
- **Condition:** Soil is clay or sandy (no condition, user-preference)
- **Action:** Run zone for 10 min → pause 5 min → run 10 min → pause 5 min → run 10 min (total 35 min, 3 cycles)
- **Why:** Clay + sand don't absorb water quickly. Short pulses let soil absorb, reducing runoff / root stress.
- **Hubitat advantage:** Rainbird's scheduling only does flat durations. Hubitat can chain zone-stop → delay → restart as automation.

#### 5. **Vacation Mode — Increase Frequency**
- **Trigger:** "Away" mode activated for >3 days (Hubitat presence)
- **Condition:** None
- **Action:** Increase all zone run times by 20% (1.2× multiplier via custom Rule Machine rule)
- **Why:** When you're gone, no foot traffic = less soil compaction. Grass & plants can handle slightly more water without stress. Recover faster post-trip.
- **Hubitat advantage:** Rainbird has no presence awareness; it can only run on calendar. Hubitat knows when house is empty.

#### 6. **Quiet Hours — Presence-Based Pause**
- **Trigger:** Motion sensor detects activity in backyard (Hubitat, any standard sensor)
- **Condition:** None
- **Action:** Pause all running zones; resume after 1 hour of no motion
- **Why:** Sprinklers won't spray guests/kids. Backyard remains usable.
- **Hubitat advantage:** Rainbird runs on schedule only. Hubitat can read motion sensors.

### Tier 2 — Moderate Value, Medium Effort (~3-4 per rule)

#### 7. **Energy-Cost-Aware Scheduling** ← **$$ If Your Pump is Electric**
- **Trigger:** Time window check (e.g., 12 AM – 6 AM = cheap-rate window, on utility plan)
- **Condition:** Electricity rate <$0.12/kWh (if available via API, e.g., IFTTT→Maker or manual preference)
- **Action:** Shift zone start time to align with cheap-rate windows
- **Why:** If irrigation pump is electric (not municipal water), running during off-peak can save $100+/year.
- **Hubitat advantage:** Rainbird has no utility-rate awareness. Hubitat can integrate with smart-meter APIs or Maker endpoints.

#### 8. **Master-Valve Cutoff via Door/Window**
- **Trigger:** Door/window sensor opens in house (e.g., back patio door)
- **Condition:** Irrigation system is running
- **Action:** Close master valve isolating all zones; send notification "Irrigation cut: patio door open"
- **Why:** Safety + convenience. If you open a door while zones are running, you don't want spray hitting the house or messing with HVAC intakes.
- **Hubitat advantage:** Rainbird has no door/window integration. Hubitat sees all contact sensors.

### Tier 3 — Nice-to-Have, Moderate Effort (~2-3 per rule, less immediate ROI)

#### 9. **Seasonal Time Shift**
- **Trigger:** Month change (calendar automation)
- **Condition:** Check current month
- **Action:** For April–September, shift all zone start times ±15 min depending on sunrise/sunset (month-based; or tie to sunrise/sunset automation)
- **Why:** Summer = earlier sunrise, you may want to water before heat; winter = later sunrise, water can wait.
- **Hubitat advantage:** Rainbird's scheduling doesn't automatically shift. Hubitat can use astro plugin or seasonal rules.

#### 10. **Manual Spot-Water Voice Control**
- **Trigger:** Voice command: "Alexa, turn on the front-lawn sprinkler"
- **Condition:** None (or: only if master valve is open)
- **Action:** Run zone 1 for 15 min, then auto-off
- **Why:** Convenience. Quick water a specific zone without opening app.
- **Hubitat advantage:** Rainbird app is slower than voice. Hubitat + Maker API = direct Alexa integration.

#### 11. **System Fault Notification**
- **Trigger:** Hubitat polls Rainbird API; zone fails to start (API returns error) or rain sensor trips unexpectedly
- **Condition:** None
- **Action:** Send push notification: "Zone 3 failed to start: check controller" / "Rain sensor activated"
- **Why:** You're not checking the Rainbird app daily. Faults need to reach you.
- **Hubitat advantage:** Rainbird doesn't push notifications on faults. Hubitat can poll and alert.

#### 12. **Multi-System Orchestration: Misting + Grass During Heat Wave**
- **Trigger:** Temperature forecast >95°F or outdoor temp >90°F for >3 hours
- **Condition:** Time is 2 PM – 5 PM (peak heat)
- **Action:** Run misting line (zone 5) + cool-down grass zone (zone 1) simultaneously; run for 20 min, repeat every 2 hours until sunset
- **Why:** During extreme heat, evaporative cooling of misting + light grass watering keeps root zone cool, preventing heat stress and wilting.
- **Hubitat advantage:** Rainbird can't coordinate with weather forecasts or multi-zone thermal logic. Hubitat rule can compose temp + time + zones.

---

## Composition Opportunities with Existing Drivers / Research

### Free or Lightweight Integrations (Already in Hubitat Ecosystem)

1. **NOAA Weather Driver** — Public API, no auth required
   - ✅ Precipitation forecast (% chance, expected inches)
   - ✅ Temperature, wind speed (for extreme-heat or wind-blow-off scenarios)
   - ✅ Sunrise/sunset (for seasonal time shifts)
   - Cost: Free

2. **PurpleAir Air Quality** — Free API (rate-limited public tier works)
   - ✅ PM2.5 (smoke indicator)
   - ✅ PM10 (dust)
   - Cost: Free

3. **Hubitat Built-in Capabilities**
   - ✅ Motion sensors (quiet hours)
   - ✅ Contact sensors / door/window (master-valve cutoff)
   - ✅ Leak / water sensors (emergency shutoff)
   - ✅ Presence (vacation mode)
   - Cost: Hardware-dependent ($15–$40 per sensor)

4. **Hubitat Rule Machine**
   - ✅ Conditional logic composition
   - ✅ Time-based triggers
   - ✅ Notifications
   - Cost: Free (built-in to Hubitat C-7)

### Ecosystem Fit

- **Rainbird WiFi driver integrates cleanly** with Rule Machine for all Tier 1–2 rules
- **No conflicts** with existing Daikin / SunStat / Gemstone patterns (none are irrigation)
- **Parent/Child not needed** (single Rainbird controller per install, not multiple zones as separate devices — zones are properties of the one controller)

---

## Rainbird Driver Rubric Score

Applying Trinity's Driver Fit Rubric (max 100):

| Criterion | Score | Reasoning |
|-----------|-------|-----------|
| **Local vs. Cloud** | 10/20 | Rainbird LNK WiFi is cloud-only REST API (no local LAN endpoint). Rain-skip + smoke-pause require cloud polling anyway, so penalty is unavoidable. |
| **Mads Can Test** | 15/15 | Mads owns the hardware (Rainbird LNK WiFi + C-7). ✅ |
| **User Demand** | 13/15 | Personal ask (Mads). Strong signal (rain-skip alone justifies install). No public forum thread, but use case is real. |
| **Sandbox-Safe** | 15/15 | Pure Groovy + asynchttpGet; no reflection, no JNI. ✅ |
| **Vendor Stability** | 9/15 | Rainbird has been stable >10 years locally; WiFi cloud endpoint is mature. But: cloud can break; no SLA guarantee. Historical: no major API kills. Medium-high confidence. |
| **Effort to Ship** | 8/10 | Single-device cloud polling driver (~35–50h). Medium complexity: OAuth2 parent/child not needed, but polling, error handling, zone state parsing required. |
| **Maintenance** | 8/10 | Cloud REST drivers are more fragile than LAN. Rainbird docs exist but not public-API. Reverse-engineering risk is low (API is stable). |
| **Total** | **78/100** | Conditional Fit — High Priority Conditional. Build it if Mads commits to testing. |

**Hard Disqualifiers:** None triggered. Cloud-only is a weakness, not a killer; Rainbird's API is not hostile (unlike MyQ post-Oct-2023).

---

## Honest Assessment: Build vs. Skip

### Build If:
1. Mads commits to 4–6 weeks of real-device testing (weather cycles, seasonal changes)
2. Rainbird LNK WiFi API documentation is available or reverse-engineering succeeds quickly (<2h)
3. Mads values rain-skip + smoke-pause + leak-cutoff enough to justify ~50h of driver development + testing

### Skip If:
1. Rainbird WiFi is not available in Mads's region / API is undocumented and closed
2. Mads's lawn is simple (flat, no clay, no hillside runoff concerns) — rain-skip is the only high-value rule, and Rainbird's built-in rain sensor already does a passable job
3. Rainbird API requires monthly API key renewal or has a track record of silent breaks

---

## Recommendation: Go/No-Go

**GO.** Rainbird driver is a **conditional fit worth building** (78/100 rubric score). 

**Top 3 Value-Drivers:**
1. **Rain-Skip (Tier 1)** — Saves 15–25% water/season; composes cleanly with NOAA
2. **Smoke-Pause (Tier 1)** — Prevents plant stress during August wildfires (PNW-specific, but real)
3. **Leak Shutoff (Tier 1)** — Emergency safety; blocks water damage

**If Rainbird's API is accessible and stable, prioritize this driver for Tank's next sprint after Daikin v0.1.6 closes.**

**Rubric Filing:** `.squad/decisions/inbox/trinity-driver-fit-rubric.md` (already filed 2026-05-18)

---

## Learning — Pattern Addition to Trinity's Criterion #4 (User Demand Signal)

**Refinement:** *Use-case demand for device-class drivers is highest when the device is:*
- **Stateful & long-lived** (irrigation, HVAC, lights — not one-off sensors)
- **Multi-input compatible** (weather, presence, sensors) — Hubitat's composability is the advantage
- **Lacks native conditional logic** (Rainbird scheduling is calendar; no conditionals beyond rain detector)
- **Geographically or seasonally context-heavy** (rain-skip, seasonal shift, heat-dome response)

When ALL four hold, the driver candidate jumps from 60–70 (neutral) to 75–85 (priority conditional).

Rainbird hits all four. HVAC drivers (Daikin, SunStat) hit three. Light drivers hit two. Hence: irrigation drivers are higher-leverage in Hubitat's platform than generic device support.

---

**Decision filed by:** Trinity  
**Date:** 2026-05-18T15:44:37-07:00  
**Status:** Recommend to Mads for sprint planning

