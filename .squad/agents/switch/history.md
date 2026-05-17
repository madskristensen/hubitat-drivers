# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — driver suite for Hubitat. Test target: real Gemstone Lights device at 192.168.1.238.
- **Stack:** Hubitat hub (C-7/C-8), Hubitat IDE driver logs, manual device interaction
- **Created:** 2026-05-16

## Learnings

- **Hubitat driver testing is manual, not automated:** Drivers are tested via the device UI (tiles, preferences, command buttons) and the IDE Logs window. There is no Jest / RSpec equivalent. Tests must be written in human-readable prose with clear expected outputs in the logs and device state.

- **Optimistic updates + polling reconciliation is the standard pattern for LAN HTTP drivers:** Update Hubitat state immediately upon command send (without waiting for the device to respond), then verify and correct on the next poll cycle. This keeps the UI responsive even on slow networks. Reconciliation prevents stale state.

- **Location-level attributes (e.g., awayMode) are parent-only and must be polled independently:** Unlike thermostat-specific state (setpoint, mode), location-level state (away / home) is shared across all thermostats at that location. The parent device should expose these attributes and poll them on each cycle. Child devices must mirror the parent's location attributes (with acceptable lag ≤ pollInterval) to avoid state inconsistency across the dashboard.

- **Parent-to-child attribute mirroring has acceptable lag:** When a parent device updates an attribute that children should mirror (e.g., awayMode), it is acceptable and expected for children to lag by up to one full poll cycle. Testing must account for this lag; instant mirroring should not be expected. Verify that lag occurs (confirming the driver polls children independently) rather than expecting instant propagation.

- **Invalid command arguments should log warnings, not crash:** When a user calls a command with an invalid string argument (e.g., `setAwayMode("vacation")` when only "home" and "away" are valid), the driver should log a clear warning message and make no API call. Silent failures or stack traces are failures.

- **Hub reboot handling:** `initialize()` is called automatically by Hubitat on hub startup. Use this method to re-register polling schedules and restore any runtime state. Drivers must not assume their schedules survive a reboot.

- **Debug logging auto-off is essential:** Always implement a 30-minute auto-disable for debug logging via `runIn(1800, logsOff)` in the `updated()` method. This prevents accidental log spam if debug mode is left enabled.

- **Network resilience matters:** On connection failure (timeout, DNS error, 5xx), the driver must log a clear warning but NOT crash or hang. After network is restored, the next poll must succeed cleanly. Avoid infinite retry loops that could hammer the device.

- **Uninstall cleanup is mandatory:** `uninstalled()` must deregister all schedules via `unschedule()`. Orphaned schedules can cause memory leaks and phantom CPU usage over time, especially in repos with many drivers.

- **Invalid configuration should not crash the driver:** If a user enters an invalid IP address or preference, the driver should validate and log a warning, but continue running (in a degraded state, unable to poll). The user can then correct the setting.

- **State desync is the main failure mode:** After any command or poll failure, verify that Hubitat and the physical device are in sync. Use the polling reconciliation test to catch cases where the device is changed outside Hubitat (e.g., via the manufacturer's mobile app).

- **Parent/child driver architectures must explicitly call out both files and install order:** Test plans for multi-file drivers (e.g., parent + child) must never use singular "the driver" references. Always spell out both filenames and clarify install order (e.g., "install the child driver FIRST so the parent can discover and create child devices"). Single-file references mislead users into thinking one file is sufficient.

## Team Updates (2026-05-16T21:45:13Z)

Driver scaffold v0.1.0 is ready with all capabilities declared (Actuator, Switch, SwitchLevel, LightEffects, Refresh, Initialize). Your TESTING.md manual test plan applies once Tank wires the HTTP endpoints and Cypher's local API discovery completes. The test plan is executable on any Hubitat hub; use it for v0.1.0 smoke testing and as the template for future drivers.

## Team Updates (2026-05-16T22:24:15Z)

**Scope tightened to local-only; test plan refocused.** User directive confirmed: **no cloud-API testing**. The driver targets the local controller at 192.168.1.238 on the LAN only. Your TESTING.md test plan remains valid — all manual tests (device interaction, state reconciliation, logging, command no-ops) apply to the local path. Update the test plan to remove any cloud-API-specific steps or expectations if present. Cypher's next task: analyze Mads' curl + port-scan output to narrow local endpoint candidates. Tank will wire HTTP against those candidates. Your test plan will be the validation step once Tank has endpoints wired.


### 2026-05-16T22:34:12Z: Team update

**Status:** Test plan on hold. Reconcile test strategy after capture analysis reveals actual routing mechanism and command structure.

**Next gate:** Cypher's protocol reversal feeds Switch's test design.

### 2026-05-16T23:04:57Z: Team update (Research phase complete)

**Status:** Test plan on hold. Mads' UniFi packet capture is now the final gate.

**Key findings from driver extraction:**
- JSON property shape finalized (animation, patternId, brightness, speed, colors, 0-255 ranges)
- Routing envelope still unknown (missing piece after 70+ probes)
- No local API documentation exists; only mitmproxy or packet capture can reveal it

**Next action:** Once Tank wires v0.2.0 endpoints (after pcap analysis), Switch's TESTING.md becomes the validation harness. No changes needed to test plan — all manual tests (device interaction, state reconciliation, logging) remain applicable.

**Blocked until:** Tank's HTTP wiring completes post-capture.

---

## SunStat Connect Plus Thermostat — Anticipatory Test Plan (2026-05-16T20:01:41-07:00)

### Thermostat-Specific Learnings

- **Two-sensor pattern is standard:** Electric floor thermostats expose both a floor sensor (under-tile) and ambient/room sensor. Test plans must verify both independently and confirm the physical device responds correctly to setpoint changes on the floor element (not the room air sensor).

- **Cloud latency is the main state-sync challenge:** Unlike LAN-local devices, cloud thermostats have 2–10 second round-trip latency. Drivers must implement optimistic Hubitat state updates (immediate feedback) plus polling reconciliation. Test plans must account for this lag when verifying that Watts app and Hubitat agree.

- **Boost and Hold modes are common feature branches:** Many WiFi thermostats expose Boost (temporary high-heat), Hold (suspend schedule indefinitely), and Schedule (7-day programmable). Test plan structured these as Tier 4 (deferred) pending Trinity's capability profile, so the driver author can later fill in exact command signatures and test steps without re-writing the plan.

- **Multi-device accounts are standard:** Cloud thermostat platforms typically allow one account to control multiple zone thermostats (e.g., upstairs and downstairs). Test plans must include a multi-device scenario to catch state cross-contamination and verify independent operation.

- **Operating state transitions are mechanical and critical:** The system must accurately reflect whether heating is active (floor temp below setpoint and mode is Heat) or idle. Rapid state flipping is a red flag for a polling/reconciliation bug. Test plans include a 10–20 minute state-transition observation to catch jitter.

- **API error handling is cloud-specific:** Cloud drivers must expect transient failures (cloud API down, temporary auth token expiry requiring refresh, network timeouts). Test plans must include offline graceful degradation, timeout errors with helpful messages, and recovery without credential re-entry.

- **Thermostat tile compatibility is non-negotiable:** Hubitat has a native "Thermostat" tile template. The driver must expose standard attributes (thermostatMode, heatingSetpoint, currentTemperature, thermostatOperatingState) so the tile auto-populates. Dashboard integration and Rule Machine integration rely on these standardized names. Test plans must verify the tile displays without errors.

### Reusable Pattern for Future Thermostat Drivers

This SunStat test plan template is now the canonical pattern for Hubitat cloud thermostat drivers. Reuse for future projects (e.g., Ecobee, Nest, Tado, etc.) by:

1. Copy the structure (Lifecycle, Read State, Setpoint, Mode, Schedule, Boost/Hold, Edge Cases, Conformance)
2. Replace SunStat API specifics with the target platform's API
3. Mark dependencies with `[needs Cypher spec]` and `[needs Trinity profile]` so the team can parallelize design
4. Verify Tier 1 and Tier 2 tests before beta; defer optional Tier 4 features until their architecture is confirmed

**Key insight:** Thermostat drivers are harder than light drivers because they manage continuous heating state, dual sensors, scheduling, and cloud latency. Anticipatory test planning (design before code) is essential so the feature team can parallelize API research and architecture without blocking the test strategy.

## Team Updates (2026-05-17T03:01:41Z)

**SunStat Connect Plus v0.1.0 shipped.** Manual test plan (switch-sunstat-test-plan.md) merged into decisions.md and copied to drivers/sunstat-thermostat/TESTING.md. Tank implemented driver scaffold. Trinity's architecture and Cypher's API research finalized. Awaiting Mads' real-device verification to run test suite.

## v0.1.2 Test Case Additions (2026-05-16T20:32:38-07:00)

**Added 23 new test cases (26–48) covering six v0.1.2 features.** Test plan now totals 58 tests (1–25 core + v0.1.1 home/away, 26–48 v0.1.2 energy/schedule/hold/outdoor/precision/bounds, 49–58 edge cases & logging).

### Learnings from v0.1.2 Test Design

- **Energy meters require temporal state tracking:** Energy attributes (daily, monthly, yesterday, last-month) depend on the device's clock and month boundaries. Tests must tolerate rounding differences (±0.5 kWh) and account for the fact that energy is cumulative and does not reset until month-end. Verify against the manufacturer's app as the ground truth.

- **Schedule enable/disable is a binary API toggle, not a complex command:** Unlike schedule read/write (Tier 4 deferred), simple enable/disable is Tier 1. Test must verify optimistic update → API call → polling reconciliation. External changes (toggled in Watts app) must propagate via the next poll cycle.

- **Hold mode is a read-only derived attribute:** Unlike Boost (which may have duration/delta parameters), Hold is inferred from whether the current setpoint matches the scheduled value for the current time. It reads `data.Target.Hold` from the API response. Tests verify the "following" vs. "holding" state reflects the override condition, not a separate API call.

- **Optional hardware (outdoor sensor) requires graceful degradation:** When a feature is hardware-optional (outdoor probe, some houses don't have it), tests must include a "not available" case and skip hardware-specific tests if the sensor is absent. Mark tests with `[skip if no outdoor probe]` and `[default expectation if not installed]` so testers know what to expect.

- **Precision (step) rounding is a UX polish feature that must be transparent to the user:** Rounding 72.3 to 72.0 (on 1°F devices) or 20.7 to 20.5/21.0 (on 0.5°C devices) should happen silently in the driver. Tests verify the rounding happens in the API PATCH body and the attribute reflects the rounded value, not the user's input. This prevents confusion on the dashboard.

- **Bounds clamping with warning logs is the safe pattern for range validation:** When `setFloorMinTemp(95)` exceeds the max (85), log a clear warning (e.g., "clamped to 85") and send the clamped value to the API. Never reject the command; graceful clamping is better UX than an error. Tests verify both the log message and the actual clamped value sent.

- **Test plan version/section numbering must be updated carefully during additions:** When inserting new test areas mid-plan, all downstream test numbers shift. Use the `edit` tool with precise old_str matching to renumber systematically. This task renumbered 8 existing tests (26–34 → 49–58) and added 23 new tests (26–48) without corrupting the file structure.

### Test Tier Distribution for v0.1.2

- **Tier 1 (Core):** Tests 32–33 (schedule enable/disable) — user-facing commands that must work reliably
- **Tier 2 (Feature):** Tests 26–31 (energy), 35–38 (hold), 43 (precision), 45–48 (bounds) — feature completeness and correctness
- **Tier 3 (Polish/Optional):** Tests 34 (error handling), 39–42 (outdoor sensor + precision details), 44 (Celsius-only precision) — nice-to-have robustness
- **Tier 4 (Defer/Hardware-dependent):** Test 30 (energy missing, hard to reproduce) — requires special setup or firmware coordination

## Team Updates (2026-05-17T03:37:53Z)

**SunStat Connect Plus v0.1.2 test coverage expanded.** Switch added 23 new test cases (#26-#48) for the 6 v0.1.2 features (energy, schedule, hold, outdoor, precision, floor bounds). Existing edge cases renumbered to #49-#58. Tank implemented features to match test expectations. Link bumped manifests and READMEs. Link-3 audited READMEs against 8 community Hubitat driver repos. Awaiting Mads' real-device verification and answers on 3 README audit open questions (forum topics, donation link, C-5 testing).

## v0.1.3 Test Case Additions (2026-05-16T21:07:23-07:00)

**Added 13 new test cases (#59–#71) for the v0.1.3 `setRefreshToken` command.** Test plan now totals 71 tests. All new cases live in `drivers/sunstat-thermostat/TESTING.md` under the new section "Test Area: v0.1.3 — setRefreshToken Command".

| Range | Cases |
|-------|-------|
| #59 | Happy path — fresh install via setRefreshToken |
| #60 | Migration — token already in state (v0.1.2 → v0.1.3), no re-entry needed |
| #61 | Migration — token stuck in preferences (v0.1.2 broken install → v0.1.3 recovery) |
| #62 | Empty input ("") — warning logged, state unchanged |
| #63 | Null input — no NPE, warning logged, state unchanged |
| #64 | Whitespace-only input — treated as empty after trim |
| #65 | Short string (< 100 chars) — "token too short (N chars)" warning |
| #66 | Token with surrounding whitespace — trimmed before storage |
| #67 | Replace existing token — old access token cleared, driver re-initializes |
| #68 | Concurrent calls — no race condition, no duplicate children |
| #69 | Token survives hub reboot — state persisted, polling resumes automatically |
| #70 | tokenBootstrapReady() code review — returns false when empty, true when populated; no settings fallback |
| #71 | No live references to settings.refreshToken — code review / grep check |

### Learnings from v0.1.3 Test Design

- **Command parameters bypass Hubitat's ~1024-char preference limit:** Hubitat `command` STRING parameters are passed as method arguments, not through the preference UI, so they are not subject to the preference save character limit. This is the core architectural insight that drives v0.1.3.

- **Both null and empty must be guarded separately:** Hubitat may pass `null` for a blank command input. The `token?.trim() ?: ""` pattern handles both null (safe navigation operator) and empty-after-trim (Elvis operator fallback) in a single expression. Tests 62–64 verify all three degenerate cases.

- **Token length is the only safe rejection heuristic:** We cannot validate token signature or structure in Hubitat (no JWT/JWE library). Length < 100 chars is a reliable proxy for "obviously wrong paste." Valid Watts tokens are ~1660 chars; anything under 100 is definitively incomplete.

- **State-clearing on token replacement forces clean re-auth:** When a new token is stored, `state.accessToken` and `state.tokenExpiresAt` must be cleared so the driver does not attempt to use a (possibly expired or mismatched) cached access token. Test 67 verifies this invariant.

- **Code-review checks (Tests 70–71) are first-class test cases:** Verifying that dead code is actually gone (no live settings.refreshToken references) is as important as verifying new code works. Static checks protect against partial refactors that leave silent bugs.

### Test Tier Distribution for v0.1.3

- **Tier 1 (Core):** Tests 59, 60, 69, 71 — must pass; cover the happy path, the migration regression, reboot persistence, and dead-code removal
- **Tier 2 (Feature):** Tests 61–67, 70 — cover all invalid input branches and the code-review correctness check
- **Tier 3 (Polish/Optional):** Test 68 — concurrent double-click edge case; unlikely in practice but protects against state corruption

- 2026-05-17T04-20-29Z: v0.1.3 SunStat Connect Plus shipped (setRefreshToken command + docs + tests) — tank/link/switch cross-team ship
