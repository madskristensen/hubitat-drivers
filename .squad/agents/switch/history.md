# Switch — QA / Testing Engineer

QA and testing specialist for Hubitat drivers. Focuses on real-device validation, edge-case discovery, and test plan creation for Tuya/LAN devices.

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

## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.
- 2026-05-18: Queued for validation: Touchstone v0.1.18 persistent socket (Tests 34–37) + Gemstone v0.4.10 multi-zones (Tests 19–22) + Touchstone v0.1.19 child lock (Test 38) + Touchstone v0.1.20 discovery (Test 39) + HPM bundle v1.0.0 (multi-driver bundle install). Five validation areas pending hardware.
