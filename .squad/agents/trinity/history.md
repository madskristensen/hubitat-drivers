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

## Learnings

### 2026-05-17T15:41:32-07:00 — Cross-driver review: Gemstone, SunStat, Touchstone

**Recurring patterns found across all three drivers (good — consistent idioms):**
- `logEnable` / `txtEnable` preference pair — all three. `logsOff()` auto-disable at 30 min — all three.
- `@Field static final` constants with no cross-field references — all three respect the Hubitat sandbox rule.
- `emitIfChanged()` helper to suppress redundant sendEvent calls — SunStat child + Touchstone; Gemstone not needed (always-changing color values).
- `USER_AGENT` constant on drivers that make HTTP calls (Gemstone, SunStat parent). Touchstone has one too.
- `installed()` sets preference defaults explicitly; `updated()` calls `initialize()`; `initialize()` guards on config readiness before scheduling.

**Anti-patterns / gaps identified:**

1. **`poll()` without `capability "Polling"`** — Gemstone and SunStat parent both implement `poll()` but don't declare `capability "Polling"`. Touchstone is correct. Undeclared capability means Hubitat Polling app won't discover these devices as compatible.

2. **`capability "Sensor"` missing alongside `TemperatureMeasurement`** — Touchstone has `TemperatureMeasurement` but no `Sensor`. Hubitat convention: `Sensor` is the marker capability that companion apps and dashboards use to classify sensor devices. SunStat child correctly declares both.

3. **`power` attribute duplicating `switch`** — Touchstone declares a custom `power` attribute ("on"/"off") and emits it in parallel with the standard `switch` attribute from every on/off state change. This doubles every event in the Events tab and adds noise. `switch` is the standard; `power` is redundant and should be removed.

4. **Packaging gaps are driver-specific** — SunStat has CHANGELOG.md, TESTING.md, README.md, packageManifest.json. Gemstone has TESTING.md, README.md, packageManifest.json but no CHANGELOG.md. Touchstone has README.md and packageManifest.json but no CHANGELOG.md and no TESTING.md. All drivers should have all four files for HPM compatibility and contributor onboarding.

5. **Named commands missing for known-semantic DPs** — Touchstone surfaced DP 103 (flame speed: Slow/Medium/Fast) and DP 105 (log brightness) as raw `dp103`/`dp105` attributes with no named commands, even though the architecture decision specified `setFlameSpeed()` and `setLogBrightness()` as named commands. DPs with confirmed semantics should graduate to named commands; only truly unknown DPs should stay raw.

6. **Parent/child version skew in SunStat** — Parent at v0.1.4, child at v0.1.2. The last two parent releases were parent-only fixes (no child code changed), so the skew is technically correct but confusing for users comparing versions. Convention: bump child version in lockstep with parent (even no-op) so users see consistent version numbers.

7. **`colorMode` as `string` instead of `enum`** — Gemstone declares `attribute "colorMode", "string"` but only ever emits three values: "RGB", "CT", "EFFECTS". Declaring it as `enum` gives rules engines proper constraint checking and improves dashboard tile behavior.

8. **Utility method duplication across all three drivers** — `safeStr`, `safeInt`, `safeBigDecimal`, `emitIfChanged`, `debugLog`, `infoLog`, `logsOff` are near-identical copies in all three. Hubitat sandbox doesn't allow shared libraries, so runtime duplication is unavoidable — but there is no canonical template source. A `.squad/templates/driver-utilities.groovy` snippet file would serve as the single source of truth that Tank copies from when scaffolding new drivers.

**For detailed learning notes, see archived history.**

---

## 2026-05-17T15:41:32Z — Cross-driver improvement scan (4-way)

Participated in 4-way driver improvement scan with Tank, Cypher, Switch. Findings consolidated by Squad. Orchestration log: `.squad/orchestration-log/2026-05-17T15-41-32-trinity.md`.**


## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.
- 2026-05-18: Daikin WiFi research memos (`daikin-capability-gap-memo.md`) were used as direct input for Tank-2's clean-room driver implementation (commit b26c04f). Research established the capability gap inventory (supportedThermostatModes, lifecycle, energy polling) and informed the v0.1.0 priority list. Clean-room pattern proves that research-stage feasibility analysis naturally feeds independent authorship without source code copying.

---

## Learnings

### 2026-05-18 — Write-idempotency audit across all four drivers

**Methodology:** Read every device-write path (sendDpWrite, sendDevicePatch, PUT/PATCH HTTP) in each driver and asked: "Does the driver check device.currentValue() or a state.* cache before sending?" Anything that sends unconditionally got flagged. Then classified by severity using the "does this cause a user-visible side effect?" test from the v0.1.23 context.

**Key cross-driver patterns noticed:**

1. **The `emitIfChanged` / skip-if-match split** — SunStat child's `setScheduleEnabled()` is a clear example of a partial fix: `emitIfChanged` correctly gates the Hubitat event, but the `sendDevicePatch` below it is unconditional. This pattern (event deduplication ≠ write deduplication) should be audited for in every driver. These two guards solve different problems and must both be present.

2. **Lifecycle-driven writes are the highest-risk category** — `applyOnPowerOnDefaults` (Touchstone) fires automatically on every power-on; `cancelBoost`/`boostExpired` (SunStat) fire on timer expiry without user interaction. These are where unconditional writes cause the most surprise: the user didn't explicitly ask for the write to happen at that moment. User-explicit commands (setFlameColor, setHeatingSetpoint) are lower priority because the user intent is explicit.

3. **BY-DESIGN non-idempotent writes: state-assertion semantics** — SunStat's boost recovery is intentionally non-idempotent. `cancelBoost()` force-writes the pre-boost setpoint even if the device already shows that value, because the whole point is to defeat cloud drift (the cloud might have updated the setpoint during the boost window). Same logic applies to any "restore after override" pattern. **Do not apply skip-if-match to state-assertion paths** — it defeats the purpose.

4. **Gemstone effect repeat** — `activateEffectWithPattern` is the one 🔴 in Gemstone. Sending the same effect pattern to an LED string controller visibly restarts the animation. Unlike Tuya fireplace clicks (audio), this is a visual artifact the homeowner would notice if rules re-assert the active effect. Skip-if-match should compare against `effectName` attribute or `state.lastPattern.id`.

5. **Touchstone `on()` when already on** — Tuya fireplaces emit an audible click on receiving any DP write. This means `on()` called on an already-on fireplace (common in rules that re-assert switch state) produces a physical click. This is the same class of problem as the power-on defaults and should be guarded by a simple `if (device.currentValue("switch") == "on") return` at the top of `on()`.

6. **Cloud drivers (Gemstone, SunStat) — wire vs. visible** — Redundant API calls in cloud drivers don't cause visible physical artifacts (lights don't flash on an idempotent PUT when value matches). The cost is API quota and latency. These are 🟡 not 🔴. Exception: Gemstone's `PUT /deviceControl/play/pattern` re-executes the animation sequence on the hardware, which IS visible — hence the 🔴 for `activateEffectWithPattern`.

**Filed:** `.squad/decisions/inbox/trinity-redundant-write-audit.md`

---

## Learnings

### 2026-05-18 — Daikin WiFi driver capability gap analysis (eriktack/hubitat-daikin-wifi v1.0.3)

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

