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
