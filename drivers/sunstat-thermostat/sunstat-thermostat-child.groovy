/**
 * SunStat Connect Plus — Child Driver (Thermostat)
 * Author:  Mads Kristensen
 * Version: 0.1.2
 * License: MIT
 *
 * Per-thermostat capability surface for the Watts® Home SunStat Connect Plus
 * electric floor heating thermostat. All API calls are delegated to the parent
 * driver (SunStat Connect Plus) via parent.sendDevicePatch(...).
 *
 * Changelog:
 *   0.1.2 — 2026-05-16 — Energy reporting, schedule toggle, hold attribute, outdoor temperature, setpoint stepping, floor bounds clamping
 *   0.1.1 — 2026-05-16 — Mirror parent awayMode attribute; bump version
 *   0.1.0 — 2026-05-16 — Initial release
 */

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

@Field static final String DRIVER_VERSION                    = "0.1.2"
@Field static final Long   FLOOR_PROBE_DISCONNECTED_F        = 110L
@Field static final Long   FLOOR_PROBE_DISCONNECTED_C        = 43L

metadata {
    definition(
        name:      "SunStat Connect Plus Thermostat",
        namespace: "mads",
        author:    "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/sunstat-thermostat-child.groovy"
    ) {
        capability "Actuator"
        capability "Sensor"
        capability "Thermostat"
        capability "TemperatureMeasurement"
        capability "EnergyMeter"
        capability "Refresh"
        capability "Initialize"

        command "setBoost",           [[name: "minutes", type: "NUMBER", description: "Boost duration in minutes (1-120)"]]
        command "cancelBoost"
        command "setFloorMinTemp",    [[name: "temp", type: "NUMBER", description: "Floor minimum warmth temperature"]]
        command "setScheduleEnabled", [[name: "enabled", type: "ENUM", constraints: ["on", "off"]]]

        attribute "floorTemperature",    "number"
        attribute "boostActive",         "enum",   ["true", "false"]
        attribute "boostUntil",          "string"
        attribute "deviceOnline",        "enum",   ["true", "false"]
        attribute "scheduleEnabled",     "enum",   ["on", "off", "unknown"]
        attribute "awayMode",            "enum",   ["home", "away", "unsupported", "unknown"]
        attribute "thermostatHold",      "enum",   ["holding", "following", "unknown"]
        attribute "outdoorTemperature",  "number"
        attribute "outdoorSensorStatus", "enum",   ["okay", "unavailable", "unknown"]
        attribute "setpointStep",        "number"
        attribute "energyYesterday",     "number"
        attribute "energyMonth",         "number"
        attribute "energyLastMonth",     "number"
    }

    preferences {
        input name: "logEnable", type: "bool",
              title: "Enable debug logging (auto-off after 30 minutes)",
              defaultValue: false
        input name: "txtEnable", type: "bool",
              title: "Enable descriptionText (info) logging",
              defaultValue: true
    }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    log.info "SunStat Connect Plus Thermostat v" + DRIVER_VERSION + " installed: ${device.displayName}"
    device.updateSetting("logEnable", [value: false, type: "bool"])
    device.updateSetting("txtEnable", [value: true,  type: "bool"])

    // Safe defaults so the Thermostat tile shows something sensible before first poll
    sendEvt("thermostatMode",            "off",    "off")
    sendEvt("thermostatOperatingState",  "idle",   "idle")
    sendEvt("heatingSetpoint",           68,       "68 °")
    sendEvt("coolingSetpoint",           100,      "100 °")
    sendEvt("thermostatSetpoint",        68,       "68 °")
    sendEvt("thermostatFanMode",         "auto",   "auto")
    sendEvt("boostActive",               "false",  "false")
    sendEvt("boostUntil",                "",       "")
    sendEvt("scheduleEnabled",           "off",    "off")
    sendEvt("deviceOnline",              "false",  "false")

    // Constrain the supported mode lists so dashboard hides irrelevant modes
    sendEvent(name: "supportedThermostatModes",
              value: JsonOutput.toJson(["heat", "off"]),
              descriptionText: "${device.displayName} supported modes set")
    sendEvent(name: "supportedThermostatFanModes",
              value: JsonOutput.toJson(["auto"]),
              descriptionText: "${device.displayName} supported fan modes set")
}

def updated() {
    log.info "SunStat Connect Plus Thermostat v" + DRIVER_VERSION + " preferences updated: ${device.displayName}"
    if (settings.logEnable) {
        runIn(1800, "logsOff")
        debugLog "Debug logging enabled; will auto-disable in 30 minutes"
    }
}

def initialize() {
    // Child has no independent schedule; parent owns polling
    debugLog "initialize() — child has no independent schedule"
}

def uninstalled() {
    log.info "SunStat Connect Plus Thermostat v" + DRIVER_VERSION + " removed: ${device.displayName}"
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "SunStat Connect Plus Thermostat: debug logging auto-disabled after 30 minutes"
}

// ---------------------------------------------------------------------------
// Command — Refresh
// ---------------------------------------------------------------------------

def refresh() {
    debugLog "refresh() — delegating to parent poll"
    parent?.refresh()
}

// ---------------------------------------------------------------------------
// Commands — Thermostat mode
// ---------------------------------------------------------------------------

def setThermostatMode(String mode) {
    switch (mode?.toLowerCase()) {
        case "heat":
            sendEvt("thermostatMode", "heat", "heat")
            sendDevicePatch([Mode: "Heat"])
            break
        case "off":
            sendEvt("thermostatMode", "off", "off")
            sendDevicePatch([Mode: "Off"])
            break
        case "cool":
        case "auto":
        case "emergency heat":
            log.warn "[SunStat] ${device.displayName}: mode '${mode}' is not supported on a heat-only thermostat — command ignored"
            break
        default:
            log.warn "[SunStat] ${device.displayName}: unknown thermostatMode '${mode}' — command ignored"
    }
}

def heat()          { setThermostatMode("heat") }
def off()           { setThermostatMode("off") }
def cool()          { log.warn "[SunStat] ${device.displayName}: cool() not supported on heat-only device" }
def auto()          { log.warn "[SunStat] ${device.displayName}: auto() not supported on heat-only device" }
def emergencyHeat() { log.warn "[SunStat] ${device.displayName}: emergencyHeat() not supported — use setBoost(minutes) for a timed boost override" }

// ---------------------------------------------------------------------------
// Commands — Setpoint
// ---------------------------------------------------------------------------

def setHeatingSetpoint(temp) {
    BigDecimal step    = validStep(state.setpointStep)
    BigDecimal rounded = (Math.round((temp as BigDecimal) / step) * step).setScale(2, BigDecimal.ROUND_HALF_UP)
    BigDecimal clamped = clampSetpoint(rounded)
    String descTxt = "${device.displayName} heatingSetpoint set to ${clamped}"
    sendEvent(name: "heatingSetpoint",   value: clamped, unit: location.temperatureScale, descriptionText: descTxt, type: "digital")
    sendEvent(name: "thermostatSetpoint", value: clamped, unit: location.temperatureScale,
              descriptionText: "${device.displayName} thermostatSetpoint set to ${clamped}", type: "digital")
    if (settings.txtEnable) { log.info descTxt }
    sendDevicePatch([Heat: clamped])
}

def setCoolingSetpoint(temp) {
    log.warn "[SunStat] ${device.displayName}: setCoolingSetpoint() ignored — heat-only device"
}

// ---------------------------------------------------------------------------
// Commands — Fan (stubs required by Thermostat capability)
// ---------------------------------------------------------------------------

def fanAuto()     { log.warn "[SunStat] ${device.displayName}: fanAuto() not applicable — no fan on this device" }
def fanOn()       { log.warn "[SunStat] ${device.displayName}: fanOn() not applicable — no fan on this device" }
def fanCirculate(){ log.warn "[SunStat] ${device.displayName}: fanCirculate() not applicable — no fan on this device" }
def setThermostatFanMode(mode) {
    log.warn "[SunStat] ${device.displayName}: setThermostatFanMode('${mode}') not applicable — no fan on this device"
}

// ---------------------------------------------------------------------------
// Commands — Custom
// ---------------------------------------------------------------------------

/**
 * setBoost(minutes): STUB — no documented first-class boost API in v0.1.0.
 * Pending API discovery (see TESTING.md).
 */
def setBoost(minutes) {
    log.warn "[SunStat] setBoost not yet implemented — pending API discovery (see TESTING.md)"
}

/**
 * cancelBoost: STUB — same reason as setBoost.
 */
def cancelBoost() {
    log.warn "[SunStat] cancelBoost not yet implemented — pending API discovery (see TESTING.md)"
}

/**
 * setScheduleEnabled(enabled): Enable or disable the thermostat schedule.
 * Sends PATCH /api/Device/{deviceId} with {"Settings":{"SchedEnable":"On"|"Off"}}.
 */
def setScheduleEnabled(String enabled) {
    String lower = enabled?.toLowerCase()
    if (lower != "on" && lower != "off") {
        log.warn "[SunStat] ${device.displayName}: setScheduleEnabled('${enabled}') — value must be 'on' or 'off'; command ignored"
        return
    }
    // Optimistic update before API call
    emitIfChanged("scheduleEnabled", lower, "${device.displayName} scheduleEnabled is ${lower}")
    String deviceId = device.getDataValue("wattsDeviceId")
    if (!deviceId) {
        log.error "[SunStat] ${device.displayName}: no wattsDeviceId — cannot send command. Run discoverDevices() on the parent."
        return
    }
    debugLog "setScheduleEnabled(${lower}) → wattsDeviceId=${deviceId}"
    parent.sendDevicePatch(deviceId, [SchedEnable: lower == "on" ? "On" : "Off"])
}

/**
 * setFloorMinTemp(temp): Sets the floor minimum warmth temperature.
 * Applies step-rounding (Feature 5) and floor bounds clamping (Feature 6).
 * Read-modify-write: must supply both W (warmth) and A (away) together.
 */
def setFloorMinTemp(temp) {
    BigDecimal step     = validStep(state.setpointStep)
    BigDecimal input    = safeBigDecimal(temp, 40.0)
    BigDecimal rounded  = (Math.round(input / step) * step).setScale(2, BigDecimal.ROUND_HALF_UP)
    BigDecimal floorMin = safeBigDecimal(state.floorMin, 40.0)
    BigDecimal floorMax = safeBigDecimal(state.floorMax, 85.0)
    BigDecimal clamped  = rounded.max(floorMin).min(floorMax)
    if (clamped != rounded) {
        log.warn "[SunStat] setFloorMinTemp(${input}) clamped to ${clamped} (range ${floorMin}..${floorMax})"
    }
    // Read current away value from cached state (default 0 = disabled)
    BigDecimal currentAway = safeBigDecimal(state.floorAway, 0.0)
    String descTxt = "${device.displayName} floorMinTemp set to ${clamped}"
    if (settings.txtEnable) { log.info descTxt }
    debugLog "setFloorMinTemp(${clamped}) — floorAway=${currentAway}"
    sendDevicePatch([Schedule: [Floor: [W: clamped, A: currentAway]]])
}

// ---------------------------------------------------------------------------
// State parsing — called by parent after each poll
// ---------------------------------------------------------------------------

/**
 * Receives the parsed JSON body from GET /api/Device/{deviceId}.
 * This is the primary state update path; all attribute events originate here.
 */
def parseDeviceState(Map body) {
    try {
        parseDeviceStateInternal(body)
    } catch (Exception e) {
        log.error "[SunStat] ${device.displayName} parseDeviceState exception: ${e.message}"
    }
}

private void parseDeviceStateInternal(Map body) {
    if (!body) {
        debugLog "parseDeviceState received null/empty body — skipping"
        return
    }

    // Device online
    boolean isConnected = body?.isConnected == true
    emitIfChanged("deviceOnline", isConnected ? "true" : "false",
                  "${device.displayName} deviceOnline is ${isConnected}")

    Map data = body?.data instanceof Map ? body.data as Map : [:]
    if (!data) {
        debugLog "parseDeviceState: no 'data' map in response for ${device.displayName}"
        return
    }

    // Temperature units: API returns "F" or "C"; hub uses location.temperatureScale
    String apiUnit = safeStr(data?.TempUnits?.Val, "F")
    String hubScale = location.temperatureScale ?: "F"

    // Min/max setpoint bounds — cache for clamping
    BigDecimal targetMin = safeBigDecimal(data?.Target?.Min, 40.0)
    BigDecimal targetMax = safeBigDecimal(data?.Target?.Max, 100.0)
    state.setpointMin = targetMin
    state.setpointMax = targetMax

    // Setpoint step  (data.Target.Steps)
    def stepsRaw = data?.Target?.Steps
    if (stepsRaw != null) {
        BigDecimal step = safeBigDecimal(stepsRaw, 1.0)
        if (step > 0) {
            state.setpointStep = step
            String descTxtStep = "${device.displayName} setpointStep is ${step}"
            sendEvent(name: "setpointStep", value: step, descriptionText: descTxtStep, type: "digital")
            if (settings.txtEnable) { log.info descTxtStep }
        }
    }

    // Outdoor temperature  (data.Sensors.Outdoor.Val / .Status)
    def outdoorMap = data?.Sensors?.Outdoor
    if (outdoorMap == null) {
        emitIfChanged("outdoorSensorStatus", "unavailable",
                      "${device.displayName} outdoorSensorStatus is unavailable")
    } else {
        String outdoorStatus = safeStr(outdoorMap?.Status)
        def outdoorRaw = outdoorMap?.Val
        if (outdoorStatus == "Okay" && outdoorRaw != null) {
            BigDecimal outdoorTemp = convertTemp(safeBigDecimal(outdoorRaw, 0.0), apiUnit, hubScale)
            String descTxtO = "${device.displayName} outdoorTemperature is ${outdoorTemp} °${hubScale}"
            sendEvent(name: "outdoorTemperature", value: outdoorTemp, unit: hubScale, descriptionText: descTxtO, type: "digital")
            if (settings.txtEnable) { log.info descTxtO }
            emitIfChanged("outdoorSensorStatus", "okay",
                          "${device.displayName} outdoorSensorStatus is okay")
        } else {
            emitIfChanged("outdoorSensorStatus", "unavailable",
                          "${device.displayName} outdoorSensorStatus is unavailable")
        }
    }

    // Room temperature
    def roomRaw = data?.Sensors?.Room?.Val
    if (roomRaw != null) {
        BigDecimal roomTemp = convertTemp(safeBigDecimal(roomRaw, 0.0), apiUnit, hubScale)
        String descTxt = "${device.displayName} temperature is ${roomTemp} °${hubScale}"
        sendEvent(name: "temperature", value: roomTemp, unit: hubScale, descriptionText: descTxt, type: "digital")
        if (settings.txtEnable) { log.info descTxt }
    }

    // Floor temperature — guard against disconnected probe sentinel
    def floorRaw = data?.Sensors?.Floor?.Val
    if (floorRaw != null) {
        BigDecimal floorF = apiUnit == "C"
            ? celsiusToFahrenheit(safeBigDecimal(floorRaw, 0.0))
            : safeBigDecimal(floorRaw, 0.0)

        if (floorF > FLOOR_PROBE_DISCONNECTED_F) {
            log.warn "[SunStat] ${device.displayName}: floor probe reports ${floorRaw}°${apiUnit} — sensor likely disconnected; clearing floorTemperature"
            device.deleteCurrentState("floorTemperature")
        } else {
            BigDecimal floorTemp = convertTemp(safeBigDecimal(floorRaw, 0.0), apiUnit, hubScale)
            String descTxt = "${device.displayName} floorTemperature is ${floorTemp} °${hubScale}"
            sendEvent(name: "floorTemperature", value: floorTemp, unit: hubScale, descriptionText: descTxt, type: "digital")
            if (settings.txtEnable) { log.info descTxt }
        }
    }

    // Cache floor away value for setFloorMinTemp read-modify-write
    def floorAway = data?.Schedule?.Floor?.A
    if (floorAway != null) {
        state.floorAway = floorAway
    }

    // Floor bounds clamping  (data.Schedule.FloorMin / data.Schedule.FloorMax)
    def fMin = data?.Schedule?.FloorMin
    def fMax = data?.Schedule?.FloorMax
    if (fMin != null) { state.floorMin = fMin }
    if (fMax != null) { state.floorMax = fMax }

    // Thermostat mode
    String apiMode = safeStr(data?.Mode?.Val)
    String hubMode = apiModeToHubitat(apiMode)
    if (hubMode) {
        emitIfChanged("thermostatMode", hubMode,
                      "${device.displayName} thermostatMode is ${hubMode}")
    } else if (apiMode) {
        log.warn "[SunStat] ${device.displayName}: unrecognised Mode.Val '${apiMode}'"
    }

    // Operating state
    String apiOp = safeStr(data?.State?.Op)
    String hubOp = apiOpToHubitat(apiOp)
    emitIfChanged("thermostatOperatingState", hubOp,
                  "${device.displayName} thermostatOperatingState is ${hubOp}")

    // Heating setpoint
    def heatRaw = data?.Target?.Heat
    if (heatRaw != null) {
        BigDecimal heatSetpoint = convertTemp(safeBigDecimal(heatRaw, 68.0), apiUnit, hubScale)
        String descTxt = "${device.displayName} heatingSetpoint is ${heatSetpoint} °${hubScale}"
        sendEvent(name: "heatingSetpoint",    value: heatSetpoint, unit: hubScale, descriptionText: descTxt, type: "digital")
        sendEvent(name: "thermostatSetpoint", value: heatSetpoint, unit: hubScale,
                  descriptionText: "${device.displayName} thermostatSetpoint is ${heatSetpoint} °${hubScale}", type: "digital")
        if (settings.txtEnable) { log.info descTxt }
    }

    // Cooling setpoint (store but don't surface as active for heat-only device)
    def coolRaw = data?.Target?.Cool
    if (coolRaw != null) {
        BigDecimal coolSetpoint = convertTemp(safeBigDecimal(coolRaw, 100.0), apiUnit, hubScale)
        sendEvent(name: "coolingSetpoint", value: coolSetpoint, unit: hubScale,
                  descriptionText: "${device.displayName} coolingSetpoint is ${coolSetpoint} °${hubScale}", type: "digital")
    }

    // Hold mode  (data.Target.Hold)
    def holdRaw = data?.Target?.Hold
    if (holdRaw == null) {
        emitIfChanged("thermostatHold", "unknown",
                      "${device.displayName} thermostatHold is unknown")
    } else {
        Integer holdVal = safeInt(holdRaw, 0)
        String holdStr = holdVal == 0 ? "following" : "holding"
        emitIfChanged("thermostatHold", holdStr,
                      "${device.displayName} thermostatHold is ${holdStr}")
    }

    // Schedule enabled  (data.SchedEnable.Val)
    String schedVal = safeStr(data?.SchedEnable?.Val)
    if (schedVal) {
        String schedEnabled
        if (schedVal == "On") {
            schedEnabled = "on"
        } else if (schedVal == "Off") {
            schedEnabled = "off"
        } else {
            schedEnabled = "unknown"
            log.warn "[SunStat] ${device.displayName}: unrecognised SchedEnable.Val '${schedVal}'"
        }
        emitIfChanged("scheduleEnabled", schedEnabled,
                      "${device.displayName} scheduleEnabled is ${schedEnabled}")
    }

    // Energy reporting  (data.Energy.Heat.Daily[] / Monthly[])
    def energyBlock = data?.Energy
    if (energyBlock == null) {
        debugLog "parseDeviceState: no Energy data in response for ${device.displayName} — some firmwares omit this"
    } else {
        List dailyList   = energyBlock?.Heat?.Daily   instanceof List ? energyBlock.Heat.Daily   as List : []
        List monthlyList = energyBlock?.Heat?.Monthly instanceof List ? energyBlock.Heat.Monthly as List : []
        if (dailyList.size() >= 1) {
            BigDecimal energyToday = roundEnergy(dailyList[0])
            String descTxtE = "${device.displayName} energy (today) is ${energyToday} kWh"
            sendEvent(name: "energy", value: energyToday, unit: "kWh", descriptionText: descTxtE, type: "digital")
            if (settings.txtEnable) { log.info descTxtE }
        }
        if (dailyList.size() >= 2) {
            BigDecimal energyYest = roundEnergy(dailyList[1])
            String descTxtEY = "${device.displayName} energyYesterday is ${energyYest} kWh"
            sendEvent(name: "energyYesterday", value: energyYest, unit: "kWh", descriptionText: descTxtEY, type: "digital")
            if (settings.txtEnable) { log.info descTxtEY }
        }
        if (monthlyList.size() >= 1) {
            BigDecimal energyMon = roundEnergy(monthlyList[0])
            String descTxtEM = "${device.displayName} energyMonth is ${energyMon} kWh"
            sendEvent(name: "energyMonth", value: energyMon, unit: "kWh", descriptionText: descTxtEM, type: "digital")
            if (settings.txtEnable) { log.info descTxtEM }
        }
        if (monthlyList.size() >= 2) {
            BigDecimal energyLastMon = roundEnergy(monthlyList[1])
            String descTxtELM = "${device.displayName} energyLastMonth is ${energyLastMon} kWh"
            sendEvent(name: "energyLastMonth", value: energyLastMon, unit: "kWh", descriptionText: descTxtELM, type: "digital")
            if (settings.txtEnable) { log.info descTxtELM }
        }
    }

    // Mirror awayMode from parent (location-level; read-only on child)
    String parentAway = safeStr(parent?.currentValue("awayMode"), "unknown")
    emitIfChanged("awayMode", parentAway,
                  "${device.displayName} awayMode is ${parentAway}")
}

// ---------------------------------------------------------------------------
// API mapping helpers
// ---------------------------------------------------------------------------

private String apiModeToHubitat(String apiMode) {
    switch (apiMode) {
        case "Heat": return "heat"
        case "Off":  return "off"
        default:     return null
    }
}

private String apiOpToHubitat(String apiOp) {
    switch (apiOp) {
        case "Heating": return "heating"
        case "Cooling": return "cooling"
        default:        return "idle"
    }
}

// ---------------------------------------------------------------------------
// Temperature helpers
// ---------------------------------------------------------------------------

private BigDecimal convertTemp(BigDecimal value, String fromUnit, String toUnit) {
    if (fromUnit == toUnit) {
        return value.setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    if (fromUnit == "C" && toUnit == "F") {
        return celsiusToFahrenheit(value)
    }
    if (fromUnit == "F" && toUnit == "C") {
        return fahrenheitToCelsius(value)
    }
    return value.setScale(1, BigDecimal.ROUND_HALF_UP)
}

private BigDecimal celsiusToFahrenheit(BigDecimal c) {
    return ((c * 9.0 / 5.0) + 32.0).setScale(1, BigDecimal.ROUND_HALF_UP)
}

private BigDecimal fahrenheitToCelsius(BigDecimal f) {
    return ((f - 32.0) * 5.0 / 9.0).setScale(1, BigDecimal.ROUND_HALF_UP)
}

private BigDecimal clampSetpoint(value) {
    BigDecimal temp = safeBigDecimal(value, 68.0)
    BigDecimal minSp = safeBigDecimal(state.setpointMin, 40.0)
    BigDecimal maxSp = safeBigDecimal(state.setpointMax, 100.0)
    return temp.max(minSp).min(maxSp).setScale(1, BigDecimal.ROUND_HALF_UP)
}

// ---------------------------------------------------------------------------
// Command routing to parent
// ---------------------------------------------------------------------------

private void sendDevicePatch(Map settingsMap) {
    String deviceId = device.getDataValue("wattsDeviceId")
    if (!deviceId) {
        log.error "[SunStat] ${device.displayName}: no wattsDeviceId — cannot send command. Run discoverDevices() on the parent."
        return
    }
    debugLog "sendDevicePatch(${settingsMap}) → wattsDeviceId=${deviceId}"
    parent.sendDevicePatch(deviceId, settingsMap)
}

// ---------------------------------------------------------------------------
// Event helpers
// ---------------------------------------------------------------------------

private void sendEvt(String name, value, String descValue) {
    String descTxt = "${device.displayName} ${name} is ${descValue}"
    sendEvent(name: name, value: value, descriptionText: descTxt)
    if (settings.txtEnable) { log.info descTxt }
}

private void emitIfChanged(String name, value, String descTxt) {
    String current = safeStr(device.currentValue(name))
    String newVal  = safeStr(value)
    if (current == newVal) {
        debugLog "emitIfChanged: ${name} unchanged (${newVal}) — skipping"
        return
    }
    sendEvent(name: name, value: value, descriptionText: descTxt, type: "digital")
    if (settings.txtEnable) { log.info descTxt }
}

// ---------------------------------------------------------------------------
// Safe-cast utilities
// ---------------------------------------------------------------------------

private String safeStr(value, String fallback = "") {
    return value == null ? fallback : value.toString()
}

private BigDecimal safeBigDecimal(value, BigDecimal fallback = 0.0) {
    try {
        return value != null ? (value as BigDecimal) : fallback
    } catch (ignored) {
        return fallback
    }
}

/** Returns a valid positive step; falls back to 1.0 if zero or invalid. */
private BigDecimal validStep(stepState) {
    BigDecimal step = safeBigDecimal(stepState, 1.0)
    return (step > 0) ? step : 1.0
}

/** Round an energy value to 2 decimal places. */
private BigDecimal roundEnergy(value) {
    return safeBigDecimal(value, 0.0).setScale(2, BigDecimal.ROUND_HALF_UP)
}

private Integer safeInt(value, Integer fallback = 0) {
    try {
        return value != null ? (value as Integer) : fallback
    } catch (ignored) {
        return fallback
    }
}

// ---------------------------------------------------------------------------
// Logging
// ---------------------------------------------------------------------------

private void debugLog(String msg) {
    if (settings.logEnable) {
        log.debug "[SunStat] ${msg}"
    }
}
