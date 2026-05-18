# Decision Drop — SunStat v0.1.8: Redundant Cloud PATCH Batch Fix

**Date:** 2026-05-18  
**Author:** Tank  
**Drivers:** sunstat-thermostat-parent.groovy + sunstat-thermostat-child.groovy  
**Version bump:** 0.1.7 → 0.1.8

---

## Findings Addressed

### SP-1 — setAwayModeInternal (parent)
- Added skip-if-match guard reading `device.currentValue("awayMode")` before the optimistic `sendEvent` + PATCH.
- Uses `safeStr` + null-safe compare (`current != null && current == mode`).

### SC-1 — setThermostatMode (child)
- Added per-case guards inside the `switch` for "heat" and "off" branches.
- Reads `device.currentValue("thermostatMode")` before `sendEvt` + PATCH per branch.
- No guard needed for unsupported modes (they already return with a warning).

### SC-2 — setHeatingSetpoint (child)
- Added numeric guard before `sendEvent` + PATCH.
- Reads `device.currentValue("heatingSetpoint")`, casts to BigDecimal, compares to post-clamp `clamped` value.
- Guard fires on the clamped/rounded value so it matches what the driver would actually send.

### SC-3 — setScheduleEnabled (child) — dual-guard pattern
- **Existing guard:** `emitIfChanged` at L335 already deduplicates the Hubitat event. Retained unchanged.
- **New write-guard:** reads `device.currentValue("scheduleEnabled")` BEFORE `emitIfChanged` is called (so the pre-event value is captured correctly; `sendEvent` in Hubitat updates `device.currentValue()` synchronously). Checks captured value after the `wattsDeviceId` validation, before `sendDevicePatch`.
- Both guards coexist and solve independent problems:
  - `emitIfChanged` → prevents duplicate Hubitat events on the local platform
  - write-guard → prevents redundant cloud PATCH calls to the Watts API

---

## SC-4 Deferred — setFloorMinTemp

**Status:** DEFERRED — not addressed in this batch.

**Reason:** Fixing SC-4 requires caching the current `floorMinTemp` value from `parseDeviceState`. The function sends a read-modify-write payload to `/Device/{id}` with both `Floor.W` (warmth) and `Floor.A` (away) values. To skip a redundant PATCH, the driver would need to compare the incoming `temp` against the last-polled warmth value. That value is not currently stored in `state.*` — it would need to be cached as `state.floorMinTemp` (or similar) whenever `parseDeviceState` processes the schedule payload. That's a non-trivial state-handling change warranting its own focused commit.

**What would be needed:**
1. In `parseDeviceState`, when the schedule payload contains `Floor.W`, store it: `state.floorMinTemp = <parsed value>`
2. In `setFloorMinTemp`, read `safeBigDecimal(state.floorMinTemp, null)` and compare to `clamped`. If equal, skip PATCH.

---

## BY-DESIGN Exclusions (intentionally NOT guarded)

| Finding | Function | Reason |
|---------|----------|--------|
| SC-5 | cancelBoost() | State-assertion path — must always PATCH to defeat cloud drift and correctly exit boost state |
| SC-6 | setBoost() | Always a new boost with a new duration/expiry — no meaningful idempotency |
| SC-7 | boostExpired() / initialize() | Recovery paths — must always reassert state to ensure correctness after reconnect or reboot |

---

## Version History
- Parent: 0.1.7 → 0.1.8
- Child:  0.1.7 → 0.1.8
