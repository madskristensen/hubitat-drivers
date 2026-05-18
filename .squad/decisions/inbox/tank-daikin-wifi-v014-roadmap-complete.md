# Decision: Daikin WiFi v0.1.4 — Roadmap Complete

**Date:** 2026-05-18
**Author:** Tank
**Status:** Shipped

---

## Summary

v0.1.4 bundles the three remaining roadmap items from Trinity's v0.1.0 capability gap memo. The driver is now feature-complete against the initial roadmap.

## What shipped in v0.1.4

1. **setSpecialMode + specialMode attribute** — get_special_mode / set_special_mode implemented; specialMode enum [off/econo/powerful]; polled every fast-refresh cycle.
2. **get_model_info runtime capability cache** — Called once in initialize(); caches model name, firmware, en_hum flag, swing flags in state.modelInfo. Diagnostic only in v0.1.4.
3. **Event hygiene audit** — All five checks passed (emitIfChanged on parse paths, descriptionText on all sendEvent, lastActivity throttle >=60s, no displayed:false, no isStateChange:true). Driver was already clean.

## Protocol details needing hardware verification

- adv field bitmap values: econo=2, powerful=12 (community-documented; unverified on Mads's hardware)
- get_model_info field names: model, rev, en_hum, swing_l, swing_v (community-documented; firmware-variant dependent)
- Compound adv strings like '2-fff10000': split-on-dash defensive parse

## Deferred items

- On-device timer (get_program/set_program) — use Hubitat rules instead
- Parent/child multi-unit — Mads has one unit (Trinity directive)
- EZ Dashboard JSON_OBJECT attributes — v0.1.2 candidate, not yet requested

## Commit

SHA 1dd21fe — feat(daikin-wifi): v0.1.4 econo/powerful + model_info + event hygiene
