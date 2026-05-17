# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — driver suite for Hubitat. Test target: real Gemstone Lights device at 192.168.1.238.
- **Stack:** Hubitat hub (C-7/C-8), Hubitat IDE driver logs, manual device interaction
- **Created:** 2026-05-16

## Learnings

- **Hubitat driver testing is manual, not automated:** Drivers are tested via the device UI (tiles, preferences, command buttons) and the IDE Logs window. There is no Jest / RSpec equivalent. Tests must be written in human-readable prose with clear expected outputs in the logs and device state.

- **Optimistic updates + polling reconciliation is the standard pattern for LAN HTTP drivers:** Update Hubitat state immediately upon command send (without waiting for the device to respond), then verify and correct on the next poll cycle. This keeps the UI responsive even on slow networks. Reconciliation prevents stale state.

- **Hub reboot handling:** `initialize()` is called automatically by Hubitat on hub startup. Use this method to re-register polling schedules and restore any runtime state. Drivers must not assume their schedules survive a reboot.

- **Debug logging auto-off is essential:** Always implement a 30-minute auto-disable for debug logging via `runIn(1800, logsOff)` in the `updated()` method. This prevents accidental log spam if debug mode is left enabled.

- **Network resilience matters:** On connection failure (timeout, DNS error, 5xx), the driver must log a clear warning but NOT crash or hang. After network is restored, the next poll must succeed cleanly. Avoid infinite retry loops that could hammer the device.

- **Uninstall cleanup is mandatory:** `uninstalled()` must deregister all schedules via `unschedule()`. Orphaned schedules can cause memory leaks and phantom CPU usage over time, especially in repos with many drivers.

- **Invalid configuration should not crash the driver:** If a user enters an invalid IP address or preference, the driver should validate and log a warning, but continue running (in a degraded state, unable to poll). The user can then correct the setting.

- **State desync is the main failure mode:** After any command or poll failure, verify that Hubitat and the physical device are in sync. Use the polling reconciliation test to catch cases where the device is changed outside Hubitat (e.g., via the manufacturer's mobile app).

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
