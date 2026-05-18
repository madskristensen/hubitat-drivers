/**
 * Touchstone / Tuya Fireplace
 * Author:  Mads Kristensen
 * Version: 0.1.17
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
 *   0.1.17 — 2026-05-17 — rename setLogColor → setCharcoalColor with verified labels
 *   0.1.16 — 2026-05-17 — gate v0.1.15 diagnostic flame-color logs
 *   0.1.15 — 2026-05-17 — setFlameColor verified Tuya app labels + wire debug logging
 *   0.1.14 — 2026-05-17 — colors back to NUMBER; drop setDpRaw legacy alias
 *   0.1.13 — 2026-05-17 — named-enum dropdowns for brightness and color palettes
 *   0.1.12 — 2026-05-17 — work around Hubitat Commands-tab dropdown +1 bug
 *   0.1.11 — 2026-05-17 — remove dead setLogBrightness; fix write-side off-by-one
 *   0.1.10 — 2026-05-17 — fix off-by-one in inbound enum DP display
 *   0.1.9 — 2026-05-17 — default log brightness preference
 *   0.1.8 — 2026-05-17 — default flame speed preference
 *   0.1.7 — 2026-05-17 — hotfix: restore setHeatLevel signature
 *   0.1.6 — 2026-05-17 — flame speed & log brightness, drop duplicate power attribute
 *   0.1.5 — 2026-05-17 — BUGFIX: removed paragraph() from preferences (Hubitat app-only, not allowed in drivers)
 *   0.1.4 — 2026-05-17 — SAFETY: removed defaultHeatLevel auto-apply; BUGFIX: removed sandbox-blocked reflection logging
 *   0.1.3 — 2026-05-17 — Optional default-on-power-on settings for flame color, log color, brightness, heat level, and temp setpoint
 *   0.1.2 — 2026-05-17 — Replaced blocked CRC32 import with pure-Groovy implementation (Hubitat import allowlist)
 *   0.1.1 — 2026-05-17 — Generalized device profiles, in-driver DP discovery, and auditable raw DP writes
 *   0.1.0 — 2026-05-17 — Initial Tuya Local scaffold for power, heat level, flame/log lighting, temperature polling, raw DP surfacing, and socket retry/backoff
 */
// v0.1.15 — Restored named-ENUM for setFlameColor with authoritative labels from the Tuya mobile app
//           (Orange/Blue/White/Orange+Blue/Orange+White/Blue+White). DP 101 value "1" = Orange (app default);
//           v0.1.13's invented labels were wrong — picking "Orange" sent DP=2 which is actually Blue.
//           Also added unconditional log.info in setFlameColor (write) and applyDps DP 101 (echo) so
//           Mads can confirm wire values and device echo in Hubitat logs. setLogColor (DP 104) was NUMBER at this point;
//           renamed setCharcoalColor with verified Tuya labels in v0.1.17.
// v0.1.14 — Reverted setFlameColor and setLogColor from v0.1.13's named ENUM back to NUMBER input
//           (ranges 1-6 and 1-12). The invented color labels (Red, Orange, ..., Crimson, Coral, ...)
//           were guesses without hardware verification; numeric input is honest about the fact that
//           the indices are user-discoverable. setFlameBrightness keeps its named ENUM since
//           Dimmest/Dim/Medium/Brighter/Brightest are universally meaningful regardless of hardware.
//           Also removed the legacy setDpRaw() command alias (use setRawDP() instead).
// v0.1.13 — Reverted setFlameBrightness, setFlameColor, and setLogColor back to ENUM — but now with
//           human-readable named labels instead of numeric strings. Non-numeric ENUM labels do NOT trigger
//           the Hubitat Commands-tab dropdown +1 quirk (that was specific to pure numeric-string values).
//           Setters accept a label (e.g., "Medium"), look up the wire DP value via a label→DP map, and emit
//           the label as the attribute value. applyDps() performs the reverse: DP value → label via DP_TO_* maps.
//           Power-on defaults use the same label→DP lookup. Color label names are PLACEHOLDER best-guesses
//           pending hardware verification by the user.
// v0.1.12 — Converted setFlameBrightness, setFlameColor, and setLogColor from ENUM (with numeric-string
//           constraints) to NUMBER (with range constraints). Hubitat's Commands-tab dropdown advances enum
//           entries by one position after Set when the values are numeric strings — platform UI quirk, not a
//           driver bug. NUMBER-typed parameters render as input fields, sidestepping the dropdown widget.
//           Handlers now accept a numeric argument, convert to string label, and proceed as before.
// v0.1.11 — remove dead setLogBrightness (DP 105 confirmed read-only on Sideline Elite firmware); add defensive
//           input-validation guards to setFlameColor, setFlameBrightness, setLogColor. Note: write-side emit in
//           v0.1.10 already used the input label directly — no OPTIONS[dpValue] off-by-one was present in those
//           setters. If the +1 display symptom persists it is in the device echo path (applyDps), not the write path.
// v0.1.10 — off-by-one fix: inbound enum DP values are now validated before emit; unrecognised values log-and-bail.
// v0.1.9 — defaultLogBrightness preference: completes v0.1.6 symmetry for DP 105.
// v0.1.8 — defaultFlameSpeed preference: auto-applies during the power-on defaults window.
// v0.1.7 — hotfix: restore setHeatLevel signature (parse error introduced in v0.1.6).
// v0.1.6 — Added setFlameSpeed (DP 103) + setLogBrightness (DP 105); removed duplicate `power` attribute.
// v0.1.5 — BUGFIX: removed paragraph() from preferences (Hubitat app-only, not allowed in drivers).
// v0.1.4 — SAFETY: removed defaultHeatLevel auto-apply (heater never auto-starts); BUGFIX: replaced reflection (e.getClass()) with sandbox-safe exception logging.

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@Field static final String DRIVER_VERSION = "0.1.17"
@Field static final String USER_AGENT = "Hubitat Touchstone-Tuya Fireplace/0.1.15"
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
@Field static final List<String> FLAME_COLOR_OPTIONS = ["Orange", "Blue", "White", "Orange+Blue", "Orange+White", "Blue+White"]
@Field static final Map<String, String> FLAME_COLOR_TO_DP = [
    "Orange":       "1",
    "Blue":         "2",
    "White":        "3",
    "Orange+Blue":  "4",
    "Orange+White": "5",
    "Blue+White":   "6"
]
@Field static final Map<String, String> DP_TO_FLAME_COLOR = [
    "1": "Orange",
    "2": "Blue",
    "3": "White",
    "4": "Orange+Blue",
    "5": "Orange+White",
    "6": "Blue+White"
]
@Field static final List<String> FLAME_BRIGHTNESS_OPTIONS = ["Dimmest", "Dim", "Medium", "Brighter", "Brightest"]
@Field static final List<String> FLAME_SPEED_OPTIONS = ["Slow", "Medium", "Fast"]
@Field static final List<String> CHARCOAL_COLOR_OPTIONS = [
    "Orange", "Red", "Blue", "Yellow", "Green", "Purple",
    "Cyan", "Magenta", "White", "Pink", "Rainbow", "Spotlight"
]
@Field static final Map<String, String> CHARCOAL_COLOR_TO_DP = [
    "Orange": "1",  "Red": "2",     "Blue": "3",    "Yellow": "4",
    "Green": "5",   "Purple": "6",  "Cyan": "7",    "Magenta": "8",
    "White": "9",   "Pink": "10",   "Rainbow": "11","Spotlight": "12"
]
@Field static final Map<String, String> DP_TO_CHARCOAL_COLOR = [
    "1": "Orange",  "2": "Red",     "3": "Blue",    "4": "Yellow",
    "5": "Green",   "6": "Purple",  "7": "Cyan",    "8": "Magenta",
    "9": "White",   "10": "Pink",   "11": "Rainbow","12": "Spotlight"
]
@Field static final List<String> HEAT_LEVEL_OPTIONS = ["off", "low", "high"]
@Field static final List<String> BASE_STATUS_DPS = ["1", "2", "3", "5", "13", "14", "15"]
@Field static final List<String> SIDELINE_DISCOVERY_DPS = ["101", "102", "103", "104", "105", "107", "108"]
@Field static final Map<String, Integer> SIDELINE_PROFILE_DPS = [power: 1, tempSetC: 2, heatLevel: 5, tempSetF: 14, flameColor: 101, flameBrightness: 102, flameSpeed: 103, charcoalColor: 104]
@Field static final Map<String, String> CUSTOM_DP_SETTING_NAMES = [power: "powerDp", flameColor: "flameColorDp", flameBrightness: "flameBrightnessDp", charcoalColor: "charcoalColorDp", heatLevel: "heatLevelDp", tempSetF: "tempSetFDp", tempSetC: "tempSetCDp"]
@Field static final Map<String, String> HEAT_LEVEL_TO_DP = ["off": "0", "low": "1", "high": "2"]
@Field static final Map<String, String> DP_TO_HEAT_LEVEL = ["0": "off", "1": "low", "2": "high"]
@Field static final Map<String, String> FLAME_BRIGHTNESS_TO_DP = ["Dimmest": "1", "Dim": "2", "Medium": "3", "Brighter": "4", "Brightest": "5"]
@Field static final Map<String, String> DP_TO_FLAME_BRIGHTNESS = ["1": "Dimmest", "2": "Dim", "3": "Medium", "4": "Brighter", "5": "Brightest"]

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
        command "setFlameColor", [[name: "color", type: "ENUM", description: "Flame color palette (verified Tuya app labels)", constraints: FLAME_COLOR_OPTIONS]]
        command "setFlameBrightness", [[name: "level", type: "ENUM", description: "Flame brightness (DP 102)", constraints: FLAME_BRIGHTNESS_OPTIONS]]
        command "setFlameSpeed", [[name: "speed*", type: "ENUM", constraints: FLAME_SPEED_OPTIONS,
            description: "Flame animation speed (Sideline Elite DP 103). Slow/Medium/Fast — verify labels on real hardware."]]
        command "setCharcoalColor", [[name: "color", type: "ENUM", description: "Charcoal/log color palette (verified Tuya app)", constraints: CHARCOAL_COLOR_OPTIONS]]
        command "setHeatLevel", [[name: "level*", type: "ENUM", constraints: HEAT_LEVEL_OPTIONS]]
        command "setHeatingSetpoint", [[name: "temperature*", type: "NUMBER", description: "Writes the mapped Fahrenheit or Celsius setpoint DP based on the preferred unit preference."]]
        command "setRawDP", [[name: "dpId*", type: "NUMBER"], [name: "value*", type: "STRING",
            description: "Advanced: raw DP write. true/false become booleans; whole numbers become integers; everything else is sent as a string."]]
        command "discoverDPs"
        command "captureBaseline"
        command "captureDiff"

        attribute "flameColor",       "string"
        attribute "flameBrightness",  "string"
        attribute "flameSpeed",       "enum",   FLAME_SPEED_OPTIONS
        attribute "charcoalColor",    "enum",   CHARCOAL_COLOR_OPTIONS
        attribute "heatLevel",        "enum",   ["off", "low", "high"]
        attribute "heatingSetpoint",  "number"
        attribute "online",           "enum",   ["online", "offline", "unknown"]
        attribute "dp103",            "string"
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
            input name: "charcoalColorDp", type: "number", title: "Charcoal Color DP", defaultValue: 104
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

            input name: "defaultFlameSpeed", type: "enum",
                  title: "Default flame speed (applied on power-on)",
                  description: "Applied ~1.5s after Hubitat turns the fireplace on. Leave blank to keep the fireplace firmware's last-known flame speed.",
                  options: FLAME_SPEED_OPTIONS,
                  required: false

            input name: "defaultCharcoalColor", type: "enum",
                  title: "Default charcoal color (optional)",
                  description: "Applied ~1.5s after Hubitat turns the fireplace on. Leave blank to keep the fireplace firmware's last-known charcoal color.",
                  options: CHARCOAL_COLOR_OPTIONS,
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

def setFlameColor(color) {
    String label = safeStr(color)?.trim()
    if (!(FLAME_COLOR_TO_DP.containsKey(label))) {
        log.warn "[Touchstone] setFlameColor: invalid color '${color}' — use ${FLAME_COLOR_OPTIONS.join(', ')}"
        return
    }

    Integer flameColorDp = mappedCommandDp("flameColor", "Flame color")
    if (flameColorDp == null) {
        return
    }

    String dpValue = FLAME_COLOR_TO_DP[label]
    debugLog "setFlameColor: sending DP ${flameColorDp} = '${dpValue}' (label '${label}') to device"
    infoLog "${device.displayName} flame color → ${label}"
    emitAttribute("flameColor", label, "${device.displayName} flame color set to ${label}", "digital")
    sendDpWrite(flameColorDp.toString(), dpValue, "flame color", WRITE_REFRESH_DELAY_SECONDS)
}

def setFlameBrightness(level) {
    String label = safeStr(level)?.trim()
    if (!(label in FLAME_BRIGHTNESS_OPTIONS)) {
        log.warn "[Touchstone] setFlameBrightness: invalid level '${level}' — use ${FLAME_BRIGHTNESS_OPTIONS.join(', ')}"
        return
    }

    Integer flameBrightnessDp = mappedCommandDp("flameBrightness", "Flame brightness")
    if (flameBrightnessDp == null) {
        return
    }

    String dpValue = FLAME_BRIGHTNESS_TO_DP[label]
    infoLog "${device.displayName} flame brightness → ${label}"
    emitAttribute("flameBrightness", label, "${device.displayName} flame brightness set to ${label}", "digital")
    sendDpWrite(flameBrightnessDp.toString(), dpValue, "flame brightness", WRITE_REFRESH_DELAY_SECONDS)
}

def setCharcoalColor(color) {
    String label = safeStr(color)?.trim()
    if (!(CHARCOAL_COLOR_TO_DP.containsKey(label))) {
        log.warn "[Touchstone] setCharcoalColor: invalid color '${color}' — use ${CHARCOAL_COLOR_OPTIONS.join(', ')}"
        return
    }

    Integer charcoalColorDp = mappedCommandDp("charcoalColor", "Charcoal color")
    if (charcoalColorDp == null) {
        return
    }

    String dpValue = CHARCOAL_COLOR_TO_DP[label]
    debugLog "setCharcoalColor: sending DP ${charcoalColorDp} = '${dpValue}' (label '${label}') to device"
    infoLog "${device.displayName} charcoal color → ${label}"
    emitAttribute("charcoalColor", label, "${device.displayName} charcoal color set to ${label}", "digital")
    sendDpWrite(charcoalColorDp.toString(), dpValue, "charcoal color", WRITE_REFRESH_DELAY_SECONDS)
}

// DP 103 — flame animation speed (Slow="1", Medium="2", Fast="3").
// Label↔value mapping is community-derived; Switch should verify on real Sideline Elite hardware.
@Field static final Map<String, String> FLAME_SPEED_TO_DP = ["Slow": "1", "Medium": "2", "Fast": "3"]
@Field static final Map<String, String> DP_TO_FLAME_SPEED = ["1": "Slow", "2": "Medium", "3": "Fast"]

def setFlameSpeed(String speed) {
    String label = safeStr(speed)?.trim()
    if (!(label in FLAME_SPEED_OPTIONS)) {
        log.warn "[Touchstone] setFlameSpeed: invalid speed '${speed}' — use Slow, Medium, or Fast"
        return
    }

    Integer flameSpeedDp = mappedCommandDp("flameSpeed", "Flame speed")
    if (flameSpeedDp == null) {
        return
    }

    String dpValue = FLAME_SPEED_TO_DP[label]
    infoLog "${device.displayName} flame speed → ${label}"
    emitAttribute("flameSpeed", label, "${device.displayName} flame speed set to ${label}", "digital")
    sendDpWrite(flameSpeedDp.toString(), dpValue, "flame speed", WRITE_REFRESH_DELAY_SECONDS)
}

def setHeatLevel(level) {
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
    if (flameColor && FLAME_COLOR_TO_DP.containsKey(flameColor)) {
        Integer flameColorDp = dpFor("flameColor")
        if (flameColorDp != null) {
            String flameColorDpValue = FLAME_COLOR_TO_DP[flameColor]
            emitAttribute("flameColor", flameColor, "${device.displayName} default flame color set to ${flameColor}", "digital")
            infoLog "Applied default: flameColor=${flameColor}"
            sendDpWrite(flameColorDp.toString(), flameColorDpValue, "${POWER_ON_DEFAULT_REASON_PREFIX}flame color", WRITE_REFRESH_DELAY_SECONDS)
            appliedAny = true
        } else {
            log.warn "[Touchstone] defaultFlameColor is set but flame color is not mapped for profile '${activeDeviceProfile()}'"
        }
    }

    String flameBrightness = safeStr(settings.defaultFlameBrightness)?.trim()
    if (flameBrightness && FLAME_BRIGHTNESS_TO_DP.containsKey(flameBrightness)) {
        Integer flameBrightnessDp = dpFor("flameBrightness")
        if (flameBrightnessDp != null) {
            String flameBrightnessDpValue = FLAME_BRIGHTNESS_TO_DP[flameBrightness]
            emitAttribute("flameBrightness", flameBrightness, "${device.displayName} default flame brightness set to ${flameBrightness}", "digital")
            infoLog "Applied default: flameBrightness=${flameBrightness}"
            sendDpWrite(flameBrightnessDp.toString(), flameBrightnessDpValue, "${POWER_ON_DEFAULT_REASON_PREFIX}flame brightness", WRITE_REFRESH_DELAY_SECONDS)
            appliedAny = true
        } else {
            log.warn "[Touchstone] defaultFlameBrightness is set but flame brightness is not mapped for profile '${activeDeviceProfile()}'"
        }
    }

    String flameSpeed = safeStr(settings.defaultFlameSpeed)?.trim()
    if (flameSpeed && flameSpeed in FLAME_SPEED_OPTIONS) {
        Integer flameSpeedDp = dpFor("flameSpeed")
        if (flameSpeedDp != null) {
            String flameSpeedDpValue = FLAME_SPEED_TO_DP[flameSpeed]
            emitAttribute("flameSpeed", flameSpeed, "${device.displayName} default flame speed set to ${flameSpeed}", "digital")
            infoLog "Applied default: flameSpeed=${flameSpeed}"
            sendDpWrite(flameSpeedDp.toString(), flameSpeedDpValue, "${POWER_ON_DEFAULT_REASON_PREFIX}flame speed", WRITE_REFRESH_DELAY_SECONDS)
            appliedAny = true
        } else {
            log.warn "[Touchstone] defaultFlameSpeed is set but flame speed is not mapped for profile '${activeDeviceProfile()}'"
        }
    }

    String charcoalColor = safeStr(settings.defaultCharcoalColor)?.trim()
    if (charcoalColor && CHARCOAL_COLOR_TO_DP.containsKey(charcoalColor)) {
        Integer charcoalColorDp = dpFor("charcoalColor")
        if (charcoalColorDp != null) {
            String charcoalColorDpVal = CHARCOAL_COLOR_TO_DP[charcoalColor]
            emitAttribute("charcoalColor", charcoalColor, "${device.displayName} default charcoal color set to ${charcoalColor}", "digital")
            infoLog "Applied default: charcoalColor=${charcoalColor}"
            sendDpWrite(charcoalColorDp.toString(), charcoalColorDpVal, "${POWER_ON_DEFAULT_REASON_PREFIX}charcoal color", WRITE_REFRESH_DELAY_SECONDS)
            appliedAny = true
        } else {
            log.warn "[Touchstone] defaultCharcoalColor is set but charcoal color is not mapped for profile '${activeDeviceProfile()}'"
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
    ["power", "heatLevel", "tempSetF", "tempSetC", "flameColor", "flameBrightness", "charcoalColor"].each { String role ->
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
        String flameColorVal = safeStr(dps[flameColorDpId])
        String label = DP_TO_FLAME_COLOR[flameColorVal]
        if (label) {
            debugLog "applyDps: received DP 101 = '${flameColorVal}' → '${label}'"
            emitAttribute("flameColor", label, "${device.displayName} flame color is ${label}")
        } else {
            log.warn "[Touchstone] applyDps: ignoring unrecognised flameColor DP value '${flameColorVal}'"
        }
    }

    String flameBrightnessDpId = dpIdFor("flameBrightness")
    if (flameBrightnessDpId && dps.containsKey(flameBrightnessDpId)) {
        String flameBrightnessVal = safeStr(dps[flameBrightnessDpId])
        String flameBrightnessLabel = DP_TO_FLAME_BRIGHTNESS[flameBrightnessVal]
        if (flameBrightnessLabel) {
            emitAttribute("flameBrightness", flameBrightnessLabel, "${device.displayName} flame brightness is ${flameBrightnessLabel}")
        } else {
            log.warn "[Touchstone] applyDps: ignoring unrecognised flameBrightness DP value '${flameBrightnessVal}'"
        }
    }

    String charcoalColorDpId = dpIdFor("charcoalColor")
    if (charcoalColorDpId && dps.containsKey(charcoalColorDpId)) {
        String charcoalColorVal = safeStr(dps[charcoalColorDpId])
        String label = DP_TO_CHARCOAL_COLOR[charcoalColorVal]
        if (label) {
            emitAttribute("charcoalColor", label, "${device.displayName} charcoal color is ${label}")
        } else {
            log.warn "[Touchstone] applyDps: ignoring unrecognised charcoalColor DP value '${charcoalColorVal}'"
        }
    }

    if (activeDeviceProfile() == PROFILE_SIDELINE) {
        if (dps.containsKey("103")) {
            String rawSpeed = safeStr(dps["103"])
            emitAttribute("dp103", rawSpeed, "${device.displayName} DP 103 is ${rawSpeed}")
            String speedLabel = DP_TO_FLAME_SPEED[rawSpeed]
            if (speedLabel) {
                emitAttribute("flameSpeed", speedLabel, "${device.displayName} flame speed is ${speedLabel}")
            } else {
                log.warn "[Touchstone] applyDps: ignoring unrecognised flameSpeed DP 103 value '${rawSpeed}'"
            }
        }
        if (dps.containsKey("105")) {
            // DP 105 is confirmed read-only / unimplemented on Sideline Elite firmware.
            // Silently absorb any status updates at debug level only.
            debugLog "applyDps: received DP 105 = ${safeStr(dps["105"])} (read-only on this firmware; ignored)"
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
