# Minoston Smart Plug 2-Channel (MP24Z)

A Hubitat driver for the Minoston/Eva Logik dual-outlet Z-Wave outdoor smart plug (MP24Z family variants).

> **Fork of:** `sky-nie` / Evalogik "Smart Plug 2-Channel" driver  
> **Upstream status:** Not accepting PRs  
> **Maintained by:** Mads Kristensen

---

## Why this fork exists

The built-in generic driver can leave endpoint state inconsistent in some parent/child scenarios.  
This fork is focused on reliability for dual-endpoint control, especially parent off/on behavior.

## Key reliability improvements

1. **Parent on/off always targets both endpoints explicitly**, with optimistic switch events; use Refresh to request confirmed endpoint states.
2. **Proper component child devices** (`ep1`, `ep2`) are created and kept label-synced.
3. **Aggregate parent switch state** is recomputed from child endpoint states.
4. **Broken supervision references removed** (undefined symbols that could cause runtime failures).
5. **Event emission cleaned up** to Hubitat-style `sendEvent` usage.
6. **Logging controls added** (`txtEnable`, `logEnable`) with auto-debug timeout.
7. **Capability cleanup** removes unsupported `PushableButton`.

---

## Install

### HPM (Hubitat Package Manager)

1. Open HPM → **Install** → **Search by URL**
2. Paste:
   `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/minoston-mp24z/packageManifest.json`
3. Follow prompts.

### Manual

1. In Hubitat, go to **Drivers Code** → **New Driver** → **Import**
2. Paste:
   `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/minoston-mp24z/minoston-mp24z.groovy`
3. Save, then switch your existing device type to:
   **Minoston Smart Plug 2-Channel (MP24Z)**

---

## Notes

- The driver creates two child component switches: channel 1 and channel 2.
- Parent off/on controls both outlets and publishes optimistic endpoint state. Refresh polls both outlets; commands do not include a verification/retry loop.
- Legacy child DNIs ending in `-1`, `-01`, or `-ep1` (and channel-2 equivalents) receive the same reports and aggregate-state handling. Initialization creates only missing channels.
- Association Add/Remove commands reconcile immediately. If current membership is unknown, changes wait for complete association discovery; unrelated nodes and the hub lifeline are preserved. A refused change does not trigger an unbounded report/write loop.

---

## Changelog

| Version | Date       | Notes |
|---------|------------|-------|
| 1.0.8   | 2026-10-25 | Resolve legacy children for commands, reports, and aggregation; create missing channels without duplicates; reconcile association changes immediately, including multipart discovery and normalized node IDs. |
| 1.0.7   | 2026-05-23 | Treat nonzero BasicReport/SwitchBinaryReport values as on, excluding unknown BasicReport value 0xFE. |
| 1.0.6   | 2026-05-23 | Publish optimistic parent/child switch events and remove the in-driver verification/retry loop. |
| 1.0.5   | 2026-05-19 | Reduce command/event churn: light-first child on/off path, endpoint-targeted child verify retry, duplicate child command suppression during in-flight verification, and throttled `lastActivity` updates. |
| 1.0.4   | 2026-05-19 | Fix Hubitat compile compatibility by removing script-scope `private`/`static` method modifiers. |
| 1.0.3   | 2026-05-19 | Accept legacy child DNI formats when resolving endpoint number (supports `-ep1`, `-1`, and `-01`). |
| 1.0.2   | 2026-05-19 | Strengthen child endpoint on/off reliability by sending set/get twice per child command and forcing a quick parent refresh pass to reconcile endpoint state. |
| 1.0.1   | 2026-05-19 | Fix child component command dispatch so child on/off/refresh actively sends Z-Wave commands. Add parent endpoint verification + targeted retry when one outlet misses a parent on/off command. |
| 1.0.0   | 2026-05-19 | Initial Mads-maintained fork. Reliability hardening for parent/child endpoint consistency, supervision cleanup, event model cleanup, and Hubitat capability/logging best-practice alignment. |

## Reliability validation

Local Groovy regression tests load this production source with Hubitat doubles and cover legacy/partial child layouts, parent aggregation, immediate associations, and multipart discovery. Radio delivery and device association behavior still require hardware validation. Test entry point: `tests\device-drivers\local-reliability\ReliabilityTest.groovy` (run from the repository root).

