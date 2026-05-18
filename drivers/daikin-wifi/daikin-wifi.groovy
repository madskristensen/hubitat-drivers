/**
 * Daikin WiFi Thermostat
 * Author:  Mads Kristensen
 * Version: 0.1.0
 * License: MIT
 *
 * Local LAN control for Daikin WiFi adapters (BRP069B series, BRP15B61, and similar
 * variants exposing the unauthenticated /aircon/* HTTP API on port 80).
 *
 * Inspiration / prior art:
 *   eriktack/hubitat-daikin-wifi (MIT) — first community driver to map the Daikin
 *   BRP069B HTTP API to a Hubitat thermostat. This driver is a clean-room
 *   implementation written from protocol notes; no code is derived from the
 *   original. Credit and thanks to @eriktack for the foundational research.
 *
 * Changelog:
 *   0.1.0 — 2026-05-18 — initial clean-room implementation; Thermostat + Switch +
 *     EnergyMeter + HealthCheck capabilities; .isNumber() sentinel guards on all
 *     numeric parses; separate 30-min energy poll; initialize() lifecycle.
 */

import groovy.transform.Field
import groovy.json.JsonOutput

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

@Field static final String  DRIVER_VERSION            = "0.1.0"
@Field static final Integer DAIKIN_PORT               = 80
@Field static final Integer LAST_ACTIVITY_THROTTLE_MS = 60000
@Field static final Integer ENERGY_POLL_MINUTES       = 30
@Field static final Integer PING_TIMEOUT_SECONDS      = 5

// Daikin BRP069B mode codes — documented in the community BRP069B4 local HTTP API
// spec (ael-code/daikin-control on GitHub). When pow=0 the driver overrides mode
// to "off" regardless of the mode code field.
@Field static final Map<String, String> DAIKIN_MODE_TO_HUBITAT = [
    "0": "auto", "1": "auto", "2": "dry", "3": "cool", "4": "heat",
    "6": "fan",  "7": "auto"
]

@Field static final Map<String, String> HUBITAT_MODE_TO_DAIKIN = [
    "auto": "1", "cool": "3", "heat": "4", "dry": "2", "fan": "6", "off": "0"
]

// Daikin fan-rate codes: A = Auto, B = Silent (Quiet), 3–7 = speed levels.
@Field static final List<String> FAN_RATE_OPTIONS = ["A", "B", "3", "4", "5", "6", "7"]

@Field static final Map<String, String> FAN_RATE_LABEL = [
    "A": "Auto", "B": "Silent", "3": "Speed 3", "4": "Speed 4",
    "5": "Speed 5", "6": "Speed 6", "7": "Speed 7"
]

@Field static final List<String> SUPPORTED_MODES     = ["auto", "cool", "heat", "dry", "fan", "off"]
@Field static final List<String> SUPPORTED_FAN_MODES = ["auto", "on"]

// ---------------------------------------------------------------------------
// Metadata
// ---------------------------------------------------------------------------

metadata {
    definition(
        name:      "Daikin WiFi Thermostat",
        namespace: "mads",
        author:    "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/daikin-wifi/daikin-wifi.groovy"
    ) {
        capability "Actuator"
        capability "Sensor"
        capability "Thermostat"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "EnergyMeter"
        capability "Switch"
        capability "Refresh"
        capability "Polling"
        capability "HealthCheck"

        command "refreshEnergy"
        command "setFanRate", [[name: "rate*", type: "ENUM", constraints: FAN_RATE_OPTIONS,
            description: "Daikin fan speed code (A=Auto, B=Silent, 3–7=speed levels)"]]

        attribute "outsideTemp",  "number"
        attribute "fanRate",      "enum",   FAN_RATE_OPTIONS
        attribute "healthStatus", "enum",   ["online", "offline", "unknown"]
        attribute "lastActivity", "string"
    }

    preferences {
        input name: "ip", type: "text",
              title: "Adapter IP address",
              description: "Static LAN IP of the Daikin WiFi adapter (port 80, no auth required).",
              required: true

        input name: "refreshInterval", type: "enum",
              title: "Refresh interval",
              options: ["1": "1 minute", "5": "5 minutes (recommended)", "10": "10 minutes",
                        "15": "15 minutes", "30": "30 minutes"],
              defaultValue: "5",
              required: true

        input name: "defaultMode", type: "enum",
              title: "Default mode on power-on (optional)",
              description: "Applied after Hubitat turns the unit on. Leave blank to keep the device's last setting.",
              options: ["auto", "cool", "heat", "dry", "fan"],
              required: false

        input name: "defaultSetpoint", type: "decimal",
              title: "Default setpoint on power-on (optional)",
              description: "In °F or °C matching your hub's temperature scale. Leave blank to keep the device's last setpoint.",
              required: false

        input name: "defaultFanRate", type: "enum",
              title: "Default fan rate on power-on (optional)",
              description: "Leave blank to keep the device's last fan rate.",
              options: FAN_RATE_OPTIONS,
              required: false

        input name: "logEnable", type: "bool",
              title: "Enable debug logging (auto-off after 30 minutes)",
              defaultValue: false

        input name: "traceEnable", type: "bool",
              title: "Trace logging — protocol-level detail (auto-off after 30 minutes)",
              defaultValue: false
    }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    log.info "Daikin WiFi Thermostat v${DRIVER_VERSION} installed: ${device.displayName}"
    device.updateSetting("refreshInterval", [value: "5",   type: "enum"])
    device.updateSetting("logEnable",       [value: false, type: "bool"])
    device.updateSetting("traceEnable",     [value: false, type: "bool"])

    // Emit required Thermostat schema events so RM / dashboards work on first install.
    sendEvent(name: "supportedThermostatModes",
              value: JsonOutput.toJson(SUPPORTED_MODES),
              descriptionText: "${device.displayName} supported thermostat modes set")
    sendEvent(name: "supportedThermostatFanModes",
              value: JsonOutput.toJson(SUPPORTED_FAN_MODES),
              descriptionText: "${device.displayName} supported fan modes set")
    sendEvent(name: "thermostatMode",           value: "off",     descriptionText: "${device.displayName} thermostat mode → off")
    sendEvent(name: "thermostatOperatingState", value: "idle",    descriptionText: "${device.displayName} operating state → idle")
    sendEvent(name: "thermostatFanMode",        value: "auto",    descriptionText: "${device.displayName} fan mode → auto")
    sendEvent(name: "switch",                   value: "off",     descriptionText: "${device.displayName} turned off")
    sendEvent(name: "healthStatus",             value: "unknown", descriptionText: "${device.displayName} health status is unknown")

    initialize()
}

def updated() {
    log.info "Daikin WiFi Thermostat v${DRIVER_VERSION} preferences updated: ${device.displayName}"
    if (settings.logEnable) {
        runIn(1800, "logsOff")
        debugLog "Debug logging enabled; auto-disable in 30 minutes"
    }
    if (settings.traceEnable) { runIn(1800, "traceOff") }

    // Re-emit supported lists so any new mode selections in dashboards are current.
    sendEvent(name: "supportedThermostatModes",
              value: JsonOutput.toJson(SUPPORTED_MODES),
              descriptionText: "${device.displayName} supported thermostat modes refreshed")
    sendEvent(name: "supportedThermostatFanModes",
              value: JsonOutput.toJson(SUPPORTED_FAN_MODES),
              descriptionText: "${device.displayName} supported fan modes refreshed")

    initialize()
}

def initialize() {
    debugLog "initialize()"
    unschedule()
    state.pingPending           = false
    state.pingRequestedAt       = 0L
    state.lastActivityEmittedAt = 0L

    if (!settings.ip) {
        log.warn "[Daikin] IP not configured — polling disabled until preferences are saved"
        return
    }

    ensureDNI()
    registerSchedules()
    runIn(2, "refresh")
}

def uninstalled() {
    unschedule()
    debugLog "uninstalled()"
}

// ---------------------------------------------------------------------------
// Log helpers
// ---------------------------------------------------------------------------

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "[Daikin] Debug logging auto-disabled after 30 minutes"
}

def traceOff() {
    device.updateSetting("traceEnable", [value: "false", type: "bool"])
    log.info "[Daikin] Trace logging auto-disabled after 30 minutes"
}

private void debugLog(String msg) { if (settings.logEnable)  { log.debug "[Daikin] ${msg}" } }
private void traceLog(String msg) { if (settings.traceEnable) { log.trace "[Daikin] ${msg}" } }

// ---------------------------------------------------------------------------
// Commands — Switch
// ---------------------------------------------------------------------------

def on() {
    if (device.currentValue("switch") == "on") { debugLog "on(): already on — skipping"; return }
    log.info "[Daikin] ${device.displayName} turned on"
    emitIfChanged("switch", "on", null, "${device.displayName} turned on")
    sendControlWrite([pow: "1"])
    runIn(2, "applyPowerOnDefaults")
}

def off() {
    if (device.currentValue("switch") == "off") { debugLog "off(): already off — skipping"; return }
    log.info "[Daikin] ${device.displayName} turned off"
    emitIfChanged("switch",                   "off",  null, "${device.displayName} turned off")
    emitIfChanged("thermostatMode",           "off",  null, "${device.displayName} thermostat mode → off")
    emitIfChanged("thermostatOperatingState", "idle", null, "${device.displayName} operating state → idle")
    sendControlWrite([pow: "0"])
}

// ---------------------------------------------------------------------------
// Commands — Thermostat
// ---------------------------------------------------------------------------

def auto()         { setThermostatMode("auto") }
def cool()         { setThermostatMode("cool") }
def heat()         { setThermostatMode("heat") }

def emergencyHeat() {
    log.warn "[Daikin] emergencyHeat() has no direct equivalent — delegating to heat mode"
    setThermostatMode("heat")
}

def fanAuto()      { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("auto") }   // closest Daikin analogue
def fanOn()        { setThermostatFanMode("on") }

def setThermostatMode(String mode) {
    String lmode = mode?.toLowerCase()
    if (lmode == "off") { off(); return }
    String daikinCode = HUBITAT_MODE_TO_DAIKIN[lmode]
    if (!daikinCode) { log.warn "[Daikin] Unsupported thermostat mode: ${mode}"; return }
    String opState = operatingStateForMode(lmode)
    log.info "[Daikin] ${device.displayName} thermostat mode → ${lmode}"
    emitIfChanged("thermostatMode",           lmode,   null, "${device.displayName} thermostat mode → ${lmode}")
    emitIfChanged("switch",                   "on",    null, "${device.displayName} turned on")
    emitIfChanged("thermostatOperatingState", opState, null, "${device.displayName} operating state → ${opState}")
    sendControlWrite([pow: "1", mode: daikinCode])
}

def setThermostatFanMode(String fanMode) {
    String lmode = fanMode?.toLowerCase()
    String daikinRate
    if (lmode == "auto") {
        daikinRate = "A"
    } else {
        // "on" or "circulate" → preserve current speed if set, else fall back to Speed 3
        String current = device.currentValue("fanRate") ?: "A"
        daikinRate = (current == "A") ? "3" : current
    }
    String hubitatFanMode = (daikinRate == "A") ? "auto" : "on"
    log.info "[Daikin] ${device.displayName} fan mode → ${lmode} (Daikin f_rate=${daikinRate})"
    emitIfChanged("thermostatFanMode", hubitatFanMode, null, "${device.displayName} fan mode → ${hubitatFanMode}")
    emitIfChanged("fanRate",           daikinRate,     null, "${device.displayName} fan rate → ${daikinRate}")
    sendControlWrite([f_rate: daikinRate])
}

def setFanRate(String rate) {
    if (!FAN_RATE_OPTIONS.contains(rate)) { log.warn "[Daikin] Unknown fan rate: ${rate}"; return }
    String hubitatFanMode = (rate == "A") ? "auto" : "on"
    log.info "[Daikin] ${device.displayName} fan rate → ${rate} (${FAN_RATE_LABEL[rate]})"
    emitIfChanged("fanRate",           rate,           null, "${device.displayName} fan rate → ${rate}")
    emitIfChanged("thermostatFanMode", hubitatFanMode, null, "${device.displayName} fan mode → ${hubitatFanMode}")
    sendControlWrite([f_rate: rate])
}

def setHeatingSetpoint(temp) {
    BigDecimal tempBd = new BigDecimal(temp.toString())
    BigDecimal clamped = clampSetpoint(tempBd)
    String unitStr = "°${location.temperatureScale}"
    log.info "[Daikin] ${device.displayName} heating setpoint → ${clamped}${unitStr}"
    emitIfChanged("heatingSetpoint", clamped, unitStr, "${device.displayName} heating setpoint → ${clamped}${unitStr}")
    emitIfChanged("thermostatSetpoint", clamped, unitStr, "${device.displayName} setpoint → ${clamped}${unitStr}")
    sendControlWrite([stemp: "${temperatureToC(clamped)}"])
}

def setCoolingSetpoint(temp) {
    BigDecimal tempBd = new BigDecimal(temp.toString())
    BigDecimal clamped = clampSetpoint(tempBd)
    String unitStr = "°${location.temperatureScale}"
    log.info "[Daikin] ${device.displayName} cooling setpoint → ${clamped}${unitStr}"
    emitIfChanged("coolingSetpoint",    clamped, unitStr, "${device.displayName} cooling setpoint → ${clamped}${unitStr}")
    emitIfChanged("thermostatSetpoint", clamped, unitStr, "${device.displayName} setpoint → ${clamped}${unitStr}")
    sendControlWrite([stemp: "${temperatureToC(clamped)}"])
}

def setSchedule(schedule) {
    log.warn "[Daikin] setSchedule() is not supported in v${DRIVER_VERSION} — use Hubitat rules instead"
}

// ---------------------------------------------------------------------------
// Commands — Refresh / Polling / HealthCheck
// ---------------------------------------------------------------------------

def refresh() {
    if (!settings.ip) { return }
    debugLog "refresh() — polling control + sensor info"
    sendGet("/aircon/get_control_info", "handleControlInfo")
    runIn(2, "doSensorRefresh")
}

def doSensorRefresh() {
    sendGet("/aircon/get_sensor_info", "handleSensorInfo")
}

def poll() { refresh() }

def refreshEnergy() {
    if (!settings.ip) { return }
    debugLog "refreshEnergy() — polling week + year power"
    sendGet("/aircon/get_week_power_ex", "handleWeekPower")
    runIn(3, "doYearPowerRefresh")
}

def doYearPowerRefresh() {
    sendGet("/aircon/get_year_power_ex", "handleYearPower")
}

def ping() {
    debugLog "ping()"
    state.pingPending     = true
    state.pingRequestedAt = now()
    unschedule("pingTimeout")
    runIn(PING_TIMEOUT_SECONDS, "pingTimeout")
    sendGet("/aircon/get_control_info", "handlePingResponse")
}

def pingTimeout() {
    if (state.pingPending != true) { return }
    state.pingPending = false
    log.warn "[Daikin] pingTimeout() — ${device.displayName} did not respond within ${PING_TIMEOUT_SECONDS}s"
    emitIfChanged("healthStatus", "offline", null,
        "${device.displayName} did not respond to ping within ${PING_TIMEOUT_SECONDS}s")
}

// ---------------------------------------------------------------------------
// Power-on defaults
// ---------------------------------------------------------------------------

def applyPowerOnDefaults() {
    Map writes = [pow: "1"]
    boolean hasDefaults = false

    if (settings.defaultMode) {
        String dCode = HUBITAT_MODE_TO_DAIKIN[settings.defaultMode]
        if (dCode) { writes.mode = dCode; hasDefaults = true }
    }
    if (settings.defaultSetpoint != null) {
        BigDecimal sp = new BigDecimal(settings.defaultSetpoint.toString())
        writes.stemp = "${temperatureToC(sp)}"
        hasDefaults = true
    }
    if (settings.defaultFanRate) {
        writes.f_rate = settings.defaultFanRate
        hasDefaults = true
    }

    if (hasDefaults) {
        debugLog "Applying power-on defaults: ${writes}"
        sendControlWrite(writes)
    }
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

private void ensureDNI() {
    // Hubitat routes LAN responses by matching the device's DNI to the source IP
    // (hex-encoded, upper-case, 8 characters).
    try {
        String hexIp = settings.ip.tokenize('.').collect {
            String.format('%02x', it.toInteger())
        }.join('').toUpperCase()
        if (device.deviceNetworkId != hexIp) {
            device.deviceNetworkId = hexIp
            debugLog "DNI updated to ${hexIp} for IP ${settings.ip}"
        }
    } catch (Exception e) {
        log.warn "[Daikin] Could not derive DNI from IP '${settings.ip}': ${e.message}"
    }
}

private void sendGet(String path, String callbackMethod) {
    if (!settings.ip) { log.warn "[Daikin] IP not configured — skipping: ${path}"; return }
    traceLog "GET ${path} → ${callbackMethod}"
    try {
        sendHubCommand(new hubitat.device.HubAction(
            [method: "GET", path: path,
             headers: ["HOST": "${settings.ip}:${DAIKIN_PORT}", "Accept": "*/*"]],
            hubitat.device.Protocol.LAN,
            [callback: callbackMethod]
        ))
    } catch (Exception e) {
        log.warn "[Daikin] sendGet failed (${path}): ${e.message}"
    }
}

private void sendControlWrite(Map overrides) {
    // All 6 parameters are required by the adapter on every set_control_info call.
    // Read current device state for any parameter the caller did not override.
    String currentMode = device.currentValue("thermostatMode") ?: "cool"
    boolean isPowered  = (device.currentValue("switch") == "on")

    String pow   = overrides.containsKey("pow")    ? overrides.pow    : (isPowered ? "1" : "0")
    String mCode = overrides.containsKey("mode")   ? overrides.mode   : (HUBITAT_MODE_TO_DAIKIN[currentMode] ?: "1")
    String stemp = overrides.containsKey("stemp")  ? overrides.stemp  : "${currentSetpointC()}"
    String fRate = overrides.containsKey("f_rate") ? overrides.f_rate : (device.currentValue("fanRate") ?: "A")
    String fDir  = overrides.containsKey("f_dir")  ? overrides.f_dir  : "0"
    String shum  = overrides.containsKey("shum")   ? overrides.shum   : "0"

    String path = "/aircon/set_control_info?pow=${pow}&mode=${mCode}&stemp=${stemp}&f_rate=${fRate}&f_dir=${fDir}&shum=${shum}"
    debugLog "sendControlWrite: ${path}"
    sendGet(path, "handleSetControlInfo")
}

// ---------------------------------------------------------------------------
// Response handlers (HubAction callbacks)
// ---------------------------------------------------------------------------

def handleControlInfo(hubitat.device.HubResponse response) {
    if (!checkHttpOk(response, "get_control_info")) { return }
    String body = response?.body
    traceLog "handleControlInfo: ${body}"

    Map kv = parseKV(body)
    if (kv.ret != "OK") { log.warn "[Daikin] get_control_info: ret=${kv.ret}"; return }

    String pow   = kv.pow
    String mode  = kv.mode
    String stemp = kv.stemp
    String fRate = kv.f_rate

    // pow=0 → "off"; otherwise map mode code to Hubitat string
    String tMode    = (pow == "0") ? "off" : (DAIKIN_MODE_TO_HUBITAT[mode] ?: "auto")
    String fanMode  = (fRate == "A") ? "auto" : "on"
    String opState  = operatingStateForMode(tMode)
    String switchSt = (pow == "0") ? "off" : "on"

    emitIfChanged("switch",                   switchSt, null, "${device.displayName} turned ${switchSt}")
    emitIfChanged("thermostatMode",           tMode,    null, "${device.displayName} thermostat mode → ${tMode}")
    emitIfChanged("thermostatFanMode",        fanMode,  null, "${device.displayName} fan mode → ${fanMode}")
    emitIfChanged("thermostatOperatingState", opState,  null, "${device.displayName} operating state → ${opState}")

    if (fRate) {
        emitIfChanged("fanRate", fRate, null, "${device.displayName} fan rate → ${fRate}")
    }

    // stemp can be "-" in fan/dry modes; guard before parsing
    if (stemp?.isNumber()) {
        BigDecimal setpointC       = stemp.toBigDecimal()
        BigDecimal setpointDisplay = temperatureFromC(setpointC)
        String unitStr             = "°${location.temperatureScale}"
        emitIfChanged("heatingSetpoint",   setpointDisplay, unitStr, "${device.displayName} heating setpoint → ${setpointDisplay}${unitStr}")
        emitIfChanged("coolingSetpoint",   setpointDisplay, unitStr, "${device.displayName} cooling setpoint → ${setpointDisplay}${unitStr}")
        emitIfChanged("thermostatSetpoint", setpointDisplay, unitStr, "${device.displayName} setpoint → ${setpointDisplay}${unitStr}")
    }

    emitLastActivity()
}

def handleSensorInfo(hubitat.device.HubResponse response) {
    if (!checkHttpOk(response, "get_sensor_info")) { return }
    String body = response?.body
    traceLog "handleSensorInfo: ${body}"

    Map kv = parseKV(body)
    if (kv.ret != "OK") { log.warn "[Daikin] get_sensor_info: ret=${kv.ret}"; return }

    String htemp = kv.htemp   // indoor temperature  — may be "-"
    String otemp = kv.otemp   // outdoor temperature — may be "-" (frequently, on many units)
    String hhum  = kv.hhum    // indoor humidity     — may be "-" (no humidity sensor on most units)

    // CRITICAL: All three fields return the literal string "-" when the sensor is unavailable.
    // A plain truthiness check passes for "-" (non-null, non-empty string) which would cause
    // Double.parseDouble("-") to throw NumberFormatException. Guard every parse with .isNumber().
    String unitStr = "°${location.temperatureScale}"

    if (htemp?.isNumber()) {
        BigDecimal indoorDisplay = temperatureFromC(htemp.toBigDecimal())
        emitIfChanged("temperature", indoorDisplay, unitStr, "${device.displayName} temperature → ${indoorDisplay}${unitStr}")
    } else {
        traceLog "htemp sentinel '${htemp}' — skipping indoor temperature"
    }

    if (otemp?.isNumber()) {
        BigDecimal outdoorDisplay = temperatureFromC(otemp.toBigDecimal())
        emitIfChanged("outsideTemp", outdoorDisplay, unitStr, "${device.displayName} outside temperature → ${outdoorDisplay}${unitStr}")
    } else {
        traceLog "otemp sentinel '${otemp}' — skipping outdoor temperature"
    }

    // Only emit humidity if the unit has a sensor (non-sentinel numeric response).
    if (hhum?.isNumber()) {
        BigDecimal humidity = hhum.toBigDecimal()
        emitIfChanged("humidity", humidity, "%rh", "${device.displayName} humidity → ${humidity}%")
    } else {
        traceLog "hhum sentinel '${hhum}' — no humidity sensor on this unit"
    }

    emitLastActivity()
}

def handleWeekPower(hubitat.device.HubResponse response) {
    if (!checkHttpOk(response, "get_week_power_ex")) { return }
    String body = response?.body
    traceLog "handleWeekPower: ${body}"

    Map kv = parseKV(body)
    if (kv.ret != "OK") { log.warn "[Daikin] get_week_power_ex: ret=${kv.ret}"; return }

    // s_dayw is a slash-separated list of daily kWh values: [today, yesterday, ...].
    // Guard each element with .isNumber() in case any slot returns a sentinel.
    String sDayw = kv.s_dayw
    if (sDayw) {
        List<String> days = sDayw.split("/")
        if (days.size() > 0 && days[0]?.isNumber()) {
            BigDecimal todayKwh = days[0].toBigDecimal()
            emitIfChanged("energy", todayKwh, "kWh", "${device.displayName} energy today → ${todayKwh} kWh")
        }
    }
}

def handleYearPower(hubitat.device.HubResponse response) {
    if (!checkHttpOk(response, "get_year_power_ex")) { return }
    String body = response?.body
    traceLog "handleYearPower: ${body}"

    Map kv = parseKV(body)
    if (kv.ret != "OK") { log.warn "[Daikin] get_year_power_ex: ret=${kv.ret}"; return }

    // this_year is a slash-separated list of monthly kWh totals.
    String thisYear = kv.this_year
    if (thisYear) {
        List<String> months = thisYear.split("/")
        BigDecimal yearTotal = (months.findAll { it?.isNumber() }.collect { it.toBigDecimal() }.sum() ?: 0) as BigDecimal
        debugLog "Energy this year: ${yearTotal} kWh across ${months.size()} months"
    }
}

def handleSetControlInfo(hubitat.device.HubResponse response) {
    if (!checkHttpOk(response, "set_control_info")) { return }
    Map kv = parseKV(response?.body ?: "")
    if (kv.ret == "OK") {
        debugLog "set_control_info accepted"
    } else {
        log.warn "[Daikin] set_control_info: ret=${kv.ret}"
    }
    // Read back immediately to confirm the new state.
    runIn(2, "getControlInfo")
}

def handlePingResponse(hubitat.device.HubResponse response) {
    unschedule("pingTimeout")
    state.pingPending = false
    if (!checkHttpOk(response, "ping")) {
        emitIfChanged("healthStatus", "offline", null, "${device.displayName} ping response error")
        return
    }
    emitIfChanged("healthStatus", "online", null, "${device.displayName} responded to ping")
    // Parse the control-info body received as part of the ping probe.
    handleControlInfo(response)
}

// ---------------------------------------------------------------------------
// Scheduled entry points (invoked by name from runIn)
// ---------------------------------------------------------------------------

def getControlInfo() {
    sendGet("/aircon/get_control_info", "handleControlInfo")
}

// ---------------------------------------------------------------------------
// Event helpers
// ---------------------------------------------------------------------------

private void emitIfChanged(String name, Object value, String unit, String descriptionText) {
    Object current = device.currentValue(name)
    if (current != null) {
        // Try numeric comparison first to avoid "22.0" != "22" string false-positives.
        try {
            BigDecimal bdCurrent = new BigDecimal(current.toString())
            BigDecimal bdNew     = new BigDecimal(value.toString())
            if (bdCurrent.compareTo(bdNew) == 0) { traceLog "unchanged ${name}=${value} — skip"; return }
        } catch (Exception ignored) {
            if (current.toString() == value.toString()) { traceLog "unchanged ${name}=${value} — skip"; return }
        }
    }
    Map evt = [name: name, value: value, descriptionText: descriptionText]
    if (unit != null) { evt.unit = unit }
    sendEvent(evt)
}

private void emitLastActivity() {
    long nowMs  = now()
    long lastMs = (state.lastActivityEmittedAt instanceof Long) ? state.lastActivityEmittedAt : 0L
    if ((nowMs - lastMs) < LAST_ACTIVITY_THROTTLE_MS) {
        traceLog "lastActivity throttled (< ${LAST_ACTIVITY_THROTTLE_MS}ms since last emit)"
        return
    }
    state.lastActivityEmittedAt = nowMs
    String ts = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")
    sendEvent(name: "lastActivity", value: ts, descriptionText: "${device.displayName} last activity")

    // Promote healthStatus to online on any successful response.
    if (state.pingPending == true) {
        state.pingPending = false
        unschedule("pingTimeout")
        emitIfChanged("healthStatus", "online", null, "${device.displayName} responded to ping")
    } else if (device.currentValue("healthStatus") != "online") {
        emitIfChanged("healthStatus", "online", null, "${device.displayName} is online")
    }
}

// ---------------------------------------------------------------------------
// Temperature helpers
// ---------------------------------------------------------------------------

private BigDecimal temperatureFromC(BigDecimal tempC) {
    if (location.temperatureScale == "F") {
        return (tempC * 9.0G / 5.0G + 32.0G).setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    return tempC.setScale(1, BigDecimal.ROUND_HALF_UP)
}

private BigDecimal temperatureToC(BigDecimal temp) {
    if (location.temperatureScale == "F") {
        return ((temp - 32.0G) * 5.0G / 9.0G).setScale(1, BigDecimal.ROUND_HALF_UP)
    }
    return temp.setScale(1, BigDecimal.ROUND_HALF_UP)
}

private BigDecimal clampSetpoint(BigDecimal temp) {
    // Daikin cool range: 18–32 °C (64–90 °F); heat range: 10–30 °C (50–86 °F).
    // Use the wider combined range so both modes are covered.
    if (location.temperatureScale == "F") {
        return temp < 50.0G ? 50.0G : (temp > 90.0G ? 90.0G : temp)
    }
    return temp < 10.0G ? 10.0G : (temp > 32.0G ? 32.0G : temp)
}

private BigDecimal currentSetpointC() {
    String mode = device.currentValue("thermostatMode") ?: "cool"
    BigDecimal sp
    if (mode == "heat") {
        sp = device.currentValue("heatingSetpoint") as BigDecimal
    } else {
        sp = device.currentValue("coolingSetpoint") as BigDecimal
    }
    if (sp == null) { sp = (location.temperatureScale == "F") ? 72.0G : 22.0G }
    return temperatureToC(sp)
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

private void registerSchedules() {
    Integer minutes = (settings.refreshInterval ?: "5").toInteger()
    String fastCron = (minutes == 1) ? "0 * * * * ?" : "0 */${minutes} * * * ?"
    schedule(fastCron, "refresh")
    schedule("0 */${ENERGY_POLL_MINUTES} * * * ?", "refreshEnergy")
    debugLog "Schedules registered: refresh every ${minutes} min, energy every ${ENERGY_POLL_MINUTES} min"
}

private Map parseKV(String body) {
    // Daikin response format: "ret=OK,pow=1,mode=3,stemp=22.0,..."
    Map result = [:]
    if (!body) { return result }
    body.split(",").each { String pair ->
        int idx = pair.indexOf("=")
        if (idx > 0) {
            result[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
        }
    }
    return result
}

private boolean checkHttpOk(hubitat.device.HubResponse response, String endpoint) {
    if (response?.status == null) { log.warn "[Daikin] No response from ${endpoint}"; return false }
    if (response.status != 200)   { log.warn "[Daikin] HTTP ${response.status} from ${endpoint}"; return false }
    return true
}

private String operatingStateForMode(String mode) {
    switch (mode) {
        case "cool": return "cooling"
        case "heat": return "heating"
        case "fan":  return "fan only"
        case "dry":  return "drying"
        case "off":  return "idle"
        default:     return "auto"   // "auto" mode — device decides heating/cooling demand
    }
}

// Fallback for any LAN message that arrives without a registered HubAction callback.
def parse(String description) {
    traceLog "parse() — unrouted LAN message: ${description?.take(60)}"
}
