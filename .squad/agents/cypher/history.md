# Cypher — Integration / Protocol Engineer

## Session Summary

**Sessions Completed:** 4 major audit/research cycles  
**Time Span:** 2026-05-16 to 2026-05-23

### Fully Kiosk v0.6+ Parity Audit (2026-05-19)

- Inventoried v0.5.0: 21 commands, 12 attributes
- Compared against HA integration and official REST API
- **Result:** v0.5.0 is production-ready; 12 gaps identified (storage/RAM sensors, timers, screenshots)
- **MVP for v0.6:** RAM/storage sensors + reboot + timer controls (~190 LOC, low risk)
- Out of scope: MQTT, cloud APIs, video, arbitrary settings UI

### Climate Advisor v0.2.1 Comprehensive Audit (2026-05-23)

**30 findings across code/sandbox/performance/logic:**
- **🔴 Critical:** Missing ContactSensor capability, null sendEvent on NUMBER attributes, wrong namespace ("madskristensen" vs "mads")
- **🟠 High:** appendIndoorSample timing, window gate inconsistency, indoor trend threshold reuse
- **Pattern:** One child device per house, zones as attributes/JSON only
- **SKILL.md gap discovered:** Trend buffer example shows non-debounced pattern; needs update

### Climate Advisor HomeKit / House Status Research (2026-05-23)

- HomeKit bridges one-bit only (ContactSensor `open`/`closed`)
- String attributes (latestMessage, severity, messages[]) render on SharpTools only
- HomeKit capability: **ContactSensor**, NOT SmokeDetector (Critical Alerts are life-safety only)
- PurpleAir confirmed `capability.airQuality` + `airQualityIndex` attribute standard
- Sonos: use `capability.speechSynthesis` + `speak()` for announcements

### Gemstone v0.4.16 Stale-Token Diagnostic (2026-05-18)

- Dedup guards prevent early auth checks; token invalidity not detected until `refresh()` called
- Proactive refresh failure clears token and timer without rescheduling
- **Pattern:** Auth gate must come BEFORE dedup in Hubitat command handlers

### Touchstone Fireplace Retry Loop Triage (2026-05-19)

- **cmd 13 (device22 control):** Heartbeats ACK but encrypted commands fail silently if localKey is stale
- **Root cause:** Likely firmware OTA rotation of Tuya localKey overnight
- **Pattern gap:** No retry cap; perpetual retries @ 5s/15s; needs max retry counter + healthStatus escalation
- **Fix for Tank:** Reset retryIndex only on DP-bearing frames; add maximum retry cap with state surface

## Five-Verdict Driver Review (2026-05-16 to 2026-05-18)

1. ✅ **Bosch Home Connect** → INSTALL craigde/hubitat-homeconnect-v3
2. ✅ **Rainbird LNK** → IMPROVE-EXISTING MHedish
3. ✅ **PurpleAir Cloud-API** → INSTALL pfmiller0
4. ✅ **MyQ Ecosystem** → BUILD ratgdo ESPHome HTTP
5. ✅ **Daikin BRP069B** → BUILD (v0.1.7 shipped)

**Result:** Zero open BUILD candidates remaining.

## Key Patterns Extracted

1. **House-status virtual devices:** Split design — HomeKit is one-bit signal, SharpTools is rich data
2. **One-per-house apps:** Use single virtual device with configuration-only zones; surface zone data as enumerated attributes or JSON, never per-zone child devices
3. **Auth flow ordering:** Auth gate before dedup in command handlers; dedup only safe after session confirmed
4. **Retry semantics:** Separate heartbeat-confirm from command-confirm; cap retries; escalate to healthStatus
5. **Trend buffer anti-pattern:** Sample collection handlers separate from evaluation orchestrator; don't append from within evaluation pass

## Archived Records

Detailed audit records for completed driver reviews moved to history-archive.md to reduce clutter.