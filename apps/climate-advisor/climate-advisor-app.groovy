/**
 * Climate Advisor
 * Namespace: madskristensen
 * Author:    Mads Kristensen
 * Version:   0.1.0
 *
 * Changelog:
 *   0.1.0 — 2026-05-23 — Initial release: parent app + child driver, per-zone alerts, outdoor/indoor trend detection, predictive close-windows alerts (cooling + heating); event-coalescing debounce, change-only sendEvent, lazy trend computation, childDniMap lookup cache.
 */

import groovy.json.JsonOutput

@Field static final String  APP_VERSION   = "0.1.0"
@Field static final String  CHILD_DRIVER  = "Climate Advisor Device"
@Field static final String  CHILD_NS      = "madskristensen"
@Field static final Integer MAX_AGG_MSG   = 20
@Field static final Integer MAX_ZONE_MSG  = 10
@Field static final Integer AQI_THRESHOLD = 100

definition(
    name:           "Climate Advisor",
    namespace:      CHILD_NS,
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
            href "globalPage",        title: "Global settings",   description: "Outdoor weather, trend windows, logging"
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
            input "throttleMinutes", "number",
                title: "Minimum minutes between repeated notifications", defaultValue: 60, required: true
            input "announceSeverityThreshold", "number",
                title: "Minimum severity for announcements (0=info, 1=warning, 2=alert)",
                range: "0..2", defaultValue: 1, required: true
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
                    title: "Zone ${i} name", required: true
                input "zone${i}Thermostats", "capability.thermostat",
                    title: "Thermostats (optional)", multiple: true, required: false
                input "zone${i}IndoorTempSensors", "capability.temperatureMeasurement",
                    title: "Indoor temperature sensors", multiple: true, required: true
                input "zone${i}ContactSensors", "capability.contactSensor",
                    title: "Window / door contact sensors (optional)", multiple: true, required: false
                input "zone${i}AqSensor", "capability.airQuality",
                    title: "Air quality sensor (optional)", multiple: false, required: false
                input "zone${i}AqiAttribute", "string",
                    title: "AQI attribute name", defaultValue: "airQualityIndex", required: false
                input "zone${i}Speakers", "capability.speechSynthesis",
                    title: "Speakers for announcements (optional)", multiple: true, required: false
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
    state.childDniMap        = [:]
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
    buildChildDniMap()
    subscribeAll()
    // Seed first evaluation after subscriptions settle
    runIn(5, "evaluateAll")
}

// ── Child device management ───────────────────────────────────────────────────

private void reconcileChildren() {
    List zones = configuredZones()
    String appLabel = app.label ?: "Climate Advisor"
    Set wantedDnis = (["${app.id}-aggregate"] + zones.collect { "${app.id}-${it.id}" }) as Set

    String aggDni = "${app.id}-aggregate"
    if (!getChildDevice(aggDni)) {
        def c = addChildDevice(CHILD_NS, CHILD_DRIVER, aggDni,
            [name: "${appLabel} \u2014 Aggregate", label: "${appLabel} \u2014 Aggregate", isComponent: false])
        c.updateDataValue("advisorRole", "aggregate")
        logInfo "Created aggregate child: ${aggDni}"
    }

    zones.each { zone ->
        String dni = "${app.id}-${zone.id}"
        def existing = getChildDevice(dni)
        if (!existing) {
            def c = addChildDevice(CHILD_NS, CHILD_DRIVER, dni,
                [name: "${appLabel} \u2014 ${zone.name}", label: "${appLabel} \u2014 ${zone.name}", isComponent: false])
            c.updateDataValue("advisorRole", "zone")
            c.updateDataValue("zoneId", zone.id as String)
            logInfo "Created zone child: ${dni} (${zone.name})"
        } else {
            String newLabel = "${appLabel} \u2014 ${zone.name}"
            if (existing.label != newLabel) { existing.label = newLabel }
            existing.updateDataValue("advisorRole", "zone")
            existing.updateDataValue("zoneId", zone.id as String)
        }
    }

    getChildDevices()?.each { child ->
        String dni = child.deviceNetworkId
        if (!wantedDnis.contains(dni)) {
            logInfo "Deleting stale child: ${dni}"
            deleteChildDevice(dni)
        }
    }
}

// Build a fast-lookup map: state.childDniMap[key] = deviceNetworkId
// Called once in initialize(); avoids getChildDevice() on every evaluation pass.
private void buildChildDniMap() {
    Map m = ["aggregate": "${app.id}-aggregate"]
    configuredZones().each { zone ->
        m[zone.id as String] = "${app.id}-${zone.id}"
    }
    state.childDniMap = m
    logDebug "childDniMap built: ${m.keySet()}"
}

private def lookupChild(String key) {
    Map m = (state.childDniMap ?: [:]) as Map
    String dni = m[key] as String
    return dni ? getChildDevice(dni) : null
}

// ── Subscriptions (surgical — exact attributes only) ─────────────────────────

private void subscribeAll() {
    if (settings.outdoorTempDevice) {
        subscribe(settings.outdoorTempDevice, "temperature", outdoorTempHandler)
    }

    String wAttr = settings.weatherAttribute as String ?: "weather"
    if (settings.weatherDevice) {
        subscribe(settings.weatherDevice, wAttr, debounceHandler)
    }

    List zones = configuredZones()
    zones.each { zone ->
        zone.indoorTempSensors?.each { sensor ->
            subscribe(sensor, "temperature", debounceHandler)
        }
        zone.thermostats?.each { t ->
            subscribe(t, "thermostatMode",    debounceHandler)
            subscribe(t, "coolingSetpoint",   debounceHandler)
            subscribe(t, "heatingSetpoint",   debounceHandler)
        }
        zone.contactSensors?.each { c ->
            subscribe(c, "contact", debounceHandler)
        }
        if (zone.aqSensor) {
            String aqAttr = zone.aqiAttribute ?: "airQualityIndex"
            subscribe(zone.aqSensor, aqAttr, debounceHandler)
        }
    }
}

// ── Event handlers ────────────────────────────────────────────────────────────

// Outdoor temp handler: O(1) sample append, then debounce
def outdoorTempHandler(evt) {
    try {
        BigDecimal t = evt.value as BigDecimal
        Long ts = now()
        List samples = (state.outdoorSamples ?: []) + [[now: ts, t: t]]
        Long cutoff = ts - (((settings.trendWindowMinutes ?: 30) as Integer) + 5) * 60 * 1000L
        state.outdoorSamples = samples.findAll { it.now >= cutoff }
    } catch (Exception e) {
        log.warn "outdoorTempHandler sample error: ${e.message}"
    }
    runIn(1, "evaluateAll", [overwrite: true])
}

// All other handlers: just schedule the debounced evaluation
def debounceHandler(evt) {
    runIn(1, "evaluateAll", [overwrite: true])
}

// ── Trend computation (lazy — only called from evaluateAll) ───────────────────

Map computeTrend(List samples, Integer windowMinutes, BigDecimal risingThreshold, BigDecimal fallingThreshold) {
    Long newestTs = now()
    Long cutoff = newestTs - windowMinutes * 60 * 1000L
    List window = (samples ?: []).findAll { it.now >= cutoff }.sort { it.now }

    if (window.size() < 2) { return [trend: "unknown", slope10min: null] }

    def oldest = window.first()
    def newest = window.last()
    BigDecimal spanMinutes = (newest.now - oldest.now) / 60000G
    if (spanMinutes < 5G) { return [trend: "unknown", slope10min: null] }

    BigDecimal slopePerMinute = ((newest.t as BigDecimal) - (oldest.t as BigDecimal)) / spanMinutes
    BigDecimal slope10min = slopePerMinute * 10G

    String trend = slope10min > risingThreshold ? "rising" :
                   slope10min < fallingThreshold ? "falling" :
                   "steady"
    return [trend: trend, slope10min: slope10min]
}

private Map outdoorTrendResult() {
    BigDecimal rising  = (settings.outdoorTrendRisingThreshold10min  ?: 0.2)  as BigDecimal
    BigDecimal falling = (settings.outdoorTrendFallingThreshold10min ?: -0.2) as BigDecimal
    return computeTrend((state.outdoorSamples ?: []) as List, (settings.trendWindowMinutes ?: 30) as Integer, rising, falling)
}

private void appendIndoorSample(String zoneId, BigDecimal avgTemp) {
    Long ts = now()
    Map indoorSamples = (state.indoorSamples ?: [:]) as Map
    List zoneSamples  = (indoorSamples[zoneId] ?: []) as List
    zoneSamples = zoneSamples + [[now: ts, t: avgTemp]]
    Long cutoff = ts - (((settings.trendWindowMinutes ?: 30) as Integer) + 5) * 60 * 1000L
    indoorSamples[zoneId] = zoneSamples.findAll { it.now >= cutoff }
    state.indoorSamples = indoorSamples
}

private Map indoorTrendResult(String zoneId) {
    BigDecimal rising  = (settings.outdoorTrendRisingThreshold10min  ?: 0.2)  as BigDecimal
    BigDecimal falling = (settings.outdoorTrendFallingThreshold10min ?: -0.2) as BigDecimal
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
        List zones = configuredZones()
        Long nowMs = now()

        // Retrieve cached messages for stable-ts optimization
        Map prevActive = (state.activeMessages ?: [:]) as Map
        Map newActive  = [:]

        List allMessages = []

        zones.each { zone ->
            List zoneCandidates = evaluateZone(zone, outdoorTemp, outdoorTrend, rainDetected)
            List zoneResolved   = resolveMessages(zoneCandidates, prevActive, newActive, nowMs)
            allMessages.addAll(zoneResolved)
            pushZoneChild(zone, zoneResolved, outdoorTrend, outdoorTemp)
        }

        // House-level rain+contact alert
        List rainCandidates = evaluateHouseRain(rainDetected, zones)
        List rainResolved   = resolveMessages(rainCandidates, prevActive, newActive, nowMs)
        allMessages.addAll(rainResolved)

        // Persist active message cache (stable ts → stable JSON → sendEventIfChanged skips no-op events)
        state.activeMessages = newActive

        // Sort: severity desc, ts desc; cap
        allMessages = allMessages.sort { a, b ->
            int sc = (b.severity as Integer) <=> (a.severity as Integer)
            sc != 0 ? sc : (b.ts as Long) <=> (a.ts as Long)
        }.take(MAX_AGG_MSG)

        int aggSeverity = allMessages ? allMessages.collect { it.severity as Integer }.max() : 0
        String aggSeverityText = severityText(aggSeverity)
        String aggLatest = allMessages ? allMessages[0].text : "All clear"
        String aggStatus = aggLatest
        String aggJson   = JsonOutput.toJson(allMessages)

        def aggChild = lookupChild("aggregate")
        if (aggChild) {
            sendEventIfChanged(aggChild, "severity",              aggSeverity)
            sendEventIfChanged(aggChild, "severityText",          aggSeverityText)
            sendEventIfChanged(aggChild, "latestMessage",         aggLatest)
            sendEventIfChanged(aggChild, "messages",              aggJson)
            sendEventIfChanged(aggChild, "houseStatus",           aggStatus)
            sendEventIfChanged(aggChild, "outdoorTrend",          outdoorTrend.trend ?: "unknown")
            sendEventIfChanged(aggChild, "tempTrend",             outdoorTrend.trend ?: "unknown")
            sendEventIfChanged(aggChild, "outdoorTempSlope10min", outdoorTrend.slope10min)
            sendEventIfChanged(aggChild, "activeAlertCount",      allMessages.count { (it.severity as Integer) >= 2 })
            sendEventIfChanged(aggChild, "zoneCount",             zones.size())
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

// Returns a List of candidate message Maps (no ts field — assigned by resolveMessages)
private List evaluateZone(Map zone, BigDecimal outdoorTemp, Map outdoorTrend, boolean rainDetected) {
    List candidates = []

    BigDecimal indoorTemp = averageTemps(zone.indoorTempSensors)
    if (indoorTemp == null) {
        logDebug "Zone ${zone.name}: no indoor temp — skipping"
        return candidates
    }

    if (settings.indoorTrendEnabled != false) {
        appendIndoorSample(zone.id as String, indoorTemp)
    }

    List contacts = zone.contactSensors ?: []
    List openContacts = contacts.findAll { it.currentValue("contact") == "open" }
    boolean anyOpen = !openContacts.isEmpty()
    boolean noContactsConfigured = contacts.isEmpty()
    boolean windowGatePasses = noContactsConfigured || anyOpen
    String noSensorNote = noContactsConfigured ? " (no window sensors configured)" : ""

    def m
    m = evaluateCoolingPreAlert(zone, indoorTemp, outdoorTemp, outdoorTrend, windowGatePasses, noSensorNote)
    if (m) { candidates << m }

    m = evaluateHeatingPreAlert(zone, indoorTemp, outdoorTemp, outdoorTrend, windowGatePasses, noSensorNote)
    if (m) { candidates << m }

    m = evaluateCoolBreach(zone, indoorTemp, outdoorTemp)
    if (m) { candidates << m }

    m = evaluateHeatBreach(zone, indoorTemp, outdoorTemp)
    if (m) { candidates << m }

    m = evaluateAqi(zone)
    if (m) { candidates << m }

    return candidates
}

private Map evaluateCoolingPreAlert(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp,
                                     Map outdoorTrend, boolean windowGatePasses, String noSensorNote) {
    if (!windowGatePasses)              { return null }
    if (outdoorTrend.trend == "unknown") { return null }
    if (outdoorTrend.trend != "rising")  { return null }
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
    String text = "${zone.name} ${indoorTemp}°F approaching ${best.setpoint}°F cool setpoint, outside ${outdoorTemp}°F rising \u2014 close windows${noSensorNote}"
    return buildCandidate("zone-${zone.id}-cooling-prealert", 1, "warning", zone.name, "coolingPreAlert", text)
}

private Map evaluateHeatingPreAlert(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp,
                                     Map outdoorTrend, boolean windowGatePasses, String noSensorNote) {
    if (!windowGatePasses)               { return null }
    if (outdoorTrend.trend == "unknown")  { return null }
    if (outdoorTrend.trend != "falling")  { return null }
    if (outdoorTemp == null || outdoorTemp >= indoorTemp) { return null }

    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        if (!(mode in ["heat", "auto"])) { return }
        BigDecimal heatSP = safeCurrentBD(t, "heatingSetpoint")
        if (heatSP == null) { return }
        BigDecimal offset = zone.heatingPreAlertOffset ?: 3.0G
        if (indoorTemp <= (heatSP + offset)) { qualifying << [setpoint: heatSP] }
    }
    if (qualifying.isEmpty()) { return null }

    Map best = qualifying.min { Math.abs((it.setpoint as BigDecimal) - indoorTemp) }
    String text = "${zone.name} ${indoorTemp}°F approaching ${best.setpoint}°F heat setpoint, outside ${outdoorTemp}°F falling \u2014 close windows${noSensorNote}"
    return buildCandidate("zone-${zone.id}-heating-prealert", 1, "warning", zone.name, "heatingPreAlert", text)
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
    return buildCandidate("zone-${zone.id}-setpoint-breach-cool", 2, "alert", zone.name, "setpointBreachCool", text)
}

private Map evaluateHeatBreach(Map zone, BigDecimal indoorTemp, BigDecimal outdoorTemp) {
    if ((zone.thermostats ?: []).isEmpty()) { return null }
    List qualifying = []
    (zone.thermostats ?: []).each { t ->
        String mode = t.currentValue("thermostatMode")
        if (!(mode in ["heat", "auto"])) { return }
        BigDecimal heatSP = safeCurrentBD(t, "heatingSetpoint")
        if (heatSP == null) { return }
        if (indoorTemp <= heatSP && outdoorTemp != null && outdoorTemp < indoorTemp) {
            qualifying << [setpoint: heatSP]
        }
    }
    if (qualifying.isEmpty()) { return null }
    Map best = qualifying.min { Math.abs((it.setpoint as BigDecimal) - indoorTemp) }
    String text = "${zone.name} ${indoorTemp}°F has breached ${best.setpoint}°F heat setpoint \u2014 close windows"
    return buildCandidate("zone-${zone.id}-setpoint-breach-heat", 2, "alert", zone.name, "setpointBreachHeat", text)
}

private Map evaluateAqi(Map zone) {
    if (!zone.aqSensor) { return null }
    String attr = zone.aqiAttribute ?: "airQualityIndex"
    def raw = zone.aqSensor.currentValue(attr)
    if (raw == null) { return null }
    try {
        BigDecimal aqi = raw as BigDecimal
        if (aqi > AQI_THRESHOLD) {
            String text = "${zone.name} air quality is poor (AQI ${aqi.toInteger()} > ${AQI_THRESHOLD}) \u2014 consider closing windows"
            return buildCandidate("zone-${zone.id}-aqi-poor", 2, "alert", zone.name, "aqiPoor", text)
        }
    } catch (Exception e) {
        log.warn "evaluateAqi error: ${e.message}"
    }
    return null
}

private List evaluateHouseRain(boolean rainDetected, List zones) {
    if (!rainDetected) { return [] }
    boolean anyZoneOpen = zones.any { zone ->
        (zone.contactSensors ?: []).any { it.currentValue("contact") == "open" }
    }
    if (!anyZoneOpen) { return [] }
    return [buildCandidate("house-rain-windows-open", 2, "alert", "House", "rainWindowsOpen",
            "Rain detected and windows are open \u2014 close windows now")]
}

// ── Per-zone child push ───────────────────────────────────────────────────────

private void pushZoneChild(Map zone, List resolvedMsgs, Map outdoorTrend, BigDecimal outdoorTemp) {
    def child = lookupChild(zone.id as String)
    if (!child) { return }

    List sorted = resolvedMsgs.sort { a, b ->
        int sc = (b.severity as Integer) <=> (a.severity as Integer)
        sc != 0 ? sc : (b.ts as Long) <=> (a.ts as Long)
    }.take(MAX_ZONE_MSG)

    int severity = sorted ? sorted.collect { it.severity as Integer }.max() : 0
    String sevText  = severityText(severity)
    String latestMsg = sorted ? sorted[0].text : "All clear"
    String zoneStatus = severity > 0 ? latestMsg : "${zone.name} all clear"

    BigDecimal indoorTemp = averageTemps(zone.indoorTempSensors)
    Map indoorTrend = (settings.indoorTrendEnabled != false) ? indoorTrendResult(zone.id as String) : [trend: "unknown", slope10min: null]

    List contacts = zone.contactSensors ?: []
    List openList = contacts.findAll { it.currentValue("contact") == "open" }
    String openNames = openList.collect { it.displayName }.join(", ")

    BigDecimal aqiVal = null
    if (zone.aqSensor) {
        def raw = zone.aqSensor.currentValue(zone.aqiAttribute ?: "airQualityIndex")
        if (raw != null) { try { aqiVal = raw as BigDecimal } catch (Exception ignored) {} }
    }

    sendEventIfChanged(child, "severity",              severity)
    sendEventIfChanged(child, "severityText",          sevText)
    sendEventIfChanged(child, "latestMessage",         latestMsg)
    sendEventIfChanged(child, "messages",              JsonOutput.toJson(sorted))
    sendEventIfChanged(child, "houseStatus",           zoneStatus)
    sendEventIfChanged(child, "zoneName",              zone.name)
    sendEventIfChanged(child, "indoorTemp",            indoorTemp)
    sendEventIfChanged(child, "indoorTrend",           indoorTrend.trend ?: "unknown")
    sendEventIfChanged(child, "indoorTempSlope10min",  indoorTrend.slope10min)
    sendEventIfChanged(child, "outdoorTrend",          outdoorTrend.trend ?: "unknown")
    sendEventIfChanged(child, "outdoorTempSlope10min", outdoorTrend.slope10min)
    sendEventIfChanged(child, "openContactCount",      openList.size())
    sendEventIfChanged(child, "openContacts",          openNames)
    if (aqiVal != null) { sendEventIfChanged(child, "aqi", aqiVal) }
}

// ── Notifications ─────────────────────────────────────────────────────────────

private void handleNotifications(List allMessages, List zones) {
    if (!allMessages) { return }
    Map lastNotif = (state.lastNotificationAt ?: [:]) as Map
    Long throttleMs = ((settings.throttleMinutes ?: 60) as Integer) * 60 * 1000L
    Long nowMs = now()
    Integer minSeverity = (settings.announceSeverityThreshold ?: 1) as Integer

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
            // legacy: stored as plain epochMs Long
            lastTs = lastEntry as Long
        }

        // Skip if within throttle window AND severity hasn't risen
        if ((nowMs - lastTs) < throttleMs && sev <= lastSev) { return }

        String zoneId = extractZoneId(msgId)
        List notifDevices = (settings.globalNotificationDevices ?: []) as List
        if (zoneId) {
            Map zone = zones.find { it.id == zoneId }
            if (zone) { notifDevices = notifDevices + (zone.notificationDevices ?: []) }
        }
        notifDevices.unique().each { d ->
            try { d.deviceNotification(msg.text as String) } catch (Exception e) { log.warn "notify error: ${e.message}" }
        }

        if (sev >= minSeverity && zoneId) {
            Map zone = zones.find { it.id == zoneId }
            (zone?.speakers ?: []).each { spk ->
                try { spk.speak(msg.text as String) } catch (Exception e) { log.warn "speak error: ${e.message}" }
            }
        }

        lastNotif[msgId] = [ts: nowMs, sev: sev]
    }
    state.lastNotificationAt = lastNotif
}

private String extractZoneId(String msgId) {
    if (msgId?.startsWith("zone-")) {
        def m = (msgId =~ /^zone-(zone\d+)-/)
        if (m) { return m[0][1] as String }
    }
    return null
}

// ── Refresh command ───────────────────────────────────────────────────────────

def refresh() {
    logDebug "refresh()"
    evaluateAll()
}

// ── Helpers ───────────────────────────────────────────────────────────────────

List<Map> configuredZones() {
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
            aqSensor             : settings["zone${i}AqSensor"],
            aqiAttribute         : settings["zone${i}AqiAttribute"]           ?: "airQualityIndex",
            speakers             : settings["zone${i}Speakers"]               ?: [],
            coolingPreAlertOffset: (settings["zone${i}CoolingPreAlertOffset"] ?: 3.0) as BigDecimal,
            heatingPreAlertOffset: (settings["zone${i}HeatingPreAlertOffset"] ?: 3.0) as BigDecimal,
            notificationDevices  : settings["zone${i}NotificationDevices"]    ?: []
        ]
    }.findAll { it != null }
}

private boolean checkRain() {
    if (!settings.weatherDevice || !settings.weatherAttribute) { return false }
    try {
        String val     = settings.weatherDevice.currentValue(settings.weatherAttribute as String) as String
        String keyword = (settings.rainKeyword ?: "rain") as String
        return val?.toLowerCase()?.contains(keyword.toLowerCase()) ?: false
    } catch (Exception e) {
        log.warn "checkRain error: ${e.message}"
        return false
    }
}

// Candidate message: no ts — ts assigned by resolveMessages at evaluation time
private Map buildCandidate(String id, int severity, String sevText, String source, String family, String text) {
    return [id: id, severity: severity, severityText: sevText, source: source, family: family, text: text]
}

private String severityText(int severity) {
    if (severity >= 2) { return "alert" }
    if (severity == 1) { return "warning" }
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
// String comparison covers numeric types safely via toString().
private void sendEventIfChanged(def d, String name, Object value, String unit = null) {
    def current = d.currentValue(name)
    if (current?.toString() == value?.toString()) { return }
    Map evt = [name: name, value: value]
    if (unit) { evt.unit = unit }
    d.sendEvent(evt)
}

private void logDebug(String msg) { if (settings.logEnable) { log.debug msg } }
private void logInfo(String msg)  { if (settings.txtEnable != false) { log.info msg } }

def logsOff() {
    log.warn "Debug logging disabled"
    app.updateSetting("logEnable", [value: false, type: "bool"])
}
