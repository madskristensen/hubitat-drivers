---
updated_at: 2026-05-17T03:37:53Z
focus_area: SunStat Connect Plus v0.1.2 — 6 new features shipped; READMEs audited for community conformance. Awaiting real-device verification and 3 audit open questions.
active_issues: []
---

# What We're Focused On

**Current Release:** SunStat Connect Plus v0.1.2 is complete and merged. Parent/child driver pair (`drivers/sunstat-thermostat/`) ships with 6 new features:
- **EnergyMeter capability** + 4 energy attributes (energy, energyYesterday, energyMonth, energyLastMonth)
- **Schedule control** (setScheduleEnabled command + scheduleEnabled attribute)
- **Thermostat hold detection** (thermostatHold attribute)
- **Outdoor sensor integration** (outdoorTemperature + outdoorSensorStatus attributes)
- **Setpoint precision** (setpointStep with step-rounding, 0.5°F/C granularity)
- **Floor sensor bounds** (floorMin/floorMax clamping, 40–85°F limits)

**Documentation Audit Complete:** Link-3 surveyed 8 community Hubitat driver repos and applied 6 targeted edits across 3 READMEs (root + 2 drivers):
- Added explicit compatibility headers (Hubitat Elevation C-7, C-8 | Platform 2.3.3.x or later)
- Added latest-version badges with GitHub Releases links
- Enhanced root README with min platform version + per-driver network requirements
- Added RELEASING.md reference for transparency on versioning

**Blocking for Immediate Next Steps:**
- Mads' real-device verification of v0.1.2 features (Mode.Enum, modelId, httpPatch sandbox compatibility)
- 3 README audit open questions:
  1. Hubitat Community forum dedicated threads available for linking?
  2. Add PayPal/Venmo donation links (optional)?
  3. C-5 hub testing verified, or keep C-7/C-8 as explicit support tier?

**Test Coverage:** Switch added 23 new test cases (#26-#48) for v0.1.2 features; existing edge cases renumbered (#49-#58). Total coverage now 48 test cases.

**Production Driver:** Gemstone Lights v0.4.0 is live on GitHub (https://github.com/madskristensen/hubitat-drivers/releases/tag/gemstone-lights-v0.4.0) with full HPM integration. Community PR #106 pending maintainer merge for full discoverability.

**No-Push Handoff Model:** All agents prepare changes locally. Mads owns remote mutations (`git push`, `gh pr create`, etc.) after reviewing handoff.

