---
updated_at: 2026-05-17T03:01:41Z
focus_area: SunStat Connect Plus v0.1.0 — shipped to repo, awaiting Mads' real-device verification
active_issues: []
---

# What We're Focused On

**Current Release:** SunStat Connect Plus v0.1.0 is complete and merged. Parent/child driver pair (`drivers/sunstat-thermostat/`) ships with:
- Full Azure AD B2C token refresh middleware
- Device discovery and child device lifecycle management
- Thermostat capability profile (heat-only modes: off, heat)
- Custom attributes: floorTemperature, boostActive, boostUntil
- Stubs for boost/hold (API shape pending real-device verification)

**Blocking for v0.2.0:** Mads' real-device testing to confirm:
- `Mode.Enum` values (are modes exactly ["Off", "Heat"]?)
- `modelId` / `modelNumber` reported by Watts API
- ROPC policy probe result (can we skip external CLI bootstrap?)
- `httpPatch` call success in Hubitat sandbox

**Production Driver:** Gemstone Lights v0.4.0 is live on GitHub (https://github.com/madskristensen/hubitat-drivers/releases/tag/gemstone-lights-v0.4.0) with full HPM integration. Community PR #106 pending maintainer merge for full discoverability.

**No-Push Handoff Model:** All agents prepare changes locally. Mads owns remote mutations (`git push`, `gh pr create`, etc.) after reviewing handoff.
