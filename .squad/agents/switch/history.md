# Switch — QA / Testing Engineer

QA and testing specialist for Hubitat drivers. Focuses on real-device validation, edge-case discovery, and test plan creation for Tuya/LAN devices.

---

## Current Session (2026-05-17)

### 2026-05-17T15:50:06Z — Touchstone TESTING.md Created (v0.1.5 + v0.1.6 coverage)

**File created:** drivers/touchstone-fireplace/TESTING.md — 33 tests across 9 test areas + validation summary. Covers v0.1.5 baseline and v0.1.6 new commands (setFlameSpeed/setLogBrightness).

**Learnings — Tuya/LAN driver TESTING.md pattern:**

1. Pre-flight checklist mandatory (close app, ping device, verify key, confirm power) — prevents >80% of false failures
2. Section order: Pre-flight → Lifecycle → Happy-path → Power-on defaults → Settings edge cases → Recovery → Discovery → Parsed-not-commanded DPs → Validation summary
3. Mark hardware unknowns explicitly with [verify on hardware] inline in Expected sections
4. Tank coordination flag for new commands (e.g., "NEW IN v0.1.6 — verify in v0.1.6" with fallback instructions)
5. Validation criteria summary checklist at end for community beta testers
6. Retry backoff is test-observable — Tuya scheduleRetry([5,15,30]) delays can be validated by log timestamps

### 2026-05-17T15:41:32Z — Cross-Driver Reliability Analysis (Mads request)

**Anti-patterns found across SunStat, Gemstone, Touchstone:**

1. Optimistic events without rollback — if sending optimistic event, MUST have reconciliation path
2. In-flight flag leaks on hub reboot — any *InFlight in state MUST clear in initialize()
3. Unbounded retry loops — cap retries to fixed N, then halt and wait for next poll
4. updated() clearing too much state — only clear when affected preference actually changed
5. Stub commands reachable from Rule Machine — implement or hide; minimum add [NOT YET IMPLEMENTED] description
6. Missing TESTING.md for LAN/protocol drivers — LAN drivers have MORE failure modes, higher priority than cloud
7. Proactive token refresh not rescheduled after hub reboot — initialize() should re-arm maintenance tasks based on current state

---

## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.
