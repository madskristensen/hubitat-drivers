/**
 * Climate Advisor
 * Namespace: mads
 * Author:    Mads Kristensen
 * Version:   0.4.1
 *
 * Changelog:
 *   0.4.1 — 2026-05-24 — Fix outdoor trend stuck at "unknown" for sparse sensors: retain last N samples regardless of age (was time-only, evicting all history on first event after a long silence) and loosen staleness guard from 2× to 4× the trend window
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

@Field static final String  APP_VERSION        = "0.4.1"
@Field static final String  CHILD_DRIVER       = "Climate Advisor Device"
@Field static final String  CHILD_NS           = "mads"
@Field static final Integer MAX_AGG_MSG        = 20
@Field static final Integer MAX_ZONE_MSG       = 10
@Field static final Integer AQI_WARN_DEFAULT   = 51     // EPA Moderate boundary
@Field static final Integer AQI_DANGER_DEFAULT = 101    // EPA Unhealthy for Sensitive Groups
@Field static final Integer DEBOUNCE_SECONDS   = 1
@Field static final Integer SEED_DELAY_SECONDS = 5
@Field static final Integer MIN_TREND_SAMPLES  = 10     // retain at least this many samples regardless of age (sparse outdoor sensors)
@Field static final Integer STALENESS_MULTIPLE = 4      // newest sample must be within N × trendWindow to be considered fresh

definition(
    name:           "Climate Advisor",
    namespace:      "mads",
    author:         "Mads Kristensen",
    description:    "Per-zone predictive close-windows alerts, indoor/outdoor trend detection, and aggregate house status for SharpTools + Rule Machine.",
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
    dynamicPage(name: "globalPage", title: "Global Settings") {
        section("Outdoor Conditions") {
            input "outdoorTempDevice", "capability.temperatureMeasurement",
                title: "Outdoor temperature device", required: true
            input "weatherDevice", "capability.sensor",
                title: "Weather / forecast device (optional)", required: false
            input "weatherAttribute", "string",
                title: "Weather condition attribute", defaultValue: "weather", required: false
            input "rainKeyword", "string",
                title: "Rain keyword in weather attribute", defaultValue: "rain", required: false
        }
        section("Air Quality") {
            input "aqiDevice", "capability.airQuality",
                title: "Air quality sensor (optional — house-wide)", required: false
        }
        section("AQI Thresholds") {
            input "aqiWarnThreshold", "number",
                title: "AQI warning threshold (default 51 — EPA Moderate)", defaultValue: 51, required: true
            input "aqiDangerThreshold", "number",
                title: "AQI danger threshold (default 101 — EPA Unhealthy for Sensitive Groups)", defaultValue: 101, required: true
        }
        section("Trend Detection") {
            input "trendWindowMinutes", "number",
                title: "Outdoor trend window (minutes)", defaultValue: 30, required: true
            input "outdoorTrendRisingThreshold10min", "decimal",
                title: "Rising threshold (°F per 10 min)", defaultValue: 0.2, required: true
            input "outdoorTrendFallingThreshold10min", "decimal",
                title: "Falling threshold (°F per 10 min)", defaultValue: -0.2, required: true
            input "indoorTrendEnabled", "bool",
                title: "Track indoor trend per zone", defaultValue: true, required: true
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
            input "throttleMinutes", "number",
                title: "Minimum minutes between repeated notifications", defaultValue: 60, required: true
            input "announceSeverityThreshold", "number",
                title: "Minimum severity for speaker announcements (1=info, 2=warning/pre-alerts, 3=danger/breaches)",
                range: "1..3", defaultValue: 2, required: true
        }
    }
}

def zonesPage() {
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
                    title: "Cooling pre-alert offset (°F)", defaultValue: 3.0, required: true
                input "zone${i}HeatingPreAlertOffset", "decimal",
                    title: "Heating pre-alert offset (°F)", defaultValue: 3.0, required: true
                input "zone${i}NotificationDevices", "capability.notification",
                    title: "Notification devices for this zone (optional)", multiple: true, required: false
            }
        }
    }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

def installed() {
    state.outdoorSamples     = []
    state.indoorSamples      = [:]
    state.lastNotificationAt = [:]
    state.activeMessages     = [:]
    state.externalMessages   = [:]
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
        // Indoor temp sensors get a dedicated handler that appends trend samples
        zone.indoorTempSensors?.each { sensor ->
            subscribe(sensor, "temperature", indoorTempHandler)
        }
        zone.thermostats?.each { t ->
            subscribe(t, "thermostatMode",    debounceHandler)
            subscribe(t, "coolingSetpoint",   debounceHandler)
            subscribe(t, "heatingSetpoint",   debounceHandler)
        }
        zone.contactSensors?.each { c ->
            subscribe(c, "contact", debounceHandler)
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

// Indoor temp handler: append sample to per-zone trend buffer, then debounce eval.
// Appends only on actual temperature events (not every evaluation pass) for slope accuracy.
def indoorTempHandler(evt) {
    try {
        BigDecimal temp = evt.value as BigDecimal
        Long ts = now()
        String evtDevId = evt.device?.id as String
        configuredZones().each { zone ->
            if (zone.indoorTempSensors?.any { (it.id as String) == evtDevId }) {
                appendIndoorSample(zone.id as String, temp, ts)
            }
        }
    } catch (Exception e) {
        log.warn "indoorTempHandler sample error: ${e.message}"
    }
    runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
}

// All other handlers: schedule the debounced evaluation
def debounceHandler(evt) {
    runIn(DEBOUNCE_SECONDS, "evaluateAll", [overwrite: true])
}

// ── Trend computation (lazy — only called from evaluateAll) ───────────────────

Map computeTrend(List samples, Integer windowMinutes, BigDecimal risingThreshold, BigDecimal fallingThreshold) {
    Long nowTs = now()
    List window = (samples ?: []).sort { it.now }

    if (window.size() < 2) { return [trend: "unknown", slope10min: null] }

    def oldest = window.first()
    def newest = window.last()

    // Staleness guard: if the most recent sample is older than STALENESS_MULTIPLE × the
    // trend window, the sensor has gone quiet and any computed slope is unreliable.
    // 4× (default 2h for a 30-min window) tolerates Philio-class outdoor sensors that
    // only report on threshold crossings.
    BigDecimal ageMinutes = (nowTs - newest.now) / 60000G
    if (ageMinutes > (windowMinutes * (STALENESS_MULTIPLE as BigDecimal))) { return [trend: "unknown", slope10min: null] }

    BigDecimal spanMinutes = (newest.now - oldest.now) / 60000G
    if (spanMinutes < 5G) { return [trend: "unknown", slope10min: null] }

    BigDecimal slopePerMinute = ((newest.t as BigDecimal) - (oldest.t as BigDecimal)) / spanMinutes
    BigDecimal slope10min = slopePerMinute * 10G

    String trend = slope10min > risingThreshold ? "heating up" :
                   slope10min < fallingThreshold ? "cooling down" :
                   "steady"
    return [trend: trend, slope10min: slope10min]
}

private Map outdoorTrendResult() {
    BigDecimal rising  = (settings.outdoorTrendRisingThreshold10min  ?: 0.2)  as BigDecimal
    BigDecimal falling = (settings.outdoorTrendFallingThreshold10min ?: -0.2) as BigDecimal
    return computeTrend((state.outdoorSamples ?: []) as List, (settings.trendWindowMinutes ?: 30) as Integer, rising, falling)
}

private List pruneSamples(List samples, Long nowTs) {
    Integer windowMin = (settings.trendWindowMinutes ?: 30) as Integer
    // Retain by time AND by count. Time-only pruning fails for sparse outdoor sensors
    // (e.g. Philio PAT02) that can go 2+ hours between temperature events: the first
    // event after the silence would evict all prior history, leaving a single sample
    // and forcing the trend back to "unknown" for another full window.
    Long retainMin = Math.max(windowMin * 3L + 5L, 90L)
    Long cutoff = nowTs - retainMin * 60 * 1000L
    List sorted = (samples ?: []).sort { it.now }
    List byTime = sorted.findAll { it.now >= cutoff }
    if (sorted.size() <= MIN_TREND_SAMPLES) { return sorted }
    if (byTime.size() >= MIN_TREND_SAMPLES) { return byTime }
    // Fall back to the most recent MIN_TREND_SAMPLES samples regardless of age.
    return sorted[-MIN_TREND_SAMPLES..-1]
}

private void appendIndoorSample(String zoneId, BigDecimal avgTemp, Long ts = null) {
    Long sampleTs = ts ?: now()
    Map indoorSamples = (state.indoorSamples ?: [:]) as Map
    List zoneSamples  = (indoorSamples[zoneId] ?: []) as List
    zoneSamples = zoneSamples + [[now: sampleTs, t: avgTemp]]
    indoorSamples[zoneId] = pruneSamples(zoneSamples, sampleTs)
    state.indoorSamples = indoorSamples
}

private Map indoorTrendResult(String zoneId) {
    // Indoor temperatures change more slowly; use a gentler threshold (0.1°F/10min default)
    BigDecimal rising  = 0.1G
    BigDecimal falling = -0.1G
    Map indoorSamples = (state.indoorSamples ?: [:]) as Map
    return computeTrend((indoorSamples[zoneId] ?: []) as List, (settings.trendWindowMinutes ?: 30) as Integer, rising, falling)
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

        // House-level rain+contact alert
        List rainCandidates = evaluateHouseRain(rainDetected, zones)
        List rainResolved   = resolveMessages(rainCandidates, prevActive, newActive, nowMs)
        allMessages.addAll(rainResolved)

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

        // Sort: severity desc, ts desc; cap
        allMessages = allMessages.sort { a, b ->
            int sc = (b.severity as Integer) <=> (a.severity as Integer)
            sc != 0 ? sc : (b.ts as Long) <=> (a.ts as Long)
        }.take(MAX_AGG_MSG)

        int    aggSeverity     = allMessages ? allMessages.collect { it.severity as Integer }.max() : 0
        String aggSeverityText = severityText(aggSeverity)
        String aggLatest       = allMessages ? allMessages[0].text : buildIdleStatus(outdoorTemp, rainDetected, houseAqi)
        int    alertCount      = allMessages.count { (it.severity as Integer) >= 1 }
        int    totalOpenContacts = zoneResults.values().sum { (it.openContactCount ?: 0) as Integer } ?: 0
        String houseStatus     = alertCount > 0 ? "${alertCount} active alert${alertCount > 1 ? 's' : ''}" : "House comfortable"
        String aggJson         = JsonOutput.toJson(allMessages)

        def aggChild = lookupChild()
        if (aggChild) {
            // Reset acknowledged flag when new or escalating alerts arrive
            boolean hasNewOrEscalated = newActive.any { id, entry ->
                !prevActive.containsKey(id) || ((prevActive[id]?.severity ?: 0) as Integer) < (entry.severity as Integer)
            }
            if (hasNewOrEscalated) { sendEventIfChanged(aggChild, "acknowledged", "false") }

            sendEventIfChanged(aggChild, "severity",              aggSeverity)
            sendEventIfChanged(aggChild, "severityText",          aggSeverityText)
            sendEventIfChanged(aggChild, "latestMessage",         aggLatest)
            sendEventIfChanged(aggChild, "messages",              aggJson)
            sendEventIfChanged(aggChild, "houseStatus",           houseStatus)
            sendEventIfChanged(aggChild, "contact",               aggSeverity >= 1 ? "open" : "closed")
            sendEventIfChanged(aggChild, "outdoorTrend",          outdoorTrend.trend ?: "unknown")
            sendEventIfChanged(aggChild, "outdoorTempSlope10min", outdoorTrend.slope10min)
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

// Reconcile candidate messages against previous-pass cache.
// Candidates have no ts field. If text unchanged from cache → reuse cached entry (stable ts).
// If new or text changed → assign nowMs. Populates newActive map.
private List resolveMessages(List candidates, Map prevActive, Map newActive, Long nowMs) {
    List resolved = []
    (candidates ?: []).each { candidate ->
        if (candidate == null) { return }
        String id = candidate.id as String
        Map prev  = prevActive[id] as Map
        Map entry
        if (prev && prev.text == candidate.text) {
            entry = prev  // unchanged — reuse with original ts
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
    Map emptyMeta = [indoorTemp: null, openContactCount: 0, openContactNames: "", aqi: null]

    BigDecimal indoorTemp = averageTemps(zone.indoorTempSensors)
    if (indoorTemp == null) {
        logDebug "Zone ${zone.name}: no indoor temp — skipping"
        return [candidates: [], meta: emptyMeta]
    }

    List contacts = zone.contactSensors ?: []
    List openContacts = contacts.findAll { it.currentValue("contact") == "open" }
    boolean anyOpen = !openContacts.isEmpty()
    boolean noContactsConfigured = contacts.isEmpty()
    boolean windowGatePasses = noContactsConfigured || anyOpen
    String noSensorNote = noContactsConfigured ? " (no window sensors configured)" : ""

    // House-wide AQI passed in from evaluateAll (one read per cycle)
    BigDecimal aqiVal = houseAqi

    List candidates = []
    def m
    m = evaluateCoolingPreAlert(zone, indoorTemp, outdoorTemp, outdoorTrend, windowGatePasses, noSensorNote)
    if (m) { candidates << m }

    m = evaluateHeatingPreAlert(zone, indoorTemp, outdoorTemp, outdoorTrend, windowGatePasses, noSensorNote)
    if (m) { candidates << m }

    m = evaluateCoolBreach(zone, indoorTemp, outdoorTemp)
    if (m) { candidates << m }

    m = evaluateHeatBreach(zone, indoorTemp, outdoorTemp)
    if (m) { candidates << m }

    m = evaluateAqi(zone, aqiVal)
    if (m) { candidates << m }

    m = evaluateComfortOpen(zone, indoorTemp, outdoorTemp, outdoorTrend, rainDetected, openContacts, aqiVal)
    if (m) { candidates << m }

    m = evaluateFreeCooling(zone, indoorTemp, outdoorTemp, outdoorTrend, openContacts, !noContactsConfigured, aqiVal, rainDetected)
    if (m) { candidates << m }

    return [
        candidates: candidates,
        meta: [
            indoorTemp      : indoorTemp,
            openContactCount: openContacts.size(),
            openContactNames: openContacts.collect { it.displayName }.join(", "),
            aqi             : aqiVal
        ]
    ]
}

private Map evaluateCoolingPreAlert(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp,
                                     Map outdoorTrend, boolean windowGatePasses, String noSensorNote) {
    if (!windowGatePasses)              { return null }
    if (outdoorTrend.trend == "unknown") { return null }
    if (outdoorTrend.trend != "heating up")  { return null }
    if (outdoorTemp == null || outdoorTemp <= indoorTemp) { return null }

    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        if (!(mode in ["cool", "auto"])) { return }
        BigDecimal coolSP = safeCurrentBD(t, "coolingSetpoint")
        if (coolSP == null) { return }
        BigDecimal offset = zone.coolingPreAlertOffset ?: 3.0G
        if (indoorTemp >= (coolSP - offset)) { qualifying << [setpoint: coolSP] }
    }
    if (qualifying.isEmpty()) { return null }

    Map best = qualifying.min { Math.abs((it.setpoint as BigDecimal) - indoorTemp) }
    String text = "${zone.name} ${indoorTemp}°F approaching ${best.setpoint}°F cool setpoint, outside ${outdoorTemp}°F heating up \u2014 close windows${noSensorNote}"
    return buildCandidate("zone-${zone.id}-cooling-prealert", 2, zone.name, "coolingPreAlert", text, zone.id as String)
}

private Map evaluateHeatingPreAlert(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp,
                                     Map outdoorTrend, boolean windowGatePasses, String noSensorNote) {
    if (!windowGatePasses)               { return null }
    if (outdoorTrend.trend == "unknown")  { return null }
    if (outdoorTrend.trend != "cooling down")  { return null }
    if (outdoorTemp == null || outdoorTemp >= indoorTemp) { return null }

    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        if (!(mode in ["heat", "auto", "emergency heat"])) { return }
        BigDecimal heatSP = safeCurrentBD(t, "heatingSetpoint")
        if (heatSP == null) { return }
        BigDecimal offset = zone.heatingPreAlertOffset ?: 3.0G
        if (indoorTemp <= (heatSP + offset)) { qualifying << [setpoint: heatSP] }
    }
    if (qualifying.isEmpty()) { return null }

    Map best = qualifying.min { Math.abs((it.setpoint as BigDecimal) - indoorTemp) }
    String text = "${zone.name} ${indoorTemp}°F approaching ${best.setpoint}°F heat setpoint, outside ${outdoorTemp}°F cooling down \u2014 close windows${noSensorNote}"
    return buildCandidate("zone-${zone.id}-heating-prealert", 2, zone.name, "heatingPreAlert", text, zone.id as String)
}

private Map evaluateCoolBreach(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp) {
    if ((zone.thermostats ?: []).isEmpty()) { return null }
    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        if (!(mode in ["cool", "auto"])) { return }
        BigDecimal coolSP = safeCurrentBD(t, "coolingSetpoint")
        if (coolSP == null) { return }
        if (indoorTemp >= coolSP && outdoorTemp != null && outdoorTemp > indoorTemp) {
            qualifying << [setpoint: coolSP]
        }
    }
    if (qualifying.isEmpty()) { return null }
    Map best = qualifying.min { Math.abs((it.setpoint as BigDecimal) - indoorTemp) }
    String text = "${zone.name} ${indoorTemp}°F has breached ${best.setpoint}°F cool setpoint \u2014 close windows"
    return buildCandidate("zone-${zone.id}-setpoint-breach-cool", 3, zone.name, "setpointBreachCool", text, zone.id as String)
}

private Map evaluateHeatBreach(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp) {
    if ((zone.thermostats ?: []).isEmpty()) { return null }
    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        if (!(mode in ["heat", "auto", "emergency heat"])) { return }
        BigDecimal heatSP = safeCurrentBD(t, "heatingSetpoint")
        if (heatSP == null) { return }
        if (indoorTemp <= heatSP && outdoorTemp != null && outdoorTemp < indoorTemp) {
            qualifying << [setpoint: heatSP]
        }
    }
    if (qualifying.isEmpty()) { return null }
    Map best = qualifying.min { Math.abs((it.setpoint as BigDecimal) - indoorTemp) }
    String text = "${zone.name} ${indoorTemp}°F has breached ${best.setpoint}°F heat setpoint \u2014 close windows"
    return buildCandidate("zone-${zone.id}-setpoint-breach-heat", 3, zone.name, "setpointBreachHeat", text, zone.id as String)
}

private Map evaluateAqi(Map zone, BigDecimal aqiVal) {
    if (aqiVal == null) { return null }
    Integer dangerThreshold = (settings.aqiDangerThreshold ?: AQI_DANGER_DEFAULT) as Integer
    Integer warnThreshold   = (settings.aqiWarnThreshold   ?: AQI_WARN_DEFAULT)   as Integer
    if (aqiVal > dangerThreshold) {
        String text = "${zone.name} air quality is hazardous (AQI ${aqiVal.toInteger()} > ${dangerThreshold}) \u2014 consider closing windows"
        return buildCandidate("zone-${zone.id}-aqi-danger", 3, zone.name, "aqiDanger", text, zone.id as String)
    }
    if (aqiVal > warnThreshold) {
        String text = "${zone.name} air quality is moderate (AQI ${aqiVal.toInteger()} > ${warnThreshold}) \u2014 consider closing windows"
        return buildCandidate("zone-${zone.id}-aqi-warn", 2, zone.name, "aqiWarn", text, zone.id as String)
    }
    return null
}

// Open-windows comfort suggestion: outdoor is comfortable relative to setpoints,
// contacts are closed, not raining, AQI ok, outdoor not rising into heat range.
// Severity = 1 (info) — the only code path that produces the "info" ENUM value.
private Map evaluateComfortOpen(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp,
                                 Map outdoorTrend, boolean rainDetected,
                                 List openContacts, BigDecimal aqiVal) {
    if (outdoorTemp == null)          { return null }
    if (rainDetected)                 { return null }
    if (!openContacts.isEmpty())      { return null }  // windows already open — no need to suggest
    if ((zone.contactSensors ?: []).isEmpty()) { return null }  // no contacts to open

    // Suppress if AQI is at warning level or above
    Integer warnThreshold = (settings.aqiWarnThreshold ?: AQI_WARN_DEFAULT) as Integer
    if (aqiVal != null && aqiVal >= warnThreshold) { return null }

    // Need thermostats with setpoints to define the comfort band
    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        if (mode in ["off", "fan only"]) { return }
        BigDecimal coolSP = safeCurrentBD(t, "coolingSetpoint")
        BigDecimal heatSP = safeCurrentBD(t, "heatingSetpoint")
        if (coolSP == null || heatSP == null) { return }
        BigDecimal comfortBuffer = 2.0G
        BigDecimal lowerBound = heatSP + comfortBuffer
        BigDecimal upperBound = coolSP - comfortBuffer
        if (upperBound <= lowerBound) { return }  // setpoints too close for a comfort band
        if (outdoorTemp >= lowerBound && outdoorTemp <= upperBound) {
            qualifying << [coolSP: coolSP, heatSP: heatSP]
        }
    }
    if (qualifying.isEmpty()) { return null }

    // Don't suggest if outdoor temp is heating up toward the cooling band
    if (outdoorTrend.trend == "heating up") { return null }

    String text = "${zone.name} outdoor ${outdoorTemp}°F is comfortable \u2014 consider opening windows for fresh air"
    return buildCandidate("zone-${zone.id}-comfort-open", 1, zone.name, "comfortOpen", text, zone.id as String)
}

// Free cooling opportunity: outdoor cooler than indoor and indoor at/near cooling setpoint.
// Ventilation displaces AC run without spending comfort — the gap evaluateComfortOpen misses
// because outdoor is below the comfort band but still cooler than the overheated interior.
// Severity = 1 (info) — positive opportunity, not a warning.
private Map evaluateFreeCooling(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp,
                                 Map outdoorTrend, List openContacts, boolean contactsConfigured,
                                 BigDecimal aqiVal, boolean rainDetected) {
    if (outdoorTemp == null)            { return null }
    if (rainDetected)                   { return null }
    if (!contactsConfigured)            { return null }  // no contacts to open
    if (!openContacts.isEmpty())        { return null }  // windows already open — suggestion redundant
    if (outdoorTemp >= indoorTemp)      { return null }  // outdoor not cooler than indoor
    if (outdoorTrend.trend == "heating up") { return null }  // temp trending up — opportunity closing

    Integer warnThreshold = (settings.aqiWarnThreshold ?: AQI_WARN_DEFAULT) as Integer
    if (aqiVal != null && aqiVal >= warnThreshold) { return null }

    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        if (!(mode in ["cool", "auto"])) { return }
        BigDecimal coolSP = safeCurrentBD(t, "coolingSetpoint")
        if (coolSP == null) { return }
        BigDecimal offset = zone.coolingPreAlertOffset ?: 3.0G
        if (indoorTemp >= (coolSP - offset)) { qualifying << [coolSP: coolSP] }
    }
    if (qualifying.isEmpty()) { return null }

    Map best = qualifying.min { Math.abs((it.coolSP as BigDecimal) - indoorTemp) }
    BigDecimal delta = (indoorTemp - outdoorTemp).setScale(1, java.math.RoundingMode.HALF_UP)
    String text = "${zone.name} outdoor ${outdoorTemp}°F is ${delta}°F cooler than indoor ${indoorTemp}°F \u2014 consider opening windows for free cooling (cool setpoint ${best.coolSP}°F)"
    return buildCandidate("zone-${zone.id}-free-cooling", 1, zone.name, "freeCooling", text, zone.id as String)
}

private List evaluateHouseRain(boolean rainDetected, List zones) {
    if (!rainDetected) { return [] }
    boolean anyZoneOpen = zones.any { zone ->
        (zone.contactSensors ?: []).any { it.currentValue("contact") == "open" }
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
    state.activeMessages   = [:]
    state.externalMessages = [:]
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
    if (sev < 0) { sev = 0 }
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
    if (!allMessages) { return }
    Map lastNotif = (state.lastNotificationAt ?: [:]) as Map
    Long throttleMs = ((settings.throttleMinutes ?: 60) as Integer) * 60 * 1000L
    Long nowMs = now()
    Integer minSeverity = (settings.announceSeverityThreshold ?: 2) as Integer

    allMessages.each { msg ->
        int sev = msg.severity as Integer
        if (sev < 1) { return }

        String msgId = msg.id as String
        def lastEntry = lastNotif[msgId]
        Long lastTs   = 0L
        int  lastSev  = -1
        if (lastEntry instanceof Map) {
            lastTs  = (lastEntry.ts  ?: 0L) as Long
            lastSev = (lastEntry.sev ?: -1) as Integer
        } else if (lastEntry != null) {
            lastTs = lastEntry as Long
        }

        // Skip if within throttle window AND severity hasn't risen
        if ((nowMs - lastTs) < throttleMs && sev <= lastSev) { return }

        String msgZoneId = msg.zoneId as String
        List notifDevices = (settings.globalNotificationDevices ?: []) as List
        if (msgZoneId) {
            Map zone = zones.find { it.id == msgZoneId }
            if (zone) { notifDevices = notifDevices + (zone.notificationDevices ?: []) }
        }
        notifDevices.unique().each { d ->
            try { d.deviceNotification(msg.text as String) } catch (Exception e) { log.warn "notify error: ${e.message}" }
        }

        if (sev >= minSeverity) {
            // Global speakers
            (settings.globalSpeakers ?: []).each { spk ->
                try { spk.speak(msg.text as String) } catch (Exception e) { log.warn "speak error: ${e.message}" }
            }
            // Zone speakers
            if (msgZoneId) {
                Map zone = zones.find { it.id == msgZoneId }
                (zone?.speakers ?: []).each { spk ->
                    try { spk.speak(msg.text as String) } catch (Exception e) { log.warn "speak error: ${e.message}" }
                }
            }
        }

        lastNotif[msgId] = [ts: nowMs, sev: sev]
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
            thermostats          : settings["zone${i}Thermostats"]            ?: [],
            indoorTempSensors    : settings["zone${i}IndoorTempSensors"]      ?: [],
            contactSensors       : settings["zone${i}ContactSensors"]         ?: [],
            speakers             : settings["zone${i}Speakers"]               ?: [],
            coolingPreAlertOffset: (settings["zone${i}CoolingPreAlertOffset"] ?: 3.0) as BigDecimal,
            heatingPreAlertOffset: (settings["zone${i}HeatingPreAlertOffset"] ?: 3.0) as BigDecimal,
            notificationDevices  : settings["zone${i}NotificationDevices"]    ?: []
        ]
    }.findAll { it != null }
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
    try {
        String val     = settings.weatherDevice.currentValue(settings.weatherAttribute as String) as String
        String keyword = (settings.rainKeyword ?: "rain") as String
        return val?.toLowerCase()?.contains(keyword.toLowerCase()) ?: false
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

private BigDecimal averageTemps(List devices) {
    if (!devices) { return null }
    List<BigDecimal> vals = []
    devices.each { d ->
        try {
            def raw = d.currentValue("temperature")
            if (raw != null) { vals << (raw as BigDecimal) }
        } catch (Exception e) { log.warn "averageTemps error: ${e.message}" }
    }
    if (!vals) { return null }
    BigDecimal sum = vals.inject(0.0G) { acc, v -> acc + v }
    return (sum / vals.size()).setScale(1, BigDecimal.ROUND_HALF_UP)
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
// Called when no advisory messages are active.
// Builds "☀️ Sunny · 72°F · AQI 38 (good) · House comfortable" for dashboard tiles.
// Every segment is optional — gracefully omitted when data is unavailable.

private String buildIdleStatus(BigDecimal outdoorTemp, boolean rainDetected, BigDecimal houseAqi) {
    List<String> parts = []

    // Weather emoji + condition word
    String conditionRaw = null
    if (settings.weatherDevice) {
        try {
            String attr = (settings.weatherAttribute ?: "weather") as String
            conditionRaw = settings.weatherDevice.currentValue(attr) as String
        } catch (Exception e) { /* skip — device not ready */ }
    }

    if (conditionRaw || rainDetected) {
        boolean isNight = isNighttime()
        String emoji
        String word
        if (rainDetected) {
            emoji = "🌧️"; word = "Rain"
        } else {
            String cond = conditionRaw.toLowerCase()
            if      (cond.contains("snow") || cond.contains("sleet") || cond.contains("flurr"))                { emoji = "🌨️"; word = "Snow"          }
            else if (cond.contains("thunder") || cond.contains("storm"))                                        { emoji = "⛈️";  word = "Stormy"        }
            else if (cond.contains("fog") || cond.contains("mist") || cond.contains("haze"))                   { emoji = "🌫️"; word = "Foggy"          }
            else if (cond.contains("partly") || cond.contains("partial") ||
                     cond.contains("mostly cloud") || cond.contains("mostly sunny"))                            { emoji = "⛅"; word = "Partly cloudy"   }
            else if (cond.contains("cloud") || cond.contains("overcast"))                                       { emoji = "☁️";  word = "Cloudy"         }
            else if (cond.contains("clear") || cond.contains("sunny") ||
                     cond.contains("fair") || cond.contains("bright"))                                          { emoji = isNight ? "🌙" : "☀️"
                                                                                                                  word = isNight ? "Clear"  : "Sunny"   }
            else {
                // Unknown condition: show raw string with a generic day/night emoji
                emoji = isNight ? "🌙" : "🌤️"
                word  = conditionRaw.capitalize()
            }
        }
        parts << "${emoji} ${word}"
    }

    // Outdoor temperature — integer display ("72°F")
    if (outdoorTemp != null) {
        parts << "${outdoorTemp.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()}°F"
    }

    // AQI value + EPA category (omitted entirely when no AQI device configured)
    if (houseAqi != null) {
        int aqi = houseAqi.toInteger()
        String cat = aqiCategory(aqi)
        parts << "AQI ${aqi}${cat ? ' (' + cat + ')' : ''}"
    }

    parts << "House comfortable"
    return parts.join(" \u00B7 ")  // middle dot separator: " · "
}

private boolean isNighttime() {
    try {
        Long rise = location.sunrise?.time
        Long set  = location.sunset?.time
        Long nowT = now()
        if (rise != null && set != null) { return nowT < rise || nowT > set }
    } catch (Exception e) { /* graceful skip if location data unavailable */ }
    return false
}

private String aqiCategory(int aqi) {
    if (aqi <= 50)  { return "good" }
    if (aqi <= 100) { return "moderate" }
    if (aqi <= 150) { return "sensitive groups" }
    if (aqi <= 200) { return "unhealthy" }
    if (aqi <= 300) { return "very unhealthy" }
    return "hazardous"
}
