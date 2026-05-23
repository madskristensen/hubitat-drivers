/**
 * Climate Advisor Device
 * Namespace: madskristensen
 * Author:    Mads Kristensen
 * Version:   0.1.0
 *
 * Single child driver used for BOTH the aggregate house child and per-zone children.
 * Role is set by the parent app via device data value "advisorRole" (aggregate | zone).
 * Zone children also carry "zoneId" data value.
 *
 * Changelog:
 *   0.1.0 — 2026-05-23 — Initial release: unified aggregate + zone child driver for Climate Advisor app; all attributes from Trinity Section 6; Refresh + Notification capability surface.
 */

@Field static final String DRIVER_VERSION = "0.1.0"

metadata {
    definition(
        name:      "Climate Advisor Device",
        namespace: "madskristensen",
        author:    "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/climate-advisor/climate-advisor-device.groovy"
    ) {
        capability "Sensor"
        capability "Refresh"
        capability "Notification"

        // ── Shared attributes (aggregate + zone) ─────────────────────────────
        attribute "severity",              "NUMBER"
        attribute "severityText",          "ENUM",   ["clear", "info", "warning", "alert"]
        attribute "latestMessage",         "STRING"
        attribute "messages",              "STRING"
        attribute "houseStatus",           "STRING"

        // ── Aggregate-only attributes ─────────────────────────────────────────
        attribute "tempTrend",             "ENUM",   ["rising", "falling", "steady", "unknown"]
        attribute "outdoorTrend",          "ENUM",   ["rising", "falling", "steady", "unknown"]
        attribute "outdoorTempSlope10min", "NUMBER"
        attribute "activeAlertCount",      "NUMBER"
        attribute "zoneCount",             "NUMBER"

        // ── Zone attributes (also present on aggregate for pass-through) ──────
        attribute "zoneName",              "STRING"
        attribute "indoorTemp",            "NUMBER"
        attribute "indoorTrend",           "ENUM",   ["rising", "falling", "steady", "unknown"]
        attribute "indoorTempSlope10min",  "NUMBER"
        attribute "openContactCount",      "NUMBER"
        attribute "openContacts",          "STRING"
        attribute "aqi",                   "NUMBER"
    }

    preferences {
        input "logEnable", "bool",
            title: "Enable debug logging (auto-disables after 30 min)", defaultValue: false, required: false
        input "txtEnable", "bool",
            title: "Enable info logging", defaultValue: true, required: false
    }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

def installed() {
    logInfo "Climate Advisor Device installed (role: ${device.getDataValue('advisorRole') ?: 'unknown'})"
    sendEvent(name: "severity",              value: 0)
    sendEvent(name: "severityText",          value: "clear")
    sendEvent(name: "latestMessage",         value: "Initializing…")
    sendEvent(name: "messages",              value: "[]")
    sendEvent(name: "houseStatus",           value: "Initializing…")
    sendEvent(name: "outdoorTrend",          value: "unknown")
    sendEvent(name: "tempTrend",             value: "unknown")
    sendEvent(name: "indoorTrend",           value: "unknown")
    sendEvent(name: "activeAlertCount",      value: 0)
    sendEvent(name: "openContactCount",      value: 0)
    sendEvent(name: "openContacts",          value: "")
}

def updated() {
    logDebug "updated()"
    if (settings.logEnable) { runIn(1800, logsOff) }
}

// ── Commands ──────────────────────────────────────────────────────────────────

def refresh() {
    logDebug "refresh() — delegating to parent app"
    try {
        parent?.refresh()
    } catch (Exception e) {
        log.warn "refresh() error: ${e.message}"
    }
}

def deviceNotification(String text) {
    logInfo "deviceNotification: ${text}"
    sendEvent(name: "latestMessage", value: text)
}

// ── Logging helpers ───────────────────────────────────────────────────────────

private void logDebug(String msg) { if (settings.logEnable) { log.debug "${device.displayName}: ${msg}" } }
private void logInfo(String msg)  { if (settings.txtEnable != false) { log.info "${device.displayName}: ${msg}" } }

def logsOff() {
    log.warn "${device.displayName}: debug logging disabled"
    device.updateSetting("logEnable", [value: false, type: "bool"])
}
