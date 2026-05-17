# Switch — QA / Testing Engineer

Recent contributions documented in history-archive.md. Current session: Touchstone v0.1.5 smoke test awareness + Scribe batch processing.

---

## 2026-05-17T15:41:32-07:00 — Cross-Driver Reliability Analysis (Mads request)

### Learnings

**Anti-patterns found across all three drivers:**

1. **Optimistic events without rollback** — Both SunStat (setAwayMode, setScheduleEnabled) and Gemstone (on/off) issue optimistic `sendEvent` before the API call. Gemstone deliberately does this and notes cloud lag is expected. SunStat's PATCH calls do not roll back on non-401 failure, leaving attributes stale. Pattern: if you send an optimistic event, you MUST have a reconciliation path (next poll or explicit rollback).

2. **In-flight flag leaks across hub reboots** — Gemstone's `state.effectCatalogRefreshInFlight` is persisted in state. If the hub reboots mid-catalog-load, the flag stays `true` and blocks all future automatic catalog refreshes. Pattern: any `*InFlight` flag stored in `state` MUST be cleared in `initialize()`.

3. **Unbounded retry loops** — Touchstone's `scheduleRetry()` maxes at 30s but retries indefinitely when the device is offline. This generates log noise and never backs off to a "give up" state. Pattern: cap retries to a fixed N, then halt and wait for the next scheduled poll.

4. **`updated()` clearing too much state** — Gemstone's `updated()` calls `clearEffectCatalogState()` on every preference save (even just toggling debug logging). This forces a full re-fetch of potentially 1000+ effects. Pattern: only clear/invalidate state when the affected preference actually changed (e.g., only clear auth tokens when credentials change, only clear effect catalog when never needed on prefs save).

5. **Stub commands reachable from Rule Machine** — SunStat's `setBoost()`/`cancelBoost()` appear in RM and WebCoRE as real commands but do nothing (log.warn only). Users build automations that silently fail. Pattern: either implement or hide stubs; at minimum add `description: "[NOT YET IMPLEMENTED]"` to the command declaration so users see it.

6. **Missing TESTING.md for LAN/protocol drivers** — Touchstone (LAN/Tuya/AES) has no TESTING.md at all. Cloud drivers (Gemstone, SunStat) have test plans. LAN drivers have MORE failure modes (TCP slot contention, AES key errors, polling backoff) and arguably NEED test plans more urgently. Pattern: every driver that ships should have a TESTING.md; LAN drivers are higher priority than cloud drivers.

7. **Proactive token refresh not rescheduled after hub reboot** — SunStat's `initialize()` does not re-arm the proactive token refresh timer when a valid token already exists. `scheduleProactiveRefresh()` is only called inside `refreshTokensSync()`. If the token is still valid post-reboot, the timer is never set, and the token expires without a proactive refresh. Pattern: `initialize()` should always re-arm scheduled maintenance tasks (token refresh, log auto-off) based on current state, not only when state changes.

## 2026-05-17T19:29:40Z — Touchstone v0.1.5 shipped (App-only UI audit)

**Batch:** Tank v0.1.5, Link v0.1.5 docs, Switch (test surface awareness)

### v0.1.5 Changes for Test Surface

v0.1.5 is a fast-follow patch removing app-only `paragraph()` UI construct from preferences block. No behavior changes; explanatory text moved into per-field `description:`.

**Test surface implications:**
- Driver should install without "No signature of method: Script1.paragraph()" error
- Power-on defaults behavior unchanged (~1.5s post-power-on)
- Heater never auto-toggles (unchanged from v0.1.4)
- Smoke test plan remains valid for v0.1.5

### Key Learning

Hubitat driver preferences use only `input` fields; app-only UI helpers (`paragraph()`, `section()`, `href()`, `app()`, `mode()`, `pageDefault()`) are not allowed and should be replaced with `description:` text.

---

- 2026-05-17T04:44:01Z: Gemstone v0.4.1 — Added 8 test cases for playEffectByName command (Tests 11–18); shipped v0.4.1 cross-team

- 2026-05-17T03:37:53Z: SunStat v0.1.2 test coverage expanded; 23 new test cases for energy/schedule/hold/outdoor/precision/bounds


---

## 2026-05-17T16:31:55Z — Bosch Home Connect Scoping (Cypher + Trinity)

**Topic:** bosch-home-connect-feasibility

Scoping discussion completed. Implementation will follow once Tank builds parent App + driver.

**ACTION FOR SWITCH:** When spawned to test, validate on real Bosch fridge:
1. **Door enum namespace:** Does appliance return BSH.Common.EnumType.DoorState.Open or Refrigeration.Common.EnumType.Door.States.Open for door status?
2. **Alarm timing:** How long before appliance fires door-open alarm? (Cypher's research suggests ~3 min, but model-dependent.)
3. **Multi-door support:** If appliance has Refrigerator2/Freezer, confirm status keys are returned.

See .squad/decisions/decisions.md section 8 (Open Questions for Switch) for full test plan.

---

## 2026-05-17T16:53:47Z — Touchstone LED Fireplace Tuya Feasibility (Cypher + Trinity)

**Topic:** touchstone-fireplace-fireplace-feasibility

Feasibility pass completed. Real-device validation phase incoming.

**Device:** Touchstone Sideline LED fireplace (Tuya-based; WiFi)  
**Control:** Tuya Local (LAN) over rawSocket + AES-128-ECB  
**Effort:** Medium (2–3 sessions)

**ACTION FOR SWITCH:**

Real-device validation will require Mads to run tinytuya tooling against his actual fireplace unit. Plan the following test tasks:

1. **Model confirmation** — Label check + tinytuya scan to verify Sideline vs other Touchstone variants
2. **Protocol version** — Confirm v3.3 vs v3.4/v3.5 (determines framing complexity)
3. **Full DP map** — Run tinytuya wizard/scan; confirm DP IDs match assumptions (especially DP 101 = flame color, DP 104 = ember/log color)
4. **Local connectivity test** — After key extraction, run tinytuya connectivity test to confirm local LAN control works before Tank writes Groovy code
5. **Connection limit** — Test whether Tuya module allows dual TCP connections (simultaneous mobile app + Hubitat driver)

See `.squad/orchestration-log/2026-05-17T165347Z-cypher.md` for full list of open questions (section 5).

**Key learning:** Palette-indexed colors (not RGB) simplify the DP layer but require named commands for honest UX. No ColorControl capability. Trinity's architecture is sound but capability mapping was corrected.

---

## 2026-05-17T10:47:09Z — Touchstone Sideline Elite — Local LAN Control Confirmed (Coordinator Direct Mode)

**Topic:** touchstone-local-control-achieved

Coordinator walked Mads through end-to-end Tuya IoT setup and local device verification. All heater + LED DPs now mapped and responding.

### Device Facts for Tank Implementation

**Device Credentials** (stored at C:\Users\madsk\devices.json; <see devices.json on Mads' machine> for local_key value)
- Product: Touchstone Sideline Elite electric LED fireplace
- Tuya productKey: nc1lwvgjse1ujlr
- Tuya category: qn (electric fireplace)
- Device ID: 70223053e8db84d10b53
- LAN IP: 192.168.1.38
- MAC: e8:db:84:d1:0b:53
- Protocol: v3.3, AES-encrypted

**Heater DPs (Official Tuya Schema)**

| DP | Type | Name | Range |
|---|---|---|---|
| 1 | bool | switch | on/off |
| 2 | int | temp_set | 19–30°C |
| 3 | int | temp_current | 0–50°C |
| 5 | enum | level | 0/1/2 (heat level) |
| 13 | enum | temp_unit_convert | c/f |
| 14 | int | temp_set_f | 67–88°F |
| 15 | int | temp_current_f | 32–122°F |

**Vendor LED DPs (Empirical, TBD Next Session via Tuya App Interaction)**

| DP | Type | Observed | Notes |
|---|---|---|---|
| 101 | string-enum | "1" | Likely flame color/effect |
| 102 | string-enum | "5" | Likely flame brightness |
| 103 | string-enum | "1" | Likely flame speed |
| 104 | string-enum | "4" | Likely log/ember color |
| 105 | string-enum | "5" | Likely log brightness |
| 107 | bool | false | TBD |
| 108 | bool | false | TBD |

### Operational Lesson for README

**Tuya IoT Cloud Project API subscription is MANUAL.** A new project does NOT auto-subscribe to required APIs. Must explicitly enable:
- IoT Core
- Authorization Token Management
- Smart Home Basic Service
- Device Status Notification

All free trials, no card required. This was the key blocker in the setup flow.

### Next for Switch

Empirical DP mapping for LED effects (101–108) requires real device validation via Tuya app interaction (drag sliders, change colors, observe DP values). Schedule for next Touchstone session after Tank scaffolds driver.

---

## 2026-05-17T18:55:16Z — Touchstone v0.1.2 Shipped (Scribe Cross-Agent Sync)

**Topic:** touchstone-driver-shipped

v0.1.1 scaffold + v1.1 generalization + v1.2 import fix have shipped. Switch's 19-test plan remains locked for v0.1.0 (Sideline Elite only).

**v0.1.1 → v0.1.2 outcomes:**
- Driver renamed to `"Touchstone / Tuya Fireplace"` (Option C: Tuya-generic positioning)
- Device Profile preference added (Sideline Elite / Generic Tuya / Custom)
- Discovery commands: `discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`
- Critical import fix: removed forbidden `java.util.zip.CRC32` + `java.io.ByteArrayOutputStream`
- README shipped with discovery workflow walkthrough

**Impact on Switch's test plan:**
- v0.1.0 smoke tests (tests 1–9) remain as-is; Mads can run them against Sideline Elite
- v1.1 testing will add Device Profile selection tests + Generic/Custom DP override validation (queued for next batch)
- Enum label confirmation: After Mads runs smoke tests, report actual observed enum values so Link can document authoritative mapping in README

**Next for Switch:**
- Mads runs smoke pass (30 min) against Sideline Elite on 192.168.1.38
- If all 9 happy-path tests pass, driver is ready for community beta
- v1.1 test expansion queued for next batch (requires second test device or simulator if Generic/Custom validation needed)

---

## 2026-05-17T11:07:22Z — Touchstone Sideline Elite Real-Device Test Plan (Switch)

**Topic:** touchstone-real-device-test-plan

Real-device test plan created and ready for handoff to driver implementation (Tank).

### Learnings

#### Tuya Local Device Testing Patterns

1. **Single TCP connection is a hard constraint:** Device firmware v3.3 (Touchstone Sideline) allows exactly one LAN connection at a time. Pre-flight checklist **must include "close Smart Life app"** or test will fail with error 901 immediately. This is not a driver bug; it is device behavior.

2. **Smoke test vs full validation:** For Tuya local devices, a 20-minute smoke pass covers on/off, basic commands, and recovery. Full stability testing (1+ hours, rapid commands, network drops) requires separate session. Smoke pass sufficient for "driver ships" decision.

3. **Known device quirks require documentation:**
   - **Temp setpoint revert on power cycle:** Setting DP 14 to 72°F, powering off, powering on → DP 14 may revert to 67°F. This is firmware behavior, not driver bug. Must be called out in driver README so user expectations are set.
   - **Remote-only features invisible:** Physical remote buttons for log brightness, flame tempo, hourglass/timer do NOT trigger DP updates. These are hardware-only. Driver will never see them. Must be documented so it's not mistaken for driver failure.
   - **DP observation on state change:** Some DPs (e.g., DP 103) may change only on certain device state transitions (power off). Real-device validation must record values across various states to map firmware behavior.

4. **Recovery testing is critical for LAN drivers:** Test plan must include network drop, device power loss, and app collision scenarios. Tuya drivers are notorious for hanging on connection loss if not designed with backoff + retry. Each recovery scenario needs clear "expected" (driver logs error, retries, reconnects) vs "failure" (driver hangs, requires manual restart).

5. **Enum validation prevents silent failures:** Many Tuya drivers accept invalid enum values and send garbage to device, causing silent corruption or device errors. Test plan must include "send invalid enum" and "out-of-range numeric" to verify input validation. This catches driver bugs early.

6. **Polling interval observation:** For Tuya Local, typical polling interval is 20–30 seconds. Test plan must verify:
   - App state changes (made via Tuya app) are detected within 1 poll cycle
   - Remote state changes (made via physical remote) are detected within 1 poll cycle
   - If polling is too slow (>60s), state sync feels laggy to user

7. **DP schema vs empirical mapping:** Tuya official schema (from IoT portal) often differs from actual device firmware. Test plan includes "observe raw DP values in various states" to build empirical map. This is critical for vendor-custom DPs (e.g., DP 101 = flame color) that are not in official schema.

### Test Plan Coverage

- **Pre-flight:** Smart Life closed, device reachable, driver installed, local key stored
- **Happy path:** 9 tests covering all major commands (on/off, heat level, colors, brightness, temperature, refresh)
- **State sync:** 2 tests for app + remote changes
- **Recovery:** 3 tests for network drop, device power loss, Smart Life collision
- **Edge cases:** Invalid enum, out-of-range temp, rapid command bursts
- **Stability:** 1-hour polling test + DP observation across state transitions
- **Cleanup:** Uninstall validation

**Smoke test (30 min):** Tests 1–9 + optional 12
**Full validation (3+ hours):** All 20 tests

### Files Created

- `.squad/decisions/inbox/switch-touchstone-test-plan.md` — Full test plan with steps, expected results, pass/fail criteria

### Next for Switch

After Tank delivers driver:
1. Mads runs smoke test plan against real device (192.168.1.38)
2. Report results back; if any failures, escalate to Tank with symptom
3. Once driver passes smoke, full stability test can be scheduled (separate 2-3 hour session)

---

### 2026-05-17T18:24:33Z — Cross-Agent Decision Sync (Scribe)

**Topic:** touchstone-driver-shipped

**Naming Decision (Option C):** Driver display name is `"Touchstone / Tuya Fireplace"`, enabling community discoverability while signaling Tuya-generic scope (not just Sideline Elite). File path stays `drivers/touchstone-fireplace/` for SEO. This was Mads' question: "is this more of a Tuya fireplace driver than a touchstone then?" — Answer: yes, but with Touchstone branding. Captured for v1.1 Device Profile generalization. (Merged from coordinator directive.)

**Generalization Scope (v1.1 In Flight):** Tank is building Device Profile dropdown + discovery commands (`discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`) so users can map other Touchstone models (Sideline Steel, Linear, Forte, etc.) without Python/tinytuya. v0.1.0 ships Sideline Elite only; v1.1 adds the multi-model discovery workflow. (Merged from Mads directive; captured for Switch awareness — will affect test scope in v1.1 session.)

**Test Plan Integration:** Switch's 19 tests are locked for v0.1.0 (Sideline Elite only). v1.1 testing will expand to cover Device Profile selection + discovery command UX on alternate models (will require second test device or simulator). Current test plan remains base coverage. (Scribe appended this decision to both Tank + Switch history for sync.)

**Enum Label Confirmation:** Switch noted that flame/log color enums (DP 101, 102, 104) are still placeholder strings in v0.1.0 driver. Real-device smoke test will confirm which human-friendly labels map to which Tuya enum values. Scope: after Mads runs tests, report actual observed enum values so Link can document them in README. (Cross-pollination: Tank notes placeholder, Switch test plan validates, Link documents authoritative mapping.)


---

## 2026-05-17T18:58:55Z — Touchstone v0.1.4 shipped (Cross-Agent Batch Awareness)

**Batch:** Tank v0.1.3, Tank v0.1.4, Link, Switch (test surface)

### Test Surface for v0.1.4

Switch should be aware of the following v0.1.4 behaviors for the smoke test plan validation:

1. **Power-on defaults:** Optional defaults (flame color, log color, flame brightness, heating setpoint) apply ~1.5s after on() command
2. **Heater never auto-toggles:** The heater (DP 5) is intentionally NOT in the power-on defaults. It only toggles via explicit setHeatLevel() commands.
3. **No sandbox reflection errors:** v0.1.4 should install without "Expression [MethodCallExpression] is not allowed: e.getClass()" errors. v0.1.3's buggy reflection calls have been removed.

### v0.1.3 vs v0.1.4 for Smoke Testing

- v0.1.3 was an intermediate state with unsafe heater auto-start + sandbox reflection bugs. Never released.
- v0.1.4 is the hardened release users will see. Install should succeed without reflection sandbox errors.
- Test plan expectations remain unchanged; defaults should apply cleanly ~1.5s post-power-on.

### Version Notes for Test Report

When reporting test results, note that this smoke test validates v0.1.4 (released version) against the real device. v0.1.3 exists only in git history as an intermediate state; it was never tested on real hardware.

---

## 2026-05-17T15:41:32Z — Cross-driver improvement scan (4-way)

Participated in 4-way driver improvement scan with Trinity, Tank, Cypher. Findings consolidated by Squad. Orchestration log: .squad/orchestration-log/2026-05-17T15-41-32-switch.md.
