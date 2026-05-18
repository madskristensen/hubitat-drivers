# Daikin BRP069B API + Driver Perf/Quality Audit
**Author:** Cypher  
**Date:** 2026-05-18  
**Driver version audited:** 0.1.5  
**Scope:** Daikin BRP069B local HTTP API completeness + driver internals audit

---

## 1. Daikin BRP069B API Surface — Untouched Endpoints

**References:** ael-code/daikin-control README (BRP069A41–B41, fw 1.4.3–3.3.1), Apollon77/daikin-controller src/DaikinACRequest.ts + DaikinAC.ts (v2.2.1, 2025-05-24). These are the most comprehensive community sources for the local HTTP API.

| Endpoint | Purpose | Our Status | Verdict | Notes |
|---|---|---|---|---|
| `GET /aircon/get_control_info` | Mode, setpoint, fan rate, swing | ✅ Called | — | Core poll |
| `POST /aircon/set_control_info` | All control writes | ✅ Called | — | Core write |
| `GET /aircon/get_sensor_info` | Indoor/outdoor temp, humidity | ✅ Called | — | Core poll |
| `GET /aircon/get_model_info` | Model name, fw, humidity/swing caps | ✅ Called (init) | — | Capability cache |
| `GET /aircon/get_special_mode` | Econo / Powerful mode | ✅ Called | — | Per-refresh |
| `POST /aircon/set_special_mode` | Write Econo / Powerful mode | ✅ Called | — | Per-command |
| `GET /aircon/get_week_power_ex` | Weekly kWh (today + 6 days) | ✅ Called | — | 30-min energy poll |
| `GET /aircon/get_year_power_ex` | Monthly kWh (12 months) | ✅ Called | — | 30-min energy poll |
| **`GET /common/basic_info`** | MAC, fw version, device name, `lpwFlag` | ❌ Not called | 🟢 Worth adopting | `lpwFlag=1` requires `lpw=` param on ALL set calls — some BRP069A-series units; also provides MAC for device label. Apollon77 calls this at init and sets lpw param globally. |
| **`GET /aircon/get_demand_control`** | Max-power cap / demand-response limit | ❌ Not called | 🟡 Maybe later | Endpoint confirmed in Apollon77 v2.2.1 (2025-05-24). Useful for utility demand-response or solar self-consumption. Returns max power percentage. Requires device-side validation on BRP069B41. |
| **`POST /aircon/set_demand_control`** | Write power cap | ❌ Not called | 🟡 Maybe later | Companion write to above. Need read-only first. |
| **`GET /common/get_notify`** | Filter maintenance alert, error codes | ❌ Not called | 🟡 Maybe later | Listed in ael-code docs; Apollon77 marks as "not implemented." Community reports filter-cleaning alert lives here. Would power a Hubitat notification. Polling overhead: +1 call/cycle if added. |
| **`GET /aircon/get_week_power`** | Weekly runtime minutes (older format) | ❌ Not called | 🟡 Maybe later | Non-`_ex` variant; older BRP069A firmware only. Useful as a fallback for older units that return 404 on `_ex` endpoints. Low priority given BRP069B focus. |
| **`GET /aircon/get_year_power`** | Yearly runtime (older format) | ❌ Not called | 🟡 Maybe later | Same as above — older firmware fallback. |
| **`GET /aircon/get_day_power_ex`** | Hourly kWh breakdown for today | ❌ Not called | 🟡 Maybe later | Listed in Apollon77 TODO as `get_day_paower_ex` (typo in their docs, likely `get_day_power_ex`). Provides per-hour energy for today. Would enable hourly energy dashboards. Low demand from users currently. |
| `GET /common/get_remote_method` | Polling interval negotiation | ❌ Not called | 🔴 Skip | Controls cloud polling interval. No value for LAN-only Hubitat driver. Apollon77 documents it but never uses it in polling logic. |
| `POST /common/set_remote_method` | Set cloud polling interval | ❌ Not called | 🔴 Skip | Cloud-facing API — irrelevant for local LAN use. |
| `GET /aircon/get_program` | On-device weekly schedule (read) | ❌ Not called | 🔴 Skip | Deliberately deferred (Trinity v0.1.0 memo: Hubitat rules cover scheduling). Poorly documented; community implementations are fragile. |
| `POST /aircon/set_program` | Write on-device schedule | ❌ Not called | 🔴 Skip | Same rationale. |
| `GET /aircon/get_scdltimer` | On/off weekly timer (read) | ❌ Not called | 🔴 Skip | Same rationale as get_program. |
| `POST /aircon/set_scdltimer` | Write on/off weekly timer | ❌ Not called | 🔴 Skip | Same. |
| `GET /aircon/get_scdltimer_info` | Timer metadata | ❌ Not called | 🔴 Skip | Apollon77 TODO. Barely documented. |
| `GET /aircon/get_timer` | One-shot on/off timer | ❌ Not called | 🔴 Skip | Poorly documented; only ael-code lists it with "?" description. |
| `GET /aircon/get_target` | Target mode (?) | ❌ Not called | 🔴 Skip | Unknown purpose; ael-code lists with "?". Community has not reverse-engineered it. |
| `GET /aircon/get_price` | Energy pricing (?) | ❌ Not called | 🔴 Skip | ael-code "?" — no known use. |
| `POST /common/set_led` | Toggle adapter LED | ❌ Not called | 🔴 Skip | ael-code notes it doesn't actually change the LED on tested hardware. |
| `POST /common/reboot` | Reboot WiFi adapter | ❌ Not called | 🔴 Skip | Dangerous — would disconnect the adapter for ~30s. No legitimate Hubitat use case. |
| `POST /common/set_regioncode` | Set region (cloud setting) | ❌ Not called | 🔴 Skip | Cloud-facing. Irrelevant for local LAN. |
| `GET /common/get_datetime` | Adapter date/time | ❌ Not called | 🔴 Skip | Apollon77 TODO. No user-visible value in a Hubitat driver. |
| `GET /aircon/get_wifi_setting` | WiFi SSID/config | ❌ Not called | 🔴 Skip | Diagnostics only; exposes credentials risk per ael-code security note. Do not expose. |

**⚠️ `lpwFlag` compatibility note:**  
Apollon77's library reads `lpwFlag` from `/common/basic_info` at init. If `lpwFlag=1`, it appends `lpw=` to all subsequent requests. Without this, `set_control_info` calls may be silently ignored on certain units. The BRP069B4x series (Mads's device) doesn't appear to require this, but it's a cross-device compatibility gap for users with older BRP069A units.

---

## 2. Performance Findings

All findings cite line numbers in `drivers/daikin-wifi/daikin-wifi.groovy` v0.1.5.

| # | Finding | Lines | Bucket |
|---|---|---|---|
| P1 | **`state.lastActivityEmittedAt` written every poll** — `emitLastActivity()` writes to `state` on every successful response when >60s have elapsed. At 5-min refresh cadence, this is ~12 state writes/hour. By design (HealthCheck throttle pattern) but unavoidable. Not a bug. | 762 | 🟢 Fine as-is |
| P2 | **`refreshEnergy()` doesn't skip when device is off** — `get_week_power_ex` and `get_year_power_ex` fire every 30 min regardless of power state. The device still responds correctly but it's 2 unnecessary HTTP calls per cycle when off. | 385–394 | 🟡 Worth fixing |
| P3 | **`state.modelInfo` is only written in `initialize()`** — not on hot path. Clean. | 508–515 | 🟢 Fine as-is |
| P4 | **`unschedule()` correctly called before `registerSchedules()`** in `initialize()`. No schedule duplication risk. | 205 | 🟢 Fine as-is |
| P5 | **All HTTP via `asynchttpGet` with 10s timeout** — no synchronous calls found. | 464–474 | 🟢 Fine as-is |
| P6 | **`handleYearPower` computes `yearTotal` but never emits it** — the sum is computed, stored in a local variable, and debug-logged only. The work is done but the result goes nowhere. Not a runtime cost issue, but dead computation. | 667–668 | 🟡 Worth fixing |

---

## 3. Code Quality Findings

| # | Finding | Lines | Bucket |
|---|---|---|---|
| Q1 | **Missing null guard on `setHeatingSetpoint` / `setCoolingSetpoint`** — `new BigDecimal(temp.toString())` will throw `NullPointerException` if `temp` is null (e.g., Rule Machine passes null on clear). Should be guarded: `if (temp == null) { log.warn "..."; return }` before the BigDecimal construction. | 340, 350 | 🔴 Hot-fix |
| Q2 | **Missing `${kv.ret}` in two warn messages** — both `parseModelInfo` (line 492) and `handleSetSpecialMode` (line 690) log `"ret="` without interpolating the actual value. Makes debugging harder when these warnings fire. | 492, 690 | 🟡 Worth fixing |
| Q3 | **`SUPPORTED_FAN_MODES` excludes "circulate"** — `fanCirculate()` is implemented (delegates to "auto") but `supportedThermostatFanModes` only lists `["auto", "on"]`. Rule Machine won't show "circulate" as an option. Debatable whether to add it; Hubitat's standard is inconsistent across thermostats. | 61 | 🟡 Worth fixing |
| Q4 | **No dead code found** — all methods are either capability stubs (required by Hubitat), active command handlers, or documented safety nets (e.g., `parse()`). | — | 🟢 Fine as-is |
| Q5 | **All magic numbers use `@Field static final`** — `DAIKIN_PORT`, `LAST_ACTIVITY_THROTTLE_MS`, `ENERGY_POLL_MINUTES`, `PING_TIMEOUT_SECONDS`. Clean. | 34–37 | 🟢 Fine as-is |
| Q6 | **No `displayed: false` or `isStateChange: true` found anywhere** — Tank-6's hygiene audit confirmed. | — | 🟢 Fine as-is |
| Q7 | **All log.debug gated by `logEnable`, all log.trace gated by `traceEnable`** — verified across all private log helpers and all call sites. | 240–241 | 🟢 Fine as-is |
| Q8 | **Sandbox clean** — no `HubAction`, no `System.arraycopy`, no `java.util.zip`, no reflection. | — | 🟢 Fine as-is |
| Q9 | **All numeric parses guarded with `.isNumber()`** — `htemp`, `otemp`, `hhum`, `stemp`, energy day/month arrays. Verified. | 578, 608, 615, 623, 647 | 🟢 Fine as-is |
| Q10 | **Potential race: setpoint write → stale poll read-back** — `setHeatingSetpoint` writes to device, `handleSetControlInfo` schedules a read-back 2s later. However, the regular poll schedule could fire between write and read-back and emit the stale (pre-write) setpoint from the device temporarily, then the read-back corrects it. Two rapid events for the same attribute. Very low severity; `emitIfChanged` limits actual event chatter. | 681 | 🟢 Fine as-is (low severity) |
| Q11 | **`emitLastActivity()` is a multi-concern method** — it emits `lastActivity`, manages `healthStatus` online promotion, AND clears `pingPending` state. Readable enough for this driver size, but worth noting if the method grows. | 755–774 | 🟢 Fine as-is |
| Q12 | **Temperature °F↔°C conversion correct** — `temperatureToC()` and `temperatureFromC()` both path through `location.temperatureScale`. `clampSetpoint()` applies scale-correct ranges. | 780–801 | 🟢 Fine as-is |
| Q13 | **Energy timestamps use hub local timezone** — `new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")` in `emitLastActivity()` uses JVM default timezone (= hub TZ). No UTC/local mismatch. | 763 | 🟢 Fine as-is |

---

## 4. Top 5 Improvements (ranked by value × ease)

| Rank | Improvement | Effort | Risk | Value |
|---|---|---|---|---|
| 1 | **Q1: Null guard on `setHeatingSetpoint` / `setCoolingSetpoint`** — add `if (temp == null) { log.warn "..."; return }` before BigDecimal construction. Prevents NPE crash when Rule Machine or automations pass null. | ~30 min | Zero | High — crash prevention |
| 2 | **Q2: Fix missing `${kv.ret}` interpolation in 2 warn messages** — trivial string fix at lines 492 and 690. Makes production log triage faster. | ~5 min | Zero | Low-medium — debug quality |
| 3 | **P2: Skip energy poll when powered off** — add `if (device.currentValue("switch") == "off") { debugLog "refreshEnergy: unit off — skipping"; return }` at top of `refreshEnergy()`. Reduces 2 HTTP calls per 30 min cycle when off. | ~5 min | Zero | Low — minor perf |
| 4 | **P6: Emit or drop `yearTotal` in `handleYearPower`** — either emit it as a new `energyYear` attribute (useful for dashboards) or remove the computation. Current state wastes cycles without benefit. | ~30 min | Low | Medium — either closes a UX gap or eliminates dead code |
| 5 | **Add `/common/basic_info` call in `initialize()`** — read `lpwFlag` for BRP069A backward compat + cache device name for label. If `lpwFlag=1` detected, append `lpw=` to all subsequent request URLs. Widens device compatibility without breaking existing BRP069B installs. | ~1–2h | Low (flag-gated) | Medium — cross-device compat |

**Not in top 5 but worth mentioning:**  
`/aircon/get_demand_control` read-only support (v0.1.7 candidate) — confirmed in Apollon77 v2.2.1 (2025-05-24). Useful for demand-response households. Requires real-device validation on BRP069B41 before shipping.

---

## 5. Cypher's Recommendation

**v0.1.6 should be a tight quality release**: ship all four of the quick-win items (Q1 null guard, Q2 log fix, P2 energy-skip, P6 yearTotal resolution) in a single commit — none of these touch any user-visible behavior and all have zero rollback risk. Add the `/common/basic_info` call only if Tank is confident Mads can test the `lpwFlag` path; if not, park it for v0.1.7 alongside demand control. The driver is genuinely in good shape — v0.1.4/v0.1.5 cleaned up the major issues and the remaining findings are maintenance-tier, not correctness bugs. The only finding that could cause a visible crash in production is the null-setpoint issue (Q1), and that should be treated as the blocking item for v0.1.6.

---

*Sources: ael-code/daikin-control README (MIT), Apollon77/daikin-controller src/DaikinACRequest.ts + DaikinAC.ts (v2.2.1, 2025-05-24, MIT), Home Assistant daikin component __init__.py (Apache 2.0).*
