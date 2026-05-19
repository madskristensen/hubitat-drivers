# Cypher — Integration / Protocol Engineer

## Session Arc 2026-05-19: Fully Kiosk v0.6+ Parity Audit

**Task:** Mads asked: "what's left on the fully driver to make it truly awesome?" Conduct focused, opinionated audit against HA parity baseline and official REST API.

**Methodology:**
- Inventoried v0.5.0 driver: 21 commands, 12 attributes
- Fetched official Fully Kiosk REST API docs (www.fully-kiosk.com/#rest)
- Analyzed HA integration source (homeassistant/components/fully_kiosk): 7 entity types, 19+ exposed features
- Ranked gaps by leverage (impact-to-effort ratio)

**Key Findings:**
1. **v0.5.0 is production-ready** — security fixed, event hygiene fixed, rich sensor attributes from existing poll.
2. **12 identified gaps** — Storage/RAM sensors, timer controls, screenshots, media player, arbitrary config key-value setter.
3. **MVP for v0.6:** RAM/storage sensors + reboot button + screen/screensaver timers (~190 LOC, all 🟢 low risk).
4. **Out of scope (final):** MQTT (rollback correct), cloud APIs, video streaming, arbitrary settings UI, reflection/metaClass.

**Deliverable:** Full audit report (4 sections) posted to Mads for prioritization. NOT pushed to decisions.md (is for team decisions, not audit recommendations).

**FKB REST API learnings:**
- All data types already supported: integers (brightness, timers, volume), booleans (settings), strings (URLs, text), JSON objects (device info)
- 350+ config parameters via `setStringSetting`/`setBooleanSetting` — too many to safe-gate in UI; generic key-value setter is out-of-scope
- No video streaming; `getScreenshot` returns static JPEG only
- Password auth is LAN-local query param — cleartext but already documented as protocol design limitation

---

## Session Arc 2026-05-18: Multi-Platform Audit (MQTT Discovery, FK v0.3.0, T6 v0.4.0)

**Summary:** 3 simultaneous audit passes completed:
1. **Hubitat MQTT Platform Survey** — Hubitat 2.4.4.155+ ships built-in MQTT broker; `interfaces.mqtt` stable since 2.2.2. FK v0.4.0 MQTT subscriber is now viable without external broker.
2. **Fully Kiosk v0.3.0 HA Gap Analysis** — 7 picks identified: brightness-scaling bug fix + 6 rich sensor attrs from existing poll + overlay-message command + utility commands (toBackground, clearCache, forceSleep, exitApp) + video playback (playVideo, stopVideo) + kiosk/lock toggles + checkInterval spam fix. Total delta: ~116–136 LOC.
3. **T6 Pro v0.4.0 HA Gap Analysis** — 3 picks: descriptionText on 3 event handlers + thermostatFanState enum fix + Notification type 9 handler. ~27 LOC.

**Key Learnings:** Built-in MQTT broker eliminates infrastructure barrier for FK pivot. Driver rubric "0 pts for MQTT" is now outdated; recommend raising MQTT-capable LAN protocols to ≥10 pts.

**Deliverables:** All 3 reports generated and merged into decisions.md (2026-05-18).

## Prior Status

---

## Audit Verdicts — 2026-05-18 Complete Cycle

### PurpleAir AQI Virtual Sensor (pfmiller0/Hubitat)

**Verdict:** INSTALL (88/100 Trinity rubric)  
**Date:** 2026-05-18T16:25:00-07:00  
**File:** .squad/decisions/inbox/cypher-purpleair-pfmiller-audit.md → merged into decisions.md

**Key Findings:**
- **Protocol:** Cloud REST on `api.purpleair.com/v1/sensors`
- **Auth:** `X-API-Key` header (free API key, no registration required for read-only)
- **Sensor modes:** Geolocation-based averaging OR explicit sensor index (neighbor's sensor)
- **EPA AQI:** Full Barkjohn 2021 conversion (wildfire smoke correction)
- **Sandbox:** `asynchttpGet` only; fully safe
- **Test requirement:** No hardware (any public sensor or hub location)
- **Last commit:** 2025-06-18 (responsive to API changes)

**Scoring Details:**
| Dimension | Score | Rationale |
|-----------|-------|-----------|
| Local vs. cloud | 10 | Cloud REST, stable API |
| Mads can test | 15 | No hardware; any public sensor by ID or hub geolocation |
| User demand | 15 | PNW wildfire season monitoring + neighbor sensor use case |
| Sandbox-safe | 15 | `asynchttpGet` + headers only, no crypto/reflection |
| Vendor stability | 15 | PurpleAir v1 API documented, API key monetized (stable incentive) |
| Effort | 10 | Zero — copy via importUrl or HPM |
| Maintenance | 8 | Last commit ~11 months ago; responsive but not 2026-active |
| **Total** | **88/100** | **Strong Fit → INSTALL** |

**Impact:** Closes the cloud-API gap for air quality monitoring. This is the **5th consecutive "install existing community driver" verdict**.

**Audit lesson:** Prior search for "PurpleAir Hubitat cloud driver" returned zero results. 
- Generic repo names (`pfmiller0/Hubitat`) don't surface well in search
- File names don't match keywords ("AQI Virtual Sensor" vs. "api.purpleair")
- User-pointed URLs override search conclusions (this driver was found via Mads's direct link)

**Recommendation:** When repo search returns zero, follow with GitHub code search (`code:"api.purpleair.com" lang:groovy`) before claiming greenfield.

---

### Five-Verdict Summary

1. ✅ **Bosch Home Connect** (2026-05-17) → INSTALL craigde/hubitat-homeconnect-v3 (67/100)
2. ✅ **Rainbird LNK** (2026-05-17) → IMPROVE-EXISTING MHedish (92/100)
3. ✅ **PurpleAir Cloud-API** (2026-05-18) → INSTALL pfmiller0 (88/100)
4. ✅ **MyQ Ecosystem** (2026-05-16) → BUILD ratgdo ESPHome HTTP (greenfield)
5. ✅ **Daikin BRP069B** (2026-05-16) → BUILD complete (v0.1.0–v0.1.7 shipped)

**Result:** Mads's complete driver stack now has **zero open BUILD candidates**.

---

## Updated Team Updates

**Archive restoration (2026-05-18T23:06:44-07:00):** Scribe corrected the archive-gate misfire from commit 06f8a35. Size threshold triggered archival without verifying date eligibility — entries dated 2026-05-16 to 2026-05-18 were moved despite the "older than 7 days" rule. All 8 entries restored to decisions.md; archive cleared to placeholder.

**Canonical ledger:** decisions.md now has full session context (Bosch spec, consumer auth analysis, driver rubric, opportunity survey, PurpleAir audit). Size: 24 KB → ~92 KB.

---

## Learnings from Audit Cycle

**Search strategy refinement:** Code-level search (`code:"string" lang:groovy`) catches file-name mismatches that repo-level search misses. For next audit: always code-search if repo search returns zero.

**User-pointed URLs as primary:** When a user provides a direct GitHub URL, treat it as primary evidence. Override prior search-based conclusions.

**Platform constraint documentation:** OAuth callback pattern is only applicable if vendor provides public OAuth Authorization Server + public client registration. When vendor offers none (Gemstone, SunStat), pattern is not applicable. Governance principle established.

---

## Cross-Agent Update — Tank's 3 Community Driver Forks Shipped (2026-05-18T17:25:00Z)

**From:** Scribe housekeeping merge (post-Tank session)

Trinity's 3 code-quality audits have all shipped as Tank forks:
- **Honeywell T6 Pro** (commit 1dc51af) — permanent fork; txtEnable BLOCKER + fan-state/scheduler fixes
- **Fully Kiosk** (commit 32a9f2c) — permanent fork; password security + event hygiene + descriptionText fixes
- **PurpleAir AQI** (commit ff3410f) — **PR-bound staging fork** (delete once upstream accepts PR)

**Your next action:** Review the PR draft at `drivers/purpleair-aqi/UPSTREAM-PR-DRAFT.md` before Mads submits upstream. The 3 fixes are minimal (AQ&U, LRAPA/Woodsmoke case fixes, failCount precedence) and low-friction for maintainer review.

---

## 2026-05-18T17:51:43-07:00 — Honeywell T6 Pro Z-Wave Feature-Gap Survey

**Task:** Survey full Z-Wave capability surface of TH6320ZW2003; identify gaps vs. v0.2.0 driver; rank for v0.3.0.

**Model confirmed:** TH6320ZW2003, mfr:0039 prod:0011 deviceId:0008 (from driver fingerprint). Z-Wave Alliance product ID 2893. Later SKU is TH6320ZW2007 (SmartStart added).

**CCs surveyed:** 20 CCs in fingerprint inClusters + 5 in CMD_CLASS_VERS. Total unique: ~22. **Config params surveyed:** 42 (ZW2003) + 3 additional (ZW2007 only, params 43–45).

**Top-3 v0.3.0 picks:**
1. **Emit `thermostatFanState` attribute** — data already parsed, never emitted. 10 lines. Free (no new polling).
2. **Battery-low notification** — events 10/11 of type-8 Notification silently `break`. Safety gap for battery-powered units.
3. **Fix `CMD_CLASS_VERS` octal bug** — `043:2` is Groovy octal (= 0x23), not 0x43 (Thermostat Setpoint). 1-char fix.

**Ruled out (confidently):**
- Schedule CC: not in device CC list; device has no Z-Wave schedule programming.
- Keypad lock: no Indicator CC on ZW2003.
- Outdoor temp via Z-Wave: param 3 enables wired sensor, display-only, NOT reported to controller.
- Vacation hold CC: no separate hold CC; setpoint-based hold already works.

**Protocol learnings:**
- Z-Wave product portals (z-wavealliance.org) are inaccessible. Go directly to OpenZWave XML repos (domoticz/domoticz Config/, OpenZWave upstream) and OpenHAB ZWave binding DB for device config data.
- For Z-Wave thermostats: device fingerprint `inClusters` is the authoritative CC list. CMD_CLASS_VERS can contain extra CCs from author assumptions — cross-check against fingerprint.
- ThermostatFanState CC v1 (0x45): values include "running high" (stage 2) and "running medium" — useful for 2-stage HVAC monitoring. Not the same as ThermostatFanMode.
- Groovy numeric literals: `043` is **octal** (= decimal 35 = 0x23), NOT 0x43. Always use `0x` prefix for hex in Groovy/Hubitat driver maps.

**Deliverable:** `.squad/decisions/inbox/cypher-honeywell-t6-zwave-survey.md`  
**Skill filed:** `.squad/skills/zwave-thermostat-audit-checklist/SKILL.md`

---

## 2026-05-18T17:50:00Z — PurpleAir v0.2.0 Polish — PR-Bound Constraint Dropped

**Directive update (2026-05-18T17:31):** Mads's "quality-first" priority supersedes the earlier PR-bound staging constraint. PurpleAir fork v0.2.0 has been polished with the full deferred-improvement backlog and Mads namespace ownership (commit 4b720aa). 

**Implications for UPSTREAM-PR-DRAFT.md:**
- The fork now contains changes beyond the original 3-bug PR scope (emitIfChanged on events, descriptionText additions, sentinel guards, etc.)
- UPSTREAM-PR-DRAFT.md is **retained** with a status note in case Mads wants to cherry-pick the original 3 fixes (AQ&U string, LRAPA/Woodsmoke case, failCount precedence) as a minimal upstream PR later
- But it is **no longer the design goal** — Mads owns the driver now; upstream PR is optional if feasible

**Your action:** If you plan to do an upstream PR, make it a separate follow-up decision after v0.2.0 validation. The fork is no longer constrained to diff-minimalism against the upstream PR shape. Clean diff at 4b720aa shows the full v0.2.0 polish (namespace + style alignment + all deferred improvements).

---

## 2026-05-19T01:55:00Z — Platform Correction: Hubitat Z-Wave JS

**From:** Mads during T6 Pro v0.4.0 shipment work

**Correction:** "Hubitat uses Z-Wave JS now too" — this invalidates prior framing in the HA gap analysis where Hubitat's driver model was framed as a platform constraint preventing auto-entity-generation.

**Implication:** Cypher's HA comparison (entries 2026-05-18T18:30) framed HA's per-param entity generation as an architectural advantage Hubitat couldn't match. This framing assumed Hubitat lacks a Z-Wave JS protocol layer comparable to HA.

**Reality:** Hubitat now has Z-Wave JS available (extent unknown to the team at this session). This means:
- Config parameter metadata APIs may be available to Hubitat driver authors
- Device-file-driven entity generation patterns may be possible in Hubitat
- Attribute-binding mechanisms in Hubitat's Z-Wave JS may mirror or differ from HA

**Future research (v0.5.0+):** What does Hubitat's Z-Wave JS layer expose to driver authors?
1. Do driver authors have access to configParams metadata from the Hubitat device-info database?
2. Can drivers emit entities dynamically based on device config (like HA does)?
3. What attribute-binding patterns does Hubitat's Z-Wave JS support vs manual Groovy emits?
4. Is TH6320ZW device-file data available in the Hubitat runtime?

**Captured as memory** for Cypher's next protocol-architecture research sprint.

---

## 2026-05-18T19:50:00-07:00 — Mads Feedback: FKB MQTT v0.4.0 Reality-Check

**Context:** Cypher's 2026-05-18 FKB MQTT reality-check analysis (merged into decisions.md) identified 4 event-name mismatches in the v0.4.0 driver's handleFkEvent() handler.

**Mads feedback:** "This analysis was exactly what I needed. Your research confirmed the architecture was right and saved me hours of debugging. The fact that you found 'FKB only shows 3 fields' was the breakthrough — turns out the Enable MQTT toggle hides the config fields. That's why I couldn't see the Client ID and Topic customization options."

**Impact on v0.4.0 design:** The v0.4.0 driver shipped with the correct topic structure and subscription pattern because Cypher's earlier feasibility work had pinned down what FKB actually exposes. The reality-check then caught the event-name mapping bugs (wrong case labels), which v0.4.3 fixed within hours of identification. This rapid-response cycle (Cypher research → Tank implementation → live validation → next patch) worked exceptionally well.

**Lesson for future audits:** When a driver ships with new protocol support, a same-day reality-check against live device output (or official docs) catches silent-failure bugs that unit tests won't find. Mads's tablet running v0.4.0 with MQTT broker connected was the test harness; Cypher's analysis was the oracle.

## Session Arc 2026-05-19: Quality Audits Complete — PurpleAir v0.3+, FK v0.6+

**Task:** Conduct post-implementation audits on PurpleAir v0.3+ (async guards, new attributes) and Fully Kiosk v0.6+ (feature scope validation).

**Audit Verdicts:**
1. **Cypher #10 — Fully Kiosk v0.6+:** No feature creep detected. MQTT-to-REST migration stable. v0.5.0 production-ready; v0.6+ scope approved (RAM/storage sensors, timers, reboot button).
2. **Cypher #11 — PurpleAir v0.3+:** Async guards validated, parseJson blank-coordinate fix confirmed, new attribute averaging logic sound (lowest confidence score, skip missing values). Production-ready.

**Deliverables:** Orchestration logs created (.squad/orchestration-log/2026-05-19-043500Z-cypher-*.md)

