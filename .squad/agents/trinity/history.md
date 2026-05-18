# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation home automation hubs.
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Recent Projects & Decisions

**For detailed learnings from prior sessions (Gemstone, SunStat v0.1.0–v0.1.2, Bosch), see history-archive.md.**

### 2026-05-17T03:37:53Z — SunStat Connect Plus v0.1.2 released

6 new features: EnergyMeter, schedule control, thermostat hold, outdoor sensor, setpoint rounding. Tank wired all. Switch added 23 test cases. Link bumped manifests/READMEs. Awaiting Mads' real-device verification.

### 2026-05-17T09:53:47-07:00: Touchstone LED Fireplace — architecture design

**Driver:** Touchstone LED Fireplace (Tuya WiFi, local LAN control)

**Architectural call:** Single driver, Tuya Local (LAN) over rawSocket TCP + AES. Folder shape: drivers/touchstone-fireplace/touchstone-fireplace.groovy.

**Initial capability mapping:**
- Switch + SwitchLevel + ColorControl (flame color in HSV)
- Refresh + Initialize
- Custom attribute logColor + custom command setLogColor(hex)

**Protocol:** Tuya Local TCP 6668 + AES-128-ECB via interfaces.rawSocket. Borrow protocol layer from kkossev community drivers.

**Effort:** Medium (2–3 sessions). Similar to Gemstone once protocol layer is confirmed.

**Key gates before implementation:**
1. Cypher confirms tinytuya can extract localKey without Tuya IoT developer account
2. Mads runs tinytuya scan; shares DP map
3. Cypher maps DPs to capabilities + confirms protocol version

**Risk flagged:** Local-key extraction barrier may block (same category as Bosch's developer account). Must verify before code.

### 2026-05-17T16:53:47Z — Touchstone Tuya Feasibility (Cypher + Trinity)

**Feasibility verdict: Yes-with-caveats.** Full DP map confirmed from make-all/tuya-local reference implementation. Protocol v3.3 feasible in Hubitat sandbox.

⚠️ **CRITICAL CORRECTION — ColorControl is INCORRECT; use named custom commands:**

Cypher's DP analysis reveals flame and ember colors are **named palette indices** (6 flame effects, 12 log colors), **not free-form RGB/HSV**. ColorControl with HSV input produces confusing rounding when mapping to palette.

**Corrected capability mapping for Tank:**
- Switch (DP 1)
- SwitchLevel (DP 102, map 0–100 → "1"–"5")
- **Custom command setFlameColor(name)** (DP 101, 6 palette options)
- **Custom command setLogColor(name)** (DP 104, 12 palette options)
- **Custom command setLogBrightness(level)** (DP 105)
- **Custom command setFlameSpeed(speed)** (DP 103, Slow/Medium/Fast)
- Refresh, Initialize

**Local key extraction:** No-account path available via SmartLife credentials + HA tuya-local (~5 min), or fallback 	inytuya wizard with free dev account (~20 min one-time).

**Next steps:**
- Tank: Scaffold driver with corrected named-command approach (NOT ColorControl). Borrow Tuya Local protocol layer from kkossev.
- Switch: Plan real-device validation (tinytuya scan, confirm model/version/DP IDs, connectivity test).
- Link: Document one-time local-key extraction in README (both methods).

See .squad/decisions.md (merged entries) and .squad/orchestration-log/2026-05-17T165347Z-{cypher,trinity}.md for full context.

---

## Core Patterns (Reusable)

1. **Parent/Child OAuth:**
   - Parent holds state.accessToken, state.refreshToken, state.tokenExpiresAt
   - getValidToken() guard before every HTTP call; refresh if now > expiresAt - 300
   - Children store cloud device IDs as DataValue("cloudDeviceId")
   - isComponent: false on child creation
   - Parent calls child.parseDeviceState(body) after each poll

2. **API Response Handling:**
   - Check response.getErrorData() for 4xx responses (getData() is null)
   - Unwrap envelopes in dedicated helpers (parseResponseBody(), parseResponseList())
   - Log diagnostic details (body type, sample data) on every response path
   - Never replace server-assigned IDs with synthetic strings

3. **Capability Metadata:**
   - Use distinct command names to avoid WebCoRE overload shadowing
   - Every sendEvent needs descriptionText
   - Standard capabilities + custom attributes for non-standard state
   - Emit optimistic updates before API calls

4. **State & Preference Hygiene:**
   - Tokens >1KB: use command parameter not preference (preference limit ~1024 chars)
   - Transient working data: local variables, not state.*
   - Persistent caches: favorites-only; on-demand non-favorites
   - Log bounds even at debug level for collections

---

## 2026-05-18 Learnings Summary

### Cross-driver improvements (2026-05-17)
- 4-way review with Tank, Cypher, Switch identified 8 anti-patterns across Gemstone, SunStat, Touchstone (undeclared `capability "Polling"`, missing `Sensor` capability, redundant event emission, packaging gaps, raw DP attributes missing named commands, parent/child version skew, colorMode as string not enum).
- Utility method duplication documented — recommendation: create `.squad/templates/driver-utilities.groovy` canonical source.

### Write-idempotency patterns (2026-05-18)
- Lifecycle-driven writes (power-on defaults, timer-expiry) are highest risk. User-explicit commands lower priority.
- `emitIfChanged` gates events; skip-if-match guards writes — both required, solve different problems.
- State-assertion paths (e.g., boost recovery, cloud-drift defense) should NOT be guarded — by-design non-idempotent.
- Tuya fireplace DP writes produce audible click → guard `on()` with `if (device.currentValue("switch") == "on") return`.

### Daikin capability gap analysis (2026-05-18)

**Daikin BRP069B API surface (reusable notes):**
- Local LAN HTTP on port 80, no auth required. BRP069B (not C) series only.
- Six useful read endpoints: `get_control_info` (power/mode/setpoint/fan), `get_sensor_info` (indoor temp, outdoor temp, optional humidity), `get_model_info` (per-unit capability flags), `get_week_power_ex` / `get_year_power_ex` (energy history), `get_special_mode` (econo/powerful flags).
- Two write endpoints beyond `set_control_info`: `set_special_mode` (econo/powerful) and `set_program` (on-device timer — rarely useful alongside Hubitat rules).
- Energy fields (`week_heat`, `week_cool`, `curr_year_heat`, etc.) are in tenths of kWh, slash-delimited arrays. Parse with `.split('/')`.
- Outdoor temp `otemp` and indoor temp `htemp` return `"-"` when the sensor is unavailable — must guard before `Double.parseDouble()`. (Bug confirmed by Cypher's concurrent analysis.)

**Top capability gaps observed (pattern, not specific count):**
- `supportedThermostatModes` is the single most common schema gap in SmartThings-era Hubitat thermostat ports. It's never declared or emitted; Rule Machine and modern dashboards break silently without it. Always check this first when auditing any ported thermostat driver.
- Energy endpoint over-polling: drivers that include energy history calls in their main `refresh()` cycle will hit energy endpoints at the same cadence as control/sensor polling. Energy data changes at most hourly; it should be on its own 30-minute schedule.
- Missing `initialize()` lifecycle is near-universal in SmartThings-era ports. All of them rely solely on `updated()` for schedule registration, which means polling dies on hub restart.

**Fork architecture verdict:** Yes, fork is architecturally sound. The driver's core structure (LAN HubAction, map-based response parsing, `runEvery*` scheduling, `unschedule()` on save) is correct for Hubitat. The issues are schema completeness and lifecycle gaps, not structural misdesign. A targeted fix session is lower risk than a rewrite.

**Filed:** `.squad/files/daikin-research/daikin-capability-gap-memo.md` and `.squad/decisions/inbox/trinity-daikin-capability-gap.md`


## Team Updates

### Hubitat Write-Only Property Gotcha + HubAction Constructor Table (Tank-3, 2026-05-18)

**Key Lessons from Daikin v0.1.1 hotfix:**

1. **Groovy JavaBean Naming + Scheduler Method Shadowing**  
   Custom command setX(x) creates a write-only property x on the driver object. If the code also calls the platform's x() scheduler method (e.g., schedule(cron, method)), Groovy's dynamic dispatch resolves the name as the write-only property instead of the method → runtime error ("Cannot read write-only property"). Workaround: use unEvery* idiomatic methods instead of calling schedule by name. Affected drivers: any Thermostat capability driver that calls schedule(cron, method) in addition to providing the setSchedule() stub.

2. **HubAction Constructor Overloads**  
   Valid forms for LAN HTTP: HubAction(String), HubAction(String, Protocol), HubAction(String, Protocol, String dni), HubAction(String, Protocol, String dni, Map options), HubAction(Map), HubAction(Map, Protocol) ← **preferred for GET**. Invalid form: HubAction(Map, Protocol, Map) does NOT exist. Callback must be inside the params Map when using 2-arg form.

3. **Test on First Install Before Shipping**  
   Both bugs were immediately visible on first Save Preferences after install. Smoke-test drivers on hub before tagging v1.0 releases.

### Daikin v0.1.2 asynchttpGet Rewrite Correction (Tank-4, 2026-05-18)

**CORRECTION to Trinity's v0.1.0 performance memo:**

Earlier memo stated: *"asynchttpGet is for cloud HTTPS calls."*

**This is incorrect.** `asynchttpGet` works for **any HTTP URL** including local LAN (e.g., `http://192.168.1.50/aircon/get_control_info`). It is the documented, stable Hubitat API for **all async HTTP on device drivers, LAN and cloud alike.**

**Correct scope distinction:**
- `asynchttpGet` — any HTTP URL (LAN or cloud), async callback pattern
- `HubAction(LAN)` — raw Hubitat socket dispatch; useful for non-HTTP protocols (raw TCP, UDP, Zigbee commands)

Tank-4 completed a full rewrite of Daikin WiFi v0.1.2 (commit e45967e) replacing all Map-based HubAction LAN calls with asynchttpGet, eliminating the constructor instability that plagued both v0.1.0 (3-arg) and v0.1.1 (2-arg). **Future drivers (Sunstat, Gemstone cloud calls, any new LAN HTTP driver) should use asynchttpGet by default.**

See decision `tank-daikin-wifi-v012-asynchttp` in decisions.md and skills `hubitat-asynchttpget-pattern` for the canonical send-helper + AsyncResponse callback template.

### Daikin v0.1.3 Swing Mode + v0.1.0 Capability Memo Correction (Tank-5, 2026-05-18)

**CORRECTION to Trinity's v0.1.0 capability gap memo:**

Earlier memo (Section "Daikin capability gap analysis > Top capability gaps observed") stated: *"`fanDirection` was parsed into custom attribute."*

**This was incorrect for v0.1.0–v0.1.2.** Tank-5's clean-room v0.1.3 audit found that the BRP069B `f_dir` field was **not being parsed at all** in the driver's original form. No `fanDirection` attribute existed, and no parse path was wired. Tank-5 added both the **read path** (`handleControlInfo` → `swingMode` attribute emission) and **write path** (`setSwingMode` command → `set_control_info` write) fresh in v0.1.3 (commit 665e968).

**Implication:** The memo's capability gap assessment was built on incomplete observations. While the underlying fork-is-sound verdict still holds, this particular gap was understated in the original analysis. Future audits should validate that attributes are actually being parsed in current driver builds, not assumed from metadata inspection alone.

Trinity's history-archive.md has a snapshot of the original memo for reference; this note serves as the live correction.

### Daikin v0.1.4 Roadmap Complete (Tank-6, 2026-05-18)

**ROADMAP CLOSURE: v0.1.0 capability gap memo items 7–8 (econo/powerful, get_model_info) now shipped.**

Tank-6 completed the final three roadmap items bundled in v0.1.4 (commit 1dd21fe):
1. **Econo/Powerful mode** — `setSpecialMode("off"|"econo"|"powerful")` command + `specialMode` ENUM attribute, polled every fast-refresh cycle.
2. **get_model_info runtime cache** — Called once in `initialize()`; caches model name, firmware, humidity sensor presence, swing support flags for diagnostics.
3. **Event hygiene audit** — All five checks passed; driver was already clean (emitIfChanged on parse paths, descriptionText on all sendEvent, ≥60s lastActivity throttle, zero displayed:false remnants, zero isStateChange:true anti-patterns).

**v0.1.0+ capability gap roadmap: FULLY IMPLEMENTED.** Hardware verification pending on Mads's BRP069B unit.

**Hardware-verifiable risks flagged:** adv field bitmap values (econo=2, powerful=12) and get_model_info field names may differ on Mads's specific firmware revision. Treat as v0.1.5 fix-up territory if hardware testing reveals discrepancies.

---

## 2026-05-18 — Thermostat Ecosystem Survey (Trinity)

### Goal
Answer: "How else could we improve this driver? Is there something other thermostat drivers do that we don't do yet?"

### Method
Surveyed 5 well-regarded Hubitat thermostat drivers across brands (Venstar, Ecobee, Honeywell, Sensi, built-in Z-Wave/Zigbee). Mapped 10 common patterns against Daikin WiFi v0.1.5 and SunStat Connect Plus v0.1.11 capabilities.

### Key Learnings

**1. Community Thermostat Pattern Catalog**

| Pattern | Adoption | Notes |
|---------|----------|-------|
| `setpointDisplay` (string) | ~90% of drivers surveyed | `"Heat: 72°F"` or `"Auto: 70°F/75°F"`. Improves dashboard UX. Missing from both our drivers. |
| `awayMode` attribute | ~85% (Venstar, Honeywell, Ecobee, Sensi) | Discrete attribute for automation visibility. SunStat has it ✅; Daikin doesn't. |
| `thermostatHold` or vacation mode | ~75% (cloud-backed + some LAN) | SunStat ✅; Daikin doesn't expose via API. |
| Schedule enable/disable toggle | ~70% (Venstar, Ecobee, Sensi, Honeywell) | SunStat ✅ (`setScheduleEnabled`); Daikin's `set_program` rarely useful with Hubitat rules. |
| Multi-stage HVAC display | ~50% (Venstar, Honeywell, multi-zone systems) | Not applicable to our devices. |
| Filter/maintenance reminders | ~30% (custom community builds) | Community pattern: Rule Machine automation, not driver-native. |
| External/remote sensor support | ~65% (Ecobee SmartSensors, Venstar aux) | SunStat + Daikin both adequate (separate attributes). |
| Occupancy / IAQ | ~40% (mostly cloud: Ecobee) | Outside driver scope. |

**2. Architecture Observation**
- Community consensus: **Capabilities > Commands.** Dashboard/Rule Machine integration keys off standard capabilities (Thermostat, TemperatureMeasurement, EnergyMeter) and well-known attributes. Custom commands are power-user territory.
- Our drivers already follow this; we use standard capabilities + minimal well-named custom commands.

**3. Ecosystem Patterns We Already Follow ✅**
- Event hygiene (`emitIfChanged` + `descriptionText`)
- `supportedThermostatModes` on `installed()`
- `lastActivity` attribute
- External sensor data (SunStat outdoor + floor temp; Daikin outdoor temp)

**4. Top Gaps to Close (Phase 1, v0.1.6)**
- **Priority 1:** Add `setpointDisplay` to both drivers (0.5 hrs, high UX value).
- **Priority 2:** Audit if Daikin BRP069B API exposes `awayMode`; add if yes (1 hr, medium value).
- **Priority 3:** SunStat is mature; no additions needed.

### Notable Drivers for Reference

| Driver | Maintainer/Repo | Reason Notable |
|--------|-----------------|-----------------|
| Venstar ColorTouch Local API | Community (toggledbits) | Best multi-stage HVAC reference; local LAN pattern; strong code quality. |
| Ecobee Suite Manager | Hubitat Community (various) | Best external-sensor child-device pattern; schedule control reference. |
| Honeywell Total Connect | Community (varies) | Away/vacation mode patterns; multi-backend reference. |
| Built-in Hubitat Z-Wave/Zigbee | Hubitat | Platform capability conventions; baseline for feature validation. |

### Filed Artifacts
- **Memo:** `.squad/files/daikin-ecosystem-survey-memo.md`
- **Decision:** `.squad/decisions/inbox/trinity-daikin-ecosystem-survey.md`

---

## Team Updates — v0.1.6 Roadmap Findings (2026-05-18 — 21:29:42Z)

**Ecosystem survey completed:** Surveyed 5 peer thermostat drivers (Venstar, Ecobee, Honeywell, Sensi, Hubitat built-in) against our Daikin + SunStat drivers.

**Key finding — setpointDisplay attribute:** ~90% of peers expose this human-readable formatted string (e.g., "Heat: 72°F", "Auto: 70°F/75°F") for dashboard UX. We're missing it on both Daikin + SunStat. Cost: 0.5 hrs/driver. Recommended v0.1.6 Phase 1 priority (high UX value, low effort).

**Secondary finding — awayMode:** Venstar, Honeywell, Sensi expose this. SunStat already has it. Daikin conditional on API audit (Cypher checking BRP069B support).

**Intentional skips confirmed:** Multi-stage HVAC (not applicable), filter maintenance (app-layer), vacation mode (Hubitat rules), scheduling (already covered). No architectural changes needed.

**Next:** Tank implements setpointDisplay on both drivers. Cypher validates Daikin awayMode during next protocol review. See .squad/decisions.md and orchestration logs.
