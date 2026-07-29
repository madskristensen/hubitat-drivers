/**
 * Climate Advisor
 * Namespace: mads
 * Author:    Mads Kristensen
 * Version:   0.4.30
 *
 * Changelog:
 *   0.4.30 — 2026-07-29 — Require outdoor temperature to be above the cooling setpoint for warm-weather close advice and below the heating setpoint for cold-weather close advice.
 *   0.4.29 — 2026-07-27 — Make open-window advice conservative across multiple indoor sensors: cooling requires outdoor air cooler than every zone reading, and warming requires it warmer than every zone reading.
 *   0.4.28 — 2026-07-26 — Require indoor temperature to fall below the heating setpoint before advising that windows be closed for cold outdoor air; outdoor temperature/trend alone no longer triggers the warning.
 *   0.4.27 — 2026-07-26 — Make window-advice wording trend-aware: say “getting cooler/warmer” only when the measured trend agrees; setpoint-breach advice without that trend uses neutral “cooler/warmer outside” wording.
 *   0.4.26 — 2026-07-26 — Restore AQI alerts, honor AQI/trend threshold boundaries, constrain trend calculations to the configured window, keep all temperature-driven window advice at warning severity, reset acknowledgments for external alerts, remove unused indoor-trend tracking, and harden optional weather/device handling.
 *   0.4.25 — 2026-07-26 — Suppress open-window advice immediately when any configured contact is physically open, while retaining the qualification delay before open contacts trigger close-window advice.
 *   0.4.24 — 2026-07-21 — Add a configurable window/door open qualification delay (default 60 seconds) so brief contact openings do not change status; closing still clears immediately.
 *   0.4.23 — 2026-07-21 — Unify opening/closing advice into one action per zone. Use indoor and outdoor proximity plus outdoor trend for predictive warnings, escalate when indoor crosses the setpoint, shorten messages, and notify only for a new action or escalation.
 *   0.4.22 — 2026-07-21 — Normalize device inputs so a single optional indoor temperature sensor is used reliably instead of being skipped when Hubitat returns a scalar device selection.
 *   0.4.21 — 2026-07-21 — Restrict AC-off and heat-off close-windows fallbacks to thermostatMode off. Previously cooling mode could produce a contradictory heating warning (and heating mode could produce a cooling warning).
 *   0.4.20 — 2026-07-20 — Close-windows alerts now require an open contact when contact sensors are configured, pre-alerts stop at the setpoint so they do not overlap breach alerts, and alert text remains stable for each active episode so SharpTools and speakers trigger only once. Global/per-zone duplicate speaker selections are also deduplicated.
 *   0.4.19 — 2026-07-20 — Use thermostat temperature when a zone has no dedicated indoor temperature sensors. Previously these zones were skipped entirely, leaving SharpTools on the idle forecast instead of showing applicable close-windows warnings.
 *   0.4.18 — 2026-06-22 — Symmetric heating counterpart to 0.4.17: new close-windows warning for the windows-open/heat-off case. When the thermostat isn't actively heating (e.g. turned off because windows are open), warn (severity 2) once indoor is within the heating pre-alert offset of the heat setpoint AND it's colder outside than that setpoint.
 *   0.4.17 — 2026-06-22 — New close-windows warning for the windows-open/AC-off case: when the thermostat isn't actively cooling (e.g. turned off because windows are open), warn (severity 2) once indoor is within the cooling pre-alert offset of the cool setpoint AND it's hotter outside than that setpoint. Previously every cooling alert required thermostatMode cool/auto, so an "off" thermostat produced no status message.
 *   0.4.16 — 2026-06-10 — Speaker announcements can now be restricted to selected location modes (new "announceModes" input). Leave empty to allow all modes; otherwise speakers stay silent outside the chosen modes (e.g. avoid blasting alerts overnight).
 *   0.4.15 — 2026-05-29 —
 *   0.4.14 — 2026-05-28 — Idle status: prefix today's high segment with the current condition emoji to mirror the Tomorrow styling ("Today ☀️ high 88°").
 *   0.4.13 — 2026-05-25 — Idle status: restyle tomorrow segment to "Tomorrow ☁️ ↓49° ↑63°" — emoji replaces the condition word; up/down arrows make low/high scannable.
 *   0.4.12 — 2026-05-25 — Idle status: capitalize "Tomorrow" segment so it reads consistently regardless of position in the line.
 *   0.4.11 — 2026-05-25 — Idle status: prefix tomorrow's condition word with an emoji (☁️ overcast, ☀️ sunny, ⛅ partly cloudy, etc.).
 *   0.4.10 — 2026-05-25 — Idle status: drop the "within 6h" suffix on rain probability (6h is the implicit default); keep "within 1h" only for urgent imminent rain.
 *   0.4.9 — 2026-05-25 — Idle status: capitalize the first segment for nicer dashboard tile reading; remove wind gusts segment (not actionable for this user).
 *   0.4.8 — 2026-05-25 — Reshape idle status line to be more actionable: drop weather emoji/condition, current temp, today's low, and trend arrow. Keep only signals that change behavior: rain probability (≥1h 60%/6h 30%), today's high (before 4pm only), current feels-like (when delta ≥5°F), tomorrow's range/condition/rain (after 4pm), gusts/UV/AQI as before.
 *   0.4.7 — 2026-05-25 — Feels-like calc now prefers humidity from the outdoor temperature device when it's a combo temp+humidity sensor; falls back to the weather device's humidity otherwise
 *   0.4.6 — 2026-05-25 — Feels-like now also adjusts in the mild range (50–80°F) by applying the weather device's apparent−temperature delta to the local sensor reading, so a breezy 64° day shows the wind cooling effect (NWS wind chill / heat index formulas only cover their strict regimes)
 *   0.4.5 — 2026-05-25 — Idle-status feels-like is now computed from the local outdoor sensor temperature + weather device wind/humidity (NWS wind chill ≤50°F, heat index ≥80°F) instead of the weather device's forecast-derived apparentTemperature; falls back to apparentTemperature when wind/humidity are unavailable
 *   0.4.4 — 2026-05-25 — Drop F/C scale suffix from idle status temperatures (just °); the weather device already implies the scale
 *   0.4.3 — 2026-05-25 — Idle status line is now actionable: drops "House comfortable" filler, hides AQI when good, adds outdoor trend arrow, feels-like (when ≥3° off), today's high/low, precip probability (1h/6h), wind gusts ≥20 mph, UV ≥8. Auto-detects standard attributes from Open-Meteo Weather Enhanced driver.
 *   0.4.2 — 2026-05-25 — Weather attribute is now a dropdown of the selected sensor's attributes; rain keyword field supports comma-separated includes and ! prefix exclusions (e.g. "rain,!light rain")
 *   0.4.1 — 2026-05-24
 *   0.4.0 — 2026-05-24 — External message API (pushMessage/clearMessage on child device for Rule Machine + webCoRE); larger trend sample retention + staleness guard fixes sparse-reporting outdoor sensors; rename trend states to "heating up"/"cooling down"
 *   0.3.4 — 2026-05-23 — Replace idle "all clear" with contextual weather/AQI dashboard line
 *   0.3.3 — 2026-05-23 — Free cooling opportunity evaluator: notify when outside cooler than inside and AC would otherwise run
 *   0.3.2 — 2026-05-23 — Set isComponent: true on child device; provides ownership metadata and auto-cleanup on app uninstall; device appears in Devices list AND under the app in App Details (same platform behavior as Groups and Scenes)
 *   0.3.1 — 2026-05-23 — Remove redundant aqiAttribute input — capability.airQuality standardizes attribute as airQualityIndex.
 *   0.3.0 — 2026-05-23 — Lift AQI to house-level: one global AQI device input instead of per-zone. Breaking config change (re-select your AQI device after upgrade).
 *   0.2.3 — 2026-05-23 — Add missing groovy.transform.Field import (fixes Hubitat publish failure)
 *   0.2.2 — 2026-05-23 — Remove tempTrend legacy alias attribute (use outdoorTrend)
 *   0.2.1 — 2026-05-23 — Single-child architecture, optional dashboard children, 4-level severity restored, namespace fix, null-slope guard, dedicated indoor temp handler, comfort-open advisory
 *   0.1.0 — 2026-05-23 — Initial release: parent app + child driver, per-zone alerts, outdoor/indoor trend detection, predictive close-windows alerts (cooling + heating); event-coalescing debounce, change-only sendEvent, lazy trend computation, childDniMap lookup cache.
 */

import groovy.json.JsonOutput
import groovy.transform.Field

@Field static final String  APP_VERSION        = "0.4.30"
@Field static final String  CHILD_DRIVER       = "Climate Advisor Device"
@Field static final String  CHILD_NS           = "mads"
@Field static final Integer MAX_AGG_MSG        = 20
@Field static final Integer MAX_ZONE_MSG       = 10
@Field static final Integer AQI_WARN_DEFAULT   = 51     // EPA Moderate boundary
@Field static final Integer AQI_DANGER_DEFAULT = 101    // EPA Unhealthy for Sensitive Groups
@Field static final Integer DEBOUNCE_SECONDS   = 1
@Field static final Integer SEED_DELAY_SECONDS = 5
@Field static final Integer MIN_TREND_SAMPLES  = 10     // retain enough history if the configured window is later enlarged

definition(
    name:           "Climate Advisor",
    namespace:      "mads",
    author:         "Mads Kristensen",
    description:    "Per-zone predictive open/close window advice, outdoor trend detection, and aggregate house status for SharpTools + Rule Machine.",
    category:       "Convenience",
    singleInstance: false,
    iconUrl:        "https://github.githubassets.com/favicons/favicon.png",
    iconX2Url:      "https://github.githubassets.com/favicons/favicon.png",
    importUrl:      "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/climate-advisor/climate-advisor-app.groovy"
)

// ── Preferences ────────────────────────────────────────────────────────────────

preferences {
    page(name: "mainPage")
    page(name: "globalPage")
    page(name: "zonesPage")
    page(name: "notificationsPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Climate Advisor", install: true, uninstall: true) {
        section("Setup") {
            href "globalPage",        title: "Global settings",   description: "Outdoor weather, trend windows, dashboard devices, logging"
            href "zonesPage",         title: "Zones",             description: "Configure ${settings.zoneCount ?: 1} zone(s)"
            href "notificationsPage", title: "Notifications",     description: "Global notification and throttling defaults"
        }
        section("App Info") {
            paragraph "Version ${APP_VERSION}"
        }
    }
}

def globalPage() {
    String tempScale = location?.temperatureScale ?: "F"
    dynamicPage(name: "globalPage", title: "Global Settings") {
        section("Outdoor Conditions") {
            input "outdoorTempDevice", "capability.temperatureMeasurement",
                title: "Outdoor temperature device", required: true
            input "weatherDevice", "capability.sensor",
                title: "Weather / forecast device (optional)", required: false, submitOnChange: true
            if (settings.weatherDevice) {
                List attrNames = []
                try {
                    attrNames = settings.weatherDevice.getSupportedAttributes()*.name.unique().sort()
                } catch (Exception e) {
                    log.warn "Could not enumerate attributes for weather device: ${e.message}"
                }
                if (attrNames) {
                    String defaultAttr = attrNames.contains("weather") ? "weather"
                                       : attrNames.contains("condition") ? "condition"
                                       : attrNames.contains("conditionText") ? "conditionText"
                                       : attrNames[0]
                    input "weatherAttribute", "enum",
                        title: "Weather condition attribute",
                        options: attrNames, defaultValue: defaultAttr, required: false
                } else {
                    input "weatherAttribute", "string",
                        title: "Weather condition attribute", defaultValue: "weather", required: false
                }
                input "rainKeyword", "string",
                    title: "Rain keywords (comma-separated; prefix with ! to exclude)",
                    defaultValue: "rain", required: false
                paragraph "Matches if the weather attribute contains any keyword, unless it also contains an excluded keyword. " +
                          "Example: <code>rain, !light rain</code> matches \"rain\" and \"heavy rain\" but not \"light rain\". " +
                          "Matching is case-insensitive."
            }
        }
        section("Air Quality") {
            input "aqiDevice", "capability.airQuality",
                title: "Air quality sensor (optional — house-wide)", required: false
        }
        section("AQI Thresholds") {
            input "aqiWarnThreshold", "number",
                title: "AQI warning threshold (default 51 — EPA Moderate)", range: "0..500", defaultValue: 51, required: true
            input "aqiDangerThreshold", "number",
                title: "AQI danger threshold (default 101 — EPA Unhealthy for Sensitive Groups)", range: "0..500", defaultValue: 101, required: true
        }
        section("Trend Detection") {
            input "trendWindowMinutes", "number",
                title: "Outdoor trend window (minutes)", range: "5..1440", defaultValue: 30, required: true
            input "outdoorTrendRisingThreshold10min", "decimal",
                title: "Rising threshold (°${tempScale} per 10 min)", defaultValue: 0.2, required: true
            input "outdoorTrendFallingThreshold10min", "decimal",
                title: "Falling threshold (°${tempScale} per 10 min)", defaultValue: -0.2, required: true
        }
        section("Window / Door Contacts") {
            input "contactOpenDelaySeconds", "number",
                title: "Seconds before an open window or door triggers close/rain status",
                range: "0..3600", defaultValue: 60, required: true
        }
        section("Dashboard Devices") {
            input "createDashboardDevices", "bool",
                title: "Create dashboard child device (one house-wide device for SharpTools / Hubitat Dashboard)",
                defaultValue: false, required: false, submitOnChange: true
            if (settings.createDashboardDevices) {
                paragraph "A single 'Climate Advisor Device' will be created as a component device nested under this app (visible in Apps, not in the main Devices list)."
            }
        }
        section("Logging") {
            input "logEnable", "bool",
                title: "Enable debug logging (auto-disables after 30 min)", defaultValue: false, required: false
            input "txtEnable", "bool",
                title: "Enable info logging", defaultValue: true, required: false
        }
    }
}

def notificationsPage() {
    dynamicPage(name: "notificationsPage", title: "Notifications") {
        section("Global notification defaults") {
            input "globalNotificationDevices", "capability.notification",
                title: "Global notification devices (all zones)", multiple: true, required: false
            input "globalSpeakers", "capability.speechSynthesis",
                title: "Global speakers for announcements (all zones, optional)", multiple: true, required: false
            input "announceSeverityThreshold", "number",
                title: "Minimum severity for speaker announcements (1=info, 2=warning/pre-alerts, 3=danger/breaches)",
                range: "1..3", defaultValue: 2, required: true
            input "announceModes", "mode",
                title: "Location modes in which speaker announcements are enabled (leave empty to allow all modes)",
                multiple: true, required: false
        }
    }
}

def zonesPage() {
    String tempScale = location?.temperatureScale ?: "F"
    dynamicPage(name: "zonesPage", title: "Zones") {
        section("Zone Count") {
            input "zoneCount", "number",
                title: "Number of zones", range: "1..10", defaultValue: 1, required: true,
                submitOnChange: true
        }

        Integer count = Math.max(1, Math.min((settings.zoneCount ?: 1) as Integer, 10))
        (1..count).each { i ->
            section("Zone ${i}") {
                input "zone${i}Name", "string",
                    title: "Zone ${i} name (e.g., Upstairs, Sunroom, Workshop)", required: true
                input "zone${i}Thermostats", "capability.thermostat",
                    title: "Thermostats (optional)", multiple: true, required: false
                input "zone${i}IndoorTempSensors", "capability.temperatureMeasurement",
                    title: "Indoor temperature sensors (optional — uses thermostat temp if omitted)", multiple: true, required: false
                input "zone${i}ContactSensors", "capability.contactSensor",
                    title: "Window / door contact sensors (optional)", multiple: true, required: false
                input "zone${i}Speakers", "capability.speechSynthesis",
                    title: "Speakers for zone announcements (optional)", multiple: true, required: false
                input "zone${i}CoolingPreAlertOffset", "decimal",
                    title: "Cooling anticipation offset for indoor/outdoor temperatures (°${tempScale})", range: "0..20", defaultValue: 3.0, required: true
                input "zone${i}HeatingPreAlertOffset", "decimal",
                    title: "Heating anticipation offset for indoor/outdoor temperatures (°${tempScale})", range: "0..20", defaultValue: 3.0, required: true
                input "zone${i}NotificationDevices", "capability.notification",
                    title: "Notification devices for this zone (optional)", multiple: true, required: false
            }
        }
    }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

def installed() {
    state.outdoorSamples     = []
    state.contactOpenedAt    = [:]
    state.lastNotificationAt = [:]
    state.activeMessages     = [:]
    state.externalMessages   = [:]
    state.presentedMessages  = [:]
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
    reconcileChildren()
    List zones = configuredZones()
    subscribeAll(zones)
    schedulePendingContactEvaluations(zones)
    runIn(SEED_DELAY_SECONDS, "evaluateAll")
}

// ── Child device management ───────────────────────────────────────────────────

private String childDni() { return "climate-advisor-${app.id}" }

private def lookupChild() { return getChildDevice(childDni()) }

private void reconcileChildren() {
    String dni   = childDni()
    boolean want = settings.createDashboardDevices == true
    if (want && !getChildDevice(dni)) {
        String label = app.label ?: "Climate Advisor"
        addChildDevice(CHILD_NS, CHILD_DRIVER, dni, [name: label, label: label, isComponent: true])
        logInfo "Created dashboard child: ${dni}"
    } else if (!want && getChildDevice(dni)) {
        deleteChildDevice(dni)
        logInfo "Deleted dashboard child (dashboard devices toggled off): ${dni}"
    }
}

// ── Subscriptions (surgical — exact attributes only) ─────────────────────────

private void subscribeAll(List zones) {
    if (settings.outdoorTempDevice) {
        subscribe(settings.outdoorTempDevice, "temperature", outdoorTempHandler)
    }

    String wAttr = settings.weatherAttribute as String ?: "weather"
    if (settings.weatherDevice) {
        subscribe(settings.weatherDevice, wAttr, debounceHandler)
    }

    if (settings.aqiDevice) {
        subscribe(settings.aqiDevice, "airQualityIndex", debounceHandler)
    }

    zones.each { zone ->
        temperatureDevicesForZone(zone).each { sensor ->
            subscribe(sensor, "temperature", indoorTempHandler)
        }
        zone.thermostats?.each { t ->
            subscribe(t, "thermostatMode",    debounceHandler)
            subscribe(t, "coolingSetpoint",   debounceHandler)
            subscribe(t, "heatingSetpoint",   debounceHandler)
        }
        zone.contactSensors?.each { c ->
            subscribe(c, "contact", contactHandler)
        }
    }
}

// ── Event handlers ────────────────────────────────────────────────────────────

// Outdoor temp handler: O(1) sample append, then debounce eval
def outdoorTempHandler(evt) {
    try {
        BigDecimal t = evt.value as BigDecimal
        Long ts = now()
        List samples = (state.outdoorSamples ?: []) + [[now: ts, t: t]]
        state.outdoorSamples = pruneSamples(samples, ts)
    } catch (Exception e) {
        log.warn "outdoorTempHandler sample error: ${e.message}"
    }
    runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
}

// Indoor temperature changes only require a debounced reevaluation.
def indoorTempHandler(evt) {
    runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
}

// Brief contact openings should not trigger close-window advice. Re-evaluate
// immediately to clear stale open-window advice, then qualify close advice later.
def contactHandler(evt) {
    Map openedAt = (state.contactOpenedAt ?: [:]) as Map
    String deviceId = evt.device?.id as String
    if (evt.value == "open") {
        openedAt[deviceId] = now()
        state.contactOpenedAt = openedAt
        Integer delaySeconds = configuredContactOpenDelaySeconds()
        runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
        runIn(Math.max(DEBOUNCE_SECONDS, delaySeconds), "contactDelayElapsed", [overwrite: false])
    } else {
        openedAt.remove(deviceId)
        state.contactOpenedAt = openedAt
        runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
    }
}

def contactDelayElapsed() {
    evaluateAll()
}

private void schedulePendingContactEvaluations(List zones) {
    Map openedAt = (state.contactOpenedAt ?: [:]) as Map
    Long delayMs = configuredContactOpenDelaySeconds() * 1000L
    Long nowMs = now()
    zones.each { zone ->
        (zone.contactSensors ?: []).each { contact ->
            Long opened = openedAt[contact.id as String] as Long
            if (contact.currentValue("contact") == "open" && opened != null) {
                Long remainingMs = delayMs - (nowMs - opened)
                if (remainingMs > 0) {
                    Integer remainingSeconds = Math.max(1, Math.ceil(remainingMs / 1000G) as Integer)
                    runIn(remainingSeconds, "contactDelayElapsed", [overwrite: false])
                }
            }
        }
    }
}

// All other handlers: schedule the debounced evaluation
def debounceHandler(evt) {
    runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
}

// ── Trend computation (lazy — only called from evaluateAll) ───────────────────

Map computeTrend(List samples, Integer windowMinutes, BigDecimal risingThreshold, BigDecimal fallingThreshold) {
    Long windowStart = now() - windowMinutes * 60 * 1000L
    List window = (samples ?: []).findAll { it.now >= windowStart }.sort { it.now }
    if (window.size() < 2) { return [trend: "unknown", slope10min: null] }

    def oldest = window.first()
    def newest = window.last()
    BigDecimal spanMinutes = (newest.now - oldest.now) / 60000G
    if (spanMinutes < 5G) { return [trend: "unknown", slope10min: null] }

    BigDecimal slopePerMinute = ((newest.t as BigDecimal) - (oldest.t as BigDecimal)) / spanMinutes
    BigDecimal slope10min = slopePerMinute * 10G

    String trend = slope10min >= risingThreshold ? "heating up" :
                   slope10min <= fallingThreshold ? "cooling down" :
                   "steady"
    return [trend: trend, slope10min: slope10min]
}

private Map outdoorTrendResult() {
    BigDecimal rising = settings.outdoorTrendRisingThreshold10min == null ? 0.2G : settings.outdoorTrendRisingThreshold10min as BigDecimal
    BigDecimal falling = settings.outdoorTrendFallingThreshold10min == null ? -0.2G : settings.outdoorTrendFallingThreshold10min as BigDecimal
    return computeTrend((state.outdoorSamples ?: []) as List, configuredTrendWindowMinutes(), rising, falling)
}

private List pruneSamples(List samples, Long nowTs) {
    Integer windowMin = configuredTrendWindowMinutes()
    // Retain extra history so increasing the configured window does not immediately
    // discard all prior context; computeTrend still uses only the active window.
    Long retainMin = Math.max(windowMin * 3L + 5L, 90L)
    Long cutoff = nowTs - retainMin * 60 * 1000L
    List sorted = (samples ?: []).sort { it.now }
    List byTime = sorted.findAll { it.now >= cutoff }
    if (sorted.size() <= MIN_TREND_SAMPLES) { return sorted }
    if (byTime.size() >= MIN_TREND_SAMPLES) { return byTime }
    // Fall back to the most recent MIN_TREND_SAMPLES samples regardless of age.
    return sorted[-MIN_TREND_SAMPLES..-1]
}

// ── Evaluation orchestrator ───────────────────────────────────────────────────

def evaluateAll() {
    try {
        logDebug "evaluateAll()"

        Map outdoorTrend = outdoorTrendResult()
        BigDecimal outdoorTemp = safeCurrentTemp(settings.outdoorTempDevice)
        boolean rainDetected = checkRain()
        BigDecimal houseAqi = currentHouseAqi()
        List zones = configuredZones()
        Long nowMs = now()

        Map prevActive = (state.activeMessages ?: [:]) as Map
        Map newActive  = [:]
        List allMessages = []
        Map zoneResults = [:]

        zones.each { zone ->
            Map zoneEval     = evaluateZone(zone, outdoorTemp, outdoorTrend, rainDetected, houseAqi)
            List candidates  = zoneEval.candidates as List
            Map  meta        = zoneEval.meta as Map
            List zoneResolved = resolveMessages(candidates, prevActive, newActive, nowMs)
            allMessages.addAll(zoneResolved)

            List zoneSorted = zoneResolved.sort { a, b ->
                int sc = (b.severity as Integer) <=> (a.severity as Integer)
                sc != 0 ? sc : (b.ts as Long) <=> (a.ts as Long)
            }.take(MAX_ZONE_MSG)
            int    zoneSev    = zoneSorted ? zoneSorted.collect { it.severity as Integer }.max() : 0
            String zoneLatest = zoneSorted ? zoneSorted[0].text : "All clear — no climate issues detected"
            zoneResults[zone.id as String] = [
                severity        : zoneSev,
                severityText    : severityText(zoneSev),
                latestMessage   : zoneLatest,
                indoorTemp      : meta.indoorTemp,
                openContactCount: meta.openContactCount,
                aqi             : meta.aqi
            ]
        }

        List houseCandidates = []
        Map aqiAlert = evaluateHouseAqi(houseAqi)
        if (aqiAlert) { houseCandidates << aqiAlert }
        houseCandidates.addAll(evaluateHouseRain(rainDetected, zones))
        allMessages.addAll(resolveMessages(houseCandidates, prevActive, newActive, nowMs))

        state.activeMessages = newActive

        // External messages pushed by Rule Machine / webCoRE pistons / other automations.
        // These live in state.externalMessages until explicitly cleared.
        Map externalMessages = (state.externalMessages ?: [:]) as Map
        externalMessages.each { String key, def raw ->
            Map msg = raw as Map
            String text = msg?.text as String
            if (!text) { return }
            int sev = ((msg.severity ?: 1) as Integer)
            allMessages << [
                id          : "ext-${key}".toString(),
                severity    : sev,
                severityText: severityText(sev),
                source      : "External",
                family      : "external",
                text        : text,
                zoneId      : null,
                ts          : (msg.ts as Long) ?: nowMs
            ]
        }

        allMessages = allMessages.sort { a, b ->
            int sc = (b.severity as Integer) <=> (a.severity as Integer)
            sc != 0 ? sc : (b.ts as Long) <=> (a.ts as Long)
        }
        List displayedMessages = allMessages.take(MAX_AGG_MSG)

        int    aggSeverity     = allMessages ? allMessages.collect { it.severity as Integer }.max() : 0
        String aggSeverityText = severityText(aggSeverity)
        String aggLatest       = allMessages ? allMessages[0].text : buildIdleStatus(outdoorTemp, rainDetected, houseAqi)
        int    alertCount      = allMessages.count { (it.severity as Integer) >= 1 }
        List uniqueContacts    = zones.collectMany { it.contactSensors ?: [] }.unique { it.id as String }
        int    totalOpenContacts = qualifiedOpenContacts(uniqueContacts).size()
        String houseStatus     = alertCount > 0 ? "${alertCount} active alert${alertCount > 1 ? 's' : ''}" : "House comfortable"
        String aggJson         = JsonOutput.toJson(displayedMessages)

        Map previousPresented = (state.presentedMessages ?: [:]) as Map
        Map currentPresented = allMessages.collectEntries { msg ->
            [(msg.id as String): [severity: msg.severity as Integer, text: msg.text as String]]
        }
        boolean hasNewOrEscalated = currentPresented.any { id, entry ->
            Map previous = previousPresented[id] as Map
            int severity = entry.severity as Integer
            int previousSeverity = (previous?.severity ?: 0) as Integer
            !previous || severity > previousSeverity || (severity == previousSeverity && entry.text != previous.text)
        }
        state.presentedMessages = currentPresented

        def aggChild = lookupChild()
        if (aggChild) {
            if (hasNewOrEscalated) { sendEventIfChanged(aggChild, "acknowledged", "false") }

            sendEventIfChanged(aggChild, "severity",              aggSeverity)
            sendEventIfChanged(aggChild, "severityText",          aggSeverityText)
            sendEventIfChanged(aggChild, "latestMessage",         aggLatest)
            sendEventIfChanged(aggChild, "messages",              aggJson)
            sendEventIfChanged(aggChild, "houseStatus",           houseStatus)
            sendEventIfChanged(aggChild, "contact",               aggSeverity >= 1 ? "open" : "closed")
            sendEventIfChanged(aggChild, "outdoorTrend",          outdoorTrend.trend ?: "unknown")
            sendEventIfChanged(aggChild, "outdoorTempSlope10min", outdoorTrend.slope10min == null ? 0 : outdoorTrend.slope10min,
                    "°${location?.temperatureScale ?: 'F'}/10 min")
            sendEventIfChanged(aggChild, "activeAlertCount",      alertCount)
            sendEventIfChanged(aggChild, "openContactCount",      totalOpenContacts)
            sendEventIfChanged(aggChild, "zoneCount",             zones.size())
            pushZoneAttributes(aggChild, zones, zoneResults)
        }

        handleNotifications(allMessages, zones)

    } catch (Exception e) {
        log.warn "evaluateAll error: ${e.message}"
    }
}

// Reconcile candidate messages against the previous pass. Keep an active episode's
// timestamp and text stable so changing sensor values do not retrigger dashboards
// or speakers. A cleared/reappearing or escalating alert gets a fresh entry.
private List resolveMessages(List candidates, Map prevActive, Map newActive, Long nowMs) {
    List resolved = []
    (candidates ?: []).each { candidate ->
        if (candidate == null) { return }
        String id = candidate.id as String
        Map prev  = prevActive[id] as Map
        Map entry
        if (prev && (prev.severity as Integer) == (candidate.severity as Integer) &&
                prev.family == candidate.family && prev.text == candidate.text) {
            entry = prev
        } else {
            entry = candidate + [ts: nowMs]
        }
        newActive[id] = entry
        resolved << entry
    }
    return resolved
}

// ── Zone evaluation ───────────────────────────────────────────────────────────

// Returns a Map {candidates: List, meta: Map{indoorTemp, openContactCount, aqi}}
private Map evaluateZone(Map zone, BigDecimal outdoorTemp, Map outdoorTrend, boolean rainDetected, BigDecimal houseAqi) {
    List contacts = zone.contactSensors ?: []
    List physicallyOpenContacts = contacts.findAll { it.currentValue("contact") == "open" }
    List qualifiedContacts = qualifiedOpenContacts(contacts)
    Map meta = [
        indoorTemp      : null,
        openContactCount: qualifiedContacts.size(),
        openContactNames: qualifiedContacts.collect { it.displayName }.join(", "),
        aqi             : houseAqi
    ]

    List candidates = []
    List<BigDecimal> indoorTemps = currentTemperatures(temperatureDevicesForZone(zone))
    if (!indoorTemps) {
        logDebug "Zone ${zone.name}: no indoor temperature — skipping window advice"
        return [candidates: candidates, meta: meta]
    }
    BigDecimal indoorTemp = averageTemperatureValues(indoorTemps)
    meta.indoorTemp = indoorTemp

    Map windowAction = evaluateWindowAction(zone, indoorTemp, indoorTemps, outdoorTemp, outdoorTrend,
            rainDetected, qualifiedContacts, contacts.isEmpty(), physicallyOpenContacts.isEmpty(), houseAqi)
    if (windowAction) { candidates << windowAction }

    return [candidates: candidates, meta: meta]
}

// Produces at most one warning-level window action per zone using indoor/outdoor
// proximity, setpoint breaches, and outdoor trend.
private Map evaluateWindowAction(Map zone, BigDecimal indoorTemp, List<BigDecimal> indoorTemps,
                                 BigDecimal outdoorTemp, Map outdoorTrend, boolean rainDetected,
                                 List qualifiedOpenContacts, boolean noContactsConfigured,
                                 boolean allContactsClosed, BigDecimal aqiVal) {
    if (outdoorTemp == null || (zone.thermostats ?: []).isEmpty()) { return null }

    BigDecimal coolestIndoor = indoorTemps.min()
    BigDecimal warmestIndoor = indoorTemps.max()
    boolean closeAllowed = noContactsConfigured || !qualifiedOpenContacts.isEmpty()
    boolean openAllowed = noContactsConfigured || allContactsClosed
    Map aqiThresholds = configuredAqiThresholds()
    boolean openingSafe = !rainDetected && (aqiVal == null || aqiVal < (aqiThresholds.warn as Integer))
    String trend = outdoorTrend?.trend as String
    List options = []

    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        BigDecimal coolSP = safeCurrentBD(t, "coolingSetpoint")
        BigDecimal heatSP = safeCurrentBD(t, "heatingSetpoint")

        if (coolSP != null && (mode in ["cool", "auto", "off"])) {
            BigDecimal offset = zone.coolingPreAlertOffset == null ? 3.0G : zone.coolingPreAlertOffset as BigDecimal
            boolean indoorNear = indoorTemp >= (coolSP - offset)
            boolean breached = indoorTemp >= coolSP
            boolean outdoorAboveSetpoint = outdoorTemp > coolSP
            boolean anticipateClose = trend == "heating up" && indoorNear &&
                    outdoorAboveSetpoint && outdoorTemp > indoorTemp
            boolean urgentClose = breached && outdoorAboveSetpoint && outdoorTemp >= indoorTemp
            if (closeAllowed && (anticipateClose || urgentClose)) {
                String actionText = trend == "heating up" ? "It's getting warmer outside" : "It's warmer outside"
                options << [severity: 2, family: "windowCloseWarm",
                            text: "${actionText} \u2014 close the windows",
                            distance: Math.abs(indoorTemp - coolSP) + Math.abs(outdoorTemp - coolSP)]
            }

            boolean comfortableOutside = heatSP == null || outdoorTemp > heatSP
            boolean coolingUseful = outdoorTemp < coolestIndoor
            boolean anticipateOpen = trend == "cooling down" && indoorNear &&
                    outdoorTemp <= (coolSP + offset) && coolingUseful && comfortableOutside
            boolean urgentOpen = breached && coolingUseful && comfortableOutside
            if ((mode in ["cool", "auto"]) && openAllowed && openingSafe && (anticipateOpen || urgentOpen)) {
                String actionText = trend == "cooling down" ? "It's getting cooler outside" : "It's cooler outside"
                options << [severity: 2, family: "windowOpenCool",
                            text: "${actionText} \u2014 open the windows",
                            distance: Math.abs(indoorTemp - coolSP) + Math.abs(outdoorTemp - coolSP)]
            }
        }

        if (heatSP != null && (mode in ["heat", "auto", "emergency heat", "off"])) {
            BigDecimal offset = zone.heatingPreAlertOffset == null ? 3.0G : zone.heatingPreAlertOffset as BigDecimal
            boolean indoorNear = indoorTemp <= (heatSP + offset)
            boolean breached = indoorTemp < heatSP
            boolean outdoorBelowSetpoint = outdoorTemp < heatSP
            boolean urgentClose = breached && outdoorBelowSetpoint && outdoorTemp < indoorTemp
            if (closeAllowed && urgentClose) {
                String actionText = trend == "cooling down" ? "It's getting colder outside" : "It's colder outside"
                options << [severity: 2, family: "windowCloseCold",
                            text: "${actionText} \u2014 close the windows",
                            distance: Math.abs(indoorTemp - heatSP) + Math.abs(outdoorTemp - heatSP)]
            }

            boolean comfortableOutside = coolSP == null || outdoorTemp < coolSP
            boolean warmingUseful = outdoorTemp > warmestIndoor
            boolean anticipateOpen = trend == "heating up" && indoorNear &&
                    outdoorTemp >= (heatSP - offset) && warmingUseful && comfortableOutside
            boolean urgentOpen = breached && warmingUseful && comfortableOutside
            if ((mode in ["heat", "auto", "emergency heat"]) && openAllowed && openingSafe && (anticipateOpen || urgentOpen)) {
                String actionText = trend == "heating up" ? "It's getting warmer outside" : "It's warmer outside"
                options << [severity: 2, family: "windowOpenWarm",
                            text: "${actionText} \u2014 open the windows",
                            distance: Math.abs(indoorTemp - heatSP) + Math.abs(outdoorTemp - heatSP)]
            }
        }
    }

    if (options.isEmpty()) { return null }
    Map best = options.sort { a, b ->
        int severityOrder = (b.severity as Integer) <=> (a.severity as Integer)
        severityOrder != 0 ? severityOrder : (a.distance as BigDecimal) <=> (b.distance as BigDecimal)
    }.first()
    String text = "${zone.name}: ${best.text}"
    return buildCandidate("zone-${zone.id}-window-action", best.severity as Integer,
            zone.name, best.family as String, text, zone.id as String)
}

private Map evaluateHouseAqi(BigDecimal aqiVal) {
    if (aqiVal == null) { return null }
    Map thresholds = configuredAqiThresholds()
    Integer warnThreshold = thresholds.warn as Integer
    Integer dangerThreshold = thresholds.danger as Integer
    if (aqiVal >= dangerThreshold) {
        String text = "Air quality is ${aqiCategory(dangerThreshold)} or worse (AQI ${dangerThreshold}+) \u2014 limit outdoor air"
        return buildCandidate("house-aqi", 3, "House", "aqiDanger", text)
    }
    if (aqiVal >= warnThreshold) {
        String text = "Air quality is ${aqiCategory(warnThreshold)} or worse (AQI ${warnThreshold}+) \u2014 limit outdoor air"
        return buildCandidate("house-aqi", 2, "House", "aqiWarn", text)
    }
    return null
}

private List evaluateHouseRain(boolean rainDetected, List zones) {
    if (!rainDetected) { return [] }
    boolean anyZoneOpen = zones.any { zone ->
        !qualifiedOpenContacts(zone.contactSensors ?: []).isEmpty()
    }
    if (!anyZoneOpen) { return [] }
    return [buildCandidate("house-rain-windows-open", 3, "House", "rainWindowsOpen",
            "Rain detected and windows are open \u2014 close windows now")]
}

// ── Per-child zone attribute push ─────────────────────────────────────────────

private void pushZoneAttributes(def aggChild, List zones, Map zoneResults) {
    // zoneStatuses JSON keyed by zone name → {severity, severityText, latestMessage, indoorTemp, openContactCount, aqi}
    Map statusMap = [:]
    zones.each { zone ->
        Map r = (zoneResults[zone.id as String] ?: [:]) as Map
        statusMap[zone.name as String] = [
            severity        : r.severity ?: 0,
            severityText    : r.severityText ?: "clear",
            latestMessage   : r.latestMessage ?: "All clear — no climate issues detected",
            indoorTemp      : r.indoorTemp,
            openContactCount: r.openContactCount ?: 0,
            aqi             : r.aqi
        ]
    }
    sendEventIfChanged(aggChild, "zoneStatuses", JsonOutput.toJson(statusMap))

    // Indexed flat attributes — always write all 10 slots; clear unused ones
    (1..10).each { i ->
        Map zone = zones.find { it.index == i }
        Map r    = zone ? ((zoneResults[zone.id as String] ?: [:]) as Map) : [:]
        sendEventIfChanged(aggChild, "zone${i}Name",     zone?.name       ?: "")
        sendEventIfChanged(aggChild, "zone${i}Severity", zone ? (r.severity ?: 0) : 0)
        sendEventIfChanged(aggChild, "zone${i}Message",  zone ? (r.latestMessage ?: "All clear — no climate issues detected") : "")
    }
}

// ── App-side commands (called from child device via parent?.method()) ─────────

def clearAllMessages() {
    logInfo "clearAllMessages()"
    state.activeMessages    = [:]
    state.externalMessages  = [:]
    state.presentedMessages = [:]
    runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
}

// External message API — callable from the child device, which exposes
// pushMessage(key, severity, text) and clearMessage(key) commands for Rule
// Machine, webCoRE pistons, and other automations. Sending null/empty text via
// pushMessage clears the entry for that key.
def pushExternalMessage(String key, def severity, String text) {
    if (!key) { log.warn "pushExternalMessage: key required"; return }
    if (text == null || (text instanceof String && text.trim().isEmpty())) {
        clearExternalMessage(key)
        return
    }
    Integer sev = ((severity ?: 1) as Integer)
    if (sev < 1) { sev = 1 }
    if (sev > 3) { sev = 3 }
    Map external = (state.externalMessages ?: [:]) as Map
    Map prev = external[key] as Map
    Long ts = (prev && prev.text == text && (prev.severity as Integer) == sev) ? (prev.ts as Long) : now()
    external[key] = [severity: sev, text: text, ts: ts]
    state.externalMessages = external
    logDebug "pushExternalMessage(${key}, sev=${sev}): ${text}"
    runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
}

def clearExternalMessage(String key) {
    if (!key) { log.warn "clearExternalMessage: key required"; return }
    Map external = (state.externalMessages ?: [:]) as Map
    if (external.remove(key) != null) {
        state.externalMessages = external
        logDebug "clearExternalMessage(${key})"
        runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
    }
}

// ── Notifications ─────────────────────────────────────────────────────────────

private void handleNotifications(List allMessages, List zones) {
    Map lastNotif = (state.lastNotificationAt ?: [:]) as Map
    Set activeIds = (allMessages ?: []).collect { it.id as String } as Set
    lastNotif = lastNotif.findAll { id, ignored -> activeIds.contains(id as String) }
    if (!allMessages) {
        state.lastNotificationAt = lastNotif
        return
    }

    Long nowMs = now()
    Integer minSeverity = (settings.announceSeverityThreshold ?: 2) as Integer
    List announceModes = deviceList(settings.announceModes)
    boolean speakAllowed = !announceModes || announceModes.contains(location.mode)

    allMessages.each { msg ->
        int sev = msg.severity as Integer
        if (sev < 1) { return }

        String msgId = msg.id as String
        def lastEntry = lastNotif[msgId]
        int  lastSev  = -1
        String lastText = null
        if (lastEntry instanceof Map) {
            lastSev = (lastEntry.sev ?: -1) as Integer
            lastText = lastEntry.text as String
        }

        // Announce a new action once; allow one escalation of that same action.
        // Record de-escalation silently so a later escalation can announce again.
        if (lastEntry != null && sev < lastSev) {
            lastNotif[msgId] = [ts: nowMs, sev: sev, text: msg.text as String]
            return
        }
        if (lastEntry != null && lastText == (msg.text as String) && sev == lastSev) { return }

        String msgZoneId = msg.zoneId as String
        List notifDevices = deviceList(settings.globalNotificationDevices)
        if (msgZoneId) {
            Map zone = zones.find { it.id == msgZoneId }
            if (zone) { notifDevices = notifDevices + deviceList(zone.notificationDevices) }
        }
        notifDevices.unique().each { d ->
            try { d.deviceNotification(msg.text as String) } catch (Exception e) { log.warn "notify error: ${e.message}" }
        }

        if (sev >= minSeverity && speakAllowed) {
            List speakers = deviceList(settings.globalSpeakers)
            if (msgZoneId) {
                Map zone = zones.find { it.id == msgZoneId }
                speakers = speakers + deviceList(zone?.speakers)
            }
            speakers.unique().each { spk ->
                try { spk.speak(msg.text as String) } catch (Exception e) { log.warn "speak error: ${e.message}" }
            }
        }

        lastNotif[msgId] = [ts: nowMs, sev: sev, text: msg.text as String]
    }
    state.lastNotificationAt = lastNotif
}

// ── Refresh command ───────────────────────────────────────────────────────────

def refresh() {
    logDebug "refresh()"
    evaluateAll()
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private List<Map> configuredZones() {
    Integer count = Math.max(1, Math.min((settings.zoneCount ?: 1) as Integer, 10))
    return (1..count).collect { i ->
        String name = settings["zone${i}Name"]?.trim()
        if (!name) { return null }
        [
            id                   : "zone${i}",
            index                : i,
            name                 : name,
            thermostats          : deviceList(settings["zone${i}Thermostats"]),
            indoorTempSensors    : deviceList(settings["zone${i}IndoorTempSensors"]),
            contactSensors       : deviceList(settings["zone${i}ContactSensors"]),
            speakers             : deviceList(settings["zone${i}Speakers"]),
            coolingPreAlertOffset: settings["zone${i}CoolingPreAlertOffset"] == null ? 3.0G : settings["zone${i}CoolingPreAlertOffset"] as BigDecimal,
            heatingPreAlertOffset: settings["zone${i}HeatingPreAlertOffset"] == null ? 3.0G : settings["zone${i}HeatingPreAlertOffset"] as BigDecimal,
            notificationDevices  : deviceList(settings["zone${i}NotificationDevices"])
        ]
    }.findAll { it != null }
}

private List deviceList(def selection) {
    if (!selection) { return [] }
    return selection instanceof Collection ? selection as List : [selection]
}

private List qualifiedOpenContacts(List contacts) {
    Map openedAt = (state.contactOpenedAt ?: [:]) as Map
    Long delayMs = configuredContactOpenDelaySeconds() * 1000L
    Long nowMs = now()
    return (contacts ?: []).findAll { contact ->
        if (contact.currentValue("contact") != "open") { return false }
        Long opened = openedAt[contact.id as String] as Long
        // An already-open contact with no recorded event predates this app run.
        return opened == null || (nowMs - opened) >= delayMs
    }
}

private Integer configuredContactOpenDelaySeconds() {
    return Math.max(0, settings.contactOpenDelaySeconds == null ? 60 : settings.contactOpenDelaySeconds as Integer)
}

private Integer configuredTrendWindowMinutes() {
    return Math.max(5, settings.trendWindowMinutes == null ? 30 : settings.trendWindowMinutes as Integer)
}

private Map configuredAqiThresholds() {
    Integer configuredWarn = settings.aqiWarnThreshold == null ? AQI_WARN_DEFAULT : settings.aqiWarnThreshold as Integer
    Integer configuredDanger = settings.aqiDangerThreshold == null ? AQI_DANGER_DEFAULT : settings.aqiDangerThreshold as Integer
    return [warn: Math.min(configuredWarn, configuredDanger), danger: Math.max(configuredWarn, configuredDanger)]
}

private List temperatureDevicesForZone(Map zone) {
    List sensors = deviceList(zone.indoorTempSensors)
    return sensors ?: deviceList(zone.thermostats)
}

private BigDecimal currentHouseAqi() {
    if (!settings.aqiDevice) { return null }
    try {
        def raw = settings.aqiDevice.currentValue("airQualityIndex")
        if (raw != null) { return raw as BigDecimal }
    } catch (Exception e) { log.warn "currentHouseAqi error: ${e.message}" }
    return null
}

private boolean checkRain() {
    if (!settings.weatherDevice) { return false }
    try {
        String attributeName = (settings.weatherAttribute ?: "weather") as String
        String val = settings.weatherDevice.currentValue(attributeName) as String
        if (!val) { return false }
        String lower = val.toLowerCase()
        String raw   = (settings.rainKeyword ?: "rain") as String
        List<String> includes = []
        List<String> excludes = []
        raw.split(",").each { String token ->
            String t = token?.trim()?.toLowerCase()
            if (!t) { return }
            if (t.startsWith("!")) {
                String ex = t.substring(1).trim()
                if (ex) { excludes << ex }
            } else {
                includes << t
            }
        }
        if (!includes) { includes << "rain" }
        if (excludes.any { lower.contains(it) }) { return false }
        return includes.any { lower.contains(it) }
    } catch (Exception e) {
        log.warn "checkRain error: ${e.message}"
        return false
    }
}

// Candidate message: no ts — ts assigned by resolveMessages at evaluation time.
// zoneId is null for house-level messages (rain); non-null for zone-scoped messages.
private Map buildCandidate(String id, int severity, String source, String family, String text, String zoneId = null) {
    return [id: id, severity: severity, severityText: severityText(severity), source: source, family: family, text: text, zoneId: zoneId]
}

private String severityText(int severity) {
    if (severity >= 3) { return "danger" }
    if (severity == 2) { return "warning" }
    if (severity == 1) { return "info" }
    return "clear"
}

private List<BigDecimal> currentTemperatures(List devices) {
    List<BigDecimal> values = []
    (devices ?: []).each { d ->
        try {
            def raw = d.currentValue("temperature")
            if (raw != null) { values << (raw as BigDecimal) }
        } catch (Exception e) { log.warn "currentTemperatures error: ${e.message}" }
    }
    return values
}

private BigDecimal averageTemperatureValues(List<BigDecimal> values) {
    BigDecimal sum = values.inject(0.0G) { acc, value -> acc + value }
    return (sum / values.size()).setScale(1, BigDecimal.ROUND_HALF_UP)
}

private BigDecimal safeCurrentTemp(device) {
    if (!device) { return null }
    try { return device.currentValue("temperature") as BigDecimal }
    catch (Exception e) { return null }
}

private BigDecimal safeCurrentBD(device, String attr) {
    if (!device) { return null }
    try {
        def v = device.currentValue(attr)
        return v != null ? (v as BigDecimal) : null
    } catch (Exception e) { return null }
}

// Compute "feels like" temperature from a local sensor reading + weather wind/humidity.
// Uses NWS wind chill (T ≤ 50°F and V > 3 mph) and NWS Rothfusz heat index
// (T ≥ 80°F and RH ≥ 40%). Returns null outside those regimes (or when the
// required wind/humidity input is missing) so the caller can fall back to the
// weather device's own apparentTemperature. Honours location.temperatureScale:
// inputs and outputs are in the user's preferred unit, with internal conversion
// to °F / mph for the formulas.
private BigDecimal computeFeelsLike(BigDecimal temp, BigDecimal windSpeed, BigDecimal humidity) {
    if (temp == null) { return null }
    boolean metric = (location?.temperatureScale == "C")
    BigDecimal tF   = metric ? (temp * 9.0G / 5.0G + 32.0G) : temp
    BigDecimal vMph = (windSpeed == null) ? null : (metric ? windSpeed * 0.621371G : windSpeed)

    BigDecimal feelsF = tF

    if (tF <= 50.0G && vMph != null && vMph > 3.0G) {
        double t  = tF.doubleValue()
        double v  = vMph.doubleValue()
        double vp = Math.pow(v, 0.16d)
        feelsF = new BigDecimal(35.74d + 0.6215d * t - 35.75d * vp + 0.4275d * t * vp)
    } else if (tF >= 80.0G && humidity != null && humidity >= 40.0G) {
        double t  = tF.doubleValue()
        double r  = humidity.doubleValue()
        double hi = (-42.379d + 2.04901523d * t + 10.14333127d * r - 0.22475541d * t * r
                     - 0.00683783d * t * t - 0.05481717d * r * r + 0.00122874d * t * t * r
                     + 0.00085282d * t * r * r - 0.00000199d * t * t * r * r)
        if (r < 13d && t >= 80d && t <= 112d) {
            hi -= ((13d - r) / 4d) * Math.sqrt((17d - Math.abs(t - 95d)) / 17d)
        } else if (r > 85d && t >= 80d && t <= 87d) {
            hi += ((r - 85d) / 10d) * ((87d - t) / 5d)
        }
        feelsF = new BigDecimal(hi)
    } else {
        // Outside both regimes — no meaningful adjustment; signal "no feels-like"
        // by returning null so the caller can fall back to the weather device value.
        return null
    }

    return metric ? (feelsF - 32.0G) * 5.0G / 9.0G : feelsF
}

// Change-only sendEvent: skip the call entirely if value is unchanged.
// Null guard prevents platform errors on NUMBER attributes when value is unavailable.
private void sendEventIfChanged(def d, String name, Object value, String unit = null) {
    if (value == null) { return }
    def current = d.currentValue(name)
    if (current?.toString() == value?.toString()) { return }
    Map evt = [name: name, value: value, descriptionText: "${d.displayName}: ${name} is ${value}${unit ? ' ' + unit : ''}"]
    if (unit) { evt.unit = unit }
    d.sendEvent(evt)
}

private void logDebug(String msg) { if (settings.logEnable) { log.debug msg } }
private void logInfo(String msg)  { if (settings.txtEnable != false) { log.info msg } }

def logsOff() {
    log.info "Debug logging disabled"
    app.updateSetting("logEnable", [value: false, type: "bool"])
}

// ── Idle ambient status ───────────────────────────────────────────────────────
// Called when no advisory messages are active. Builds an actionable single-line
// dashboard string surfacing only information that influences behaviour. Every
// segment is conditional and silently drops out when its gate isn't tripped.
//
// Order (left to right):
//   1. rain probability (1h ≥ 60% wins over 6h ≥ 30%)
//   2. today's high — only before 4pm local (afternoon = high is historical)
//   3. current feels-like — only when |feels − actual| ≥ 5° in the local scale
//   4. tomorrow's range/condition/rain — always when forecast available
//   5. UV ≥ 8
//   6. AQI > 50
//
// The first segment is capitalized for nicer dashboard tile reading.
//
// Examples:
//   "rain 50% · high 68° · feels 62°"
//   "Tomorrow ⛅ ↓55° ↑75°, rain 40%"
//   "high 88° · gusts 28 mph · UV 9 very high · AQI 78 (moderate)"
//
// Empty fallback: "No notable weather".

private String buildIdleStatus(BigDecimal outdoorTemp, boolean rainDetected, BigDecimal houseAqi) {
    List<String> parts = []
    int hourOfDay = currentLocalHour()
    boolean beforeAfternoonCutoff = hourOfDay < 16

    // 1. Rain probability — urgent 1h wins over 6h. Folded into the Today
    // segment below (mirroring the Tomorrow ", rain X%" styling) so today's
    // rain reads as part of today rather than dangling ahead of the word "Today".
    BigDecimal prob1h = safeCurrentBD(settings.weatherDevice, "precipitationProbabilityNextHour")
    BigDecimal prob6h = safeCurrentBD(settings.weatherDevice, "precipitationProbabilityNext6h")
    String rainText = null
    if (prob1h != null && prob1h.intValue() >= 60) {
        rainText = "rain ${prob1h.intValue()}% within 1h"
    } else if (prob6h != null && prob6h.intValue() >= 30) {
        rainText = "rain ${prob6h.intValue()}%"
    }

    // 2. Today's high — only before 4pm (after that, the high is historical).
    // Prefix with the current condition emoji (when available) to match the
    // Tomorrow segment styling: "Today ☀️ high 88°, rain 61% within 1h".
    boolean rainAppended = false
    if (beforeAfternoonCutoff) {
        BigDecimal tMax = safeCurrentBD(settings.weatherDevice, "temperatureMax")
        if (tMax != null) {
            int hi = tMax.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
            String condToday  = settings.weatherDevice?.currentValue((settings.weatherAttribute as String) ?: "weather") as String
            String emojiToday = conditionEmoji(condToday)
            StringBuilder seg = new StringBuilder("Today")
            if (emojiToday) { seg.append(" ").append(emojiToday) }
            seg.append(" high ").append(hi).append("\u00B0")
            if (rainText) {
                seg.append(", ").append(rainText)
                rainAppended = true
            }
            parts << seg.toString()
        }
    }

    // Fallback: no Today segment (after 4pm or missing high) — surface rain on its own.
    if (rainText && !rainAppended) {
        parts << rainText
    }

    // 3. Current feels-like — only when notably different from actual outdoor temp.
    // Prefers NWS formulas (cold+windy or hot+humid) from sensor temp + weather
    // wind/humidity; falls back to a mild-range delta against the weather device's
    // own apparentTemperature so wind cooling still surfaces between 50–80°F.
    if (outdoorTemp != null) {
        int curT = outdoorTemp.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
        BigDecimal wind     = safeCurrentBD(settings.weatherDevice, "windSpeed")
        BigDecimal humidity = safeCurrentBD(settings.outdoorTempDevice, "humidity")
        if (humidity == null) { humidity = safeCurrentBD(settings.weatherDevice, "humidity") }
        BigDecimal apparent = computeFeelsLike(outdoorTemp, wind, humidity)
        if (apparent == null) {
            BigDecimal wxTemp     = safeCurrentBD(settings.weatherDevice, "temperature")
            BigDecimal wxApparent = safeCurrentBD(settings.weatherDevice, "apparentTemperature")
            if (wxTemp != null && wxApparent != null) {
                apparent = outdoorTemp + (wxApparent - wxTemp)
            } else if (wxApparent != null) {
                apparent = wxApparent
            }
        }
        if (apparent != null) {
            int feels = apparent.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
            if (Math.abs(feels - curT) >= 5) {
                parts << "feels ${feels}°"
            }
        }
    }

    // 4. Tomorrow — always include when forecast data is available, so users
    // see what's coming regardless of time of day.
    BigDecimal tMaxT = safeCurrentBD(settings.weatherDevice, "temperatureMaxTomorrow")
    BigDecimal tMinT = safeCurrentBD(settings.weatherDevice, "temperatureMinTomorrow")
    String condT     = settings.weatherDevice?.currentValue("weatherTomorrow") as String
    BigDecimal rainT = safeCurrentBD(settings.weatherDevice, "precipitationProbabilityTomorrow")
    if (tMaxT != null && tMinT != null) {
        int hi = tMaxT.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
        int lo = tMinT.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()
        StringBuilder seg = new StringBuilder("Tomorrow")
        if (condT) {
            String emoji = conditionEmoji(condT)
            seg.append(" ").append(emoji ?: condT.toLowerCase())
        }
        seg.append(" \u2193").append(lo).append("\u00B0 \u2191").append(hi).append("\u00B0")
        if (rainT != null && rainT.intValue() >= 30) {
            seg.append(", rain ").append(rainT.intValue()).append("%")
        }
        parts << seg.toString()
    }

    // 5. UV index
    BigDecimal uv = safeCurrentBD(settings.weatherDevice, "ultravioletIndex")
    if (uv != null && uv >= 8.0G) {
        String uvWord = uv >= 11.0G ? "extreme" : "very high"
        parts << "UV ${uv.intValue()} ${uvWord}"
    }

    // 6. AQI (only when moderate or worse)
    if (houseAqi != null) {
        int aqi = houseAqi.toInteger()
        if (aqi > 50) {
            String cat = aqiCategory(aqi)
            parts << "AQI ${aqi}${cat ? ' (' + cat + ')' : ''}"
        }
    }

    if (parts.isEmpty()) { return "No notable weather" }
    String first = parts[0]
    if (first && first.length() > 0) {
        parts[0] = first.substring(0, 1).toUpperCase() + first.substring(1)
    }
    return parts.join(" \u00B7 ")  // middle dot separator: " · "
}

private int currentLocalHour() {
    try {
        Calendar cal = Calendar.getInstance(location?.timeZone ?: TimeZone.getDefault())
        return cal.get(Calendar.HOUR_OF_DAY)
    } catch (Exception e) {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
}

// Maps a free-form WMO/forecast condition string to a single weather emoji.
// Returns null when no confident match is found, so callers can skip the emoji.
private String conditionEmoji(String condition) {
    if (!condition) { return null }
    String c = condition.toLowerCase()
    if (c.contains("thunder") || c.contains("storm"))                                     { return "⛈️" }
    if (c.contains("snow") || c.contains("sleet") || c.contains("flurr") || c.contains("blizzard")) { return "🌨️" }
    if (c.contains("freezing") || c.contains("hail"))                                     { return "🥶" }
    if (c.contains("rain") || c.contains("shower") || c.contains("drizzle"))              { return "🌧️" }
    if (c.contains("fog") || c.contains("mist") || c.contains("haze") || c.contains("smoke")) { return "🌫️" }
    if (c.contains("partly") || c.contains("partial") || c.contains("mostly cloud") || c.contains("mostly sunny")) { return "⛅" }
    if (c.contains("cloud") || c.contains("overcast"))                                    { return "☁️" }
    if (c.contains("clear") || c.contains("sunny") || c.contains("fair") || c.contains("bright")) { return "☀️" }
    return null
}

private String aqiCategory(int aqi) {
    if (aqi <= 50)  { return "good" }
    if (aqi <= 100) { return "moderate" }
    if (aqi <= 150) { return "unhealthy for sensitive groups" }
    if (aqi <= 200) { return "unhealthy" }
    if (aqi <= 300) { return "very unhealthy" }
    return "hazardous"
}
