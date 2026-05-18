# Tank — Performance & Quality Todos Shipped (2026-05-18)

**Session Trigger:** "let's do them in order, high to medium to low priority"

## Summary

Tank shipped all 8 perf/quality todos across 5 driver releases in a single batch. All commits pushed to origin/main with full decision documentation.

## Shipped Releases

### 1. Touchstone v0.1.28 (commit e701e58)
**Perf Todos:** touchstone-rxbuffer-partial-state, touchstone-dedupe-parse-events

- rxbuffer partial-write: Builds concatenated receive buffer locally; only leftover hex written to state
- parse-event dedupe: Added parse-only dedup helpers, routes unchanged frames through them
- Documented parse-path dedupe rule in .squad/skills/hubitat-event-hygiene/SKILL.md

### 2. Gemstone v0.4.15 (commit 5843dbe)
**Perf Todos:** gemstone-clonemap-copy-hygiene, gemstone-dedupe-refresh-telemetry-events

- cloneMap copy hygiene: Removed JSON round-trip, now recursively clones only mutable containers
- refresh telemetry dedupe: Added refresh-only dedup helpers, routes unchanged polls through them
- Captured copy-hygiene rule in .squad/skills/hubitat-hot-path-copy-hygiene/SKILL.md

### 3. Touchstone v0.1.29 (commit 5c3531a)
**Perf Todos:** touchstone-drop-lastdps-state, touchstone-byte-copy-helpers

- Removed dead state.lastDps writes from processFrame() hot path
- Added one-time state.remove cleanup in initialize()
- Reworked byte helpers: primitive int counters + System.arraycopy for copy operations
- Captured byte-helper pattern in .squad/skills/tuya-local-groovy/SKILL.md

### 4. SunStat v0.1.10 (commit 0e9d5f9)
**Audit Item:** SC-4 (last unshipped repo-backed item from Trinity's redundant-write audit)

- Cached Schedule.Floor.W into state.floorWarmth
- setFloorMinTemp() now skips redundant PATCH when thermostat already matches
- Closes SunStat audit board entirely

### 5. Gemstone v0.4.16 + SunStat parent/child v0.1.11 + root bundle v1.0.5 (commit 9591c37)
**Perf Todo:** cloud-driver-metadata-hygiene (final todo)

- Added capability "Polling" to Gemstone driver metadata
- Added capability "Polling" + capability "Actuator" to SunStat parent metadata
- Synced child driver and manifests to v0.1.11
- Enables Hubitat app discovery without changing behavior

## Impact

**Todos Closed:** 8
- touchstone-rxbuffer-partial-state ✓
- touchstone-dedupe-parse-events ✓
- gemstone-clonemap-copy-hygiene ✓
- gemstone-dedupe-refresh-telemetry-events ✓
- touchstone-drop-lastdps-state ✓
- touchstone-byte-copy-helpers ✓
- SC-4 (SunStat redundant write) ✓
- cloud-driver-metadata-hygiene ✓

**Drivers Modified:** 3 (Touchstone, Gemstone, SunStat)
**Versions Bumped:** 5 releases
**Skills Updated/Created:** 2 new skills + 1 updated

## Guardrails Maintained

- Parse dedupe separated from command echoes (UX preserved)
- Refresh dedupe on cloud telemetry path only (command confirmations intact)
- JSON operations removed only from internal hot paths
- Byte-copy helpers use only primitive types (no reflection)
- Null/unknown cached values still fall through to writes (fresh installs work)

## Decision Records

See .squad/decisions/decisions.md for full decision details on all 5 releases.

**Orchestration Log Entries:**
- 2026-05-18T17-11-04Z-tank-touchstone-v0128.md
- 2026-05-18T17-13-04Z-tank-gemstone-v0415.md
- 2026-05-18T17-15-04Z-tank-touchstone-v0129.md
- 2026-05-18T17-17-04Z-tank-sunstat-sc4.md
- 2026-05-18T17-19-04Z-tank-capability-markers.md
