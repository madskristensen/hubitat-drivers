/**
 *  Daikin WiFi Thermostat
 *
 *  Original Author:    eriktack (https://github.com/eriktack/hubitat-daikin-wifi)
 *  Original Copyright: Copyright 2018 Ben Dews - https://bendews.com
 *  Fork by:            Mads Kristensen — 2026-05-18
 *
 *  Author:   Mads Kristensen
 *  Version:  0.1.0
 *  License:  MIT
 *
 *  Based on eriktack's Hubitat port of Ben Dews' SmartThings driver; original Hubitat community
 *  port credit to tsaaek (https://community.hubitat.com/t/a-c-control-daikin-mobile-controller/38911/28).
 *
 *  NOTE: Requires a B-series WiFi module (BRP069B4x). The C-series cloud module (BRP069C4x)
 *  does not respond to local HTTP commands and is not supported.
 *
 * ============================================================================================
 *  Copyright 2018 Ben Dews - https://bendews.com
 *  Contribution by RBoy Apps
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ============================================================================================
 *
 *  Changelog:
 *
 *  0.1.0 — 2026-05-18 — initial fork of eriktack/hubitat-daikin-wifi with sentinel guard fix, supportedThermostatModes, initialize() lifecycle, throttled energy polling, HealthCheck
 *
 *  Upstream changelog (eriktack/hubitat-daikin-wifi):
 *  1.0.3   (2021-04-30) - Bug fixes for fan rate settings.
 *  1.0.2   (2020-12-15) - Cleanup fan rate and mode setting.
 *  1.0.1   (2020-10-21) - Fix bug with this year energy reporting.
 *  1.0     (2020-10-19) - Initial 1.0 Release.
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "0.1.0"

@Field final Map DAIKIN_MODES = [
    "0":    "auto",
    "1":    "auto",
    "2":    "dry",
    "3":    "cool",
    "4":    "heat",
    "6":    "fan",
    "7":    "auto",
    "off": "off",
]

@Field final Map DAIKIN_FAN_RATE = [
    "A":    "auto",
    "B":    "silent",
    "3":    "1",
    "4":    "2",
    "5":    "3",
    "6":    "4",
    "7":    "5"
]

@Field final Map DAIKIN_FAN_DIRECTION = [
    "0":    "Off",
    "1":    "Vertical",
    "2":    "Horizontal",
    "3":    "3D"
]

metadata {
    definition (name: "Daikin WiFi Thermostat", namespace: "mads", author: "Mads Kristensen") {
        capability "Thermostat"
        capability "Temperature Measurement"
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"
        capability "Initialize"
        capability "HealthCheck"
        capability "EnergyMeter"

        attribute "outsideTemp",       "number"
        attribute "targetTemp",        "number"
        attribute "currMode",          "string"
        attribute "fanAPISupport",     "string"
        attribute "fanRate",           "string"
        attribute "fanDirection",      "string"
        attribute "statusText",        "string"
        attribute "connection",        "string"
        attribute "energyToday",       "number"
        attribute "energyYesterday",   "number"
        attribute "energyThisYear",    "number"
        attribute "energyLastYear",    "number"
        attribute "energy12Months",    "number"
        attribute "healthStatus",      "enum",   ["online", "offline", "unknown"]
        attribute "lastActivity",      "string"

        command "fan"
        command "dry"
        command "tempUp"
        command "tempDown"
        command "fanRateAuto"
        command "fanRateSilent"
        command "fanDirectionVertical"
        command "fanDirectionHorizontal"
        command "setFanRate", ["number"]
        command "setTemperature", ["number"]
    }

    preferences {
        input("ipAddress",       "string",  title: "Daikin WiFi IP Address",                       required: true,  displayDuringSetup: true)
        input("ipPort",          "string",  title: "Daikin WiFi Port (default: 80)",                defaultValue: 80, required: true, displayDuringSetup: true)
        input("refreshInterval", "enum",    title: "Refresh Interval in minutes",                   defaultValue: "10", required: true, displayDuringSetup: true, options: ["1","5","10","15","30"])
        input("displayFahrenheit","boolean",title: "Display Fahrenheit",                            defaultValue: false, displayDuringSetup: true)
        input(name: "debugLogging", type: "bool", defaultValue: false, submitOnChange: true,
              title: "Enable debug logging\n<b>CAUTION:</b> a lot of log entries will be recorded!")
    }
}

// -------  Generic Private Functions -------

private getHostAddress() {
    def ip = settings.ipAddress
    def port = settings.ipPort
    return ip + ":" + port
}

private getDNI(String ipAddress, String port) {
    logDebug "Generating DNI"
    String ipHex   = ipAddress.tokenize('.').collect { String.format('%02X', it.toInteger()) }.join()
    String portHex = String.format('%04X', port.toInteger())
    return ipHex + ":" + portHex
}

private apiGet(def apiCommand) {
    logDebug "Executing hubaction on " + getHostAddress() + apiCommand
    sendEvent(name: "hubactionMode", value: "local")
    return new hubitat.device.HubAction(
        method: "GET",
        path: apiCommand,
        headers: [Host: getHostAddress()]
    )
}

private roundHalf(Double num) {
    return ((num * 2).round() / 2)
}

private convertTemp(Double temp, Boolean isFahrenheit) {
    logDebug "Converting ${temp}, Fahrenheit: ${isFahrenheit}"
    Double convertedTemp
    if (isFahrenheit) {
        convertedTemp = ((temp - 32) * 5) / 9
        return convertedTemp.round()
    }
    convertedTemp = ((temp * 9) / 5) + 32
    return convertedTemp.round()
}

// -------  Daikin-Specific Private Functions -------

private parseTemp(Double temp, String method) {
    logDebug "${method}-ing ${temp}"
    if (settings.displayFahrenheit.toBoolean()) {
        switch (method) {
            case "GET": return convertTemp(temp, false)
            case "SET": return convertTemp(temp, true)
        }
    }
    return temp
}

private parseDaikinResp(String response) {
    def parsedResponse = response.replace("=", "\":\"").replace(",", "\",\"")
    def jsonString = "{\"${parsedResponse}\"}"
    return new groovy.json.JsonSlurper().parseText(jsonString)
}

private updateDaikinDevice(Boolean turnOff = false) {
    def pow   = "?pow=1"
    def mode  = "&mode=3"
    def sTemp = "&stemp=26"
    def fRate = "&f_rate=A"
    def fDir  = "&f_dir=3"

    String modeAttr    = turnOff ? "currMode" : "thermostatMode"
    def currentMode    = device.currentState(modeAttr)?.value
    def currentModeKey = DAIKIN_MODES.find { it.value == currentMode }?.key

    def currentfRate    = device.currentState("fanRate")?.value
    def currentfRateKey = DAIKIN_FAN_RATE.find { it.value == currentfRate }?.key

    def currentfDir    = device.currentState("fanDirection")?.value
    def currentfDirKey = DAIKIN_FAN_DIRECTION.find { it.value == currentfDir }?.key
    logDebug "${currentfDirKey}"

    def targetTemp = parseTemp(device.currentValue("targetTemp"), "SET")

    if (turnOff) { pow = "?pow=0" }
    if (currentModeKey.isNumber()) { mode = "&mode=${currentModeKey}" }
    if (targetTemp) { sTemp = "&stemp=${targetTemp}" }
    if (currentfRateKey) { fRate = "&f_rate=${currentfRateKey}" }
    if (currentfDirKey)  { fDir  = "&f_dir=${currentfDirKey}" }

    // Energy endpoints removed from the update cycle — now polled separately by refreshEnergy() on a 30-min schedule.
    def apiCalls = [
        apiGet("/aircon/set_control_info" + pow + mode + sTemp + fRate + fDir + "&shum=0"),
        runIn(2, 'apiGet', [overwrite: false, data: "/aircon/get_control_info"]),
        runIn(4, 'apiGet', [overwrite: false, data: "/aircon/get_sensor_info"]),
    ]
    return apiCalls
}

// -------  Utility Functions -------

private startScheduledRefresh() {
    logDebug "startScheduledRefresh()"
    def minutes = settings.refreshInterval?.toInteger()
    if (!minutes) {
        log.warn "Using default refresh interval: 10"
        minutes = 10
    }
    logDebug "Scheduling polling task for every '${minutes}' minutes"
    if (minutes == 1) {
        runEvery1Minute(refresh)
    } else {
        "runEvery${minutes}Minutes"(refresh)
    }
}

def setDNI() {
    logDebug "Setting DNI"
    String ip   = settings.ipAddress
    String port = settings.ipPort
    device.setDeviceNetworkId(getDNI(ip, port))
}

private void ensureDefaultAttributes() {
    if (device.currentValue("healthStatus") == null) {
        sendEvent(name: "healthStatus", value: "unknown",
                  descriptionText: "${device.displayName} health status is unknown")
    }
    if (device.currentValue("lastActivity") == null) {
        sendEvent(name: "lastActivity", value: "",
                  descriptionText: "${device.displayName} no activity yet")
    }
}

// -------  Lifecycle -------

def initialize() {
    logDebug "initialize()"
    if (settings.ipAddress) { setDNI() }
    unschedule()
    state.pingPending      = false
    state.pingRequestedAt  = 0L
    ensureDefaultAttributes()
    startScheduledRefresh()
    // Fixed 30-minute energy poll — independent of the main refresh cadence.
    schedule("0 */30 * * * ?", refreshEnergy)
    refresh()
}

def updated() {
    logDebug "Updated with settings: ${settings}"
    if (!state.updated || now() >= state.updated + 5000) {
        sendEvent(name: "supportedThermostatModes",
                  value: ["auto","cool","heat","dry","fan","off"],
                  descriptionText: "${device.displayName} supported thermostat modes")
        sendEvent(name: "supportedThermostatFanModes",
                  value: ["auto","silent","1","2","3","4","5"],
                  descriptionText: "${device.displayName} supported thermostat fan modes")
        initialize()
    }
    state.updated = now()
}

def installed() {
    logDebug "installed()"
    sendEvent(name: 'heatingSetpoint',          value: '18')
    sendEvent(name: 'coolingSetpoint',           value: '28')
    sendEvent(name: 'temperature',               value: null)
    sendEvent(name: 'targetTemp',                value: null)
    sendEvent(name: 'thermostatOperatingState',  value: 'idle')
    sendEvent(name: 'outsideTemp',               value: null)
    sendEvent(name: 'currMode',                  value: null)
    sendEvent(name: 'thermostatMode',            value: null)
    sendEvent(name: 'thermostatFanMode',         value: null)
    sendEvent(name: 'fanRate',                   value: null)
    sendEvent(name: 'fanDirection',              value: null)
    sendEvent(name: 'fanState',                  value: null)
    sendEvent(name: 'energyToday',               value: null)
    sendEvent(name: 'energyYesterday',           value: null)
    sendEvent(name: 'energyThisYear',            value: null)
    sendEvent(name: 'energyLastYear',            value: null)
    sendEvent(name: 'energy12Months',            value: null)
    sendEvent(name: 'energy',                    value: null)
    sendEvent(name: "supportedThermostatModes",
              value: ["auto","cool","heat","dry","fan","off"],
              descriptionText: "${device.displayName} supported thermostat modes")
    sendEvent(name: "supportedThermostatFanModes",
              value: ["auto","silent","1","2","3","4","5"],
              descriptionText: "${device.displayName} supported thermostat fan modes")
    sendEvent(name: "healthStatus", value: "unknown",
              descriptionText: "${device.displayName} health status is unknown")
    sendEvent(name: "lastActivity",  value: "",
              descriptionText: "${device.displayName} no activity yet")
    initialize()
}

def poll() {
    logDebug "Executing poll(), unscheduling existing"
    refresh()
}

def refresh() {
    logDebug "Refreshing"
    // Energy endpoints are excluded here — refreshEnergy() handles those on a 30-min schedule.
    runIn(2, 'apiGet', [data: "/aircon/get_sensor_info"])
    runIn(4, 'apiGet', [overwrite: false, data: "/aircon/get_control_info"])
}

def refreshEnergy() {
    logDebug "refreshEnergy()"
    runIn(2, 'apiGet', [overwrite: false, data: "/aircon/get_week_power_ex"])
    runIn(4, 'apiGet', [overwrite: false, data: "/aircon/get_year_power_ex"])
}

// -------  Parse and Update Functions -------

def parse(String description) {
    def msg        = parseLanMessage(description)
    def body       = msg.body
    def daikinResp = parseDaikinResp(body)
    logDebug "Parsing Response: ${daikinResp}"

    def events     = []
    def turnedOff  = false
    def currMode   = null
    def modeVal    = null
    def targetTempVal = null

    def devicePower              = daikinResp.get("pow",            null)
    def deviceMode               = daikinResp.get("mode",           null)
    def deviceInsideTempSensor   = daikinResp.get("htemp",          null)
    def deviceOutsideTempSensor  = daikinResp.get("otemp",          null)
    def deviceTargetTemp         = daikinResp.get("stemp",          null)
    def devicefanRate            = daikinResp.get("f_rate",         null)
    def devicefanDirection       = daikinResp.get("f_dir",          null)
    def deviceFanSupport         = device.currentValue("fanAPISupport")

    // Energy fields
    def deviceWeekEnergyHeat  = daikinResp.get("week_heat",       null)
    def deviceWeekEnergyCool  = daikinResp.get("week_cool",       null)
    def deviceYear1EnergyHeat = daikinResp.get("curr_year_heat",  null)
    def deviceYear1EnergyCool = daikinResp.get("curr_year_cool",  null)
    def deviceYear2EnergyHeat = daikinResp.get("prev_year_heat",  null)
    def deviceYear2EnergyCool = daikinResp.get("prev_year_cool",  null)

    if (deviceWeekEnergyHeat) {
        // Values are in tenths of a kWh
        def deviceTodayEnergy     = deviceWeekEnergyHeat.split('/')[0].toInteger() + deviceWeekEnergyCool.split('/')[0].toInteger()
        def deviceYesterdayEnergy = deviceWeekEnergyHeat.split('/')[1].toInteger() + deviceWeekEnergyCool.split('/')[1].toInteger()
        events.add(createEvent(name: "energyToday",     value: deviceTodayEnergy / 10))
        events.add(createEvent(name: "energyYesterday", value: deviceYesterdayEnergy / 10))
        // Standard EnergyMeter attribute — energy apps discover today's kWh via this.
        events.add(createEvent(name: "energy",          value: deviceTodayEnergy / 10, unit: "kWh"))
    }

    if (deviceYear1EnergyHeat) {
        def y1h = deviceYear1EnergyHeat.split('/')
        def y1c = deviceYear1EnergyCool.split('/')
        def y2h = deviceYear2EnergyHeat.split('/')
        def y2c = deviceYear2EnergyCool.split('/')

        def thisYearEnergy  = 0.0
        def lastYearEnergy  = 0.0
        for (def i = 0; i < 12; i++) {
            thisYearEnergy += y1h[i].toInteger() + y1c[i].toInteger()
            lastYearEnergy += y2h[i].toInteger() + y2c[i].toInteger()
        }

        def twelveMonthEnergy = 0.0
        def thisMonth = new Date().getMonth()
        for (def i = thisMonth - 11; i <= thisMonth; i++) {
            if (i >= 0) {
                twelveMonthEnergy += y1h[i].toInteger() + y1c[i].toInteger()
            } else {
                twelveMonthEnergy += y2h[i + 12].toInteger() + y2c[i + 12].toInteger()
            }
        }

        events.add(createEvent(name: "energyThisYear",  value: thisYearEnergy / 10))
        events.add(createEvent(name: "energyLastYear",  value: lastYearEnergy / 10))
        events.add(createEvent(name: "energy12Months",  value: twelveMonthEnergy / 10))
    }

    // Power
    if (devicePower) {
        if (devicePower == "0") {
            turnedOff = true
            events.add(createEvent(name: "thermostatMode", value: "off"))
        }
    }

    // Mode
    if (deviceMode) {
        currMode = DAIKIN_MODES.get(deviceMode.toString())
        if (!turnedOff) { modeVal = currMode }
        events.add(createEvent(name: "currMode", value: currMode))
    }

    // Indoor temperature — guarded against "-" sentinel (fixes NumberFormatException on otemp/htemp="-")
    if (deviceInsideTempSensor?.isNumber()) {
        String insideTemp = parseTemp(Double.parseDouble(deviceInsideTempSensor), "GET")
        emitIfChanged("temperature", insideTemp, "${device.displayName} temperature → ${insideTemp}°")
    }

    // Outdoor temperature — same sentinel guard
    if (deviceOutsideTempSensor?.isNumber()) {
        String outsideTemp = parseTemp(Double.parseDouble(deviceOutsideTempSensor), "GET")
        emitIfChanged("outsideTemp", outsideTemp, "${device.displayName} outside temperature → ${outsideTemp}°")
    }

    // Target temperature setpoint (already correctly guarded in upstream)
    if (deviceTargetTemp) {
        targetTempVal = deviceTargetTemp.isNumber() ? parseTemp(Double.parseDouble(deviceTargetTemp), "GET") : null
    }

    // Fan rate
    if (devicefanRate) {
        events.add(createEvent(name: "fanAPISupport",     value: "true", displayed: false))
        events.add(createEvent(name: "fanRate",           value: DAIKIN_FAN_RATE.get(devicefanRate).toString()))
        events.add(createEvent(name: "thermostatFanMode", value: DAIKIN_FAN_RATE.get(devicefanRate).toString()))
    }

    // Fan direction
    if (devicefanDirection) {
        events.add(createEvent(name: "fanDirection", value: DAIKIN_FAN_DIRECTION.get(devicefanDirection)))
    }

    if (deviceMode && !devicefanRate) {
        events.add(createEvent(name: "fanAPISupport", value: "false", displayed: false))
    }

    if (modeVal || targetTempVal) {
        events.add(0, updateEvents(mode: modeVal, temperature: targetTempVal, updateDevice: false))
    }

    // HealthCheck + lastActivity — throttled to ≥60s per event-hygiene skill
    touchActivity()
    if (state.pingPending == true) {
        state.pingPending = false
        sendEvent(name: "healthStatus", value: "online",
                  descriptionText: "${device.displayName} responded to ping")
    } else if ((device.currentValue("healthStatus") ?: "unknown") != "online") {
        sendEvent(name: "healthStatus", value: "online",
                  descriptionText: "${device.displayName} health status is online")
    }

    return events
}

private updateEvents(Map args) {
    logDebug "Executing 'updateEvents' with ${args.mode}, ${args.temperature} and ${args.updateDevice}"
    def mode        = args.get("mode",         null)
    def temperature = args.get("temperature",  null)
    def updateDevice = args.get("updateDevice", false)
    Boolean turnOff = false
    def events = []

    if (!mode) {
        mode = device.currentValue("thermostatMode")
    } else {
        events.add(sendEvent(name: "thermostatMode", value: mode))
    }
    if (!temperature) {
        temperature = device.currentValue("targetTemp")
    }

    switch (mode) {
        case "fan":
            events.add(sendEvent(name: "statusText",            value: "Fan Mode",     displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "fan only",  displayed: false))
            events.add(sendEvent(name: "targetTemp",            value: null))
            break
        case "dry":
            events.add(sendEvent(name: "statusText",            value: "Dry Mode",     displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "fan only",  displayed: false))
            events.add(sendEvent(name: "targetTemp",            value: null))
            break
        case "heat":
            events.add(sendEvent(name: "statusText",            value: "Heating to ${temperature}°", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "heating",   displayed: false))
            events.add(sendEvent(name: "heatingSetpoint",       value: temperature,    displayed: false))
            events.add(sendEvent(name: "targetTemp",            value: temperature))
            break
        case "cool":
            events.add(sendEvent(name: "statusText",            value: "Cooling to ${temperature}°", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "cooling",   displayed: false))
            events.add(sendEvent(name: "coolingSetpoint",       value: temperature,    displayed: false))
            events.add(sendEvent(name: "targetTemp",            value: temperature))
            break
        case "auto":
            events.add(sendEvent(name: "statusText",            value: "Auto Mode: ${temperature}°", displayed: false))
            events.add(sendEvent(name: "targetTemp",            value: temperature))
            break
        case "off":
            events.add(sendEvent(name: "statusText",            value: "System is off", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "idle",       displayed: false))
            turnOff = true
            break
    }

    if (turnOff) {
        events.add(sendEvent(name: "switch", value: "off", displayed: false))
    } else {
        events.add(sendEvent(name: "switch", value: "on",  displayed: false))
    }

    if (updateDevice) {
        runIn(1, 'updateDaikinDevice', [data: turnOff])
    }
}

// -------  Temperature Functions -------

def tempUp() {
    logDebug "tempUp()"
    def step       = 0.5
    def targetTemp = device.currentValue("targetTemp")
    updateEvents(temperature: targetTemp + step, updateDevice: true)
}

def tempDown() {
    logDebug "tempDown()"
    def step       = 0.5
    def targetTemp = device.currentValue("targetTemp")
    updateEvents(temperature: targetTemp - step, updateDevice: true)
}

def setThermostatMode(String newMode) {
    logDebug "Executing 'setThermostatMode'"
    def currMode = device.currentValue("thermostatMode")
    if (currMode != newMode) {
        updateEvents(mode: newMode, updateDevice: true)
    }
}

def setTemperature(Double value) {
    logDebug "Executing 'setTemperature' with ${value}"
    updateEvents(temperature: value, updateDevice: true)
}

def setHeatingSetpoint(Double value) {
    logDebug "Executing 'setHeatingSetpoint' with ${value}"
    updateEvents(temperature: value, updateDevice: true)
}

def setCoolingSetpoint(Double value) {
    logDebug "Executing 'setCoolingSetpoint' with ${value}"
    updateEvents(temperature: value, updateDevice: true)
}

// -------  Daikin Modes -------

def auto() {
    logDebug "Executing 'auto'"
    updateEvents(mode: "auto", updateDevice: true)
}

def dry() {
    logDebug "Executing 'dry'"
    updateEvents(mode: "dry", updateDevice: true)
}

def cool() {
    logDebug "Executing 'cool'"
    def coolPoint = device.currentValue("coolingSetpoint")
    updateEvents(mode: "cool", temperature: coolPoint, updateDevice: true)
}

def heat() {
    logDebug "Executing 'heat'"
    def heatPoint = device.currentValue("heatingSetpoint")
    updateEvents(mode: "heat", temperature: heatPoint, updateDevice: true)
}

def fan() {
    logDebug "Executing 'fan'"
    updateEvents(mode: "fan", updateDevice: true)
}

// -------  Switch Functions -------

def on() {
    logDebug "Executing 'on'"
    def currMode = device.currentValue("currMode")
    updateEvents(mode: currMode, updateDevice: true)
}

def off() {
    logDebug "Executing 'off'"
    updateEvents(mode: "off", updateDevice: true)
}

// -------  Fan Actions -------

private fanAPISupported() {
    String deviceFanSupport = device.currentValue("fanAPISupport")
    if (deviceFanSupport == "false") {
        logDebug "Fan settings not supported on this model"
        sendEvent(name: "fanDirection", value: "Not Supported")
        return false
    }
    return true
}

def setFanRate(def fanRate) {
    logDebug "Executing 'setFanRate' with ${fanRate}"
    def currFanRate = device.currentValue("fanRate")
    if (currFanRate != fanRate) {
        if (fanAPISupported()) {
            switch (fanRate) {
                case "silent":
                case "1":
                case "2":
                case "3":
                case "4":
                case "5":
                    sendEvent(name: "fanRate",           value: fanRate.toString())
                    sendEvent(name: "thermostatFanMode", value: fanRate.toString(), displayed: false)
                    break
                case "auto":
                case "0":
                    sendEvent(name: "fanRate",           value: "auto")
                    sendEvent(name: "thermostatFanMode", value: "auto", displayed: false)
                    break
                default:
                    sendEvent(name: "thermostatFanMode", value: "silent", displayed: false)
                    break
            }
            runIn(1, 'updateDaikinDevice', [data: false])
        } else {
            sendEvent(name: "fanRate", value: "Not Supported")
        }
    }
}

def fanRateAuto() {
    logDebug "Executing 'fanRateAuto'"
    setFanRate("auto")
}

def fanRateSilent() {
    logDebug "Executing 'fanRateSilent'"
    setFanRate("silent")
}

def toggleFanDirection(String toggleDir) {
    logDebug "Executing 'toggleFanDirection' with ${toggleDir}"
    String currentDir = device.currentValue("fanDirection")

    if (currentDir == "Off") {
        sendEvent(name: "fanDirection", value: toggleDir)
    } else if (currentDir == "3D") {
        String newDir = toggleDir == "Horizontal" ? "Vertical" : "Horizontal"
        sendEvent(name: "fanDirection", value: newDir)
    } else if (currentDir != toggleDir) {
        sendEvent(name: "fanDirection", value: "3D")
    } else if (currentDir == toggleDir) {
        sendEvent(name: "fanDirection", value: "Off")
    }

    if (fanAPISupported()) {
        runIn(1, 'updateDaikinDevice', [data: false])
    } else {
        sendEvent(name: "fanDirection", value: "Not Supported")
    }
}

def fanDirectionHorizontal() {
    logDebug "Executing 'fanDirectionHorizontal'"
    toggleFanDirection("Horizontal")
}

def fanDirectionVertical() {
    logDebug "Executing 'fanDirectionVertical'"
    toggleFanDirection("Vertical")
}

def fanOn() {
    logDebug "Executing 'fanOn'"
    fanRateSilent()
}

def fanAuto() {
    logDebug "Executing 'fanAuto'"
    fanRateAuto()
}

def fanCirculate() {
    log.warn "Executing 'fanCirculate' not currently supported"
}

def setThermostatFanMode(String value) {
    String val = value.trim()
    logDebug "Executing 'setThermostatFanMode' with fan mode '${val}'"
    switch (val) {
        case "auto":      fanAuto();       break
        case "on":        fanOn();         break
        case "circulate": fanCirculate();  break
        case "silent":
        case "1":
        case "2":
        case "3":
        case "4":
        case "5":
            setFanRate(val)
            break
        default:
            log.warn "Unknown fan mode: ${value}"
            break
    }
}

// def setSchedule() {
//     logDebug "Executing 'setSchedule'"
//     // TODO: handle 'setSchedule' command
// }

// -------  HealthCheck -------

def ping() {
    log.info "${device.displayName} ping() requested"
    state.pingPending     = true
    state.pingRequestedAt = now()
    unschedule("pingTimeout")
    runIn(5, "pingTimeout")
    // Return a HubAction — Hubitat sends it immediately, response arrives in parse()
    return apiGet("/aircon/get_sensor_info")
}

def pingTimeout() {
    if (state.pingPending != true) { return }
    log.warn "[Daikin] ping timed out — no response from device within 5s"
    sendEvent(name: "healthStatus", value: "offline",
              descriptionText: "${device.displayName} did not respond to ping within 5s")
    state.pingPending = false
}

// -------  Helpers -------

private void touchActivity() {
    Long lastEmittedAt = (state.lastActivityEmittedAt ?: 0L) as Long
    if ((now() - lastEmittedAt) < 60000L) { return }
    state.lastActivityEmittedAt = now()
    String ts = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")
    sendEvent(name: "lastActivity", value: ts,
              descriptionText: "${device.displayName} last activity")
}

private void emitIfChanged(String name, value, String descTxt, String unit = null) {
    def current = device.currentValue(name)
    boolean changed
    if (current != null && value != null) {
        try {
            changed = new BigDecimal(current.toString()) != new BigDecimal(value.toString())
        } catch (Exception e) {
            changed = current?.toString() != value?.toString()
        }
    } else {
        changed = current != value
    }
    if (!changed) { return }
    Map evt = [name: name, value: value, descriptionText: descTxt]
    if (unit) { evt.unit = unit }
    sendEvent(evt)
}

// -------  Logging -------

def logDebug(message) { if (debugLogging) log.debug(message) }
def logInfo(message)  { log.info(message) }
def logWarn(message)  { log.warn(message) }
