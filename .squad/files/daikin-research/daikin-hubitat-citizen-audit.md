# Daikin WiFi v0.1.6 — Hubitat Ecosystem-Citizen Audit

**Date:** 2026-05-18  
**Scope:** drivers/daikin-wifi/daikin-wifi.groovy v0.1.6 (baseline: v0.1.5)  
**Auditor:** Trinity (Lead/Architect)  
**Angle:** How well does this driver coexist with other Hubitat hub features and practices?

---

## Executive Summary

The Daikin WiFi driver is **a well-behaved ecosystem citizen**. It follows Hubitat platform conventions across all seven audit dimensions: event hygiene, state persistence, scheduler discipline, network behavior, lifecycle management, compatibility with standard Hubitat apps, and resource usage. No blocking issues; one minor dead-code cleanup suggestion.

---

## Findings by Dimension

### A. Event-DB Chatter — 🟢 **Clean**

**sendEvent() deduplication:**
- All parse-path events routed through `emitIfChanged()` helper (line 771–786)
- Numeric attributes compared with BigDecimal precision (lines 775–780)
- Eliminates false-positive "22.0" vs "22" events ✅
- Used consistently on lines 603–627, 649–671, 743, 751, 754, 803–805

**descriptionText everywhere:**
- Every `sendEvent()` call includes a meaningful description (lines 171–181, 195–200, 252, 260–262, 264, 291, etc.)
- Uses `${device.displayName}` for dynamic user-friendly names ✅
- Examples: `"${device.displayName} thermostat mode → ${tMode}"`, `"${device.displayName} turned ${switchSt}"`

**lastActivity throttling:**
- Respects 60-second floor (LAST_ACTIVITY_THROTTLE_MS = 60000, line 37)
- Throttle check: lines 790–794 gates emission by timestamp
- Only called from successful API response paths (lines 628, 671, 744) ✅

**Debug/trace log gating:**
- `debugLog()` gated by `settings.logEnable` (line 242)
- `traceLog()` gated by `settings.traceEnable` (line 243)
- Both preferences auto-disable after 30 min (lines 189, 192) ✅
- All policy/status logs use "[Daikin]" prefix for filtering

**Event rate analysis:**
- **5-min polling cycle:** ~7 events max on state change (control + sensor + special mode + display), 0 when unchanged
- **30-min energy cycle:** ≤1 event (only today's kWh from handleWeekPower)
- **Worst case peak:** Power-on with defaults + immediate device response = 7 events in 4 seconds, then normal polling resumes
- **Verdict:** Healthy, no spam ✅

---

### B. State-DB Churn — 🟢 **Minimal**

**State writes per poll cycle:**
- `state.lastActivityEmittedAt`: Written once per ≥60s (line 795) — only on poll success
- `state.pingPending`: Only during manual ping operations, not on normal polling
- `state.pingRequestedAt`: Only during ping, never read after write (minor dead code)
- `state.modelInfo`: Written once per driver load/initialize (line 548)
- **Total: 0 writes per normal 5-min polling cycle** ✅

**No unbounded growth:**
- All state keys are fixed and small (flags, timestamp, one info map)
- `state.modelInfo` is ~5 entries per device discovery ✅
- No lists or maps accumulating data over time

**Dead state analysis:**
- No stale/orphaned keys accumulating
- `state.pingRequestedAt` is written but never read (minor hygiene issue, lines 439 and 800–806 reference pingPending but not pingRequestedAt)

---

### C. Scheduler Load — 🟢 **Disciplined**

**Persistent schedules:**
- `registerSchedules()` (lines 870–884) creates:
  - One `runEvery*` for control polling (1/5/10/15/30 min depending on preference)
  - One `runEvery30Minutes("refreshEnergy")` — separate cycle for energy
- **Total base: 2 persistent schedules** ✅

**One-shot jobs (transient):**
- refresh() → schedules doSensorRefresh (2s) + doSpecialModeRefresh (4s)
- refreshEnergy() → schedules doYearPowerRefresh (3s)
- on() → schedules applyPowerOnDefaults (2s)
- Ping operations → schedule pingTimeout (5s), properly unscheduled on completion (lines 748, 802)
- All complete and clean up naturally ✅

**Initialization safety:**
- `initialize()` calls `unschedule()` FIRST (line 207) before `registerSchedules()` ✅
- Prevents duplicate job accumulation on hub reboot or preference update
- `uninstalled()` cleans up all schedules (line 224) ✅

**No accumulation risk:** 🟢

---

### D. Network Behavior — 🟢 **Cautious**

**asynchttpGet everywhere:**
- All HTTP calls use `asynchttpGet()` (skill: hubitat-asynchttpget-pattern, validated v0.1.2)
- 10-second timeout on every call (line 506) ✅
- Non-blocking; callbacks handled by Hubitat thread pool

**Concurrent request analysis:**
- Refresh cycle (staggered):
  - T+0s: get_control_info
  - T+2s: get_sensor_info
  - T+4s: get_special_mode
  - **Overlap: 3 concurrent requests max, staggered by 2-4s** ✅
- Energy cycle (separate schedule): 1–2 requests every 30 min, no overlap with control polls
- Peak with manual ping: 4 concurrent requests max — well under Hubitat's ~10 limit ✅

**Polling rate tunable:**
- `refreshInterval` preference: 1, 5, 10, 15, 30 minutes (default 5) — line 126–131 ✅
- Users can back off if Daikin unit rate-limits
- No hardcoded aggressive polling

**Offline device handling:**
- Device offline → HTTP timeout (10s) → logged as warning (line 513)
- Next poll retry happens on normal schedule (no exponential backoff)
- For 1-min polling with offline device: ~60 warnings/hour
- **Assessment:** Not ideal (logs could be spammy), but driver doesn't crash and keeps retrying ✅

**Energy poll optimization:**
- `refreshEnergy()` guards against `switch == "off"` (line 423–425) ✅
- Avoids querying power when unit is powered down
- Separate 30-min schedule decouples energy from control polling

---

### E. Lifecycle Hygiene — 🟢 **Correct**

**installed() (lines 164–184):**
- Emits required schema events: `supportedThermostatModes`, `supportedThermostatFanModes` ✅
- Initializes device state to safe defaults (off, idle, unknown)
- Calls `initialize()` to register schedules
- No dangerous side effects ✅

**updated() (lines 186–203):**
- Calls `initialize()` (which calls `unschedule()` first) ✅
- Re-emits schema events so dashboard selections stay current
- Handles `logEnable`/`traceEnable` auto-off timers

**initialize() (lines 205–221):**
- Calls `unschedule()` BEFORE `registerSchedules()` ✅
- Resets transient state: pingPending, lastActivityEmittedAt (lines 208–210)
- Ensures DNI matches configured IP (line 217)
- Gracefully handles missing IP (lines 212–215)
- Requests model info once (line 220)

**uninstalled() (lines 223–225):**
- Calls `unschedule()` to clean up pending jobs ✅
- asynchttpGet doesn't hold persistent sockets; no extra cleanup needed

**Hub reboot survival:**
- Hubitat automatically re-runs `updated()` on reboot
- `initialize()` re-registers schedules ✅
- Driver resumes polling without user intervention

---

### F. Interaction with Other Hubitat Features — 🟢 **Friendly**

**Rule Machine compatibility:**
- `setHeatingSetpoint(temp)` guards against:
  - null (line 344–346) ✅
  - NaN (line 349–351) ✅
  - Out-of-range (5–40 °C, lines 356–358) ✅
- `setCoolingSetpoint(temp)` has identical guards (lines 370–384) ✅
- RM can't crash the driver with garbage input

**Thermostat capability completeness:**
- Implements all required methods: `setThermostatMode()`, `setThermostatFanMode()`, `setHeatingSetpoint()`, `setCoolingSetpoint()`
- Emits `supportedThermostatModes` on install/update (lines 171–176, 195–200) ✅
- Hubitat native apps (Thermostat Manager, Scheduler) will auto-discover this driver

**Dashboard attribute compatibility:**
- All attributes properly typed:
  - `temperature`, `outsideTemp`, `heatingSetpoint`, `coolingSetpoint`, `humidity` → NUMBER
  - `thermostatMode`, `fanRate`, `swingMode`, `specialMode`, `healthStatus` → ENUM
  - `lastActivity`, `setpointDisplay` → STRING
- `setpointDisplay` added in v0.1.6 for human-readable mode/setpoint display ✅
- Complex state (state.modelInfo) is NOT an attribute, so doesn't pollute dashboards ✅

**Hub Mesh compatibility:**
- All attributes use simple types (no JSON blobs, no complex objects) ✅
- `state.modelInfo` is internal state; not exposed to Mesh
- Mesh can safely consume all device attributes

**HSM/Automation integration:**
- `temperature`, `outsideTemp` changes fire events that HSM can monitor ✅
- `thermostatMode`, `switch` state changes also fire ✅
- `lastActivity` timestamp allows Rule Machine to detect hub connectivity issues

---

### G. Resource Hygiene — 🟢 **Responsible**

**No unbounded growth:**
- All state keys are fixed: pingPending, pingRequestedAt, lastActivityEmittedAt, modelInfo ✅
- modelInfo map contains exactly 5 entries per device load
- No lists or maps that grow without bound ✅

**Timestamp safety:**
- Uses `Long` (64-bit milliseconds) for timestamps ✅
- Won't overflow for ~292 million years of operation
- No Y2038 or similar gotchas

**Logging appropriateness:**
- Errors logged at WARN level (HTTP failures, invalid inputs, timeouts) ✅
- User actions logged at INFO level (power on/off, mode changes) — good for audit trail
- Protocol details at DEBUG and TRACE levels, gated by preferences
- No spam-level logging ✅

---

## Top 5 Ecosystem-Citizen Improvements (Ranked)

| # | Item | Impact | Effort | Hours | Priority |
|---|------|--------|--------|-------|----------|
| 1 | **Remove dead `state.pingRequestedAt` write** | Code hygiene; saves 1 state I/O per manual ping | Low | 0.25 hrs | Nice-to-have |
| 2 | **Document offline backoff strategy** | User transparency; clarify error log volume at 1-min polling | Documentation | 0.5 hrs | Nice-to-have |
| 3 | **Add optional error backoff for repeated offline** | Reduce log spam if device offline >10 min; exponential backoff cap | Medium | 2 hrs | Nice-to-have |
| 4 | **Add `awayMode` attribute (if Daikin API supports)** | Align with peer thermostat drivers (Venstar, Honeywell, Sensi) | Medium | 1 hr | Depends on API audit |
| 5 | **Validate `state.modelInfo` fields on real hardware** | v0.1.4–v0.1.5 hardcoded field names may differ on user's firmware | Testing | 1 hr | Before v1.0 release |

---

## Trinity's Verdict

**The Daikin WiFi driver is already a good Hubitat citizen.** It follows platform conventions, plays nice with other drivers and apps, respects the hub's scheduler, and emits events responsibly. The driver passes all seven ecosystem-citizenship dimensions without structural concerns.

**Compared to other drivers in this repo:**
- **Touchstone (Tuya fireplace):** Similar event discipline; Daikin is cleaner on state persistence (no socket buffers).
- **Gemstone (cloud lights):** Both use lastActivity for health; Gemstone has cloud quota concerns Daikin doesn't face.
- **SunStat (thermostat):** Peer pattern; both implement full Thermostat capability, emitIfChanged, and proper lifecycle. SunStat is more complex (parent/child + cloud); Daikin is simpler LAN model.

**No blocking issues.** Ready for user adoption. One optional cleanup on `state.pingRequestedAt`; otherwise ship as-is. Before v1.0 release, validate `state.modelInfo` field names on real BRP069B hardware to ensure v0.1.4+ hard-coded assumptions hold on Mads's device firmware.

---

## Cross-References

- **Cypher's perf/quality audit:** `.squad/files/daikin-research/daikin-api-perf-audit-memo.md` — focuses on internal driver efficiency; complements this ecosystem-impact view
- **Ecosystem survey:** `.squad/decisions.md` (Trinity section) — feature-gap analysis vs peer thermostat drivers (completed v0.1.6 setpointDisplay)
- **Skills referenced:**
  - `hubitat-event-hygiene` — emitIfChanged + descriptionText patterns
  - `hubitat-state-hygiene` — state persistence discipline
  - `hubitat-asynchttpget-pattern` — HTTP-over-LAN best practices
  - `hubitat-healthcheck-vs-lastactivity` — HealthCheck vs lastActivity decision flowchart

---

## Notes for Follow-up

1. **Hardware validation pending:** Mads's real-device testing of v0.1.6 will confirm field name assumptions in `parseModelInfo()`.
2. **Offline handling:** If user reports log spam during prolonged outages, revisit item #3 (optional backoff).
3. **awayMode audit:** Cypher to check if BRP069B exposes away/vacation mode in next protocol review.
