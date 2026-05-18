## tank-gemstone-g1-effect-idempotent

---
author: tank
date: 2026-05-18T07:56:18-07:00
status: ready-for-review
subject: Gemstone G-1 — skip redundant setEffect pattern PUT when effect already active
---

## Problem

`activateEffectWithPattern()` unconditionally sent `PUT /deviceControl/play/pattern` on every
`setEffect()` call. Gemstone hardware visibly restarts the animation sequence on receiving any
pattern PUT, even with identical parameters. Calling `setEffect("Pulse")` when Pulse is already
playing caused a visible animation glitch.

**Audit finding:** G-1 🔴 (Trinity's redundant-write audit)

## Fix (v0.4.12)

Added an idempotency guard in `activateEffectWithPattern()` immediately before the
`executeOrQueueRequest(buildEffectRequest(...))` call:

```groovy
String currentEffect = safeString(device.currentValue("effectName"))
if (resolvedName && currentEffect == resolvedName) {
    debugLog "setEffect: '${resolvedName}' already active — skipping pattern PUT"
    return
}
```

State events (`switch`, `level`, `effectName`, `colorMode`) are still emitted before the guard —
the Hubitat device state stays consistent whether or not the PUT is skipped.

`safeString` is an existing helper at L2221 (confirmed present in the file).

## Edge-Case Decision: cycleEffect with 1-effect catalog

`setNextEffect()` and `setPreviousEffect()` delegate through `cycleEffect()` →
`activateEffectByIndex()` → `activateEffectWithPattern()`. With a 1-effect catalog, "next" wraps
back to the same effect — the guard suppresses that cycle command (no PUT is sent).

**Decision:** Accept this edge case. A 1-effect catalog is a degenerate configuration; there is
nothing meaningful to cycle to. The guard suppression is the correct behavior (no pointless
restart). A `forceWrite: true` flag was considered to thread through `cycleEffect()` to bypass the
guard, but the wiring would touch three call sites for negligible real-world benefit. Simpler
guard wins.

This edge case is documented here; no code comment added (would be noise for normal catalogs).

## Same Pattern As

Touchstone v0.1.23+ skip-if-match guards for redundant defaults (Trinity audit T-series).
