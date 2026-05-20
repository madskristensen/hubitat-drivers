/**
 * Away Lights
 *
 * Changelog:
 *   0.6.0 — 2026-05-20 — Remove occupancy sensor integration; rely on mode change only. Improve scene selector UI with clearer guidance.
 *   0.5.0 — 2026-05-20 — Add occupancy sensor integration: detect real arrival and immediately disable away simulation.
 *   0.4.0 — 2026-05-20 — Add multi-scene rotation; cycle through preset scenes with randomized hold times (backward compatible).
 *   0.3.0 — 2026-05-20 — Add always-on lights feature (never rotated); improve preference descriptions.
 *   0.2.0 — 2026-05-20 — Add randomized on/off timing (±N minute jitter) and sunset-relative window start.
 *   0.1.0 — 2026-05-20 — Initial release: simulate occupancy by turning lights on/off during Away mode within a configurable time window.
 */

import groovy.transform.Field

@Field static final String VERSION = "0.6.0"
@Field static final Integer SCENE_MIN_HOLD_MINUTES = 5
@Field static final Integer SCENE_MAX_HOLD_MINUTES = 20

definition(
    name: "Away Lights",
    namespace: "mads",
    author: "Mads Kristensen",
    description: "Turns selected lights on during a set time window when the hub is in Away mode, simulating occupancy.",
    category: "Convenience",
    iconUrl: "https://github.githubassets.com/favicons/favicon.png",
    iconX2Url: "https://github.githubassets.com/favicons/favicon.png",
    importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/away-lights/away-lights.groovy"
)

preferences {
    page(name: "mainPage", title: "Away Lights", install: true, uninstall: true)
}

def mainPage() {
    dynamicPage(name: "mainPage") {
        section("Lights") {
            input "awayLights", "capability.switch",
                title: "Lights to control",
                description: "Select all lights you want included in the away simulation (both always-on and rotating)",
                required: true, multiple: true
            input "alwaysOnLights", "capability.switch",
                title: "Always-on lights (optional)",
                description: "Select lights that should stay ON throughout the entire window (except at off-time). These prevent complete darkness and make occupancy more convincing. Lights here are NEVER turned off by random timing—they stay on until the window closes.",
                required: false, multiple: true
        }

        section("Scene Rotation (optional)") {
            paragraph "🎭 <b>Multi-Scene Mode:</b> Instead of simple on/off, cycle through preset Hubitat scenes to simulate variable activity. Each scene holds for a randomized duration (${SCENE_MIN_HOLD_MINUTES}-${SCENE_MAX_HOLD_MINUTES} min) before rotating to the next. If no scenes are selected, falls back to standard light toggling."
            paragraph "📚 <b>What are scenes?</b> Scenes are preset light configurations you create in Hubitat. Go to <b>Devices → Rooms & Scenes → Scene Settings</b> to create scenes like 'Movie Time', 'Reading', 'Evening Activity'. Once created, they'll appear here as selectable options. If you don't see any scenes listed below, create them first in the Scene Settings."
            input "sceneRotation", "hub.scene",
                title: "Scenes to rotate through",
                description: "Select one or more scenes. They will cycle in the order listed. Example: 'Movie Time' → 'Dim Lights' → 'Night Mode'. Leave empty to disable scene rotation.",
                required: false, multiple: true
        }

        section("Schedule") {
            paragraph "⏰ <b>Time Window:</b> Set when your away simulation should run. Lights will be randomly turned on/off within this window to vary patterns and appear more natural."
            input "onTime", "time",
                title: "Turn on at (start of window)",
                description: "When to start the away simulation. If using sunset, this is ignored.",
                required: true, defaultValue: "16:00"
            input "useSunset", "bool",
                title: "Use sunset instead of fixed on-time",
                description: "✓ Sunset varies by season—more realistic occupancy simulation. ✗ Only use if you want on-time to follow sunset. Uncheck for a fixed time.",
                required: false, defaultValue: false
            if (useSunset) {
                input "sunsetOffsetMinutes", "number",
                    title: "Sunset offset (minutes)",
                    description: "Adjust the sunset trigger: negative values turn lights on BEFORE sunset (e.g., -30), positive values turn them on AFTER sunset (e.g., 15). Range: -120 to +120 minutes.",
                    required: false, defaultValue: 0, range: "-120..120"
            }
            input "offTime", "time",
                title: "Turn off at (end of window)",
                description: "When to end the away simulation and turn off all lights.",
                required: true, defaultValue: "22:00"
            input "awayDebounceMinutes", "number",
                title: "Delay after entering Away mode (minutes)",
                description: "Prevents instant light activation when entering Away mode. Useful to avoid lights turning on by accident during quick mode changes. Set to 0 for immediate response.",
                required: true, defaultValue: 10, range: "0..60"
            input "randomizeMinutes", "number",
                title: "Random time jitter (minutes)",
                description: "Adds unpredictable delays to make the pattern less obvious. On/off times will shift by 0 to this many minutes randomly. Set to 0 to disable randomization (lights will turn on/off at exact times).",
                required: false, defaultValue: 0, range: "0..60"
        }

        section("Mode") {
            input "awayMode", "mode",
                title: "Away mode name",
                description: "The hub mode that triggers the away lights simulation.",
                required: true, defaultValue: "Away"
            input "turnOffOnHome", "bool",
                title: "Turn lights off when leaving Away mode",
                description: "✓ Lights turn off immediately if mode changes away from Away. ✗ Lights stay in their current state.",
                required: false, defaultValue: false
        }

        section("Notifications") {
            input "notifyDevices", "capability.notification",
                title: "Notification devices (optional)",
                description: "Send a message to these devices when away lights turn on. Useful for debugging or monitoring.",
                required: false, multiple: true
            if (notifyDevices) {
                input "notifyMessage", "string",
                    title: "Notification message",
                    description: "The message to send. Leave blank to disable notifications.",
                    required: false, defaultValue: "Away lights on"
            }
        }

        section("Logging") {
            input "logEnable", "bool",
                title: "Enable debug logging",
                description: "Write detailed debug messages to the hub's logs. Helpful for troubleshooting but increases log clutter.",
                required: false, defaultValue: false
        }
    }
}

// ── Lifecycle ──────────────────────────────────────────────────────────────────

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def uninstalled() {
    unsubscribe()
    unschedule()
}

def initialize() {
    unsubscribe()
    unschedule()
    state.sceneIndex = null
    subscribe(location, "mode", modeHandler)
    if (useSunset) {
        schedule("0 0 12 * * ?", "scheduleSunsetOn")
        scheduleSunsetOn()
    } else {
        schedule(onTime, "onTimeHandler")
    }
    schedule(offTime, "offTimeHandler")
    log.info "Away Lights initialized"
}

// ── Event handlers ─────────────────────────────────────────────────────────────

def modeHandler(evt) {
    if (evt.value == awayMode) {
        if (logEnable) log.debug "Away Lights: entered Away mode"
        state.sceneIndex = null
        if ((awayDebounceMinutes as Integer) == 0) {
            checkAndTurnOn()
        } else {
            runIn((awayDebounceMinutes as Integer) * 60, "checkAndTurnOn")
        }
    } else if (turnOffOnHome) {
        unschedule("checkAndTurnOn")
        unschedule("doLightsOn")
        unschedule("rotateScene")
        state.sceneIndex = null
        lightsOff()
        if (logEnable) log.debug "Away Lights: left Away mode — lights turned off"
    }
}

def onTimeHandler() {
    if (location.mode != awayMode) {
        if (logEnable) log.debug "Away Lights: onTime reached but not in Away mode — skipping"
        return
    }
    
    // Always-on lights turn on immediately (no jitter)
    if (alwaysOnLights) {
        for (def light in alwaysOnLights) { light.on() }
        if (logEnable) log.debug "Away Lights: always-on lights turned ON (no jitter)"
    }
    
    // Regular lights get jitter applied
    Integer jitter = randomizeMinutes ? (int)(Math.random() * (randomizeMinutes as Integer) * 60) : 0
    if (jitter > 0) {
        if (logEnable) log.debug "Away Lights: onTime — delaying ${jitter}s (random jitter)"
        runIn(jitter, "doLightsOn")
    } else {
        doLightsOn()
    }
}

def doLightsOn() {
    if (location.mode != awayMode) return
    
    // If scene rotation is configured, use it instead of simple on/off
    if (sceneRotation && sceneRotation.size() > 0) {
        rotateScene()
        return
    }
    
    // Fall back to standard light toggling
    if (awayLights) {
        for (def light in awayLights) {
            // Skip lights that are always-on (they're already on from onTimeHandler)
            if (alwaysOnLights && light in alwaysOnLights) {
                continue
            }
            light.on()
        }
    }
    if (notifyDevices && notifyMessage) {
        for (def dev in notifyDevices) { dev.deviceNotification(notifyMessage) }
    }
    if (logEnable) log.debug "Away Lights: rotating lights on"
}

def offTimeHandler() {
    if (location.mode != awayMode) {
        if (logEnable) log.debug "Away Lights: offTime reached but not in Away mode — skipping"
        return
    }
    Integer jitter = randomizeMinutes ? (int)(Math.random() * (randomizeMinutes as Integer) * 60) : 0
    if (jitter > 0) {
        if (logEnable) log.debug "Away Lights: offTime — delaying ${jitter}s (random jitter)"
        runIn(jitter, "doLightsOff")
    } else {
        doLightsOff()
    }
}

def doLightsOff() {
    if (location.mode != awayMode) return
    lightsOff()
}

def checkAndTurnOn() {
    if (location.mode != awayMode) {
        if (logEnable) log.debug "Away Lights: checkAndTurnOn — mode changed back, skipping"
        return
    }
    if (!isInWindow()) {
        if (logEnable) log.debug "Away Lights: checkAndTurnOn — outside time window, skipping"
        return
    }
    
    // Always-on lights turn on immediately (no jitter)
    if (alwaysOnLights) {
        for (def light in alwaysOnLights) { light.on() }
        if (logEnable) log.debug "Away Lights: always-on lights turned ON (no jitter)"
    }
    
    // Then turn on rotating lights with jitter
    Integer jitter = randomizeMinutes ? (int)(Math.random() * (randomizeMinutes as Integer) * 60) : 0
    if (jitter > 0) {
        if (logEnable) log.debug "Away Lights: checkAndTurnOn — delaying ${jitter}s (random jitter)"
        runIn(jitter, "doLightsOn")
    } else {
        doLightsOn()
    }
}

// ── Scene rotation helpers ────────────────────────────────────────────────────

def rotateScene() {
    if (location.mode != awayMode) return
    if (!sceneRotation || sceneRotation.size() == 0) return
    
    // Initialize or increment scene index
    if (state.sceneIndex == null) {
        state.sceneIndex = 0
    } else {
        state.sceneIndex = (state.sceneIndex + 1) % sceneRotation.size()
    }
    
    def currentScene = sceneRotation[state.sceneIndex]
    if (logEnable) log.debug "Away Lights: activating scene [${state.sceneIndex}/${sceneRotation.size()}]: ${currentScene.label ?: currentScene.name}"
    
    currentScene.activate()
    
    // Send notification
    if (notifyDevices && notifyMessage) {
        for (def dev in notifyDevices) { dev.deviceNotification(notifyMessage) }
    }
    
    // Schedule next rotation with random hold time
    scheduleNextSceneRotation()
}

def scheduleNextSceneRotation() {
    if (location.mode != awayMode) return
    if (!sceneRotation || sceneRotation.size() == 0) return
    if (!isInWindow()) return
    
    // Generate random hold time between SCENE_MIN_HOLD_MINUTES and SCENE_MAX_HOLD_MINUTES
    Integer holdMinutes = SCENE_MIN_HOLD_MINUTES + (int)(Math.random() * (SCENE_MAX_HOLD_MINUTES - SCENE_MIN_HOLD_MINUTES + 1))
    Integer holdSeconds = holdMinutes * 60
    
    if (logEnable) log.debug "Away Lights: next scene rotation in ${holdMinutes} minutes"
    
    runIn(holdSeconds, "rotateScene")
}

// ── Helpers ────────────────────────────────────────────────────────────────────

def scheduleSunsetOn() {
    Integer offsetMinutes = (sunsetOffsetMinutes as Integer) ?: 0
    def sunData = getSunriseAndSunset(sunsetOffset: offsetMinutes)
    def targetTime = sunData.sunset
    if (logEnable) log.debug "Away Lights: scheduling on at sunset+offset = ${targetTime}"
    schedule(targetTime, "onTimeHandler")
}

private boolean isInWindow() {
    def now = new Date()
    Date on
    if (useSunset) {
        Integer offsetMinutes = (sunsetOffsetMinutes as Integer) ?: 0
        on = getSunriseAndSunset(sunsetOffset: offsetMinutes).sunset
    } else {
        on = timeToday(onTime, location.timeZone)
    }
    def off = timeToday(offTime, location.timeZone)
    return now >= on && now < off
}

private void lightsOn() {
    if (awayLights) {
        for (def light in awayLights) {
            // Skip lights that are always-on (they were already turned on in onTimeHandler)
            if (alwaysOnLights && light in alwaysOnLights) {
                continue
            }
            light.on()
        }
    }
    if (notifyDevices && notifyMessage) {
        for (def dev in notifyDevices) { dev.deviceNotification(notifyMessage) }
    }
    if (logEnable) log.debug "Away Lights: lights on"
}

private void lightsOff() {
    unschedule("rotateScene")
    if (awayLights) {
        for (def light in awayLights) { light.off() }
    }
    if (logEnable) log.debug "Away Lights: lights off"
}
