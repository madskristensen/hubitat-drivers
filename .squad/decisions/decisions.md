# Decisions Log

**Last updated:** 2026-05-23

---

## Decision: isComponent behavior clarification — v0.3.2 wording corrected

**Date:** 2026-05-23  
**Author:** Tank  
**Status:** Resolved — no code change, wording fix only

### What happened

v0.3.2 set `isComponent: true` on the Climate Advisor child device and shipped with changelog text claiming it produced "cleaner Devices list" and that the device "nests under app" (implying hidden from Devices). Mads then reported the device appeared in the Devices list after recreating it, and questioned whether the change worked as described.

### Research findings (primary sources)

**Official Hubitat App Object documentation (`docs2.hubitat.com/en/developer/app/app-object`) — fetched live 2026-05-23:**

> `boolean isComponent` — true or false, **if true, device will still show up in device list but will not be able to be deleted or edited in the UI.** If false, device can be modified/deleted on the UI.

This wording has been identical since the feature was introduced in 2018 (confirmed from original Hubitat staff post by `chuck.schwer` in `community.hubitat.com/t/composite-devices-parent-child-devices/1925`).

**Official Parent-Child Drivers documentation (`docs2.hubitat.com/en/developer/driver/parent-child-drivers`) — fetched live:**

> Child devices of an app "will have a 'Parent app' listed in the 'Device Details' table on the 'Device Info' tab of their device detail page."

App-parented children appear in the Devices list (not indented, not hidden). Only device-parented children get visual indentation.

**Groups and Scenes confirmation:** G&S group/scene devices also appear in the main Devices list and are fully accessible in dashboards, Rule Machine, SharpTools, etc. The "nesting" visible in the Apps section is the app's own management UI — not a Devices-list-level feature. G&S does not hide its children from the Devices list; no third-party app can.

### What isComponent: true actually provides

| Behavior | isComponent: false | isComponent: true |
|---|---|---|
| Appears in main Devices list | yes | yes (unchanged) |
| Appears in App Details (Apps section) | yes | yes (unchanged) |
| User can delete via Devices UI | yes | blocked ✅ |
| User can change driver via Devices UI | yes | blocked ✅ |
| "Parent app" shown in Device Info tab | yes | yes (unchanged) |
| Auto-removed on app uninstall | yes | yes (unchanged) |

**The v0.3.2 change is real and useful** — it prevents accidental deletion/driver-change. The changelog claim "cleaner Devices list" was simply wrong.

### Decision

- `isComponent: true` kept (prevents accidental deletion — genuinely useful)
- Mads accepted Option 1: accept platform behavior, no architecture change
- There is no Hubitat SDK mechanism available to third-party apps to hide an app-created child device from the main Devices list — this is a hard platform limitation
- Changelog entries in app and driver headers corrected to accurate wording
- README Architecture section corrected to accurate wording
- No version bump; wording fix folded into a separate clarification commit

---

## Decision: Climate Advisor v0.3.3 — evaluateFreeCooling

**Date:** 2026-05-23  
**Author:** Tank  
**Version:** 0.3.3

### The Gap (diagnosed in behavioral trace)

Mads reported a real-world scenario that produced zero notifications despite an actionable situation:

- Outdoor: 65°F, Indoor: 75°F (= cooling setpoint), windows CLOSED, trend unknown

**Why nothing fired:**

| Evaluator | Exit reason |
|---|---|
| `evaluateCoolingPreAlert` | `windowGatePasses = false` (contacts configured, all closed) — exits line 1 |
| `evaluateHeatingPreAlert` | Same gate — exits line 1 |
| `evaluateCoolBreach` | `outdoorTemp > indoorTemp` required (65 > 75 = false) — wrong direction |
| `evaluateComfortOpen` | `outdoorTemp >= lowerBound` required (65 >= 70 = false) — outdoor below comfort band |

The root cause: `evaluateCoolBreach` fires when outdoor is **hotter** than indoor (close-windows-now). `evaluateComfortOpen` fires when outdoor is in the mid-comfort band. Neither covers outdoor **cooler** than indoor when indoor is at the setpoint — which is the ideal free-cooling condition.

### The Fix

New evaluator `evaluateFreeCooling` added after `evaluateComfortOpen` in `climate-advisor-app.groovy`. Fires INFO (severity 1) when ALL:

1. `outdoorTemp < indoorTemp` — outdoor is cooler (right direction for ventilation)
2. `indoorTemp >= (coolSP - coolingPreAlertOffset)` — indoor at/near setpoint (reuses existing offset, default 3°F); AC would otherwise run
3. Thermostat in `cool` or `auto` mode
4. Contacts configured AND all closed (something to open; not already open)
5. Not raining
6. AQI below warn threshold
7. Outdoor trend NOT `rising` (don't suggest opening if it'll overshoot)

Message format: `"Zone 1 outdoor 65°F is 10.0°F cooler than indoor 75°F — consider opening windows for free cooling (cool setpoint 75°F)"`

### Non-overlap proof

- **vs `evaluateCoolBreach`**: that requires `outdoorTemp > indoorTemp` — mutually exclusive with condition 1 above
- **vs `evaluateComfortOpen`**: that requires outdoor inside `[heatSP+2, coolSP-2]`; free cooling fires when outdoor is *below* that band
- **vs `evaluateCoolingPreAlert`**: that requires `windowGatePasses = true` (contacts open or absent) AND outdoor rising AND outdoor hotter — three conditions opposite to this scenario

### Behavioural change for Mads's scenario

Before v0.3.3: no notification. After v0.3.3: INFO message "Zone 1 outdoor 65°F is 10.0°F cooler than indoor 75°F — consider opening windows for free cooling (cool setpoint 75°F)". Severity = 1; `contact` attribute stays `closed` (INFO < 1 threshold for open); `severityText` = "info".

### Files changed

- `apps/climate-advisor/climate-advisor-app.groovy` — new `evaluateFreeCooling` method, wire-up in `evaluateZone`, version 0.3.3, changelog
- `drivers/climate-advisor/climate-advisor-device.groovy` — version 0.3.3, changelog
- `apps/climate-advisor/packageManifest.json` — version 0.3.3
- `packageManifest.json` (root) — Climate Advisor app + driver entries 0.3.3
- `apps/climate-advisor/README.md` — free cooling row added to alert table
