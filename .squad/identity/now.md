---
updated_at: 2026-05-18T14:56:18Z
focus_area: Redundant-write audit workstream (Trinity). Touchstone v0.1.24 shipped (closes T-1). Next 🔴 items: T-2, G-1 (pending Mads authorization).
active_issues:
  - T-2: Touchstone on() unconditional write (audible artifact)
  - G-1: Gemstone setEffect() animation restart (visible artifact)
  - 14 🟡 batch: Cloud-driver & wire-only findings (API quota)
---

# What We're Focused On

**Active Workstream: Redundant-Write Audit (Trinity)**

Trinity completed a comprehensive audit across all 4 drivers:
- **18 findings total:** 3 🔴 (user-visible), 14 🟡 (quota/wire-only), 1 🟢 (harmless), 3 BY-DESIGN
- **Audit file:** `.squad/decisions.md` (merged 2026-05-18)

**Latest Ship: Touchstone v0.1.24 (Tank)**
- ✅ **T-1 CLOSED** (🔴 → 🟢): `defaultHeatingSetpoint` now skip-if-match guarded
- Applied same pattern as v0.1.23 (conditional guard vs. unconditional write)
- Eliminates audible relay click during power-on defaults
- Committed + pushed to main

**Next 🔴 Items Pending Mads Authorization:**

1. **T-2: Touchstone `on()` unconditional write**
   - Impact: Audible artifact when rules repeatedly assert on()
   - Fix: Add `if (switch == "on") return` guard at top of on()

2. **G-1: Gemstone `setEffect()` animation restart**
   - Impact: Visible animation restart when setEffect(sameName) called
   - Fix: Add `if (effectName == currentEffect) return` guard in activateEffectWithPattern()

3. **Batch: 14 🟡 findings** (no user-visible artifacts, pure API/wire quota)
   - T-3..T-9: Touchstone user-command DPs
   - G-2..G-6: Gemstone user-command endpoints
   - SP-1: SunStat parent away-mode PATCH
   - SC-1..SC-4: SunStat child setpoint/mode/schedule (SC-3 is half-fixed; SC-5/6/7 are BY-DESIGN)

**Timeline:** Once Mads authorizes T-2 and G-1, Tank can ship v0.1.25 (Touchstone) and v0.4.11 (Gemstone) immediately. Batch 🟡 findings can follow.

**No-Push Handoff Model:** All agents prepare changes locally. Mads owns remote mutations (`git push`, `gh pr create`, etc.) after reviewing decisions.

