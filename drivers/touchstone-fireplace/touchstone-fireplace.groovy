/**
 * Touchstone / Tuya Fireplace
 * Author:  Mads Kristensen
 * Version: 0.1.5
 * License: MIT
 *
 * Local LAN control for the Touchstone Sideline Elite — and other Tuya WiFi
 * fireplaces — via Tuya Local protocol v3.3 (AES-128-ECB over raw TCP port 6668).
 *
 * For non-Sideline-Elite devices:
 *   1. Set Device Profile = "Custom" (or "Generic Tuya Fireplace" for basic control)
 *   2. Run discoverDPs() to see your device's DP layout
 *   3. Run captureBaseline(), press a remote button, run captureDiff() to map each control
 *   4. Set the DP numbers in Preferences
 *   See README for the full walkthrough.
 *
 * Optional "Default settings on power-on" preferences are only applied after Hubitat turns the fireplace on; leave any blank to keep the device's remembered setting. Heater state is intentionally excluded for safety.
 *
 * Changelog:
 *   0.1.5 — 2026-05-17T12:22:15-07:00 — BUGFIX: removed paragraph() from preferences (Hubitat app-only, not allowed in drivers)
 *   0.1.4 — 2026-05-17T11:58:55-07:00 — SAFETY: removed defaultHeatLevel auto-apply; BUGFIX: removed sandbox-blocked reflection logging
 *   0.1.3 — 2026-05-17T11:58:55-07:00 — Optional default-on-power-on settings for flame color, log color, brightness, heat level, and temp setpoint
 *   0.1.2 — 2026-05-17 — Replaced blocked CRC32 import with pure-Groovy implementation (Hubitat import allowlist)
 *   0.1.1 — 2026-05-17T11:24:33-07:00 — Generalized device profiles, in-driver DP discovery, and auditable raw DP writes
 *   0.1.0 — 2026-05-17 — Initial Tuya Local scaffold for power, heat level, flame/log lighting, temperature polling, raw DP surfacing, and socket retry/backoff
 */
// v0.1.5 — BUGFIX: removed paragraph() from preferences (Hubitat app-only, not allowed in drivers).
// v0.1.4 — SAFETY: removed defaultHeatLevel auto-apply (heater never auto-starts); BUGFIX: replaced reflection (e.getClass()) with sandbox-safe exception logging.

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Field static final String DRIVER_VERSION = "0.1.5"
@Field static final String USER_AGENT = "Hubitat Touchstone-Tuya Fireplace/0.1.5"
@Field static final long[] CRC32_TABLE = (0..255).collect { int n ->
    long c = n as long
    8.times {
        c = ((c & 1L) != 0L) ? (0xEDB88320L ^ (c >>> 1)) : (c >>> 1)
    }
    c & 0xFFFFFFFFL
} as long[]
@Field static final Integer TUYA_PORT = 6668
@Field static final String TUYA_VERSION = "3.3"
@Field static final Long TUYA_PREFIX = 0x000055AAL
@Field static final Long TUYA_SUFFIX = 0x0000AA55L
@Field static final Integer TUYA_CMD_CONTROL = 7
@Field static final Integer TUYA_CMD_STATUS = 8
@Field static final Integer TUYA_CMD_HEARTBEAT = 9
@Field static final Integer TUYA_CMD_DP_QUERY = 10
@Field static final Integer TUYA_CMD_CONTROL_NEW = 13
@Field static final Integer DEFAULT_POLL_SECONDS = 60
@Field static final Integer RESPONSE_TIMEOUT_SECONDS = 5
@Field static final Integer SOCKET_IDLE_CLOSE_SECONDS = 2
@Field static final Integer WRITE_REFRESH_DELAY_SECONDS = 3
@Field static final Integer POWER_REFRESH_DELAY_SECONDS = 8
@Field static final Long POWER_TRANSITION_SETTLE_MILLIS = 10000L
@Field static final Integer POWER_ON_DEFAULTS_DELAY_MILLIS = 1500
@Field static final String POWER_ON_DEFAULT_REASON_PREFIX = "power-on default "
@Field static final List<Integer> RETRY_DELAYS_SECONDS = [5, 15, 30]
@Field static final String PROFILE_SIDELINE = "Sideline Elite (tested)"
@Field static final String PROFILE_GENERIC = "Generic Tuya Fireplace"
@Field static final String PROFILE_CUSTOM = "Custom"
@Field static final List<String> FLAME_COLOR_OPTIONS = ["1", "2", "3", "4", "5", "6"]
@Field static final List<String> FLAME_BRIGHTNESS_OPTIONS = ["1", "2", "3", "4", "5"]
@Field static final List<String> LOG_COLOR_OPTIONS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"]
@Field static final List<String> HEAT_LEVEL_OPTIONS = ["off", "low", "high"]
@Field static final List<String> BASE_STATUS_DPS = ["1", "2", "3", "5", "13", "14", "15"]
@Field static final List<String> SIDELINE_DISCOVERY_DPS = ["101", "102", "103", "104", "105", "107", "108"]
@Field static final Map<String, Integer> SIDELINE_PROFILE_DPS = [power: 1, tempSetC: 2, heatLevel: 5, tempSetF: 14, flameColor: 101, flameBrightness: 102, logColor: 104]
@Field static final Map<String, String> CUSTOM_DP_SETTING_NAMES = [power: "powerDp", flameColor: "flameColorDp", flameBrightness: "flameBrightnessDp", logColor: "logColorDp", heatLevel: "heatLevelDp", tempSetF: "tempSetFDp", tempSetC: "tempSetCDp"]
@Field static final Map<String, String> HEAT_LEVEL_TO_DP = ["off": "0", "low": "1", "high": "2"]
@Field static final Map<String, String> DP_TO_HEAT_LEVEL = ["0": "off", "1": "low", "2": "high"]

metadata {
    // Keep namespace stable for existing imports/upgrades.
    definition(
        name:         "Touchstone / Tuya Fireplace",
        namespace:    "mads",
        author:       "Mads Kristensen",
        importUrl:    "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/touchstone-fireplace/touchstone-fireplace.groovy",
        singleThreaded: true
    ) {
        capability "Actuator"
        capability "Switch"
        capability "Refresh"
        capability "Initialize"
        capability "Polling"
        capability "TemperatureMeasurement"

        // TODO (Switch): verify these community-derived raw Tuya enum ranges on real Touchstone hardware.
        // Keep the command inputs as raw strings for now so the driver does not pretend to know labels it has not verified.
        command "setFlameColor", [[name: "color*", type: "ENUM", constraints: FLAME_COLOR_OPTIONS,
            description: "Raw Tuya enum string for the mapped flame-color DP (Sideline default 101; use setRawDP() for experiments)."]]
        command "setFlameBrightness", [[name: "level*", type: "ENUM", constraints: FLAME_BRIGHTNESS_OPTIONS,
            description: "Raw Tuya enum string for the mapped flame-brightness DP (Sideline default 102)."]]
        command "setLogColor", [[name: "color*", type: "ENUM", constraints: LOG_COLOR_OPTIONS,
            description: "Raw Tuya enum string for the mapped log-color DP (Sideline default 104)."]]
        command "setHeatLevel", [[name: "level*", type: "ENUM", constraints: HEAT_LEVEL_OPTIONS]]
        command "setHeatingSetpoint", [[name: "temperature*", type: "NUMBER", description: "Writes the mapped Fahrenheit or Celsius setpoint DP based on the preferred unit preference."]]
        command "setRawDP", [[name: "dpId*", type: "NUMBER"], [name: "value*", type: "STRING",
            description: "Advanced: raw DP write. true/false become booleans; whole numbers become integers; everything else is sent as a string."]]
        command "setDpRaw", [[name: "dpId*", type: "NUMBER"], [name: "value*", type: "STRING",
            description: "Legacy alias for setRawDP()."]]
        command "discoverDPs"
        command "captureBaseline"
        command "captureDiff"

        attribute "power",            "enum",   ["on", "off"]
        attribute "flameColor",       "string"
        attribute "flameBrightness",  "string"
        attribute "logColor",         "string"
        attribute "heatLevel",        "enum",   ["off", "low", "high"]
        attribute "heatingSetpoint",  "number"
        attribute "online",           "enum",   ["online", "offline", "unknown"]
        attribute "dp103",            "string"
        attribute "dp105",            "string"
        attribute "dp107",            "string"
        attribute "dp108",            "string"
        attribute "tempUnit",         "enum",   ["F", "C"]
    }

    preferences {
        input name: "deviceIP", type: "text",
              title: "Device IP address",
              description: "Static LAN IP for the fireplace's Tuya WiFi module.",
              required: true

        input name: "deviceId", type: "text",
              title: "Device ID",
              description: "Tuya device ID from tinytuya or another local-key extraction workflow.",
              required: true

        input name: "localKey", type: "password",
              title: "Local key (16 chars)",
              description: "Never hardcode this. Enter the Tuya local key for this device.",
              required: true

        input name: "deviceProfile", type: "enum",
              title: "Device Profile",
              options: ["Sideline Elite (tested)", "Generic Tuya Fireplace", "Custom"],
              defaultValue: "Sideline Elite (tested)",
              required: true

        if (settings?.deviceProfile == "Custom") {
            input name: "flameColorDp", type: "number", title: "Flame Color DP", defaultValue: 101
            input name: "flameBrightnessDp", type: "number", title: "Flame Brightness DP", defaultValue: 102
            input name: "logColorDp", type: "number", title: "Log Color DP", defaultValue: 104
            input name: "heatLevelDp", type: "number", title: "Heat Level DP", defaultValue: 5
            input name: "tempSetFDp", type: "number", title: "Temperature Setpoint (°F) DP", defaultValue: 14
            input name: "tempSetCDp", type: "number", title: "Temperature Setpoint (°C) DP", defaultValue: 2
            input name: "powerDp", type: "number", title: "Power DP", defaultValue: 1
        }

        input name: "setpointUnit", type: "enum",
              title: "Preferred setpoint / temperature unit",
              options: ["F": "Fahrenheit (recommended for US Touchstone units)", "C": "Celsius"],
              defaultValue: "F",
              required: true

        if (settings?.deviceProfile != PROFILE_GENERIC) {
            input name: "defaultFlameColor", type: "enum",
                  title: "Default flame color (optional)",
                  description: "Applied ~1.5s after Hubitat turns the fireplace on. Leave blank to keep the fireplace firmware's last-known flame color.",
                  options: FLAME_COLOR_OPTIONS,
                  required: false

            input name: "defaultFlameBrightness", type: "enum",
                  title: "Default flame brightness (optional)",
                  description: "Applied ~1.5s after Hubitat turns the fireplace on. Leave blank to keep the fireplace firmware's last-known flame brightness.",
                  options: FLAME_BRIGHTNESS_OPTIONS,
                  required: false

            input name: "defaultLogColor", type: "enum",
                  title: "Default log color (optional)",
                  description: "Applied ~1.5s after Hubitat turns the fireplace on. Leave blank to keep the fireplace firmware's last-known log color.",
                  options: LOG_COLOR_OPTIONS,
                  required: false
        }

        input name: "defaultHeatingSetpoint", type: "number",
              title: "Default heating setpoint (optional)",
              description: "Applied ~1.5s after Hubitat turns the fireplace on, using the preferred unit above. Leave blank to keep the fireplace firmware's last-known target temperature.",
              required: false

        input name: "pollInterval", type: "enum",
              title: "Polling interval",
              options: ["0": "Disabled", "30": "30 seconds", "60": "60 seconds (recommended)", "120": "2 minutes", "300": "5 minutes", "600": "10 minutes"],
              defaultValue: "60",
              required: true

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
    log.info "Touchstone / Tuya Fireplace v${DRIVER_VERSION} installed"
    device.updateSetting("deviceProfile", [value: PROFILE_SIDELINE, type: "enum"])
    device.updateSetting("pollInterval", [value: "60", type: "enum"])
    device.updateSetting("setpointUnit", [value: "F", type: "enum"])
    device.updateSetting("logEnable", [value: false, type: "bool"])
    device.updateSetting("txtEnable", [value: true, type: "bool"])
    initialize()
}

def updated() {
    log.info "Touchstone / Tuya Fireplace v${DRIVER_VERSION} preferences updated"
    unschedule()
    state.pendingRequests = []
    state.inFlight = null
    state.awaitingResponse = false
    state.rxBuffer = ""
    state.retryIndex = 0
    state.statusCommand = null
    state.seqNo = 0L
    state.manualSocketCloseAt = null
    state.remove("dpBaseline")

    if (settings.logEnable) {
        runIn(1800, "logsOff")
        debugLog "Debug logging enabled; will auto-disable in 30 minutes"
    }

    initialize()
}

def initialize() {
    debugLog "initialize() called"
    unschedule("poll")
    unschedule("retryPendingRequests")
    unschedule("responseTimeout")
    unschedule("closeSocketIfIdle")
    closeSocket(false)

    if (!preferencesReady()) {
        updateOnlineStatus("unknown", "Waiting for device IP, device ID, and a 16-character local key")
        return
    }

    ensureDefaultAttributes()
    schedulePolling()
    runIn(2, "refresh")
}

def uninstalled() {
    unschedule()
    closeSocket(false)
    debugLog "uninstalled()"
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "Touchstone / Tuya Fireplace: debug logging auto-disabled after 30 minutes"
}

// ---------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------

def on() {
    Integer powerDp = dpFor("power")
    if (powerDp == null) {
        log.warn "[Touchstone] Power is not mapped for profile '${activeDeviceProfile()}'"
        return
    }

    infoLog "${device.displayName} switch → on"
    markPowerTransitionIfChanged(true)
    applySwitchState(true, "digital")
    unschedule("applyOnPowerOnDefaults")
    cancelQueuedPowerOnDefaultWrites()
    sendDpWrite(powerDp.toString(), true, "power on", POWER_REFRESH_DELAY_SECONDS)
    // DP 14 can revert during off → on; give the firmware a beat before pushing optional defaults.
    runInMillis(POWER_ON_DEFAULTS_DELAY_MILLIS, "applyOnPowerOnDefaults")
}

def off() {
    Integer powerDp = dpFor("power")
    if (powerDp == null) {
        log.warn "[Touchstone] Power is not mapped for profile '${activeDeviceProfile()}'"
        return
    }

    infoLog "${device.displayName} switch → off"
    markPowerTransitionIfChanged(false)
    applySwitchState(false, "digital")
    unschedule("applyOnPowerOnDefaults")
    cancelQueuedPowerOnDefaultWrites()
    sendDpWrite(powerDp.toString(), false, "power off", POWER_REFRESH_DELAY_SECONDS)
}

def refresh() {
    requestStatus("refresh")
}

def poll() {
    refresh()
}

def discoverDPs() {
    requestStatus("discover DPs", "discover", discoveryStatusDpIds())
}

def captureBaseline() {
    requestStatus("capture baseline", "baseline", discoveryStatusDpIds())
}

def captureDiff() {
    Map baseline = state.dpBaseline instanceof Map ? (Map) state.dpBaseline : [:]
    if (!baseline) {
        log.warn "[Touchstone] captureDiff() requires a baseline first — run captureBaseline()"
        return
    }

    requestStatus("capture diff", "diff", discoveryStatusDpIds())
}

def setFlameColor(String color) {
    String raw = safeStr(color)?.trim()
    if (!raw) {
        log.warn "[Touchstone] setFlameColor requires a raw enum value"
        return
    }

    Integer flameColorDp = mappedCommandDp("flameColor", "Flame color")
    if (flameColorDp == null) {
        return
    }

    infoLog "${device.displayName} flame color → ${raw}"
    emitAttribute("flameColor", raw, "${device.displayName} flame color set to ${raw}", "digital")
    sendDpWrite(flameColorDp.toString(), raw, "flame color", WRITE_REFRESH_DELAY_SECONDS)
}

def setFlameBrightness(String level) {
    String raw = safeStr(level)?.trim()
    if (!raw) {
        log.warn "[Touchstone] setFlameBrightness requires a raw enum value"
        return
    }

    Integer flameBrightnessDp = mappedCommandDp("flameBrightness", "Flame brightness")
    if (flameBrightnessDp == null) {
        return
    }

    infoLog "${device.displayName} flame brightness → ${raw}"
    emitAttribute("flameBrightness", raw, "${device.displayName} flame brightness set to ${raw}", "digital")
    sendDpWrite(flameBrightnessDp.toString(), raw, "flame brightness", WRITE_REFRESH_DELAY_SECONDS)
}

def setLogColor(String color) {
    String raw = safeStr(color)?.trim()
    if (!raw) {
        log.warn "[Touchstone] setLogColor requires a raw enum value"
        return
    }

    Integer logColorDp = mappedCommandDp("logColor", "Log color")
    if (logColorDp == null) {
        return
    }

    infoLog "${device.displayName} log color → ${raw}"
    emitAttribute("logColor", raw, "${device.displayName} log color set to ${raw}", "digital")
    sendDpWrite(logColorDp.toString(), raw, "log color", WRITE_REFRESH_DELAY_SECONDS)
}

def setHeatLevel(String level) {
    String normalized = safeStr(level)?.trim()?.toLowerCase()
    if (!(normalized in HEAT_LEVEL_OPTIONS)) {
        log.warn "[Touchstone] setHeatLevel: invalid level '${level}' — use off, low, or high"
        return
    }

    Integer heatLevelDp = mappedCommandDp("heatLevel", "Heat level")
    if (heatLevelDp == null) {
        return
    }

    infoLog "${device.displayName} heat level → ${normalized}"
    emitAttribute("heatLevel", normalized, "${device.displayName} heat level set to ${normalized}", "digital")
    sendDpWrite(heatLevelDp.toString(), HEAT_LEVEL_TO_DP[normalized], "heat level", WRITE_REFRESH_DELAY_SECONDS)
}

def setHeatingSetpoint(temp) {
    Integer requested = safeInt(temp, null)
    if (requested == null) {
        log.warn "[Touchstone] setHeatingSetpoint: '${temp}' is not a valid whole-number temperature"
        return
    }

    String unit = preferredTempUnit()
    Integer targetDp = mappedCommandDp(unit == "F" ? "tempSetF" : "tempSetC", "Heating setpoint")
    if (targetDp == null) {
        return
    }

    Integer clamped = clampSetpoint(requested, unit)
    infoLog "${device.displayName} heating setpoint → ${clamped}°${unit}"
    emitAttribute("heatingSetpoint", clamped, "${device.displayName} heating setpoint set to ${clamped}°${unit}", "digital", unit)
    sendDpWrite(targetDp.toString(), clamped, "heating setpoint", WRITE_REFRESH_DELAY_SECONDS)
}

def setDpRaw(dpId, String value) {
    setRawDP(safeStr(dpId), value)
}

def setRawDP(dpId, String value) {
    Integer targetDp = safeInt(dpId, null)
    if (targetDp == null || targetDp <= 0) {
        log.warn "[Touchstone] setRawDP: dpId must be a positive integer"
        return
    }

    Object coerced = coerceRawValue(value)
    log.info "[Touchstone][RawDP] Writing DP ${targetDp}=${formatDpValue(coerced)} (${dpValueType(coerced)})"
    sendDpWrite(targetDp.toString(), coerced, "raw DP ${targetDp}", WRITE_REFRESH_DELAY_SECONDS)
}

// ---------------------------------------------------------------------------
// Socket / queue management
// ---------------------------------------------------------------------------

def socketStatus(String message) {
    String text = safeStr(message) ?: ""
    if (!text) {
        return
    }

    debugLog "socketStatus: ${text}"

    Long manualCloseAt = safeLong(state.manualSocketCloseAt, null)
    if (manualCloseAt != null && (now() - manualCloseAt) < 2000L) {
        state.manualSocketCloseAt = null
        return
    }
    state.manualSocketCloseAt = null

    String lower = text.toLowerCase()
    if (lower.contains("disconnect") || lower.contains("error") || lower.contains("reset") || lower.contains("broken pipe") || lower.contains("closed")) {
        requeueInFlight()
        closeSocket(false)

        if (hasPendingWork()) {
            scheduleRetry("Socket closed before the fireplace answered. Another Tuya client may still own the single TCP slot (tinytuya 901 equivalent).")
        } else {
            updateOnlineStatus("offline", text)
        }
    }
}

def parse(String message) {
    String chunk = sanitizeHex(message)
    if (!chunk) {
        debugLog "parse() received an empty/invalid payload"
        return
    }

    try {
        String buffer = safeStr(state.rxBuffer) ?: ""
        buffer += chunk
        if (buffer.size() > 32768) {
            int keepFrom = Math.max(buffer.lastIndexOf("000055AA"), buffer.size() - 8192)
            buffer = keepFrom > 0 ? buffer.substring(keepFrom) : buffer
        }
        state.rxBuffer = buffer

        Integer processed = consumeReceiveBuffer()
        if ((processed ?: 0) > 0) {
            unschedule("responseTimeout")
            state.awaitingResponse = false
            state.inFlight = null
            state.retryIndex = 0
            updateOnlineStatus("online", "Device responded")
            pumpQueue()
            scheduleSocketClose(SOCKET_IDLE_CLOSE_SECONDS)
        }
    } catch (Exception e) {
        log.warn "[Touchstone] parse() failed — ${e.message}"
    }
}

private void requestStatus(String reason, String captureMode = null, List<String> requestedDps = null) {
    if (!preferencesReady()) {
        log.warn "[Touchstone] ${reason} skipped — configure device IP, device ID, and local key first"
        updateOnlineStatus("unknown", "Configuration incomplete")
        return
    }

    List<String> effectiveDps = uniqueDpIds(requestedDps ?: statusDpIds())
    enqueueRequest(getStatusCommand(), buildStatusPayloadJson(effectiveDps), reason, captureMode, effectiveDps)
}

private void enqueueRequest(Integer cmd, String payloadJson, String reason, String captureMode = null, List<String> requestedDps = null) {
    List<Map> queue = pendingRequestQueue()
    Map request = [cmd: cmd, payloadJson: payloadJson, reason: reason]
    if (captureMode) {
        request.captureMode = captureMode
    }
    if (requestedDps) {
        request.requestedDps = requestedDps.collect { safeStr(it) }
    }
    queue << request
    state.pendingRequests = queue
    debugLog "Queued Tuya cmd ${cmd} for ${reason}; pending=${queue.size()}"
    pumpQueue()
}

private void sendDpWrite(String dpId, Object value, String reason, Integer refreshDelaySeconds) {
    if (!preferencesReady()) {
        log.warn "[Touchstone] ${reason} skipped — configure device IP, device ID, and local key first"
        updateOnlineStatus("unknown", "Configuration incomplete")
        return
    }

    Map payload = [
        devId: deviceIdValue(),
        uid:   deviceIdValue(),
        t:     currentEpochSecondsString(),
        dps:   [(dpId): value]
    ]

    enqueueRequest(TUYA_CMD_CONTROL, JsonOutput.toJson(payload), reason)
    queueDelayedRefresh(refreshDelaySeconds)
}

private void pumpQueue() {
    if (!preferencesReady()) {
        return
    }
    if (state.awaitingResponse == true) {
        return
    }

    List<Map> queue = pendingRequestQueue()
    if (!queue) {
        return
    }

    if (!ensureSocketConnected()) {
        scheduleRetry("Unable to open the Tuya socket. Another client may be holding the single connection slot.")
        return
    }

    Map request = queue.remove(0)
    state.pendingRequests = queue

    try {
        byte[] frame = buildTuyaFrame(safeInt(request.cmd, TUYA_CMD_CONTROL), safeStr(request.payloadJson) ?: "{}")
        String hex = hubitat.helper.HexUtils.byteArrayToHexString(frame)
        interfaces.rawSocket.sendMessage(hex)
        state.inFlight = request
        state.awaitingResponse = true
        debugLog "Sent Tuya cmd ${request.cmd} for ${request.reason}"
        unschedule("responseTimeout")
        runIn(RESPONSE_TIMEOUT_SECONDS, "responseTimeout")
    } catch (Exception e) {
        log.warn "[Touchstone] send failed for ${request.reason} — ${e.message}"
        List<Map> retryQueue = pendingRequestQueue()
        retryQueue.add(0, request)
        state.pendingRequests = retryQueue
        state.inFlight = null
        state.awaitingResponse = false
        closeSocket(false)
        scheduleRetry("Socket write failed. Another Tuya client may still own the single TCP slot.")
    }
}

def responseTimeout() {
    if (state.awaitingResponse != true) {
        return
    }

    requeueInFlight()
    closeSocket(false)
    updateOnlineStatus("offline", "No response within ${RESPONSE_TIMEOUT_SECONDS}s")
    scheduleRetry("No response from fireplace within ${RESPONSE_TIMEOUT_SECONDS}s. Backing off before retrying.")
}

def retryPendingRequests() {
    debugLog "retryPendingRequests()"
    state.awaitingResponse = false
    state.inFlight = null
    pumpQueue()
}

private void scheduleRetry(String reason) {
    Integer currentIndex = safeInt(state.retryIndex, 0)
    Integer delay = RETRY_DELAYS_SECONDS[Math.min(currentIndex, RETRY_DELAYS_SECONDS.size() - 1)]
    state.retryIndex = Math.min(currentIndex + 1, RETRY_DELAYS_SECONDS.size() - 1)
    unschedule("retryPendingRequests")
    runIn(delay, "retryPendingRequests")
    log.warn "[Touchstone] ${reason} Retrying in ${delay}s."
}

private void requeueInFlight() {
    Map request = state.inFlight instanceof Map ? (Map) state.inFlight : null
    state.awaitingResponse = false
    state.inFlight = null
    unschedule("responseTimeout")

    if (request) {
        List<Map> queue = pendingRequestQueue()
        queue.add(0, request)
        state.pendingRequests = queue
    }
}

private Boolean hasPendingWork() {
    return (state.awaitingResponse == true) || pendingRequestQueue().size() > 0
}

private List<Map> pendingRequestQueue() {
    if (!(state.pendingRequests instanceof List)) {
        return []
    }

    List<Map> queue = []
    state.pendingRequests.each { entry ->
        if (entry instanceof Map) {
            Map copy = [cmd: entry.cmd, payloadJson: entry.payloadJson, reason: entry.reason]
            if (entry.captureMode) {
                copy.captureMode = safeStr(entry.captureMode)
            }
            if (entry.requestedDps instanceof List) {
                copy.requestedDps = entry.requestedDps.collect { safeStr(it) }
            }
            queue << copy
        }
    }
    return queue
}

private Boolean ensureSocketConnected() {
    try {
        if (interfaces.rawSocket.connected) {
            return true
        }
    } catch (ignored) {
        // fall through and reconnect
    }

    try {
        state.manualSocketCloseAt = null
        interfaces.rawSocket.connect(deviceIpValue(), TUYA_PORT, byteInterface: true, readDelay: 150)
        debugLog "Opened raw socket to ${deviceIpValue()}:${TUYA_PORT}"
        return true
    } catch (Exception e) {
        debugLog "rawSocket.connect failed: ${e.message}"
        updateOnlineStatus("offline", "Connect failed")
        return false
    }
}

private void scheduleSocketClose(Integer delaySeconds) {
    unschedule("closeSocketIfIdle")
    runIn(delaySeconds, "closeSocketIfIdle")
}

def closeSocketIfIdle() {
    if (state.awaitingResponse == true || pendingRequestQueue()) {
        return
    }
    closeSocket(false)
}

private void closeSocket(Boolean markOffline) {
    unschedule("closeSocketIfIdle")
    try {
        state.manualSocketCloseAt = now()
        interfaces.rawSocket.close()
    } catch (ignored) {
        // socket may already be closed
    }

    if (markOffline) {
        updateOnlineStatus("offline", "Socket closed")
    }
}

private void queueDelayedRefresh(Integer delaySeconds) {
    unschedule("delayedRefresh")
    runIn(delaySeconds, "delayedRefresh")
}

def delayedRefresh() {
    refresh()
}

def applyOnPowerOnDefaults() {
    if (!preferencesReady()) {
        debugLog "Skipping power-on defaults because configuration is incomplete"
        return
    }
    if (safeStr(device.currentValue("switch")) != "on") {
        debugLog "Skipping power-on defaults because the fireplace is no longer on"
        return
    }

    // SAFETY: The heater (DP 5) is intentionally excluded from defaults.
    // Auto-starting a radiant heat element is a fire/burn risk.
    // Heater state changes ONLY via explicit setHeatLevel() user commands.
    Boolean appliedAny = false

    String flameColor = safeStr(settings.defaultFlameColor)?.trim()
    if (flameColor) {
        Integer flameColorDp = dpFor("flameColor")
        if (flameColorDp != null) {
            emitAttribute("flameColor", flameColor, "${device.displayName} default flame color set to ${flameColor}", "digital")
            infoLog "Applied default: flameColor=${flameColor}"
            sendDpWrite(flameColorDp.toString(), flameColor, "${POWER_ON_DEFAULT_REASON_PREFIX}flame color", WRITE_REFRESH_DELAY_SECONDS)
            appliedAny = true
        } else {
            log.warn "[Touchstone] defaultFlameColor is set but flame color is not mapped for profile '${activeDeviceProfile()}'"
        }
    }

    String flameBrightness = safeStr(settings.defaultFlameBrightness)?.trim()
    if (flameBrightness) {
        Integer flameBrightnessDp = dpFor("flameBrightness")
        if (flameBrightnessDp != null) {
            emitAttribute("flameBrightness", flameBrightness, "${device.displayName} default flame brightness set to ${flameBrightness}", "digital")
            infoLog "Applied default: flameBrightness=${flameBrightness}"
            sendDpWrite(flameBrightnessDp.toString(), flameBrightness, "${POWER_ON_DEFAULT_REASON_PREFIX}flame brightness", WRITE_REFRESH_DELAY_SECONDS)
            appliedAny = true
        } else {
            log.warn "[Touchstone] defaultFlameBrightness is set but flame brightness is not mapped for profile '${activeDeviceProfile()}'"
        }
    }

    String logColor = safeStr(settings.defaultLogColor)?.trim()
    if (logColor) {
        Integer logColorDp = dpFor("logColor")
        if (logColorDp != null) {
            emitAttribute("logColor", logColor, "${device.displayName} default log color set to ${logColor}", "digital")
            infoLog "Applied default: logColor=${logColor}"
            sendDpWrite(logColorDp.toString(), logColor, "${POWER_ON_DEFAULT_REASON_PREFIX}log color", WRITE_REFRESH_DELAY_SECONDS)
            appliedAny = true
        } else {
            log.warn "[Touchstone] defaultLogColor is set but log color is not mapped for profile '${activeDeviceProfile()}'"
        }
    }

    Integer requestedSetpoint = safeInt(settings.defaultHeatingSetpoint, null)
    if (requestedSetpoint != null) {
        String unit = preferredTempUnit()
        Integer setpointDp = dpFor(unit == "F" ? "tempSetF" : "tempSetC")
        Integer clampedSetpoint = clampSetpoint(requestedSetpoint, unit)
        if (setpointDp != null) {
            emitAttribute("heatingSetpoint", clampedSetpoint, "${device.displayName} default heating setpoint set to ${clampedSetpoint}°${unit}", "digital", unit)
            infoLog "Applied default: heatingSetpoint=${clampedSetpoint}°${unit}"
            sendDpWrite(setpointDp.toString(), clampedSetpoint, "${POWER_ON_DEFAULT_REASON_PREFIX}heating setpoint", WRITE_REFRESH_DELAY_SECONDS)
            appliedAny = true
        } else {
            log.warn "[Touchstone] defaultHeatingSetpoint is set but the preferred-unit setpoint DP is not mapped for profile '${activeDeviceProfile()}'"
        }
    }

    if (appliedAny) {
        queueDelayedRefresh(POWER_REFRESH_DELAY_SECONDS)
    }
}

private void cancelQueuedPowerOnDefaultWrites() {
    List<Map> queue = pendingRequestQueue()
    if (!queue) {
        return
    }

    List<Map> filtered = queue.findAll { Map request ->
        !(safeStr(request.reason)?.startsWith(POWER_ON_DEFAULT_REASON_PREFIX))
    }
    if (filtered.size() != queue.size()) {
        state.pendingRequests = filtered
        debugLog "Dropped ${queue.size() - filtered.size()} queued power-on default write(s)"
    }
}

// ---------------------------------------------------------------------------
// Tuya framing / crypto
// ---------------------------------------------------------------------------

private byte[] buildTuyaFrame(Integer cmd, String payloadJson) {
    byte[] payloadBytes = payloadJson.getBytes("UTF-8")
    byte[] encryptedPayload = encryptTuyaPayload(cmd, payloadBytes)
    byte[] withoutCrc = concatBytes(
        intToBytes(TUYA_PREFIX),
        intToBytes(nextSeqNo()),
        intToBytes(cmd as Long),
        intToBytes((encryptedPayload.length + 8) as Long),
        encryptedPayload
    )

    Long crc = crc32(withoutCrc)
    return concatBytes(withoutCrc, intToBytes(crc), intToBytes(TUYA_SUFFIX))
}

private byte[] encryptTuyaPayload(Integer cmd, byte[] payloadBytes) {
    byte[] encrypted = aesEncrypt(payloadBytes, localKeyBytes())
    if (!commandNeedsVersionHeader(cmd)) {
        return encrypted
    }

    return concatBytes(protocol33HeaderBytes(), encrypted)
}

private String decryptTuyaPayload(byte[] payloadBytes) {
    if (!payloadBytes) {
        return null
    }

    byte[] working = payloadBytes
    byte[] versionHeader = protocol33HeaderBytes()
    byte[] versionPrefix = TUYA_VERSION.getBytes("UTF-8")

    if (startsWithBytes(working, versionPrefix)) {
        working = sliceBytes(working, versionHeader.length, working.length - versionHeader.length)
    } else if (getStatusCommand() == TUYA_CMD_CONTROL_NEW && (working.length % 16) != 0 && working.length > versionHeader.length) {
        working = sliceBytes(working, versionHeader.length, working.length - versionHeader.length)
    }

    try {
        byte[] decrypted = aesDecrypt(working, localKeyBytes())
        return new String(decrypted, "UTF-8")
    } catch (Exception e) {
        debugLog "AES decrypt failed (${e.message}); falling back to plain UTF-8 payload"
        return new String(working, "UTF-8")
    }
}

private byte[] aesEncrypt(byte[] plaintext, byte[] keyBytes) {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    return cipher.doFinal(plaintext)
}

private byte[] aesDecrypt(byte[] ciphertext, byte[] keyBytes) {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)
    return cipher.doFinal(ciphertext)
}

private long crc32(byte[] data) {
    long crc = 0xFFFFFFFFL
    for (byte b : data) {
        crc = CRC32_TABLE[((int) (crc ^ (b & 0xFF))) & 0xFF] ^ (crc >>> 8)
    }
    return (crc ^ 0xFFFFFFFFL) & 0xFFFFFFFFL
}

private byte[] concatBytes(byte[]... arrays) {
    Integer totalLength = 0
    for (byte[] part : arrays) {
        totalLength += part?.length ?: 0
    }

    byte[] combined = new byte[totalLength]
    Integer offset = 0
    for (byte[] part : arrays) {
        if (!part) {
            continue
        }
        for (Integer i = 0; i < part.length; i++) {
            combined[offset + i] = part[i]
        }
        offset += part.length
    }
    return combined
}

private byte[] intToBytes(Long value) {
    byte[] bytes = new byte[4]
    bytes[0] = ((value >> 24) & 0xFF) as byte
    bytes[1] = ((value >> 16) & 0xFF) as byte
    bytes[2] = ((value >> 8) & 0xFF) as byte
    bytes[3] = (value & 0xFF) as byte
    return bytes
}

private Long readUInt32(byte[] data, Integer offset) {
    return ((data[offset] & 0xFFL) << 24) |
           ((data[offset + 1] & 0xFFL) << 16) |
           ((data[offset + 2] & 0xFFL) << 8) |
           (data[offset + 3] & 0xFFL)
}

private byte[] sliceBytes(byte[] source, Integer start, Integer length) {
    byte[] copy = new byte[length]
    for (Integer i = 0; i < length; i++) {
        copy[i] = source[start + i]
    }
    return copy
}

private Boolean startsWithBytes(byte[] data, byte[] prefix) {
    if (!data || !prefix || data.length < prefix.length) {
        return false
    }
    for (Integer i = 0; i < prefix.length; i++) {
        if (data[i] != prefix[i]) {
            return false
        }
    }
    return true
}

private byte[] protocol33HeaderBytes() {
    byte[] versionBytes = TUYA_VERSION.getBytes("UTF-8")
    byte[] header = new byte[versionBytes.length + 12]
    for (Integer i = 0; i < versionBytes.length; i++) {
        header[i] = versionBytes[i]
    }
    return header
}

private Boolean commandNeedsVersionHeader(Integer cmd) {
    return !(cmd in [TUYA_CMD_DP_QUERY, TUYA_CMD_HEARTBEAT])
}

private Long nextSeqNo() {
    Long current = safeLong(state.seqNo, 0L) + 1L
    state.seqNo = current
    return current
}

private Integer getStatusCommand() {
    Integer current = safeInt(state.statusCommand, null)
    if (current != null) {
        return current
    }

    Integer initial = deviceIdValue().size() == 22 ? TUYA_CMD_CONTROL_NEW : TUYA_CMD_DP_QUERY
    state.statusCommand = initial
    return initial
}

private List<String> statusDpIds() {
    List<String> ids = []
    ids.addAll(BASE_STATUS_DPS)
    if (activeDeviceProfile() == PROFILE_SIDELINE) {
        ids.addAll(SIDELINE_DISCOVERY_DPS)
    }
    ["power", "heatLevel", "tempSetF", "tempSetC", "flameColor", "flameBrightness", "logColor"].each { String role ->
        String dpId = dpIdFor(role)
        if (dpId) {
            ids << dpId
        }
    }
    return uniqueDpIds(ids)
}

private List<String> discoveryStatusDpIds() {
    List<String> ids = []
    (1..30).each { Integer dp ->
        ids << dp.toString()
    }
    (101..120).each { Integer dp ->
        ids << dp.toString()
    }
    ids.addAll(statusDpIds())
    return uniqueDpIds(ids)
}

private List<String> uniqueDpIds(List<String> dpIds) {
    List<String> ordered = []
    (dpIds ?: []).each { Object raw ->
        Integer normalized = safeInt(raw, null)
        if (normalized != null && normalized > 0) {
            String dpId = normalized.toString()
            if (!(dpId in ordered)) {
                ordered << dpId
            }
        }
    }
    return ordered
}

private String buildStatusPayloadJson(List<String> requestedDps = null) {
    Integer cmd = getStatusCommand()
    List<String> dpsToRequest = uniqueDpIds(requestedDps ?: statusDpIds())
    if (cmd == TUYA_CMD_CONTROL_NEW) {
        Map dps = [:]
        dpsToRequest.each { String dp -> dps[dp] = null }
        return JsonOutput.toJson([
            devId: deviceIdValue(),
            uid:   deviceIdValue(),
            t:     currentEpochSecondsString(),
            dps:   dps
        ])
    }

    return JsonOutput.toJson([
        gwId:  deviceIdValue(),
        devId: deviceIdValue(),
        uid:   deviceIdValue(),
        t:     currentEpochSecondsString()
    ])
}

// ---------------------------------------------------------------------------
// Tuya frame parsing
// ---------------------------------------------------------------------------

private Integer consumeReceiveBuffer() {
    String buffer = safeStr(state.rxBuffer) ?: ""
    Integer processed = 0

    while (buffer.size() >= 32) {
        Integer prefixIndex = buffer.indexOf("000055AA")
        if (prefixIndex == -1) {
            buffer = ""
            break
        }
        if (prefixIndex > 0) {
            buffer = buffer.substring(prefixIndex)
        }
        if (buffer.size() < 32) {
            break
        }

        Integer payloadLength = hexToInt(buffer.substring(24, 32))
        Integer frameHexLength = (16 + payloadLength) * 2
        if (buffer.size() < frameHexLength) {
            break
        }

        String frameHex = buffer.substring(0, frameHexLength)
        buffer = buffer.substring(frameHexLength)
        if (processFrame(frameHex)) {
            processed++
        }
    }

    state.rxBuffer = buffer
    return processed
}

private Boolean processFrame(String frameHex) {
    byte[] frame = hubitat.helper.HexUtils.hexStringToByteArray(frameHex)
    if (frame.length < 28) {
        log.warn "[Touchstone] Ignoring short Tuya frame (${frame.length} bytes)"
        return false
    }

    Long suffix = readUInt32(frame, frame.length - 4)
    if (suffix != TUYA_SUFFIX) {
        log.warn "[Touchstone] Ignoring Tuya frame with bad suffix"
        return false
    }

    Long expectedCrc = readUInt32(frame, frame.length - 8)
    Long actualCrc = crc32(sliceBytes(frame, 0, frame.length - 8))
    if (expectedCrc != actualCrc) {
        log.warn "[Touchstone] Ignoring Tuya frame with CRC mismatch"
        return false
    }

    Integer cmd = (readUInt32(frame, 8) as Integer)
    Integer retcode = (readUInt32(frame, 16) as Integer)
    Integer payloadLength = frame.length - 28
    byte[] payload = payloadLength > 0 ? sliceBytes(frame, 20, payloadLength) : new byte[0]

    debugLog "Received Tuya cmd ${cmd} retcode=${retcode} payloadLen=${payload.length}"

    if (cmd == TUYA_CMD_HEARTBEAT) {
        return true
    }

    Map inFlightRequest = state.inFlight instanceof Map ? (Map) state.inFlight : [:]
    String decoded = decryptTuyaPayload(payload)?.trim()
    if (!decoded) {
        return true
    }

    debugLog "Decoded Tuya payload: ${decoded}"

    if (decoded.contains("data unvalid")) {
        if (getStatusCommand() != TUYA_CMD_CONTROL_NEW) {
            log.warn "[Touchstone] Standard DP_QUERY was rejected; switching to device22 status mode"
            state.statusCommand = TUYA_CMD_CONTROL_NEW
            List<String> requestedDps = inFlightRequest.requestedDps instanceof List ? (List<String>) inFlightRequest.requestedDps : statusDpIds()
            enqueueRequest(TUYA_CMD_CONTROL_NEW, buildStatusPayloadJson(requestedDps), "device22 retry", safeStr(inFlightRequest.captureMode), requestedDps)
        }
        return true
    }

    if (!decoded.startsWith("{")) {
        return true
    }

    Map response = new JsonSlurper().parseText(decoded) as Map
    if (response?.dps instanceof Map) {
        Map<String, Object> dps = normaliseDps(response.dps as Map)
        state.lastDps = dps
        applyDps(dps)
        handleStatusCapture(safeStr(inFlightRequest.captureMode), dps)
    }

    return true
}

private Map<String, Object> normaliseDps(Map dps) {
    Map<String, Object> normalised = [:]
    dps.each { key, value ->
        normalised[safeStr(key)] = value
    }
    return normalised
}

private void applyDps(Map<String, Object> dps) {
    if (dps.containsKey("13")) {
        String deviceUnit = normaliseTempUnit(dps["13"])
        if (deviceUnit) {
            emitAttribute("tempUnit", deviceUnit, "${device.displayName} temperature unit is ${deviceUnit}")
        }
    }

    String powerDpId = dpIdFor("power")
    if (powerDpId && dps.containsKey(powerDpId)) {
        Boolean isOn = asBoolean(dps[powerDpId])
        if (isOn != null) {
            markPowerTransitionIfChanged(isOn)
            applySwitchState(isOn, "physical")
        }
    }

    String heatLevelDpId = dpIdFor("heatLevel")
    if (heatLevelDpId && dps.containsKey(heatLevelDpId)) {
        String heatLevel = DP_TO_HEAT_LEVEL[safeStr(dps[heatLevelDpId])] ?: safeStr(dps[heatLevelDpId])
        emitAttribute("heatLevel", heatLevel, "${device.displayName} heat level is ${heatLevel}")
    }

    String flameColorDpId = dpIdFor("flameColor")
    if (flameColorDpId && dps.containsKey(flameColorDpId)) {
        emitAttribute("flameColor", safeStr(dps[flameColorDpId]), "${device.displayName} flame color is ${dps[flameColorDpId]}")
    }

    String flameBrightnessDpId = dpIdFor("flameBrightness")
    if (flameBrightnessDpId && dps.containsKey(flameBrightnessDpId)) {
        emitAttribute("flameBrightness", safeStr(dps[flameBrightnessDpId]), "${device.displayName} flame brightness is ${dps[flameBrightnessDpId]}")
    }

    String logColorDpId = dpIdFor("logColor")
    if (logColorDpId && dps.containsKey(logColorDpId)) {
        emitAttribute("logColor", safeStr(dps[logColorDpId]), "${device.displayName} log color is ${dps[logColorDpId]}")
    }

    if (activeDeviceProfile() == PROFILE_SIDELINE) {
        if (dps.containsKey("103")) {
            emitAttribute("dp103", safeStr(dps["103"]), "${device.displayName} DP 103 is ${dps["103"]}")
        }
        if (dps.containsKey("105")) {
            emitAttribute("dp105", safeStr(dps["105"]), "${device.displayName} DP 105 is ${dps["105"]}")
        }
        if (dps.containsKey("107")) {
            emitAttribute("dp107", safeStr(dps["107"]), "${device.displayName} DP 107 is ${dps["107"]}")
        }
        if (dps.containsKey("108")) {
            emitAttribute("dp108", safeStr(dps["108"]), "${device.displayName} DP 108 is ${dps["108"]}")
        }
    }

    Integer setpoint = extractHeatingSetpoint(dps)
    if (setpoint != null) {
        if (shouldSuppressSetpointUpdate(dps)) {
            debugLog "Skipping setpoint update during power-transition settle window"
        } else {
            emitAttribute("heatingSetpoint", setpoint, "${device.displayName} heating setpoint is ${setpoint}°${preferredTempUnit()}", null, preferredTempUnit())
        }
    }

    Integer currentTemp = extractCurrentTemperature(dps)
    if (currentTemp != null) {
        emitAttribute("temperature", currentTemp, "${device.displayName} temperature is ${currentTemp}°${preferredTempUnit()}", null, preferredTempUnit())
    }
}

private Integer extractHeatingSetpoint(Map<String, Object> dps) {
    String sourceUnit = sourceTempUnit(dps)
    String preferred = preferredTempUnit()
    String tempSetFDpId = dpIdFor("tempSetF")
    String tempSetCDpId = dpIdFor("tempSetC")
    Integer raw = null

    if (sourceUnit == "F" && tempSetFDpId) {
        raw = safeInt(dps[tempSetFDpId], null)
    } else if (sourceUnit == "C" && tempSetCDpId) {
        raw = safeInt(dps[tempSetCDpId], null)
    }

    if (raw == null) {
        String preferredDpId = preferred == "F" ? tempSetFDpId : tempSetCDpId
        raw = preferredDpId ? safeInt(dps[preferredDpId], null) : null
        sourceUnit = preferred
    }

    if (raw == null) {
        return null
    }
    if (sourceUnit == preferred) {
        return raw
    }
    return preferred == "F" ? celsiusToFahrenheit(raw) : fahrenheitToCelsius(raw)
}

private Integer extractCurrentTemperature(Map<String, Object> dps) {
    String sourceUnit = sourceTempUnit(dps)
    String preferred = preferredTempUnit()
    Integer raw = null

    if (sourceUnit == "F") {
        raw = safeInt(dps["15"], null)
    } else if (sourceUnit == "C") {
        raw = safeInt(dps["3"], null)
    }

    if (raw == null) {
        raw = safeInt(preferred == "F" ? dps["15"] : dps["3"], null)
        sourceUnit = preferred
    }

    if (raw == null) {
        return null
    }
    if (sourceUnit == preferred) {
        return raw
    }
    return preferred == "F" ? celsiusToFahrenheit(raw) : fahrenheitToCelsius(raw)
}

private Boolean shouldSuppressSetpointUpdate(Map<String, Object> dps) {
    if ((now() - safeLong(state.lastPowerTransitionAt, 0L)) > POWER_TRANSITION_SETTLE_MILLIS) {
        return false
    }
    String tempSetFDpId = dpIdFor("tempSetF")
    return sourceTempUnit(dps) == "F" && (tempSetFDpId && dps.containsKey(tempSetFDpId))
}

private String sourceTempUnit(Map<String, Object> dps) {
    String reported = normaliseTempUnit(dps["13"])
    if (reported) {
        return reported
    }
    return preferredTempUnit()
}

// ---------------------------------------------------------------------------
// Discovery helpers
// ---------------------------------------------------------------------------

private void handleStatusCapture(String captureMode, Map<String, Object> dps) {
    switch (captureMode) {
        case "discover":
            log.info "[Touchstone][DiscoverDPs] ${formatDpDump(dps)}"
            break
        case "baseline":
            state.dpBaseline = snapshotDps(dps)
            log.info "[Touchstone][Baseline captured] ${dps.size()} DPs recorded. Now press your remote button, then call captureDiff()."
            break
        case "diff":
            Map<String, Object> baseline = normaliseDps(state.dpBaseline instanceof Map ? (Map) state.dpBaseline : [:])
            List<String> changes = diffDpEntries(baseline, dps)
            if (changes) {
                log.info "[Touchstone][DiscoverDPs DIFF] ${changes.join('  ')}"
            } else {
                log.info "[Touchstone][DiscoverDPs DIFF] No DP changes detected."
            }
            state.remove("dpBaseline")
            break
    }
}

private Map<String, Object> snapshotDps(Map<String, Object> dps) {
    return new JsonSlurper().parseText(JsonOutput.toJson(dps)) as Map<String, Object>
}

private String formatDpDump(Map<String, Object> dps) {
    List<String> parts = sortedDpKeys(dps).collect { String dpId ->
        Object value = dps[dpId]
        "DP ${dpId}=${formatDpValue(value)} (${dpValueType(value)})"
    }
    return parts ? parts.join(", ") : "No DPs returned"
}

private List<String> diffDpEntries(Map<String, Object> before, Map<String, Object> after) {
    List<String> changes = []
    sortedDpKeys(before, after).each { String dpId ->
        if (!sameDpValue(before[dpId], after[dpId])) {
            changes << "DP ${dpId}: ${formatDpValue(before[dpId])} → ${formatDpValue(after[dpId])}"
        }
    }
    return changes
}

private List<String> sortedDpKeys(Map primary, Map secondary = [:]) {
    List<String> keys = []
    (primary ?: [:]).keySet().each { keys << safeStr(it) }
    (secondary ?: [:]).keySet().each { keys << safeStr(it) }
    keys = keys.findAll { it != null }.unique()
    keys.sort { String left, String right ->
        Integer leftInt = safeInt(left, Integer.MAX_VALUE)
        Integer rightInt = safeInt(right, Integer.MAX_VALUE)
        leftInt == rightInt ? (left <=> right) : (leftInt <=> rightInt)
    }
    return keys
}

private Boolean sameDpValue(Object left, Object right) {
    return dpComparableValue(left) == dpComparableValue(right)
}

private String dpComparableValue(Object value) {
    if (value instanceof Map || value instanceof List) {
        return "${dpValueType(value)}:${JsonOutput.toJson(value)}"
    }
    return "${dpValueType(value)}:${safeStr(value)}"
}

// ---------------------------------------------------------------------------
// Attribute helpers
// ---------------------------------------------------------------------------

private void ensureDefaultAttributes() {
    if (device.currentValue("online") == null) {
        emitAttribute("online", "unknown", "${device.displayName} connection state is unknown")
    }
    if (device.currentValue("tempUnit") == null) {
        emitAttribute("tempUnit", preferredTempUnit(), "${device.displayName} preferred temperature unit is ${preferredTempUnit()}")
    }
}

private void applySwitchState(Boolean isOn, String eventType) {
    String switchValue = isOn ? "on" : "off"
    emitAttribute("switch", switchValue, "${device.displayName} was turned ${switchValue}", eventType)
    emitAttribute("power", switchValue, "${device.displayName} power is ${switchValue}", eventType)
}

private void updateOnlineStatus(String value, String detail = null) {
    String description = detail ? "${device.displayName} is ${value} (${detail})" : "${device.displayName} is ${value}"
    emitAttribute("online", value, description)
}

private void emitAttribute(String name, Object value, String descriptionText, String type = null, String unit = null) {
    Map event = [name: name, value: value, descriptionText: descriptionText]
    if (type) {
        event.type = type
    }
    if (unit) {
        event.unit = unit
    }
    sendEvent(event)
}

private void markPowerTransitionIfChanged(Boolean requestedPower) {
    String currentSwitch = safeStr(device.currentValue("switch"))
    String newSwitch = requestedPower ? "on" : "off"
    if (currentSwitch != newSwitch) {
        state.lastPowerTransitionAt = now()
    }
}

// ---------------------------------------------------------------------------
// Conversion / validation helpers
// ---------------------------------------------------------------------------

private Boolean preferencesReady() {
    return deviceIpValue() && deviceIdValue() && localKeyValue() && localKeyValue().size() == 16
}

private String deviceIpValue() {
    return safeStr(settings.deviceIP)?.trim()
}

private String deviceIdValue() {
    return safeStr(settings.deviceId)?.trim()
}

private String localKeyValue() {
    String raw = safeStr(settings.localKey)
    if (!raw) {
        return null
    }
    return raw.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim()
}

private byte[] localKeyBytes() {
    return localKeyValue().getBytes("UTF-8")
}

private String activeDeviceProfile() {
    String configured = safeStr(settings.deviceProfile)?.trim()
    return configured in [PROFILE_SIDELINE, PROFILE_GENERIC, PROFILE_CUSTOM] ? configured : PROFILE_SIDELINE
}

private Integer dpFor(String role) {
    switch (activeDeviceProfile()) {
        case PROFILE_GENERIC:
            return role in ["power", "tempSetC", "heatLevel", "tempSetF"] ? SIDELINE_PROFILE_DPS[role] : null
        case PROFILE_CUSTOM:
            String settingName = CUSTOM_DP_SETTING_NAMES[role]
            if (!settingName) {
                return SIDELINE_PROFILE_DPS[role]
            }
            Integer configured = safeInt(settings[settingName], null)
            return configured != null && configured > 0 ? configured : SIDELINE_PROFILE_DPS[role]
        default:
            return SIDELINE_PROFILE_DPS[role]
    }
}

private String dpIdFor(String role) {
    Integer dpId = dpFor(role)
    return dpId == null ? null : dpId.toString()
}

private Integer mappedCommandDp(String role, String label) {
    Integer targetDp = dpFor(role)
    if (targetDp != null) {
        return targetDp
    }
    if (activeDeviceProfile() == PROFILE_GENERIC) {
        log.warn "[Touchstone] ${label} not mapped for Generic profile — use Custom or setRawDP()"
        return null
    }
    String settingName = CUSTOM_DP_SETTING_NAMES[role]
    if (settingName) {
        log.warn "[Touchstone] ${label} DP is not configured — set ${settingName} in Preferences or use setRawDP()"
    } else {
        log.warn "[Touchstone] ${label} is not mapped"
    }
    return null
}

private String preferredTempUnit() {
    String configured = safeStr(settings.setpointUnit)?.trim()?.toUpperCase()
    return configured in ["F", "C"] ? configured : "F"
}

private void schedulePolling() {
    Integer seconds = safeInt(settings.pollInterval, DEFAULT_POLL_SECONDS)
    switch (seconds) {
        case 0:
            debugLog "Polling disabled"
            break
        case 30:
            schedule("0/30 * * ? * *", "poll")
            break
        case 60:
            runEvery1Minute("poll")
            break
        case 120:
            schedule("0 */2 * ? * *", "poll")
            break
        case 300:
            runEvery5Minutes("poll")
            break
        case 600:
            runEvery10Minutes("poll")
            break
        default:
            if (seconds < 60) {
                schedule("0/${seconds} * * ? * *", "poll")
            } else {
                Integer minutes = Math.max((seconds / 60) as Integer, 1)
                schedule("0 */${minutes} * ? * *", "poll")
            }
            break
    }
}

private Integer clampSetpoint(Integer value, String unit) {
    if (unit == "C") {
        return Math.max(19, Math.min(30, value))
    }
    return Math.max(67, Math.min(88, value))
}

private Integer celsiusToFahrenheit(Integer celsius) {
    return Math.round((celsius * 9.0d / 5.0d) + 32.0d) as Integer
}

private Integer fahrenheitToCelsius(Integer fahrenheit) {
    return Math.round((fahrenheit - 32.0d) * 5.0d / 9.0d) as Integer
}

private Integer hexToInt(String hex) {
    return Integer.parseUnsignedInt(hex, 16)
}

private String sanitizeHex(String text) {
    String cleaned = safeStr(text)?.replaceAll("[^0-9A-Fa-f]", "")?.toUpperCase()
    if (!cleaned) {
        return null
    }
    return cleaned
}

private Integer safeInt(Object value, Integer fallback = null) {
    if (value == null) {
        return fallback
    }
    try {
        return value as Integer
    } catch (ignored) {
        try {
            return Integer.parseInt(value.toString().trim())
        } catch (ignoredToo) {
            return fallback
        }
    }
}

private Long safeLong(Object value, Long fallback = null) {
    if (value == null) {
        return fallback
    }
    try {
        return value as Long
    } catch (ignored) {
        try {
            return Long.parseLong(value.toString().trim())
        } catch (ignoredToo) {
            return fallback
        }
    }
}

private String safeStr(Object value) {
    return value == null ? null : value.toString()
}

private String formatDpValue(Object value) {
    if (value == null) {
        return "null"
    }
    if (value instanceof String || value instanceof GString) {
        String escaped = value.toString().replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"${escaped}\""
    }
    if (value instanceof Map || value instanceof List) {
        return JsonOutput.toJson(value)
    }
    return safeStr(value)
}

private String dpValueType(Object value) {
    if (value == null) {
        return "null"
    }
    if (value instanceof Boolean) {
        return "bool"
    }
    if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
        return "int"
    }
    if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
        return "decimal"
    }
    if (value instanceof String || value instanceof GString) {
        return "string"
    }
    if (value instanceof List) {
        return "list"
    }
    if (value instanceof Map) {
        return "map"
    }
    // Hubitat blocks reflection helpers like getClass(), so unknown DP values log generically.
    return "object"
}

private Boolean asBoolean(Object value) {
    if (value instanceof Boolean) {
        return (Boolean) value
    }
    String text = safeStr(value)?.trim()?.toLowerCase()
    if (text in ["true", "on", "1"]) {
        return true
    }
    if (text in ["false", "off", "0"]) {
        return false
    }
    return null
}

private Object coerceRawValue(String value) {
    String trimmed = safeStr(value)?.trim()
    if (trimmed == null) {
        return ""
    }
    if (trimmed.equalsIgnoreCase("true")) {
        return true
    }
    if (trimmed.equalsIgnoreCase("false")) {
        return false
    }
    if (trimmed ==~ /^-?\d+$/) {
        return Integer.parseInt(trimmed)
    }
    return trimmed
}

private String normaliseTempUnit(Object value) {
    String text = safeStr(value)?.trim()?.toUpperCase()
    if (!text) {
        return null
    }
    if (text.startsWith("F")) {
        return "F"
    }
    if (text.startsWith("C")) {
        return "C"
    }
    return null
}

private String currentEpochSecondsString() {
    return ((Math.floor(now() / 1000.0d)) as Long).toString()
}

private void debugLog(String message) {
    if (settings.logEnable) {
        log.debug "[Touchstone] ${message}"
    }
}

private void infoLog(String message) {
    if (settings.txtEnable != false) {
        log.info "[Touchstone] ${message}"
    }
}
