# Session Log: Hubitat Sandbox Iteration v0.2.2 → v0.2.3

**Date:** 2026-05-17T00:25:00Z
**Agent:** Scribe
**Scope:** Squad decision/log consolidation for Tank spawns v0.2.2 → v0.2.3

## Work Summary

Scribe consolidated Tank's two-phase Hubitat sandbox remediation into Squad's decision and orchestration records:

### Phase 1: Parse-Time Fix (v0.2.2)
- Tank identified that cross-`@Field static final` references fail at parse-time, even with `+` concatenation
- Solution: inline USER_AGENT literal directly
- Updated `.squad/skills/hubitat-sandbox-pitfalls/SKILL.md` with corrected rule (confidence bumped low → medium)
- Result: `drivers/gemstone-lights/gemstone-lights.groovy` v0.2.2 passes Hubitat parse

### Phase 2: Runtime Audit (v0.2.3)
- Hubitat runtime rejection of `System.currentTimeMillis()` triggered full driver sandbox audit
- Found 2 issues: `System.currentTimeMillis()` and `UUID.randomUUID()`
- Created sandbox-safe helpers: `currentEpochSeconds()` and `generatePatternId()`
- Full driver rescanned for JDK blocklist — no additional violations
- Renamed skill folder: `hubitat-static-field-pitfalls/` → `hubitat-sandbox-pitfalls/` (scope now covers both layers)
- Result: `drivers/gemstone-lights/gemstone-lights.groovy` v0.2.3 passes parse + runtime validation

### Consolidation Tasks
1. **Merged inbox:** Removed old `2026-05-16: Hubitat static field init gotcha` entry (replaced by corrected v0.2.2 rule)
2. **Added decisions:** Two new entries in `.squad/decisions.md`:
   - `2026-05-16: Hubitat static field correction for v0.2.2`
   - `2026-05-16: Gemstone Lights v0.2.3 Hubitat sandbox audit`
3. **Cleaned inbox:** Deleted 2 processed files from `.squad/decisions/inbox/`
4. **Orchestration log:** Created `.squad/orchestration-log/2026-05-17T00-25-00Z-tank.md` documenting both spawns as one iteration
5. **Skill rename:** Confirmed `.squad/skills/hubitat-sandbox-pitfalls/` is new permanent location (was `hubitat-static-field-pitfalls/`)

## Key Learning Documented

Hubitat sandbox is **two-layer enforcement**:
- **Layer 1 (parse-time):** Static field initializer validator rejects ANY cross-`@Field` reference
- **Layer 2 (runtime):** JDK API blocklist (System, Thread, Runtime, reflection, Date, File, Eval, UUID, etc.)

Both must be cleared before shipping to Hubitat Hub.

## Files Affected
- `.squad/decisions.md` (replaced 1 entry, added 2 new)
- `.squad/orchestration-log/2026-05-17T00-25-00Z-tank.md` (created)
- `.squad/decisions/inbox/` (2 files deleted)
- `.squad/skills/hubitat-sandbox-pitfalls/` (permanent name, no action needed)

## Status
✓ Inbox merged and cleaned
✓ Decisions archive skipped (all entries dated 2026-05-16, none eligible)
✓ History summarization skipped (Tank's history 8856 bytes, below 15360 threshold)
✓ Git commit skipped (repo not git-initialized)
