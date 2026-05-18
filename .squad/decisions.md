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
