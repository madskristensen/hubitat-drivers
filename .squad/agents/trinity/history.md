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

