---
author: tank
date: 2026-05-18T07:56:18-07:00
status: ready-for-review
subject: Touchstone v0.1.25 — skip redundant on()/off() DP writes (audit T-2, T-3)
---

# Touchstone — Idempotent on()/off() (T-2, T-3)

## Context

Trinity's redundant-write audit identified two findings in the Touchstone Fireplace driver:

- **T-2 🔴** — `on()` unconditionally sends DP1=true even when the switch is already on. Tuya fireplaces emit an audible click on every DP1 write regardless of current state. Rules that re-assert `on` (common in mode-based automations) produce repeated click artifacts.
- **T-3 🟡** — `off()` unconditionally sends DP1=false even when the switch is already off. No audible artifact, but generates unnecessary wire traffic and can interfere with DP echo sequencing.

## Decision

Add an early-return guard at the top of `on()` and `off()` (after the null-profile check, before any state mutations or DP writes) that returns silently when the device is already in the requested state:

```groovy
// on()
if (device.currentValue("switch") == "on") {
    debugLog "on(): device already on — skipping DP write"
    return
}

// off()
if (device.currentValue("switch") == "off") {
    debugLog "off(): device already off — skipping DP write"
    return
}
```

## applyOnPowerOnDefaults Reschedule Decision

`on()` calls `runInMillis(POWER_ON_DEFAULTS_DELAY_MILLIS, "applyOnPowerOnDefaults")` after the DP write. The early-return bypasses this scheduling.

**Decision: skip the runIn when already on.**

Rationale:
- If the device is already on, the power-on defaults were applied during the prior `on()` invocation. Rescheduling them on a redundant `on()` call would re-fire `applyOnPowerOnDefaults` unnecessarily — the same redundant-write problem the v0.1.23/0.1.24 audit addressed.
- If a user believes values have drifted since the last power-on, the correct recovery path is toggle off → on, which forces a full transition including the defaults schedule. This is the idiomatic "re-apply defaults" gesture and is consistent with how the v0.1.23 defaults guard was designed.
- Adding a conditional re-runIn (e.g., "reschedule only if uptime > threshold") would introduce stateful complexity with no measurable benefit and contradicts the audit's skip-if-match philosophy.

## Consistency with Prior Art

Same pattern as v0.1.23 (`applyOnPowerOnDefaults` per-DP guards) and v0.1.24 (`defaultHeatingSetpoint` guard). All use `device.currentValue()` as the idempotency key and log at `debug` level on skip.

## Files Changed

- `drivers/touchstone-fireplace/touchstone-fireplace.groovy` — v0.1.25

## Findings Closed

- T-2 🔴 (audible artifact on redundant `on()`)
- T-3 🟡 (wire traffic on redundant `off()`)
