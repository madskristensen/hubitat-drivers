# Switch — QA / Testing Engineer

QA and testing specialist for Hubitat drivers. Focuses on real-device validation, edge-case discovery, and test plan creation for Tuya/LAN devices. Driver-opportunity shortlist + fit rubric now available (Cypher + Trinity, 2026-05-18) — standing policy for future candidate evaluation.

---

## Current Session (2026-05-17)

### 2026-05-17T15:50:06Z — Touchstone TESTING.md Created (v0.1.5 + v0.1.6 coverage)

**File created:** drivers/touchstone-fireplace/TESTING.md — 33 tests across 9 test areas + validation summary. Covers v0.1.5 baseline and v0.1.6 new commands (setFlameSpeed/setLogBrightness).

**Learnings — Tuya/LAN driver TESTING.md pattern:**

1. Pre-flight checklist mandatory (close app, ping device, verify key, confirm power) — prevents >80% of false failures
2. Section order: Pre-flight → Lifecycle → Happy-path → Power-on defaults → Settings edge cases → Recovery → Discovery → Parsed-not-commanded DPs → Validation summary
3. Mark hardware unknowns explicitly with [verify on hardware] inline in Expected sections
4. Tank coordination flag for new commands (e.g., "NEW IN v0.1.6 — verify in v0.1.6" with fallback instructions)
5. Validation criteria summary checklist at end for community beta testers
6. Retry backoff is test-observable — Tuya scheduleRetry([5,15,30]) delays can be validated by log timestamps

### 2026-05-17T15:41:32Z — Cross-Driver Reliability Analysis (Mads request)

**Anti-patterns found across SunStat, Gemstone, Touchstone:**

1. Optimistic events without rollback — if sending optimistic event, MUST have reconciliation path
2. In-flight flag leaks on hub reboot — any *InFlight in state MUST clear in initialize()
3. Unbounded retry loops — cap retries to fixed N, then halt and wait for next poll
4. updated() clearing too much state — only clear when affected preference actually changed
5. Stub commands reachable from Rule Machine — implement or hide; minimum add [NOT YET IMPLEMENTED] description
6. Missing TESTING.md for LAN/protocol drivers — LAN drivers have MORE failure modes, higher priority than cloud
7. Proactive token refresh not rescheduled after hub reboot — initialize() should re-arm maintenance tasks based on current state

---

### 2026-05-18T01:41:11Z — New Test Areas Queued for Hardware Validation

**Two new test suites awaiting your hardware validation:**

1. **Touchstone v0.1.18 persistent socket** — Tests 34–37 (TESTING.md)
   - Test 34: Socket persistence (confirm `socketState = "open"` after 5+ min idle)
   - Test 35: Heartbeat send (confirm no "Heartbeat failed" errors; device stays connected)
   - Test 36: Reconnect backoff (simulate loss; verify 5s → 30s → 60s → 300s progression)
   - Test 37: Push frames (press remote button; verify dashboard updates within 1 s, not waiting for 5-min poll)
   - **Risks to watch:** Single TCP slot (Tuya enforces one connection); push/response ambiguity at protocol level; heartbeat format must be zero-byte payload
   - **Pass criteria:** All four tests confirm or fail with clear symptom (e.g., "heartbeat timeout after X minutes")

2. **Gemstone v0.4.10 multi-controller zones** — Tests 19–22 (TESTING.md)
   - Test 19: Multiple controllers discovered (confirm `devices.size() > 1` from account)
   - Test 20: Controller binding (create two Hubitat devices; set `controllerName` to "Front of House" and "Eaves"; verify each controls correct zone)
   - Test 21: Graceful fallback (set `controllerName = "Nonexistent"`; verify warning logs but driver continues on first device)
   - Test 22: Independent operation (toggle lights on two separate Hubitat devices; confirm each controls correct physical controller)
   - **Risks to watch:** Controller naming stability (are names unique?); device group schema unconfirmed; per-zone effect catalog uniformity
   - **Pass criteria:** All four tests confirm, including graceful fallback on no-match

**Expectation:** Run both test suites on real hardware after Tank's commits land (Touchstone 67f905b, Gemstone e35b666). Report findings to team channel.

---

### 2026-05-18T02:42:00Z — Three New Driver Updates Queued for Hardware Validation

**Three new test areas pending your validation:**

1. **Touchstone v0.1.19 — Child Lock (DP 108)**
   - **Test 38:** setChildLock command
   - Action: Press `setChildLock on` from device page; verify physical buttons on fireplace are disabled
   - Action: Press `setChildLock off`; verify buttons re-enable
   - Expected: `childLock` attribute reflects on/off state in real time (push frame or next poll)
   - **Risk:** None identified; simple boolean DP write
   - **Pass criteria:** Buttons disable/enable as commanded; attribute state correct
   - **Commit:** 3a59f04

2. **Touchstone v0.1.20 — Active TCP Discovery (DHCP-Renewal Recovery)**
   - **Test 39:** discover command
   - **Pre-condition:** Fireplace DHCP IP known (e.g., 192.168.1.47)
   - Action: Simulate DHCP renewal: Unplug fireplace for 5+ seconds, plug in. Device gets new IP (e.g., 192.168.1.100)
   - Expected: Driver loses connection (logs "Cannot connect to Touchstone fireplace at 192.168.1.47")
   - Action: Press `discover` button from device page
   - Expected: Driver scans /24 subnet; logs "Tuya autodiscovery: found device at 192.168.1.100"; device `networkAddress` attribute updates to new IP
   - Expected: All commands resume working at new IP
   - **Risk:** Hub rate-limiting on rapid TCP connects; gwId match must work on v3.3 protocol
   - **Pass criteria:** Discovery succeeds within 2 min; device recovers on new IP
   - **Commit:** ffbfd08

3. **HPM Bundle v1.0.0 — Multi-Driver Bundle Installation**
   - **Test:** Install via bundle URL
   - Action: Open Hubitat Package Manager → "Install from URL"
   - Action: Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/packageManifest.json`
   - Expected: HPM shows checklist of 4 drivers (Touchstone, Gemstone, SunStat parent, SunStat child)
   - Action: Select all 4 drivers
   - Expected: HPM installs all four `.groovy` files; no errors
   - **Risk (Cypher-4 flagged):** Unknown HPM behavior if user already installed drivers via per-driver URLs. Test both scenarios:
     - Scenario A: Fresh install via bundle only → should work cleanly
     - Scenario B: Install Touchstone via per-driver URL first; then install bundle → does HPM show one update or two? (This is the unknown edge case)
   - **Pass criteria:** Both scenarios install without duplicates or conflicts visible in HPM UI
   - **Commit:** a0e695d

**Expectation:** Run all three tests on real hardware after commits land. Report findings (including HPM edge case) to team.

---

### 2026-05-18T03:18:12Z — HealthCheck + lastActivity Health Monitoring Shipped

**Four new test areas for hardware validation:**

1. **Touchstone v0.1.21 — HealthCheck capability + ping() command**
   - **Test 40:** HealthCheck probe — `ping()` returns online within 2s on working device
     - Pre-condition: Fireplace powered on, connected to network, Touchstone driver initialized
     - Action: Press `ping` command from Hubitat device page (or call via Rule Machine)
     - Expected: Dashboard shows `healthStatus = "online"` within 2 seconds
     - Expected: Driver logs show "ping received" or equivalent success indicator
     - **Risk:** 5-second timeout must fire if device goes offline during ping; watch for false "offline" on transient network blip
     - **Pass criteria:** Ping resolves to online within 2s; timeout works if device is actually unreachable
     - **Commit:** 6ab7ac3

   - **Test 41:** HealthCheck offline detection — `healthStatus = "offline"` after device unplugged
     - Pre-condition: Touchstone driver running with persistent socket, healthStatus currently online
     - Action: Unplug fireplace from power for 30+ seconds
     - Expected: Dashboard does NOT immediately flip to offline (waits for heartbeat failure + reconnect backoff cycle)
     - Expected: After >= 2 failed reconnect attempts (~35 seconds), `healthStatus = "offline"`
     - Expected: Logs show "Reconnect attempt 1", "Reconnect attempt 2", then offline transition
     - **Risk:** Timing is dependent on heartbeat interval (10s) and reconnect backoff (5s, 30s, 60s) — may take 60+ seconds total
     - **Pass criteria:** Offline state eventually reached after hardware disconnection; no premature flips

2. **Gemstone v0.4.11 — lastActivity attribute on cloud REST API calls**
   - **Test:** lastActivity timestamp updates on every successful API call
     - Pre-condition: Gemstone driver installed, authenticated to cloud, at least one light group discovered
     - Action: Observe initial `lastActivity` attribute (ISO 8601 timestamp)
     - Action: Refresh the driver (from device page or Rule Machine)
     - Expected: `lastActivity` timestamp updated to current time
     - Action: Send a command (e.g., `on` to a light group)
     - Expected: `lastActivity` timestamp updated again
     - **Risk:** Cloud may be very fast or slow; verify timestamp is actually changing, not stuck
     - **Pass criteria:** lastActivity advances on each refresh + command cycle

3. **SunStat v0.1.7 — lastActivity attribute on parent + child cascade**
   - **Test:** Parent and child lastActivity updates on poll cycle
     - Pre-condition: SunStat parent + child devices installed, authenticated to Watts, thermostat paired
     - Action: Observe initial `lastActivity` on both parent and child devices
     - Action: Trigger a poll cycle (from parent device page)
     - Expected: Both parent `lastActivity` and child `lastActivity` timestamps update
     - Action: Send a command to child (e.g., `setHeatingSetpoint`)
     - Expected: Child `lastActivity` updates; parent may or may not update (depending on response path)
     - **Risk:** Parent-child cascade timing; verify child receives update from parent callback
     - **Pass criteria:** Both parent and child show recent timestamps after poll; no stale attributes

---

## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.
- 2026-05-18: Queued for validation: Touchstone v0.1.18 persistent socket (Tests 34–37) + Gemstone v0.4.10 multi-zones (Tests 19–22) + Touchstone v0.1.19 child lock (Test 38) + Touchstone v0.1.20 discovery (Test 39) + HPM bundle v1.0.0 (multi-driver bundle install). Five validation areas pending hardware.
- 2026-05-18: New health monitoring validation areas: Touchstone v0.1.21 HealthCheck ping (Tests 40–41) + Gemstone v0.4.11 lastActivity (Test 42) + SunStat v0.1.7 lastActivity cascade (Test 43). Eight total validation areas queued.
- 2026-05-18: ⚠️ Alert — `java.lang.System.arraycopy` is sandbox-blocked on Hubitat (same as CRC32 import block). Never use in driver code; use primitive `for` loops instead. Touchstone v0.1.30 hotfix applied. See `.squad/decisions/decisions.md::tank-touchstone-v130-arraycopy-fix`.

## Team Updates

### Hubitat Write-Only Property Gotcha + HubAction Constructor Table (Tank-3, 2026-05-18)

**Key Lessons from Daikin v0.1.1 hotfix:**

1. **Groovy JavaBean Naming + Scheduler Method Shadowing**  
   Custom command setX(x) creates a write-only property x on the driver object. If the code also calls the platform's x() scheduler method (e.g., schedule(cron, method)), Groovy's dynamic dispatch resolves the name as the write-only property instead of the method → runtime error ("Cannot read write-only property"). Workaround: use unEvery* idiomatic methods instead of calling schedule by name. Affected drivers: any Thermostat capability driver that calls schedule(cron, method) in addition to providing the setSchedule() stub.

2. **HubAction Constructor Overloads**  
   Valid forms for LAN HTTP: HubAction(String), HubAction(String, Protocol), HubAction(String, Protocol, String dni), HubAction(String, Protocol, String dni, Map options), HubAction(Map), HubAction(Map, Protocol) ← **preferred for GET**. Invalid form: HubAction(Map, Protocol, Map) does NOT exist. Callback must be inside the params Map when using 2-arg form.

3. **Test on First Install Before Shipping**  
   Both bugs were immediately visible on first Save Preferences after install. Smoke-test drivers on hub before tagging v1.0 releases.

### Daikin v0.1.4 Roadmap Complete (Tank-6, 2026-05-18)

**Daikin WiFi driver v0.1.4 shipped; v0.1.0+ roadmap CLOSED.** Tank-6 bundled final three capability items (commit 1dd21fe):
1. **Econo/Powerful mode** — setSpecialMode + specialMode ENUM, polled every fast-refresh
2. **get_model_info cache** — Called in initialize(); caches name, firmware, humidity/swing flags for diagnostics
3. **Event hygiene audit** — All five checks passed (no anti-patterns detected)

Your cross-driver reliability analysis (8 anti-patterns) directly informed this v0.1.4 audit. Hardware verification pending on Mads's BRP069B unit.

**2026-05-18 Team Update:** MyQ feasibility research completed; verdict = build ratgdo ESPHome HTTP driver (local, no cloud). Cypher confirmed ratgdo firmware actively maintained; Trinity sketched architecture. Awaiting Mads hardware decision. See .squad/decisions.md for full report.

**2026-05-18 Team Update (Scribe merge):** Rainbird verdict = install existing community driver; no new code to test this round.
## Team Update — PurpleAir Cloud Driver (2026-05-18)

**From:** Cypher audit (2026-05-18T23:18:00Z)

PurpleAir cloud driver is the next likely build target. No hardware needed for testing — use any public sensor ID from map.purpleair.com.

---

## Team Update — Honeywell T6 Pro Fork (2026-05-18)

**From:** Trinity audit + Tank fork verdict (2026-05-18T23:45:00Z)

**When Honeywell T6 Pro fork ships to `drivers/honeywell-t6-pro/`, validate:**

1. **Fan-state operating-mode detection** — Currently broken. `currentValue()` method reference (missing attribute name argument) in `zwaveEvent(ThermostatFanStateReport)` at line ~210. After fix, fan state changes should correctly trigger `thermostatOperatingStateGet` poll. Verify: change fan mode on physical thermostat; check `thermostatOperatingState` attribute updates within 5 seconds.

2. **Info-level log events** — txtEnable preference was never declared; all `if (txtEnable) log.info` calls are permanently silenced. After fix (add preference + verify calls), system events should log: battery %, AC mains events, Z-Wave associations. Enable `txtEnable` in preferences; check logs during poll cycle + mode change.

3. **syncClock scheduler accumulation** — configure() doesn't call unschedule() before runEvery3Hours("syncClock"), causing zombie schedulers to pile up on repeated configure invocations (common during setup). After fix, manual configure() from device page should not spawn new scheduler instances. Test: open device page; click `Configure` 3 times in quick succession; check if syncClock fires exactly once every 3 hours (not 3×).

**Affected devices:** Mads's Downstairs thermostat runs this driver today. Honeywell T6 Pro is the thermostat model.

---

