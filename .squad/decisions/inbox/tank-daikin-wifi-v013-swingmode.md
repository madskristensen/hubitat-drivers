# Decision: daikin-wifi v0.1.3 — setSwingMode command added

**Date:** 2026-05-18  
**Author:** Tank  
**Driver:** `drivers/daikin-wifi/daikin-wifi.groovy`  
**Version bump:** 0.1.2 → 0.1.3  
**Commit:** 665e968

## Decision

Added `setSwingMode` command and `swingMode` attribute to the Daikin WiFi Thermostat driver, per user request from Mads (v0.1.2 confirmed working; swing mode controls missing from device UI).

## f_dir Mapping

The Daikin BRP069B exposes swing direction as the `f_dir` field in `get_control_info` / `set_control_info`:

| f_dir | swingMode string |
|-------|-----------------|
| 0 | off (fixed position) |
| 1 | vertical (up/down swing) |
| 2 | horizontal (left/right swing) |
| 3 | 3d (both axes — Daikin's term) |

## Gap found vs. Trinity's memo

Trinity's v0.1.0 capability gap memo stated `f_dir` was "parsed into `fanDirection` custom attr." This was **incorrect for the v0.1.2 driver** — `f_dir` was not parsed at all and no `fanDirection` attribute existed. Both the parse path (inbound `get_control_info`) and the write path were missing. Both were added in v0.1.3.

## Implementation

1. **Constants:** `SWING_MODE_OPTIONS`, `DAIKIN_F_DIR_TO_SWING`, `SWING_TO_DAIKIN_F_DIR` maps added as `@Field` constants.
2. **Attribute:** `swingMode` ENUM attribute declared in metadata (`["off", "vertical", "horizontal", "3d"]`).
3. **Command:** `setSwingMode` declared with ENUM constraint — produces a dropdown in the Hubitat device UI.
4. **Parse path:** `handleControlInfo` now reads `kv.f_dir` and emits `swingMode` via `emitIfChanged`.
5. **Write path:** `setSwingMode(String mode)` validates against the enum, maps to f_dir code, calls `sendControlWrite([f_dir: daikinFDir])`.
6. **sendControlWrite default:** Changed from hardcoded `f_dir="0"` to read current `swingMode` attribute — preserves swing setting across all other control writes.
7. **README:** Removed "swing direction is fixed at 0" from Known Limitations; added `swingMode` to custom attributes list.

## No new skill filed

The `setSwingMode` pattern is Daikin-specific (f_dir field, 4-value enum). Not cross-driver reusable as a skill.
