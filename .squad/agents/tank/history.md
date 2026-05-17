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

## Learnings

### EnergyMeter Capability Quirks
Adding `capability "EnergyMeter"` gives you the built-in `energy` attribute automatically — no separate `attribute "energy", "number"` declaration needed (Hubitat adds it for you). Custom sibling attributes (`energyYesterday`, `energyMonth`, `energyLastMonth`) must be declared explicitly. The `energy` event requires `unit: "kWh"` to display correctly in dashboards. Always guard array access with `instanceof List` before indexing — older SunStat firmwares may omit the entire `data.Energy` block; log a debug message and skip rather than erroring.

### Hold/Schedule Attribute Patterns
API integer-as-boolean fields (e.g. `data.Target.Hold`) should map to descriptive string enums (`"holding"/"following"/"unknown"`) rather than `"true"/"false"` — this is more self-documenting and survives future API revisions where non-zero values may encode hold duration. For API string enums that must be surfaced as commands (e.g. `SchedEnable`), always use lowercase Hubitat-side values (`"on"/"off"`) even if the API uses titlecase (`"On"/"Off"`). Always emit optimistic attribute updates before the PATCH call so dashboards respond immediately.

### Step-Rounding Pattern for Setpoints
When `data.Target.Steps` is present, persist it to `state.setpointStep` each poll and apply it in every setpoint write command: `rounded = (Math.round(temp / step) * step).setScale(2, ROUND_HALF_UP)`. A `validStep()` helper (returns `step > 0 ? step : 1.0`) prevents division-by-zero if the API returns 0 for an unknown firmware. Apply step-rounding *before* clamping, not after, so the clamped value is always on a valid step boundary.

### Command-vs-Preference for Long Strings (v0.1.3)
Hubitat's `password`/`text` preference type silently truncates values beyond ~1024 chars. For tokens longer than that (e.g. Azure AD B2C refresh tokens at ~1660 chars), use a `command` with a `STRING` parameter instead — command arguments bypass the preference size limit. v0.1.3 removed the `refreshToken` password preference, added `setRefreshToken(String)` command, removed the initialize() migration block, simplified `tokenBootstrapReady()` and `refreshTokensSync()` to state-only reads. Lines touched in parent: header/constants (~4–31), definition (~54), preferences (~60–65), updated() (~110), initialize() (~125–134), commands section (~192–206), refreshTokensSync() (~529–536), tokenBootstrapReady() (~704–707). packageManifest.json top-level + both driver entries bumped to 0.1.3.

## Team Updates (2026-05-16T21:07:23-07:00)

**SunStat Connect Plus v0.1.3 complete.** Replaced `refreshToken` password preference with `setRefreshToken(String)` command to bypass Hubitat's ~1024-char preference limit on saved values. Removed initialize() migration block; `state.refreshToken` is now the sole source of truth. packageManifest.json bumped to 0.1.3 across all entries.

