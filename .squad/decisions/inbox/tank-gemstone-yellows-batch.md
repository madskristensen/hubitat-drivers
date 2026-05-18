# Decision Drop: Gemstone Lights v0.4.13 — Batch Yellow Audit Findings G-2 through G-6

**Author:** Tank (Driver Developer)
**Date:** 2026-05-18
**Driver:** `drivers/gemstone-lights/gemstone-lights.groovy`
**Version shipped:** 0.4.13

---

## Summary

Five 🟡 cloud-quota findings from Trinity's redundant-write audit were closed in a single batch. All share the same root cause: commands were unconditionally issuing cloud API calls (`PUT /deviceControl/onState` or `PUT /deviceControl/play/pattern`) even when the device was already in the requested state.

---

## Findings Closed

| # | Function | Guard condition |
|---|---|---|
| G-2 | `on()` | `device.currentValue("switch") == "on"` → return before sendCommand |
| G-3 | `off()` | `device.currentValue("switch") == "off"` → return before sendCommand |
| G-4 | `setLevel()` | captured level before sendEvent; if `currentLevel == clamped` → skip pattern PUT |
| G-5 | `setColor()` | composite: `colorMode == "RGB"` AND hue + saturation + level all match → skip pattern PUT |
| G-6 | `setColorTemperature()` | `colorMode == "CT"` AND `colorTemperature == kelvin` → skip pattern PUT |

---

## G-5 Composite-Write Rationale

`setColor()` writes three attributes atomically (hue, saturation, level). A single-attribute check would miss partial-match cases (e.g., same hue but different saturation). Three options were considered:

1. **Compare hue + saturation + level all three** — safe; misses only if all three happen to match by coincidence with a non-RGB color mode. ✅ **Chosen.**
2. **Check colorMode == "RGB" AND three components** — same as option 1 with explicit mode guard. ✅ (also included — this is effectively what option 1 became.)
3. **Skip guarding G-5 entirely** — avoided; the composite check is tractable and avoids unnecessary cloud calls.

The implemented guard requires:
- `colorMode == "RGB"` (ensures we're not in CT or EFFECTS mode)
- `curHue as Integer == hue` AND `curSat as Integer == saturation` AND `curLevel as Integer == level`

This is conservative: any mismatch on any component causes the PUT to proceed. No risk of false-dedup from mode transitions.

---

## G-6 CT Mode Guard Rationale

`setColorTemperature()` should only skip when in `colorMode == "CT"`. If the device is in RGB or EFFECTS mode and a CT command arrives with a matching kelvin value (unlikely but possible after a driver restart with stale state), we must still send the PUT to switch the hardware into CT mode. The guard therefore requires both conditions before skipping.

---

## Non-Interference with G-1 (setEffect)

G-1's guard (shipped v0.4.12) lives in `activateEffectWithPattern()` and checks `effectName`. The G-2–G-6 guards are in distinct command handlers (`on`, `off`, `setLevel`, `setColor`, `setColorTemperature`) and do not touch the effects execution path. No interaction.

---

## Sandbox Safety

All comparisons use `device.currentValue()` (standard Hubitat API) and integer arithmetic. No reflection, no `System.*`, no `Thread.*`. Passes Hubitat sandbox constraints.
