# Session Log — Touchstone v0.1.2 Shipped

**Date:** 2026-05-17  
**Version:** 0.1.2  
**Status:** Shipped to main branch

## What Landed

### Tank v1.1 — Device Profile Generalization

- Renamed driver to `"Touchstone / Tuya Fireplace"` (from `"Touchstone Sideline Elite"`)
- Added Device Profile preference (Sideline Elite / Generic Tuya / Custom)
- Implemented discovery commands: `discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`
- Custom profile exposes per-DP overrides in preferences (no code edit required)

### Tank v1.2 — Critical Import Allowlist Fix

- Removed forbidden `java.util.zip.CRC32` import; replaced with pure-Groovy table-driven implementation
- Removed `java.io.ByteArrayOutputStream`; replaced with `concatBytes()` helper
- Verified all imports against Hubitat allowlist; no forbidden classes remain
- **This fix was blocking installation in Hubitat. Unblocked by v1.2.**

### Link — Documentation Complete

- README (18.2 KB): capabilities, setup, discovery workflow, troubleshooting
- HPM manifest ready for publish
- Documented both Tuya key extraction paths; positioned iot.tuya.com as primary

## Manifest Sync

- Bumped `packageManifest.json` version from 0.1.1 → 0.1.2 to match driver code
- Both root and drivers[0] version fields updated

## Next Phase

- Mads: Re-test Hubitat install to confirm import allowlist fix works
- Mads: Review documentation; iterate if clarifications needed
- Switch: Expanded validation for Generic/Custom profiles (queued for next batch)
- Community: Users with other Touchstone models can use Custom profile + discovery workflow to map their devices
