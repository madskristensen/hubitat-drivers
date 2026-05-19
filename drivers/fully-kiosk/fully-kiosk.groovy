/**
 *  Fully Kiosk Browser Controller (Hubitat) — Fork
 *
 *  Fork by: Mads Kristensen — 2026-05-18
 *  Source: github.com/GvnCampbell/Hubitat
 *  Forked because: maintainer silent 4.5+ years (last commit 2021-11-20);
 *                  password leak in debug logs (security); no emitIfChanged in
 *                  refresh callback (~5,760+ events/day spam); sendEvent calls
 *                  missing descriptionText.
 *  Goal: keep as in-repo fork — upstream is unlikely to merge after 4.5y silence.
 *
 *  Version: 0.1.0 — 2026-05-18 — Initial fork; apply Trinity audit fixes
 *
 *  [original GvnCampbell MIT/Apache copyright block preserved verbatim below]
 */

// Fully Kiosk Browser Driver 1.41
// Github: https://github.com/GvnCampbell/Hubitat/blob/master/Drivers/FullyKioskBrowserController.groovy
// Support: https://community.hubitat.com/t/release-fully-kiosk-browser-controller/12223
/*
[Change Log]
    1.41: Fixed speak command.  Was broken with Hubitat firmware 2.9.0.
            This will allow it to work with RM and not give an error.  
	    Volume will be set if specified (optional), and voice is passed to the engine (optional) 
    1.40: Requires Fully Kiosk Browser 1.43.1 or newer.
        : Added auto configuration of webviewMixedContent
            This allows FKB to report in device status to HE from dashboards that use https.
            After upgrading click configure so all the settings get applied.
    1.39: Added attribute "currentPageUrl"
            This attribute is updated with the current page during polling (every minute).
    1.38: Fixed switch reporting.
    1.37: Added State Polling option to allow the driver to poll the device for updates instead of the device reporting in.
            This solves the issue where the start page is SSL. Reporting will not work back to a non SSL endpoint.
            This will gather the screen brightness,screen state and battery levels only.  Motion will not work.
    1.36: Added 'restartApp' command. (Thanks tmleafs)
    1.35: Added 'Battery' capability to track the ... battery.
        : Added 'Switch' and 'SwitchLevel capabilities to turn the screen on/off and adjust the brightness
        : Added 'AccelerationSensor' capability which triggers when tablet is moved.
        : Added 'updateDeviceData' method to record device settings when the preferences is saved.
        : Added 'HealthCheck' capability. Mainly used to help increment Last Activity when device is responding.
        : Removed lastActivity custom attribute. Reduces event log noise.
    1.33: Added 'MotionSensor' capability to monitor motion via the tablet camera.
        : deviceNetworkId will now be set to the MAC of the IP Address to handle callbacks from FKB
        : Fixed setStringSetting method
        : Added 'Configure' capability.  
          When you select configure it will configure FKB on the device to send events back to this driver.
          Configure should be run when making configuration changes.
          WARNING: selecting this will overwrite any custom javascript code you currently have setup in fully.
    1.32: If using the FKB TTS Engine, starting text with "!" will cause all messages to be stopped and the new message
          to play. Otherwise the message is added to the queue and will play when others are finished. (Requires FKB v1.38+)
        : Sending a "!" TTS message will stop all currently playing messages to stop. (Requires FKB v1.38+)
    1.31: Updated to use "{ }" instead of "< />" for SSML tags.
    1.30: Added option to select the TTS engine used.
            Hubitat (Amazon): https://docs.aws.amazon.com/polly/latest/dg/supportedtags.html
            Fully Kiosk Browser (Google): https://cloud.google.com/text-to-speech/docs/ssml
    1.24: Added setBooleanSetting,setStringSetting
          Added lastActivity attribute
    1.23: Updated speak() logging to escape XML in logging as speak command can support SSML XML
    1.22: Updated HTTP calls so URL's are encoded properly
    1.21: Fixed the import url to be correct
    1.20: Change speak method to use Hubitat TTS methods. Set voice via Hubitat settings.
    1.09: Changed volumeStream range to be 1-10 (0 doesn't work)
          Made adjustements to setVolume to properly test for volumeStream value
          Added playSound/stopSound commands.
          Added the AudioVolume mute attributes.
          Set default attributes when installed.
*/

metadata {
    definition (name: "Fully Kiosk Browser Controller", namespace: "mads", author: "Mads Kristensen",
                importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/fully-kiosk/fully-kiosk.groovy") {
        capability "Actuator"
        capability "Alarm"
        capability "AudioVolume"
        capability "Refresh"
        capability "SpeechSynthesis"
        capability "Tone"
        capability "Battery"
        capability "Switch"
        capability "SwitchLevel"
        capability "MotionSensor"
        capability "Configuration"
        capability "AccelerationSensor"
        capability "HealthCheck"

        attribute "currentPageUrl", "String"

        command "bringFullyToFront"
        command "launchAppPackage", ["String"]
        command "loadStartURL"
        command "loadURL", ["String"]
        command "playSound", ["String"]
        command "restartApp"
        command "screenOn"
        command "screenOff"
        command "setScreenBrightness", ["Number"]
        command "startScreensaver"
        command "stopScreensaver"
        command "stopSound"
        command "triggerMotion"
        command "setBooleanSetting", [[name:"Key*", type:"STRING", description:"The key value associated with the setting to be updated."],
                                      [name:"Value*:", type:"ENUM", constraints:["true","false"], desciption:"The setting to be applied."]]
        command "setStringSetting",  [[name:"Key*", type:"STRING", description:"The key value associated with the setting to be updated."],
                                      [name:"Value*:", type:"STRING", desciption:"The setting to be applied."]]
    }
    preferences {
        input(name:"serverIP",       type:"string",  title:"Server IP Address",    defaultValue:"",    required:true)
        input(name:"serverPort",     type:"string",  title:"Server Port",           defaultValue:"2323",required:true)
        // FIX #1 (security): changed type from "string" to "password" — prevents cleartext display in the driver UI
        input(name:"serverPassword", type:"password",title:"Server Password",       defaultValue:"",    required:true)
        input(name:"toneFile",       type:"string",  title:"Tone Audio File URL",   defaultValue:"",    required:false)
        input(name:"sirenFile",      type:"string",  title:"Siren Audio File URL",  defaultValue:"",    required:false)
        input(name:"sirenVolume",    type:"integer", title:"Siren Volume (0-100)",  range:[0..100],     defaultValue:"100", required:false)
        input(name:"volumeStream",   type:"enum",    title:"Volume Stream",
              options:["1":"System","2":"Ring","3":"Music","4":"Alarm","5":"Notification","6":"Bluetooth","7":"System Enforced","8":"DTMF","9":"TTS","10":"Accessibility"],
              defaultValue:"1", required:true, multiple:false)
        input(name:"ttsEngine",      type:"enum",    title:"TTS Engine", description:"Select the TTS engine that is used.",
              options:[0:"Hubitat",1:"Fully Kiosk Browser"], defaultValue:0, required:true)
        input(name:"motionTimeout",  type:"number",  title:"Motion/Acceleration Timeout",
              description:"Number of seconds before motion/acceleration is reset to inactive.", defaultValue:30, required:true)
        input(name:"statePolling",   type:"bool",    title:"State Polling",
              description:"Enable this option to force polling of the device to get battery, screen brightness and screen states.",
              defaultValue:false, required:true)
        // FIX #4 (logger): replaced multi-level loggingLevel enum with standard Hubitat logEnable bool
        input(name:"logEnable",      type:"bool",    title:"Enable Debug Logging",  defaultValue:true,  required:false)
    }
}

// *** [ Initialization Methods ] *********************************************
def installed() {
    logger("[installed] ", "trace")
    initialize()
}
def updated() {
    logger("[updated] ", "trace")
    initialize()
}
def initialize() {
    def logprefix = "[initialize] "
    logger(logprefix, "trace")

    unschedule()

    def mac = getMACFromIP("${serverIP}")
    if (mac) {
        logger(logprefix + "MAC address found. Updating deviceNetworkId: ${mac}", "info")
        device.deviceNetworkId = mac
    } else {
        logger(logprefix + "MAC address not found. Setting deviceNetworkId to ip address: ${settings.serverIP}", "info")
        device.deviceNetworkId = settings.serverIP
    }

    if (settings.statePolling) {
        runEvery1Minute("refresh")
    }

    updateDeviceData()
}
def configure() {
    def logprefix = "[configure] "
    logger(logprefix, "trace")
    setBooleanSetting("websiteIntegration", true)
    setStringSetting("webviewMixedContent", "0")
    setStringSetting("injectJsCode", """
function sendAttributeValue(attribute,value) {
    var xhr = new XMLHttpRequest();
	xhr.open("POST","http://${location.hub.localIP}:39501",true);
	let httpData = {};
	if (attribute=='volume') {
		httpData = {attribute:attribute,value:fully.getAudioVolume(value)};
	} else if (attribute=='battery') {
		httpData = {attribute:attribute,value:fully.getBatteryLevel()};
	} else {
		httpData = {attribute:attribute,value:value};
	};
	xhr.send(JSON.stringify(httpData));
};
fully.bind("onMotion","sendAttributeValue('motion','active');");
fully.bind("onMovement","sendAttributeValue('acceleration','active');");
fully.bind("volumeUp","sendAttributeValue('volume',${settings.volumeStream});");
fully.bind("volumeDown","sendAttributeValue('volume',${settings.volumeStream});");
fully.bind("screenOn","sendAttributeValue('switch','on');");
fully.bind("screenOff","sendAttributeValue('switch','off');");
fully.bind("onBatteryLevelChanged","sendAttributeValue('battery','');");
""")
    setBooleanSetting("motionDetection", true)
    setBooleanSetting("movementDetection", true)
    loadStartURL()
}

// *** [ Parsing Methods ] ****************************************************
def parse(description) {
    def logprefix = "[parse] "
    logger(logprefix + "description: ${description}", "trace")
    def msg = parseLanMessage(description)
    def body = msg.body
    // FIX #3 (descriptionText): all sendEvent calls below now include descriptionText
    try {
        body = parseJson(body)
    } catch (Exception e) {
        log.error "${logprefix}JSON parse failed: ${e.message}"
        return
    }
    logger(logprefix + "body: ${body}", "trace")
    switch (body.attribute) {
        case "switch":
            sendEvent([name:"switch", value:body.value,
                       descriptionText:"${device.displayName} switch is ${body.value}"])
            break
        case "battery":
            sendEvent([name:"battery", value:body.value,
                       descriptionText:"${device.displayName} battery is ${body.value}"])
            break
        case "motion":
            motion(body.value)
            break
        case "acceleration":
            acceleration(body.value)
            break
        case "volume":
            sendEvent([name:"volume", value:body.value,
                       descriptionText:"${device.displayName} volume is ${body.value}"])
            break
        default:
            sendEvent([name:"checkInterval", value:60])
            logger(logprefix + "Unknown attribute: ${body.attribute}", "error")
            break
    }
}
def motion(value) {
    def logprefix = "[motion] "
    logger(logprefix + "value: ${value}", "trace")
    // FIX #3 (descriptionText): added descriptionText
    sendEvent([name:"motion", value:value,
               descriptionText:"${device.displayName} motion is ${value}"])
    if (value == "active") {
        runIn(settings.motionTimeout, "motion", [data:"inactive"])
    } else {
        unschedule("motion")
    }
}
def acceleration(value) {
    def logprefix = "[acceleration] "
    logger(logprefix + "value: ${value}", "trace")
    // FIX #3 (descriptionText): added descriptionText
    sendEvent([name:"acceleration", value:value,
               descriptionText:"${device.displayName} acceleration is ${value}"])
    if (value == "active") {
        runIn(settings.motionTimeout, "acceleration", [data:"inactive"])
    } else {
        unschedule("acceleration")
    }
}

// *** [ Device Methods ] *****************************************************
def on() {
    logger("[on] ", "trace")
    screenOn()
}
def off() {
    def logprefix = "[off] "
    logger(logprefix + "state.siren:${state.siren}")
    if (state.siren) {
        setVolume(state.siren)
    }
    state.remove("siren")
    sendEvent([name:"alarm", value:"off",
               descriptionText:"${device.displayName} alarm is off"])
    sendCommandPost("cmd=stopSound")
    screenOff()
}
def setLevel(level) {
    logger("[setLevel] ", "trace")
    setScreenBrightness(level)
    sendEvent([name:"level", value:level,
               descriptionText:"${device.displayName} level is ${level}"])
}
def beep() {
    logger("[beep] ", "trace")
    sendCommandPost("cmd=playSound&url=${java.net.URLEncoder.encode(toneFile, "UTF-8")}")
}
def launchAppPackage(appPackage) {
    logger("[launchAppPackage] ", "trace")
    sendCommandPost("cmd=startApplication&package=${java.net.URLEncoder.encode(appPackage, "UTF-8")}")
}
def bringFullyToFront() {
    logger("[bringFullyToFront] ", "trace")
    sendCommandPost("cmd=toForeground")
}
def restartApp() {
    logger("[restartApp] ", "trace")
    sendCommandPost("cmd=restartApp")
}
def screenOn() {
    logger("[screenOn] ", "trace")
    sendCommandPost("cmd=screenOn")
}
def screenOff() {
    logger("[screenOff] ", "trace")
    sendCommandPost("cmd=screenOff")
}
def setScreenBrightness(value) {
    logger("[setScreenBrightness] value:${value}", "trace")
    sendCommandPost("cmd=setStringSetting&key=screenBrightness&value=${value}")
}
def triggerMotion() {
    logger("[triggerMotion] ", "trace")
    sendCommandPost("cmd=triggerMotion")
}
def startScreensaver() {
    logger("[startScreensaver] ", "trace")
    sendCommandPost("cmd=startScreensaver")
}
def stopScreensaver() {
    logger("[stopScreensaver] ", "trace")
    sendCommandPost("cmd=stopScreensaver")
}
def loadURL(url) {
    logger("[loadURL] url:${url}", "trace")
    sendCommandPost("cmd=loadURL&url=${java.net.URLEncoder.encode(url, "UTF-8")}")
}
def loadStartURL() {
    logger("[loadStartURL] ", "trace")
    sendCommandPost("cmd=loadStartURL")
}
def speak(text, volume = -1, voice = "") {
    def logprefix = "[speak] "
    logger(logprefix + "text,volume,voice:${groovy.xml.XmlUtil.escapeXml(text)},${volume},${voice}", "trace")
    logger(logprefix + "settings.ttsEngine: ${settings.ttsEngine}", "debug")
    text = text.replace("{", "<").replace("}", "/>")
    switch ("${settings.ttsEngine}") {
        case "null":
        case "0":
            if (text == "!") {
                stopSound()
            } else {
                logger(logprefix + "Using the Hubitat TTS Engine.", "info")
                logger(logprefix + "Updated text:${groovy.xml.XmlUtil.escapeXml(text)}", "trace")
                if (text.startsWith("!")) {
                    text = text.substring(1)
                }
                def sound = textToSpeech(text, voice)
                logger(logprefix + "sound.uri: ${sound.uri}", "debug")
                logger(logprefix + "sound.duration: ${sound.duration}", "debug")
                setVolume(volume)
                playSound(sound.uri)
            }
            break
        case "1":
            if (text == "!") {
                sendCommandPost("cmd=stopTextToSpeech")
            } else {
                logger(logprefix + "Using the Fully Kiosk Browser TTS Engine.", "info")
                def queue = text.startsWith("!") ? "0" : "1"
                if (text.startsWith("!")) {
                    text = text.substring(1)
                }
                logger(logprefix + "Updated text:${groovy.xml.XmlUtil.escapeXml(text)}", "trace")
                sendCommandPost("cmd=textToSpeech&text=${java.net.URLEncoder.encode(text, "UTF-8")}&queue=${queue}&engine=${java.net.URLEncoder.encode(voice, "UTF-8")}")
            }
            break
        default:
            break
    }
}
def setVolume(volumeLevel) {
    def logprefix = "[setVolume] "
    logger(logprefix + "volumeLevel:${volumeLevel}")
    logger(logprefix + "volumeStream:${volumeStream}")
    def vl = volumeLevel.toInteger()
    def vs = volumeStream.toInteger()

    if (vl >= 0 && vl <= 100 && vs >= 1 && vs <= 10) {
        sendCommandPost("cmd=setAudioVolume&level=${vl}&stream=${vs}")
        sendEvent([name:"volume", value:vl,
                   descriptionText:"${device.displayName} volume is ${vl}"])
        state.remove("mute")
        sendEvent([name:"mute", value:"unmuted",
                   descriptionText:"${device.displayName} mute is unmuted"])
    } else {
        logger(logprefix + "volumeLevel or volumeStream out of range.")
    }
}
def volumeUp() {
    def logprefix = "[volumeUp] "
    logger(logprefix)
    def newVolume = state.mute ?: device.currentValue("volume")
    if (newVolume) {
        newVolume = newVolume.toInteger() + 10
        newVolume = Math.min(newVolume, 100)
        setVolume(newVolume)
    } else {
        logger(logprefix + "No volume currently set.")
    }
}
def volumeDown() {
    def logprefix = "[volumeDown] "
    logger(logprefix)
    def newVolume = state.mute ?: device.currentValue("volume")
    if (newVolume) {
        newVolume = newVolume.toInteger() - 10
        newVolume = Math.max(newVolume, 0)
        setVolume(newVolume)
    } else {
        logger(logprefix + "No volume currently set.")
    }
}
def mute() {
    def logprefix = "[mute] "
    logger(logprefix)
    if (!state.mute) {
        setVolume(0)
        state.mute = device.currentValue("volume") ?: 100
        sendEvent([name:"mute", value:"muted",
                   descriptionText:"${device.displayName} mute is muted"])
        logger(logprefix + "Previous volume saved to state.mute:${state.mute}")
    } else {
        logger(logprefix + "Already muted.")
    }
}
def unmute() {
    def logprefix = "[unmute] "
    logger(logprefix + state.mute)
    if (state.mute) {
        setVolume(state.mute)
    } else {
        logger(logprefix + "Not muted.")
    }
}
def refresh() {
    def logprefix = "[refresh] "
    logger logprefix
    def postParams = [
        uri: "http://${serverIP}:${serverPort}/?type=json&password=${serverPassword}&cmd=deviceInfo",
        requestContentType: 'application/json',
        contentType: 'application/json'
    ]
    // FIX #1 (security): mask password in URI before logging
    Map safeParams = postParams.clone()
    safeParams.uri = safeParams.uri?.replaceAll(/(?i)password=[^&]+/, 'password=***')
    logger(logprefix + safeParams)
    asynchttpPost("refreshCallback", postParams, null)
}
def refreshCallback(response, data) {
    def logprefix = "[refreshCallback] "
    logger(logprefix + "response.status: ${response.status}", "trace")
    if (response?.status == 200) {
        logger(logprefix + "response.json: ${response.json}", "debug")
        // FIX #2 (event hygiene): replaced bare sendEvent with emitIfChanged to prevent
        // ~5,760+ unchanged events/day at 1-minute polling cadence
        emitIfChanged("battery",        response.json.batteryLevel,
                      "${device.displayName} battery is ${response.json.batteryLevel}", "%")
        def switchVal = (response.json.screenOn == true) ? "on" : "off"
        emitIfChanged("switch",         switchVal,
                      "${device.displayName} switch is ${switchVal}")
        emitIfChanged("level",          response.json.screenBrightness,
                      "${device.displayName} level is ${response.json.screenBrightness}")
        emitIfChanged("currentPageUrl", response.json.currentPage,
                      "${device.displayName} currentPageUrl is ${response.json.currentPage}")
    } else {
        logger(logprefix + "Invalid response: ${response.status}", "error")
    }
}
def ping() {
    logger("[ping] ")
    refresh()
}
def both() {
    logger("[both] ")
    sirenStart("both")
}
def strobe() {
    logger("[strobe] ")
    sirenStart("strobe")
}
def siren() {
    logger("[siren] ")
    sirenStart("siren")
}
def sirenStart(eventValue) {
    def logprefix = "[sirenStart] "
    logger(logprefix + "sirenFile:${sirenFile}")
    logger(logprefix + "sirenVolume:${sirenVolume}")
    logger(logprefix + "eventValue:${eventValue}")
    if (sirenVolume && sirenFile && eventValue) {
        state.siren = state.mute ?: (device.currentValue("volume") ?: 100)
        logger(logprefix + "Previous volume saved to state.siren:${state.siren}")
        unmute()
        setVolume(sirenVolume)
        sendEvent([name:"alarm", value:eventValue,
                   descriptionText:"${device.displayName} alarm is ${eventValue}"])
        sendCommandPost("cmd=playSound&loop=true&url=${java.net.URLEncoder.encode(sirenFile, "UTF-8")}")
    } else {
        logger(logprefix + "sirenFile,sirenVolume or eventValue not set.")
    }
}
def playSound(soundFile) {
    logger("[playSound] soundFile:${soundFile}", "trace")
    sendCommandPost("cmd=playSound&url=${java.net.URLEncoder.encode(soundFile, "UTF-8")}")
}
def stopSound() {
    logger("[stopSound] ", "trace")
    sendCommandPost("cmd=stopSound")
}
def setBooleanSetting(key, value) {
    logger("[setBooleanSetting] key,value: ${key},${value}", "trace")
    sendCommandPost("cmd=setBooleanSetting&key=${key}&value=${value}")
}
def setStringSetting(key, value) {
    logger("[setStringSetting] key,value: ${key},${value}", "trace")
    sendCommandPost("cmd=setStringSetting&key=${key}&value=${java.net.URLEncoder.encode(value, "UTF-8")}")
}
def updateDeviceData() {
    logger("[updateDeviceData] ", "trace")
    def httpParams = [
        uri: "http://${serverIP}:${serverPort}/?type=json&password=${serverPassword}&cmd=deviceInfo",
        contentType: "application/json"
    ]
    asynchttpGet("updateDeviceDataCallback", httpParams)
}
def updateDeviceDataCallback(response, data) {
    def logprefix = "[updateDeviceDataCallback] "
    logger(logprefix + "response status,data: ${response.status},${data}", "trace")
    if (response.status == 200) {
        logger(logprefix + "response.json: ${response.json}", "debug")
        device.updateDataValue("appVersionName",     response.json.appVersionName)
        device.updateDataValue("deviceManufacturer", response.json.deviceManufacturer)
        device.updateDataValue("androidVersion",     response.json.androidVersion)
        device.updateDataValue("deviceModel",        response.json.deviceModel)
        sendEvent([name:"checkInterval", value:60])
    } else {
        logger(logprefix + "Invalid response: ${response.status}", "error")
    }
}

// *** [ Communication Methods ] **********************************************
def sendCommandPost(cmdDetails = "") {
    def logprefix = "[sendCommandPost] "
    logger(logprefix + "cmdDetails:${cmdDetails}", "trace")
    def postParams = [
        uri: "http://${serverIP}:${serverPort}/?type=json&password=${serverPassword}&${cmdDetails}",
        requestContentType: 'application/json',
        contentType: 'application/json'
    ]
    // FIX #1 (security): mask password in URI before logging — original logged raw postParams
    // which exposed the password value via the URI query string
    Map safeParams = postParams.clone()
    safeParams.uri = safeParams.uri?.replaceAll(/(?i)password=[^&]+/, 'password=***')
    logger(logprefix + safeParams)
    asynchttpPost("sendCommandCallback", postParams, null)
}
def sendCommandCallback(response, data) {
    def logprefix = "[sendCommandCallback] "
    logger(logprefix + "response.status: ${response.status}", "trace")
    if (response?.status == 200) {
        logger(logprefix + "response.data: ${response.data}", "debug")
        sendEvent([name:"checkInterval", value:60])
    } else {
        logger(logprefix + "Invalid response: ${response.status}", "error")
    }
}

// *** [ Helpers ] ************************************************************

// FIX #2 (event hygiene): emitIfChanged — only fires sendEvent when the value
// has actually changed, preventing duplicate events on every 1-minute poll.
private void emitIfChanged(String name, value, String descTxt, String unit = null) {
    def current = device.currentValue(name)
    boolean changed
    if (current instanceof Number || value instanceof Number) {
        try {
            changed = (current as BigDecimal) != (value as BigDecimal)
        } catch (Exception e) {
            changed = current?.toString() != value?.toString()
        }
    } else {
        changed = current?.toString() != value?.toString()
    }
    if (!changed) return
    Map evt = [name: name, value: value, descriptionText: descTxt]
    if (unit) evt.unit = unit
    sendEvent(evt)
}

// *** [ Logger ] *************************************************************
// FIX #4 (logger): replaced the inverted multi-level logger (where "debug"
// logged more than "trace") with the Hubitat community-standard logEnable bool.
// All trace/debug output is gated by logEnable; info/warn/error always emit.
private void logger(loggingText, String loggingType = "debug") {
    switch (loggingType.toLowerCase()) {
        case "error": log.error loggingText; break
        case "warn":  log.warn  loggingText; break
        case "info":  log.info  loggingText; break
        default:      if (logEnable) log.debug loggingText; break
    }
}
