# Skill: hubitat-driver-perf-audit-checklist

**Confidence:** low  
**Validated:** 2026-05-18 — Daikin WiFi v0.1.5 audit by Cypher; complements Trinity's citizen-checklist  
**Author:** Cypher

---

## Summary

A **systematic methodology** for auditing Hubitat driver performance and code quality. Complements the high-level citizen checklist with lower-level operational concerns: state churn, polling load, network behavior, and lifecycle ordering.

**Use when:** Shipping a driver that polls devices frequently or manages complex state. Not required for every driver, but captures risks that code review alone might miss.

---

## The Audit Checklist

### Phase 1: Performance Instrumentation

| Check | Method | Notes |
|---|---|---|
| **Polling cadence** | Grep for `runEvery`, `schedule`, `poll()` | Identify all recurring timers. Map to endpoint load. |
| **State writes per cycle** | Trace `state.x =` statements | Count state mutations in hot paths (poll handlers). 1–2 per cycle OK; >5 is suspicious. |
| **HTTP calls per cycle** | List all `asynchttpGet` / `asynchttpPost` calls | Identify if any are conditional or batched. |
| **Callback frequency** | Check `runIn()` / delay-schedule callbacks | Verify they don't reschedule themselves infinitely. |
| **Schedule ordering** | Verify `unschedule()` before `registerSchedules()` | Prevents duplicate schedules on `updated()` re-entry. |

### Phase 2: Code Quality (Defensive)

| Check | Pattern | Notes |
|---|---|---|
| **Null guards** | Search for `?.` and `if (x != null)` | Verify parses are guarded: `if (temp?.isNumber()) { parse }` |
| **Magic numbers** | Search for bare `10`, `5`, `300` | Should be `@Field static final TIMEOUT_SECONDS = 10` |
| **Dead computation** | Verify all computed values are used | If computed but never emitted/logged, it's dead code. |
| **Logging gates** | Verify `log.debug` behind `logEnable` check | All debug logging should be conditional. Trace logging behind `traceEnable`. |
| **Sandbox compliance** | Search for: `HubAction(Map`, `System.arraycopy`, `java.util.zip`, `java.lang.reflect` | All are blocklisted on current Hubitat firmware. |
| **Event hygiene** | Per Trinity's citizen-checklist | Verify: `emitIfChanged()`, no `displayed: false`, no `isStateChange: true` abuse. |

### Phase 3: Polling Behavior

| Check | Pattern | Notes |
|---|---|---|
| **Conditional polling** | Do endpoints skip when device is off/unavailable? | E.g., "don't poll energy if switch=='off'". Saves 2 calls per 30-min cycle. |
| **Polling boundaries** | Verify calls only happen when device is reachable | If device times out, does the poll retry or backoff? |
| **Stale read-back race** | Check setpoint write → read-back sequence | If a write is followed by an immediate poll, the poll may read the stale (pre-write) state, emit it, then emit the correct state 2s later. `emitIfChanged()` limits chatter but worth noting. |

### Phase 4: Lifecycle & Initialization

| Check | Pattern | Notes |
|---|---|---|
| **initialize() idempotence** | Verify `initialize()` can be called multiple times | Unschedule all timers before registering new ones. |
| **State initialization** | Verify all `state.x` reads have defaults | E.g., `state.rxBuffer ?: ""` to prevent NPE on first run. |
| **Callback routing** | Verify all async callbacks are defined as methods | Typos in callback names will silently fail (no compile-time check). |

### Phase 5: Network Resilience

| Check | Pattern | Notes |
|---|---|---|
| **HTTP timeouts** | Check `asynchttpGet(..., timeout: 10)` | 10 seconds is reasonable for LAN. Verify not too short (triggers false negatives) or too long (driver hangs). |
| **Error handling** | Verify `if (response.hasError())` checks | All asynchttpGet callbacks should check for network errors. |
| **Retry logic** | Check if callbacks reschedule on failure | Some drivers backoff and retry; others accept one failure and move on. Document the choice. |

---

## Worked Example: Daikin WiFi v0.1.5

From `.squad/files/daikin-research/daikin-api-perf-audit-memo.md`:

```
P1: state.lastActivityEmittedAt written every poll (emitLastActivity, line 762)
    → Design: HealthCheck throttle pattern, ~12 writes/hour at 5-min cadence
    → Verdict: Fine as-is (by design)

P2: refreshEnergy() fires even when device is off (line 385)
    → 2 HTTP calls per 30-min cycle unnecessarily
    → Fix: Add guard: if (device.currentValue("switch") == "off") return
    → Effort: 5 min

Q1: setHeatingSetpoint(null) crashes (line 340)
    → Rule Machine passes null on clear; BigDecimal(null.toString()) → NPE
    → Fix: if (temp == null) { log.warn "..."; return }
    → Effort: 30 min (high-value safety fix)
```

---

## Typical Findings

Most drivers pass with minor findings:

- ✅ **Fine as-is:** Schedule ordering, callback routing, sandbox compliance, event hygiene
- 🟡 **Worth fixing:** Dead computation removal, conditional polling guards, missing null checks
- 🔴 **Hot-fix:** Crashes (NPE, NumberFormatException), infinite loops, severe perf regressions

---

## Integration with Other Skills

- **Trinity's citizen-checklist** → Event hygiene, basic code review
- **hubitat-event-hygiene** → Detailed emitIfChanged patterns
- **hubitat-asynchttpget-pattern** → Callback handler guards
- **hubitat-sentinel-value-guards** → Numeric parse safety

---

## References

- Daikin audit: `.squad/files/daikin-research/daikin-api-perf-audit-memo.md`
- Trinity's citizen checklist: `.squad/skills/hubitat-driver-citizen-checklist/SKILL.md`
