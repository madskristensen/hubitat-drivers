/**
 *  Daikin WiFi Split System Hubitat
 *  V 1.0.3 - 2021-04-30
 *
 *  This is a port of the Smartthings daikin ac controller code by Ben Dews, the code is
 *  based on the modifications made by https://community.hubitat.com/u/tsaaek in this thread:
 *  https://community.hubitat.com/t/a-c-control-daikin-mobile-controller/38911/28
 *
 *  I added a few energy reports from /aircon/get_year_power_ex and /aircon/get_week_power_ex: 
 *  today, yesterday, this year, last year and last 12 months
 *  
 *  There is more information about the DAikin API in the repo this file came from, see daikinapi.md
 *
 *  NOTE: You need a B series wifi module to use this integration. The module series can be found in
 *  the model number, it should be BRP069B4x, where the letter after BRP069 denote series. If the 
 *  module number is BRP069C4x, you have the new cloud connected module which will not respond to
 *  any of the commands sent by this integration.
 *  
 *  Here is some legal mumbo-jumbo everyone that throws a piece of code on the internet is 
 *  overly fond of including, even though noone actually gives a crap as long as their 
 *  stuff works:
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
 *  Ok, with that out of the way, do whatever the hell you want with this code as far as I'm concerned.
 *  I take no responsibility for anything that happens if you use this code.
 *  /Erik
 *
 *	Changelog:
 *
 *  1.0     (2020-10-19) - Initial 1.0 Release. Forked from SmartThings Daikin WiFi Split System and added 
 *                         energy reports for today, yesterday, this year, last year and last 12 months.
 *  1.0.1   (2020-10-21) - Fix bug with this year energy reporting, it was grabbing the cooling value from last year.
 *
 *  1.0.2   (2020-12-15) - Cleanup fan rate and mode setting, it now support auto, silent and level 1-5. 
 *                         Save precferences once to get the new values in your thermostat dashboard widgets.
 *
 *  1.0.3   (2021-04-30) - Bug fixes for fan rate settings, the setting get applied more consistently if sending 
 *                         it as string instead of a number.
 */

import groovy.transform.Field

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
    definition (name: "Daikin WiFi Split System Hubitat", namespace: "bendews", author: "contact@bendews.com") {
        capability "Thermostat"
        capability "Temperature Measurement"
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"

        attribute "outsideTemp", "number"
        attribute "targetTemp", "number"
        attribute "currMode", "string"
        attribute "fanAPISupport", "string"
        attribute "fanRate", "string"
        attribute "fanDirection", "string"
        attribute "statusText", "string"
        attribute "connection", "string"
        attribute "energyToday", "number"
        attribute "energyYesterday", "number"
        attribute "energyThisYear", "number"
        attribute "energyLastYear", "number"
        attribute "energy12Months", "number"
        
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
        input("ipAddress", "string", title:"Daikin WiFi IP Address", required:true, displayDuringSetup:true)
        input("ipPort", "string", title:"Daikin WiFi Port (default: 80)", defaultValue:80, required:true, displayDuringSetup:true)
        input("refreshInterval", "enum", title: "Refresh Interval in minutes", defaultValue: "10", required:true, displayDuringSetup:true, options: ["1","5","10","15","30"])
        input("displayFahrenheit", "boolean", title: "Display Fahrenheit", defaultValue: false, displayDuringSetup:true)
        /* 2021-02-19 - logDebug use */
        input (name: "debugLogging", type: "bool", defaultValue: false, submitOnChange: true, title: "Enable debug logging\n<b>CAUTION:</b> a lot of log entries will be recorded!")			                    
    }

}

// Generic Private Functions -------
private getHostAddress() {
    def ip = settings.ipAddress
    def port = settings.ipPort
    return ip + ":" + port
}

private getDNI(String ipAddress, String port){
    logDebug "Generating DNI"
    String ipHex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    String portHex = String.format( '%04X', port.toInteger() )
    String newDNI = ipHex + ":" + portHex
    return newDNI
}

private apiGet(def apiCommand) {
    logDebug "Executing hubaction on " + getHostAddress() + apiCommand
    sendEvent(name: "hubactionMode", value: "local")

    def hubAction = new hubitat.device.HubAction(
        method: "GET",
        path: apiCommand,
        headers: [Host:getHostAddress()]
    )
    return hubAction
}

private roundHalf(Double num){
    return ((num * 2).round() / 2)
}

private convertTemp(Double temp, Boolean isFahrenheit){
    logDebug "Converting ${temp}, Fahrenheit: ${isFahrenheit}"
    Double convertedTemp
    if (isFahrenheit) {
        convertedTemp = ((temp - 32) * 5) / 9
        return convertedTemp.round()
    }
    convertedTemp = ((temp * 9) / 5) + 32
    return convertedTemp.round()
}

// -------


// Daikin Specific Private Functions -------
private parseTemp(Double temp, String method){
    logDebug "${method}-ing ${temp}"
    if (settings.displayFahrenheit.toBoolean()) {
        switch(method) {
            case "GET":
                return convertTemp(temp, false)
            case "SET":
                return convertTemp(temp, true)
        }
    }
    return temp
}
private parseDaikinResp(String response) {
    // Convert Daikin response to Groovy Map
    // Convert to JSON
    def parsedResponse = response.replace("=", "\":\"").replace(",", "\",\"")
    def jsonString = "{\"${parsedResponse}\"}"
    // Parse JSON to Map
    def results = new groovy.json.JsonSlurper().parseText(jsonString)  
    return results
}

private updateDaikinDevice(Boolean turnOff = false){
    // "Power", either 0 or 1
    def pow = "?pow=1"
    // "Mode", 0, 1, 2, 3, 4, 6 or 7
    def mode = "&mode=3"
    // "Set Temperature", degrees in Celsius
    def sTemp = "&stemp=26"
    // "Fan Rate", A, B, 3, 4, 5, 6, or 7
    def fRate = "&f_rate=A"
    // "Fan Direction", 0, 1, 2, or 3
    def fDir = "&f_dir=3"

    // Current mode selected in smartthings
    // If turning unit off, get current mode of unit instead of desired mode
    String modeAttr = turnOff ? "currMode" : "thermostatMode"
    def currentMode = device.currentState(modeAttr)?.value
    // Convert textual mode (e.g "cool") to Daikin Code (e.g "3")
    def currentModeKey = DAIKIN_MODES.find{ it.value == currentMode }?.key

    // Current fan rate selected in smartthings
    def currentfRate = device.currentState("fanRate")?.value
    // Convert textual fan rate (e.g "lvl1") to Daikin Code (e.g "3")
    def currentfRateKey = DAIKIN_FAN_RATE.find{ it.value == currentfRate }?.key

    // Current fan direction selected in smartthings
    def currentfDir = device.currentState("fanDirection")?.value
    // Convert textual fan direction (e.g "3d") to Daikin Code (e.g "3")
    def currentfDirKey = DAIKIN_FAN_DIRECTION.find{ it.value == currentfDir }?.key
    logDebug "${currentfDirKey}"
    
    // Get target temperature set in Smartthings
    def targetTemp = parseTemp(device.currentValue("targetTemp"), "SET")

    // Set power mode in HTTP call
    if (turnOff) {
        pow = "?pow=0"
    }
    if (currentModeKey.isNumber()){
        // Set desired mode in HTTP call
        mode = "&mode=${currentModeKey}"  
    }
    if (targetTemp){
        // Set desired Target Temperature in HTTP call
        sTemp = "&stemp=${targetTemp}"
    }
    if (currentfRateKey){
        // Set desired Fan Rate in HTTP call
        fRate = "&f_rate=${currentfRateKey}"
    }
    if (currentfDirKey){
        // Set desired Fan Direction in HTTP call
        fDir = "&f_dir=${currentfDirKey}"
    }

    def apiCalls = [
        // Send HTTP Call to update device
        apiGet("/aircon/set_control_info"+pow+mode+sTemp+fRate+fDir+"&shum=0"),
        						    
        // Get mode info
	runIn(2, 'apiGet', [overwrite: false, data : "/aircon/get_control_info"]),
        
        // Get temperature info
	runIn(4, 'apiGet', [overwrite: false, data : "/aircon/get_sensor_info"]),
        
    // Get power info, prev 2 weeks
	runIn(6, 'apiGet', [overwrite: false, data : "/aircon/get_week_power_ex"]),
        
    // Get power info, this year & last year
    runIn(8, 'apiGet', [overwrite : false, data : "/aircon/get_year_power_ex"])
    ]
    return apiCalls
}
// -------


// Utility Functions -------
private startScheduledRefresh() {
    logDebug "startScheduledRefresh()"
    // Get minutes from settings
    def minutes = settings.refreshInterval?.toInteger()
    if (!minutes) {
        log.warn "Using default refresh interval: 10"
        minutes = 10
    }
    logDebug "Scheduling polling task for every '${minutes}' minutes"
    if (minutes == 1){
        runEvery1Minute(refresh)
    } else {
        "runEvery${minutes}Minutes"(refresh)
    }
}

def setDNI(){
    logDebug "Setting DNI"
    String ip = settings.ipAddress
    String port = settings.ipPort
    String newDNI = getDNI(ip, port)
    device.setDeviceNetworkId("${newDNI}")
}

def updated() {
    logDebug "Updated with settings: ${settings}"
    // Prevent function from running twice on save
    if (!state.updated || now() >= state.updated + 5000){
        // Unschedule existing tasks
        unschedule()
        // Set DNI
	    runIn(1, 'setDNI',  [overwrite : false])
        runIn(5, 'refresh')
        // Start scheduled task
        startScheduledRefresh()
    }
    
    // this only need to be set once, but might be a select list in the future
    sendEvent(name: "supportedThermostatFanModes", value: ["auto","silent","1","2","3","4","5"], displayed: false)

    state.updated = now()
}

def poll() {
    logDebug "Executing poll(), unscheduling existing"
    refresh()
}

def refresh() {
    logDebug "Refreshing"
	runIn(2, 'apiGet', [data:"/aircon/get_sensor_info"])
    
    runIn(4, 'apiGet', [overwrite : false, data : "/aircon/get_control_info"])

    runIn(6, 'apiGet', [overwrite : false, data : "/aircon/get_week_power_ex"])
    
    runIn(8, 'apiGet', [overwrite : false, data : "/aircon/get_year_power_ex"])
}

def installed() {
    logDebug "installed()"
    sendEvent(name:'heatingSetpoint', value: '18', displayed:false)
    sendEvent(name:'coolingSetpoint', value: '28', displayed:false)
    sendEvent(name:'temperature', value: null, displayed:false)
    sendEvent(name:'targetTemp', value: null, displayed:false)
    sendEvent(name:'thermostatOperatingState', value:'idle', displayed:false)
    sendEvent(name:'outsideTemp', value: null, displayed:false)
    sendEvent(name:'currMode', value: null, displayed:false)
    sendEvent(name:'thermostatMode', value: null, displayed:false)
    sendEvent(name:'thermostatFanMode', value: null, displayed:false)
    sendEvent(name:'fanRate', value: null, displayed:false)
    sendEvent(name:'fanDirection', value: null, displayed:false)
    sendEvent(name:'fanState', value: null, displayed:false)
    sendEvent(name:'energyToday', value: null, displayed:false)
    sendEvent(name:'energyYesterday', value: null, displayed:false)
    sendEvent(name:'energyThisYear', value: null, displayed:false)
    sendEvent(name:'energyLastYear', value: null, displayed:false)
    sendEvent(name:'energy12Months', value: null, displayed:false)
}
// -------


// Parse and Update functions
def parse(String description) {
    // Parse Daikin response
    def msg = parseLanMessage(description)
    def body = msg.body
    def daikinResp = parseDaikinResp(body)
    // Debug Response
    logDebug "Parsing Response: ${daikinResp}"
    // Custom definitions
    def events = []
    def turnedOff = false
    def currMode = null
    def modeVal = null
    def targetTempVal = null
    // Define return field data we are interested in
    def devicePower = daikinResp.get("pow", null)
    def deviceMode = daikinResp.get("mode", null)
    def deviceInsideTempSensor = daikinResp.get("htemp", null)
    def deviceOutsideTempSensor = daikinResp.get("otemp", null)
    def deviceTargetTemp = daikinResp.get("stemp", null)
    def devicefanRate = daikinResp.get("f_rate", null)
    def devicefanDirection = daikinResp.get("f_dir", null)
    def deviceFanSupport = device.currentValue("fanAPISupport")
    
    //energy report
    def deviceWeekEnergyHeat = daikinResp.get("week_heat", null)
    def deviceWeekEnergyCool = daikinResp.get("week_cool", null)
    
    def deviceYear1EnergyHeat = daikinResp.get("curr_year_heat", null)
    def deviceYear1EnergyCool = daikinResp.get("curr_year_cool", null)
    def deviceYear2EnergyHeat = daikinResp.get("prev_year_heat", null)
    def deviceYear2EnergyCool = daikinResp.get("prev_year_cool", null)
    
    // if heat is available, cool is as well
    if(deviceWeekEnergyHeat){
        //values are in thenths of a kWh
        def deviceTodayEnergy = deviceWeekEnergyHeat.split('/')[0].toInteger() + deviceWeekEnergyCool.split('/')[0].toInteger()
        def deviceYesterdayEnergy = deviceWeekEnergyHeat.split('/')[1].toInteger() + deviceWeekEnergyCool.split('/')[1].toInteger()
        events.add(createEvent(name: "energyToday", value: deviceTodayEnergy/10))
        events.add(createEvent(name: "energyYesterday", value: deviceYesterdayEnergy/10))
    }
    
    // if heat for year 1 is available, all other year values are as well
    if(deviceYear1EnergyHeat){
        def y1h = deviceYear1EnergyHeat.split('/')
        def y1c = deviceYear1EnergyCool.split('/')
        def y2h = deviceYear2EnergyHeat.split('/')
        def y2c = deviceYear2EnergyCool.split('/')
        
        def thisYearEnergy = 0.0
        def lastYearEnergy = 0.0
        
        for(def i=0; i<12;i++){
            thisYearEnergy += y1h[i].toInteger() + y1c[i].toInteger()
            lastYearEnergy += y2h[i].toInteger() + y2c[i].toInteger()
        }
        
        def twelveMonthEnergy = 0.0
        def thisMonth = new Date().getMonth()
        for(def i=thisMonth-11; i<=thisMonth;i++){
            if(i >= 0){
                twelveMonthEnergy += y1h[i].toInteger() + y1c[i].toInteger()
            } else{ 
                twelveMonthEnergy += y2h[i+12].toInteger() + y2c[i+12].toInteger()
            }
        }

        //values are in thenths of a kWh
        events.add(createEvent(name: "energyThisYear", value: thisYearEnergy/10))
        events.add(createEvent(name: "energyLastYear", value: lastYearEnergy/10))
        events.add(createEvent(name: "energy12Months", value: twelveMonthEnergy/10))
    }
    

    //  Get power info
    if (devicePower){
        // logDebug "pow: ${devicePower}"
        if (devicePower == "0") {
            turnedOff = true
            events.add(createEvent(name: "thermostatMode", value: "off"))
        }  
    }
    //  Get mode info
    if (deviceMode){
        // logDebug "mode: ${deviceMode}"
        currMode = DAIKIN_MODES.get(deviceMode.toString())
        if (!turnedOff) {
            modeVal = currMode
        }
        events.add(createEvent(name: "currMode", value: currMode))
    }
    //  Get inside temperature sensor info
    if (deviceInsideTempSensor){
        // logDebug "htemp: ${deviceInsideTempSensor}"
        String insideTemp = parseTemp(Double.parseDouble(deviceInsideTempSensor), "GET")
        events.add(createEvent(name: "temperature", value: insideTemp))
    }
    //  Get outside temperature sensor info
    if (deviceOutsideTempSensor){
        // logDebug "otemp: ${deviceOutsideTempSensor}"
        String outsideTemp = parseTemp(Double.parseDouble(deviceOutsideTempSensor), "GET")
        events.add(createEvent(name: "outsideTemp", value: outsideTemp))
    }
    //  Get currently set target temperature
    if (deviceTargetTemp){
        // logDebug "stemp: ${deviceTargetTemp}"
        // Value of "M" is for modes that don't support temperature changes, make value null
        targetTempVal = deviceTargetTemp.isNumber() ? parseTemp(Double.parseDouble(deviceTargetTemp), "GET") : null
    }
    //  Get current fan rate
    if (devicefanRate){
        // logDebug "f_rate: ${devicefanRate}"
        events.add(createEvent(name: "fanAPISupport", value: "true", displayed: false))
        events.add(createEvent(name: "fanRate", value: DAIKIN_FAN_RATE.get(devicefanRate).toString()))
        events.add(createEvent(name: "thermostatFanMode", value: DAIKIN_FAN_RATE.get(devicefanRate).toString()))
    }
    //  Get current fan direction
    if (devicefanDirection){
        // logDebug "f_dir: ${devicefanDirection}"
        events.add(createEvent(name: "fanDirection", value: DAIKIN_FAN_DIRECTION.get(devicefanDirection)))
    }
    // If device doesnt support API Fan functions
    if (deviceMode && !devicefanRate){
        // logDebug "Fan support: False"
        events.add(createEvent(name: "fanAPISupport", value: "false", displayed: false))
    }
    
    // Update temperature and mode values if applicable (and all other values based from that)
    // Add to start of returned list for faster UI feedback
    if (modeVal || targetTempVal){
        events.add(0, updateEvents(mode: modeVal, temperature: targetTempVal, updateDevice: false))
    }

    return events
}

private updateEvents(Map args){
    logDebug "Executing 'updateEvents' with ${args.mode}, ${args.temperature} and ${args.updateDevice}"
    // Get args with default values
    def mode = args.get("mode", null)
    def temperature = args.get("temperature", null)
    def updateDevice = args.get("updateDevice", false)
    // Daikin "Off" mode is handled as seperate attribute
    // Smarthings thermostat handles "Off" as another mode
    // Work around this by defining a "turnOff" boolean and set where appropiate
    Boolean turnOff = false
    def events = []
    if (!mode){
        mode = device.currentValue("thermostatMode")
    } else {
        events.add(sendEvent(name: "thermostatMode", value: mode))   
    }
    if (!temperature){
        temperature = device.currentValue("targetTemp")
    }
    switch(mode) {
        case "fan":
            events.add(sendEvent(name: "statusText", value: "Fan Mode", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "fan only", displayed: false))
            events.add(sendEvent(name: "targetTemp", value: null))
            break
        case "dry":
            events.add(sendEvent(name: "statusText", value: "Dry Mode", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "fan only", displayed: false))
            events.add(sendEvent(name: "targetTemp", value: null))
            break
        case "heat":
            events.add(sendEvent(name: "statusText", value: "Heating to ${temperature}°", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "heating", displayed: false))
            events.add(sendEvent(name: "heatingSetpoint", value: temperature, displayed: false))
            events.add(sendEvent(name: "targetTemp", value: temperature))
            break
        case "cool":
            events.add(sendEvent(name: "statusText", value: "Cooling to ${temperature}°", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "cooling", displayed: false))
            events.add(sendEvent(name: "coolingSetpoint", value: temperature, displayed: false))
            events.add(sendEvent(name: "targetTemp", value: temperature))
            break
        case "auto":
            events.add(sendEvent(name: "statusText", value: "Auto Mode: ${temperature}°", displayed: false))
            events.add(sendEvent(name: "targetTemp", value: temperature))
            break
        case "off":
            events.add(sendEvent(name: "statusText", value: "System is off", displayed: false))
            events.add(sendEvent(name: "thermostatOperatingState", value: "idle", displayed: false))
            turnOff = true
            break
    }

    if (turnOff){
        events.add(sendEvent(name: "switch", value: "off", displayed: false))
    } else {
        events.add(sendEvent(name: "switch", value: "on", displayed: false))
    }

    if (updateDevice){	
	    runIn(1, 'updateDaikinDevice', [ data : turnOff])    
    }

}
// -------


// Temperature Functions
def tempUp() {
    logDebug "tempUp()"
    def step = 0.5
    def mode = device.currentValue("thermostatMode")
    def targetTemp = device.currentValue("targetTemp")
    def value = targetTemp + step
    updateEvents(temperature: value, updateDevice: true)
}

def tempDown() {
    logDebug "tempDown()"
    def step = 0.5
    def mode = device.currentValue("thermostatMode")
    def targetTemp = device.currentValue("targetTemp")
    def value = targetTemp - step
    updateEvents(temperature: value, updateDevice: true)
}

def setThermostatMode(String newMode) {
    logDebug "Executing 'setThermostatMode'"
    def currMode = device.currentValue("thermostatMode")
    if (currMode != newMode){
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
// -------


// Daikin "Modes" ----
def auto(){
    logDebug "Executing 'auto'"
    updateEvents(mode: "auto", updateDevice: true)
}

def dry() {
    logDebug "Executing 'dry'"
    updateEvents(mode: "dry", updateDevice: true)
}

def cool() {
    logDebug "Executing 'cool'"
    // Set target temp to previously set Cool temperature
    def coolPoint = device.currentValue("coolingSetpoint")
    updateEvents(mode: "cool", temperature: coolPoint, updateDevice: true)
}

def heat() {
    logDebug "Executing 'heat'"
    // Set target temp to previously set Heat temperature
    def heatPoint = device.currentValue("heatingSetpoint")
    updateEvents(mode: "heat", temperature: heatPoint, updateDevice: true)
}

def fan() {
    logDebug "Executing 'fan'"
    updateEvents(mode: "fan", updateDevice: true)
}
// -------


// Switch Functions ----
def on(){
    logDebug "Executing 'on'"
    def currMode = device.currentValue("currMode")
    updateEvents(mode: currMode, updateDevice: true)
}

def off() {
    logDebug "Executing 'off'"
    updateEvents(mode: "off", updateDevice: true)
}
// -------


// Fan Actions ----
private fanAPISupported() {
    // Returns boolean based on whether API Fan actions are supported by the model
    String deviceFanSupport = device.currentValue("fanAPISupport")
    if (deviceFanSupport == "false"){
        logDebug "Fan settings not supported on this model"
        sendEvent(name: "fanDirection", value: "Not Supported")
        return false
    } else {
        return true
    }
}

def setFanRate(def fanRate) {
    logDebug "Executing 'setFanRate' with ${fanRate}"
    def currFanRate = device.currentValue("fanRate")
    // Check that rate is different before setting.
	if (currFanRate != fanRate){
		if (fanAPISupported()){
				switch (fanRate) {
                    case "silent":                  
                    case "1":
                    case "2":
                    case "3":
                    case "4":
                    case "5":
        				sendEvent(name: "fanRate", value: fanRate.toString())
                        sendEvent(name: "thermostatFanMode", value: fanRate.toString(), displayed: false)
                        break
                    
                    case "auto":
                    case "0":
                        sendEvent(name: "fanRate", value: "auto")
                        sendEvent(name: "thermostatFanMode", value: "auto", displayed: false)
                        break
                    
					default: // all other modes indicate fan rate silent setting
						sendEvent(name: "thermostatFanMode", value: "silent", displayed: false)
						break
				}
			runIn(1, 'updateDaikinDevice', [ data : false])
		} else {
			sendEvent(name: "fanRate", value: "Not Supported")
		}
	}
}

def fanRateAuto(){
    logDebug "Executing 'fanRateAuto'"
    setFanRate("auto")
}

def fanRateSilent(){
    logDebug "Executing 'fanRateSilent'"
    setFanRate("silent")
}

def toggleFanDirection(String toggleDir){
    logDebug "Executing 'toggleFanDirection' with ${toggleDir}"
    String currentDir = device.currentValue("fanDirection")
    
    if (currentDir == "Off"){
        // If both directions are OFF, set to toggled value
        sendEvent(name: "fanDirection", value: toggleDir)
    } else if (currentDir == "3D"){
        // If both directions are on ("3D"), set to opposite of toggled state
        String newDir = toggleDir == "Horizontal" ? "Vertical" : "Horizontal"
        sendEvent(name: "fanDirection", value: newDir)
    } else if (currentDir != toggleDir) {
        // If one direction is on and toggled state is not currently active, turn on both
        sendEvent(name: "fanDirection", value: "3D")
    } else if (currentDir == toggleDir){
        // If toggled state is currently active, turn directional off
        sendEvent(name: "fanDirection", value: "Off")
    }
    if (fanAPISupported()){
        runIn(1, 'updateDaikinDevice', [ data : false])
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
    fanRateSilent() // Should this be made configurable to allow the user to select the default fan rate?
}

def fanAuto() {
    logDebug "Executing 'fanAuto'"
	fanRateAuto()
}

// TODO: Implement these functions if possible
 def fanCirculate() {
    log.warn "Executing 'fanCirculate' not currently supported"
    // TODO: handle 'fanCirculate' command
 }

 def setThermostatFanMode(String value) {
     // for some reason, there is a space inserted in the command here, trimming get the real command
    String val = value.trim()
    logDebug "Executing 'setThermostatFanMode' with fan mode '$val'"
	switch(val){
		case "auto":
			fanAuto()
			break
		
		case "on":
			fanOn()
			break
		
		case "circulate":
			fanCirculate()
			break
        
		case "silent":
        case "1":
        case "2":
        case "3":
        case "4":
        case "5":
            setFanRate(val)
            break
		
		default:
			log.warn "Unknown fan mode: $value"
			break
	}
 }

// def setSchedule() {
    // logDebug "Executing 'setSchedule'"
    // TODO: handle 'setSchedule' command
// }
// -------

//
// Logging stuff - 2021-02-19
//
def logDebug (message) { if (debugLogging) log.debug (message) }
def logInfo  (message) { log.info (message) }
def logWarn  (message) { log.warn (message) }

