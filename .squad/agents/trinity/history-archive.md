# Trinity — Archived History

## Learnings (Prior Sessions)

### 2026-05-16T21:07:23-07:00: Hubitat preference length limit & long-secret pattern

**Problem:** Hubitat's preference UI silently rejects values exceeding ~1024 characters.

**Solution:** Use `setRefreshToken(String token)` command instead of preference. Command STRING parameters bypass length limits. Pattern reused in SunStat v0.1.3+.

### 2026-05-16: Repo layout, conventions, and Gemstone driver architecture

**Folder structure:**
- Top-level `drivers/` (lowercase), one `kebab-case` subfolder per driver
- Each driver subfolder: `.groovy`, `README.md`, `packageManifest.json`
- Namespace: `"mads"` (consistent across drivers)

**HPM conventions:**
- Per-driver `packageManifest.json` + repo-root `repository.json`
- `id` field = UUID v4, never changed
- `minimumHEVersion: "2.3.0"` baseline (C-7/C-8)

**Gemstone design (single driver, local-only):**
- Capabilities: Actuator, Switch, SwitchLevel, ColorControl, LightEffects, Refresh, Initialize
- Polling: `runEvery5Minutes`
- Logging: `logEnable` preference, 30-min auto-off

---

## 2026-05-16T21:45:13Z — Team update: Gemstone protocol research ongoing

Cypher confirmed cloud API is documented (sslivins). Local API discovery phase underway. Tank scaffolded driver with HubAction stubs.

---

## 2026-05-16T22:24:15Z — User directive: local-only scope

Scope tightened to local controller only (no cloud path). v0.2.0 timeline tied to Mads' packet capture success.

---

## 2026-05-16T23:04:57Z — Gemstone architecture validated; v0.2.0 blocked on pcap

**Findings from driver extraction:**
- C4 driver confirmed: TCP port 80, PKCS#7 encryption
- ELAN driver: edrvc binary format
- 70+ API probes: all 404

Architecture remains sound. Next gate: Mads' UniFi pcap reveals routing envelope.

---

## 2026-05-16T20:01:41-07:00: SunStat Connect Plus — architecture design

**New patterns:**
- Parent/child from day one (multi-device reality known at architecture time)
- `Thermostat` capability with constrained modes via `supportedThermostatModes`
- `setBoost(minutes)` custom command for timed override
- Dual-sensor split: `temperature` + custom `floorTemperature`
- Temperature unit normalization with `location.temperatureScale`

**Effort:** Medium (2 sessions)

---

## 2026-05-17T03:01:41Z — SunStat v0.1.0 shipped

Parent/child driver pair complete. Tank wired full implementation. Switch drafted test plan. Awaiting Mads' real-device verification.

---

## 2026-05-16T20:01:41-07:00: Bosch Home Connect — driver architecture design

**Architecture:**
- OAuth2 via App (only Apps get `mappings {}`)
- Parent App + child Driver per door (ContactSensor capability)
- Polling first; SSE deferred pending research

**New patterns:**
- `hubitat-cloud-oauth-app` skill extracted
- `atomicState` for CSRF state parameter
- `doorAlarm` custom attribute for "left open too long"
- Folder: `*-app.groovy` naming when entry point is App

**Risk:** Bosch requires exact-match redirect URI. Community driver research gates implementation.

---

## 2026-05-17T16:45:09Z — Bosch consumer auth investigation

**Finding:** Developer portal registration is unavoidable (single 5-min cost). No consumer-auth alternatives exist.

**Verdict:** Device Flow OAuth2 design stands. User registers own client_id + client_secret at developer portal.

---

## 2026-05-18 — Daikin Capability Gap Analysis (Detailed Working Notes)

### Daikin BRP069B API Surface (Complete Inventory)

**Implemented in driver v0.1.5:**
- `/aircon/get_control_info` — power/mode/setpoint/fan/swing read
- `/aircon/set_control_info` — all control writes
- `/aircon/get_sensor_info` — indoor/outdoor temp, humidity
- `/aircon/get_model_info` — model name, firmware, capability flags
- `/aircon/get_special_mode` + `/set_special_mode` — econo/powerful
- `/aircon/get_week_power_ex` / `/get_year_power_ex` — energy history

**Not called — skip (10 endpoints):**
- `/common/get_remote_method`, `/set_remote_method` — cloud polling negotiation; irrelevant for LAN
- `/aircon/get_program`, `/set_program` — on-device timer; low utility with Hubitat rules
- `/aircon/get_scdltimer`, `/set_scdltimer` — weekly timer; same rationale
- `/aircon/get_timer`, `/get_target`, `/get_price` — unknown/undocumented
- `/common/set_led`, `/common/reboot`, `/common/set_regioncode`, `/common/get_datetime`, `/aircon/get_wifi_setting` — non-functional, dangerous, or credential-risk

**Deferred for future investigation (4 endpoints):**
- `/common/basic_info` — MAC, firmware, `lpwFlag` (BRP069A backward compat)
- `/aircon/get_demand_control`, `/set_demand_control` — power demand-response (needs real-device validation)
- `/common/get_notify` — filter maintenance alert (schema undocumented)
- `/aircon/get_day_power_ex` — hourly kWh (Apollon77 TODO)

**Energy field format notes:**
- Energy fields (`week_heat`, `week_cool`, etc.) are in tenths of kWh
- Multi-day arrays stored as slash-delimited strings: `"120/130/140/"` → parse with `.split('/')`
- Parse-safety: guard `Double.parseDouble()` on empty strings

**Temperature sensor absence:**
- Outdoor temp `otemp` and indoor temp `htemp` return `"-"` when unavailable
- Must guard: `if (otemp != "-") { parseDouble(otemp) }`

### v0.1.0 Capability Gap Assessment (Original Memo Context)

**Top gaps identified:**
1. `supportedThermostatModes` never declared or emitted — Rule Machine mode selector fails
2. Energy polling in main refresh cycle (should be separate 30-min schedule)
3. Missing `initialize()` lifecycle (schedule registration dies on hub reboot)
4. Fan direction (f_dir) was not parsed in original driver (added Tank-5 v0.1.3)
5. Mode/fan display strings hardcoded English instead of localized/dashboard-friendly
6. Null guards missing on setpoint setters (Rule Machine "clear" sends null)
7. Event hygiene: emitIfChanged, lastActivity throttle, descriptionText coverage

**Resolution status after v0.1.1–v0.1.4:**
- ✅ Added initialize() + proper schedule registration
- ✅ Separated energy poll (30 min) from control poll (5 min, tunable)
- ✅ Added swing mode (f_dir) read + write
- ✅ Added null guards on setpoint setters
- ✅ Event hygiene audit passed all 5 checks (v0.1.4)
- ⏳ Fan display strings (dashboard localization) — optional v0.1.6+
- ⏳ Econo/Powerful modes added v0.1.4 ✅

**Fork architecture verdict:** Sound. Core structure (LAN HubAction, response parsing, scheduling) is correct. Issues were schema completeness + lifecycle gaps, not structural misdesign.

---

## 2026-05-18 — Thermostat Ecosystem Survey (Detailed Working Notes)

### Method
Surveyed 5 well-regarded Hubitat thermostat drivers: Venstar ColorTouch, Ecobee Suite, Honeywell Total Connect, Sensi, Hubitat built-in Z-Wave/Zigbee.

Mapped 10 common patterns against Daikin WiFi v0.1.5 and SunStat Connect Plus v0.1.11.

### Findings Table

| Pattern | Adoption % | Daikin | SunStat | Notes |
|---------|-----------|--------|----------|-------|
| `setpointDisplay` (string) | ~90% | ❌ | ❌ | "Heat: 72°F" format; high UX value |
| `awayMode` attribute | ~85% | ? (API unknown) | ✅ | Venstar, Honeywell, Ecobee, Sensi |
| `thermostatHold` / vacation | ~75% | ❌ | ✅ | SunStat has it; Daikin no API |
| Schedule enable/disable | ~70% | ⚠️ (low value) | ✅ | SunStat ✅; Daikin `set_program` rarely useful |
| Multi-stage HVAC display | ~50% | ❌ | ❌ | Not applicable (single-stage units) |
| Filter/maintenance reminders | ~30% | ⚠️ | ⚠️ | Community pattern: Rule Machine, not driver |
| External/remote sensor | ~65% | ✅ | ✅ | Both adequate (separate attributes) |
| Occupancy / IAQ | ~40% | ❌ | ❌ | Mostly cloud; outside driver scope |

### Community Consensus
**Capabilities > Commands.** Dashboard and Rule Machine integration keys off standard capabilities (Thermostat, TemperatureMeasurement, EnergyMeter) and well-known attributes. Custom commands are power-user territory. Our drivers already follow this.

### Patterns We Already Implement ✅
- Event hygiene (emitIfChanged + descriptionText)
- `supportedThermostatModes` on installed()
- `lastActivity` attribute
- External sensor data (SunStat outdoor + floor; Daikin outdoor)

### v0.1.6 Phase 1 Roadmap
- **Priority 1:** Add `setpointDisplay` to both drivers (0.5h each, high UX value)
- **Priority 2:** Audit Daikin BRP069B API for `awayMode` support (Cypher, 1h)
- **Priority 3:** SunStat mature; no additions needed

### Reference Drivers
- **Venstar ColorTouch:** Best multi-stage HVAC + local LAN pattern; strong code quality
- **Ecobee Suite:** Best external-sensor child-device pattern; schedule control reference
- **Honeywell Total Connect:** Away/vacation mode patterns; multi-backend reference
- **Hubitat built-in Z-Wave/Zigbee:** Platform capability conventions; baseline validation

---

## 2026-05-18 — Hubitat Ecosystem-Citizen Audit (Detailed Working Notes)

### Audit Framework
Applied 7-dimensional checklist to Daikin v0.1.6 (test case); all dimensions pass → production-ready verdict.

### Dimension Results (Daikin v0.1.6)

| Dimension | Status | Key Notes |
|-----------|--------|-----------|
| **A. Event-DB chatter** | 🟢 | emitIfChanged() on all parse paths; lastActivity ≥60s throttle; descriptionText universal; debug/trace logs gated |
| **B. State-DB churn** | 🟢 | 0 state writes per normal 5-min poll; only ping + lastActivity throttled writes; no unbounded growth |
| **C. Scheduler load** | 🟢 | 2 base schedules + transient one-shots; initialize() unschedules first; no accumulation risk |
| **D. Network behavior** | 🟢 | asynchttpGet + 10s timeout, 3 concurrent requests max, energy poll separated (30 min), offline device retries gracefully |
| **E. Lifecycle hygiene** | 🟢 | installed() → updated() → initialize() chain clean; uninstalled() unschedules; hub reboot survival ✅ |
| **F. App compatibility** | 🟢 | Null/NaN guards on setpoint setters, full Thermostat capability, setpointDisplay (v0.1.6), Hub Mesh types OK |
| **G. Resource hygiene** | 🟢 | No unbounded collections, timestamp overflow safe, logging levels appropriate |

### Optional Improvements (Non-Blocking)
1. Remove dead `state.pingRequestedAt` write (0.25h)
2. Validate `state.modelInfo` field names on real hardware (1h test)
3. Document offline error-log volume at 1-min polling (0.5h docs)

### Cross-Comparisons
- **vs Touchstone:** Similar event discipline; Daikin cleaner on state persistence
- **vs Gemstone:** Both use lastActivity; Gemstone has cloud quota concerns Daikin doesn't
- **vs SunStat:** Peer thermostat; both correct; SunStat more complex (parent/child)

### Checklist Usage
Apply when:
1. Auditing new driver before first release (adoption readiness review)
2. Reviewing driver PRs for cross-repo consistency
3. Diagnosing performance issues (check dimensions B, C, G first)
4. Planning driver maintenance (identify tech debt by dimension)

**Time estimate:** 30–60 min per driver depending on code size.

---
