/**
 * Gemstone Lights
 * Author:  Mads Kristensen
 * Version: 0.4.9
 * License: MIT
 *
 * Controls a Gemstone permanent outdoor LED string via the Gemstone cloud REST API.
 * Enter your Gemstone account email + password in preferences; Hubitat stores them
 * as encrypted preferences and the driver caches Cognito tokens in state.
 *
 * Changelog:
 *   0.4.9 — 2026-05-17 — reliability fixes for catalog & in-flight state
 *     - Stale in-flight flags (effectCatalogRefreshInFlight, discoveryInFlight, authInFlight) now cleared on initialize() — fixes silent no-op state after hub reboot mid-operation
 *     - Effect catalog is no longer wiped on every preference save — only when credentials change (previously forced a full re-fetch on any pref toggle)
 *     - Removed dead state.idToken storage — the Cognito IdToken was written but never read (state bloat fix)
 *   0.4.8 — 2026-05-16 — Fill descriptionText on every sendEvent so the Hubitat Events tab Description column is populated for status-refresh events (effectName, colorMode, lightEffects, authStatus, etc.), not just user-initiated commands. Uses device.displayName so it works regardless of what the user renamed the device.
 *   0.4.7 — 2026-05-16 — Drop the non-favorite name list from the debug catalog-load log. Mads has 1457 non-favorite patterns; even at debug level, dumping all names was multi-KB. Log now shows just the count. Non-favorite names are still surfaced (capped at 20) on miss-path warns.
 *   0.4.6 — 2026-05-16 — Log hygiene + favorites-only UI/state: stop dumping the full preset list on every named setEffect call (only on miss, warn level, capped at 20); `lightEffects` UI dropdown now shows favorites only (curated set marked in the Gemstone app); `state.effectCatalog` and `state.effectPatterns` cache favorites only — non-favorites still resolve by name via on-demand catalog lookup, but no longer clog the device State Variables panel. Existing installs prune non-favorite state entries on next driver update.
 *   0.4.5 — 2026-05-16 — Fix color byte order: Gemstone wire format is ABGR (A, B, G, R) not ARGB. v0.4.4 packed bytes as ARGB which caused red to render as blue, green correctly, blue as red. Swap r/b byte positions in hubitatHueSatToArgb and kelvinToArgb; reverse the same in gemstoneArgbToHubitatColor.
 *   0.4.4 — 2026-05-16 — Force ARGB color values to positive Long (Gemstone API requires unsigned 32-bit range [0, 4294967295]; v0.4.2's (0xFF << 24) produced a negative signed-int which failed validation). hubitatHueSatToArgb and kelvinToArgb now use long arithmetic with 0xFFL literals and return Long. gemstoneArgbToHubitatColor accepts Number/Long for symmetry.
 *   0.4.3 — 2026-05-16 — Diagnostic: flatten multi-line 400 response bodies (Python tracebacks from Gemstone API) to single-line log output so the full error is visible. Bumped truncate length to 2000.
 *   0.4.2 — 2026-05-16 — Diagnostic + payload fixes for setColor 400 / setColorTemperature silent-fail. Surface response.getErrorData() in 400 handler; log non-gated info when request is queued (no token or no deviceId); ARGB color generation now includes 0xFF alpha byte; setColor/setColorTemperature no longer override pattern.id (preserves real UUID from refresh); referencePatternId omitted entirely instead of sent as null.
 *   0.4.1 — 2026-05-16 — Added playEffectByName(String) as a separate (non-overloaded) command so WebCoRE's action picker exposes a String input. Internally delegates to setEffect(String).
 *   0.4.0 — 2026-05-16 — Added LightEffects, ColorTemperature, and colorMode support. Favorites now surface first in lightEffects, info logs, and favoriteEffects.
 *   0.3.0 — 2026-05-16 — Added setEffect(name) custom command, refreshEffectCatalog() helper, and effectName attribute. Effects can now be invoked by name from Hubitat rules.
 *   0.2.5 — 2026-05-16 — Fixed Hubitat encoder rejection by pre-serializing Cognito JSON body and routing AWS Content-Type via headers map
 *   0.2.4 — 2026-05-16 — Diagnostic logging on Cognito auth failure; verified InitiateAuth payload shape against AWS docs; HTTP timeout raised to 30s
 *   0.2.3 — 2026-05-16 — Full Hubitat sandbox audit: replaced System.currentTimeMillis() with now() and swept other forbidden JDK calls (Thread/Runtime/reflection/file IO)
 *   0.2.2 — 2026-05-16 — Inlined version literal in USER_AGENT to satisfy Hubitat sandbox static-init rules (concat was insufficient)
 *   0.2.1 — 2026-05-16 — Fixed Hubitat compile error: static-context reference to DRIVER_VERSION in USER_AGENT
 *   0.2.0 — 2026-05-16 — Implemented Cognito auth + Gemstone cloud REST control (on/off, level, color, refresh)
 *   0.1.1 — 2026-05-16 — Added scaffold transparency warn banner (always-visible log.warn in sendCommand)
 *   0.1.0 — 2026-05-16 — Initial scaffold (HTTP endpoints stubbed pending protocol discovery)
 */

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URLEncoder

@Field static final String DRIVER_VERSION = "0.4.9"
@Field static final String COGNITO_URL = "https://cognito-idp.us-west-2.amazonaws.com/"
@Field static final String JSON_CONTENT_TYPE = "application/json"
@Field static final String COGNITO_CONTENT_TYPE = "application/x-amz-json-1.1"
@Field static final String COGNITO_TARGET = "AWSCognitoIdentityProviderService.InitiateAuth"
@Field static final String COGNITO_CLIENT_ID = "2647t144niotrl53vvru0ivno7"
@Field static final String API_BASE_URL = "https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod"
@Field static final Integer DEFAULT_TIMEOUT_SECONDS = 30
@Field static final Integer COGNITO_TIMEOUT_SECONDS = 30
@Field static final Integer TOKEN_REFRESH_LEEWAY_SECONDS = 300
@Field static final Integer DEFAULT_PATTERN_SPEED = 128
@Field static final Integer DEFAULT_PATTERN_DIRECTION = 0
@Field static final String DEFAULT_PATTERN_ANIMATION = "motionless"
@Field static final Long EFFECT_CATALOG_TTL_MILLIS = 3600000L
@Field static final String FAVORITE_EFFECT_PREFIX = "⭐ "
@Field static final String COLOR_MODE_RGB = "RGB"
@Field static final String COLOR_MODE_CT = "CT"
@Field static final String COLOR_MODE_EFFECTS = "EFFECTS"
@Field static final String CT_PATTERN_NAME_PREFIX = "Hubitat White Temperature"
// keep in sync with DRIVER_VERSION
@Field static final String USER_AGENT = "Hubitat Gemstone Lights/0.4.8"

metadata {
    definition(
        name:      "Gemstone Lights",
        namespace: "mads",
        author:    "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/gemstone-lights.groovy"
    ) {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "ColorControl"
        capability "ColorTemperature"
        capability "LightEffects"
        capability "Refresh"
        capability "Initialize"

        command "setEffect", [[name: "name", type: "STRING", description: "Gemstone effect name (⭐ prefix optional)"]]
        command "playEffectByName", [[name: "name*", type: "STRING",
            description: "Play a Gemstone effect by name (e.g. 'Pulse' or '⭐ Pulse'). Use this from WebCoRE — the standard setEffect command only accepts a number."]]
        command "refreshEffectCatalog"

        attribute "effectName", "string"
        attribute "authStatus", "string"
        attribute "colorMode", "string"
        attribute "colorName", "string"
        attribute "favoriteEffects", "string"
    }

    preferences {
        input name: "accountEmail",    type: "text",     title: "Gemstone account email",
              description: "Email used in the Gemstone mobile app. Required.",
              required: true
        input name: "accountPassword", type: "password", title: "Gemstone account password",
              description: "Stored by Hubitat as an encrypted preference. Required.",
              required: true
        input name: "pollInterval",    type: "enum",     title: "Polling interval",
              options: ["1": "1 minute", "5": "5 minutes (recommended)", "10": "10 minutes", "0": "Disabled"],
              defaultValue: "5", required: true
        input name: "requestTimeout",  type: "number",   title: "HTTPS request timeout (seconds)",
              defaultValue: 30, range: "5..60", required: true
        input name: "logEnable",       type: "bool",     title: "Enable debug logging (auto-off after 30 minutes)",
              defaultValue: false
        input name: "txtEnable",       type: "bool",     title: "Enable descriptionText (info) logging",
              defaultValue: true
    }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    log.info "Gemstone Lights v" + DRIVER_VERSION + " installed"
    device.updateSetting("pollInterval", [value: "5", type: "enum"])
    device.updateSetting("requestTimeout", [value: 30, type: "number"])
    device.updateSetting("logEnable", [value: false, type: "bool"])
    device.updateSetting("txtEnable", [value: true, type: "bool"])
    updateAuthStatus("Not configured")
    initialize()
}

def updated() {
    log.info "Gemstone Lights v" + DRIVER_VERSION + " preferences updated"
    unschedule()

    boolean credentialsChanged = (settings.accountEmail != state.lastKnownEmail)
    state.lastKnownEmail = settings.accountEmail

    clearAuthTokens()
    clearDiscoveryState()
    pruneNonFavoriteStateEntries()
    if (credentialsChanged) {
        clearEffectCatalogState()
    }
    clearPendingRequests()

    if (settings.logEnable) {
        runIn(1800, "logsOff")
        debugLog "Debug logging enabled; will auto-disable in 30 minutes"
    }

    initialize()
}

def initialize() {
    debugLog "initialize() called"
    pruneNonFavoriteStateEntries()
    unschedule("poll")
    unschedule("refreshAccessTokenTask")
    clearPendingRequests()

    // clear stale in-flight flags after init (recovery from interrupted operations)
    state.effectCatalogRefreshInFlight = false
    state.discoveryInFlight = false
    state.authInFlight = false

    if (!credentialsConfigured()) {
        updateAuthStatus("Not configured")
        infoLog "${device.displayName} is waiting for Gemstone cloud credentials"
        return
    }

    schedulePolling()
    scheduleTokenRefreshIfNeeded()
    runIn(2, "refresh")
}

def uninstalled() {
    unschedule()
    clearPendingRequests()
    debugLog "uninstalled()"
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "Gemstone Lights: debug logging auto-disabled after 30 minutes"
}

// ---------------------------------------------------------------------------
// Commands — Switch
// ---------------------------------------------------------------------------

def on() {
    infoLog "${device.displayName} switch → on"
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} was turned on", type: "digital")
    state.lastOnState = true
    sendCommand(action: "on")
}

def off() {
    infoLog "${device.displayName} switch → off"
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} was turned off", type: "digital")
    state.lastOnState = false
    sendCommand(action: "off")
}

// ---------------------------------------------------------------------------
// Commands — SwitchLevel
// ---------------------------------------------------------------------------

def setLevel(level, duration = 0) {
    Integer clamped = clampPercent(level)
    infoLog "${device.displayName} level → ${clamped}"
    sendEvent(name: "level", value: clamped, unit: "%", descriptionText: "${device.displayName} level set to ${clamped}%", type: "digital")
    sendEvent(name: "switch", value: clamped > 0 ? "on" : "off", descriptionText: "${device.displayName} turned ${clamped > 0 ? 'on' : 'off'}", type: "digital")
    state.lastOnState = clamped > 0

    if (clamped == 0) {
        sendCommand(action: "off")
        return
    }

    sendCommand(action: "setLevel", level: clamped)
}

// ---------------------------------------------------------------------------
// Commands — ColorControl
// ---------------------------------------------------------------------------

def setColor(colorMap) {
    Integer hue = clampPercent(colorMap?.hue ?: 0)
    Integer saturation = clampPercent(colorMap?.saturation ?: 100)
    Integer level = clampPercent(colorMap?.level ?: safeInt(device.currentValue("level"), 100))

    infoLog "${device.displayName} color → hue=${hue} sat=${saturation} level=${level}"
    sendEvent(name: "hue", value: hue, descriptionText: "${device.displayName} hue set to ${hue}", type: "digital")
    sendEvent(name: "saturation", value: saturation, descriptionText: "${device.displayName} saturation set to ${saturation}", type: "digital")
    sendEvent(name: "level", value: level, unit: "%", descriptionText: "${device.displayName} level set to ${level}%", type: "digital")
    sendEvent(name: "switch", value: level > 0 ? "on" : "off", descriptionText: "${device.displayName} turned ${level > 0 ? 'on' : 'off'}", type: "digital")
    state.lastOnState = level > 0
    clearCurrentEffectIndex()
    updateColorMode(COLOR_MODE_RGB)

    if (level == 0) {
        sendCommand(action: "off")
        return
    }

    sendCommand(action: "setColor", color: [hue: hue, saturation: saturation, level: level])
}

def setHue(hue) {
    setColor([
        hue: hue,
        saturation: safeInt(device.currentValue("saturation"), 100),
        level: safeInt(device.currentValue("level"), 100)
    ])
}

def setSaturation(saturation) {
    setColor([
        hue: safeInt(device.currentValue("hue"), 0),
        saturation: saturation,
        level: safeInt(device.currentValue("level"), 100)
    ])
}

// ---------------------------------------------------------------------------
// Commands — Effects
// ---------------------------------------------------------------------------

def setColorTemperature(colorTemperature, level = null, transitionTime = null) {
    Integer kelvin = clampColorTemperature(colorTemperature)
    Integer targetLevel = level == null ? clampPercent(safeInt(device.currentValue("level"), 100)) : clampPercent(level)
    if (transitionTime != null) {
        debugLog "Gemstone ignores transitionTime=${transitionTime} for setColorTemperature()"
    }

    warnColorTemperatureFallback()
    infoLog "${device.displayName} color temperature → ${kelvin}K level=${targetLevel} (RGB fallback)"
    updateColorTemperatureAttributes(kelvin)
    Map hs = gemstoneArgbToHubitatColor(kelvinToArgb(kelvin))
    sendEvent(name: "hue", value: hs.hue, descriptionText: "${device.displayName} hue set to ${hs.hue}", type: "digital")
    sendEvent(name: "saturation", value: hs.saturation, descriptionText: "${device.displayName} saturation set to ${hs.saturation}", type: "digital")
    sendEvent(name: "level", value: targetLevel, unit: "%", descriptionText: "${device.displayName} level set to ${targetLevel}%", type: "digital")
    sendEvent(name: "switch", value: targetLevel > 0 ? "on" : "off", descriptionText: "${device.displayName} turned ${targetLevel > 0 ? 'on' : 'off'}", type: "digital")
    state.lastOnState = targetLevel > 0
    clearCurrentEffectIndex()
    updateColorMode(COLOR_MODE_CT)

    if (targetLevel == 0) {
        sendCommand(action: "off")
        return
    }

    executeOrQueueRequest(buildColorTemperatureRequest(kelvin, targetLevel))
}

def setEffect(BigDecimal effectNumber) {
    Integer requestedIndex = safeInt(effectNumber, -1)
    if (requestedIndex < 0) {
        log.error "[Gemstone] setEffect(effectNumber) requires a non-negative effect index."
        return
    }
    if (!credentialsConfigured()) {
        log.error "[Gemstone] Gemstone account email and password are required in Preferences before 'setEffect' can run."
        updateAuthStatus("Not configured — add email/password")
        return
    }

    if (effectCatalogMissing()) {
        queuePendingEffectRequest([type: "index", value: requestedIndex])
        requestEffectCatalogRefresh(false)
        return
    }

    if (effectCatalogStale()) {
        queuePendingEffectRequest([type: "index", value: requestedIndex])
        requestEffectCatalogRefresh(false)
        return
    }

    activateEffectByIndex(requestedIndex)
}

def setEffect(String name) {
    String requestedName = safeString(name).trim()
    if (!requestedName) {
        log.error "[Gemstone] setEffect(name) requires a non-empty effect name."
        return
    }
    if (!credentialsConfigured()) {
        log.error "[Gemstone] Gemstone account email and password are required in Preferences before 'setEffect' can run."
        updateAuthStatus("Not configured — add email/password")
        return
    }

    if (effectCatalogMissing()) {
        queuePendingEffectRequest([type: "name", value: requestedName])
        requestEffectCatalogRefresh(true)
        return
    }

    if (effectCatalogStale()) {
        queuePendingEffectRequest([type: "name", value: requestedName])
        requestEffectCatalogRefresh(false)
        return
    }

    activateEffectByName(requestedName)
}

def playEffectByName(String name) {
    setEffect(name)
}

def setNextEffect() {
    if (!credentialsConfigured()) {
        log.error "[Gemstone] Gemstone account email and password are required in Preferences before 'setNextEffect' can run."
        updateAuthStatus("Not configured — add email/password")
        return
    }
    if (effectCatalogMissing()) {
        queuePendingEffectRequest([type: "next"])
        requestEffectCatalogRefresh(false)
        return
    }
    if (effectCatalogStale()) {
        queuePendingEffectRequest([type: "next"])
        requestEffectCatalogRefresh(false)
        return
    }

    cycleEffect(1)
}

def setPreviousEffect() {
    if (!credentialsConfigured()) {
        log.error "[Gemstone] Gemstone account email and password are required in Preferences before 'setPreviousEffect' can run."
        updateAuthStatus("Not configured — add email/password")
        return
    }
    if (effectCatalogMissing()) {
        queuePendingEffectRequest([type: "previous"])
        requestEffectCatalogRefresh(false)
        return
    }
    if (effectCatalogStale()) {
        queuePendingEffectRequest([type: "previous"])
        requestEffectCatalogRefresh(false)
        return
    }

    cycleEffect(-1)
}

def refreshEffectCatalog() {
    if (!credentialsConfigured()) {
        log.error "[Gemstone] Gemstone account email and password are required in Preferences before 'refreshEffectCatalog' can run."
        updateAuthStatus("Not configured — add email/password")
        return
    }

    requestEffectCatalogRefresh(false)
}

// ---------------------------------------------------------------------------
// Commands — Refresh / Poll
// ---------------------------------------------------------------------------

def refresh() {
    debugLog "refresh() requested"
    sendCommand(action: "refresh")
}

def poll() {
    debugLog "poll() scheduled"
    refresh()
}

// ---------------------------------------------------------------------------
// Async HTTP callbacks
// ---------------------------------------------------------------------------

def cognitoAuthCallback(response, data) {
    state.authInFlight = false

    Integer status = responseStatus(response)
    boolean hasError = responseHasError(response)
    String rawBody = responseBody(response)
    String responseMessage = extractServiceMessage(rawBody)
    boolean refreshMode = safeString(data?.mode) == "refresh"

    if (status == 200) {
        Map payload = responseJson(response) ?: [:]
        Map authResult = payload?.AuthenticationResult instanceof Map ? payload.AuthenticationResult as Map : [:]

        if (!authResult?.AccessToken || !authResult?.IdToken) {
            logCognitoAuthFailure(response, data)
            String challenge = safeString(payload?.ChallengeName)
            if (challenge) {
                handleAuthFailure(
                    "Cognito requested challenge '${challenge}'. USER_PASSWORD_AUTH did not complete.",
                    "Auth failed — Cognito challenge returned"
                )
            } else {
                handleAuthFailure(
                    "Cognito auth succeeded at HTTP level but did not return tokens.",
                    "Auth failed — no tokens returned"
                )
            }
            return
        }

        storeTokens(authResult, refreshMode ? safeString(state.refreshToken) : null)
        scheduleTokenRefresh()

        if (!state.deviceId) {
            discoverDevice()
        } else {
            updateAuthenticatedStatus()
            flushPendingRequests()
        }
        return
    }

    if (hasError && (status == null || status == 408 || !rawBody)) {
        logCognitoAuthFailure(response, data)
        String networkMessage = buildNetworkFailureMessage(
            refreshMode ? "Cognito token refresh" : "Cognito auth",
            responseErrorMessage(response),
            safeCognitoTimeout()
        )

        if (refreshMode && credentialsConfigured()) {
            log.error "[Gemstone] ${networkMessage}. Falling back to full login."
            startPasswordAuth("refresh fallback after network error")
            return
        }

        handleAuthFailure(networkMessage, "Auth failed — network error")
        return
    }

    if (status != null && status >= 400) {
        logCognitoAuthFailure(response, data)
        if (refreshMode && credentialsConfigured()) {
            log.error "[Gemstone] Token refresh failed (HTTP ${status}${responseMessage ? ": ${responseMessage}" : ""}). Falling back to full login."
            startPasswordAuth("refresh fallback after HTTP ${status}")
            return
        }

        String message = buildAuthFailureMessage(status, responseMessage)
        handleAuthFailure(message, authStatusValueForAuthFailure(responseMessage))
        return
    }

    if (hasError) {
        logCognitoAuthFailure(response, data)
        String networkMessage = buildNetworkFailureMessage(
            refreshMode ? "Cognito token refresh" : "Cognito auth",
            responseErrorMessage(response),
            safeCognitoTimeout()
        )

        if (refreshMode && credentialsConfigured()) {
            log.error "[Gemstone] ${networkMessage}. Falling back to full login."
            startPasswordAuth("refresh fallback after network error")
            return
        }

        handleAuthFailure(networkMessage, "Auth failed — network error")
        return
    }

    logCognitoAuthFailure(response, data)
    handleAuthFailure(
        "Cognito auth failed with an unexpected response.",
        "Auth failed — unexpected response"
    )
}

def apiResponseCallback(response, data) {
    Integer status = responseStatus(response)

    if (status == 401) {
        cleanupAfterRequestFailure(data)

        if (data?.allowRetry401 != false) {
            log.error "[Gemstone] Gemstone cloud returned HTTP 401 for ${data?.requestLabel ?: data?.path}. Refreshing the access token and retrying once."
            Map retryRequest = cloneMap(data as Map)
            retryRequest.allowRetry401 = false
            queueRequest(retryRequest)
            startTokenRefresh("HTTP 401 from ${data?.path}")
            return
        }

        handleAuthFailure(
            "Gemstone cloud returned HTTP 401 again after a token refresh while handling ${data?.requestLabel ?: data?.path}.",
            "Auth failed — token refresh did not recover"
        )
        return
    }

    if (status != null && status >= 400) {
        cleanupAfterRequestFailure(data)
        String body = responseBody(response)
        if (!body) {
            // For 4xx responses Hubitat puts the error body in errorData, not getData()
            try {
                def errData = response.getErrorData()
                if (errData) body = safeString(errData)
            } catch (ignored) {}
        }
        String displayBody = body ? truncate((extractServiceMessage(body) ?: body).replaceAll("[\\r\\n]+", " | "), 2000) : null
        log.error "[Gemstone] Unexpected HTTP ${status} from ${data?.path}${displayBody ? ": ${displayBody}" : " (empty response body)"}"
        if (data?.action == "effectCatalogPage") {
            handleEffectCatalogRefreshFailure()
        }
        if (data?.action in ["discoverHomegroups", "discoverDevices"]) {
            clearPendingRequests()
            updateAuthStatus("Authenticated — device discovery failed")
        }
        return
    }

    if (responseHasError(response)) {
        cleanupAfterRequestFailure(data)
        log.error "[Gemstone] ${buildNetworkFailureMessage("Gemstone ${data?.requestLabel ?: 'request'}", responseErrorMessage(response))}"
        if (data?.action == "effectCatalogPage") {
            handleEffectCatalogRefreshFailure()
        }
        if (data?.action in ["discoverHomegroups", "discoverDevices"]) {
            clearPendingRequests()
            updateAuthStatus("Authenticated — device discovery failed")
        }
        return
    }

    Map payload = responseJson(response) ?: [:]
    if (data?.action == "discoverHomegroups") {
        handleHomegroupsResponse(payload)
        return
    }
    if (data?.action == "discoverDevices") {
        handleDevicesResponse(payload)
        return
    }
    if (data?.action == "refresh") {
        handleRefreshResponse(payload)
    }
    if (data?.action == "effectCatalogPage") {
        handleEffectCatalogPageResponse(payload, data as Map)
    }

    updateAuthenticatedStatus()

    if (data?.followUp instanceof Map) {
        dispatchApiRequest(data.followUp as Map)
    }
}

// ---------------------------------------------------------------------------
// Cloud driver parse hook (unused but safe)
// ---------------------------------------------------------------------------

def parse(String description) {
    debugLog "Unexpected parse() payload for cloud driver: ${description}"
}

// ---------------------------------------------------------------------------
// Command mapping / request queue
// ---------------------------------------------------------------------------

private void sendCommand(Map params) {
    switch (safeString(params?.action)) {
        case "on":
            executeOrQueueRequest(buildOnStateRequest(true))
            break
        case "off":
            executeOrQueueRequest(buildOnStateRequest(false))
            break
        case "refresh":
            executeOrQueueRequest(buildRefreshRequest())
            break
        case "setLevel":
            executeOrQueueRequest(buildLevelRequest(safeInt(params?.level, 100)))
            break
        case "setColor":
            executeOrQueueRequest(buildColorRequest(params?.color instanceof Map ? params.color as Map : [:]))
            break
        default:
            log.warn "[Gemstone] Unsupported action '${params?.action}'"
    }
}

private Map buildOnStateRequest(Boolean onState, Map followUp = null) {
    [
        method        : "PUT",
        path          : "/deviceControl/onState",
        body          : [onState: onState],
        action        : "command",
        requestLabel  : onState ? "turn on" : "turn off",
        requiresDevice: true,
        allowRetry401 : true,
        followUp      : followUp
    ]
}

private Map buildRefreshRequest() {
    [
        method        : "GET",
        path          : "/deviceControl/currentlyPlaying",
        action        : "refresh",
        requestLabel  : "refresh device state",
        requiresDevice: true,
        allowRetry401 : true
    ]
}

private Map buildEffectCatalogPageRequest(String catalogSource, Integer page) {
    [
        method       : "GET",
        path         : catalogSource == "managed" ? "/downloads/folders/pattern/listGemstoneManaged" : "/folders/pattern/list",
        query        : [page: page],
        action       : "effectCatalogPage",
        requestLabel : "${catalogSource == 'managed' ? 'load built-in effects' : 'load custom effects'} page ${page}",
        allowRetry401: true,
        catalogSource: catalogSource,
        catalogPage  : page
    ]
}

private Map buildLevelRequest(Integer level) {
    Map pattern = currentOrDefaultPattern()
    pattern.brightness = levelToWireBrightness(level)
    rememberPattern(pattern)

    Map request = buildPatternRequest(pattern, "set brightness to ${level}%")
    if (!isSwitchCurrentlyOn()) {
        return buildOnStateRequest(true, request)
    }
    return request
}

private Map buildColorRequest(Map color) {
    Integer hue = clampPercent(color?.hue ?: 0)
    Integer saturation = clampPercent(color?.saturation ?: 100)
    Integer level = clampPercent(color?.level ?: safeInt(device.currentValue("level"), 100))

    Map pattern = currentOrDefaultPattern()
    pattern.name = "Hubitat Solid Color"
    pattern.animation = DEFAULT_PATTERN_ANIMATION
    pattern.colors = [hubitatHueSatToArgb(hue, saturation)]
    pattern.brightness = levelToWireBrightness(level)
    pattern.remove("referencePatternId")
    rememberPattern(pattern)

    Map request = buildPatternRequest(pattern, "set color")
    if (!isSwitchCurrentlyOn()) {
        return buildOnStateRequest(true, request)
    }
    return request
}

private Map buildColorTemperatureRequest(Integer colorTemperature, Integer level) {
    Map pattern = currentOrDefaultPattern()
    pattern.name = buildColorTemperaturePatternName(colorTemperature)
    pattern.animation = DEFAULT_PATTERN_ANIMATION
    pattern.colors = [kelvinToArgb(colorTemperature)]
    pattern.brightness = levelToWireBrightness(level)
    pattern.remove("referencePatternId")
    rememberPattern(pattern)
    state.lastColorTemperature = colorTemperature

    Map request = buildPatternRequest(pattern, "set color temperature to ${colorTemperature}K")
    if (!isSwitchCurrentlyOn()) {
        return buildOnStateRequest(true, request)
    }
    return request
}

private Map buildEffectRequest(Map pattern, String effectName) {
    Map request = buildPatternRequest(pattern, "set effect '${effectName}'")
    if (!isSwitchCurrentlyOn()) {
        return buildOnStateRequest(true, request)
    }
    return request
}

private Map buildPatternRequest(Map pattern, String label) {
    [
        method        : "PUT",
        path          : "/deviceControl/play/pattern",
        body          : [pattern: cloneMap(pattern)],
        action        : "command",
        requestLabel  : label,
        requiresDevice: true,
        allowRetry401 : true
    ]
}

private void executeOrQueueRequest(Map request) {
    if (!credentialsConfigured()) {
        log.error "[Gemstone] Gemstone account email and password are required in Preferences before '${request?.requestLabel ?: 'this command'}' can run."
        updateAuthStatus("Not configured — add email/password")
        return
    }

    if (!hasUsableAccessToken() || (request?.requiresDevice && !state.deviceId)) {
        String reason = !hasUsableAccessToken() ? "no usable access token" : "deviceId not yet discovered"
        log.info "[Gemstone] Queuing '${request?.requestLabel ?: request?.path}' (${reason}) — will dispatch after session setup"
        queueRequest(request)
        continueSessionSetup()
        return
    }

    dispatchApiRequest(request)
}

private void continueSessionSetup() {
    if (!credentialsConfigured()) {
        updateAuthStatus("Not configured — add email/password")
        return
    }

    if (state.authInFlight) {
        return
    }

    if (!hasUsableAccessToken()) {
        if (state.refreshToken) {
            startTokenRefresh("session setup")
        } else {
            startPasswordAuth("session setup")
        }
        return
    }

    if (!state.deviceId) {
        discoverDevice()
        return
    }

    updateAuthenticatedStatus()
    flushPendingRequests()
}

private void flushPendingRequests() {
    if (state.authInFlight || state.discoveryInFlight) {
        return
    }
    if (!hasUsableAccessToken()) {
        continueSessionSetup()
        return
    }
    if (!state.deviceId) {
        discoverDevice()
        return
    }

    List pending = (state.pendingRequests ?: []) as List
    if (!pending) {
        return
    }

    state.pendingRequests = []
    pending.each { request ->
        dispatchApiRequest(request as Map)
    }
}

private void queueRequest(Map request) {
    List pending = (state.pendingRequests ?: []) as List
    pending << cloneMap(request)
    state.pendingRequests = pending
}

private void clearPendingRequests() {
    state.pendingRequests = []
    state.authInFlight = false
    state.discoveryInFlight = false
}

private void requestEffectCatalogRefresh(boolean logAvailableAfterLoad) {
    state.effectCatalogLogAfterLoad = (state.effectCatalogLogAfterLoad == true) || logAvailableAfterLoad

    if (state.effectCatalogRefreshInFlight) {
        debugLog "Effect catalog refresh already in flight"
        return
    }

    state.effectCatalogRefreshInFlight = true
    state.effectCatalogBuildCustom = [:]
    state.effectCatalogBuildManaged = [:]
    executeOrQueueRequest(buildEffectCatalogPageRequest("custom", 1))
}

private void queuePendingEffectRequest(Map request) {
    List pending = (state.pendingEffectRequests ?: []) as List
    pending << cloneMap(request ?: [:])
    state.pendingEffectRequests = pending
}

private void processPendingEffectRequests() {
    List pending = (state.pendingEffectRequests ?: []) as List
    state.pendingEffectRequests = []

    boolean logAvailableAfterLoad = state.effectCatalogLogAfterLoad == true
    state.effectCatalogLogAfterLoad = false
    if (logAvailableAfterLoad) {
        logAvailableEffectNames()
    }

    pending.each { request ->
        processPendingEffectRequest(request as Map)
    }
}

private void processPendingEffectRequest(Map request) {
    switch (safeString(request?.type)) {
        case "name":
            activateEffectByName(safeString(request?.value))
            break
        case "index":
            activateEffectByIndex(safeInt(request?.value, -1))
            break
        case "next":
            cycleEffect(1)
            break
        case "previous":
            cycleEffect(-1)
            break
        default:
            debugLog "Ignoring unknown pending effect request '${request?.type}'"
    }
}

// Resolves any pending "name" requests for non-favorites from the full in-flight catalog data,
// playing them directly without writing to state.effectCatalog or state.effectPatterns.
// Must be called before processPendingEffectRequests() so the local mergedEntries data is available.
private void processPendingNonFavoriteNameRequests(Map mergedEntries, Map favoriteCatalog) {
    List pending = (state.pendingEffectRequests ?: []) as List
    if (!pending) return

    List remainingPending = []
    pending.each { req ->
        Map request = req as Map
        if (safeString(request?.type) != "name") {
            remainingPending << request
            return
        }

        String reqName = safeString(request?.value)
        String normalizedReq = normalizeEffectName(reqName)

        // If the name resolves in the favorites cache, leave it for normal processing.
        boolean isFavorite = favoriteCatalog.keySet().any { candidate ->
            normalizeEffectName(candidate) == normalizedReq
        }
        if (isFavorite) {
            remainingPending << request
            return
        }

        // Not a favorite — look it up in the full mergedEntries (keyed by normalizedName).
        Map foundEntry = mergedEntries[normalizedReq] instanceof Map ? mergedEntries[normalizedReq] as Map : null
        if (foundEntry) {
            String patternId = safeString(foundEntry.patternId)
            String displayName = safeString(foundEntry.displayName) ?: reqName
            Map pattern = foundEntry.pattern instanceof Map ? cloneMap(foundEntry.pattern as Map) : [:]
            if (patternId) {
                log.debug "[Gemstone] Non-favorite '${displayName}' resolved on-demand; playing without caching to state"
                activateEffectWithPattern(patternId, displayName, pattern)
            } else {
                log.error "[Gemstone] No effect named '${reqName}'."
                warnAvailableEffectNames()
            }
        } else {
            log.error "[Gemstone] No effect named '${reqName}'."
            warnAvailableEffectNames()
        }
    }
    state.pendingEffectRequests = remainingPending
}

private void activateEffectByName(String requestedName) {
    String resolvedName = resolveEffectCatalogName(requestedName)
    if (!resolvedName) {
        // Name not in favorites cache — trigger an on-demand full catalog fetch;
        // finalizeEffectCatalogRefresh() will resolve the name from the live response
        // and play it without persisting to state.
        queuePendingEffectRequest([type: "name", value: requestedName])
        requestEffectCatalogRefresh(false)
        return
    }

    String patternId = safeString((state.effectCatalog as Map)[resolvedName])
    activateEffectByPattern(patternId, resolvedName)
}

private void activateEffectByIndex(Integer requestedIndex) {
    if (requestedIndex == null || requestedIndex < 0) {
        log.error "[Gemstone] Effect index '${requestedIndex}' is invalid."
        return
    }

    String patternId = state.effectIndex instanceof Map ? safeString((state.effectIndex as Map)[requestedIndex.toString()]) : ""
    if (!patternId) {
        log.error "[Gemstone] No effect at index ${requestedIndex}. Available indexes: ${availableEffectIndexes().join(', ')}"
        warnAvailableEffectNames()
        return
    }

    String resolvedName = effectNameForPatternId(patternId)
    if (!resolvedName) {
        log.error "[Gemstone] Effect index ${requestedIndex} is missing a cached name. Refreshing the effect catalog."
        queuePendingEffectRequest([type: "index", value: requestedIndex])
        requestEffectCatalogRefresh(false)
        return
    }

    activateEffectByPattern(patternId, resolvedName)
}

private void activateEffectByPattern(String patternId, String resolvedName) {
    Map pattern = effectPatternForId(patternId)
    if (!pattern) {
        queuePendingEffectRequest([type: "name", value: resolvedName])
        requestEffectCatalogRefresh(false)
        return
    }

    activateEffectWithPattern(patternId, resolvedName, pattern)
}

private void activateEffectWithPattern(String patternId, String resolvedName, Map pattern) {
    if (!pattern.name) {
        pattern.name = resolvedName
    }

    Integer effectIndex = effectIndexForPatternId(patternId)
    infoLog "${device.displayName} effect → ${displayEffectName(resolvedName)}"
    rememberPattern(pattern)
    sendEvent(name: "level", value: wireBrightnessToLevel(safeInt(pattern.brightness, 255)), unit: "%", descriptionText: "${device.displayName} level set to ${wireBrightnessToLevel(safeInt(pattern.brightness, 255))}%", type: "digital")
    state.lastOnState = true
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} turned on", type: "digital")
    updateCurrentEffectIndex(effectIndex)
    updateColorMode(COLOR_MODE_EFFECTS)
    executeOrQueueRequest(buildEffectRequest(pattern, resolvedName))
}

private void cycleEffect(Integer direction) {
    List indexes = availableEffectIndexes()
    if (!indexes) {
        log.error "[Gemstone] No effects are loaded. Run refreshEffectCatalog() first."
        return
    }

    Integer currentIndex = currentEffectIndex()
    Integer targetIndex
    if (currentIndex == null || !indexes.contains(currentIndex)) {
        targetIndex = direction >= 0 ? indexes[0] : indexes[-1]
    } else {
        Integer currentPosition = indexes.indexOf(currentIndex)
        Integer nextPosition = (currentPosition + direction) % indexes.size()
        if (nextPosition < 0) {
            nextPosition += indexes.size()
        }
        targetIndex = indexes[nextPosition]
    }

    activateEffectByIndex(targetIndex)
}

private void dispatchApiRequest(Map request) {
    if (!request) {
        return
    }

    if (request.requiresDevice && !state.deviceId) {
        queueRequest(request)
        discoverDevice()
        return
    }

    Map params = buildApiParams(request)
    Map callbackData = cloneMap(request)

    debugLog "Dispatching ${request.method} ${request.path} (${request.requestLabel})"

    switch (safeString(request.method)) {
        case "GET":
            asynchttpGet("apiResponseCallback", params, callbackData)
            break
        case "PUT":
            asynchttpPut("apiResponseCallback", params, callbackData)
            break
        case "POST":
            asynchttpPost("apiResponseCallback", params, callbackData)
            break
        default:
            log.error "[Gemstone] Unsupported HTTP method '${request.method}' for ${request.path}"
    }
}

// ---------------------------------------------------------------------------
// Auth / discovery
// ---------------------------------------------------------------------------

private Map buildCognitoParams(String authFlow, Map authParameters) {
    String bodyJson = serializeJsonBody([
        AuthFlow      : authFlow,
        ClientId      : COGNITO_CLIENT_ID,
        AuthParameters: authParameters
    ])

    return [
        uri               : COGNITO_URL,
        timeout           : safeCognitoTimeout(),
        headers           : [
            "Accept"      : JSON_CONTENT_TYPE,
            "Content-Type": COGNITO_CONTENT_TYPE,
            "X-Amz-Target": COGNITO_TARGET
        ],
        requestContentType: JSON_CONTENT_TYPE,
        contentType       : JSON_CONTENT_TYPE,
        body              : bodyJson
    ]
}

private Map buildCognitoCallbackData(String mode, String reason, String authFlow, Collection authParameterKeys) {
    return [
        mode             : mode,
        reason           : reason,
        authFlow         : authFlow,
        authParameterKeys: authParameterKeys ? (authParameterKeys.collect { key -> safeString(key) } as List) : [],
        contentType      : COGNITO_CONTENT_TYPE,
        amzTarget        : COGNITO_TARGET,
        clientId         : COGNITO_CLIENT_ID
    ]
}

private void startPasswordAuth(String reason) {
    if (state.authInFlight) {
        return
    }
    if (!credentialsConfigured()) {
        log.error "[Gemstone] Gemstone account email and password are required before authentication can start."
        updateAuthStatus("Not configured — add email/password")
        return
    }

    state.authInFlight = true
    updateAuthStatus("Authenticating")

    Map authParameters = [
        USERNAME: safeString(settings.accountEmail).trim(),
        PASSWORD: safeString(settings.accountPassword)
    ]
    Map params = buildCognitoParams("USER_PASSWORD_AUTH", authParameters)

    debugLog "Starting Cognito USER_PASSWORD_AUTH (${reason})"
    asynchttpPost("cognitoAuthCallback", params, buildCognitoCallbackData("login", reason, "USER_PASSWORD_AUTH", authParameters.keySet()))
}

private void startTokenRefresh(String reason) {
    if (state.authInFlight) {
        return
    }
    if (!state.refreshToken) {
        startPasswordAuth("refresh token unavailable (${reason})")
        return
    }

    state.authInFlight = true
    updateAuthStatus("Refreshing token")

    Map authParameters = [
        REFRESH_TOKEN: safeString(state.refreshToken)
    ]
    Map params = buildCognitoParams("REFRESH_TOKEN_AUTH", authParameters)

    debugLog "Refreshing Cognito token (${reason})"
    asynchttpPost("cognitoAuthCallback", params, buildCognitoCallbackData("refresh", reason, "REFRESH_TOKEN_AUTH", authParameters.keySet()))
}

def refreshAccessTokenTask() {
    if (!credentialsConfigured()) {
        return
    }
    if (state.authInFlight) {
        return
    }
    if (state.refreshToken) {
        startTokenRefresh("scheduled refresh")
    } else {
        startPasswordAuth("scheduled login")
    }
}

private void storeTokens(Map authResult, String refreshFallback = null) {
    state.accessToken = safeString(authResult.AccessToken)

    String refreshToken = safeString(authResult.RefreshToken)
    if (!refreshToken && refreshFallback) {
        refreshToken = refreshFallback
    }
    if (refreshToken) {
        state.refreshToken = refreshToken
    }

    Integer expiresIn = safeInt(authResult.ExpiresIn, 3600)
    Long expiresAt = (currentEpochSeconds() + expiresIn) as Long
    state.tokenExpiry = expiresAt
}

private void clearAuthTokens() {
    state.remove("accessToken")
    state.remove("refreshToken")
    state.remove("tokenExpiry")
    unschedule("refreshAccessTokenTask")
}

private void clearDiscoveryState() {
    state.remove("homegroupId")
    state.remove("homegroupName")
    state.remove("deviceId")
    state.remove("deviceName")
    state.remove("lastPattern")
    state.remove("discoveryHomegroups")
}

private void clearEffectCatalogState() {
    state.remove("effectCatalog")
    state.remove("effectPatterns")
    state.remove("favorites")
    state.remove("effectIndex")
    state.remove("effectCatalogFetchedAt")
    state.remove("effectCatalogCollisionWarnings")
    state.remove("pendingEffectSelections")
    state.remove("pendingEffectRequests")
    state.remove("effectCatalogLogAfterLoad")
    clearCurrentEffectIndex()
    updateLightEffectsAttribute([:])
    updateFavoriteEffectsAttribute([])
    clearEffectCatalogRefreshState()
}

private void clearEffectCatalogRefreshState() {
    state.remove("effectCatalogRefreshInFlight")
    state.remove("effectCatalogBuildCustom")
    state.remove("effectCatalogBuildManaged")
}

private void pruneNonFavoriteStateEntries() {
    Map favorites = (state.favorites instanceof Map) ? state.favorites as Map : [:]
    Set favoritePatternIds = favorites.values() as Set
    Set favoriteNames = favorites.keySet() as Set

    if (state.effectCatalog instanceof Map) {
        Map cat = state.effectCatalog as Map
        cat.keySet().retainAll(favoriteNames)
    }
    if (state.effectPatterns instanceof Map) {
        Map pats = state.effectPatterns as Map
        pats.keySet().retainAll(favoritePatternIds)
    }
}

private void discoverDevice() {
    if (state.discoveryInFlight) {
        return
    }
    if (!hasUsableAccessToken()) {
        continueSessionSetup()
        return
    }

    state.discoveryInFlight = true
    state.discoveryHomegroups = []
    debugLog "Discovering Gemstone home groups"
    dispatchApiRequest([
        method       : "GET",
        path         : "/homegroup/list",
        action       : "discoverHomegroups",
        requestLabel : "discover home groups",
        allowRetry401: true
    ])
}

private void handleHomegroupsResponse(Map payload) {
    List groups = payload?.data instanceof List ? payload.data as List : []
    if (!groups) {
        state.discoveryInFlight = false
        state.discoveryHomegroups = []
        clearPendingRequests()
        log.error "[Gemstone] Gemstone authentication succeeded, but no home groups were returned for this account."
        updateAuthStatus("Authenticated — no home groups found")
        return
    }

    List simplified = groups.collect { group ->
        [id: safeString(group?.id), name: safeString(group?.name, "Gemstone Home")]
    }.findAll { it.id }

    if (simplified.size() > 1) {
        log.warn "[Gemstone] Multiple Gemstone home groups were found. This driver will use the first home group that contains a device."
    }

    state.discoveryHomegroups = simplified
    requestNextHomegroupDevices()
}

private void requestNextHomegroupDevices() {
    List remaining = (state.discoveryHomegroups ?: []) as List
    if (!remaining) {
        state.discoveryInFlight = false
        clearPendingRequests()
        log.error "[Gemstone] No Gemstone controllers were found in any discovered home group."
        updateAuthStatus("Authenticated — no devices found")
        return
    }

    Map selectedHomegroup = remaining.remove(0) as Map
    state.discoveryHomegroups = remaining
    state.homegroupId = safeString(selectedHomegroup.id)
    state.homegroupName = safeString(selectedHomegroup.name, "Gemstone Home")

    debugLog "Discovering devices in home group '${state.homegroupName}'"
    dispatchApiRequest([
        method          : "GET",
        path            : "/homegroup/devices",
        action          : "discoverDevices",
        requestLabel    : "discover devices",
        requiresHomegroup: true,
        allowRetry401   : true
    ])
}

private void handleDevicesResponse(Map payload) {
    List devices = payload?.data instanceof List ? payload.data as List : []
    if (!devices) {
        log.warn "[Gemstone] No Gemstone devices were found in home group '${state.homegroupName}'. Trying the next home group, if available."
        requestNextHomegroupDevices()
        return
    }

    state.discoveryInFlight = false
    state.discoveryHomegroups = []

    if (devices.size() > 1) {
        log.warn "[Gemstone] Multiple Gemstone controllers were found in '${state.homegroupName}'. Using the first cloud device for this Hubitat driver."
    }

    Map selectedDevice = devices[0] as Map
    state.deviceId = safeString(selectedDevice?.id)
    state.deviceName = safeString(selectedDevice?.name, device.displayName)

    infoLog "${device.displayName} is bound to Gemstone cloud device '${state.deviceName}'"
    updateAuthenticatedStatus()
    flushPendingRequests()
}

private void handleAuthFailure(String logMessage, String statusValue) {
    clearAuthTokens()
    clearEffectCatalogState()
    clearPendingRequests()
    state.discoveryHomegroups = []
    log.error "[Gemstone] ${logMessage}"
    updateAuthStatus(statusValue)
}

private void updateAuthenticatedStatus() {
    String deviceName = safeString(state.deviceName)
    updateAuthStatus(deviceName ? "Authenticated: ${deviceName}" : "Authenticated")
}

// ---------------------------------------------------------------------------
// Response handling
// ---------------------------------------------------------------------------

private void handleRefreshResponse(Map payload) {
    Map data = payload?.data instanceof Map ? payload.data as Map : [:]
    if (!data) {
        log.error "[Gemstone] Refresh returned an unexpected payload with no 'data' object."
        return
    }

    Boolean onState = data.onState == true
    state.lastOnState = onState
    sendEvent(name: "switch", value: onState ? "on" : "off", descriptionText: "${device.displayName} turned ${onState ? 'on' : 'off'}", type: "digital")

    Map pattern = data.pattern instanceof Map ? data.pattern as Map : null
    if (pattern) {
        Map previousPattern = state.lastPattern instanceof Map ? cloneMap(state.lastPattern as Map) : [:]
        pattern.name = currentEffectName(pattern)
        String inferredColorMode = inferColorMode(pattern, previousPattern)
        Integer inferredColorTemperature = inferredColorMode == COLOR_MODE_CT ? resolvePatternColorTemperature(pattern) : null

        rememberPattern(pattern)

        Integer level = wireBrightnessToLevel(safeInt(pattern.brightness, 255))
        sendEvent(name: "level", value: level, unit: "%", descriptionText: "${device.displayName} level set to ${level}%", type: "digital")

        List colors = pattern.colors instanceof List ? pattern.colors as List : []
        if (colors) {
            Map hs = gemstoneArgbToHubitatColor(safeInt(colors[0], 0))
            sendEvent(name: "hue", value: hs.hue, descriptionText: "${device.displayName} hue set to ${hs.hue}", type: "digital")
            sendEvent(name: "saturation", value: hs.saturation, descriptionText: "${device.displayName} saturation set to ${hs.saturation}", type: "digital")
        }

        if (inferredColorMode) {
            updateColorMode(inferredColorMode)
            if (inferredColorMode == COLOR_MODE_EFFECTS) {
                updateCurrentEffectIndex(effectIndexForPatternId(safeString(pattern.id ?: pattern.patternId)))
            } else {
                clearCurrentEffectIndex()
            }
        }

        if (inferredColorTemperature != null) {
            state.lastColorTemperature = inferredColorTemperature
            updateColorTemperatureAttributes(inferredColorTemperature)
        }
    } else {
        state.remove("lastPattern")
        sendEvent(name: "level", value: onState ? safeInt(device.currentValue("level"), 100) : 0, unit: "%", descriptionText: "${device.displayName} level set to ${onState ? safeInt(device.currentValue('level'), 100) : 0}%", type: "digital")
        updateEffectName("")
        clearCurrentEffectIndex()
    }
}

private void handleEffectCatalogPageResponse(Map payload, Map request) {
    List entries = payload?.data instanceof List ? payload.data as List : []
    String catalogSource = safeString(request?.catalogSource)
    Integer page = safeInt(request?.catalogPage, 1)

    if (entries) {
        mergeEffectCatalogEntries(entries, catalogSource)
        dispatchApiRequest(buildEffectCatalogPageRequest(catalogSource, page + 1))
        return
    }

    if (catalogSource == "custom") {
        dispatchApiRequest(buildEffectCatalogPageRequest("managed", 1))
        return
    }

    finalizeEffectCatalogRefresh()
}

private void mergeEffectCatalogEntries(List entries, String catalogSource) {
    Map target = catalogSource == "managed"
        ? (state.effectCatalogBuildManaged instanceof Map ? state.effectCatalogBuildManaged as Map : [:])
        : (state.effectCatalogBuildCustom instanceof Map ? state.effectCatalogBuildCustom as Map : [:])

    entries.each { entry ->
        Map catalogEntry = compactEffectCatalogEntry(entry as Map, catalogSource)
        if (catalogEntry) {
            target.remove(catalogEntry.normalizedName)
            target[catalogEntry.normalizedName] = catalogEntry
        }
    }

    if (catalogSource == "managed") {
        state.effectCatalogBuildManaged = target
    } else {
        state.effectCatalogBuildCustom = target
    }
}

private Map compactEffectCatalogEntry(Map entry, String catalogSource) {
    Map pattern = entry?.patternData instanceof Map ? cloneMap(entry.patternData as Map) : [:]
    if (!pattern) {
        return [:]
    }

    String displayName = safeString(pattern.name ?: entry?.patternName ?: entry?.name).trim()
    String patternId = safeString(pattern.id ?: pattern.patternId ?: entry?.patternId ?: entry?.referencePatternId).trim()
    if (!displayName || !patternId) {
        return [:]
    }

    pattern.id = patternId
    pattern.name = displayName

    [
        normalizedName: normalizeEffectName(displayName),
        displayName   : displayName,
        patternId     : patternId,
        pattern       : pattern,
        source        : catalogSource,
        favorite      : entry?.isFavorite == true
    ]
}

private void finalizeEffectCatalogRefresh() {
    Map customEntries = state.effectCatalogBuildCustom instanceof Map ? state.effectCatalogBuildCustom as Map : [:]
    Map managedEntries = state.effectCatalogBuildManaged instanceof Map ? state.effectCatalogBuildManaged as Map : [:]
    Map mergedEntries = [:]

    managedEntries.each { normalizedName, entry ->
        mergedEntries[normalizedName] = entry
    }
    customEntries.each { normalizedName, entry ->
        if (managedEntries[normalizedName]) {
            logEffectCatalogCollision(entry as Map)
        }
        mergedEntries[normalizedName] = entry
    }

    Map favorites = [:]
    List favoriteNames = []
    customEntries.each { normalizedName, entry ->
        Map catalogEntry = entry as Map
        if (catalogEntry.favorite == true && mergedEntries[normalizedName]) {
            String displayName = safeString(catalogEntry.displayName)
            String patternId = safeString(catalogEntry.patternId)
            if (displayName && patternId && !favorites.containsKey(displayName)) {
                favorites[displayName] = patternId
                favoriteNames << displayName
            }
        }
    }

    Set favoriteNormalizedNames = favoriteNames.collect { normalizeEffectName(it) } as Set
    List otherEntries = mergedEntries.values().collect { it as Map }.findAll { entry ->
        !favoriteNormalizedNames.contains(safeString(entry.normalizedName))
    }.sort { left, right ->
        safeString(left.displayName).toLowerCase() <=> safeString(right.displayName).toLowerCase()
    }

    Map effectCatalog = [:]
    Map effectPatterns = [:]
    Map effectIndex = [:]
    Map lightEffects = [:]
    Integer otherCount = 0
    Integer nextIndex = 0

    favorites.each { displayName, patternId ->
        Map catalogEntry = mergedEntries[normalizeEffectName(displayName)] as Map
        effectCatalog[displayName] = patternId
        effectPatterns[patternId] = catalogEntry?.pattern instanceof Map ? cloneMap(catalogEntry.pattern as Map) : [:]
        lightEffects[nextIndex.toString()] = displayEffectName(displayName)
        effectIndex[nextIndex.toString()] = patternId
        nextIndex++
    }

    otherEntries.each { entry ->
        String displayName = safeString(entry.displayName)
        if (displayName) {
            otherCount++
        }
    }

    state.favorites = favorites
    state.effectCatalog = effectCatalog
    state.effectPatterns = effectPatterns
    state.effectIndex = effectIndex
    state.effectCatalogFetchedAt = now()
    clearEffectCatalogRefreshState()
    updateLightEffectsAttribute(lightEffects)
    updateFavoriteEffectsAttribute(favoriteNames.collect { displayEffectName(it) })

    Integer count = effectCatalog.size()
    log.debug "[Gemstone] Loaded ${count} effects. Favorites (${favoriteNames.size()}): ${favoriteNames ? favoriteNames.join(', ') : '(none)'}"
    log.debug "[Gemstone] Other patterns: ${otherCount} non-favorite patterns available by name."

    String currentPatternId = safeString(state.lastPattern?.id ?: state.lastPattern?.patternId)
    if (currentPatternId) {
        Integer currentIndex = effectIndexForPatternId(currentPatternId)
        if (currentIndex != null) {
            updateCurrentEffectIndex(currentIndex)
        }
    }

    processPendingNonFavoriteNameRequests(mergedEntries, effectCatalog)
    processPendingEffectRequests()
}

private void handleEffectCatalogRefreshFailure() {
    boolean logAvailableAfterLoad = state.effectCatalogLogAfterLoad == true
    List pending = (state.pendingEffectRequests ?: []) as List

    clearEffectCatalogRefreshState()
    state.pendingEffectRequests = []
    state.effectCatalogLogAfterLoad = false

    if (pending && hasCachedEffectCatalog()) {
        log.info "[Gemstone] Effect catalog refresh failed; falling back to the cached effect catalog for pending effect requests."
        if (logAvailableAfterLoad) {
            logAvailableEffectNames()
        }
        pending.each { request ->
            processPendingEffectRequest(request as Map)
        }
        return
    }

    if (pending) {
        log.error "[Gemstone] Effect catalog refresh failed before pending effect requests could be resolved."
    }
}

private void logEffectCatalogCollision(Map entry) {
    String normalizedName = safeString(entry?.normalizedName)
    if (!normalizedName) {
        return
    }

    List warned = (state.effectCatalogCollisionWarnings ?: []) as List
    if (warned.contains(normalizedName)) {
        return
    }

    warned << normalizedName
    state.effectCatalogCollisionWarnings = warned
    log.info "[Gemstone] Effect name collision for '${entry.displayName}' — preferring the user preset over the built-in pattern."
}

private void cleanupAfterRequestFailure(Map request) {
    if (request?.action in ["discoverHomegroups", "discoverDevices"]) {
        state.discoveryInFlight = false
        state.remove("discoveryHomegroups")
    }
}

// ---------------------------------------------------------------------------
// Scheduling helpers
// ---------------------------------------------------------------------------

private void schedulePolling() {
    switch (safeString(settings.pollInterval, "5")) {
        case "1":
            runEvery1Minute("poll")
            break
        case "5":
            runEvery5Minutes("poll")
            break
        case "10":
            runEvery10Minutes("poll")
            break
        default:
            infoLog "Polling disabled"
    }
}

private void scheduleTokenRefreshIfNeeded() {
    if (!state.refreshToken || !state.tokenExpiry) {
        return
    }
    scheduleTokenRefresh()
}

private void scheduleTokenRefresh() {
    unschedule("refreshAccessTokenTask")

    Long expiresAt = safeLong(state.tokenExpiry, 0L)
    if (!expiresAt || !state.refreshToken) {
        return
    }

    Long nowSeconds = currentEpochSeconds()
    Integer delay = Math.max(5, (expiresAt - nowSeconds - TOKEN_REFRESH_LEEWAY_SECONDS) as Integer)
    runIn(delay, "refreshAccessTokenTask")
    debugLog "Next token refresh scheduled in ${delay} seconds"
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

private String serializeJsonBody(Object payload) {
    return payload instanceof String ? (payload as String) : JsonOutput.toJson(payload)
}

private Map buildApiParams(Map request) {
    Map query = [:]
    if (request.query instanceof Map) {
        query.putAll(request.query as Map)
    }
    if (request.requiresHomegroup) {
        query.homegroupId = safeString(state.homegroupId)
    }
    if (request.requiresDevice) {
        query.deviceOrGroupId = safeString(state.deviceId)
    }

    Map headers = [
        "Accept"       : JSON_CONTENT_TYPE,
        "Authorization": "Bearer ${state.accessToken}",
        "User-Agent"   : USER_AGENT
    ]

    Map params = [
        uri    : buildUrl(API_BASE_URL, safeString(request.path), query),
        timeout: safeRequestTimeout(),
        headers: headers
    ]

    if (request.body != null) {
        String bodyJson = serializeJsonBody(request.body)
        params.requestContentType = JSON_CONTENT_TYPE
        params.contentType = JSON_CONTENT_TYPE
        params.headers["Content-Type"] = JSON_CONTENT_TYPE
        params.body = bodyJson
    }

    return params
}

private String buildUrl(String base, String path, Map query = [:]) {
    String url = "${base}${path}"
    List pairs = query.findAll { it.value != null && it.value.toString() }.collect { key, value ->
        "${URLEncoder.encode(key.toString(), 'UTF-8')}=${URLEncoder.encode(value.toString(), 'UTF-8')}"
    }

    if (pairs) {
        url = "${url}?${pairs.join('&')}"
    }

    return url
}

// ---------------------------------------------------------------------------
// Pattern / color helpers
// ---------------------------------------------------------------------------

private Map currentOrDefaultPattern() {
    Map pattern = state.lastPattern instanceof Map ? cloneMap(state.lastPattern as Map) : [:]
    if (!pattern) {
        Integer hue = clampPercent(safeInt(device.currentValue("hue"), 0))
        Integer saturation = clampPercent(safeInt(device.currentValue("saturation"), 0))
        pattern = [
            id                : generatePatternId(),
            name              : "Hubitat Solid Color",
            colors            : [hubitatHueSatToArgb(hue, saturation)],
            animation         : DEFAULT_PATTERN_ANIMATION,
            brightness        : levelToWireBrightness(safeInt(device.currentValue("level"), 100)),
            speed             : DEFAULT_PATTERN_SPEED,
            direction         : DEFAULT_PATTERN_DIRECTION,
            backgroundColor   : 0,
            referencePatternId: null
        ]
    }

    if (!pattern.id) {
        pattern.id = generatePatternId()
    }
    if (!pattern.name) {
        pattern.name = "Hubitat Pattern"
    }
    if (!(pattern.colors instanceof List) || (pattern.colors as List).isEmpty()) {
        pattern.colors = [hubitatHueSatToArgb(clampPercent(safeInt(device.currentValue("hue"), 0)), clampPercent(safeInt(device.currentValue("saturation"), 0)))]
    }
    if (!pattern.animation) {
        pattern.animation = DEFAULT_PATTERN_ANIMATION
    }
    if (pattern.speed == null) {
        pattern.speed = DEFAULT_PATTERN_SPEED
    }
    if (pattern.direction == null) {
        pattern.direction = DEFAULT_PATTERN_DIRECTION
    }
    if (pattern.backgroundColor == null) {
        pattern.backgroundColor = 0
    }
    if (!pattern.containsKey("referencePatternId")) {
        pattern.referencePatternId = null
    }

    return pattern
}

private String generatePatternId() {
    Integer nextSequence = safeInt(state.patternIdSequence, 0) + 1
    state.patternIdSequence = nextSequence
    return "hubitat-${now()}-${nextSequence}"
}

private void rememberPattern(Map pattern) {
    state.lastPattern = cloneMap(pattern)
    if (pattern?.name) {
        updateEffectName(safeString(pattern.name))
    }
}

private void updateEffectName(String value) {
    String safeValue = safeString(value)
    if (safeString(device.currentValue("effectName")) != safeValue) {
        sendEvent(name: "effectName", value: safeValue, descriptionText: "${device.displayName} effect → ${safeValue}", type: "digital")
    }
}

private void updateColorMode(String value) {
    String safeValue = safeString(value)
    state.lastColorMode = safeValue
    if (safeValue && safeString(device.currentValue("colorMode")) != safeValue) {
        sendEvent(name: "colorMode", value: safeValue, descriptionText: "${device.displayName} color mode → ${safeValue}", type: "digital")
    }
}

private void updateColorTemperatureAttributes(Integer colorTemperature) {
    Integer kelvin = clampColorTemperature(colorTemperature)
    if (safeInt(device.currentValue("colorTemperature"), 0) != kelvin) {
        sendEvent(name: "colorTemperature", value: kelvin, unit: "K", descriptionText: "${device.displayName} color temperature set to ${kelvin}K", type: "digital")
    }

    String colorName = colorTemperatureName(kelvin)
    if (safeString(device.currentValue("colorName")) != colorName) {
        sendEvent(name: "colorName", value: colorName, descriptionText: "${device.displayName} color name set to ${colorName}", type: "digital")
    }
}

private void updateLightEffectsAttribute(Map lightEffectsMap) {
    String lightEffectsJson = JsonOutput.toJson(lightEffectsMap ?: [:])
    if (safeString(device.currentValue("lightEffects")) != lightEffectsJson) {
        sendEvent(name: "lightEffects", value: lightEffectsJson, descriptionText: "${device.displayName} effects catalog refreshed (${lightEffectsMap?.size() ?: 0} entries)", type: "digital")
    }
}

private void updateFavoriteEffectsAttribute(List favoriteNames) {
    String favoriteValue = favoriteNames ? favoriteNames.join(", ") : ""
    if (safeString(device.currentValue("favoriteEffects")) != favoriteValue) {
        sendEvent(name: "favoriteEffects", value: favoriteValue, descriptionText: "${device.displayName} favorite effects updated", type: "digital")
    }
}

private void updateCurrentEffectIndex(Integer index) {
    if (index == null || index < 0) {
        clearCurrentEffectIndex()
        return
    }
    state.currentEffectIndex = index
}

private void clearCurrentEffectIndex() {
    state.remove("currentEffectIndex")
}

private String currentEffectName(Map pattern) {
    String patternId = safeString(pattern?.id ?: pattern?.patternId)
    String cachedName = effectNameForPatternId(patternId)
    if (cachedName) {
        return cachedName
    }

    return safeString(pattern?.name ?: pattern?.animation ?: "")
}

private boolean hasCachedEffectCatalog() {
    return state.effectCatalog instanceof Map && !(state.effectCatalog as Map).isEmpty()
}

private boolean effectCatalogMissing() {
    return !hasCachedEffectCatalog()
}

private boolean effectCatalogStale() {
    Long fetchedAt = safeLong(state.effectCatalogFetchedAt, 0L)
    return !fetchedAt || ((now() - fetchedAt) >= EFFECT_CATALOG_TTL_MILLIS)
}

private Map effectPatternForId(String patternId) {
    if (!patternId || !(state.effectPatterns instanceof Map)) {
        return [:]
    }

    Map pattern = (state.effectPatterns as Map)[patternId] instanceof Map ? (state.effectPatterns as Map)[patternId] as Map : [:]
    return pattern ? cloneMap(pattern) : [:]
}

private String effectNameForPatternId(String patternId) {
    if (!patternId || !(state.effectCatalog instanceof Map)) {
        return ""
    }

    return (state.effectCatalog as Map).find { entry ->
        safeString(entry.value) == patternId
    }?.key as String ?: ""
}

private Integer effectIndexForPatternId(String patternId) {
    if (!patternId || !(state.effectIndex instanceof Map)) {
        return null
    }

    String indexValue = (state.effectIndex as Map).find { entry ->
        safeString(entry.value) == patternId
    }?.key as String

    return indexValue ? safeInt(indexValue, -1) : null
}

private Integer currentEffectIndex() {
    Integer cachedIndex = safeInt(state.currentEffectIndex, -1)
    if (cachedIndex >= 0 && state.effectIndex instanceof Map && (state.effectIndex as Map).containsKey(cachedIndex.toString())) {
        return cachedIndex
    }

    String patternId = safeString(state.lastPattern?.id ?: state.lastPattern?.patternId)
    Integer resolvedIndex = effectIndexForPatternId(patternId)
    if (resolvedIndex != null && resolvedIndex >= 0) {
        state.currentEffectIndex = resolvedIndex
        return resolvedIndex
    }

    return null
}

private String resolveEffectCatalogName(String requestedName) {
    String normalizedRequestedName = normalizeEffectName(requestedName)
    if (!normalizedRequestedName || !(state.effectCatalog instanceof Map)) {
        return ""
    }

    return ((state.effectCatalog as Map).keySet() as Collection).find { candidate ->
        normalizeEffectName(candidate) == normalizedRequestedName
    } as String ?: ""
}

private String normalizeEffectName(value) {
    return stripEffectPrefix(value).trim().replaceAll(/\s+/, " ").toLowerCase()
}

private String stripEffectPrefix(value) {
    String name = safeString(value).trim()
    return name.replaceFirst(/^[⭐★☆]\s*/, "")
}

private String displayEffectName(String effectName) {
    String rawName = stripEffectPrefix(effectName)
    if (!rawName) {
        return ""
    }
    return isFavoriteEffect(rawName) ? FAVORITE_EFFECT_PREFIX + rawName : rawName
}

private boolean isFavoriteEffect(String effectName) {
    String rawName = stripEffectPrefix(effectName)
    return rawName && state.favorites instanceof Map && (state.favorites as Map).containsKey(rawName)
}

private List orderedEffectDisplayNames() {
    if (!(state.effectCatalog instanceof Map)) {
        return []
    }

    return ((state.effectCatalog as Map).keySet() as List).collect { name ->
        displayEffectName(safeString(name))
    }
}

private List availableEffectIndexes() {
    if (!(state.effectIndex instanceof Map)) {
        return []
    }

    return ((state.effectIndex as Map).keySet() as List)
        .collect { key -> safeInt(key, -1) }
        .findAll { it >= 0 }
        .sort()
}

private void logAvailableEffectNames() {
    List names = orderedEffectDisplayNames()
    log.debug "[Gemstone] Available effects: ${names ? names.join(', ') : '(none loaded)'}"
}

private void warnAvailableEffectNames() {
    List names = orderedEffectDisplayNames().sort()
    Integer total = names.size()
    List shown = total > 20 ? names.take(20) : names
    String suffix = total > 20 ? ", … (${total - 20} more)" : ""
    log.warn "[Gemstone] Available effects: ${shown ? shown.join(', ') + suffix : '(none loaded)'}"
}

private String inferColorMode(Map pattern, Map previousPattern = [:]) {
    String patternId = safeString(pattern?.id ?: pattern?.patternId)
    String patternName = safeString(pattern?.name)
    List colors = pattern?.colors instanceof List ? pattern.colors as List : []

    if (effectNameForPatternId(patternId)) {
        return COLOR_MODE_EFFECTS
    }
    if (resolveEffectCatalogName(patternName)) {
        return COLOR_MODE_EFFECTS
    }
    if (safeString(pattern?.referencePatternId)) {
        return COLOR_MODE_EFFECTS
    }
    if (safeString(pattern?.animation) && safeString(pattern?.animation) != DEFAULT_PATTERN_ANIMATION) {
        return COLOR_MODE_EFFECTS
    }
    if (colors.size() > 1) {
        return COLOR_MODE_EFFECTS
    }
    if (isColorTemperaturePattern(pattern, previousPattern)) {
        return COLOR_MODE_CT
    }
    return COLOR_MODE_RGB
}

private boolean isColorTemperaturePattern(Map pattern, Map previousPattern = [:]) {
    String patternName = safeString(pattern?.name)
    if (patternName.startsWith(CT_PATTERN_NAME_PREFIX)) {
        return true
    }

    String patternId = safeString(pattern?.id ?: pattern?.patternId)
    String previousPatternId = safeString(previousPattern?.id ?: previousPattern?.patternId)
    return safeString(state.lastColorMode) == COLOR_MODE_CT &&
        patternId &&
        patternId == previousPatternId &&
        safeString(pattern?.animation) == DEFAULT_PATTERN_ANIMATION &&
        pattern?.colors instanceof List &&
        (pattern.colors as List).size() == 1
}

private Integer resolvePatternColorTemperature(Map pattern) {
    Integer fromPatternName = extractColorTemperatureFromPatternName(safeString(pattern?.name))
    if (fromPatternName != null) {
        return fromPatternName
    }

    Integer lastKelvin = safeInt(state.lastColorTemperature, 0)
    return lastKelvin > 0 ? clampColorTemperature(lastKelvin) : null
}

private Integer extractColorTemperatureFromPatternName(String patternName) {
    def matcher = safeString(patternName) =~ /(\d{4,5})K/
    if (matcher.find()) {
        return clampColorTemperature(safeInt(matcher.group(1), 0))
    }
    return null
}

private String buildColorTemperaturePatternName(Integer colorTemperature) {
    return "${CT_PATTERN_NAME_PREFIX} ${clampColorTemperature(colorTemperature)}K"
}

private Integer clampColorTemperature(value) {
    Integer kelvin = safeInt(value, 3000)
    return Math.max(2200, Math.min(9000, kelvin))
}

private String colorTemperatureName(Integer colorTemperature) {
    Integer kelvin = clampColorTemperature(colorTemperature)
    if (kelvin < 3000) {
        return "Warm White"
    }
    if (kelvin < 4000) {
        return "White"
    }
    if (kelvin < 6500) {
        return "Cool White"
    }
    return "Daylight"
}

private Long kelvinToArgb(Integer colorTemperature) {
    // NOTE: Despite the historical name, this produces an ABGR-packed Long for the
    // Gemstone cloud API wire format (A in high byte, then B, G, R). Confirmed
    // by v0.4.5 empirical testing on a real Gemstone controller.
    Double temperature = clampColorTemperature(colorTemperature) / 100.0d
    Double red
    Double green
    Double blue

    if (temperature <= 66.0d) {
        red = 255.0d
    } else {
        red = 329.698727446d * Math.pow(temperature - 60.0d, -0.1332047592d)
    }

    if (temperature <= 66.0d) {
        green = 99.4708025861d * Math.log(temperature) - 161.1195681661d
    } else {
        green = 288.1221695283d * Math.pow(temperature - 60.0d, -0.0755148492d)
    }

    if (temperature >= 66.0d) {
        blue = 255.0d
    } else if (temperature <= 19.0d) {
        blue = 0.0d
    } else {
        blue = 138.5177312231d * Math.log(temperature - 10.0d) - 305.0447927307d
    }

    Integer redValue = clampByte(Math.round(red) as Integer)
    Integer greenValue = clampByte(Math.round(green) as Integer)
    Integer blueValue = clampByte(Math.round(blue) as Integer)
    return ((0xFFL << 24) | ((blueValue & 0xFFL) << 16) | ((greenValue & 0xFFL) << 8) | (redValue & 0xFFL)) as Long
}

private Integer clampByte(Integer value) {
    return Math.max(0, Math.min(255, safeInt(value, 0)))
}

private void warnColorTemperatureFallback() {
    if (state.colorTemperatureFallbackWarned == true) {
        return
    }

    state.colorTemperatureFallbackWarned = true
    log.warn "[Gemstone] Gemstone's cloud API spec exposes no native CCT endpoint. setColorTemperature() is using an RGB white-spectrum fallback via /deviceControl/play/pattern."
}

private Integer levelToWireBrightness(Integer level) {
    Integer clamped = clampPercent(level)
    return Math.round((clamped * 255.0d) / 100.0d) as Integer
}

private Integer wireBrightnessToLevel(Integer brightness) {
    Integer clamped = Math.max(0, Math.min(255, safeInt(brightness, 0)))
    return Math.round((clamped * 100.0d) / 255.0d) as Integer
}

private Long hubitatHueSatToArgb(Integer huePercent, Integer saturationPercent) {
    // NOTE: Despite the historical name, this produces an ABGR-packed Long for the
    // Gemstone cloud API wire format (A in high byte, then B, G, R). Confirmed
    // by v0.4.5 empirical testing on a real Gemstone controller.
    float h = (clampPercent(huePercent) / 100.0f) * 360.0f
    float s = clampPercent(saturationPercent) / 100.0f
    float c = s
    float x = c * (1.0f - Math.abs(((h / 60.0f) % 2.0f) - 1.0f))
    float m = 1.0f - c

    float rf = 0.0f
    float gf = 0.0f
    float bf = 0.0f

    if (h < 60.0f) {
        rf = c; gf = x; bf = 0.0f
    } else if (h < 120.0f) {
        rf = x; gf = c; bf = 0.0f
    } else if (h < 180.0f) {
        rf = 0.0f; gf = c; bf = x
    } else if (h < 240.0f) {
        rf = 0.0f; gf = x; bf = c
    } else if (h < 300.0f) {
        rf = x; gf = 0.0f; bf = c
    } else {
        rf = c; gf = 0.0f; bf = x
    }

    Integer r = Math.round((rf + m) * 255.0f) as Integer
    Integer g = Math.round((gf + m) * 255.0f) as Integer
    Integer b = Math.round((bf + m) * 255.0f) as Integer

    return ((0xFFL << 24) | ((b & 0xFFL) << 16) | ((g & 0xFFL) << 8) | (r & 0xFFL)) as Long
}

private Map gemstoneArgbToHubitatColor(Number argb) {
    // NOTE: Despite the historical name, this produces an ABGR-packed Long for the
    // Gemstone cloud API wire format (A in high byte, then B, G, R). Confirmed
    // by v0.4.5 empirical testing on a real Gemstone controller.
    long argbLong = (argb instanceof Long) ? (long) argb : ((argb ?: 0) as Long)
    // Wire format is ABGR: high byte alpha, then B (>>16), G (>>8), R (low). See setter notes above.
    float b = ((argbLong >> 16) & 0xFFL) / 255.0f
    float g = ((argbLong >> 8)  & 0xFFL) / 255.0f
    float r = (argbLong         & 0xFFL) / 255.0f

    float max = [r, g, b].max() as float
    float min = [r, g, b].min() as float
    float delta = max - min

    float hueDegrees = 0.0f
    if (delta != 0.0f) {
        if (max == r) {
            hueDegrees = 60.0f * (((g - b) / delta) % 6.0f)
        } else if (max == g) {
            hueDegrees = 60.0f * (((b - r) / delta) + 2.0f)
        } else {
            hueDegrees = 60.0f * (((r - g) / delta) + 4.0f)
        }
    }

    if (hueDegrees < 0.0f) {
        hueDegrees += 360.0f
    }

    float saturation = max == 0.0f ? 0.0f : (delta / max)

    [
        hue       : clampPercent(Math.round((hueDegrees / 360.0f) * 100.0f) as Integer),
        saturation: clampPercent(Math.round(saturation * 100.0f) as Integer)
    ]
}

// ---------------------------------------------------------------------------
// State / response helpers
// ---------------------------------------------------------------------------

private boolean credentialsConfigured() {
    return safeString(settings.accountEmail).trim() && safeString(settings.accountPassword)
}

private boolean hasUsableAccessToken() {
    return safeString(state.accessToken) && !tokenExpiresSoon()
}

private boolean tokenExpiresSoon() {
    Long expiresAt = safeLong(state.tokenExpiry, 0L)
    if (!expiresAt) {
        return true
    }
    Long nowSeconds = currentEpochSeconds()
    return (expiresAt - TOKEN_REFRESH_LEEWAY_SECONDS) <= nowSeconds
}

private Long currentEpochSeconds() {
    return Math.round(now() / 1000.0d) as Long
}

private boolean isSwitchCurrentlyOn() {
    if (state.lastOnState != null) {
        return state.lastOnState == true
    }
    return safeString(device.currentValue("switch")) == "on"
}

private Integer safeRequestTimeout() {
    Integer timeout = safeInt(settings.requestTimeout, DEFAULT_TIMEOUT_SECONDS)
    return Math.max(5, Math.min(60, timeout))
}

private Integer safeCognitoTimeout() {
    return Math.max(COGNITO_TIMEOUT_SECONDS, safeRequestTimeout())
}

private Integer clampPercent(value) {
    Integer intValue = safeInt(value, 0)
    return Math.max(0, Math.min(100, intValue))
}

private Integer safeInt(value, Integer fallback = 0) {
    try {
        return value != null ? (value as Integer) : fallback
    } catch (ignored) {
        return fallback
    }
}

private Long safeLong(value, Long fallback = 0L) {
    try {
        return value != null ? (value as Long) : fallback
    } catch (ignored) {
        return fallback
    }
}

private String safeString(value, String fallback = "") {
    return value == null ? fallback : value.toString()
}

private Map cloneMap(Map source) {
    if (!source) {
        return [:]
    }
    return new JsonSlurper().parseText(JsonOutput.toJson(source)) as Map
}

private Integer responseStatus(response) {
    try {
        return response?.getStatus() as Integer
    } catch (ignored) {
        try {
            return response?.status as Integer
        } catch (ignoredAgain) {
            return null
        }
    }
}

private boolean responseHasError(response) {
    try {
        return response?.hasError() == true
    } catch (ignored) {
        try {
            return response?.hasError == true
        } catch (ignoredAgain) {
            return false
        }
    }
}

private String responseErrorMessage(response) {
    try {
        return safeString(response?.getErrorMessage())
    } catch (ignored) {
        try {
            return safeString(response?.errorMessage)
        } catch (ignoredAgain) {
            return ""
        }
    }
}

private Object responseHeaders(response) {
    try {
        return response?.getHeaders()
    } catch (ignored) {
        try {
            return response?.headers
        } catch (ignoredAgain) {
            return null
        }
    }
}

private Object responseRawData(response) {
    try {
        return response?.getData()
    } catch (ignored) {
        try {
            return response?.data
        } catch (ignoredAgain) {
            return null
        }
    }
}

private Object responseErrorJson(response) {
    try {
        return response?.getErrorJson()
    } catch (ignored) {
        try {
            return response?.errorJson
        } catch (ignoredAgain) {
            return null
        }
    }
}

private String responseBody(response) {
    Object rawData = responseRawData(response)
    return rawData == null ? "" : safeString(rawData)
}

private Map responseJson(response) {
    try {
        def parsed = response?.getJson()
        if (parsed instanceof Map) {
            return parsed as Map
        }
    } catch (ignored) {
    }

    try {
        if (response?.json instanceof Map) {
            return response.json as Map
        }
    } catch (ignoredAgain) {
    }

    String body = responseBody(response)
    if (!body) {
        return [:]
    }

    try {
        def parsed = new JsonSlurper().parseText(body)
        return parsed instanceof Map ? parsed as Map : [:]
    } catch (ignoredAgain) {
        return [:]
    }
}

private String extractServiceMessage(String body) {
    if (!body) {
        return ""
    }

    try {
        def parsed = new JsonSlurper().parseText(body)
        if (parsed instanceof Map) {
            return safeString(parsed.message ?: parsed.Message ?: parsed.error ?: parsed.__type ?: body)
        }
    } catch (ignored) {
    }

    return body
}

private String buildAuthFailureMessage(Integer status, String responseMessage) {
    if (responseMessage?.toLowerCase()?.contains("incorrect username or password")) {
        return "Cognito auth failed. Check the email/password preferences and try again."
    }
    if (responseMessage?.toLowerCase()?.contains("user_password_auth")) {
        return "Cognito rejected USER_PASSWORD_AUTH. This v0.4.0 driver does not implement SRP fallback."
    }
    if (status == 408) {
        return "Cognito auth timed out while contacting AWS. See diagnostic log lines above."
    }
    return "Cognito auth failed (HTTP ${status}${responseMessage ? ": ${responseMessage}" : ""})."
}

private String authStatusValueForAuthFailure(String responseMessage) {
    if (responseMessage?.toLowerCase()?.contains("incorrect username or password")) {
        return "Auth failed — check email/password"
    }
    if (responseMessage?.toLowerCase()?.contains("user_password_auth")) {
        return "Auth failed — USER_PASSWORD_AUTH rejected"
    }
    return "Auth failed — see logs"
}

private String buildNetworkFailureMessage(String context, String responseMessage) {
    return buildNetworkFailureMessage(context, responseMessage, safeRequestTimeout())
}

private String buildNetworkFailureMessage(String context, String responseMessage, Integer timeoutSeconds) {
    String detail = responseMessage ?: "no response details"
    String lowered = detail.toLowerCase()
    if (lowered.contains("no encoder found for request content type")) {
        return "${context} could not be sent because Hubitat rejected the request body content type before contacting AWS."
    }
    if (lowered.contains("timeout") || lowered.contains("timed out")) {
        return "${context} timed out after ${timeoutSeconds} seconds. Check internet connectivity or increase the timeout preference."
    }
    return "${context} failed: ${detail}"
}

private void logCognitoAuthFailure(response, Map data) {
    String ctType = safeString(data?.contentType, COGNITO_CONTENT_TYPE)
    String amzTarget = safeString(data?.amzTarget, COGNITO_TARGET)
    String authFlow = safeString(data?.authFlow)
    String clientId = safeString(data?.clientId, COGNITO_CLIENT_ID)
    String authParameters = formatAuthParameterShape(data?.authParameterKeys)

    log.error "[Gemstone] Cognito auth failed."
    log.error "  Request:  POST ${COGNITO_URL}"
    log.error "  Headers:  Content-Type=${ctType}, X-Amz-Target=${amzTarget}"
    log.error "  Body shape: AuthParameters=${authParameters}, AuthFlow=${authFlow}, ClientId=${truncateClientIdForLog(clientId)}"
    log.error "  resp.hasError=${responseHasError(response)}  resp.status=${responseStatus(response)}  resp.errorMessage=${redactSensitiveText(responseErrorMessage(response))}"
    log.error "  resp.headers=${formatLogValue(responseHeaders(response))}"
    log.error "  resp.data (raw)=${formatLogValue(responseRawData(response))}"
    log.error "  resp.errorJson=${formatLogValue(responseErrorJson(response))}"
}

private String formatAuthParameterShape(def authParameterKeys) {
    List keys = authParameterKeys instanceof Collection ? (authParameterKeys as Collection).collect { key -> safeString(key) } : []
    return "{${keys.join(', ')}}"
}

private String truncateClientIdForLog(String clientId) {
    String safeClientId = safeString(clientId)
    return safeClientId ? "${safeClientId.take(8)}..." : "unknown"
}

private String formatLogValue(value) {
    Object sanitized = sanitizeLogValue(value)
    if (sanitized == null) {
        return "null"
    }
    if (sanitized instanceof Map || sanitized instanceof List) {
        try {
            return JsonOutput.toJson(sanitized)
        } catch (ignored) {
            return sanitized.toString()
        }
    }
    return safeString(sanitized)
}

private Object sanitizeLogValue(value) {
    if (value instanceof Map) {
        Map sanitized = [:]
        (value as Map).each { key, item ->
            String safeKey = safeString(key)
            sanitized[safeKey] = isSensitiveLogKey(safeKey) ? "<redacted>" : sanitizeLogValue(item)
        }
        return sanitized
    }
    if (value instanceof List) {
        return (value as List).collect { item -> sanitizeLogValue(item) }
    }
    return redactSensitiveText(value)
}

private boolean isSensitiveLogKey(String key) {
    String normalized = safeString(key).replaceAll(/[^A-Za-z0-9]/, "").toLowerCase()
    return normalized in [
        "password",
        "accesstoken",
        "idtoken",
        "refreshtoken",
        "secrethash",
        "authorization",
        "mgmtkey",
        "xauthkey",
        "xapitoken",
        "xapikey"
    ]
}

private String redactSensitiveText(value) {
    String text = safeString(value)
    if (!text) {
        return text
    }

    text = text.replaceAll(/(?i)("(?:password|accessToken|idToken|refreshToken|secret_hash|secretHash|authorization|x_authkey|x_api_token|mgmtKey)"\s*:\s*")[^"]*(")/, '$1<redacted>$2')
    text = text.replaceAll(/(?i)('(?:password|accessToken|idToken|refreshToken|secret_hash|secretHash|authorization|x_authkey|x_api_token|mgmtKey)'\s*:\s*')[^']*(')/, '$1<redacted>$2')
    text = text.replaceAll(/(?i)\b(password|accessToken|idToken|refreshToken|secret_hash|secretHash|authorization|x_authkey|x_api_token|mgmtKey)\b\s*:\s*([^,\]\}\s]+)/, '$1:<redacted>')
    return text
}

private String truncate(String text, Integer maxLength = 160) {
    String safe = safeString(text)
    if (safe.size() <= maxLength) {
        return safe
    }
    return safe.substring(0, maxLength) + "…"
}

private void updateAuthStatus(String value) {
    if (safeString(device.currentValue("authStatus")) != value) {
        sendEvent(name: "authStatus", value: value, descriptionText: "${device.displayName} auth status → ${value}", type: "digital")
    }
}

// ---------------------------------------------------------------------------
// Logging helpers
// ---------------------------------------------------------------------------

private void debugLog(String msg) {
    if (settings.logEnable) {
        log.debug "[${device.displayName}] ${msg}"
    }
}

private void infoLog(String msg) {
    if (settings.txtEnable) {
        log.info "[${device.displayName}] ${msg}"
    }
}
