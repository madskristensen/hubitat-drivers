# Decision: Touchstone v0.1.26 — Batch-fix audit T-4 through T-10

**Date:** 2026-05-18  
**Author:** Tank  
**Driver:** drivers/touchstone-fireplace/touchstone-fireplace.groovy  
**Version:** 0.1.26

## Summary

Applied the v0.1.23 skip-if-match idempotency pattern to seven user-explicit command paths that were previously writing the device DP unconditionally (Trinity's 🟡 findings T-4 through T-10, plus T-10 🟢).

## Functions Guarded

| Finding | Function | Attribute | DP | Type |
|---|---|---|---|---|
| T-4 | setFlameColor(name) | "flameColor" | 101 | String (label) |
| T-5 | setFlameBrightness(level) | "flameBrightness" | 102 | String (label) |
| T-6 | setFlameSpeed(speed) | "flameSpeed" | 103 | String (label) |
| T-7 | setCharcoalColor(name) | "charcoalColor" | 104 | String (label) |
| T-8 | setHeatLevel(level) | "heatLevel" | 5 | String (normalized lowercase) |
| T-9 | setHeatingSetpoint(temp) | "heatingSetpoint" | 14 | Integer (compared as Integer) |
| T-10 | setChildLock(state) | "childLock" | 108 | String ("on"/"off") |

## Pattern Applied

Each function now reads `device.currentValue("<attribute>")` before the `sendDpWrite` call. If the current value matches the requested value AND current is non-null, the function logs a `debugLog` skip message and returns early without sending to the device.

Null-current rule: if `device.currentValue()` returns null (unknown state, fresh install), the write proceeds — guards only trigger on confirmed matches.

## Numeric Attribute Note (T-9)

`setHeatingSetpoint` compares as `Integer` (via `safeInt`) to avoid false mismatches from type coercion. The comparison uses the post-clamped value so edge-of-range requests are handled correctly.

## setHeatLevel Safety Note (T-8)

The heater is intentionally excluded from `applyOnPowerOnDefaults` for safety (no auto-start on power-on). That exclusion is **preserved and unaffected** by this change. The guard added here only applies to the explicit `setHeatLevel` user command path.

## Rationale

Tuya Local DP writes cause an audible relay click on the Touchstone Sideline Elite regardless of whether the new value equals the current one. Automation rules that re-assert the same state (e.g., scene controllers, mode rules) would cause repeated audible artifacts. The skip-if-match pattern eliminates these while preserving correct behavior: the first write always goes through (null state), and the Hubitat attribute stays in sync via the `emitAttribute` call that follows a successful write.
