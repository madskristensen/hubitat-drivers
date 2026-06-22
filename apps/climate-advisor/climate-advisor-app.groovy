/**
 * Climate Advisor
 * Namespace: mads
 * Author:    Mads Kristensen
 * Version:   0.4.18
 *
 * Changelog:
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

@Field static final String  APP_VERSION        = "0.4.18"
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
            input "announceModes", "mode",
                title: "Location modes in which speaker announcements are enabled (leave empty to allow all modes)",
                multiple: true, required: false
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

    m = evaluateCoolingWindowOpen(zone, indoorTemp, outdoorTemp, windowGatePasses, noSensorNote)
    if (m) { candidates << m }

    m = evaluateHeatBreach(zone, indoorTemp, outdoorTemp)
    if (m) { candidates << m }

    m = evaluateHeatingWindowOpen(zone, indoorTemp, outdoorTemp, windowGatePasses, noSensorNote)
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

// Windows-open / AC-off close-windows warning. Fires when the thermostat is NOT
// actively cooling (e.g. turned off because windows are open) yet the room is
// approaching the cool setpoint and it's hotter outside than that setpoint — the
// active-cooling pre-alert/breach evaluators skip this because they require
// thermostatMode cool/auto. Independent of trend so it warns whenever the
// conditions hold.
private Map evaluateCoolingWindowOpen(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp,
                                       boolean windowGatePasses, String noSensorNote) {
    if (!windowGatePasses)   { return null }
    if (outdoorTemp == null) { return null }

    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        // Active cooling is covered by evaluateCoolingPreAlert / evaluateCoolBreach.
        if (mode in ["cool", "auto"]) { return }
        BigDecimal coolSP = safeCurrentBD(t, "coolingSetpoint")
        if (coolSP == null) { return }
        BigDecimal offset = zone.coolingPreAlertOffset ?: 3.0G
        if (indoorTemp >= (coolSP - offset) && outdoorTemp > coolSP) {
            qualifying << [setpoint: coolSP]
        }
    }
    if (qualifying.isEmpty()) { return null }

    Map best = qualifying.min { Math.abs((it.setpoint as BigDecimal) - indoorTemp) }
    String text = "${zone.name} ${indoorTemp}°F nearing ${best.setpoint}°F cool setpoint, outside ${outdoorTemp}°F is hotter \u2014 close windows${noSensorNote}"
    return buildCandidate("zone-${zone.id}-cooling-windowopen", 2, zone.name, "coolingWindowOpen", text, zone.id as String)
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

// Windows-open / heat-off close-windows warning. Mirror of evaluateCoolingWindowOpen
// for heating season: fires when the thermostat is NOT actively heating (e.g. turned
// off because windows are open) yet the room is approaching the heat setpoint and it's
// colder outside than that setpoint — the active-heating pre-alert/breach evaluators
// skip this because they require thermostatMode heat/auto/emergency heat. Independent
// of trend so it warns whenever the conditions hold.
private Map evaluateHeatingWindowOpen(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp,
                                       boolean windowGatePasses, String noSensorNote) {
    if (!windowGatePasses)   { return null }
    if (outdoorTemp == null) { return null }

    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        // Active heating is covered by evaluateHeatingPreAlert / evaluateHeatBreach.
        if (mode in ["heat", "auto", "emergency heat"]) { return }
        BigDecimal heatSP = safeCurrentBD(t, "heatingSetpoint")
        if (heatSP == null) { return }
        BigDecimal offset = zone.heatingPreAlertOffset ?: 3.0G
        if (indoorTemp <= (heatSP + offset) && outdoorTemp < heatSP) {
            qualifying << [setpoint: heatSP]
        }
    }
    if (qualifying.isEmpty()) { return null }

    Map best = qualifying.min { Math.abs((it.setpoint as BigDecimal) - indoorTemp) }
    String text = "${zone.name} ${indoorTemp}°F nearing ${best.setpoint}°F heat setpoint, outside ${outdoorTemp}°F is colder \u2014 close windows${noSensorNote}"
    return buildCandidate("zone-${zone.id}-heating-windowopen", 2, zone.name, "heatingWindowOpen", text, zone.id as String)
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
    List announceModes = (settings.announceModes ?: []) as List
    boolean speakAllowed = !announceModes || announceModes.contains(location.mode)

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

        if (sev >= minSeverity && speakAllowed) {
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
        String val = settings.weatherDevice.currentValue(settings.weatherAttribute as String) as String
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
//   3. current feels-like — only when |feels − actual| ≥ 5°F
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
    if (aqi <= 150) { return "sensitive groups" }
    if (aqi <= 200) { return "unhealthy" }
    if (aqi <= 300) { return "very unhealthy" }
    return "hazardous"
}
