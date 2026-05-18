# Session Log: Touchstone Flame-Color Iteration Arc

**Date:** 2026-05-17  
**Duration:** v0.1.11 → v0.1.15 (5-spawn, multi-day touchstone series)  
**Pivot:** From read-only DP 105 discovery → enum-type platform-bug investigation → authoritative Tuya app labels  
**Status:** v0.1.15 shipped with diagnostic logging; awaiting Mads empirical test report

---

## The Arc: v0.1.11 → v0.1.15

**v0.1.11 (Hardening):** Discovered DP 105 (log brightness) is read-only on Sideline Elite firmware. Removed dead `setLogBrightness()` code path and `defaultLogBrightness` preference. Added defensive validation to flame/log color and brightness command handlers. This unblocked the team to move forward on color labeling work.

**v0.1.12 (NUMBER Pivot):** Converted `setFlameBrightness()`, `setFlameColor()`, and `setLogColor()` from ENUM to NUMBER type to defeat Hubitat's dropdown +1 quirk (Hubitat maps dropdown indices 0–4 to values 1–5, off by one). NUMBER type prevents the dropdown UI and sends raw numeric values directly. Brightness change later reverted; this was an intermediate step.

**v0.1.13 (Wrong Labels):** Converted all three commands back to named ENUM, betting that human-readable labels were better than NUMBER. Brightness labels correct (Dimmest/Dim/Medium/Brighter/Brightest); but flame color and log color labels were **invented without hardware verification** (Red/Orange/Yellow/Green/Blue/Purple, Crimson/Coral/Gold/Sage/Azure/Lavender). This caused the "set flame color doesn't work" bug: UI "Orange" mapped to Hubitat ENUM index 1, which sent DP value 2 (Blue in reality), so the device changed color — to the wrong one.

**v0.1.14 (Safe Rollback):** Rolled back flame color + log color to NUMBER type (1–6 and 1–12 respectively), avoiding the invented-labels trap. Brightness stayed as correct ENUM. This was the safe interim state. Also removed legacy `setDpRaw()` alias per code review.

**v0.1.15 (Authoritative Restore):** Sourced **authoritative** DP 101 (flame color) labels directly from a Tuya app screenshot provided by Mads Kristensen (device owner). Restored `setFlameColor()` to named ENUM with the verified mapping: Orange/Blue/White/Orange+Blue/Orange+White/Blue+White (DP values 1–6 in app order). Added unconditional `log.info` wire-debug logging to both the command (write side) and the device echo handler (read side) to capture wire values and echo behavior for diagnostic purposes.

---

## Pivotal Moments

1. **DP 105 Read-Only Finding (v0.1.11):** Cypher's investigation of why `setRawDP` couldn't write DP 105 revealed the driver's `coerceRawValue()` function was corrupting string-typed DPs by coercing numeric-looking strings to integers. This discovery forced a fundamental reconsideration: not all DPs are writable, some are read-only firmware constraints.

2. **Dropdown +1 Platform Bug (v0.1.12):** Investigating why ENUM labels weren't working led to the discovery of Hubitat's dropdown UI index-off-by-one quirk. This drove the temporary NUMBER pivot and opened the question: can ENUM work if labels are correct?

3. **Wrong-Color-Labels Course Correction (v0.1.13 → v0.1.14):** v0.1.13's invented labels immediately failed in the field (users reporting color changes to wrong values). The team rolled back to NUMBER (v0.1.14) to stop the bleeding, recognizing that unverified labels are worse than no labels.

4. **Tuya App Screenshot (v0.1.15):** Mads provided a Tuya app screenshot showing the device's actual color palette and the correct DP value → label mappings. This gave the team the **authoritative source** needed to restore ENUM safely. Wire-debug logging was added to capture both the write direction (what Hubitat sends) and the echo direction (what the device reports back) for ongoing verification.

---

## Next Step

**PENDING:** Mads to run `setFlameColor("Orange")` on his Sideline Elite hardware and report the Hubitat log lines from the wire-debug output. This will confirm whether DP 101 is writable (working as expected) or read-only like DP 105 and DP 109. The result determines:
- If writable: DP 101 is live and the fix is complete.
- If read-only: DP 101 will be documented as read-only and `setFlameColor()` may be deprecated/removed in a follow-up version.

This empirical test is the final gate before closing the touchstone-flame-color-iteration work item.
