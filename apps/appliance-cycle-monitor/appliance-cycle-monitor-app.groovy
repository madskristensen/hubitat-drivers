/**
 * Appliance Cycle Monitor
 * Namespace: mads
 * Author:    Mads Kristensen
 * Version:   0.1.0
 *
 * Turns "dumb" appliances such as washers, dryers, and dishwashers into
 * status-reporting devices. Each appliance gets a child "Appliance Cycle Monitor
 * Device" whose applianceStatus follows a simple lifecycle:
 *
 *     Ready → Running → Finished → (door opens) → Ready
 *
 * Run detection is per-appliance and uses whichever signal fits the machine:
 *   • Power meter   — Running while watts ≥ the Running threshold; heading to
 *                     Finished once watts fall to ≤ the Finished threshold
 *                     (hysteresis deadband prevents fluttering)
 *   • Acceleration  — Running while the vibration sensor is "active"
 *
 * A short "quiet" debounce keeps a mid-cycle pause from prematurely flipping to
 * Finished. An optional door contact sensor is mirrored onto the child and, when
 * held open for 15 seconds after a cycle, resets the appliance to Ready ("you
 * unloaded it"); a quick peek that re-closes the door does not reset it.
 *
 * Changelog:
 *   0.1.0 — 2026-05-29 — Initial release: parent app + child driver, power (with hysteresis) / acceleration run detection, finish debounce, 15s door-hold-open reset, manual status override API.
 */

import groovy.transform.Field

@Field static final String  APP_VERSION       = "0.1.0"
@Field static final String  CHILD_DRIVER      = "Generic Appliance Device"
@Field static final String  CHILD_NS          = "mads"
@Field static final Integer MAX_APPLIANCES    = 10
@Field static final Integer SEED_DELAY_SECONDS = 5
@Field static final BigDecimal DEFAULT_POWER_THRESHOLD          = 5.0   // watts — at/above = Running
@Field static final BigDecimal DEFAULT_POWER_FINISHED_THRESHOLD = 2.0   // watts — at/below = heading to Finished
@Field static final Integer DEFAULT_FINISH_DELAY_MIN    = 3
@Field static final Integer DOOR_OPEN_READY_SECONDS      = 15   // door must stay open this long after a cycle before resetting to Ready

definition(
    name:           "Appliance Cycle Monitor",
    namespace:      "mads",
    author:         "Mads Kristensen",
    description:    "Report Ready / Running / Finished cycle status for washers, dryers, dishwashers, and other appliances using a power meter or vibration sensor, with a door-contact reset.",
    category:       "Convenience",
    singleInstance: false,
    iconUrl:        "https://github.githubassets.com/favicons/favicon.png",
    iconX2Url:      "https://github.githubassets.com/favicons/favicon.png",
    importUrl:      "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/appliance-cycle-monitor/appliance-cycle-monitor-app.groovy"
)

// ── Preferences ────────────────────────────────────────────────────────────────

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Appliance Cycle Monitor", install: true, uninstall: true) {
        section("Appliances") {
            input "applianceCount", "number",
                title: "How many appliances?", range: "1..${MAX_APPLIANCES}",
                defaultValue: 1, required: true, submitOnChange: true
        }

        int count = (settings.applianceCount ?: 1) as int
        (1..count).each { i -> applianceSection(i) }

        section("Logging") {
            input "logEnable", "bool", title: "Enable debug logging (auto-disables after 30 min)", defaultValue: false
            input "txtEnable", "bool", title: "Enable info logging", defaultValue: true
        }

        section("App Info") {
            paragraph "Version ${APP_VERSION}"
        }
    }
}

private void applianceSection(int i) {
    section(hideable: true, hidden: false, "Appliance ${i}: ${settings["applianceName_${i}"] ?: 'Unnamed'}") {
        input "applianceName_${i}", "text",
            title: "Name", defaultValue: "Appliance ${i}", required: true, submitOnChange: true

        input "runSourceType_${i}", "enum",
            title: "Run detection source",
            options: ["power": "Power meter", "accel": "Vibration / acceleration sensor"],
            required: true, submitOnChange: true

        String src = settings["runSourceType_${i}"]
        if (src == "power") {
            input "powerDevice_${i}", "capability.powerMeter",
                title: "Power meter device", required: true
            input "powerThreshold_${i}", "decimal",
                title: "Running when watts rise to ≥", defaultValue: DEFAULT_POWER_THRESHOLD, required: true
            input "powerFinishedThreshold_${i}", "decimal",
                title: "Finished when watts fall to ≤ (must be ≤ the Running value)",
                defaultValue: DEFAULT_POWER_FINISHED_THRESHOLD, required: true
        } else if (src == "accel") {
            input "accelDevice_${i}", "capability.accelerationSensor",
                title: "Acceleration / vibration sensor", required: true
        }

        input "finishDelayMin_${i}", "number",
            title: "Quiet minutes before marking Finished", range: "0..120",
            defaultValue: DEFAULT_FINISH_DELAY_MIN, required: true

        input "contactDevice_${i}", "capability.contactSensor",
            title: "Door contact sensor (optional — opening it after a cycle resets to Ready)", required: false
    }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

def installed() {
    state.appliances = [:]
    initialize()
}

def updated() {
    unschedule()
    unsubscribe()
    if (settings.logEnable) { runIn(1800, "logsOff") }
    initialize()
}

def uninstalled() {
    getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
    logDebug "initialize()"
    if (state.appliances == null) { state.appliances = [:] }
    reconcileChildren()
    subscribeAll()
    runIn(SEED_DELAY_SECONDS, "seedAll")
}

// ── Child device management ───────────────────────────────────────────────────

private int configuredCount() { return (settings.applianceCount ?: 1) as int }

private String childDni(int i) { return "appliance-cycle-monitor-${app.id}-${i}" }

private void reconcileChildren() {
    int count = configuredCount()
    Set<String> wanted = (1..count).collect { childDni(it) } as Set

    // Create / relabel wanted children
    (1..count).each { i ->
        String dni   = childDni(i)
        String label = settings["applianceName_${i}"] ?: "Appliance ${i}"
        def child = getChildDevice(dni)
        if (!child) {
            child = addChildDevice(CHILD_NS, CHILD_DRIVER, dni, [name: label, label: label, isComponent: true])
            logInfo "Created appliance child: ${label} (${dni})"
        } else if (child.label != label) {
            child.setLabel(label)
        }
        if (state.appliances[dni] == null) {
            state.appliances[dni] = [status: "Unknown", runningSince: null, pendingFinishAt: null, pendingReadyAt: null]
        }
    }

    // Remove children for appliances that no longer exist
    getChildDevices()?.each { c ->
        if (!wanted.contains(c.deviceNetworkId)) {
            logInfo "Removing stale appliance child: ${c.deviceNetworkId}"
            state.appliances.remove(c.deviceNetworkId)
            deleteChildDevice(c.deviceNetworkId)
        }
    }
}

// ── Subscriptions ─────────────────────────────────────────────────────────────

private void subscribeAll() {
    int count = configuredCount()
    (1..count).each { i ->
        String src = settings["runSourceType_${i}"]
        if (src == "power" && settings["powerDevice_${i}"]) {
            subscribe(settings["powerDevice_${i}"], "power", "powerHandler")
        } else if (src == "accel" && settings["accelDevice_${i}"]) {
            subscribe(settings["accelDevice_${i}"], "acceleration", "accelHandler")
        }
        if (settings["contactDevice_${i}"]) {
            subscribe(settings["contactDevice_${i}"], "contact", "contactHandler")
        }
    }
}

// ── Event handlers ────────────────────────────────────────────────────────────

def powerHandler(evt) {
    Integer i = applianceIndexForDevice(evt.deviceId, "power")
    if (i) { evaluateRun(i) }
}

def accelHandler(evt) {
    Integer i = applianceIndexForDevice(evt.deviceId, "accel")
    if (i) { evaluateRun(i) }
}

def contactHandler(evt) {
    Integer i = applianceIndexForDevice(evt.deviceId, "contact")
    if (i) { handleContact(i, evt.value as String) }
}

// Map an incoming event's device id back to its appliance slot + role.
private Integer applianceIndexForDevice(def deviceId, String role) {
    int count = configuredCount()
    String key = role == "power" ? "powerDevice_" : (role == "accel" ? "accelDevice_" : "contactDevice_")
    for (int i = 1; i <= count; i++) {
        def dev = settings["${key}${i}"]
        if (dev && dev.id?.toString() == deviceId?.toString()) { return i }
    }
    return null
}

// ── Run-state evaluation ──────────────────────────────────────────────────────

// Returns "running", "idle", or "hold". The power source applies hysteresis: a
// reading between the Finished and Running thresholds keeps ("holds") the current
// state so the status doesn't flutter around a single boundary. The acceleration
// source is binary (active = running, otherwise idle).
private String runSignal(int i) {
    String src = settings["runSourceType_${i}"]
    if (src == "power") {
        def dev = settings["powerDevice_${i}"]
        if (!dev) { return "hold" }
        BigDecimal watts  = (dev.currentValue("power") ?: 0) as BigDecimal
        BigDecimal runThr = (settings["powerThreshold_${i}"] ?: DEFAULT_POWER_THRESHOLD) as BigDecimal
        BigDecimal finThr = (settings["powerFinishedThreshold_${i}"] != null
                                ? settings["powerFinishedThreshold_${i}"]
                                : DEFAULT_POWER_FINISHED_THRESHOLD) as BigDecimal
        // Guard against misconfiguration: the Finished value can never exceed Running.
        if (finThr > runThr) { finThr = runThr }
        if (watts >= runThr) { return "running" }
        if (watts <= finThr) { return "idle" }
        return "hold"
    } else if (src == "accel") {
        def dev = settings["accelDevice_${i}"]
        if (!dev) { return "hold" }
        return (dev.currentValue("acceleration") as String) == "active" ? "running" : "idle"
    }
    return "hold"
}

private void evaluateRun(int i) {
    String dni = childDni(i)
    def st = state.appliances[dni]
    if (st == null) { st = [status: "Unknown", runningSince: null, pendingFinishAt: null, pendingReadyAt: null]; state.appliances[dni] = st }

    String sig = runSignal(i)
    if (sig == "hold") { return }   // deadband — keep current state, no work
    boolean running = (sig == "running")

    if (running) {
        if (st.status == "Running") {
            // Steady-state running: the common case for a chatty power meter.
            // Only touch state if we're cancelling a pending finish (a mid-cycle
            // dip recovered); otherwise return without mutating/persisting state.
            if (st.pendingFinishAt != null) { st.pendingFinishAt = null }
            return
        }
        st.status = "Running"
        st.runningSince = now()
        st.pendingFinishAt = null
        getChildDevice(dni)?.applyRunStarted(nowIso())
        pushState(i)
        logInfo "${settings["applianceName_${i}"]}: Running"
    } else {
        // Source went quiet. Only meaningful while a cycle is in progress.
        if (st.status == "Running") {
            // Start the finish countdown once per quiet period — don't reschedule
            // on every sub-threshold report.
            if (st.pendingFinishAt == null) {
                int delayMin = (settings["finishDelayMin_${i}"] ?: DEFAULT_FINISH_DELAY_MIN) as int
                st.pendingFinishAt = now() + (delayMin * 60_000L)
                scheduleNearestTimer()
                logDebug "${settings["applianceName_${i}"]}: quiet — Finished in ${delayMin} min unless it resumes"
            }
        } else if (st.status == "Unknown") {
            // First idle reading on a never-run appliance — baseline to Ready.
            st.status = "Ready"
            pushState(i)
        }
    }
}

// Single shared finish timer: scan all appliances, fire the due ones, reschedule
// for the next-soonest. Avoids per-appliance schedule-name clobbering.
// Single shared timer: scan all appliances, fire any due finish/ready transitions,
// then reschedule for the next-soonest pending deadline. Avoids per-appliance
// schedule-name clobbering.
def checkTimers() {
    long nowMs = now()
    state.appliances.each { dni, st ->
        Integer i = indexForDni(dni)

        // Pending Finished: a cycle went quiet long enough.
        if (st.pendingFinishAt != null && nowMs >= (st.pendingFinishAt as long)) {
            if (st.status == "Running") {
                st.status = "Finished"
                BigDecimal durMin = null
                if (st.runningSince) {
                    durMin = (((nowMs - (st.runningSince as long)) / 60_000L) as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
                }
                getChildDevice(dni)?.applyRunEnded(nowIso(), durMin)
                if (i) { pushState(i) }
                logInfo "${settings["applianceName_${i}"]}: Finished${durMin != null ? " (${durMin} min cycle)" : ''}"
                // If the door is already open as the cycle ends, start the
                // hold-open countdown now (no further contact event will fire).
                if (i && currentContact(i) == "open") {
                    st.pendingReadyAt = nowMs + (DOOR_OPEN_READY_SECONDS * 1000L)
                }
            }
            st.pendingFinishAt = null
        }

        // Pending Ready: the door has been held open long enough after a cycle.
        if (st.pendingReadyAt != null && nowMs >= (st.pendingReadyAt as long)) {
            if (st.status == "Finished") {
                st.status = "Ready"
                st.runningSince = null
                st.pendingFinishAt = null
                if (i) { pushState(i) }
                logInfo "${settings["applianceName_${i}"]}: door held open after cycle — reset to Ready"
            }
            st.pendingReadyAt = null
        }
    }
    scheduleNearestTimer()
}

private void scheduleNearestTimer() {
    long nowMs = now()
    Long nearest = null
    state.appliances.each { dni, st ->
        [st.pendingFinishAt, st.pendingReadyAt].each { v ->
            if (v != null) {
                long due = v as long
                if (nearest == null || due < nearest) { nearest = due }
            }
        }
    }
    if (nearest != null) {
        int delaySec = Math.max(1, (int) Math.ceil((nearest - nowMs) / 1000.0d))
        runIn(delaySec, "checkTimers", [overwrite: true])
    }
}

// ── Contact (door) handling ───────────────────────────────────────────────────

private void handleContact(int i, String contactState) {
    String dni = childDni(i)
    def st = state.appliances[dni]
    if (st == null) { return }

    def child = getChildDevice(dni)
    child?.applyContact(contactState)

    // After a finished cycle, the door must stay open for DOOR_OPEN_READY_SECONDS
    // before we treat it as "unloaded" and reset to Ready. A quick open/close
    // (peeking inside) won't trigger the reset.
    if (st.status == "Finished") {
        if (contactState == "open") {
            st.pendingReadyAt = now() + (DOOR_OPEN_READY_SECONDS * 1000L)
            scheduleNearestTimer()
            logDebug "${settings["applianceName_${i}"]}: door opened — Ready in ${DOOR_OPEN_READY_SECONDS}s if it stays open"
        } else {
            // Door closed again before the timer elapsed — cancel the pending reset.
            if (st.pendingReadyAt != null) {
                st.pendingReadyAt = null
                logDebug "${settings["applianceName_${i}"]}: door closed before reset — staying Finished"
            }
        }
    } else if (st.pendingReadyAt != null) {
        // Status changed out from under a pending reset (e.g. a new cycle began).
        st.pendingReadyAt = null
    }
    pushState(i)
}

// ── State → child device ──────────────────────────────────────────────────────

private void pushState(int i) {
    String dni = childDni(i)
    def st = state.appliances[dni]
    def child = getChildDevice(dni)
    if (!child || st == null) { return }

    String doorState = currentContact(i)
    child.applyStatus(st.status as String)
    child.applyStatusText(buildStatusText(st.status as String, doorState))
}

private String buildStatusText(String status, String doorState) {
    String base
    switch (status) {
        case "Running":  base = "Running";                  break
        case "Finished": base = "Finished — ready to unload"; break
        case "Ready":    base = "Ready";                     break
        default:         base = "Unknown";                   break
    }
    if (doorState == "open" && status != "Running") {
        base = base + " · door open"
    }
    return base
}

private String currentContact(int i) {
    def dev = settings["contactDevice_${i}"]
    return dev ? (dev.currentValue("contact") as String ?: "closed") : "closed"
}

// ── Seeding & refresh ─────────────────────────────────────────────────────────

def seedAll() {
    int count = configuredCount()
    (1..count).each { i ->
        // Mirror the door first so status text is accurate, then evaluate run state.
        def cd = settings["contactDevice_${i}"]
        if (cd) { getChildDevice(childDni(i))?.applyContact(cd.currentValue("contact") as String ?: "closed") }
        evaluateRun(i)
        pushState(i)
    }
}

def refresh() {
    logDebug "refresh()"
    seedAll()
}

// Manual override from a child device command (setStatus / markRunning / …).
def applyManualStatus(String dni, String status) {
    def st = state.appliances[dni]
    if (st == null) { return }
    Integer i = indexForDni(dni)
    long nowMs = now()

    st.status = status
    st.pendingFinishAt = null
    st.pendingReadyAt = null
    if (status == "Running") {
        st.runningSince = nowMs
        getChildDevice(dni)?.applyRunStarted(nowIso())
    } else if (status == "Finished") {
        BigDecimal durMin = null
        if (st.runningSince) {
            durMin = (((nowMs - (st.runningSince as long)) / 60_000L) as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)
        }
        getChildDevice(dni)?.applyRunEnded(nowIso(), durMin)
    } else {
        st.runningSince = null
    }
    if (i) { pushState(i) }
    logInfo "${settings["applianceName_${i}"]}: status manually set to ${status}"
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private Integer indexForDni(String dni) {
    int count = configuredCount()
    for (int i = 1; i <= count; i++) {
        if (childDni(i) == dni) { return i }
    }
    return null
}

private String nowIso() { return new Date().format("yyyy-MM-dd'T'HH:mm:ss", location.timeZone) }

private void logDebug(String msg) { if (settings.logEnable) { log.debug msg } }
private void logInfo(String msg)  { if (settings.txtEnable != false) { log.info msg } }

def logsOff() {
    log.info "Debug logging disabled"
    app.updateSetting("logEnable", [value: false, type: "bool"])
}
