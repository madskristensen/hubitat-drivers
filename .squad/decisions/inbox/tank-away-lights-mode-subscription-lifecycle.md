# Decision: Away Lights — Mode Subscription Lifecycle & Unconditional Cleanup (v0.8.1)

**Date:** 2026-05-20  
**Author:** Tank  
**File:** `apps/away-lights/away-lights.groovy`

## Context

v0.8.1 resource cleanup task asked for:
1. `unschedule("offTimeHandler")` on mode exit
2. A "subscription manager" that subscribes to mode events only during Away mode and unsubscribes on exit

Mads clarified: backcompat is not a priority pre-v1.0.0 — make breaking changes if needed.

## Decision

### Enhancement 1 — `unschedule("offTimeHandler")` is UNCONDITIONAL

The unschedule fires on ANY Away exit (`else` block), not just inside `turnOffOnHome=true`. When `turnOffOnHome=false`, the old code left `offTimeHandler` scheduled to fire at end-time and then no-op (mode guard). Pure waste. Since `offTimeHandler` only turns off lights when `location.mode == awayMode`, cancelling it early has no visible behavioral difference and eliminates the wasted invocation.

### Enhancement 2 — `else if (turnOffOnHome)` → `else` (structural)

All resource cleanup (`unschedule` × 3, state reset) is now unconditional on Away exit. Only `lightsOff()` stays inside `if (turnOffOnHome)`. Previously, `turnOffOnHome=false` did ZERO cleanup on Away exit — `checkAndTurnOn` and `doLightsOn` ran and no-oped at their scheduled times.

### What was NOT done — mode subscription cannot be made conditional

`subscribe(location, "mode", modeHandler)` in `initialize()` remains permanent. You cannot subscribe "only during Away mode" for the subscription that detects Away entry — it's circular. A `unsubscribe("modeHandler")` + `subscribe(...)` pair in the exit block is a net no-op and was dropped.

If Hubitat ever adds value-filtered subscriptions (`subscribe(location, "mode.Away", handler)`), the circular dependency can be broken. Until then, the permanent subscription is the correct approach.

## Impact

**Breaking:** When `turnOffOnHome=false`, scheduled tasks now cancel on Away exit (previously lingered). No user-visible difference in light behavior — the break is resource-use only.
