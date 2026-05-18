# Perf Todos Triage — Session Log

**Date:** 2026-05-18  
**Agent:** Tank  
**Trigger:** Audit recovery + fresh perf proposals

## Summary

Merged 8 perf/quality items from Tank's audit into .squad/decisions.md:
1. SC-4 recovery (SunStat floor temp caching)
2-8. Seven new Tank proposals spanning Touchstone (3), Gemstone (2), SunStat (1), and cloud driver metadata (1)

## Items

- sunstat-sc4-cache-floor-min-temp — Cache floor warmth (priority: medium)
- 	ouchstone-rxbuffer-partial-state — Partial Tuya frame buffering (priority: high)
- 	ouchstone-drop-lastdps-state — Remove dead state.lastDps (priority: medium)
- 	ouchstone-dedupe-parse-events — Dedupe telemetry in applyDps (priority: high)
- 	ouchstone-byte-copy-helpers — Replace boxed loops with arraycopy (priority: medium)
- gemstone-clonemap-copy-hygiene — Lighter cloneMap copies (priority: high)
- gemstone-dedupe-refresh-telemetry-events — Gate refresh events (priority: high)
- cloud-driver-metadata-hygiene — Add missing Polling/Actuator markers (priority: low)

## Outcomes

✓ Decisions inbox merged and cleared  
✓ 8 todos inserted into session SQL  
✓ Hubitat event hygiene skill updated with latest perf patterns
