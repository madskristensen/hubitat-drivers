# Skill: Hubitat Driver Ecosystem-Citizen Audit Checklist

**Confidence:** low  
**Source:** Daikin WiFi v0.1.6 ecosystem-citizen audit (2026-05-18, Trinity). Derives 7-dimension framework applicable to any LAN, cloud, or hybrid driver.  
**Complements:** `hubitat-driver-audit-checklist` (Cypher's internal quality audit). This skill focuses on **ecosystem impact** (how the driver interacts with other Hubitat components), not internal efficiency.

---

## Summary

A 7-dimensional checklist for evaluating whether a driver is a "good citizen" of the Hubitat ecosystem. Use this when auditing drivers for cross-driver consistency, adoption readiness, and resource-management hygiene.

| Dimension | Concern | Why |
|-----------|---------|-----|
| **A. Event-DB chatter** | Spam, missing descriptions, throttle violations | Users see every event in Events tab; spam = unusable |
| **B. State-DB churn** | Unbounded growth, unnecessary persistence, dead writes | State writes hit Hubitat's SQLite; expensive on hubs with 50+ drivers |
| **C. Scheduler load** | Accumulating duplicate schedules, forgot cleanup | Forgot unschedule = 2× jobs on update, growing memory leak |
| **D. Network behavior** | Timeouts, concurrent limits, error backoff | Can exhaust Hubitat's async-http pool or overwhelm LAN device |
| **E. Lifecycle hygiene** | Missing installed()/updated()/uninstalled(), no hub reboot survival | Drivers that don't clean up leak resources; hub reboot = dead polling |
| **F. App compatibility** | Null crashes in setters, wrong attribute types, missing schema events | Rule Machine crashes, dashboards render blanks, apps don't discover driver |
| **G. Resource hygiene** | Overflow, spam logging, unbounded collections | Numeric overflow breaks timers; excessive logging fills hubs' flash |

---

## Dimension A — Event-DB Chatter

**Checklist:**
- [ ] `sendEvent()` call count under typical polling — peak should be <10 events/cycle
- [ ] Do ALL parse paths (not just command handlers) route through `emitIfChanged()`?
- [ ] `lastActivity` attribute: is it throttled ≥60 seconds between emissions? (if exposed)
- [ ] Every `sendEvent()` includes `descriptionText:`? Uses `${device.displayName}` (dynamic)?
- [ ] Debug/trace logs gated by preference (not spamming at INFO level)?

**Green flags:**
- ✅ ~3–7 events per 5-min polling cycle when state changes; 0 when unchanged
- ✅ `lastActivity` emitted at most once per 60s
- ✅ descriptionText is human-readable ("turned on", "mode → heat", "temp → 72°F")
- ✅ `logEnable`/`traceEnable` preferences auto-disable after 30 min

**Red flags:**
- ❌ >20 events per poll cycle
- ❌ `sendEvent()` calls without descriptionText
- ❌ Hardcoded device names in descriptionText (breaks on user rename)
- ❌ Raw JSON in descriptionText (illegible in Events tab)
- ❌ `lastActivity` emitted on every response (no throttle)
- ❌ Debug logs at INFO level; user can't disable them

---

## Dimension B — State-DB Churn

**Checklist:**
- [ ] Count state writes that fire on EVERY poll cycle (not just init)
- [ ] Any unnecessary persistence? (e.g., caching API response bodies, non-curated collections)
- [ ] Stale/orphaned state keys accumulating over time?
- [ ] Any state writes that are never read afterward (dead writes)?

**Green flags:**
- ✅ 0–1 state writes per normal poll (excluding transient operations like ping)
- ✅ Only curated, user-visible data in state (favorites, not full catalog)
- ✅ All state keys are fixed and small (no lists growing forever)
- ✅ Upgrade path cleans stale keys (e.g., pruneNonFavoriteStateEntries on initialize)

**Red flags:**
- ❌ 5+ state writes per poll cycle
- ❌ Full API response catalog cached in state (pollutes State Variables panel)
- ❌ Lists/maps growing without bound (favorites should be curated only)
- ❌ Dead writes (write but never read afterward)

**Example:** Gemstone caches full light-effect catalog (50–150 entries) in state. Better: cache only favorites (5–15), on-demand fetch non-favorites into local variables.

---

## Dimension C — Scheduler Load

**Checklist:**
- [ ] How many `runEvery*` registrations exist simultaneously?
- [ ] Does `initialize()` call `unschedule()` BEFORE registering?
- [ ] Any `runIn()` calls that forget to unschedule themselves?
- [ ] Peak transient one-shot jobs during single poll cycle?

**Green flags:**
- ✅ 2–3 base persistent schedules (refresh, energy, etc.)
- ✅ `initialize()` calls `unschedule()` first
- ✅ One-shot `runIn()` jobs complete naturally (auto-cleanup)
- ✅ Transient peak: 0–3 one-shots during a single cycle

**Red flags:**
- ❌ 5+ base persistent schedules
- ❌ `updated()` calls `registerSchedules()` without `unschedule()` first (duplicate jobs accumulate)
- ❌ `runIn()` calls without corresponding unschedule (memory leak on repeat calls)
- ❌ Job scheduling inside `initialize()` without clearing state — orphan lingering from prior init

**Example:** Daikin v0.1.0 had schedule-property-shadowing bug: `schedule()` method call was shadowed by `setSchedule()` write-only property. Tank-3 fixed with `runEvery*` idiom instead.

---

## Dimension D — Network Behavior

**Checklist:**
- [ ] Polling rate: is it tunable (preference)? Can user back off if device rate-limits?
- [ ] Every async HTTP call has a timeout? (10s typical for LAN, 30s for cloud)
- [ ] Peak concurrent requests: how many in-flight at once?
- [ ] Offline device: does driver keep retrying, or crash? Error log volume?

**Green flags:**
- ✅ Refresh interval configurable (1/5/10/15/30 min; default sane like 5 min)
- ✅ asynchttpGet or async HubAction with 10s timeout (LAN) or 30s (cloud)
- ✅ Peak concurrent: 3–5 requests typical LAN; 1–2 typical cloud
- ✅ Offline = keeps retrying every cycle; logs 1 warning per failure (not spam)

**Red flags:**
- ❌ Hardcoded 1-min polling (no user tuning)
- ❌ No timeout on async calls (Hubitat default is 20s; explicit 10s is better)
- ❌ 10+ concurrent requests at peak (Hubitat's limit is ~10 for asynchttpGet)
- ❌ Offline = crashes, or logs 1000s of warnings per hour
- ❌ Aggressive retries without backoff (hammers Daikin or cloud API)

**Example:** Daikin v0.1.6 separates energy poll (30 min) from control poll (5 min, user-tunable) so energy doesn't get rate-limited by aggressive polling.

---

## Dimension E — Lifecycle Hygiene

**Checklist:**
- [ ] `installed()` exists, initializes device state, calls `initialize()`?
- [ ] `updated()` calls `initialize()` or `unschedule()` + re-register before `registerSchedules()`?
- [ ] `uninstalled()` exists, calls `unschedule()`, cleans persistent connections?
- [ ] Hub reboot survival: will polling resume without user action?

**Green flags:**
- ✅ `installed()` → sets defaults → calls `initialize()`
- ✅ `updated()` → calls `initialize()` (which unschedules first)
- ✅ `uninstalled()` → calls `unschedule()`
- ✅ Hub reboot → Hubitat re-runs `updated()` → schedules re-register ✅

**Red flags:**
- ❌ No `installed()` (driver skips defaults)
- ❌ `updated()` never calls `unschedule()` (duplicate schedules accumulate on save)
- ❌ No `uninstalled()` (orphan schedules, connections persist)
- ❌ Hub reboot → polling never resumes (schedules not re-registered)

**Example:** Tank-3 lesson: always call `unschedule()` in `initialize()`. On hub reboot, Hubitat calls `updated()` → `initialize()` → `unschedule()` + re-register in one clean sequence.

---

## Dimension F — App Compatibility

**Checklist:**
- [ ] Command safety: setpoint/value setters guard against null, NaN, out-of-range?
- [ ] Thermostat drivers: emit `supportedThermostatModes` on install/update?
- [ ] Attributes: correct types (NUMBER vs STRING vs ENUM)? Dashboard tile-compatible?
- [ ] Hub Mesh: all attributes use simple types (no JSON objects in attribute values)?
- [ ] Automation: does driver fire events that Rule Machine and HSM can monitor?

**Green flags:**
- ✅ `setHeatingSetpoint(temp)` checks `temp == null`, `temp.isNaN()`, range [5–40 °C for Daikin]
- ✅ `supportedThermostatModes` emitted once on install, re-emitted on update
- ✅ `temperature`, `heatingSetpoint` → NUMBER; `thermostatMode` → ENUM; `lastActivity` → STRING
- ✅ No complex objects in attribute values (maps/lists belong in state, not attributes)
- ✅ `temperature` and `switch` changes fire events (HSM can trigger on these)

**Red flags:**
- ❌ `setHeatingSetpoint(null)` → crashes with NPE (no guard)
- ❌ `supportedThermostatModes` never emitted (Rule Machine lists no modes available)
- ❌ `thermostatMode` as STRING instead of ENUM (dashboard mode selector doesn't work)
- ❌ Large JSON object stored in attribute value (Hub Mesh can't serialize)
- ❌ No events fired (automation apps can't react to state changes)

**Example:** Cypher-2 found setpoint null-guard bug in Daikin v0.1.5 (fixed in v0.1.6): Rule Machine's "clear" command sends null; driver must guard before `new BigDecimal(temp.toString())`.

---

## Dimension G — Resource Hygiene

**Checklist:**
- [ ] Unbounded growth: any lists/maps that grow without bound?
- [ ] Timestamp safety: using Long (ms) not Int (seconds)? Won't overflow for years?
- [ ] Logging levels: INFO for user actions, WARN for errors, DEBUG gated by pref?
- [ ] Peak memory usage: reasonable for long-running drivers?

**Green flags:**
- ✅ All state keys fixed in count (no lists growing forever)
- ✅ Timestamps use Long (64-bit ms); won't overflow for ~292 million years
- ✅ log.info for user-visible actions; log.warn for errors; log.debug gated by preference
- ✅ Per-device state <1 KB typically (flags, few attributes, small maps)

**Red flags:**
- ❌ `state.history = state.history ?: []; state.history << newEvent` (accumulating list)
- ❌ Timestamps as Int seconds (overflows in year 2038, though Hubitat's Java runtime is 64-bit)
- ❌ `log.debug` without gating (fills live log even when user didn't enable debug mode)
- ❌ >100 KB state per device

**Example:** Touchstone v0.1.28 removed dead `state.lastDps` assignment (never read after write). Cleaned up State Variables panel and reduced state churn on every frame.

---

## Scoring Rubric (Optional)

For a quick pass/fail on adoption readiness:

| Dimension | Pass Criteria | Score |
|-----------|---------------|-------|
| A. Event-DB chatter | <10 events/cycle, emitIfChanged on all paths, descriptionText universal | 15 pts |
| B. State-DB churn | <2 writes/cycle, no unbounded collections | 15 pts |
| C. Scheduler load | 2–3 base schedules, initialize() unschedules first | 15 pts |
| D. Network behavior | Tunable rate, 10s timeout, <5 concurrent, offline keeps retrying | 15 pts |
| E. Lifecycle hygiene | installed/updated/uninstalled present, hub reboot works | 15 pts |
| F. App compatibility | Null guards, schema events, correct types, Hub Mesh OK | 15 pts |
| G. Resource hygiene | No unbounded growth, safe timestamps, debug logs gated | 10 pts |
| **TOTAL** | | **100 pts** |

**Adoption readiness:** 85+ points = ship; 70–84 = review findings, fix blockers; <70 = rework required.

---

## Reference Implementations

| Driver | Link | Pattern | Notes |
|--------|------|---------|-------|
| Daikin WiFi v0.1.6 | `drivers/daikin-wifi/daikin-wifi.groovy` | LAN polling | All 7 dimensions pass; production-ready |
| Touchstone Fireplace v0.1.28 | `drivers/touchstone-fireplace/touchstone-fireplace.groovy` | LAN socket | Full HealthCheck + ping pattern; 2026-05-18 state cleanup |
| Gemstone Lights v0.4.15 | `drivers/gemstone-lights/gemstone-lights.groovy` | Cloud REST | lastActivity only; catalog deduplication lesson |
| SunStat v0.1.10 | `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy` | Cloud + parent/child | Parent/child activity cascading |

---

## Decision Flowchart

```
Does the driver have async HTTP polling?
  YES → Check dimensions A, B, C, D, E, F, G
  NO  → Skip D (network), focus on E (lifecycle), F (app), G (resources)

Does the driver expose Thermostat capability?
  YES → Must pass F (app compatibility) — emit supportedThermostatModes
  NO  → Still check F for null guards + attribute types

Does the driver run on hub reboot?
  YES → Must pass E (lifecycle) — initialize() re-registers schedules
  NO  → Clarify: transient? Reinstall required? Document in README

Will the driver run on 50+ hubs in a large home?
  YES → Must pass B (state-DB churn) and G (resource) — every byte/cycle counts
  NO  → Still follow best practices; easier to maintain if reused later
```

---

## Usage

Apply this checklist when:
1. Auditing a new driver before first release (adoption readiness review)
2. Reviewing driver PRs for cross-repo consistency
3. Diagnosing performance issues (look at dimensions B, C, G first)
4. Planning driver maintenance (identify tech debt by dimension)

**Time estimate:** 30–60 min per driver, depending on code size.

---

## Cross-References

- **Cypher's audit checklist:** `hubitat-driver-audit-checklist` — focuses on internal code quality (idiomatic Groovy, error handling, test coverage). Complements this skill.
- **Event hygiene:** `hubitat-event-hygiene` — descriptionText, emitIfChanged patterns
- **State hygiene:** `hubitat-state-hygiene` — persistent state minimization
- **Async HTTP:** `hubitat-asynchttpget-pattern` — asynchttpGet best practices
- **Health monitoring:** `hubitat-healthcheck-vs-lastactivity` — when to use HealthCheck vs lastActivity

---

**Last updated:** 2026-05-18  
**Validated on:** Daikin WiFi v0.1.6, Touchstone v0.1.28, Gemstone v0.4.15, SunStat v0.1.10
