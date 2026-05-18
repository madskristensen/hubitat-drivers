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

## Learnings

### Irrigation Driver Use-Case Evaluation Pattern (Trinity)

**Date:** 2026-05-18T15:44:37-07:00

**Pattern:** Evaluating user demand for device-class drivers reveals a common archetype — the **Stateful Multi-Input Compositor** — that justifies higher effort than generic device drivers.

**Rainbird WiFi case study:**
- **Stateful:** irrigation schedule + zone state (on/off, duration) persists across API calls
- **Multi-input:** composes with NOAA (rain forecast), PurpleAir (air quality), motion sensors (presence), leak sensors (safety), Rule Machine (conditional logic)
- **No conditional logic native:** Rainbird app is calendar + simple "skip if it rained" detector; lacks threshold conditions, cross-device awareness
- **Geographically context-heavy:** rain-skip + smoke-pause + seasonal time-shift are PNW-specific values; less relevant in high-desert or tropics

**Criterion #4 refinement (User Demand Signal):**

When evaluating a device-class driver, score demand as *high* (13–15 pts) if:
1. Device is stateful & long-lived (HVAC, irrigation, lights, locks — not one-off sensors)
2. Hubitat's composability unlocks 3+ concrete automation rules Rainbird's app cannot do
3. Device lacks native conditional logic (calendar-based or hardwired rules only)
4. Use case has geographically or seasonally meaningful variability (not uniform across all climates)

**Scoring impact:**
- All four criteria met = prioritize (75–85 rubric score, conditional-fit-high)
- Three criteria met = standard (65–75, conditional-fit-medium)
- Two criteria met = defer (50–65, weak fit)

**Examples:**
- ✅ Rainbird irrigation (4/4) → 78/100 → prioritize
- ✅ Daikin HVAC (3/4: stateful, multi-input, no conditional, but thermal needs are geographically uniform) → 85/100 → prioritize  
- ✅ Gemstone lights (2/4: stateful, multi-input, but lighting lacks heavy seasonal logic in most climates) → 72/100 → conditional
- ❌ Generic temp/humidity sensor (1/4: not stateful in interesting way) → 45/100 → skip

**Filing:** Rainbird use-case essay at `.squad/decisions/inbox/trinity-rainbird-use-cases.md`.

---

### Driver Fit Rubric & House Style Distillation (Trinity)

**Date:** 2026-05-18T15:28:26-07:00

**House Style — What This Repo IS:**
- Single-author maintainable (Mads is one person; every driver must "install and forget")
- Local-first protocol preference (Daikin LAN > cloud; Touchstone Tuya LAN, not MQTT polling)
- Parent/child for multi-device cloud accounts (SunStat pattern: one parent auth, many child thermostats)
- Clean Hubitat ecosystem citizen (event hygiene, state bounds, scheduler discipline, network caution)
- Hardware-tested only (Mads owns or will buy the device; no "untested but should work")
- Graceful degradation (cloud breaks? local survives. Firmware missing endpoint? probe once, disable, don't crash)

**Scoring Rubric (Max 100 pts):**
| Criterion | Pts | Examples |
|-----------|-----|----------|
| Local vs. Cloud | 20 | 20: LAN HTTP/JSON (Daikin, Touchstone). 10: Cloud REST (Gemstone, SunStat). 5: Cloud w/ stability issues. 0: Killed API, MQTT-only. |
| Mads Can Test | 15 | 15: Owns device or <$100. 7: $100–$500. 0: >$500 or unavailable. |
| User Demand | 15 | 15: 2+ forum threads or abandoned prior driver. 10: 1 thread. 5: Mads's idea. 0: No signal. |
| Sandbox-Safe | 15 | 15: Pure Groovy + SDK. 10: Needs long-secret pattern. 5: Workaround feasible. 0: Reflection/JNI/MQTT-subscriber/protobuf. |
| Vendor Stability | 15 | 15: Local API stable >3y (Daikin). 10: Cloud stable (Gemstone). 5: Cloud with breakage history. 0: Killed (MyQ). |
| Effort to Ship | 10 | 10: Local single-device (<40h). 5: Parent/child or multi-device (40–80h). 0: >80h. |
| Maintenance | 10 | 10: Local + vendor docs. 5: Cloud or reverse-eng. 0: Killed API, frequent breaks. |

**Thresholds:**
- 80–100: ✅ Strong Fit (prioritize)
- 65–79: 🟡 Conditional (check disqualifiers)
- 50–64: ❌ Weak (defer)
- <50: 🔴 No Fit

**Hard Disqualifiers (any one = OUT):**
1. Official killed/hostile API (MyQ post-Oct-2023)
2. Reflection/JNI/native libs (sandbox-blocked)
3. Device >$500 or unavailable
4. Browser OAuth2 redirect (use parent/child instead)
5. MQTT persistent subscriber
6. Binary protocol w/o Groovy decoder
7. Safety-critical without audit logging (garage door, lock, gate)
8. Secrets >1KB in preferences (use parent/child pattern)
9. Multi-protocol with undocumented fallback
10. Uses getClass() or sandbox-restricted Groovy

**Cloud-Service Trigger Patterns:**
- **Pattern A:** Cloud-polling parent + children (SunStat example). Best for many devices per account (10+ thermostats). Scalable, 5 min latency.
- **Pattern B:** Cloud-polling single device (Gemstone example). Best for one device per install. Simple, 5 min latency.
- **Pattern C:** Webhook relay (Maker API + driver endpoint). Event-driven (doorbells, motion). <1 sec latency, requires Maker API setup.
- **Pattern D:** Hybrid polling + webhook. Mission-critical + high reliability. Overkill unless service has <99% delivery SLA.

**Workflow:**
1. Cypher generates candidate list (forum demand, protocol research)
2. Trinity scores each against rubric (hard disqualifiers first)
3. Mads picks from shortlist ("80+ club" vs. "conditional fits")
4. Tank implements with Trinity architecture review if needed

**Rubric file:** `.squad/decisions/inbox/trinity-driver-fit-rubric.md`

---

## Team Learnings — May 18, 2026

### Daikin BRP069B Complete Endpoint Catalog (Cypher)
28 total endpoints documented. 7 implemented; 10 intentionally skipped (cloud, scheduling, undocumented, dangerous); 4 deferred (demand control, filter alerts, hourly energy, BRP069A fallback). See `.squad/files/daikin-research/` for full breakdown.

### asynchttpGet Standard for All HTTP (Tank-4)
`asynchttpGet` works for **any HTTP URL** including local LAN. Replaces unreliable Map-based HubAction constructors. Pattern documented in `hubitat-asynchttpget-pattern` skill (confidence: medium). See `drivers/daikin-wifi/daikin-wifi.groovy` v0.1.2+ for reference.

### Thermostat Capability Gaps (Trinity + Peer Survey)
Most SmartThings-era Hubitat thermostat ports missing `supportedThermostatModes` emission and `setpointDisplay` attribute. Daikin passes; will add setpointDisplay v0.1.6. Audit memo: `.squad/files/daikin-research/daikin-ecosystem-survey-memo.md`.

---

## Learnings — 2026-05-18 (MyQ Architecture Sketch)

### Garage Door / Safety-Critical Driver Patterns

- **`Switch` capability on garage doors is a hard no.** Rule Machine's "turn off all switches" automation category makes Switch capability on a garage door actively dangerous. Any new actuator driver for a safety-critical device (garage door, gate, lock) must explicitly skip Switch.
- **Audit-trail logging at INFO is mandatory for safety-critical commands.** Unlike HVAC commands where debug-level gating is fine, `open()` and `close()` on a garage door must always log at `log.info` regardless of `logEnable`. This is an audit trail, not a debug trace.
- **Auto-close timers belong in Rule Machine, not the driver.** The driver's job is state + command; time-based safety logic is a user-defined rule. Never implement auto-close in the driver layer.
- **Rate-limit bidirectional commands on physical-state machines.** For garage doors (and future gate/lock drivers): guard `close()` against already-closing/closed state, and `open()` against already-opening/open state. Double-commanding some opener firmware causes an unintended state reversal.
- **Obstruction detection deserves a first-class attribute.** If the protocol exposes a safety beam or obstruction sensor, surface it as `obstructed: enum ["true","false","unknown"]` and emit at `log.warn` level — not just log.info. Safety events should stand out in logs.

### Cloud-Killed-API Lessons

- **Separate risk tiers clearly.** A cloud driver for a killed API (e.g., MyQ post-Oct-2023) should be labeled "best effort, may break" in its README. Never bundle it with a local hardware path — they have completely different reliability profiles and install requirements.
- **Separate HPM packages for separate hardware paths.** Cloud vs. local = separate `packageManifest.json` files. Users installing ratgdo-garage should never see MyQ-cloud prompts.
- **Local hardware bridge (ratgdo-class) = preferred over cloud reverse-engineering** when hardware cost is reasonable (~$35). The repo's strong local preference applies doubly to safety-critical devices.

### Parent/Child for Multi-Device Cloud Accounts

- **SunStat parent/child pattern applies directly to MyQ cloud.** Account parent (auth + polling) + door children + light children. The only new wrinkle: each child needs a `door` attribute (GarageDoorControl) plus the ContactSensor mirror — same emitIfChanged discipline as thermostats.
- **Light children are a clean separation.** Don't add Light/Switch capability to the door driver. Keep opener light as a separate child with Switch + Light capabilities. Users can control lights independently without triggering door movement.

---

## Next Session Focus

- [ ] Tank: Implement setpointDisplay on Daikin + SunStat (v0.1.6 phase, 0.5h each)
- [ ] Cypher: Validate Daikin BRP069B awayMode support (1h, conditional on API)
- [ ] Mads: Real-device testing on Daikin v0.1.4 BRP069B unit (1–2h, hardware-dependent)
- [ ] Link: Update Daikin + SunStat READMEs with v0.1.6 improvements; bump manifests

---

**Last updated:** 2026-05-18T22:00Z (arc close reskill)
