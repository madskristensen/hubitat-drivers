/**
 * Climate Advisor Device
 * Namespace: mads
 * Author:    Mads Kristensen
 * Version:   0.2.1
 *
 * House-wide aggregate child driver for the Climate Advisor app (one device per installation).
 * Per-zone data exposed via zoneStatuses JSON attribute and indexed flat attributes zone1..zone10.
 *
 * Changelog:
 *   0.2.1 — 2026-05-23 — Single-child architecture, optional dashboard children, 4-level severity restored, namespace fix, null-slope guard, dedicated indoor temp handler, comfort-open advisory
 *   0.1.0 — 2026-05-23 — Initial release: unified aggregate + zone child driver for Climate Advisor app; all attributes from Trinity Section 6; Refresh + Notification capability surface.
 */

@Field static final String DRIVER_VERSION = "0.2.1"

metadata {
    definition(
        name:      "Climate Advisor Device",
        namespace: "mads",
        author:    "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/climate-advisor/climate-advisor-device.groovy"
    ) {
        capability "Sensor"
        capability "Refresh"
        capability "Notification"
        capability "ContactSensor"

        // ── Aggregate status ──────────────────────────────────────────────────
        attribute "severity",              "NUMBER"
        attribute "severityText",          "ENUM",   ["clear", "info", "warning", "danger"]
        attribute "latestMessage",         "STRING"
        attribute "messages",              "STRING"
        attribute "houseStatus",           "STRING"
        attribute "contact",               "ENUM",   ["closed", "open"]
        attribute "acknowledged",          "ENUM",   ["false", "true"]

        // ── Outdoor trend ─────────────────────────────────────────────────────
        attribute "outdoorTrend",          "ENUM",   ["rising", "falling", "steady", "unknown"]
        attribute "tempTrend",             "ENUM",   ["rising", "falling", "steady", "unknown"]  // TODO v0.3: remove legacy alias
        attribute "outdoorTempSlope10min", "NUMBER"

        // ── Aggregate counters ────────────────────────────────────────────────
        attribute "activeAlertCount",      "NUMBER"
        attribute "zoneCount",             "NUMBER"
        attribute "openContactCount",      "NUMBER"

        // ── Per-zone JSON dump (SharpTools Custom Tiles / webCoRE) ────────────
        // Keys: zone name → {severity, severityText, latestMessage, indoorTemp, openContactCount, aqi}
        attribute "zoneStatuses",          "STRING"

        // ── Per-zone indexed flat attributes (slots 1–10) ─────────────────────
        // Unused slots stay at "" / 0 / "". Rule Machine can compare zoneNSeverity directly.
        attribute "zone1Name",     "STRING"
        attribute "zone1Severity", "NUMBER"
        attribute "zone1Message",  "STRING"
        attribute "zone2Name",     "STRING"
        attribute "zone2Severity", "NUMBER"
        attribute "zone2Message",  "STRING"
        attribute "zone3Name",     "STRING"
        attribute "zone3Severity", "NUMBER"
        attribute "zone3Message",  "STRING"
        attribute "zone4Name",     "STRING"
        attribute "zone4Severity", "NUMBER"
        attribute "zone4Message",  "STRING"
        attribute "zone5Name",     "STRING"
        attribute "zone5Severity", "NUMBER"
        attribute "zone5Message",  "STRING"
        attribute "zone6Name",     "STRING"
        attribute "zone6Severity", "NUMBER"
        attribute "zone6Message",  "STRING"
        attribute "zone7Name",     "STRING"
        attribute "zone7Severity", "NUMBER"
        attribute "zone7Message",  "STRING"
        attribute "zone8Name",     "STRING"
        attribute "zone8Severity", "NUMBER"
        attribute "zone8Message",  "STRING"
        attribute "zone9Name",     "STRING"
        attribute "zone9Severity", "NUMBER"
        attribute "zone9Message",  "STRING"
        attribute "zone10Name",    "STRING"
        attribute "zone10Severity","NUMBER"
        attribute "zone10Message", "STRING"

        command "clearMessages"
        command "acknowledge"
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
    logInfo "Climate Advisor Device installed"
    sendEvent(name: "severity",              value: 0)
    sendEvent(name: "severityText",          value: "clear")
    sendEvent(name: "latestMessage",         value: "Initializing…")
    sendEvent(name: "messages",              value: "[]")
    sendEvent(name: "houseStatus",           value: "House — initializing…")
    sendEvent(name: "contact",               value: "closed")
    sendEvent(name: "acknowledged",          value: "false")
    sendEvent(name: "outdoorTrend",          value: "unknown")
    sendEvent(name: "tempTrend",             value: "unknown")
    sendEvent(name: "activeAlertCount",      value: 0)
    sendEvent(name: "openContactCount",      value: 0)
    sendEvent(name: "zoneCount",             value: 0)
    sendEvent(name: "zoneStatuses",          value: "{}")
    (1..10).each { i ->
        sendEvent(name: "zone${i}Name",     value: "")
        sendEvent(name: "zone${i}Severity", value: 0)
        sendEvent(name: "zone${i}Message",  value: "")
    }
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

def clearMessages() {
    logInfo "clearMessages()"
    sendEvent(name: "severity",      value: 0)
    sendEvent(name: "severityText",  value: "clear")
    sendEvent(name: "latestMessage", value: "All clear — messages cleared")
    sendEvent(name: "messages",      value: "[]")
    sendEvent(name: "contact",       value: "closed")
    sendEvent(name: "acknowledged",  value: "false")
    try { parent?.clearAllMessages() } catch (Exception e) { log.warn "clearMessages error: ${e.message}" }
}

def acknowledge() {
    logInfo "acknowledge()"
    sendEvent(name: "acknowledged", value: "true")
    // acknowledged resets to "false" automatically when new/escalating alerts arrive
}

// ── Logging helpers ───────────────────────────────────────────────────────────

private void logDebug(String msg) { if (settings.logEnable) { log.debug "${device.displayName}: ${msg}" } }
private void logInfo(String msg)  { if (settings.txtEnable != false) { log.info "${device.displayName}: ${msg}" } }

def logsOff() {
    log.info "${device.displayName}: debug logging disabled"
    device.updateSetting("logEnable", [value: false, type: "bool"])
}
