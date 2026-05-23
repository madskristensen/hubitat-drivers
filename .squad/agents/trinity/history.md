# Trinity — Architect

## Status Summary (2026-05-23)

**Current focus:** Climate Advisor v0.1.0 shipped. Design audit for v0.2.1 filed at `.squad/decisions/inbox/trinity-climate-advisor-v0.2.1-design-audit.md`. 22 findings (4 critical, 4 high, 8 medium, 6 low). Top 3 arch changes: (1) single-child architecture rework — PERMANENT, (2) restore 4-level severity, (3) clearMessages/acknowledge + open-windows comfort family. v0.2.1 is a 4–5 day release. Namespace fix (`"madskristensen"` → `"mads"`) noted as F-21, Cypher owns it.

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

## 2026-05-23 — Climate Advisor v0.1.0 Post-Ship Audit

**Scope:** Architecture & feature-design audit of Tank's shipped implementation vs. Trinity's v2 spec.  
**Output:** `.squad/decisions/inbox/trinity-climate-advisor-v0.2.1-design-audit.md` (20 findings)

### Key audit learnings

#### Always spec the severity model as an integer constant set, not just a count
The v2 spec named severity levels 0–3 but Tank found the spec named "danger" as level 3 while all real alert logic was written as if max was 2. The 4-level model collapsed to 3 in implementation. In future, spec should include a concrete `@Field static final Map SEVERITY = [clear:0, info:1, warning:2, danger:3]` reference so Tank cannot independently remap them.

#### "Optional" vs "required" for inputs must be explicit in spec pseudocode
`zone${i}IndoorTempSensors` landed as `required: true` because Tank saw "required" in the subscription logic but not in the preferences. Spec should annotate each preference with `required: true/false` explicitly.

#### Spec commands on child devices must be tested against "can user invoke this?" not just "does the app need this?"
`clearMessages()` and `acknowledge()` were in the spec but not implemented. Post-audit learning: any command listed in the spec that the USER (not the app) invokes must be called out as "user-invocable" to ensure Tank treats it as a public device command, not an internal app method.

#### One child device per app instance — PERMANENT for advisory apps
Mads directive 2026-05-23: Climate Advisor spawns exactly ONE child device (house aggregate). Per-zone children are wrong. Zones are configuration, not devices. Per-zone data surfaces as indexed flat attributes (`zone1Severity`, `zone1Message`, etc.) plus a `zoneStatuses` JSON attribute on the single child. This is permanent — never re-introduce zone-scoped child devices. The `createDashboardDevices` toggle (default off) gates whether even this one child is created. Pattern is reusable for any advisory app: one app instance = one child device maximum.



#### AQI two-tier threshold is always better than one
Whenever an air quality (or any physical) threshold drives alerts, always expose both a warn and a danger threshold as configurable preferences. Single hard-coded thresholds frustrate users with atypical sensitivity or use cases.

**Last updated:** 2026-05-23 (Climate Advisor v0.1.0 audit)

---



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

---

## PERMANENT ARCHITECTURAL BOUNDARY (2026-05-23)

**Piston Coexistence — v0.1.0 and beyond:**

Mads confirmed that Climate Advisor v0.1.0 is **advisor-only.** The existing webCoRE pistons ("Thermostat management" and "Sunroom climate") will retain permanent ownership of HVAC actions (off-on-open, mode/setpoint restoration via RM preset rules). Climate Advisor owns notifications, alerts, and predictive guidance.

This separation is permanent: **not to be revisited for v0.2.0+.** The pistons have been running stably for 6 months; porting working code into new code is where regressions live. The "mode preset rules" restoration pattern is cleaner than Climate Advisor re-implementing it.

**Why this works:**
1. No dual-write conflict — Climate Advisor never calls `thermostat.off()` or `setThermostatMode()`
2. Failure isolation — if RM rules break, Climate Advisor alerts still work
3. Complementary timing — Climate Advisor warns before setpoint breach; piston catches after 60s
4. Cleanly scoped — SharpTools status attributes + messaging never touch thermostat control

**Previous consideration (withdrawn):** v0.2.0+ might consolidate config under Climate Advisor + toggle `controlHvac`. **This idea is dropped.** Two app/piston screens for related-but-distinct concerns is fine. No value in absorbing working code just to unify config.

**Last updated:** 2026-05-23 (Climate Advisor architecture v2 — generic app, dropped HomeKit)

---

## Learnings

### Generic App Preference Pattern (2026-05-23)

When building a community-distributable Hubitat app, never hard-code device names, zone names, or attribute names — even when designing for a specific user's home. All devices must be user-selectable via preferences. All attribute names (especially non-standard ones like `aqi` vs `airQualityIndex`) must be user-configurable with a sensible default.

For N-zone configurations where N is user-defined: define a fixed maximum and use numbered input names (`zone1Name`, `zone2Name`, etc.). Build the runtime zone list dynamically by iterating through the configured count; for Climate Advisor, use `dynamicPage` with `zoneCount` 1–10 and render only the active numbered sections.

### Drop HomeKit When It Adds No Value (2026-05-23)

`ContactSensor` was selected as a HomeKit proxy because it's the cleanest binary signal that homebridge maps. But when HomeKit is not a requirement, that capability adds semantic confusion (the device isn't actually a contact sensor) and creates a false coupling to a HomeKit bridge pattern. The rule: if a capability exists purely to satisfy one integration that the user isn't requiring, drop it. Custom attributes for the actual target platform (SharpTools) are cleaner and more expressive.

Corollary: don't let one speculative "it might be useful for X" question drive capability selection. Confirm the requirement first.

### Rain Device Pattern: No Standard Hubitat Capability (2026-05-23)

Hubitat has no `capability.rain`. Weather devices (e.g., OpenWeatherMap app device) expose a `weather` (STRING) attribute. Make both the attribute name and the keyword configurable preferences. Default `rainAttribute = "weather"`, `rainKeyword = "rain"`. This pattern handles any weather device a user might have.

### Climate Advisor Dynamic Zone Pattern (2026-05-23)

Use a Hubitat `dynamicPage` with app-level `zoneCount` constrained to 1–10, then render numbered zone inputs (`zone${i}Name`, `zone${i}Thermostats`, `zone${i}IndoorTempSensors`, etc.) for the active count. Build runtime zone maps by iterating `1..zoneCount`; keep DNIs stable by zone index so renames only update labels.

### Climate Advisor Predictive Alerts (2026-05-23)

Predictive close-window alerts are WARNING severity between INFO=0 and ALERT=2. Cooling pre-alert requires cooling-capable mode, indoor temp within the cooling offset, outdoor temp hotter than indoor, outdoor trend rising, and open contacts when contacts exist; heating mirrors this with outdoor colder and falling.

### Climate Advisor Trend Buffers (2026-05-23)

Trend detection stores `[now: epochMs, t: value]` samples in app state, trims older than `trendWindowMinutes + 5`, and computes °F/10min from newest versus oldest sample inside the window. Fewer than two samples or less than five minutes of span yields `unknown`; predictive alerts skip unknown trends.

### Climate Advisor Child Device Split (2026-05-23)

Use one aggregate child plus one per-zone child, all backed by `drivers/climate-advisor/climate-advisor-device.groovy`. The aggregate child preserves app-wide `houseStatus`; per-zone children make SharpTools tiles simple without parsing zone JSON.
