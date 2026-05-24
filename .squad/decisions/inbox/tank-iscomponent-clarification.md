# Decision: isComponent behavior clarification — v0.3.2 wording corrected

**Date:** 2026-05-23  
**Author:** Tank  
**Status:** Resolved — no code change, wording fix only

## What happened

v0.3.2 set `isComponent: true` on the Climate Advisor child device and shipped with changelog text claiming it produced "cleaner Devices list" and that the device "nests under app" (implying hidden from Devices). Mads then reported the device appeared in the Devices list after recreating it, and questioned whether the change worked as described.

## Research findings (primary sources)

**Official Hubitat App Object documentation (`docs2.hubitat.com/en/developer/app/app-object`) — fetched live 2026-05-23:**

> `boolean isComponent` — true or false, **if true, device will still show up in device list but will not be able to be deleted or edited in the UI.** If false, device can be modified/deleted on the UI.

This wording has been identical since the feature was introduced in 2018 (confirmed from original Hubitat staff post by `chuck.schwer` in `community.hubitat.com/t/composite-devices-parent-child-devices/1925`).

**Official Parent-Child Drivers documentation (`docs2.hubitat.com/en/developer/driver/parent-child-drivers`) — fetched live:**

> Child devices of an app "will have a 'Parent app' listed in the 'Device Details' table on the 'Device Info' tab of their device detail page."

App-parented children appear in the Devices list (not indented, not hidden). Only device-parented children get visual indentation.

**Groups and Scenes confirmation:** G&S group/scene devices also appear in the main Devices list and are fully accessible in dashboards, Rule Machine, SharpTools, etc. The "nesting" visible in the Apps section is the app's own management UI — not a Devices-list-level feature. G&S does not hide its children from the Devices list; no third-party app can.

## What isComponent: true actually provides

| Behavior | isComponent: false | isComponent: true |
|---|---|---|
| Appears in main Devices list | yes | yes (unchanged) |
| Appears in App Details (Apps section) | yes | yes (unchanged) |
| User can delete via Devices UI | yes | blocked ✅ |
| User can change driver via Devices UI | yes | blocked ✅ |
| "Parent app" shown in Device Info tab | yes | yes (unchanged) |
| Auto-removed on app uninstall | yes | yes (unchanged) |

**The v0.3.2 change is real and useful** — it prevents accidental deletion/driver-change. The changelog claim "cleaner Devices list" was simply wrong.

## Decision

- `isComponent: true` kept (prevents accidental deletion — genuinely useful)
- Mads accepted Option 1: accept platform behavior, no architecture change
- There is no Hubitat SDK mechanism available to third-party apps to hide an app-created child device from the main Devices list — this is a hard platform limitation
- Changelog entries in app and driver headers corrected to accurate wording
- README Architecture section corrected to accurate wording
- No version bump; wording fix folded into a separate clarification commit
