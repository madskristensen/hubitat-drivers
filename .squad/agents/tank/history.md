# Tank — Driver Developer

**Status:** Shipped 3 community driver forks + 2 polish versions (2026-05-18 through 2026-05-19). T6 Pro v0.4.0 live on Mads's Downstairs thermostat. PurpleAir, Fully Kiosk, T6 Pro all permanent forks (PurpleAir has PR-draft staging ready). Stack complete; awaiting next hardware target.

## 2026-05-18 — 3 Trinity Audits Complete + Forks Shipped

**Audit verdicts merged to decisions.md:**
- **PurpleAir AQI (pfmiller0):** INSTALL 88/100; 3 bug fixes ready as PR-draft (AQ&U string, LRAPA/Woodsmoke case, failCount precedence)
- **Fully Kiosk (GvnCampbell):** FORK; 4 major fixes (password security, emitIfChanged, descriptionText, logger inversion bug)
- **T6 Pro (djdizzyd):** FORK; 3 critical fixes (txtEnable BLOCKER, fan-state nil-dereference, octal CMD_CLASS_VERS bug)

Mads's complete stack now has **zero open BUILD candidates**.

## 2026-05-18 — Shipped Forks

### PurpleAir AQI v0.1.0 (3 bug fixes)
- Fixed `apply_conversion()` "AQ and U" → "AQ&U" dead-code check
- Fixed `sensorCheck()` case mismatch: "lrapa"/"woodsmoke" → "LRAPA"/"Woodsmoke" (was fetching wrong PM2.5 field)
- Fixed `httpResponse()` failCount precedence: `(state.failCount ?: 0) + 1` (exponential backoff was broken)
- Status: TEMPORARY fork; delete once upstream merges PR

### Fully Kiosk v0.1.0 (4 critical fixes)
- Password security: `type:"string"` → `type:"password"` + URI masking in logs
- Event hygiene: `emitIfChanged()` on 4 attributes; prevents 5,760+ unnecessary events/day
- descriptionText: Added to parse(), motion(), acceleration() handlers
- Logger: Replaced backwards multi-level enum with standard `logEnable` bool
- Status: PERMANENT fork; GvnCampbell silent 4.5 years

### Fully Kiosk v0.2.0 (5 Trinity deferred items)
- descriptionText on checkInterval events (3 sites)
- logsOff auto-disable 30-min pattern (Gemstone standard)
- Security doc in README
- checkInterval 60→120 (2× polling cadence for health robustness)
- setLevel now async; emits only on HTTP 200

### Honeywell T6 Pro v0.1.0 (3 critical fixes) [djdizzyd fork]
- Added missing `txtEnable` preference (had been permanently false, breaking all info logs)
- Fixed `device.currentValue=="cooling"` → `device.currentValue("thermostatOperatingState")=="cooling"` in 2 locations (method reference never equals string; broke fan-state logic)
- Added `unschedule("syncClock")` to configure() (prevented scheduler zombie accumulation)
- Namespace: `djdizzyd` → `mads`

### Honeywell T6 Pro v0.3.0 (3 Cypher survey picks)
- Emit `thermostatFanState` attribute (data already parsed, never exposed)
- Battery-low notification (events 10/11 of type-8; was silently discarded)
- Fixed octal: `043:2` → `0x43:2` in CMD_CLASS_VERS (was reading 0x23 Scene Controller instead of 0x43 Thermostat Setpoint)
- Full namespace/author alignment; `Initialize` capability + lifecycle hooks; README restructure to repo template

### Honeywell T6 Pro v0.4.0 (3 additive Cypher picks) ✅ SHIPPED LIVE
- descriptionText on 3 event handlers (thermostatOperatingState, thermostatFanMode, thermostatMode)
- thermostatFanState type "string" → "enum" with 8 values
- Notification type 9 handler added (else if branch for System events; handles idle/hw-failure/sw-failure with log.warn)
- Running live on Mads's Downstairs thermostat

### PurpleAir v0.2.0 (5 Trinity deferred items + full alignment)
- emitIfChanged on 4 sendEvent calls (removes ~35,040 duplicate events/year)
- sites descriptionText with proper formatting
- Quota warning in polling preference
- UUID generation for HPM packageManifest
- lastActivity + touchActivity (Pattern B healthcheck)
- Namespace/author: `pfmiller0` → `mads`; attribution preserved
- logsOff auto-disable (logEnable bool)
- Null guards on PM2.5 float parses
- README rebuilt to repo template (What It Is / Capabilities / HPM / Architecture / Fixes / Attribution)

**Key learnings:**
- Fork-cleanup pattern: preserve copyright verbatim, add attribution block, apply audited fixes only with FIX comments citing severity, use Daikin/SunStat templates for packageManifest.json and README
- Groovy numeric literals: `043` is **octal** (= 0x23), not `0x43` — always use `0x` prefix for hex
- Z-Wave device config: fingerprint `inClusters` is authoritative; CMD_CLASS_VERS can have author assumptions — cross-check both sources
- UX pattern: emitIfChanged + descriptionText + lastActivity (Pattern B for cloud, Pattern A for LAN ping) is the canonical hygiene standard across the repo


