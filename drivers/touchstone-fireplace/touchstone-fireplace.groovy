/**
 * Touchstone Fireplace
 * Author:  Mads Kristensen
 * Version: 0.1.0
 * License: MIT
 *
 * Local LAN control for the Touchstone Sideline Elite fireplace via Tuya Local
 * protocol v3.3 (AES-128-ECB over raw TCP port 6668).
 *
 * Changelog:
 *   0.1.0 — 2026-05-17 — Initial Tuya Local scaffold for power, heat level, flame/log lighting, temperature polling, raw DP surfacing, and socket retry/backoff
 */

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Field static final String DRIVER_VERSION = "0.1.0"
@Field static final String USER_AGENT = "Hubitat Touchstone Fireplace/0.1.0"
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
@Field static final List<Integer> RETRY_DELAYS_SECONDS = [5, 15, 30]
@Field static final List<String> KNOWN_STATUS_DPS = ["1", "2", "3", "5", "13", "14", "15", "101", "102", "103", "104", "105", "107", "108"]
@Field static final Map<String, String> HEAT_LEVEL_TO_DP = ["off": "0", "low": "1", "high": "2"]
@Field static final Map<String, String> DP_TO_HEAT_LEVEL = ["0": "off", "1": "low", "2": "high"]

metadata {
    definition(
        name:         "Touchstone Fireplace",
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
        command "setFlameColor", [[name: "color*", type: "ENUM", constraints: ["1", "2", "3", "4", "5", "6"],
            description: "Raw Tuya enum string for DP 101 (placeholder palette; use setDpRaw() for experiments)."]]
        command "setFlameBrightness", [[name: "level*", type: "ENUM", constraints: ["1", "2", "3", "4", "5"],
            description: "Raw Tuya enum string for DP 102 (placeholder range; verify on hardware)."]]
        command "setLogColor", [[name: "color*", type: "ENUM", constraints: ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"],
            description: "Raw Tuya enum string for DP 104 (placeholder palette; verify on hardware)."]]
        command "setHeatLevel", [[name: "level*", type: "ENUM", constraints: ["off", "low", "high"]]]
        command "setHeatingSetpoint", [[name: "temperature*", type: "NUMBER", description: "Writes DP 14 (F) or DP 2 (C) based on the preferred unit preference."]]
        command "setDpRaw", [[name: "dpId*", type: "NUMBER"], [name: "value*", type: "STRING",
            description: "Advanced: raw DP write. true/false become booleans; whole numbers become integers; everything else is sent as a string."]]

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

        input name: "setpointUnit", type: "enum",
              title: "Preferred setpoint / temperature unit",
              options: ["F": "Fahrenheit (recommended for US Touchstone units)", "C": "Celsius"],
              defaultValue: "F",
              required: true

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
    log.info "Touchstone Fireplace v${DRIVER_VERSION} installed"
    device.updateSetting("pollInterval", [value: "60", type: "enum"])
    device.updateSetting("setpointUnit", [value: "F", type: "enum"])
    device.updateSetting("logEnable", [value: false, type: "bool"])
    device.updateSetting("txtEnable", [value: true, type: "bool"])
    initialize()
}

def updated() {
    log.info "Touchstone Fireplace v${DRIVER_VERSION} preferences updated"
    unschedule()
    state.pendingRequests = []
    state.inFlight = null
    state.awaitingResponse = false
    state.rxBuffer = ""
    state.retryIndex = 0
    state.statusCommand = null
    state.seqNo = 0L
    state.manualSocketCloseAt = null

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
    log.info "Touchstone Fireplace: debug logging auto-disabled after 30 minutes"
}

// ---------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------

def on() {
    infoLog "${device.displayName} switch → on"
    markPowerTransitionIfChanged(true)
    applySwitchState(true, "digital")
    sendDpWrite("1", true, "power on", POWER_REFRESH_DELAY_SECONDS)
}

def off() {
    infoLog "${device.displayName} switch → off"
    markPowerTransitionIfChanged(false)
    applySwitchState(false, "digital")
    sendDpWrite("1", false, "power off", POWER_REFRESH_DELAY_SECONDS)
}

def refresh() {
    if (!preferencesReady()) {
        log.warn "[Touchstone] refresh() skipped — configure device IP, device ID, and local key first"
        updateOnlineStatus("unknown", "Configuration incomplete")
        return
    }

    enqueueRequest(getStatusCommand(), buildStatusPayloadJson(), "refresh")
}

def poll() {
    refresh()
}

def setFlameColor(String color) {
    String raw = safeStr(color)?.trim()
    if (!raw) {
        log.warn "[Touchstone] setFlameColor requires a raw enum value"
        return
    }

    infoLog "${device.displayName} flame color → ${raw}"
    emitAttribute("flameColor", raw, "${device.displayName} flame color set to ${raw}", "digital")
    sendDpWrite("101", raw, "flame color", WRITE_REFRESH_DELAY_SECONDS)
}

def setFlameBrightness(String level) {
    String raw = safeStr(level)?.trim()
    if (!raw) {
        log.warn "[Touchstone] setFlameBrightness requires a raw enum value"
        return
    }

    infoLog "${device.displayName} flame brightness → ${raw}"
    emitAttribute("flameBrightness", raw, "${device.displayName} flame brightness set to ${raw}", "digital")
    sendDpWrite("102", raw, "flame brightness", WRITE_REFRESH_DELAY_SECONDS)
}

def setLogColor(String color) {
    String raw = safeStr(color)?.trim()
    if (!raw) {
        log.warn "[Touchstone] setLogColor requires a raw enum value"
        return
    }

    infoLog "${device.displayName} log color → ${raw}"
    emitAttribute("logColor", raw, "${device.displayName} log color set to ${raw}", "digital")
    sendDpWrite("104", raw, "log color", WRITE_REFRESH_DELAY_SECONDS)
}

def setHeatLevel(String level) {
    String normalized = safeStr(level)?.trim()?.toLowerCase()
    if (!(normalized in ["off", "low", "high"])) {
        log.warn "[Touchstone] setHeatLevel: invalid level '${level}' — use off, low, or high"
        return
    }

    infoLog "${device.displayName} heat level → ${normalized}"
    emitAttribute("heatLevel", normalized, "${device.displayName} heat level set to ${normalized}", "digital")
    sendDpWrite("5", HEAT_LEVEL_TO_DP[normalized], "heat level", WRITE_REFRESH_DELAY_SECONDS)
}

def setHeatingSetpoint(temp) {
    Integer requested = safeInt(temp, null)
    if (requested == null) {
        log.warn "[Touchstone] setHeatingSetpoint: '${temp}' is not a valid whole-number temperature"
        return
    }

    String unit = preferredTempUnit()
    Integer clamped = clampSetpoint(requested, unit)
    String dpId = unit == "F" ? "14" : "2"

    infoLog "${device.displayName} heating setpoint → ${clamped}°${unit}"
    emitAttribute("heatingSetpoint", clamped, "${device.displayName} heating setpoint set to ${clamped}°${unit}", "digital", unit)
    sendDpWrite(dpId, clamped, "heating setpoint", WRITE_REFRESH_DELAY_SECONDS)
}

def setDpRaw(dpId, String value) {
    Integer targetDp = safeInt(dpId, null)
    if (targetDp == null || targetDp <= 0) {
        log.warn "[Touchstone] setDpRaw: dpId must be a positive integer"
        return
    }

    Object coerced = coerceRawValue(value)
    infoLog "${device.displayName} raw DP ${targetDp} → ${coerced}"
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
        debugLog "parse() exception class=${e.getClass().getName()}"
    }
}

private void enqueueRequest(Integer cmd, String payloadJson, String reason) {
    List<Map> queue = pendingRequestQueue()
    queue << [cmd: cmd, payloadJson: payloadJson, reason: reason]
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
            queue << [cmd: entry.cmd, payloadJson: entry.payloadJson, reason: entry.reason]
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

// ---------------------------------------------------------------------------
// Tuya framing / crypto
// ---------------------------------------------------------------------------

private byte[] buildTuyaFrame(Integer cmd, String payloadJson) {
    byte[] payloadBytes = payloadJson.getBytes("UTF-8")
    byte[] encryptedPayload = encryptTuyaPayload(cmd, payloadBytes)

    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    buffer.write(intToBytes(TUYA_PREFIX))
    buffer.write(intToBytes(nextSeqNo()))
    buffer.write(intToBytes(cmd as Long))
    buffer.write(intToBytes((encryptedPayload.length + 8) as Long))
    buffer.write(encryptedPayload)

    byte[] withoutCrc = buffer.toByteArray()
    Long crc = crc32(withoutCrc)
    buffer.write(intToBytes(crc))
    buffer.write(intToBytes(TUYA_SUFFIX))
    return buffer.toByteArray()
}

private byte[] encryptTuyaPayload(Integer cmd, byte[] payloadBytes) {
    byte[] encrypted = aesEncrypt(payloadBytes, localKeyBytes())
    if (!commandNeedsVersionHeader(cmd)) {
        return encrypted
    }

    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    buffer.write(protocol33HeaderBytes())
    buffer.write(encrypted)
    return buffer.toByteArray()
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

private Long crc32(byte[] data) {
    CRC32 crc = new CRC32()
    crc.update(data)
    return crc.getValue() & 0xFFFFFFFFL
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
    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    buffer.write(TUYA_VERSION.getBytes("UTF-8"))
    for (Integer i = 0; i < 12; i++) {
        buffer.write(0x00)
    }
    return buffer.toByteArray()
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

private String buildStatusPayloadJson() {
    Integer cmd = getStatusCommand()
    if (cmd == TUYA_CMD_CONTROL_NEW) {
        Map dps = [:]
        KNOWN_STATUS_DPS.each { String dp -> dps[dp] = null }
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

    String decoded = decryptTuyaPayload(payload)?.trim()
    if (!decoded) {
        return true
    }

    debugLog "Decoded Tuya payload: ${decoded}"

    if (decoded.contains("data unvalid")) {
        if (getStatusCommand() != TUYA_CMD_CONTROL_NEW) {
            log.warn "[Touchstone] Standard DP_QUERY was rejected; switching to device22 status mode"
            state.statusCommand = TUYA_CMD_CONTROL_NEW
            enqueueRequest(TUYA_CMD_CONTROL_NEW, buildStatusPayloadJson(), "device22 retry")
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

    if (dps.containsKey("1")) {
        Boolean isOn = asBoolean(dps["1"])
        if (isOn != null) {
            markPowerTransitionIfChanged(isOn)
            applySwitchState(isOn, "physical")
        }
    }

    if (dps.containsKey("5")) {
        String heatLevel = DP_TO_HEAT_LEVEL[safeStr(dps["5"])] ?: safeStr(dps["5"])
        emitAttribute("heatLevel", heatLevel, "${device.displayName} heat level is ${heatLevel}")
    }

    if (dps.containsKey("101")) {
        emitAttribute("flameColor", safeStr(dps["101"]), "${device.displayName} flame color is ${dps["101"]}")
    }

    if (dps.containsKey("102")) {
        emitAttribute("flameBrightness", safeStr(dps["102"]), "${device.displayName} flame brightness is ${dps["102"]}")
    }

    if (dps.containsKey("104")) {
        emitAttribute("logColor", safeStr(dps["104"]), "${device.displayName} log color is ${dps["104"]}")
    }

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
    Integer raw = null

    if (sourceUnit == "F") {
        raw = safeInt(dps["14"], null)
    } else if (sourceUnit == "C") {
        raw = safeInt(dps["2"], null)
    }

    if (raw == null) {
        raw = safeInt(preferred == "F" ? dps["14"] : dps["2"], null)
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
    return sourceTempUnit(dps) == "F" || dps.containsKey("14")
}

private String sourceTempUnit(Map<String, Object> dps) {
    String reported = normaliseTempUnit(dps["13"])
    if (reported) {
        return reported
    }
    return preferredTempUnit()
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
