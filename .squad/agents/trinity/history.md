# Trinity — Architect

## Status Summary (2026-05-23)

**Current focus:** Climate Advisor architecture design; completed comprehensive proposal (App + child device, capability selection, multi-zone data model). Awaiting Mads sign-off for Tank implementation.

**Recent work:** 3 community driver audits completed (PurpleAir v0.4.0 shipped, T6 Pro forked, Fully Kiosk forked). 5 reusable skills extracted to .squad/skills/. Gemstone OAuth pattern documented.

---

## Core Architectural Patterns (Reusable)

1. **Parent/Child OAuth:** Parent holds tokens in state; getValidToken() guard before HTTP; children store cloudDeviceId as DataValue
2. **API Response Handling:** Check error data; unwrap envelopes in helpers; diagnostic logging on all paths
3. **Capability Metadata:** Distinct command names; descriptionText on all sendEvents; custom attributes for non-standard state
4. **State Hygiene:** Large tokens use command params not preferences; transient data stays local
5. **Async HTTP:** asynchttpGet by default; 10s LAN / 30s cloud timeout
6. **Health Monitoring:** Local sockets get HealthCheck+ping; cloud gets lastActivity only; parent cascades to children
7. **Lifecycle Safety:** Guards before audible side effects; dedup is highest risk at initialization
8. **Write-Only Property Gotcha:** `setX(x)` command shadows method dispatch; use `runEvery*` instead of `schedule(cron, method)`

---

## 2026-05-23 — Climate Advisor Architecture (PROPOSAL v2)

**Scope:** House Status / Climate Advisor virtual device on Hubitat for SharpTools dashboards + HomeKit. Full spec in .squad/decisions/decisions.md.

**Key Decisions:**
- **Architecture:** Parent App + Child Virtual Device (app = multi-device brain, child = platform-visible face)
- **HomeKit:** `ContactSensor` via homebridge-hubitat-tonesto7 (contact: open=alert, closed=clear)
- **Rich Data:** `Sensor` + custom attributes (severity 0–3, severityText, latestMessage, messages JSON, houseStatus, tempTrend)
- **Zones:** 3-zone model (Upstairs 8 windows, Downstairs 2 windows + 2 doors, Sunroom 3 doors)
- **Outdoor Data:** Backyard sensor (temp/trend, primary), Weather device (rain only), PurpleAir (AQI)
- **Thermostat Mode Gating:** Suppress setpoint alerts when mode=off/fan; rain/AQI always apply
- **BigDecimal Coercion:** Daikin reports 64.4/75.2; T6 Pro reports 69/75 — evaluation logic must coerce all setpoint reads
- **Temperature Trend:** Ring buffer in app state (default 12 samples over 30 min); compute slope for rising/falling/stable
- **All-Clear Message:** Explicit "All clear — no climate issues detected" when no problems exist
- **Announcements:** Direct `capability.speechSynthesis` to Sonos Advanced; severity threshold default=2
- **Write Commands:** `addMessage()` / `clearMessage()` are private app methods, NOT device commands
- **houseStatus Attribute:** Permanent first-class attribute (backward compat + SharpTools dashboard stability)

**Status:** PROPOSAL approved for decision merge. Awaiting Mads sign-off before Tank implementation.

---

## 2026-05-18 — Community Driver Audits (3 drivers)

| Driver | Verdict | Key Fixes | Status |
|--------|---------|-----------|--------|
| **PurpleAir AQI** (pfmiller0) | PR upstream | String math, LRAPA case fixes, failCount parens | v0.4.0 shipped |
| **Fully Kiosk** (GvnCampbell) | FORK | Password security + emitIfChanged + descriptionText | v0.4.5 shipped |
| **T6 Pro** (djdizzyd) | FORK | txtEnable BLOCKER + currentValue() method fix | v0.4.0 LIVE |

**v0.2.0 Polish:** All 15 deferred improvements applied. BigDecimal comparisons, descriptionText, null guards, state management best practices.

---

## PurpleAir v0.3.0–v0.4.0 Audit Learnings

- Groovy enum preferences stay as strings; coerce before math (`"60" * 5` → `"6060606060"`)
- Geospatial: latitude constant, longitude shrinks by cos(latitude)
- Cloud drivers: treat lastActivity as coarse signal; keep disabled polling disabled
- Key state history by stable ID, not human names
- Release hygiene: single-line changelog entries for extraction

**v0.4.0 shipped with 5 bugs fixed:** String-math, polling retry, pole clamp, zero-distance averaging, async guards.

---

## Future Work — Archived to history-archive.md

Detailed learnings from Daikin, Gemstone, SunStat, MyQ, Rainbird, Bosch, OAuth, and driver rubric.

**Cross-agent contributions:**
- Skill extraction: 5 reusable skills in .squad/skills/
- Gemstone OAuth pattern: general-purpose auth-before-dedup
- Driver quality checklist: post-fork audit discipline

---

**Last updated:** 2026-05-23 (Climate Advisor architecture; history summarized below 15,360 byte threshold)
