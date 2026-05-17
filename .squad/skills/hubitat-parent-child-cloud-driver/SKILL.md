---
name: "hubitat-parent-child-cloud-driver"
description: "Architecture and lifecycle patterns for a Hubitat parent/child cloud driver where a parent manages auth and discovery, and children hold per-device capability surfaces."
domain: "groovy"
confidence: "high"
source: "earned — SunStat Connect Plus v0.1.0"
---

## Context

Use this skill when a Hubitat driver must manage **multiple devices from one cloud account**. The parent holds credentials and tokens; each child exposes a full capability surface for one physical device and delegates all API calls back through the parent.

## Architecture

```
Parent driver (one Hubitat device)
├── Stores: accessToken, refreshToken, tokenExpiresAt in state
├── Lifecycle: installed/updated/initialize/uninstalled
├── Commands: discoverDevices(), refresh() (polls all children), poll()
├── Creates child devices: addChildDevice("namespace", "Child Driver Name", dni, [...])
├── Pushes state to children: child.parseDeviceState(body)
└── Exposes helper: parent.sendDevicePatch(deviceId, settingsMap)

Child driver (one per physical thermostat/device)
├── Implements: Actuator, Sensor, Thermostat, TemperatureMeasurement, Refresh, Initialize
├── Stores: per-device state (setpoint bounds, floor away value, etc.)
├── Commands delegate to parent: parent.sendDevicePatch(deviceId, settingsMap)
└── State updates received: child.parseDeviceState(body)
```

## Lifecycle Rules

### Parent
- `installed()`: set preference defaults, call `initialize()`
- `updated()`: `unschedule()`, clear auth tokens (force re-auth with new prefs), call `initialize()`
- `initialize()`: if no credentials → log + return; else `schedulePolling()` + `runIn(2, "refresh")`
- `uninstalled()`: `unschedule()` + `getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }`

### Child
- `installed()`: emit safe defaults for all attributes (avoids null dashboard tiles), set `supportedThermostatModes` / `supportedThermostatFanModes`
- `updated()`: re-enable debug-log auto-disable if logEnable is true
- `initialize()`: no-op — parent owns scheduling
- `uninstalled()`: log removal only

## Device Discovery Pattern

```groovy
// 1. GET /User → defaultLocationId (also save userId, measurementScale)
// 2. GET /Location/{id}/Devices → list of devices
// 3. For each device of the right type:
String dni = "mydriver-${deviceId}"
def existing = getChildDevice(dni)
if (!existing) {
    def child = addChildDevice("mads", "My Child Driver", dni, [name: displayName, isComponent: false])
    child.updateDataValue("cloudDeviceId", deviceId)
} else {
    existing.updateDataValue("cloudDeviceId", deviceId)
}
```

Key: `isComponent: false` lets users rename children. Store the cloud device ID as a `DataValue`, NOT in `state`, so the parent can read it via `child.getDataValue("cloudDeviceId")`.

## Child-to-Parent Command Routing

```groovy
// In child driver:
private void sendDevicePatch(Map settingsMap) {
    String deviceId = device.getDataValue("cloudDeviceId")
    parent.sendDevicePatch(deviceId, settingsMap)
}
```

## Parent-to-Child State Push

```groovy
// In parent poll():
getChildDevices()?.each { child ->
    String deviceId = child.getDataValue("cloudDeviceId")
    pollDevice(child, deviceId)
}

// After successful GET:
child.parseDeviceState(responseBody)
```

## Thermostat Capability (heat-only)

```groovy
// In child installed():
sendEvent(name: "supportedThermostatModes",    value: JsonOutput.toJson(["heat", "off"]))
sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["auto"]))

// Required stubs (log warn, no API call):
def cool()          { log.warn "not supported" }
def auto()          { log.warn "not supported" }
def emergencyHeat() { log.warn "use setBoost() instead" }
def fanOn()         { log.warn "no fan on this device" }
def fanAuto()       { log.warn "no fan on this device" }
def fanCirculate()  { log.warn "no fan on this device" }
def setThermostatFanMode(m) { log.warn "no fan on this device" }
def setCoolingSetpoint(t)   { log.warn "heat-only device" }

// thermostatSetpoint mirrors heatingSetpoint for heat-only:
sendEvent(name: "thermostatSetpoint", value: heatSetpoint, unit: location.temperatureScale, ...)
```

## Floor Probe Disconnected Sentinel

```groovy
// Guard before emitting floorTemperature:
Long FLOOR_PROBE_DISCONNECTED_F = 110L
BigDecimal floorF = (apiUnit == "C") ? celsiusToFahrenheit(raw) : raw
if (floorF > FLOOR_PROBE_DISCONNECTED_F) {
    log.warn "${device.displayName}: floor probe disconnected — clearing floorTemperature"
    device.deleteCurrentState("floorTemperature")
} else {
    sendEvent(name: "floorTemperature", value: convertedTemp, unit: location.temperatureScale, ...)
}
```

## Token Bootstrap Pattern

When a cloud API uses a complex initial login flow (OAuth PKCE, etc.) that cannot be done in Hubitat:
1. User runs an external tool to obtain a refresh token.
2. User pastes the refresh token into a `password` type preference field.
3. On `initialize()`, if `settings.refreshToken` is set but `state.refreshToken` is not, lift the value into state and clear the preference (optional — reduces plaintext exposure).
4. Driver handles all subsequent token lifecycle via simple refresh POST.

## Token Refresh (Azure AD B2C variant)

```groovy
// Body as pre-built query string — use requestContentType: "application/x-www-form-urlencoded"
// This IS one of Hubitat's three built-in encoders, so no pre-serialization quirk needed.
String bodyStr = "client_id=${CLIENT_ID}" +
                 "&grant_type=refresh_token" +
                 "&refresh_token=" + URLEncoder.encode(rt, "UTF-8") +
                 "&client_info=1&scope=${SCOPE_ENCODED}"

Map params = [
    uri                : TOKEN_URL,
    requestContentType : "application/x-www-form-urlencoded",
    contentType        : "application/json",
    body               : bodyStr,
    timeout            : safeRequestTimeout()
]
httpPost(params) { resp ->
    // ALWAYS persist the new refresh_token — old one is now invalid (rotation)
    state.accessToken    = resp.data.access_token
    state.refreshToken   = resp.data.refresh_token  // critical!
    state.tokenExpiresAt = resp.data.expires_on as Long
}
```

## Examples

- `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy`
- `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy`

## Anti-Patterns

- Storing cloud credentials in child devices (parent owns auth surface).
- Using `addChildDevice(..., isComponent: true)` — locks the name, confuses users.
- Storing cloudDeviceId in `state` on the child (use `DataValue` — survives state clears).
- Emitting `floorTemperature` when the value is a disconnected-probe sentinel.
- Referencing another `@Field static final` in a `@Field` initializer — always inline literals.
- Forgetting to persist the rotated `refresh_token` after a token refresh call.

## Location-Level vs Device-Level API Concepts

Some cloud APIs expose concepts that span multiple devices (e.g. home/away mode applies to an entire location, not a single thermostat). Pattern:

**Parent:**
```groovy
// State persisted from GET /api/Location response
state.locationId           = safeStr(loc.locationId)
state.locationSupportsAway = loc?.supportsAway == true
state.awayState            = safeInt(loc?.awayState, 0)

// Attributes on the parent device
attribute "awayMode",             "enum", ["home", "away", "unsupported", "unknown"]
attribute "locationSupportsAway", "enum", ["true", "false"]
```

**Helper: `parseLocationState(Map loc)`** — called from both discovery and polling:
```groovy
private void parseLocationState(Map loc) {
    boolean supportsAway = loc?.supportsAway == true
    state.locationSupportsAway = supportsAway
    if (!supportsAway) {
        // emit "unsupported" once; skip poll entirely on this location
        return
    }
    String modeStr = (safeInt(loc?.awayState, 0) == 1) ? "away" : "home"
    // use emitIfChanged to avoid event spam
}
```

**Poll guard:**
```groovy
if (locId && state.locationSupportsAway != false) {
    fetchAndParseLocationState(locId)
}
// Groovy: null != false → true (before first discovery), false != false → false (unsupported)
```

**PATCH + optimistic update:**
```groovy
def setAwayMode(String mode) { setAwayModeInternal(mode, true) }
private void setAwayModeInternal(String mode, boolean retry401) {
    // Guard: locationSupportsAway, locationId, ensureValidToken
    sendEvent(name: "awayMode", value: mode, ...)  // optimistic before HTTP call
    httpMethod("PATCH", buildApiParams("PATCH", "/Location/${locId}/State",
               JsonOutput.toJson([awayState: mode == "away" ? 1 : 0]))) { resp ->
        if (status == 401 && retry401) { /* refresh + retry */ }
        else if (status >= 200 && status < 300) { parseLocationState(parseResponseBody(resp)) }
        else { log.error "... — optimistic state may be stale" }
    }
}
```

## Mirror Attribute Pattern (Parent → Child Read-Only)

When a parent-level attribute should also appear per-device on dashboard tiles, mirror it in the child's `parseDeviceState()`. The parent updates its own attribute first (in `refresh()` before iterating children), so the mirror is always fresh.

```groovy
// At end of parseDeviceStateInternal() in child:
String parentAway = safeStr(parent?.currentValue("awayMode"), "unknown")
emitIfChanged("awayMode", parentAway,
              "${device.displayName} awayMode is ${parentAway}")
```

Notes:
- `parent?.currentValue(...)` — safe-navigation guards against null parent (e.g. standalone test).
- `safeStr(..., "unknown")` — fallback when parent attribute has never been set.
- `emitIfChanged` prevents event spam when the value hasn't changed between polls.
- Declare the attribute in the child's `metadata` with the same enum values as the parent.
- The parent must update its own attribute *before* iterating children in `refresh()`.

## Energy Reporting Pattern (EnergyMeter Capability)

Add `capability "EnergyMeter"` to the child metadata — this gives the built-in `energy` attribute automatically.

For indexed array fields (e.g. today/yesterday from `Daily[]`, current/last month from `Monthly[]`):

```groovy
// Declare custom attributes alongside EnergyMeter's built-in "energy":
attribute "energyYesterday", "number"   // Daily[1]
attribute "energyMonth",     "number"   // Monthly[0]
attribute "energyLastMonth", "number"   // Monthly[1]

// In parseDeviceStateInternal():
def energyBlock = data?.Energy
if (energyBlock == null) {
    debugLog "no Energy data — some firmwares omit this"
} else {
    List dailyList   = energyBlock?.Heat?.Daily   instanceof List ? energyBlock.Heat.Daily   as List : []
    List monthlyList = energyBlock?.Heat?.Monthly instanceof List ? energyBlock.Heat.Monthly as List : []
    if (dailyList.size() >= 1) {
        BigDecimal v = roundEnergy(dailyList[0])
        sendEvent(name: "energy", value: v, unit: "kWh", descriptionText: "... ${v} kWh", type: "digital")
    }
    // repeat for [1] → energyYesterday, monthlyList[0] → energyMonth, [1] → energyLastMonth
}
```

- Always guard with `instanceof List` before indexing — API may return null for the whole block on older firmwares.
- Round to 2 decimal places: `safeBigDecimal(value, 0.0).setScale(2, BigDecimal.ROUND_HALF_UP)` — keeps dashboard tiles readable.
- Emit `unit: "kWh"` on every energy event; include "kWh" in `descriptionText`.

## API Enum Attribute Pattern (on/off/unknown)

For attributes that map an API string enum to a Hubitat enum:

```groovy
// Constraints use the Hubitat-style values, not raw API strings:
attribute "scheduleEnabled", "enum", ["on", "off", "unknown"]

// Parsing:
String apiVal = safeStr(data?.SchedEnable?.Val)
if (apiVal) {
    String v = (apiVal == "On") ? "on" : (apiVal == "Off") ? "off" : "unknown"
    if (v == "unknown") { log.warn "unrecognised value '${apiVal}'" }
    emitIfChanged("scheduleEnabled", v, "${device.displayName} scheduleEnabled is ${v}")
}

// Command (with optimistic update before API call):
def setScheduleEnabled(String enabled) {
    String lower = enabled?.toLowerCase()
    if (lower != "on" && lower != "off") { log.warn "invalid value"; return }
    emitIfChanged("scheduleEnabled", lower, ...)  // optimistic
    parent.sendDevicePatch(deviceId, [SchedEnable: lower == "on" ? "On" : "Off"])
}
```

## Integer-as-Boolean Attribute Pattern (hold/following)

When an API returns an integer that acts as a boolean (0 = off, non-zero = on):

```groovy
attribute "thermostatHold", "enum", ["holding", "following", "unknown"]

def holdRaw = data?.Target?.Hold
if (holdRaw == null) {
    emitIfChanged("thermostatHold", "unknown", ...)
} else {
    String holdStr = (safeInt(holdRaw, 0) == 0) ? "following" : "holding"
    emitIfChanged("thermostatHold", holdStr, ...)
}
```

- Do NOT cast to boolean directly — the non-zero value may encode additional semantics (e.g. hold duration) in a future API revision.

## Setpoint Step-Rounding Pattern

When the device has a configurable setpoint increment (e.g. 1°F or 0.5°C):

```groovy
// Persist from poll:
state.setpointStep = safeBigDecimal(data?.Target?.Steps, 1.0)

// Before clamping in setHeatingSetpoint/setFloorMinTemp:
BigDecimal step    = validStep(state.setpointStep)   // returns >= 1.0, never 0
BigDecimal rounded = (Math.round((temp as BigDecimal) / step) * step).setScale(2, BigDecimal.ROUND_HALF_UP)
BigDecimal clamped = clampSetpoint(rounded)

// Helper:
private BigDecimal validStep(stepState) {
    BigDecimal step = safeBigDecimal(stepState, 1.0)
    return (step > 0) ? step : 1.0
}
```

## Optional Sensor Attribute Pattern (outdoorTemperature)

For sensors that may be absent (most installs lack an outdoor probe):

```groovy
attribute "outdoorTemperature",  "number"
attribute "outdoorSensorStatus", "enum", ["okay", "unavailable", "unknown"]

def outdoorMap = data?.Sensors?.Outdoor
if (outdoorMap == null) {
    emitIfChanged("outdoorSensorStatus", "unavailable", ...)
} else if (safeStr(outdoorMap?.Status) == "Okay" && outdoorMap?.Val != null) {
    BigDecimal temp = convertTemp(safeBigDecimal(outdoorMap.Val, 0.0), apiUnit, hubScale)
    sendEvent(name: "outdoorTemperature", value: temp, unit: hubScale, ...)
    emitIfChanged("outdoorSensorStatus", "okay", ...)
} else {
    emitIfChanged("outdoorSensorStatus", "unavailable", ...)
}
```

- Do NOT emit `outdoorTemperature` when status is not "Okay" — stale values mislead rules engines.
- Status enum uses lowercase Hubitat values (`"okay"`, `"unavailable"`) even though the API returns `"Okay"`.
