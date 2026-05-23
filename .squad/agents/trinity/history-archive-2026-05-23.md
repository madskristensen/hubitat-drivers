# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation home automation hubs.
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Recent Projects & Decisions

**2026-05-19 — Cross-Agent Note: auth-before-dedup Pattern (Gemstone Stale-Token Fix)**  
Trinity: This pattern may generalize to other cloud drivers (SunStat, PurpleAir, Daikin, Rainbird, Bosch). See `.squad/skills/auth-before-dedup/SKILL.md` and `.squad/decisions.md` (2026-05-19 entry) for the Gemstone implementation. The antipattern is dedup guards that return before reaching session-validity checks; consider auditing other cloud drivers for the same vulnerability.

**2026-05-19 — Reskill Pass** — Extracted 5 new skills to `.squad/skills/`: async HTTP error handling, temperature scale honoring, refresh-on-save, HPM root registration, geospatial distance math. Commit 1bf5844.

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

---

## Learnings — Post-Fork Code Review (2026-05-18T17:30:00-07:00)

Trinity reviewed all three Tank forks against original audit line citations. Full report: `.squad/decisions/inbox/trinity-post-fork-code-review.md`

**Per-driver verdicts:**

1. **Honeywell T6 Pro** (commit 1dc51af) — **SHIP. No regressions.** All 3 fixes verified: txtEnable declared with `defaultValue:true` (correct), both `device.currentValue("thermostatOperatingState")` arg fixes working, `unschedule("syncClock")` in configure() is correctly targeted and does not kill other jobs. Production thermostat is safe. Top deferred: add `descriptionText` to temperature/humidity events (Events tab blank for most-watched attributes).

2. **Fully Kiosk** (commit 32a9f2c) — **SHIP. No regressions.** All 4 fixes verified: password type+mask correct (regex `/(?i)password=[^&]+/`), emitIfChanged canonical implementation applied to all 4 refreshCallback attributes, descriptionText on all parse-path sendEvents, logger replacement correct. Top deferred: `logEnable` defaults to `true` with no auto-disable — noisy citizenship; flip to `false` and add `logsOff` in v0.2.0.

3. **PurpleAir AQI** (commit ff3410f) — **SHIP (temporary fork). No regressions.** All 3 fixes verified: AQ&U string match fixed, LRAPA/Woodsmoke case + pm2.5_cf_1 field both corrected consistently across `sensorCheck()` and `apply_conversion()`, failCount parens force correct precedence. Top deferred: no `emitIfChanged` on sendEvents — 35,040 duplicate events/year at default 1-hr cadence.

**Reusable pattern identified:** Post-fork verification should always check (1) fix comments cite the right line, (2) behavior preserved on non-fixed paths, (3) scheduler changes are targeted not broad, (4) numeric equality comparisons use BigDecimal not string conversion. Added as checklist pattern in review doc.

---

## 2026-05-18T17:50:00Z — Post-Audit Impact Summary

Your post-fork audit drove the v0.2.0 polish across all 3 drivers. All 15 of your deferred-improvement items were applied by Tank in parallel:

**Honeywell T6 Pro (5 items applied):**
- ✅ C1: Add descriptionText to temperature/humidity events
- ✅ C2: ventProcess() numeric comparison via BigDecimal (prevent 68 vs 68.0 false-positives)
- ✅ C3: Remove configurationGet(52) dead code
- ✅ C4: Add descriptionText to supportedThermostat* events
- ✅ C5: Remove duplicate syncClock in refresh() (carried over by Trinity request; low priority)

**Fully Kiosk (4 items applied + 1 partial):**
- ✅ C1: Add descriptionText to all checkInterval sendEvents + parse() events
- ✅ C2: Add logsOff auto-disable with 30-min timeout; flip logEnable default to alse
- ✅ C3: Document LAN password-in-URI pattern in README
- ✅ C4: Revise checkInterval from 60 to 120 seconds for 1-min polling cadence
- ⚠️ C5: setLevel event before HTTP 200 — documented as "low practical impact" but left as deferred (optimistic event is repo pattern elsewhere)

**PurpleAir AQI (3 items applied + 2 carryover):**
- ✅ C1: Add mitIfChanged to all httpResponse() sendEvents (eliminates 35,040 duplicate events/year)
- ✅ C2: Include device.displayName in sites event descriptionText
- ✅ C5: Fix "IQAir" log prefix → "PurpleAir" (copy-paste artifact)
- ⚠️ C3: Password cleartext in URI — documented, not a fixable bug (Fully Kiosk API design)
- ⚠️ C4: UUID in packageManifest.json — deferred (fork is temporary pending PR decision)

**Coordinate with Switch for v0.2.0 hardware validation.** Clean diff available in commits ac5b939 / 0e9f8ed / 4b720aa whenever you want to audit the polish pass results.

---

## 2026-05-18 — PurpleAir v0.3.0 Deep Quality Audit

- Groovy settings values from enum preferences stay as strings; retry math like `update_interval * failCount` becomes string repetition (`"60" * 5` → `"6060606060"`) unless the value is coerced before multiplication. Audit every backoff path for this class of bug.
- Geospatial helper math is easy to get subtly wrong in Hubitat drivers. PurpleAir's search-box helper swapped latitude vs. longitude miles-per-degree, and weighted averaging needs an explicit zero-distance guard so an exact coordinate match does not produce `NaN` / no-data results.
- Cloud-poll drivers should treat `lastActivity` as a coarse freshness signal and keep disabled polling truly disabled even on error paths. Manual refresh and retry code are where schedule leaks tend to sneak back in.
- If a driver caches sensor history in `state`, key it by a stable sensor identifier and prune stale entries. Human-readable site names are not stable enough for long-lived health heuristics.
- Repo release hygiene still matters at the driver header level: keep each top-of-file `Changelog:` entry as one parsable `version — YYYY-MM-DD — description` line so release-note extraction does not depend on wrapped continuation lines.

## Session Arc 2026-05-19: PurpleAir Production Bug Audit → v0.4.0 Shipping

**Trinity #6 — Post-v0.3.0 Bug Audit:** Identified 5 production bugs for v0.4.0 scope:
1. String-math retry backoff (Groovy String * Integer trap)
2. Disabled polling retry storm (error callbacks triggering reschedules)
3. distance2degrees() pole clamp (missing pole-handling math)
4. Zero-distance averaging (divide-by-zero on sensor co-location)
5. Async body guards (empty PurpleAir API response crash risk)

**Outcome:** All 5 bugs shipped fixed in Tank #22 (PurpleAir v0.4.0, commit 2d62b05). Quality audit complete; driver production-ready.

**Deliverables:** Orchestration log created (.squad/orchestration-log/2026-05-19-043500Z-trinity-6.md)

---

## Learnings — Climate Advisor Architecture (2026-05-23T14:56:54-07:00)

**Source:** Architecture proposal for Mads's climate control system. Full proposal in `.squad/decisions/inbox/trinity-climate-advisor-architecture.md`.

### Key Decisions Made

1. **Parent App + Child Virtual Device is the correct pattern for multi-device logic.** Drivers cannot subscribe to external device events — a hard platform constraint. Any feature that subscribes to 5+ external devices must be an App. The App is the brain; the child virtual device is the capability surface that SharpTools and HomeKit see.

2. **ContactSensor is the best HomeKit proxy capability for an advisor/alert device.** `contact: "open"` = alert active; `contact: "closed"` = all clear. homebridge-hubitat-tonesto7 and the official Hubitat HomeKit integration both map this to HomeKit Contact Sensor. Semantic is defensible. SmokeDetector/CO capabilities are technically possible but semantically abusive and will alarm users incorrectly.

3. **Layered capability pattern for advisory devices:**
   - `capability "ContactSensor"` — HomeKit visibility, binary alert signal
   - `capability "Sensor"` — Hubitat marker for "reads data"
   - Custom attributes: `severity` (NUMBER 0–3), `severityText` (ENUM), `latestMessage` (STRING), `messages` (STRING — JSON array), `houseStatus` (STRING — backward compat), `tempTrend` (ENUM)

4. **All-clear explicit message matters.** Always emit "All clear — no climate issues detected" when no problems exist. Dashboard tiles should never be blank.

5. **Ring buffer for temperature trend in app state.** Keep N samples (default 12) over M minutes (default 30). Compute slope. State cost is ~540 bytes — negligible. Trend is exposed as a `tempTrend` attribute on the child device for Rule Machine / SharpTools consumption.

6. **Per-zone contact grouping via app preferences.** Each zone (Upstairs/Downstairs/Sunroom) has its own thermostat + contacts list. Evaluate zones independently; aggregate max severity. This allows zone-specific messages.

7. **houseStatus attribute is a permanent first-class attribute**, not a migration shim. SharpTools tile configs are time-consuming to rebuild; keeping the attribute stable eliminates migration risk.

8. **Write commands (addMessage/clearMessage) should be private app-side methods, NOT device commands.** Exposing them as commands lets WebCoRE/RM bypass throttle and dedup logic. The app owns the write path.

9. **Thermostat mode awareness is mandatory.** When thermostatMode=off (Upstairs and Sunroom are both off today), setpoint-based hot/cold alerts must be suppressed. Rain and AQI alerts still apply. Mode=fan on Daikin is equivalent to off for setpoint logic.

10. **Daikin setpoints are BigDecimal (64.4, 75.2); T6 Pro setpoints are integers (69, 75).** The evaluation logic must coerce all setpoint reads to BigDecimal before comparison. This is a Tank implementation note.

11. **Two outdoor data sources serve different roles.** `Backyard sensor` (Philio PAT02-B, Z-Wave, event-driven) is the primary outdoor temp source. `Weather` (OpenWeatherMap) is the rain source only — it has a `weather` attribute but its temperature reading is cloud-polled and less accurate. Never mix the two sources for temperature.

12. **PurpleAir driver uses custom `aqi` attribute, not Hubitat's standard `airQualityIndex`.** App must read it via a configurable attribute name preference (default: `"aqi"`) to avoid breaking if the driver changes.

13. **Downstairs zone has ambiguous doors.** `Bathroom shower door` (interior) and `Garage/sunroom door` (zone-crossing) should be excluded by default from the contact lists. The app's `multiple: true` inputs give Mads full control — no need for an explicit exclude list in code.

14. **Sonos Advanced supports `capability.speechSynthesis` for TTS.** The app can call `speak(text)` directly, replacing the webCoRE speaker piston dependency entirely. Severity threshold for audio announcements should default to 2 (warning+) — severity 1 (info suggestions) should be SharpTools display only.

