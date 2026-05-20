/**
 * Away Lights
 *
 * Changelog:
 *   0.8.1 — 2026-05-20 — Remove turnOffOnHome gate on scheduler cleanup; unschedule offTimeHandler on Away mode exit to eliminate wasteful no-op invocations; reduce CPU/memory overhead.
 *   0.8.0 — 2026-05-20 — Fix critical race condition in light control; fix scheduler memory leak in sunset mode; fix midnight-crossing time windows; improve label clarity; refactor helpers for efficiency.
 *   0.7.0 — 2026-05-20 — UI refinement pass: improve all label descriptions for clarity; add always-on lights feature; add max start time constraint; remove scene selector.
 *   0.6.0 — 2026-05-20 — Remove occupancy sensor integration; rely on mode change only. Improve scene selector UI with clearer guidance.
 *   0.5.0 — 2026-05-20 — Add occupancy sensor integration: detect real arrival and immediately disable away simulation.
 *   0.4.0 — 2026-05-20 — Add multi-scene rotation; cycle through preset scenes with randomized hold times (backward compatible).
 *   0.3.0 — 2026-05-20 — Add always-on lights feature (never rotated); improve preference descriptions.
 *   0.2.0 — 2026-05-20 — Add randomized on/off timing (±N minute jitter) and sunset-relative window start.
 *   0.1.0 — 2026-05-20 — Initial release: simulate occupancy by turning lights on/off during Away mode within a configurable time window.
 */

import groovy.transform.Field

@Field static final String VERSION = "0.8.1"
@Field static final Integer SECONDS_PER_MINUTE = 60

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
                title: "Lights to randomly turn on and off",
                description: "Select all lights for the away simulation. These will be randomly turned on/off, UNLESS also selected as always-on lights below (which stay on the entire window).",
                required: true, multiple: true
            input "alwaysOnLights", "capability.switch",
                title: "Always-on lights (optional)",
                description: "Lights that stay ON for the entire window (turn off only at end time). Never included in random rotation. Example: hallway light to prevent complete darkness.",
                required: false, multiple: true
        }



        section("Schedule") {
            paragraph "⏰ <b>Time Window:</b> Set when your away simulation should run. Lights will be randomly turned on/off within this window to vary patterns and appear more natural."
            input "onTime", "time",
                title: "Start time",
                description: "When to start the away simulation. Ignored if using sunset.",
                required: true, defaultValue: "16:00"
            input "useSunset", "bool",
                title: "Use sunset instead of fixed on-time",
                description: "✓ Sunset varies by season—more realistic occupancy simulation. ✗ Only use if you want on-time to follow sunset. Uncheck for a fixed time.",
                required: false, defaultValue: false
            if (useSunset) {
                input "sunsetOffsetMinutes", "number",
                    title: "Sunset offset (±minutes)",
                    description: "Adjust when lights turn on relative to sunset. Negative values = start BEFORE sunset (-30 = 30 min before), positive values = start AFTER (+30 = 30 min after). Example: -15 means 15 minutes before sunset.",
                    required: false, defaultValue: 0, range: "-120..120"
            }
            input "offTime", "time",
                title: "End time",
                description: "When to end the away simulation and turn off all lights.",
                required: true, defaultValue: "22:00"
            input "awayDebounceMinutes", "number",
                title: "Wait before starting (minutes)",
                description: "When the hub enters Away mode, wait N minutes before checking if away simulation should start. Prevents false activations during quick mode changes. Set to 0 for immediate response.",
                required: true, defaultValue: 10, range: "0..60"
            input "randomizeMinutes", "number",
                title: "Add randomness to start time (±minutes)",
                description: "Vary the start time of the away simulation by 0 to N minutes for realism. Example: with 15 selected, if start time is 6:00 PM, lights might actually start between 5:45 PM and 6:15 PM. Set to 0 to disable.",
                required: false, defaultValue: 0, range: "0..60"
            input "maxStartTime", "time",
                title: "Don't start later than (optional)",
                description: "Prevents away simulation from starting after this time. Useful to avoid late-night false activations. Leave empty to disable.",
                required: false
        }

        section("Mode") {
            input "awayMode", "mode",
                title: "Away mode name",
                description: "The hub mode that triggers the away lights simulation.",
                required: true, defaultValue: "Away"
            input "turnOffOnHome", "bool",
                title: "Turn lights off when hub leaves Away mode",
                description: "If the hub mode changes from Away to Home (or any other mode), all lights turn off immediately.",
                required: false, defaultValue: false
        }

        section("Notifications") {
            input "notifyDevices", "capability.notification",
                title: "Notification devices (optional)",
                description: "Send a notification when away simulation starts. Useful for debugging, monitoring, or receiving phone alerts.",
                required: false, multiple: true
            if (notifyDevices) {
                input "notifyMessage", "string",
                    title: "Notification message",
                    description: "Message to send. Leave blank to disable notifications.",
                    required: false, defaultValue: "Away simulation started"
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
    state.away_lights_active = false  // Explicit initialization to prevent race conditions
    subscribe(location, "mode", modeHandler)
    if (useSunset) {
        schedule("0 0 12 * * ?", "scheduleSunsetOn")
        scheduleSunsetOn()
    } else {
        schedule(onTime, "onTimeHandler")
    }
    schedule(offTime, "offTimeHandler")
    log.info "Away Lights v${VERSION} initialized"
}

// ── Event handlers ─────────────────────────────────────────────────────────────

def modeHandler(evt) {
    if (evt.value == awayMode) {
        if (logEnable) log.debug "Away Lights: entered Away mode"
        state.away_lights_active = false  // Reset flag when entering Away mode
        if ((awayDebounceMinutes as Integer) == 0) {
            checkAndTurnOn()
        } else {
            runIn((awayDebounceMinutes as Integer) * 60, "checkAndTurnOn")
        }
    } else {
        unschedule("checkAndTurnOn")
        unschedule("doLightsOn")
        unschedule("offTimeHandler")
        state.away_lights_active = false
        if (turnOffOnHome) {
            lightsOff()
            if (logEnable) log.debug "Away Lights: left Away mode — lights turned off"
        } else {
            if (logEnable) log.debug "Away Lights: left Away mode"
        }
    }
}

def onTimeHandler() {
    // Race condition prevention: if already active from mode change, skip
    if (state.away_lights_active) {
        if (logEnable) log.debug "Away Lights: onTimeHandler — already active from mode change, skipping"
        return
    }
    if (location.mode != awayMode) {
        if (logEnable) log.debug "Away Lights: onTime reached but not in Away mode — skipping"
        return
    }
    
    state.away_lights_active = true
    turnOnAlwaysOnLights()
    scheduleRandomLightToggle()
}

def doLightsOn() {
    if (location.mode != awayMode) return
    
    if (awayLights) {
        Set alwaysOnIds = getAlwaysOnLightIds()
        for (def light in awayLights) {
            if (light.id in alwaysOnIds) continue  // Skip always-on lights (they're already on)
            light.on()
        }
    }
    
    if (notifyDevices && notifyMessage) {
        for (def dev in notifyDevices) { dev.deviceNotification(notifyMessage) }
    }
    if (logEnable) log.debug "Away Lights: lights on"
}

def offTimeHandler() {
    if (location.mode != awayMode) {
        if (logEnable) log.debug "Away Lights: offTime reached but not in Away mode — skipping"
        return
    }
    Integer jitter = getRandomJitterSeconds()
    if (jitter > 0) {
        if (logEnable) log.debug "Away Lights: offTime — delaying ${jitter}s (random jitter)"
        runIn(jitter, "doLightsOff")
    } else {
        doLightsOff()
    }
}

def doLightsOff() {
    if (location.mode != awayMode) return
    state.away_lights_active = false  // Clear active flag when lights turn off
    lightsOff()
}

def checkAndTurnOn() {
    if (state.away_lights_active) {
        if (logEnable) log.debug "Away Lights: checkAndTurnOn — already active, skipping"
        return
    }
    if (location.mode != awayMode) {
        if (logEnable) log.debug "Away Lights: checkAndTurnOn — mode changed back, skipping"
        return
    }
    if (!isInWindow()) {
        if (logEnable) log.debug "Away Lights: checkAndTurnOn — outside time window, skipping"
        return
    }
    
    state.away_lights_active = true
    turnOnAlwaysOnLights()
    scheduleRandomLightToggle()
}

// ── Helpers ────────────────────────────────────────────────────────────────────

def scheduleSunsetOn() {
    unschedule("onTimeHandler")  // Remove old sunset schedule before adding new one (prevents leak)
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
    
    // Handle midnight-crossing windows (e.g., 23:00 - 06:00 next day)
    if (on < off) {
        // Normal case: on before off (same day)
        if (now < on || now >= off) return false
    } else {
        // Midnight-crossing case: off before on (wraps to next day)
        // Window is active if: now >= on OR now < off
        if (now < on && now >= off) return false
    }
    
    if (maxStartTime) {
        def maxStart = timeToday(maxStartTime, location.timeZone)
        if (now > maxStart) {
            if (logEnable) log.debug "Away Lights: current time ${now} is after max start time ${maxStart}"
            return false
        }
    }
    
    return true
}

// ── Private Helpers ───────────────────────────────────────────────────────────

private void turnOnAlwaysOnLights() {
    if (alwaysOnLights) {
        for (def light in alwaysOnLights) { light.on() }
        if (logEnable) log.debug "Away Lights: always-on lights turned ON (no jitter)"
    }
}

private void scheduleRandomLightToggle() {
    Integer jitter = getRandomJitterSeconds()
    if (jitter > 0) {
        if (logEnable) log.debug "Away Lights: scheduling lights on with ${jitter}s random delay"
        runIn(jitter, "doLightsOn")
    } else {
        doLightsOn()
    }
}

private Integer getRandomJitterSeconds() {
    if (!randomizeMinutes) return 0
    Integer maxSeconds = (randomizeMinutes as Integer) * SECONDS_PER_MINUTE
    return (int)(Math.random() * maxSeconds)
}

private Set getAlwaysOnLightIds() {
    return alwaysOnLights?.collect { it.id }?.toSet() ?: []
}

private void lightsOff() {
    if (awayLights) {
        for (def light in awayLights) { light.off() }
    }
    if (logEnable) log.debug "Away Lights: lights off"
}
