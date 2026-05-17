# Session Log — Touchstone Real-Hardware Bugfix Batch

**Date:** 2026-05-17T16:39:59-07:00  
**Session:** touchstone-real-hw-bugfix-batch  
**Requested by:** Mads Kristensen (community-beta self-testing real-hardware bug reports)

## Batch Summary

Two parallel investigations into Touchstone Sideline Elite real-hardware failures, both targeting v0.1.10 release:

1. **Cypher — DP coercion bug** (root cause: `setRawDP` type corruption)
2. **Tank — Enum bounds-check hardening** (symptom: display blanking on out-of-range device echoes)

## Bug #1: DP Type Coercion (Cypher — confirmed root cause)

**Symptom:** `setRawDP 105 "5"` and `setRawDP 109 "1"` don't work on real device.

**Root Cause:** `coerceRawValue()` converts numeric-looking strings to integers. DP 105 and DP 109 are declared as `type: string` in the Tuya device YAML; device rejects integer-typed values.

- DP 105 (log brightness) expects `"1"–"12"` (quoted strings)
- DP 109 (ember brightness) expects `"L0"–"L5"` (capital-L prefix; NOT numeric strings)

**Evidence:**
- Tuya YAML fetched from `make-all/tuya-local` GitHub repo
- `setLogBrightness()` (dedicated command) sends correct string type; never tested on real hardware
- `setRawDP` command documentation warns "whole numbers become integers" — using `setRawDP` to test string DPs is invalid

**Status:** Hypothesis B (type coercion) **CONFIRMED**; Hypothesis C (read-only) **UNRESOLVED** pending Mads's test.

**v0.1.10 fixes:**
- Add `setRawDPString` command (or quoted-string input syntax) to skip coercion
- Add `setEmberBrightness` command with "L0"–"L5" enum for DP 109
- Add `dp109` inbound attribute
- Conditional: remove/deprecate `setLogBrightness` if empirical test fails

## Bug #2: Enum Display Blanking (Tank — defensive hardening)

**Symptom:** UI attribute blanks when device pushes out-of-range enum value (device echo during DP write).

**Root Cause:** Driver applies invalid DP value directly to UI attribute without bounds-checking. Mads reported "one higher" display anomaly; investigation confirmed: NOT driver off-by-one, likely device firmware noise or Hubitat UI quirk.

**Fix:** Added `in OPTIONS` bounds-check + `log.warn` + early bail in `applyDps()` for enum DPs (101, 102, 103, 104, 105).

**v0.1.10 commit:** `3fe727c` — includes bounds-checks for all enum DPs.

## Pending Empirical Test (Mads)

Before Tank finalizes v0.1.10, Mads must run:

1. Turn fireplace on (fire must be active)
2. Call `setLogBrightness("12")` directly from Hubitat device page (NOT via setRawDP)
   - **Expected if working:** Visible log brightness increase
   - **If fails:** DP 105 is read-only; v0.1.10 will remove write command
3. Call `setRawDP 109 "L3"` from device page
   - **Expected if exists and working:** Visible ember brightness change
   - **If fails:** DP 109 may be optional on this firmware; confirm in logs

## Deliverables

- **Cypher investigation:** Merged into `.squad/decisions.md` with full YAML excerpts, hypothesis verdict, sources
- **Tank commit:** `3fe727c` — enum bounds-checks in `applyDps()`, v0.1.9 → v0.1.10 candidate
- **Orchestration logs:** Two entries for Cypher and Tank parallel sessions

## Timeline

- **16:34:52 UTC-7** — Cypher completes investigation, writes decision
- **16:39:59 UTC-7** — Tank completes bounds-check hardening, commits `3fe727c`
- **TBD** — Mads runs empirical test; Tank finalizes v0.1.10 changelog

---
