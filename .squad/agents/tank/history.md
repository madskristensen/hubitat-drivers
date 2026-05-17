# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Active Milestones

### 2026-05-17: Gemstone Lights v0.4.0 — HPM Release Infrastructure & Public Listing
**Status:** Complete

**Scope:**
- Root repository.json — HPM publisher index pointing to driver packageManifest
- .github/workflows/release.yml — Automated release workflow on packageManifest version bump
- RELEASING.md — Six-step version-bump checklist
- README.md (updated) — Added HPM install instructions
- release-tools/ — Community PR handoff (instructions, JSON snippet, PR body)
- .squad/ team infrastructure — Decisions, agent charters, histories, casting registry

**Deliverables:**
- **GitHub Release:** https://github.com/madskristensen/hubitat-drivers/releases/tag/gemstone-lights-v0.4.0
- **HPM Publisher Index:** https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/repository.json
- **Community PR:** https://github.com/HubitatCommunity/hubitat-packagerepositories/pull/106 (awaiting maintainer merge)

**Status:**
✅ v0.4.0 released and live on GitHub
✅ HPM publishing kit complete
✅ Community PR submitted (awaiting merge for full HPM discoverability)

### 2026-05-16T20:01:41-07:00: SunStat Connect Plus v0.1.0

**Status:** COMPLETED

**Scope:** Full parent/child driver pair for the SunStat Connect Plus electric floor heating thermostat via the Watts® Home cloud API.

**Files delivered:**
- drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy — cloud auth, token refresh, device discovery, polling, command routing
- drivers/sunstat-thermostat/sunstat-thermostat-child.groovy — Thermostat capability surface, all command handlers, parseDeviceState()
- drivers/sunstat-thermostat/CHANGELOG.md — v0.1.0 entry

**Architecture:**
- Parent holds all credentials and tokens; children never touch auth
- Azure AD B2C token refresh via httpPost with application/x-www-form-urlencoded encoding
- Proactive token refresh scheduled ~5 minutes before expiry; full retry on 401
- Children delegate all API calls via parent.sendDevicePatch(deviceId, settingsMap)
- Parent pushes state via child.parseDeviceState(body) after each poll

**Key implementation notes:**
- Floor probe disconnected sentinel guard: floorTemperature > 110°F → device.deleteCurrentState() + warning log
- thermostatSetpoint mirrors heatingSetpoint (heat-only device)
- setBoost / cancelBoost are honest stubs — API not documented; log warning, no API call
- All Thermostat capability commands present; unsupported ones (cool/fan/emergencyHeat) log warnings
- supportedThermostatModes = ["heat", "off"], supportedThermostatFanModes = ["auto"] set on installed()
- Temperature unit conversion between API unit and location.temperatureScale on every poll
- Setpoint clamped to data.Target.Min/data.Target.Max cached from each poll

## Team Updates (2026-05-17T03:01:41Z)

**SunStat Connect Plus v0.1.0 shipped.** Driver implementation (sunstat-thermostat-parent.groovy, sunstat-thermostat-child.groovy) delivered with full parent/child architecture, token refresh middleware, and capability profile. Trinity's architecture and Switch's test plan finalized. Awaiting Mads' real-device verification (Mode.Enum, modelId, ROPC probe, httpPatch sandbox compatibility).

---

**For archived sessions and learnings, see history-archive.md**
