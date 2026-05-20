/**
 * Occupancy Mode Manager
 *
 * Changelog:
 *   0.11.0 — 2026-05-20 — Fix asymmetric cooldown: Home transitions now bypass the mode-change cooldown so arriving home is never delayed.
 *   0.10.0 — 2026-05-20 — Add optional Away prerequisite locks: Away is only evaluated when all designated locks are locked; locking them also triggers an immediate re-evaluation.
 *   0.9.0 — 2026-05-20 — Lock unlock events count as occupancy activity via new activity locks picker.
 *   0.8.0 — 2026-05-20 — Track presence inactivity separately so Not Present does not count as activity; schedule checks from the remaining time on each inactivity window.
 *   0.7.0 — 2026-05-20 — Replace periodic polling with event-driven debounced evaluation and schedule the next check only when needed.
 *   0.6.0 — 2026-05-20 — Remove secondary Away confirmation and treat contact open/close as activity for the inactivity timer.
 *   0.5.0 — 2026-05-20 — Add lock/unlock actions tied to Away/Home mode changes with separate device lists for securing and releasing locks.
 *   0.4.0 — 2026-05-20 — Remove strategy preset and use simple Home/Away inactivity thresholds only; motion and contact now roll into the same inactivity timer.
 *   0.3.0 — 2026-05-20 — Add explicit arrival-vs-departure asymmetry controls and optional Away secondary confirmation using exit contact closure.
 *   0.2.0 — 2026-05-20 — Rename app, replace confidence percentages with strategy presets and timing controls, add decision explainability/history, and add mode-based path restrictions with Night pause support.
 *   0.1.1 — 2026-05-20 — Add required iconUrl/iconX2Url metadata in app definition.
 *   0.1.0 — 2026-05-20 — Initial release: confidence-based Home/Away mode control from selected presence, motion, and contact sensors.
 */

import groovy.transform.Field

@Field static final String VERSION = "0.11.0"
@Field static final Integer MAX_HISTORY_ITEMS = 12
@Field static final Integer EVALUATION_DEBOUNCE_SECONDS = 1

definition(
    name: "Occupancy Mode Manager",
    namespace: "mads",
    author: "Mads Kristensen",
    description: "Sets Home/Away mode from selected presence, motion, and contact sensors using inactivity thresholds.",
    category: "Convenience",
    iconUrl: "https://github.githubassets.com/favicons/favicon.png",
    iconX2Url: "https://github.githubassets.com/favicons/favicon.png",
    importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/presence-confidence-mode-manager/presence-confidence-mode-manager.groovy"
)

preferences {
    page(name: "mainPage", title: "Occupancy Mode Manager", install: true, uninstall: true)
}

def mainPage() {
    dynamicPage(name: "mainPage") {
        section("Sensors") {
            input "presenceSensors", "capability.presenceSensor", title: "Presence sensors", required: false, multiple: true
            input "motionSensors", "capability.motionSensor", title: "Motion sensors", required: false, multiple: true
            input "contactSensors", "capability.contactSensor", title: "Contact sensors", required: false, multiple: true
            input "activityLocks", "capability.lock",
                title: "Locks (unlock = activity signal)",
                description: "Unlocking any of these doors counts as occupancy activity",
                required: false,
                multiple: true
        }

        section("Target modes") {
            input "homeMode", "mode", title: "Mode to set when occupied", required: true, defaultValue: "Home"
            input "awayMode", "mode", title: "Mode to set when unoccupied", required: true, defaultValue: "Away"
        }

        section("Locks") {
            input "awayLockDevices", "capability.lock",
                title: "Locks to secure when switching to Away",
                required: false,
                multiple: true

            input "homeUnlockDevices", "capability.lock",
                title: "Locks to unlock when switching to Home",
                required: false,
                multiple: true

            input "awayPrerequisiteLocks", "capability.lock",
                title: "Away prerequisite locks (optional)",
                description: "Away mode is only evaluated when all of these are locked. Locking any of them also triggers an immediate evaluation.",
                required: false,
                multiple: true
        }

        section("Occupancy behavior") {
            input "homeInactiveMinutes", "number",
                title: "Minutes of inactivity before switching to Home",
                required: true,
                defaultValue: 0,
                range: "0..180"

            input "awayInactiveMinutes", "number",
                title: "Minutes of inactivity before switching to Away",
                required: true,
                defaultValue: 20,
                range: "1..1440"

            input "modeChangeCooldownMinutes", "number",
                title: "Minimum minutes between mode changes",
                required: true,
                defaultValue: 20,
                range: "0..1440"

            paragraph("Home and Away use separate inactivity thresholds. A Home value of 0 means any current activity switches Home immediately.")
        }

        section("Mode restrictions") {
            input "pausedModes", "mode",
                title: "Pause app in these current modes",
                required: false,
                multiple: true,
                defaultValue: defaultPausedModes()

            input "homePathAllowedFromModes", "mode",
                title: "Allow Home path only when current mode is one of these (optional)",
                required: false,
                multiple: true

            input "awayPathAllowedFromModes", "mode",
                title: "Allow Away path only when current mode is one of these (optional)",
                required: false,
                multiple: true

            paragraph("Mode selectors come directly from your hub's configured Location Modes.")
        }

        section("Runtime") {
            input "logEnable", "bool", title: "Enable debug logging", required: false, defaultValue: false
            input "evaluateNow", "button", title: "Evaluate occupancy now"
            paragraph("Why this mode: ${currentSummary()}")
            paragraph("Recent decisions:\n${recentHistoryText()}")
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def appButtonHandler(String buttonName) {
    if (buttonName == "evaluateNow") {
        evaluateOccupancy(true)
    }
}

private void initialize() {
    if (!hasAnyConfiguredSensor()) {
        log.warn "Occupancy Mode Manager: No sensors selected. Configure at least one sensor."
        state.lastEvaluationSummary = "No sensors selected."
        return
    }

    state.lastActivityTs =
        seedTimestampFromActiveSensor(motionSensors, "motion", "active") ?:
        seedTimestampFromActiveSensor(contactSensors, "contact", "open") ?:
        seedTimestampFromAnyPresenceSensor(presenceSensors)
    state.presenceInactiveSinceTs = presenceSensors?.size() ? now() : null

    if (presenceSensors) {
        subscribe(presenceSensors, "presence", "presenceHandler")
    }
    if (motionSensors) {
        subscribe(motionSensors, "motion", "motionHandler")
    }
    if (contactSensors) {
        subscribe(contactSensors, "contact", "contactHandler")
    }
    if (activityLocks) {
        subscribe(activityLocks, "lock", "lockActivityHandler")
    }
    if (awayPrerequisiteLocks) {
        subscribe(awayPrerequisiteLocks, "lock", "awayPrerequisiteLockHandler")
    }
    queueEvaluation(EVALUATION_DEBOUNCE_SECONDS)
}

def presenceHandler(evt) {
    if ("present".equalsIgnoreCase("${evt?.value}")) {
        state.lastActivityTs = eventTimestamp(evt)
        state.presenceInactiveSinceTs = null
    } else if (presenceSensors?.every { "not present".equalsIgnoreCase("${it.currentValue("presence")}") }) {
        if (!state.presenceInactiveSinceTs) {
            state.presenceInactiveSinceTs = eventTimestamp(evt)
        }
    }
    queueEvaluation(EVALUATION_DEBOUNCE_SECONDS)
}

def motionHandler(evt) {
    if ("active".equalsIgnoreCase("${evt?.value}")) {
        state.lastActivityTs = eventTimestamp(evt)
    }
    queueEvaluation(EVALUATION_DEBOUNCE_SECONDS)
}

def contactHandler(evt) {
    if ("open".equalsIgnoreCase("${evt?.value}") || "closed".equalsIgnoreCase("${evt?.value}")) {
        state.lastActivityTs = eventTimestamp(evt)
    }
    queueEvaluation(EVALUATION_DEBOUNCE_SECONDS)
}

def lockActivityHandler(evt) {
    if ("unlocked".equalsIgnoreCase("${evt?.value}")) {
        state.lastActivityTs = eventTimestamp(evt)
        queueEvaluation(EVALUATION_DEBOUNCE_SECONDS)
    }
}

def awayPrerequisiteLockHandler(evt) {
    // Re-evaluate whenever a prerequisite lock changes state (locked or unlocked).
    // On locked: may immediately trigger Away if inactivity already elapsed.
    // On unlocked: cancels any pending Away path.
    queueEvaluation(EVALUATION_DEBOUNCE_SECONDS)
}

def evaluateOccupancy(Boolean manualTrigger = false) {
    if (!hasAnyConfiguredSensor()) {
        return
    }

    final Long nowTs = now()
    final List<String> homeReasons = []
    final List<String> awayReasons = []
    final List<String> holdReasons = []

    if (isPausedByCurrentMode()) {
        String summary = "PAUSED because current mode is ${location.mode}."
        recordSummary(summary, manualTrigger)
        return
    }

    final boolean hasPresence = (presenceSensors?.size() ?: 0) > 0
    final boolean anyPresent = hasPresence && presenceSensors.any { "present".equalsIgnoreCase("${it.currentValue("presence")}") }
    final boolean allNotPresent = hasPresence && presenceSensors.every { "not present".equalsIgnoreCase("${it.currentValue("presence")}") }

    final boolean motionActive = (motionSensors?.size() ?: 0) > 0 && motionSensors.any { "active".equalsIgnoreCase("${it.currentValue("motion")}") }
    final boolean contactOpen = (contactSensors?.size() ?: 0) > 0 && contactSensors.any { "open".equalsIgnoreCase("${it.currentValue("contact")}") }

    if (motionActive || contactOpen) {
        state.lastActivityTs = nowTs
    }

    if (hasPresence) {
        if (anyPresent) {
            state.presenceInactiveSinceTs = null
        } else if (allNotPresent && !state.presenceInactiveSinceTs) {
            state.presenceInactiveSinceTs = nowTs
        }
    }

    final Long lastActivityTs = state.lastActivityTs as Long
    final Integer minutesSinceActivity = minutesSince(lastActivityTs, nowTs)
    Integer homeThresholdMinutes = (homeInactiveMinutes as Integer) ?: 0
    Integer awayThresholdMinutes = (awayInactiveMinutes as Integer) ?: 20
    boolean inactivityMet = minutesSinceActivity >= awayThresholdMinutes

    if (anyPresent) {
        homeReasons << "presence sensor reports present"
    }
    if (motionActive) {
        homeReasons << "motion is active now"
    }
    if (contactOpen) {
        homeReasons << "contact is open now"
    }
    if (allNotPresent) {
        awayReasons << "all selected presence sensors report not present"
    }
    awayReasons << "minutes since activity: ${minutesSinceActivity} (threshold: ${awayThresholdMinutes})"

    boolean homeActivityMet = minutesSinceActivity <= homeThresholdMinutes
    boolean shouldHome = anyPresent || homeActivityMet
    if (shouldHome) {
        homeReasons << "minutes since activity: ${minutesSinceActivity} (threshold: ${homeThresholdMinutes})"
    } else {
        holdReasons << "Home threshold not met (${minutesSinceActivity}/${homeThresholdMinutes}m)"
    }

    boolean shouldAway = false

    if (hasPresence) {
        Long presenceInactiveSinceTs = state.presenceInactiveSinceTs as Long
        boolean presenceInactiveMet = allNotPresent && presenceInactiveSinceTs && minutesSince(presenceInactiveSinceTs, nowTs) >= awayThresholdMinutes
        shouldAway = presenceInactiveMet && inactivityMet
    } else {
        shouldAway = inactivityMet
    }

    if (shouldAway && awayPrerequisiteLocks) {
        boolean allPrerequisiteLocked = (awayPrerequisiteLocks as Collection).every {
            "locked".equalsIgnoreCase("${it.currentValue("lock")}")
        }
        if (!allPrerequisiteLocked) {
            holdReasons << "away prerequisite lock(s) not yet locked"
            shouldAway = false
        }
    }

    String decision = "hold"
    String targetMode = null

    if (shouldHome && isPathAllowed(homePathAllowedFromModes)) {
        decision = "home"
        targetMode = homeMode
    } else if (shouldHome && !isPathAllowed(homePathAllowedFromModes)) {
        holdReasons << "Home path disabled from current mode ${location.mode}"
    }

    if (shouldAway && !shouldHome && isPathAllowed(awayPathAllowedFromModes)) {
        decision = "away"
        targetMode = awayMode
    } else if (shouldAway && !shouldHome && !isPathAllowed(awayPathAllowedFromModes)) {
        holdReasons << "Away path disabled from current mode ${location.mode}"
    }

    boolean changedMode = false
    if (targetMode && location.mode != targetMode) {
        if (decision == "home" || cooldownElapsed(nowTs)) {
            setLocationMode(targetMode)
            state.lastModeChangeTs = nowTs
            changedMode = true
            runModeActions(decision)
        } else {
            holdReasons << "mode change cooldown active"
            decision = "hold"
        }
    } else if (decision == "away" || decision == "home") {
        runModeActions(decision)
    }

    String summary = "Decision: ${decision.toUpperCase()} | " +
        "Home evidence: ${homeReasons ? homeReasons.join(', ') : 'none'} | " +
        "Away evidence: ${awayReasons ? awayReasons.join(', ') : 'none'} | " +
        "Hold reason: ${holdReasons ? holdReasons.join(', ') : 'none'} | " +
        "Current mode: ${location.mode}${changedMode ? ' (updated)' : ''}"

    recordSummary(summary, manualTrigger)
    scheduleNextEvaluation(nowTs, awayThresholdMinutes, targetMode)
}

private void recordSummary(String summary, Boolean manualTrigger = false) {
    state.lastEvaluationSummary = summary
    appendHistory(summary)
    if (manualTrigger || logEnable) {
        log.info "Occupancy Mode Manager: ${summary}"
    }
}

private void appendHistory(String summary) {
    List<String> history = (state.decisionHistory instanceof List) ? (state.decisionHistory as List<String>) : []
    String ts = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    history.add(0, "${ts} — ${summary}")
    state.decisionHistory = history.take(MAX_HISTORY_ITEMS)
}

private String recentHistoryText() {
    List<String> history = (state.decisionHistory instanceof List) ? (state.decisionHistory as List<String>) : []
    return history ? history.take(5).join("\n") : "No history yet."
}

private boolean hasAnyConfiguredSensor() {
    return (presenceSensors?.size() ?: 0) > 0 || (motionSensors?.size() ?: 0) > 0 || (contactSensors?.size() ?: 0) > 0 || (activityLocks?.size() ?: 0) > 0
}

private boolean isPausedByCurrentMode() {
    if (!pausedModes) {
        return false
    }
    return (pausedModes as Collection).contains(location.mode)
}

private boolean isPathAllowed(def allowedModesSetting) {
    if (!allowedModesSetting) {
        return true
    }
    return (allowedModesSetting as Collection).contains(location.mode)
}

private Long seedTimestampFromActiveSensor(Collection sensors, String attr, String activeValue) {
    if (!sensors) {
        return null
    }
    boolean active = sensors.any { activeValue.equalsIgnoreCase("${it.currentValue(attr)}") }
    return active ? now() : null
}

private Long seedTimestampFromAnyPresenceSensor(Collection sensors) {
    if (!sensors) {
        return null
    }
    return now()
}

private Integer minutesSince(Long lastTs, Long nowTs) {
    if (!lastTs) {
        return 999999
    }
    return ((nowTs - lastTs) / 60000L) as Integer
}

private boolean cooldownElapsed(Long nowTs) {
    Integer cooldownMinutes = (modeChangeCooldownMinutes as Integer) ?: 20
    Long lastModeChangeTs = state.lastModeChangeTs as Long
    if (!lastModeChangeTs || cooldownMinutes <= 0) {
        return true
    }
    return (nowTs - lastModeChangeTs) >= (cooldownMinutes * 60000L)
}

private Long eventTimestamp(evt) {
    return evt?.date?.time ?: now()
}

private String currentSummary() {
    return state.lastEvaluationSummary ?: "No evaluation yet."
}

private void queueEvaluation(Integer delaySeconds = EVALUATION_DEBOUNCE_SECONDS) {
    unschedule("evaluateOccupancy")
    runIn(Math.max(1, delaySeconds ?: EVALUATION_DEBOUNCE_SECONDS), "evaluateOccupancy")
}

private void runModeActions(String decision) {
    if (decision == "away") {
        operateLocks(awayLockDevices, "lock")
    } else if (decision == "home") {
        operateLocks(homeUnlockDevices, "unlock")
    }
}

private void operateLocks(def devices, String action) {
    if (!devices) {
        return
    }
    (devices as Collection).each { device ->
        if (!device) {
            return
        }
        String currentState = "${device.currentValue("lock")}".toLowerCase()
        if (action == "lock" && device.hasCommand("lock") && currentState != "locked") {
            device.lock()
        } else if (action == "unlock" && device.hasCommand("unlock") && currentState != "unlocked") {
            device.unlock()
        }
    }
}

private void scheduleNextEvaluation(Long nowTs, Integer awayThresholdMinutes, String targetMode) {
    Long lastActivityTs = state.lastActivityTs as Long
    Long presenceInactiveSinceTs = state.presenceInactiveSinceTs as Long
    Long nextDelaySeconds = null

    if (lastActivityTs) {
        Long inactivityDelayMs = (awayThresholdMinutes * 60000L) - (nowTs - lastActivityTs)
        if (inactivityDelayMs > 0) {
            nextDelaySeconds = Math.ceil(inactivityDelayMs / 1000.0) as Long
        }
    }

    if (presenceInactiveSinceTs) {
        Long presenceDelayMs = (awayThresholdMinutes * 60000L) - (nowTs - presenceInactiveSinceTs)
        if (presenceDelayMs > 0) {
            Long presenceDelaySeconds = Math.ceil(presenceDelayMs / 1000.0) as Long
            nextDelaySeconds = (nextDelaySeconds == null) ? presenceDelaySeconds : Math.max(nextDelaySeconds, presenceDelaySeconds) as Long
        }
    }

    if (targetMode && location.mode != targetMode) {
        Integer cooldownMinutes = (modeChangeCooldownMinutes as Integer) ?: 20
        Long lastModeChangeTs = state.lastModeChangeTs as Long
        if (lastModeChangeTs && cooldownMinutes > 0) {
            Long cooldownDelayMs = (cooldownMinutes * 60000L) - (nowTs - lastModeChangeTs)
            if (cooldownDelayMs > 0) {
                Long cooldownDelaySeconds = Math.ceil(cooldownDelayMs / 1000.0) as Long
                nextDelaySeconds = (nextDelaySeconds == null) ? cooldownDelaySeconds : Math.min(nextDelaySeconds, cooldownDelaySeconds) as Long
            }
        }
    }

    if (nextDelaySeconds != null) {
        queueEvaluation((int) Math.max(1L, nextDelaySeconds))
    }
}

private List<String> defaultPausedModes() {
    return location?.modes?.collect { it.name }?.findAll { it?.equalsIgnoreCase("Night") } ?: []
}
