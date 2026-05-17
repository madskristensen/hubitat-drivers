# 2026-05-17T02:20:33Z: Gemstone v0.4.6 Favorites Hygiene

**Release:** Gemstone v0.4.6  
**Reported by:** Mads Kristensen  
**Refinement:** Three-pass cleanup of preset-list noise

## Summary
Assembly of Tank-9 (log hygiene), Tank-10 (UI filtering), Tank-11 (state pruning). Addresses Mads' feedback loop: demoted info→debug on catalog dumps → `lightEffects` UI dropdown to favorites only → `state.effectCatalog` and `state.effectPatterns` cache favorites only. Non-favorite name lookups remain backward-compatible via on-demand refresh. Existing installs auto-prune on upgrade.

## Changes
- `drivers/gemstone-lights/gemstone-lights.groovy` (log/UI/state hygiene)
- `drivers/gemstone-lights/packageManifest.json` (bumped to v0.4.6)

## Public API
No breaking changes. `setEffect(String)` and `playEffectByName(String)` resolve non-favorite names at runtime.
