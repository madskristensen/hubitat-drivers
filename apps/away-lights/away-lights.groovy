/**
 * Away Lights
 *
 * Changelog:
 *   0.2.0 — 2026-05-20 — Add randomized on/off timing (±N minute jitter) and sunset-relative window start.
 *   0.1.0 — 2026-05-20 — Initial release: simulate occupancy by turning lights on/off during Away mode within a configurable time window.
 */

import groovy.transform.Field

@Field static final String VERSION = "0.2.0"

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
            input "awayLights", "capability.switch", title: "Lights to control", required: true, multiple: true
        }

        section("Schedule") {
            input "onTime", "time", title: "Turn on at (start of window)", required: true, defaultValue: "16:00"
            input "useSunset", "bool",
                title: "Use sunset instead of fixed on-time",
                description: "When enabled, lights turn on at sunset (± offset) instead of the fixed time above",
                required: false, defaultValue: false
            if (useSunset) {
                input "sunsetOffsetMinutes", "number",
                    title: "Sunset offset (minutes, negative = before sunset)",
                    description: "e.g. -30 = 30 min before sunset, 15 = 15 min after sunset",
                    required: false, defaultValue: 0, range: "-120..120"
            }
            input "offTime", "time", title: "Turn off at (end of window)", required: true, defaultValue: "22:00"
            input "awayDebounceMinutes", "number",
                title: "Minutes in Away before turning on",
                description: "Delay after entering Away mode before checking the window (0 = immediate)",
                required: true, defaultValue: 10, range: "0..60"
            input "randomizeMinutes", "number",
                title: "Random time offset (minutes)",
                description: "Randomly delays on/off by 0 to this many minutes (0 = disabled)",
                required: false, defaultValue: 0, range: "0..60"
        }

        section("Mode") {
            input "awayMode", "mode", title: "Away mode name", required: true, defaultValue: "Away"
            input "turnOffOnHome", "bool",
                title: "Turn lights off when leaving Away",
                description: "Turns lights off immediately when the hub leaves Away mode",
                required: false, defaultValue: false
        }

        section("Notifications") {
            input "notifyDevices", "capability.notification",
                title: "Notification devices (optional)", required: false, multiple: true
            if (notifyDevices) {
                input "notifyMessage", "string",
                    title: "Notification message", required: false, defaultValue: "Away lights on"
            }
        }

        section("Logging") {
            input "logEnable", "bool", title: "Enable debug logging", required: false, defaultValue: false
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
        if ((awayDebounceMinutes as Integer) == 0) {
            checkAndTurnOn()
        } else {
            runIn((awayDebounceMinutes as Integer) * 60, "checkAndTurnOn")
        }
    } else if (turnOffOnHome) {
        unschedule("checkAndTurnOn")
        unschedule("doLightsOn")
        lightsOff()
        if (logEnable) log.debug "Away Lights: left Away mode — lights turned off"
    }
}

def onTimeHandler() {
    if (location.mode != awayMode) {
        if (logEnable) log.debug "Away Lights: onTime reached but not in Away mode — skipping"
        return
    }
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
    lightsOn()
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
    lightsOn()
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
        for (def light in awayLights) { light.on() }
    }
    if (notifyDevices && notifyMessage) {
        for (def dev in notifyDevices) { dev.deviceNotification(notifyMessage) }
    }
    if (logEnable) log.debug "Away Lights: lights on"
}

private void lightsOff() {
    if (awayLights) {
        for (def light in awayLights) { light.off() }
    }
    if (logEnable) log.debug "Away Lights: lights off"
}
