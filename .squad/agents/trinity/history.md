# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation home automation hubs.
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Recent Projects & Decisions

**For detailed learnings from prior sessions (Gemstone, SunStat v0.1.0–v0.1.2, Bosch), see history-archive.md.**

### 2026-05-18 — Arc Completion: Daikin WiFi to v0.1.4 + Ecosystem Survey + Citizen Audit

**Daikin WiFi v0.1.0 capability gap research shipped as v0.1.1–v0.1.4** (Tank-2 through Tank-6):
- ✅ v0.1.1: Scheduler property-shadowing fix (setSchedule vs schedule method collision)
- ✅ v0.1.2: asynchttpGet rewrite (replaced unstable Map-based HubAction constructors)
- ✅ v0.1.3: Swing mode added (f_dir field was missing from original driver)
- ✅ v0.1.4: Econo/Powerful modes, model-info cache, event hygiene audit all pass

**Thermostat ecosystem survey (Trinity):**
- Surveyed 5 peer drivers (Venstar, Ecobee, Honeywell, Sensi, Hubitat built-in)
- Key gap: `setpointDisplay` attribute missing from both Daikin + SunStat (~90% peer adoption)
- SunStat: already has awayMode ✅; Daikin: conditional on API audit
- All other patterns already implemented (no architectural changes needed)
- Memo filed; Tank will implement setpointDisplay in v0.1.6 phase

**Hubitat ecosystem-citizen audit (Trinity):**
- 7-dimensional checklist applied to Daikin v0.1.6 (test case); all pass
- Framework now documented as reusable skill: `hubitat-driver-citizen-checklist`
- Complement to Cypher's internal quality audit (focus: ecosystem impact, not code quality)
- Reference implementations: Daikin (LAN), Touchstone (socket), Gemstone (cloud), SunStat (parent/child)

**Skills updated:**
- 🆕 `hubitat-driver-citizen-checklist` (low confidence; single validation run on Daikin)
- ⬆️ `hubitat-healthcheck-vs-lastactivity` (medium confidence; validated on Touchstone, Gemstone, SunStat)

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

5. **Async HTTP (LAN & Cloud):**
   - Use asynchttpGet by default (stable across all Hubitat firmware versions)
   - Callback pattern: AsyncResponse helper → error guards → body parsing → activity touch
   - 10s timeout for LAN HTTP, 30s for cloud REST
   - Never use Map-based HubAction constructors (firmware-version fragile)

6. **Health Monitoring:**
   - **Local sockets (Touchstone):** Full HealthCheck capability + ping() probing
   - **Cloud REST (Gemstone, SunStat):** lastActivity only (no quota-consuming pings)
   - Parent/child: parent cascades lastActivity to all children on successful API response

7. **Idempotency & Lifecycle:**
   - Lifecycle-driven writes (power-on defaults, timer-expiry) are highest risk for duplicates
   - User-explicit commands lower priority (user expects retry semantics)
   - State-assertion paths (e.g., boost recovery, cloud-drift defense) intentionally non-idempotent (by design)
   - Guard against audible side effects: `if (device.currentValue("switch") == "on") return` before `on()`

8. **Write-Only Property Gotcha:**
   - `setX(x)` command creates a write-only property x on driver object
   - Can shadow Groovy method dispatch if code calls scheduler method `x()`
   - Workaround: use `runEvery*` idioms instead of `schedule(cron, method)` where method name collides

---

## Team Learnings — May 18, 2026

### Daikin BRP069B Complete Endpoint Catalog (Cypher)
28 total endpoints documented. 7 implemented; 10 intentionally skipped (cloud, scheduling, undocumented, dangerous); 4 deferred (demand control, filter alerts, hourly energy, BRP069A fallback). See `.squad/files/daikin-research/` for full breakdown.

### asynchttpGet Standard for All HTTP (Tank-4)
`asynchttpGet` works for **any HTTP URL** including local LAN. Replaces unreliable Map-based HubAction constructors. Pattern documented in `hubitat-asynchttpget-pattern` skill (confidence: medium). See `drivers/daikin-wifi/daikin-wifi.groovy` v0.1.2+ for reference.

### Thermostat Capability Gaps (Trinity + Peer Survey)
Most SmartThings-era Hubitat thermostat ports missing `supportedThermostatModes` emission and `setpointDisplay` attribute. Daikin passes; will add setpointDisplay v0.1.6. Audit memo: `.squad/files/daikin-research/daikin-ecosystem-survey-memo.md`.

---

## Next Session Focus

- [ ] Tank: Implement setpointDisplay on Daikin + SunStat (v0.1.6 phase, 0.5h each)
- [ ] Cypher: Validate Daikin BRP069B awayMode support (1h, conditional on API)
- [ ] Mads: Real-device testing on Daikin v0.1.4 BRP069B unit (1–2h, hardware-dependent)
- [ ] Link: Update Daikin + SunStat READMEs with v0.1.6 improvements; bump manifests

---

**Last updated:** 2026-05-18T22:00Z (arc close reskill)
