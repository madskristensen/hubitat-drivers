# Skill: Multi-Controller Binding via Named Preference

**Status:** Proven — shipped in Gemstone Lights v0.4.10  
**Applicability:** Any Hubitat cloud driver where one API account may return multiple independently addressable devices (controllers, zones, thermostats, locks, etc.)

---

## Problem

A single cloud account may expose multiple physical devices (zones/controllers). The driver currently picks the first one. The user wants to target a specific device by name — without parent/child architecture, without exposing opaque UUIDs to the user.

---

## Pattern

Add a single optional `text` preference: `controllerName`.

```groovy
input name: "controllerName", type: "text",
    title: "Controller name (zone)",
    description: "Name of the device to bind to (case-insensitive). Leave blank to use the first device found. Create one Hubitat device per physical controller for multi-zone support.",
    required: false
```

In the discovery response handler, after receiving the device list:

```groovy
// Store all names for diagnostic reference
List allNames = devices.collect { safeString(it?.name) }.findAll { it }
state.availableControllers = allNames.sort().join(", ")

// Resolve the target device
String wanted = settings.controllerName?.trim()?.toLowerCase() ?: ""
Map selectedDevice

if (wanted) {
    selectedDevice = devices.find { safeString(it?.name).trim().toLowerCase() == wanted } as Map
    if (!selectedDevice) {
        log.warn "[Driver] No device named '${settings.controllerName}' found. Available: ${state.availableControllers}. Falling back to first device."
        selectedDevice = devices[0] as Map
    }
} else {
    if (devices.size() > 1) {
        log.warn "[Driver] Multiple devices found. Using first. Set 'Controller name' preference to pick a specific one."
    }
    selectedDevice = devices[0] as Map
}

state.deviceId   = safeString(selectedDevice?.id)
state.deviceName = safeString(selectedDevice?.name, device.displayName)
```

---

## Key Design Rules

1. **Blank = first-found (always backward compatible).** The `?: ""` normalization + `if (wanted)` branch ensures blank/null never changes behavior.
2. **Sanitize both sides:** `settings.controllerName?.trim()?.toLowerCase()` AND `safeString(it?.name).trim().toLowerCase()`. Protects against leading/trailing spaces from mobile apps or user input.
3. **Graceful degradation on no-match:** log.warn with available names, fall back to `devices[0]`. Never leave `state.deviceId` unbound.
4. **Suppress "multiple devices" warning when `controllerName` is set.** Multiple is expected; the warning is only useful when the user hasn't configured multi-device yet.
5. **`state.availableControllers`** — always set after discovery, sorted, comma-joined. Lets users find correct spelling from the **State Variables** tab without looking at the mobile app. Don't make this a Hubitat attribute — `state` is enough.
6. **One Hubitat device per physical controller.** Each Hubitat device gets its own auth token cache, its own polling schedule, and its own `controllerName`. No coordination needed between instances.

---

## User Workflow (multi-zone)

1. Install driver on first Hubitat device, enter credentials, leave `controllerName` blank.
2. After auth, check **State Variables → availableControllers** to see all controller names.
3. Set `controllerName` to the desired name (e.g., `Eaves`).
4. For each additional zone: create a new Hubitat device with the same driver and credentials, set `controllerName` to a different name.

---

## Applicability to Other Drivers

Use this pattern whenever:
- Cloud discovery returns a list of devices (not just one)
- Devices are addressable by `id` (UUID or similar) + human `name`
- Users may have 2–~10 devices (not hundreds — for large counts, parent/child is better)
- Backward compatibility to single-device behavior is required

Examples: multi-zone thermostats, multi-door lock accounts, multi-camera accounts, multi-room audio zones.

---

## Anti-patterns to Avoid

- Exposing raw UUIDs as a preference (unusable UX)
- True parent/child for small device counts (over-engineered)
- Failing hard on no-match (leaves driver non-functional; warn + fallback is better)
- Indexing by position (fragile if cloud adds/reorders devices)
