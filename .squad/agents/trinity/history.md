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

**For detailed learning notes, see archived history.**
