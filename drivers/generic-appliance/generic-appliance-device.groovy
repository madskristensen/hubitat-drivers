/**
 * Generic Appliance Device
 * Namespace: mads
 * Author:    Mads Kristensen
 * Version:   0.1.0
 *
 * Generic appliance status child device (used by the Appliance Cycle Monitor app,
 * one device per appliance). Surfaces a constrained lifecycle status
 * (Ready → Running → Finished → Ready) plus a human-readable dashboard line and a
 * mirrored door contact.
 *
 * The parent app drives transitions from a run-source device (power meter or
 * acceleration/vibration sensor) and an optional door contact sensor. The
 * setStatus / markRunning / markFinished / markReady commands let Rule Machine and
 * other automations override or seed the state directly.
 *
 * Changelog:
 *   0.1.0 — 2026-05-29 — Initial release: applianceStatus lifecycle, door contact mirror, run timing, manual override commands.
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "0.1.0"
@Field static final List<String> STATUSES = ["Ready", "Running", "Finished", "Unknown"]

metadata {
    definition(
        name:      "Generic Appliance Device",
        namespace: "mads",
        author:    "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/generic-appliance/generic-appliance-device.groovy"
    ) {
        capability "Sensor"
        capability "Refresh"
        capability "ContactSensor"

        // ── Status ────────────────────────────────────────────────────────────
        attribute "applianceStatus", "ENUM",   ["Ready", "Running", "Finished", "Unknown"]
        attribute "statusText",      "STRING"
        attribute "contact",         "ENUM",   ["closed", "open"]

        // ── Run timing ────────────────────────────────────────────────────────
        attribute "runStartedAt",  "STRING"
        attribute "runEndedAt",     "STRING"
        attribute "runDurationMin", "NUMBER"

        // Manual / automation overrides
        command "setStatus", [
            [name: "status*", type: "ENUM", description: "Lifecycle status", constraints: ["Ready", "Running", "Finished", "Unknown"]]
        ]
        command "markRunning"
        command "markFinished"
        command "markReady"
        command "reset"
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
    logInfo "Generic Appliance Device installed"
    sendEvent(name: "applianceStatus", value: "Unknown")
    sendEvent(name: "statusText",      value: "Initializing…")
    sendEvent(name: "contact",         value: "closed")
    sendEvent(name: "runDurationMin",  value: 0)
}

def updated() {
    logDebug "updated()"
    if (settings.logEnable) { runIn(1800, "logsOff") }
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

def setStatus(String status) {
    if (!STATUSES.contains(status)) {
        log.warn "setStatus: unknown status '${status}' (expected one of ${STATUSES})"
        return
    }
    logInfo "setStatus(${status})"
    if (parent) {
        parent.applyManualStatus(device.deviceNetworkId, status)
    } else {
        // No parent (standalone use) — apply locally.
        applyStatus(status)
        applyStatusText(status)
    }
}

def markRunning()  { setStatus("Running") }
def markFinished() { setStatus("Finished") }
def markReady()    { setStatus("Ready") }
def reset()        { setStatus("Ready") }

// ── Parent-facing API ─────────────────────────────────────────────────────────
// Called by the app to push computed state onto this device. Kept change-only to
// avoid event spam on busy power meters.

def applyStatus(String status) {
    if (!STATUSES.contains(status)) { return }
    sendEventIfChanged("applianceStatus", status)
}

def applyStatusText(String text) {
    sendEventIfChanged("statusText", text)
}

def applyContact(String state) {
    if (state != "open" && state != "closed") { return }
    sendEventIfChanged("contact", state)
}

def applyRunStarted(String iso) {
    sendEvent(name: "runStartedAt", value: iso)
}

def applyRunEnded(String iso, BigDecimal durationMin) {
    sendEvent(name: "runEndedAt", value: iso)
    if (durationMin != null) { sendEventIfChanged("runDurationMin", durationMin) }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private void sendEventIfChanged(String name, Object value) {
    if (value == null) { return }
    if (device.currentValue(name)?.toString() == value.toString()) { return }
    sendEvent(name: name, value: value, descriptionText: "${device.displayName}: ${name} is ${value}")
}

private void logDebug(String msg) { if (settings.logEnable) { log.debug "${device.displayName}: ${msg}" } }
private void logInfo(String msg)  { if (settings.txtEnable != false) { log.info "${device.displayName}: ${msg}" } }

def logsOff() {
    log.info "${device.displayName}: debug logging disabled"
    device.updateSetting("logEnable", [value: false, type: "bool"])
}
