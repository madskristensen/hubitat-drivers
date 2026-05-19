# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation home automation hubs.
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Recent Projects & Decisions

**For detailed learnings from prior sessions (Daikin, Gemstone, SunStat, MyQ, Rainbird, Bosch, OAuth, driver rubric), see history-archive.md.**

### 2026-05-18 — Community Driver Code-Quality Audits (3 drivers)

Trinity audited three drivers Mads runs in production against this repo's best-practice patterns and skill library.

**Verdicts:**
1. **pfmiller0 PurpleAir AQI Virtual Sensor** (v1.3.2, ~500 lines) — **PR upstream**
   - 3 BLOCKERs: AQ&U conversion string (`"AQ and U"` vs `"AQ&U"` case mismatch), LRAPA/Woodsmoke string cases (lowercase vs title-case), failCount operator precedence (`?:0 + 1` should be `(?: 0) + 1`)
   - 6 MINORs: missing lastActivity, no emitIfChanged, sparse descriptionText, type declarations
   - Maintainer active (last commit 2025-06-18) — PRs viable
   - Change size: MEDIUM (~60–90 lines diff)

2. **GvnCampbell Fully Kiosk Browser Controller** (v1.41, ~350 lines) — **FORK to `drivers/fully-kiosk/`**
   - 3 MAJORs: serverPassword cleartext in UI + debug logs (security), 5,760+ events/day from refreshCallback (no emitIfChanged), missing descriptionText on all parse() events
   - Maintainer silent 4.5 years (last commit 2021-11-20)
   - Mads runs 2 instances (Bathroom tablet, Kitchen tablet) — orphaned code needs ownership
   - Change size: MEDIUM (~80–120 lines diff)

3. **djdizzyd Advanced Honeywell T6 Pro Thermostat** (v1.2, ~500 lines) — **FORK to `drivers/honeywell-t6-pro/`**
   - 1 BLOCKER: txtEnable preference never declared — all informational log statements permanently silenced
   - 3 MAJORs: currentValue() method reference (missing attribute name) in fan-state logic, zombie syncClock schedulers in configure(), obsolete configurationGet(52) dead code
   - **⚠️ CRITICAL:** Honeywell BLOCKER affects Mads's Downstairs thermostat today
   - Maintainer silent 4+ years (last commit 2021-01-22)
   - Change size: MEDIUM (~80–100 lines diff)

**Audit files:** All three decisions stored in `.squad/decisions.md` with exact line citations for each finding.

---

## Core Patterns (Reusable)

1. **Parent/Child OAuth:**
   - Parent holds state.accessToken, state.refreshToken, state.tokenExpiresAt
   - getValidToken() guard before every HTTP call; refresh if now > expiresAt - 300
   - Children store cloud device IDs as DataValue("cloudDeviceId")
   - isComponent: false on child creation

2. **API Response Handling:**
   - Check response.getErrorData() for 4xx responses
   - Unwrap envelopes in dedicated helpers
   - Log diagnostic details on every response path

3. **Capability Metadata:**
   - Use distinct command names to avoid WebCoRE overload shadowing
   - Every sendEvent needs descriptionText
   - Standard capabilities + custom attributes for non-standard state

4. **State & Preference Hygiene:**
   - Tokens >1KB: use command parameter not preference
   - Transient working data: local variables, not state.*

5. **Async HTTP (LAN & Cloud):**
   - Use asynchttpGet by default (stable across all Hubitat firmware versions)
   - 10s timeout for LAN HTTP, 30s for cloud REST
   - Never use Map-based HubAction constructors

6. **Health Monitoring:**
   - **Local sockets:** Full HealthCheck capability + ping()
   - **Cloud REST:** lastActivity only (no quota-consuming pings)
   - Parent/child: parent cascades lastActivity to all children

7. **Idempotency & Lifecycle:**
   - Lifecycle-driven writes are highest risk for duplicates
   - Guard against audible side effects before `on()`

8. **Write-Only Property Gotcha:**
   - `setX(x)` command creates a write-only property x on driver object
   - Can shadow Groovy method dispatch if code calls scheduler method `x()`
   - Workaround: use `runEvery*` idioms instead of `schedule(cron, method)`

---

**Last updated:** 2026-05-18T23:45:00Z (community driver audits merged to decisions.md)

---

## Cross-Agent Update — Your 3 Code-Quality Audits Shipped as Tank Forks (2026-05-18T17:25:00Z)

**From:** Scribe housekeeping merge (post-Tank session)

Your 3 community driver audits have all shipped as Tank-produced forks:

1. **Honeywell T6 Pro** (commit 1dc51af) — permanent fork to `drivers/honeywell-t6-pro/`
   - Fixes per your line citations: txtEnable BLOCKER + fan-state currentValue() method call fix + syncClock scheduler leak fix
   - No regressions introduced — Tank kept minimum-change discipline

2. **Fully Kiosk Browser Controller** (commit 32a9f2c) — permanent fork to `drivers/fully-kiosk/`
   - Fixes per your line citations: password security mask + event-hygiene emitIfChanged + descriptionText cascade + logger enum reversal
   - No regressions introduced — minimum-change discipline

3. **PurpleAir AQI Virtual Sensor** (commit ff3410f) — **PR-bound staging fork** to `drivers/purpleair-aqi/`
   - Fixes per your line citations: AQ&U case fix + LRAPA/Woodsmoke case fixes + failCount precedence fix
   - Upstream PR draft staged at `drivers/purpleair-aqi/UPSTREAM-PR-DRAFT.md` — ready for Mads to submit after local validation
   - Delete this fork from repo once pfmiller accepts upstream PR

**All three audits validated against your line citations. Ready for Switch's hardware validation before any upstream PR submission.**
