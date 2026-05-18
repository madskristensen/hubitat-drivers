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
