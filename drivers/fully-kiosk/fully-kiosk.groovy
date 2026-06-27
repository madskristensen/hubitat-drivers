/**
 *  Fully Kiosk Browser (Hubitat) — Fork
 *
 *  Fork by: Mads Kristensen — 2026-05-18
 *  Source: github.com/GvnCampbell/Hubitat
 *  Forked because: maintainer silent 4.5+ years (last commit 2021-11-20);
 *                  password leak in debug logs (security); no emitIfChanged in
 *                  refresh callback (~5,760+ events/day spam); sendEvent calls
 *                  missing descriptionText.
 *  Goal: keep as in-repo fork — upstream is unlikely to merge after 4.5y silence.
 *
 *  Changelog:
 *    0.6.2 — 2026-06-27 — Add setStartURL(url) command to set the Fully Kiosk start page (FKB startURL string setting); complements loadURL() (load now) and loadStartURL() (reload start page).
 *    0.6.1
 *    0.6.0 — 2026-05-21 — Perf improvements
 *    0.5.0 — 2026-05-18 — Removed MQTT support: reverted to local REST polling after broker compatibility issues; cleaner, simpler, more reliable.
 *    0.4.2 — 2026-05-18 — Add clearOverlayMessage() command to dismiss an active overlay popup on the tablet (calls FKB's setOverlayMessage with empty text); complements setOverlayMessage(text) and deviceNotification(text) — both show, this one clears.
 *    0.4.1 — 2026-05-18 — BUG: guard NPE in beep() when toneFile preference is unset (log.warn instead of NPE); demote HTTP 408/5xx callback logging from error to warn (transient tablet unreachable); BREAKING: remove setScreenBrightness command — use setLevel(0-100) instead (SwitchLevel capability primary).
 *    0.3.0 — 2026-05-18 — Closes HA gap: brightness 0-100->0-255 conversion BUG FIX (setLevel(100) now means 100% not ~39%); 6 new emitIfChanged sensor attributes from existing deviceInfo response (charging, screensaverActive, batteryTemperature, foregroundApp, screenOrientation, kioskMode — zero extra HTTP calls); Notification capability + deviceNotification(text) → overlay popup on tablet from RM; utility commands: toBackground/clearCache/forceSleep/exitApp/lockKiosk/unlockKiosk/enableLockedMode/disableLockedMode; video playback: playVideo(url)/stopVideo; motion detection on/off toggle; checkInterval event spam dedupe (Trinity finding #8).
 *    0.2.0 — 2026-05-18 — v0.2.0 polish: logsOff auto-disable, logEnable default false, descriptionText on checkInterval events, checkInterval 60→120, setLevel event moved to callback, UUID in manifest, Security note in README.
 *    0.1.0 — 2026-05-18 — Initial fork; apply Trinity audit fixes.
 */

import groovy.transform.Field

@Field static final String VERSION = "0.6.2"

metadata {
    definition (name: "Fully Kiosk Browser", namespace: "mads", author: "Mads Kristensen",
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
        capability "Notification"

        attribute "currentPageUrl",      "String"
        // Pick #2: 6 new sensor attributes from existing deviceInfo response — zero extra HTTP calls
        attribute "charging",            "String"
        attribute "screensaverActive",   "String"
        attribute "batteryTemperature",  "Number"
        attribute "foregroundApp",       "String"
        attribute "screenOrientation",   "String"
        attribute "kioskMode",           "String"
        attribute "motionDetectionEnabled", "String"

        command "bringFullyToFront"
        command "launchAppPackage", ["String"]
        command "loadStartURL"
        command "loadURL", ["String"]
        command "setStartURL", [[name:"url*", type:"STRING", description:"URL to set as the Fully Kiosk start page."]]
        command "playSound", ["String"]
        command "restartApp"
        command "screenOn"
        command "screenOff"
        command "startScreensaver"
        command "stopScreensaver"
        command "stopSound"
        command "triggerMotion"
        command "setBooleanSetting", [[name:"Key*", type:"STRING", description:"The key value associated with the setting to be updated."],
                                      [name:"Value*:", type:"ENUM", constraints:["true","false"], desciption:"The setting to be applied."]]
        command "setStringSetting",  [[name:"Key*", type:"STRING", description:"The key value associated with the setting to be updated."],
                                      [name:"Value*:", type:"STRING", desciption:"The setting to be applied."]]
        // Pick #3: Notification capability command
        command "setOverlayMessage",    [[name:"text*", type:"STRING", description:"Text to display as overlay popup on the tablet."]]
        command "clearOverlayMessage"
        // Pick #4: Utility commands
        command "toBackground"
        command "clearCache"
        command "forceSleep"
        command "exitApp"
        command "lockKiosk"
        command "unlockKiosk"
        command "enableLockedMode"
        command "disableLockedMode"
        // Pick #5: Video playback
        command "playVideo",            [[name:"url*", type:"STRING", description:"URL of the video to play."]]
        command "stopVideo"
        // Pick #6: Motion detection toggle
        command "enableMotionDetection"
        command "disableMotionDetection"
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
        input(name:"pollingInterval", type:"enum",    title:"Polling Interval (if State Polling enabled)",
              options:["60":"1 minute","300":"5 minutes","900":"15 minutes","1800":"30 minutes"],
              defaultValue:"300", required:true)
        // FIX #4 (logger): replaced multi-level loggingLevel enum with standard Hubitat logEnable bool
        // C2: default false — auto-disabled after 30 min when enabled; avoids permanent verbose trace
        input(name:"logEnable",      type:"bool",    title:"Enable Debug Logging",  defaultValue:false, required:false)
    }
}

// *** [ Initialization Methods ] *********************************************
def installed() {
    logger("[installed] ", "trace")
    // C2: schedule debug-log auto-disable on first install if user has logEnable on
    if (logEnable) runIn(1800, "logsOff")
    initialize()
}
def updated() {
    logger("[updated] ", "trace")
    // C2: re-arm 30-min auto-disable whenever preferences are saved with logEnable on
    if (logEnable) runIn(1800, "logsOff")
    initialize()
}
def initialize() {
    def logprefix = "[initialize] "
    logger(logprefix, "trace")

    unschedule()

    // Fallback to IP address for deviceNetworkId; getMACFromIP is not available in recent Hubitat versions
    if (!settings.serverIP?.trim()) {
        logger(logprefix + "ERROR: serverIP is not configured", "error")
        return
    }
    device.deviceNetworkId = settings.serverIP
    logger(logprefix + "deviceNetworkId set to: ${settings.serverIP}", "info")

    if (settings.statePolling) {
        def interval = (settings.pollingInterval ?: "300").toInteger()
        switch (interval) {
            case 60:
                runEvery1Minute("refresh")
                logger(logprefix + "State polling enabled: 1 minute", "info")
                break
            case 300:
                runEvery5Minutes("refresh")
                logger(logprefix + "State polling enabled: 5 minutes", "info")
                break
            case 900:
                runEvery15Minutes("refresh")
                logger(logprefix + "State polling enabled: 15 minutes", "info")
                break
            case 1800:
                runEvery30Minutes("refresh")
                logger(logprefix + "State polling enabled: 30 minutes", "info")
                break
            default:
                runEvery5Minutes("refresh")
                logger(logprefix + "State polling enabled: 5 minutes (default)", "info")
        }
    }

    updateDeviceData()
}
def configure() {
	def logprefix = "[configure] "
	logger(logprefix, "trace")
	setBooleanSetting("websiteIntegration", true)
	setStringSetting("webviewMixedContent", "0")
	// Escape preference values to prevent JavaScript injection
	def escapedVolumeStream = (settings.volumeStream ?: "1").toString().replaceAll(/[^0-9]/, "1")
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
fully.bind("volumeUp","sendAttributeValue('volume',${escapedVolumeStream});");
fully.bind("volumeDown","sendAttributeValue('volume',${escapedVolumeStream});");
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
    if (logEnable) logger(logprefix + "description: ${description}", "trace")
    def msg = parseLanMessage(description)
    def body = msg.body
    // FIX #3 (descriptionText): all sendEvent calls below now include descriptionText
    try {
        body = parseJson(body)
    } catch (Exception e) {
        log.error "${logprefix}JSON parse failed: ${e.message}"
        return
    }
    if (logEnable) logger(logprefix + "body: ${body}", "trace")

    // PERF: Deduplicate rapid device pushes - skip if same value seen <5 seconds ago
    def now = now()
    def lastSeen = state.lastParseEvent ?: [:]
    String eventKey = body.attribute
    def lastValue = lastSeen[eventKey]
    def lastTime = lastSeen["${eventKey}_time"] ?: 0L
    boolean isDuplicate = lastValue == body.value && (now - lastTime) < 5000  // 5 second window

    if (isDuplicate && eventKey in ["switch", "volume"]) {
        // Skip duplicate events for non-critical attributes
        if (logEnable) logger(logprefix + "PERF: Skipping duplicate ${eventKey}=${body.value}", "debug")
        return
    }

    // Update last seen tracking
    lastSeen[eventKey] = body.value
    lastSeen["${eventKey}_time"] = now
    state.lastParseEvent = lastSeen

    switch (body.attribute) {
        case "switch":
            def switchVal = body.value
            emitIfChanged("switch", switchVal,
                          "${device.displayName} switch is ${switchVal}")
            break
        case "battery":
            emitIfChanged("battery", body.value,
                          "${device.displayName} battery is ${body.value}", "%")
            break
        case "motion":
            motion(body.value)
            break
        case "acceleration":
            acceleration(body.value)
            break
        case "volume":
            emitIfChanged("volume", body.value,
                          "${device.displayName} volume is ${body.value}")
            break
        default:
            // C1+C4: added descriptionText; value 60→120 (2× poll cadence avoids false offline on single missed poll)
            // Pick #7: gate emit — value never changes, so subsequent calls are suppressed
            emitIfChanged("checkInterval", 120,
                          "${device.displayName} checkInterval is 120")
            logger(logprefix + "Unknown attribute: ${body.attribute}", "error")
            break
    }
}
def motion(value) {
    def logprefix = "[motion] "
    logger(logprefix + "value: ${value}", "trace")
    // PERF: Only emit if value changed (prevents spam from repeated device pushes)
    emitIfChanged("motion", value,
                  "${device.displayName} motion is ${value}")
    if (value == "active") {
        // PERF: Reschedule to prevent race condition where rapid events cancel pending timeout
        unschedule("motion")
        runIn(settings.motionTimeout, "motion", [data:"inactive"])
    } else {
        unschedule("motion")
    }
}
def acceleration(value) {
    def logprefix = "[acceleration] "
    logger(logprefix + "value: ${value}", "trace")
    // PERF: Only emit if value changed (prevents spam from repeated device pushes)
    emitIfChanged("acceleration", value,
                  "${device.displayName} acceleration is ${value}")
    if (value == "active") {
        // PERF: Reschedule to prevent race condition where rapid events cancel pending timeout
        unschedule("acceleration")
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
    // C5: event now fires from setLevelCallback AFTER a successful HTTP response,
    // not optimistically before — prevents the event from lying if the call fails.
    // Security: URI logged through the same password-masking pattern as sendCommandPost().
    // Pick #1 (BUG FIX): SwitchLevel uses 0-100; FKB screenBrightness expects 0-255.
    // setLevel(100) previously sent raw 100 → FKB rendered ~39% brightness.
    // Now converts 0-100 → 0-255 so setLevel(100) sends 255 (true 100% brightness).
    def logprefix = "[setLevel] "
    logger(logprefix + "level:${level}", "trace")
    if (level == null) {
        logger(logprefix + "ERROR: level cannot be null", "error")
        return
    }
    BigDecimal levelBD
    try {
        levelBD = level.toBigDecimal()
    } catch (Exception e) {
        logger(logprefix + "ERROR: level '${level}' is not numeric: ${e.message}", "error")
        return
    }
    // Special case: levels 1 and 2 pass through as raw FKB values (1/255, 2/255)
    // so users can reach FKB's true minimum brightness. Without this, 1→3 and 2→5
    // after rounding, making raw 1 and 2 unreachable via setLevel.
    int fkbBrightness
    int levelInt = levelBD.toInteger()
    if (levelInt == 1 || levelInt == 2) {
        fkbBrightness = levelInt
    } else {
        fkbBrightness = Math.round(levelBD * 2.55).toInteger()
    }
    fkbBrightness = Math.min(255, Math.max(0, fkbBrightness))
    def postParams = [
        uri: "http://${serverIP}:${serverPort}/?type=json&password=${serverPassword}&cmd=setStringSetting&key=screenBrightness&value=${fkbBrightness}",
        requestContentType: 'application/json',
        contentType: 'application/json'
    ]
    Map safeParams = postParams.clone()
    safeParams.uri = safeParams.uri?.replaceAll(/(?i)password=[^&]+/, 'password=***')
    logger(logprefix + safeParams)
    asynchttpPost("setLevelCallback", postParams, [level: level])
}
def setLevelCallback(response, data) {
    def logprefix = "[setLevelCallback] "
    if (response == null) {
        logger(logprefix + "ERROR: response is null (possible timeout or connection failure)", "error")
        return
    }
    logger(logprefix + "response.status: ${response.status}", "trace")
    if (response?.status == 200) {
        // Pick #7: gate checkInterval — value never changes so all subsequent emits are suppressed
        emitIfChanged("checkInterval", 120,
                      "${device.displayName} checkInterval is 120")
        sendEvent([name:"level", value:data.level,
                   descriptionText:"${device.displayName} level is ${data.level}"])
    } else {
        Integer status = response?.status as Integer
        if (status == 408 || (status != null && status >= 500 && status <= 599)) {
            logger(logprefix + "Transient response: ${status}", "warn")
        } else {
            logger(logprefix + "Invalid response: ${status}", "error")
        }
    }
}
def beep() {
    logger("[beep] ", "trace")
    if (!toneFile?.trim()) {
        log.warn "[beep] toneFile preference is not configured — set it on the device's Edit page (URL of a hosted sound file)"
        return
    }
    sendCommandPost("cmd=playSound&url=${java.net.URLEncoder.encode(toneFile, "UTF-8")}")
}
def launchAppPackage(appPackage) {
    def logprefix = "[launchAppPackage] "
    logger(logprefix + "appPackage:${appPackage}", "trace")
    if (!appPackage?.trim()) {
        logger(logprefix + "ERROR: appPackage cannot be null or empty", "error")
        return
    }
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
    def logprefix = "[loadURL] "
    logger(logprefix + "url:${url}", "trace")
    if (!url?.trim()) {
        logger(logprefix + "ERROR: url cannot be null or empty", "error")
        return
    }
    sendCommandPost("cmd=loadURL&url=${java.net.URLEncoder.encode(url, "UTF-8")}")
}
def setStartURL(url) {
    def logprefix = "[setStartURL] "
    logger(logprefix + "url:${url}", "trace")
    if (!url?.trim()) {
        logger(logprefix + "ERROR: url cannot be null or empty", "error")
        return
    }
    sendCommandPost("cmd=setStringSetting&key=startURL&value=${java.net.URLEncoder.encode(url, "UTF-8")}")
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
                if (sound == null) {
                    logger(logprefix + "ERROR: textToSpeech failed to generate audio", "error")
                    return
                }
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

    if (volumeLevel == null) {
        logger(logprefix + "ERROR: volumeLevel cannot be null", "error")
        return
    }
    if (volumeStream == null) {
        logger(logprefix + "ERROR: volumeStream preference is not set", "error")
        return
    }

    Integer vl, vs
    try {
        vl = volumeLevel.toInteger()
    } catch (Exception e) {
        logger(logprefix + "ERROR: volumeLevel '${volumeLevel}' is not numeric: ${e.message}", "error")
        return
    }
    try {
        vs = volumeStream.toInteger()
    } catch (Exception e) {
        logger(logprefix + "ERROR: volumeStream '${volumeStream}' is not numeric: ${e.message}", "error")
        return
    }

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
    logger(logprefix + "current volume: ${device.currentValue('volume')}", "debug")
    def newVolume = state.mute ?: device.currentValue("volume")
    if (newVolume) {
        newVolume = newVolume.toInteger() + 10
        newVolume = Math.min(newVolume, 100)
        setVolume(newVolume)
    } else {
        logger(logprefix + "No volume currently set.", "warn")
    }
}
def volumeDown() {
    def logprefix = "[volumeDown] "
    logger(logprefix + "current volume: ${device.currentValue('volume')}", "debug")
    def newVolume = state.mute ?: device.currentValue("volume")
    if (newVolume) {
        newVolume = newVolume.toInteger() - 10
        newVolume = Math.max(newVolume, 0)
        setVolume(newVolume)
    } else {
        logger(logprefix + "No volume currently set.", "warn")
    }
}
def mute() {
    def logprefix = "[mute] "
    logger(logprefix, "debug")
    if (!state.mute) {
        // Save current volume BEFORE calling async setVolume()
        def currentVolume = device.currentValue("volume") ?: 100
        state.mute = currentVolume
        logger(logprefix + "Previous volume saved to state.mute:${state.mute}", "debug")
        setVolume(0)
        sendEvent([name:"mute", value:"muted",
                   descriptionText:"${device.displayName} mute is muted"])
    } else {
        logger(logprefix + "Already muted.", "debug")
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
    if (response == null) {
        logger(logprefix + "ERROR: response is null (possible timeout or connection failure)", "error")
        return
    }
    logger(logprefix + "response.status: ${response.status}", "trace")
    if (response?.status == 200) {
        if (logEnable) logger(logprefix + "response.json: ${response.json}", "debug")
        // FIX #2 (event hygiene): replaced bare sendEvent with emitIfChanged to prevent
        // ~5,760+ unchanged events/day at 1-minute polling cadence
        emitIfChanged("battery",        response.json.batteryLevel,
                      "${device.displayName} battery is ${response.json.batteryLevel}", "%")
        def switchVal = (response.json.screenOn == true) ? "on" : "off"
        emitIfChanged("switch",         switchVal,
                      "${device.displayName} switch is ${switchVal}")
        // Pick #1 (BUG FIX): FKB returns raw 0-255; convert back to SwitchLevel 0-100
        if (response.json.screenBrightness != null) {
            def levelVal = Math.round((response.json.screenBrightness as BigDecimal) / 2.55).toInteger()
            levelVal = Math.min(100, Math.max(0, levelVal))
            emitIfChanged("level", levelVal,
                          "${device.displayName} level is ${levelVal}", "%")
        }
        emitIfChanged("currentPageUrl", response.json.currentPage,
                      "${device.displayName} currentPageUrl is ${response.json.currentPage}")
        // Pick #2: 6 new attributes from existing deviceInfo response — zero extra HTTP calls
        def chargingVal = response.json.plugged ? "true" : "false"
        emitIfChanged("charging",          chargingVal,
                      "${device.displayName} charging is ${chargingVal}")
        def ssVal = response.json.isInScreensaver ? "true" : "false"
        emitIfChanged("screensaverActive", ssVal,
                      "${device.displayName} screensaverActive is ${ssVal}")
        if (response.json.batteryTemperature != null) {
            def tempVal = response.json.batteryTemperature as BigDecimal
            emitIfChanged("batteryTemperature", tempVal,
                          "${device.displayName} batteryTemperature is ${tempVal}°C", "°C")
        }
        if (response.json.foregroundApp != null) {
            emitIfChanged("foregroundApp", response.json.foregroundApp,
                          "${device.displayName} foregroundApp is ${response.json.foregroundApp}")
        }
        def orientVal = (response.json.screenOrientation == 1) ? "landscape" : "portrait"
        emitIfChanged("screenOrientation", orientVal,
                      "${device.displayName} screenOrientation is ${orientVal}")
        def kioskVal = response.json.kioskMode ? "true" : "false"
        emitIfChanged("kioskMode",         kioskVal,
                      "${device.displayName} kioskMode is ${kioskVal}")
        if (response.json.motionDetection != null) {
            def motionVal = response.json.motionDetection ? "true" : "false"
            emitIfChanged("motionDetectionEnabled", motionVal,
                          "${device.displayName} motionDetectionEnabled is ${motionVal}")
        }
    } else {
        Integer status = response?.status as Integer
        if (status == 408 || (status != null && status >= 500 && status <= 599)) {
            logger(logprefix + "Transient response: ${status}", "warn")
        } else {
            logger(logprefix + "Invalid response: ${status}", "error")
        }
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
    def logprefix = "[playSound] "
    logger(logprefix + "soundFile:${soundFile}", "trace")
    if (!soundFile?.trim()) {
        logger(logprefix + "ERROR: soundFile cannot be null or empty", "error")
        return
    }
    sendCommandPost("cmd=playSound&url=${java.net.URLEncoder.encode(soundFile, "UTF-8")}")
}
def stopSound() {
    logger("[stopSound] ", "trace")
    sendCommandPost("cmd=stopSound")
}
// Pick #5: Video playback
def playVideo(String url) {
    logger("[playVideo] url:${url}", "trace")
    sendCommandPost("cmd=playVideo&url=${java.net.URLEncoder.encode(url, "UTF-8")}")
}
def stopVideo() {
    logger("[stopVideo] ", "trace")
    sendCommandPost("cmd=stopVideo")
}
def setBooleanSetting(key, value) {
    def logprefix = "[setBooleanSetting] "
    logger(logprefix + "key,value: ${key},${value}", "trace")
    if (!key?.trim()) {
        logger(logprefix + "ERROR: key cannot be null or empty", "error")
        return
    }
    sendCommandPost("cmd=setBooleanSetting&key=${java.net.URLEncoder.encode(key, "UTF-8")}&value=${value}")
}
def setStringSetting(key, value) {
    def logprefix = "[setStringSetting] "
    logger(logprefix + "key,value: ${key},${value}", "trace")
    if (!key?.trim()) {
        logger(logprefix + "ERROR: key cannot be null or empty", "error")
        return
    }
    sendCommandPost("cmd=setStringSetting&key=${java.net.URLEncoder.encode(key, "UTF-8")}&value=${java.net.URLEncoder.encode(value, "UTF-8")}")
}
// Pick #4: Utility commands — thin wrappers over existing FKB REST endpoints
def toBackground() {
    logger("[toBackground] ", "trace")
    sendCommandPost("cmd=toBackground")
}
def clearCache() {
    logger("[clearCache] ", "trace")
    sendCommandPost("cmd=clearCache")
}
def forceSleep() {
    logger("[forceSleep] ", "trace")
    sendCommandPost("cmd=forceSleep")
}
def exitApp() {
    logger("[exitApp] ", "trace")
    sendCommandPost("cmd=exitApp")
}
def lockKiosk() {
    logger("[lockKiosk] ", "trace")
    sendCommandPost("cmd=lockKiosk")
}
def unlockKiosk() {
    logger("[unlockKiosk] ", "trace")
    sendCommandPost("cmd=unlockKiosk")
}
def enableLockedMode() {
    logger("[enableLockedMode] ", "trace")
    sendCommandPost("cmd=enableLockedMode")
}
def disableLockedMode() {
    logger("[disableLockedMode] ", "trace")
    sendCommandPost("cmd=disableLockedMode")
}
// Pick #3: Notification capability — overlay popup on the tablet screen
void deviceNotification(String text) {
    logger("[deviceNotification] text:${text}", "trace")
    sendCommandPost("cmd=setOverlayMessage&text=${java.net.URLEncoder.encode(text, "UTF-8")}")
}
def setOverlayMessage(String text) {
    logger("[setOverlayMessage] text:${text}", "trace")
    sendCommandPost("cmd=setOverlayMessage&text=${java.net.URLEncoder.encode(text, "UTF-8")}")
}
def clearOverlayMessage() {
    logger("[clearOverlayMessage] ", "trace")
    sendCommandPost("cmd=setOverlayMessage&text=")
}
// Pick #6: Motion detection toggle — disable camera overnight for battery savings
def enableMotionDetection() {
    logger("[enableMotionDetection] ", "trace")
    setBooleanSetting("motionDetection", true)
    emitIfChanged("motionDetectionEnabled", "true",
                  "${device.displayName} motionDetectionEnabled is true")
}
def disableMotionDetection() {
    logger("[disableMotionDetection] ", "trace")
    setBooleanSetting("motionDetection", false)
    emitIfChanged("motionDetectionEnabled", "false",
                  "${device.displayName} motionDetectionEnabled is false")
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
    if (response == null) {
        logger(logprefix + "ERROR: response is null (possible timeout or connection failure)", "error")
        return
    }
    logger(logprefix + "response status,data: ${response.status},${data}", "trace")
    if (response.status == 200) {
        logger(logprefix + "response.json: ${response.json}", "debug")
        device.updateDataValue("appVersionName",     response.json.appVersionName)
        device.updateDataValue("deviceManufacturer", response.json.deviceManufacturer)
        device.updateDataValue("androidVersion",     response.json.androidVersion)
        device.updateDataValue("deviceModel",        response.json.deviceModel)
        // C1+C4: added descriptionText; value 60→120
        // Pick #7: gate emit — value never changes, so subsequent calls are suppressed
        emitIfChanged("checkInterval", 120,
                      "${device.displayName} checkInterval is 120")
    } else {
        Integer status = response?.status as Integer
        if (status == 408 || (status != null && status >= 500 && status <= 599)) {
            logger(logprefix + "Transient response: ${status}", "warn")
        } else {
            logger(logprefix + "Invalid response: ${status}", "error")
        }
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
    if (response == null) {
        logger(logprefix + "ERROR: response is null (possible timeout or connection failure)", "error")
        return
    }
    logger(logprefix + "response.status: ${response.status}", "trace")
    if (response?.status == 200) {
        logger(logprefix + "response.data: ${response.data}", "debug")
        // C1+C4: added descriptionText; value 60→120
        // Pick #7: gate emit — value never changes, so subsequent calls are suppressed
        emitIfChanged("checkInterval", 120,
                      "${device.displayName} checkInterval is 120")
    } else {
        Integer status = response?.status as Integer
        if (status == 408 || (status != null && status >= 500 && status <= 599)) {
            logger(logprefix + "Transient response: ${status}", "warn")
        } else {
            logger(logprefix + "Invalid response: ${status}", "error")
        }
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
        } catch (NumberFormatException e) {
            // Fall back to string comparison if numeric conversion fails
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
// C2: auto-disable debug logging after 30 minutes
def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "Fully Kiosk Browser: debug logging auto-disabled after 30 minutes"
}

// FIX #4 (logger): replaced the inverted multi-level logger (where "debug"
// logged more than "trace") with the Hubitat community-standard logEnable bool.
// All trace/debug output is gated by logEnable; info/warn/error always emit.
private void logger(loggingText, String loggingType = "debug") {
    switch (loggingType.toLowerCase()) {
        case "error": log.error loggingText; break
        case "warn":  log.warn  loggingText; break
        case "info":  log.info  loggingText; break
        case "trace": if (logEnable) log.debug loggingText; break
        default:      if (logEnable) log.debug loggingText; break
    }
}
