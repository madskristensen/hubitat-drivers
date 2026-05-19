# Switch — QA / Testing Engineer

QA and testing specialist for Hubitat drivers. Focuses on real-device validation, edge-case discovery, and test plan creation for Tuya/LAN devices.

---
---

## Hardware Validation Checklist — 3 Community Driver Forks (2026-05-18T17:25:00Z)

**From:** Tank parallel fork session (commits 1dc51af, 32a9f2c, ff3410f) — all forks now available in repo.

### 1. Honeywell T6 Pro — Downstairs Thermostat

**Install:** HPM → `https://...packageManifest.json` (Honeywell T6 Pro)

**Validation checklist:**
- [ ] **Info logs fire** — Enable `txtEnable` preference; trigger poll cycle; verify logs show battery %, AC mains events, Z-Wave associations (these were silently dropped before fork)
- [ ] **Fan-state operating-mode detection** — Change fan mode on physical thermostat; verify `thermostatOperatingState` attribute updates within 5 seconds (was broken by missing currentValue() argument)
- [ ] **syncClock scheduler accumulation gone** — Open device page; click Configure 3 times rapidly; check device logs confirm syncClock fires exactly once every 3 hours (not 3× from zombie schedulers)

**Pass criteria:** All three checks confirm or show clear failure symptom. Report findings to team if any check fails.

### 2. Fully Kiosk Browser Controller — Bathroom OR Kitchen Tablet

**Install:** HPM → `https://...packageManifest.json` (Fully Kiosk)

**Validation checklist:**
- [ ] **Password not leaked in debug logs** — Enable debug logging; open app and toggle password field; verify no plaintext password appears in driver logs or Hubitat hub log (was hardcoded in query string before fork)
- [ ] **No event spam in refreshCallback** — Monitor event log while tablet is idle; verify max 2–3 events per poll cycle (was 5,760+ per day before fork due to missing emitIfChanged)
- [ ] **descriptionText shows in event log** — Trigger a command (e.g., press lock/unlock); verify event log shows human-readable message like "Bathroom Tablet motion is active" (was missing before fork)

**Pass criteria:** All three checks confirm or show clear failure symptom. Report findings to team if any check fails.

### 3. PurpleAir AQI Virtual Sensor — Any Neighbor Sensor

**Install:** HPM → `https://...packageManifest.json` (PurpleAir AQI)

**Validation checklist:**
- [ ] **AQ&U / LRAPA / Woodsmoke conversions all work** — Pick a neighbor sensor index from map.purpleair.com; configure driver with that sensor ID; wait for poll cycle; verify `aqi`, `pm25`, `aqiString` attributes appear and show correct EPA AQI values (were all dead code before fork due to string case mismatches)
- [ ] **failCount actually increments on simulated API failures** — Temporarily disable WiFi on hub or block API traffic; wait for poll timeout; verify `failCount` attribute increments (was never incremented before fork due to operator precedence bug)

**Pass criteria:** Both checks confirm or show clear failure symptom. Report findings to team if any check fails.

**Note:** PurpleAir fork is temporary — will be deleted after upstream PR is accepted. Preserve UPSTREAM-PR-DRAFT.md for Mads to submit to pfmiller when validation is done.

---


### 2026-05-18T17:50:00Z — v0.2.0 Polish Hardware Validation Checklist

**v0.2.0 driver improvements shipped.** Three drivers received full deferred-backlog polish per Trinity's v0.1.0 audit:
- **Honeywell T6 Pro** (ac5b939) — temperature/humidity descriptionText added; numeric comparison improved; configurationGet(52) dead code removed
- **Fully Kiosk** (0e9f8ed) — debug logs auto-disable 30 min after enable; checkInterval=120 prevents false offline on single missed 1-min poll; setLevel event fires only after HTTP 200
- **PurpleAir AQI** (4b720aa) — aqi/category/sites/conversion events only fire when value actually changes; sentinel guards prevent crash when sensor field is null; lastActivity timestamp updates on every successful poll

**Hardware validation checklist for v0.2.0:**
1. **Honeywell (Downstairs thermostat)**
   - [ ] Verify temperature + humidity events now have non-blank Description column in Events tab
   - [ ] Verify supportedThermostatModes events show in event log

2. **Fully Kiosk (Bathroom + Kitchen tablets)**
   - [ ] Verify debug logs auto-disable 30 min after enable
   - [ ] Verify checkInterval=120 prevents false offline on single missed 1-min poll
   - [ ] Verify setLevel event fires only after HTTP 200 success (not optimistic before command)

3. **PurpleAir (geolocation sensor)**
   - [ ] Verify aqi/category/sites/conversion events only fire when value actually changes (poll at 1-min or 5-min, watch for stable readings with no duplicate events)
   - [ ] Verify sentinel guards prevent crash when sensor field is null
   - [ ] Verify lastActivity timestamp updates on every successful poll

**Report findings to team once validation complete.**
