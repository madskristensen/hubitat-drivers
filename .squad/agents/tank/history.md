## 2026-05-23 — Climate Advisor Architecture Revised: v2 Generic & Shareable (Trinity) — IMPLEMENTATION READY

**Context:** Mads provided explicit feedback on Trinity's v1 design:
1. Make it generic — all devices selectable via app preferences for HPM distribution (not hardcoded zone names / device IDs)
2. Drop HomeKit requirement — remove `ContactSensor` capability; use main page + per-zone href sub-pages (not single-page)

**Trinity completed v2 revision.** Key changes from v1:
- **All hardcoding removed:** Zones (up to 8), thermostats, contacts, weather, AQI, speakers all user-configurable via capabilities-typed inputs
- **HomeKit dropped:** Removed `ContactSensor` capability (no HomeKit requirement in v2). Rich data only via custom attributes (SharpTools-first)
- **UX pattern:** Main preferences page (global devices + thresholds) + `href` links to per-zone sub-pages (each zone: name, thermostat, contacts, temp sensors, speakers)
- **Custom attributes preserved:** severity, severityText, latestMessage, messages, houseStatus, tempTrend, activeAlertCount
- **Parent app + child virtual device architecture unchanged**

**Directives captured:** Climate Advisor must be generic/shareable; use main+sub-page UX pattern.

**Decision locked in `.squad/decisions/decisions.md` under "Architecture Proposal: Climate Advisor — v2 (Generic, SharpTools-first) — SUPERSEDES v1"**

**Read before implementing:**
- Sections 1–7 for architecture, preferences structure, logic pseudocode, subscription model, HPM registration
- Section 8 for open questions (placeholder text vs starter template; per-zone vs house-wide rain check)
- See v1 decision entry for historical context and superseded spec

**Ready for implementation. Proceed when Mads approves v2.**

---

## 2026-05-20 — Away Lights v0.8.1 Resource Cleanup (revised — aggressive)

**Task:** Implement resource cleanup enhancements; Mads clarified backcompat is not a priority pre-v1.0.0 — make breaking changes if needed.

**Changes made to `apps/away-lights/away-lights.groovy`:**
- **Enhancement 1 (unconditional):** `unschedule("offTimeHandler")` now fires on ANY Away-mode exit, not just when `turnOffOnHome=true`
- **Enhancement 2 (structural fix):** Changed `else if (turnOffOnHome)` → `else` in `modeHandler`. All cleanup runs unconditionally on Away exit.
- **Dropped no-op:** mode subscription stays permanent (cannot unsubscribe "only during X" — it's circular)

**Architecture note:** The mode subscription must remain permanent to detect Away re-entry. Future value-filtered subscription support in Hubitat would enable conditional subscription, but today the permanent subscription is correct.

---

# Historical Archive

For full record of Tank's prior work (2026-05-17 through 2026-05-19), see:
- `history-archive-2026-05-23.md` — Summary archive created when primary file exceeded 15,360-byte threshold

**Deliverables summary:** 3 community driver forks (PurpleAir v0.4.0, Fully Kiosk v0.4.6, T6 Pro v0.4.0 LIVE), Away Lights v0.1.0 app, Climate Advisor design (v1 → v2 revision awaiting implementation).

---

*See archive file for pre-2026-05-23 details*
