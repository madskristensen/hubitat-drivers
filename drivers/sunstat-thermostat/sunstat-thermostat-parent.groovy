/**
 * SunStat Connect Plus — Parent Driver
 * Author:  Mads Kristensen
 * Version: 0.1.11
 * License: MIT
 *
 * Token-based device discovery, polling, and command routing for the
 * Watts® Home API (home.watts.com). Creates one child device per thermostat
 * found in the Watts account.
 *
 * Bootstrap: run the homebridge-tekmar-wifi CLI once externally to capture
 * your initial refresh_token, then paste the token into the setRefreshToken
 * command on the parent device. The driver rotates the token automatically after that.
 *
 * Changelog:
 *   0.1.11 — 2026-05-18 — add Polling + Actuator capability markers for parent discovery and command contract
 *   0.1.10 — 2026-05-18 — version synced to child (v0.1.10); no parent behavior change
 *   0.1.9 — 2026-05-18 — throttle lastActivity emit + child fan-out to ≥60s; cuts unchanged-event DB churn on polls (perf audit fix #1)
 *   0.1.8 — 2026-05-18 — skip redundant PATCH calls when device already matches (audit SP-1, SC-1, SC-2, SC-3); SC-4 deferred
 *   0.1.7 — 2026-05-17 — lastActivity attribute (ISO 8601 timestamp of last successful API call)
 *   0.1.6 — 2026-05-17 — Pseudo-boost implementation in child driver (driver-managed temporary setpoint override; no native boost API)
 *   0.1.5 — 2026-05-17 — Async polling fan-out (asynchttpGet/Patch replaces blocking httpGet/Patch on poll + patch paths); proactive token refresh rescheduled on initialize() when token is already valid; HTTP 429 warn-and-skip handling; child version synced to parent
 *   0.1.4 — 2026-05-16 — Fix API envelope unwrapping ({errorNumber, errorMessage, body} not unwrapped — caused "Could not resolve a Watts location ID"); URL-encode locationId in URL paths (Watts uses display names like "Misty Gray" as locationIds — spaces broke URL parsing); add diagnostic info logging in discovery
 *   0.1.3 — 2026-05-16 — Replace password preference with setRefreshToken() command to bypass Hubitat's ~1024-char preference limit; existing users with token already in state are unaffected
 *   0.1.2 — 2026-05-16 — Energy reporting, schedule toggle, hold attribute, outdoor temperature, setpoint stepping, floor bounds clamping
 *   0.1.1 — 2026-05-16 — Home/Away mode (location-level): setHome, setAway, setAwayMode; awayMode + locationSupportsAway attributes; location state polled each cycle
 *   0.1.0 — 2026-05-16 — Initial release
 */

import groovy.transform.Field
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// ---------------------------------------------------------------------------
// Constants — all literals; NO cross-@Field references (Hubitat sandbox rule)
// ---------------------------------------------------------------------------

@Field static final String DRIVER_VERSION               = "0.1.11"
@Field static final String USER_AGENT                   = "Hubitat SunStat Connect Plus/0.1.11"
@Field static final String WATTS_API_BASE               = "https://home.watts.com/api"
@Field static final String WATTS_TOKEN_URL              = "https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/token"
@Field static final String WATTS_CLIENT_ID              = "c832c38c-ce70-4ebc-83b6-b4548083ac90"
@Field static final String WATTS_SCOPE_ENCODED          = "https%3A%2F%2Fwattsb2cap02.onmicrosoft.com%2Fwattsapiresi%2Fmanage%20offline_access%20openid%20profile"
@Field static final String WATTS_API_VERSION            = "2.0"
@Field static final Integer DEFAULT_TIMEOUT_SECONDS     = 30
@Field static final Integer TOKEN_REFRESH_LEEWAY_SECONDS = 300
@Field static final Long FLOOR_PROBE_DISCONNECTED_THRESHOLD_F = 110L

metadata {
    definition(
        name:      "SunStat Connect Plus",
        namespace: "mads",
        author:    "Mads Kristensen",
        importUrl: "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy"
    ) {
        capability "Actuator"
        capability "Refresh"
        capability "Polling"
        capability "Initialize"

        command "discoverDevices"
        command "setHome"
        command "setAway"
        command "setAwayMode", [[name: "mode", type: "ENUM", constraints: ["home", "away"]]]
        command "setRefreshToken", [[name: "token*", type: "STRING",
            description: "Paste your full Watts Home refresh token here (from homebridge-tekmar-wifi CLI). ~1660 chars."]]

        attribute "awayMode",             "enum", ["home", "away", "unsupported", "unknown"]
        attribute "locationSupportsAway", "enum", ["true", "false"]
        attribute "lastActivity",         "string"
    }

    preferences {
        input name: "locationId", type: "text",
              title: "Watts Home location ID (optional)",
              description: "Leave blank to auto-discover. Useful only if you have multiple locations.",
              required: false

        input name: "pollInterval", type: "enum",
              title: "Polling interval",
              options: ["1": "1 minute", "5": "5 minutes (recommended)", "10": "10 minutes", "0": "Disabled"],
              defaultValue: "5", required: true

        input name: "requestTimeout", type: "number",
              title: "HTTPS request timeout (seconds)",
              defaultValue: 30, range: "5..60", required: true

        input name: "logEnable", type: "bool",
              title: "Enable debug logging (auto-off after 30 minutes)",
              defaultValue: false

        input name: "txtEnable", type: "bool",
              title: "Enable descriptionText (info) logging",
              defaultValue: true
    }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

def installed() {
    log.info "SunStat Connect Plus v" + DRIVER_VERSION + " installed"
    device.updateSetting("pollInterval",     [value: "5",  type: "enum"])
    device.updateSetting("requestTimeout",   [value: 30,   type: "number"])
    device.updateSetting("logEnable",        [value: false, type: "bool"])
    device.updateSetting("txtEnable",        [value: true,  type: "bool"])
    initialize()
}

def updated() {
    log.info "SunStat Connect Plus v" + DRIVER_VERSION + " preferences updated"
    unschedule()

    // Clear auth state so we pick up any new refresh token immediately
    state.remove("accessToken")
    state.remove("tokenExpiresAt")

    if (settings.logEnable) {
        runIn(1800, "logsOff")
        debugLog "Debug logging enabled; will auto-disable in 30 minutes"
    }
    initialize()
}

def initialize() {
    debugLog "initialize() called"
    unschedule("poll")
    unschedule("proactiveTokenRefresh")

    if (!tokenBootstrapReady()) {
        infoLog "${device.displayName} is waiting for a Watts Home refresh token — run the setRefreshToken command on this device"
        return
    }

    schedulePolling()
    // Reschedule proactive refresh if a valid token is already in state (e.g., after hub reboot)
    if (hasUsableToken()) {
        scheduleProactiveRefresh()
    }
    runIn(2, "refresh")
}

def uninstalled() {
    unschedule()
    getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }
    debugLog "uninstalled() — all child devices removed"
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "SunStat Connect Plus: debug logging auto-disabled after 30 minutes"
}

// ---------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------

def refresh() {
    debugLog "refresh() — polling all child devices"
    if (!ensureValidToken()) {
        return
    }
    // Refresh location state (home/away) before polling devices
    String locId = safeStr(state.locationId)
    if (locId && state.locationSupportsAway != false) {
        fetchAndParseLocationState(locId)
    }
    getChildDevices()?.each { child ->
        String deviceId = child.getDataValue("wattsDeviceId")
        if (!deviceId) {
            log.warn "[SunStat] Child ${child.displayName} has no wattsDeviceId — skipping poll"
            return
        }
        pollChildDevice(child, deviceId)
    }
}

def poll() {
    refresh()
}

def discoverDevices() {
    infoLog "${device.displayName} — starting device discovery"
    if (!ensureValidToken()) {
        log.warn "[SunStat] discoverDevices() skipped — token unavailable"
        return
    }
    runDiscovery()
}

def setHome() { setAwayMode("home") }
def setAway() { setAwayMode("away") }

def setAwayMode(String mode) {
    setAwayModeInternal(mode, true)
}

def setRefreshToken(String token) {
    String t = safeStr(token)?.trim() ?: ""
    if (t.size() < 100) {
        log.warn "[SunStat] setRefreshToken: token too short (${t.size()} chars) — paste the full refresh token from homebridge-tekmar-wifi (typically ~1660 chars)"
        return
    }
    log.info "[SunStat] Refresh token stored (${t.size()} chars); clearing cached access token and (re)initializing"
    state.refreshToken = t
    state.remove("accessToken")
    state.remove("tokenExpiresAt")
    initialize()
}

// ---------------------------------------------------------------------------
// Home / Away (location-level)
// ---------------------------------------------------------------------------

private void setAwayModeInternal(String mode, boolean retry401) {
    if (mode != "home" && mode != "away") {
        log.warn "[SunStat] setAwayMode: invalid mode '${mode}' — must be 'home' or 'away'"
        return
    }
    String locId = safeStr(state.locationId)
    if (!locId) {
        log.warn "[SunStat] setAwayMode: no locationId in state — run Discover Devices first"
        return
    }
    if (state.locationSupportsAway == false) {
        log.warn "[SunStat] setAwayMode: this location does not support away mode — command ignored"
        return
    }
    if (!ensureValidToken()) {
        log.warn "[SunStat] setAwayMode skipped — token unavailable"
        return
    }
    String currentAway = safeStr(device.currentValue("awayMode"))
    if (currentAway != null && currentAway == mode) {
        debugLog "setAwayModeInternal: already '${mode}' — skipping PATCH"
        return
    }
    // Optimistic update — Watts app reconciles on next poll if the call fails
    sendEvent(name: "awayMode", value: mode,
              descriptionText: "${device.displayName} away mode is ${mode}")
    int awayState = mode == "away" ? 1 : 0
    String body = JsonOutput.toJson([awayState: awayState])
    String encodedLocId = encodePathSegment(locId)
    Map params = buildApiParams("PATCH", "/Location/${encodedLocId}/State", body)
    try {
        httpMethod("PATCH", params) { resp ->
            Integer status = safeStatus(resp)
            debugLog "PATCH /Location/${encodedLocId}/State → HTTP ${status}"
            if (status == 401 && retry401) {
                log.warn "[SunStat] setAwayMode PATCH 401 — refreshing token and retrying once"
                if (refreshTokensSync()) {
                    setAwayModeInternal(mode, false)
                }
            } else if (status >= 200 && status < 300) {
                Map respBody = parseResponseBody(resp)
                if (respBody) {
                    parseLocationState(respBody)
                }
                infoLog "${device.displayName} away mode set to '${mode}'"
            } else if (status >= 400) {
                log.error "[SunStat] PATCH /Location/${encodedLocId}/State failed: HTTP ${status} — optimistic state may be stale"
            }
        }
    } catch (Exception e) {
        log.error "[SunStat] setAwayMode exception: ${e.message}"
    }
}

// ---------------------------------------------------------------------------
// Public helpers called by child drivers
// ---------------------------------------------------------------------------

/**
 * Called by child via parent.sendDevicePatch(deviceId, settingsMap).
 * Sends PATCH /api/Device/{deviceId} with {"Settings": settingsMap}.
 * Retries once on 401.
 */
def sendDevicePatch(String deviceId, Map settingsMap) {
    if (!ensureValidToken()) {
        log.warn "[SunStat] sendDevicePatch() skipped — token unavailable"
        return
    }
    String body = JsonOutput.toJson([Settings: settingsMap])
    Map params = buildApiParams("PATCH", "/Device/${deviceId}", body)
    asynchttpPatch("sendDevicePatchCallback", params, [deviceId: deviceId, settingsMap: settingsMap, retry401: true])
}

// ---------------------------------------------------------------------------
// Device discovery
// ---------------------------------------------------------------------------

private void runDiscovery() {
    // Step 1: GET /api/User to get defaultLocationId
    String userUrl = WATTS_API_BASE + "/User"
    Map userParams = buildApiParams("GET", "/User", null)
    try {
        httpGet(userParams) { resp ->
            Integer status = safeStatus(resp)
            if (status != 200) {
                log.error "[SunStat] GET /User failed: HTTP ${status}"
                return
            }
            Map body = parseResponseBody(resp)
            String userId           = safeStr(body?.userId)
            String defaultLocId     = safeStr(body?.defaultLocationId)
            String measurementScale = safeStr(body?.measurementScale)
            log.info "[SunStat] GET /User → userId=${userId}, defaultLocationId=${defaultLocId}, scale=${measurementScale}"
            if (userId) {
                state.userId = userId
            }
            if (measurementScale) {
                state.measurementScale = measurementScale
            }

            // Step 2: resolve locationId
            String resolvedLocationId = safeStr(settings.locationId).trim() ?: defaultLocId
            if (!resolvedLocationId) {
                resolvedLocationId = fetchFirstLocationId()
            }
            if (!resolvedLocationId) {
                log.info "[SunStat] locationId resolution: settings='${safeStr(settings.locationId)}', defaultFromUser='${defaultLocId}', fetchFirstResult='${resolvedLocationId}'"
                log.error "[SunStat] Could not resolve a Watts location ID. Set one in preferences."
                return
            }
            state.locationId = resolvedLocationId
            fetchAndParseLocationState(resolvedLocationId)
            discoverDevicesAtLocation(resolvedLocationId)
        }
    } catch (Exception e) {
        log.error "[SunStat] GET /User exception: ${e.message}"
    }
}

private String fetchFirstLocationId() {
    String result = null
    Map params = buildApiParams("GET", "/Location", null)
    try {
        httpGet(params) { resp ->
            Integer status = safeStatus(resp)
            if (status == 200) {
                List locations = parseResponseList(resp)
                log.info "[SunStat] GET /Location returned ${locations.size()} location(s)"
                if (locations) {
                    result = safeStr(locations[0]?.locationId)
                    log.info "[SunStat] Auto-selected locationId: ${result}"
                } else {
                    log.warn "[SunStat] GET /Location returned empty list — check account has at least one location"
                }
            } else {
                log.error "[SunStat] GET /Location failed: HTTP ${status}"
            }
        }
    } catch (Exception e) {
        log.error "[SunStat] GET /Location exception: ${e.message}"
    }
    return result
}

private void discoverDevicesAtLocation(String locationId) {
    String encodedLocId = encodePathSegment(locationId)
    Map params = buildApiParams("GET", "/Location/${encodedLocId}/Devices", null)
    try {
        httpGet(params) { resp ->
            Integer status = safeStatus(resp)
            if (status != 200) {
                log.error "[SunStat] GET /Location/${encodedLocId}/Devices failed: HTTP ${status}"
                return
            }
            List devices = parseResponseList(resp)
            int created = 0
            int updated = 0
            devices.each { dev ->
                Map d = dev as Map
                if (safeStr(d?.deviceType) == "Thermostat") {
                    String devId    = safeStr(d?.deviceId)
                    String devName  = safeStr(d?.name) ?: "SunStat Thermostat"
                    String dni      = "sunstat-${devId}"
                    if (!devId) {
                        return
                    }
                    def existing = getChildDevice(dni)
                    if (!existing) {
                        def child = addChildDevice("mads", "SunStat Connect Plus Thermostat", dni,
                                                   [name: devName, isComponent: false])
                        child.updateDataValue("wattsDeviceId", devId)
                        child.updateDataValue("locationId",    locationId)
                        debugLog "Created child device: ${devName} (${devId})"
                        created++
                    } else {
                        existing.updateDataValue("wattsDeviceId", devId)
                        existing.updateDataValue("locationId",    locationId)
                        if (existing.label != devName) {
                            existing.setLabel(devName)
                        }
                        updated++
                    }
                }
            }
            infoLog "Discovered ${created + updated} thermostat(s) — ${created} created, ${updated} updated"
        }
    } catch (Exception e) {
        log.error "[SunStat] GET /Location/${encodedLocId}/Devices exception: ${e.message}"
    }
}

// ---------------------------------------------------------------------------
// Location state (home / away)
// ---------------------------------------------------------------------------

/**
 * GET /api/Location (returns array), find the entry matching locId, and
 * hand off to parseLocationState. Called during discovery and on each poll.
 */
private void fetchAndParseLocationState(String locId) {
    Map params = buildApiParams("GET", "/Location", null)
    asynchttpGet("fetchLocationStateCallback", params, [locId: locId, retry401: true])
}

/**
 * Reads awayState, supportsAway, locationId, and name from a location map
 * (one entry from GET /api/Location or the body of PATCH /Location/{id}/State).
 * Updates state.* and emits corresponding attribute events.
 */
private void parseLocationState(Map loc) {
    if (!loc) { return }

    String locId = safeStr(loc?.locationId)
    if (locId) {
        state.locationId = locId
    }

    boolean supportsAway = loc?.supportsAway == true
    state.locationSupportsAway = supportsAway
    String supportsVal = supportsAway ? "true" : "false"
    String currentSupports = safeStr(device.currentValue("locationSupportsAway"))
    if (currentSupports != supportsVal) {
        sendEvent(name: "locationSupportsAway", value: supportsVal,
                  descriptionText: "${device.displayName} locationSupportsAway is ${supportsVal}")
    }

    if (!supportsAway) {
        String currentMode = safeStr(device.currentValue("awayMode"))
        if (currentMode != "unsupported") {
            sendEvent(name: "awayMode", value: "unsupported",
                      descriptionText: "${device.displayName} location does not support away mode")
            infoLog "${device.displayName} location does not support away mode"
        }
        state.awayState = 0
        return
    }

    Integer awayStateVal = safeInt(loc?.awayState, 0)
    state.awayState = awayStateVal
    String modeStr = awayStateVal == 1 ? "away" : "home"
    String currentMode = safeStr(device.currentValue("awayMode"))
    if (currentMode != modeStr) {
        sendEvent(name: "awayMode", value: modeStr,
                  descriptionText: "${device.displayName} away mode is ${modeStr}")
        infoLog "${device.displayName} away mode is ${modeStr}"
    }
}

// ---------------------------------------------------------------------------
// Per-device polling
// ---------------------------------------------------------------------------

private void pollChildDevice(child, String deviceId) {
    Map params = buildApiParams("GET", "/Device/${deviceId}", null)
    asynchttpGet("pollChildDeviceCallback", params, [childDni: child.deviceNetworkId, deviceId: deviceId, retry401: true])
}

// ---------------------------------------------------------------------------
// Async HTTP callbacks
// ---------------------------------------------------------------------------

void pollChildDeviceCallback(response, Map data) {
    String childDni = safeStr(data?.childDni)
    String deviceId = safeStr(data?.deviceId)
    boolean retry401 = data?.retry401 != false

    if (response.hasError()) {
        log.warn "[SunStat] GET /Device/${deviceId} error: ${response.errorMessage} — leaving child state as-is"
        return
    }
    Integer status = safeStatus(response)
    if (status == 429) {
        rateLimit429Warn("GET /Device/${deviceId}")
        return
    }
    if (status == 401 && retry401) {
        log.warn "[SunStat] GET /Device/${deviceId} 401 — refreshing token and retrying once"
        if (throttled401Refresh()) {
            Map retryParams = buildApiParams("GET", "/Device/${deviceId}", null)
            asynchttpGet("pollChildDeviceCallback", retryParams, [childDni: childDni, deviceId: deviceId, retry401: false])
        }
        return
    }
    if (status != 200) {
        log.warn "[SunStat] GET /Device/${deviceId} failed: HTTP ${status} — leaving child state as-is"
        return
    }
    Map body = parseResponseBodyFromString(response.data)
    if (!body) {
        log.warn "[SunStat] GET /Device/${deviceId} returned empty body"
        return
    }
    debugLog "Received state for device ${deviceId}: isConnected=${body?.isConnected}"
    def child = getChildDevice(childDni)
    if (!child) {
        log.warn "[SunStat] pollChildDeviceCallback: child device not found for DNI ${childDni}"
        return
    }
    touchActivity()
    child.parseDeviceState(body)
}

void fetchLocationStateCallback(response, Map data) {
    String locId = safeStr(data?.locId)
    boolean retry401 = data?.retry401 != false

    if (response.hasError()) {
        log.warn "[SunStat] GET /Location error: ${response.errorMessage} — location away state unchanged"
        return
    }
    Integer status = safeStatus(response)
    if (status == 429) {
        rateLimit429Warn("GET /Location")
        return
    }
    if (status == 401 && retry401) {
        log.warn "[SunStat] GET /Location 401 — refreshing token and retrying once"
        if (throttled401Refresh()) {
            Map retryParams = buildApiParams("GET", "/Location", null)
            asynchttpGet("fetchLocationStateCallback", retryParams, [locId: locId, retry401: false])
        }
        return
    }
    if (status != 200) {
        log.warn "[SunStat] GET /Location failed: HTTP ${status} — location away state unchanged"
        return
    }
    List locations = parseResponseListFromString(response.data)
    Map loc = locations.find { safeStr(it?.locationId) == locId } as Map
    if (loc) {
        touchActivity()
        parseLocationState(loc)
    } else {
        debugLog "fetchLocationStateCallback: locId ${locId} not found in /Location response"
    }
}

void sendDevicePatchCallback(response, Map data) {
    String deviceId = safeStr(data?.deviceId)
    boolean retry401 = data?.retry401 != false

    if (response.hasError()) {
        log.error "[SunStat] PATCH /Device/${deviceId} error: ${response.errorMessage}"
        return
    }
    Integer status = safeStatus(response)
    debugLog "PATCH /Device/${deviceId} → HTTP ${status}"
    if (status == 429) {
        rateLimit429Warn("PATCH /Device/${deviceId}")
        return
    }
    if (status == 401 && retry401) {
        log.warn "[SunStat] PATCH /Device/${deviceId} 401 — refreshing token and retrying once"
        if (throttled401Refresh()) {
            Map sm = data?.settingsMap as Map ?: [:]
            String retryBody = JsonOutput.toJson([Settings: sm])
            Map retryParams = buildApiParams("PATCH", "/Device/${deviceId}", retryBody)
            asynchttpPatch("sendDevicePatchCallback", retryParams, [deviceId: deviceId, settingsMap: sm, retry401: false])
        }
        return
    }
    if (status >= 400) {
        log.error "[SunStat] PATCH /Device/${deviceId} failed: HTTP ${status}"
    }
}

// ---------------------------------------------------------------------------
// Token management
// ---------------------------------------------------------------------------

private boolean ensureValidToken() {
    if (hasUsableToken()) {
        return true
    }
    return refreshTokensSync()
}

private boolean hasUsableToken() {
    if (!state.accessToken) {
        return false
    }
    Long expiresAt = safeLong(state.tokenExpiresAt, 0L)
    if (!expiresAt) {
        return false
    }
    Long nowSecs = currentEpochSeconds()
    return (expiresAt - TOKEN_REFRESH_LEEWAY_SECONDS) > nowSecs
}

/**
 * Synchronous token refresh. Returns true on success, false on failure.
 * Uses httpPost so the caller can wait for the result inline.
 * NOTE: Watts token endpoint uses application/x-www-form-urlencoded — safe
 * to pass as requestContentType because it's one of Hubitat's three encoders.
 */
private boolean refreshTokensSync() {
    String rt = safeStr(state.refreshToken)
    if (!rt) {
        log.error "[SunStat] No refresh token available — run the setRefreshToken command on the parent device"
        return false
    }

    debugLog "Refreshing Watts Home access token"
    String bodyStr = "client_id=" + WATTS_CLIENT_ID +
                     "&grant_type=refresh_token" +
                     "&refresh_token=" + URLEncoder.encode(rt, "UTF-8") +
                     "&client_info=1" +
                     "&scope=" + WATTS_SCOPE_ENCODED

    Map params = [
        uri                : WATTS_TOKEN_URL,
        contentType        : "application/json",
        requestContentType : "application/x-www-form-urlencoded",
        timeout            : safeRequestTimeout(),
        body               : bodyStr,
        headers            : [
            "User-Agent"    : USER_AGENT,
            "x-client-sku"  : "MSAL.Xamarin.iOS",
            "x-client-ver"  : "4.66.1.0"
        ]
    ]

    boolean success = false
    try {
        httpPost(params) { resp ->
            Integer status = safeStatus(resp)
            if (status == 200) {
                Map data = resp.data instanceof Map ? resp.data as Map : [:]
                String newAccess  = safeStr(data.access_token)
                String newRefresh = safeStr(data.refresh_token)
                Long   expiresOn  = safeLong(data.expires_on, 0L)
                if (!newAccess) {
                    log.error "[SunStat] Token refresh returned 200 but no access_token"
                    return
                }
                state.accessToken    = newAccess
                // Refresh tokens rotate — ALWAYS persist the new one
                if (newRefresh) {
                    state.refreshToken   = newRefresh
                }
                state.tokenExpiresAt = expiresOn ?: (currentEpochSeconds() + 900L)
                scheduleProactiveRefresh()
                touchActivity()
                debugLog "Token refreshed; expires at epoch ${state.tokenExpiresAt}"
                success = true
            } else {
                log.error "[SunStat] Token refresh failed: HTTP ${status}"
            }
        }
    } catch (Exception e) {
        log.error "[SunStat] Token refresh exception: ${e.message}"
    }
    return success
}

def proactiveTokenRefresh() {
    debugLog "proactiveTokenRefresh() scheduled task running"
    refreshTokensSync()
}

private void scheduleProactiveRefresh() {
    unschedule("proactiveTokenRefresh")
    Long expiresAt = safeLong(state.tokenExpiresAt, 0L)
    if (!expiresAt) {
        return
    }
    Long nowSecs = currentEpochSeconds()
    Integer delay = Math.max(30, (expiresAt - nowSecs - TOKEN_REFRESH_LEEWAY_SECONDS) as Integer)
    runIn(delay, "proactiveTokenRefresh")
    debugLog "Proactive token refresh scheduled in ${delay} seconds"
}

// ---------------------------------------------------------------------------
// Health tracking
// ---------------------------------------------------------------------------

private void touchActivity() {
    // Throttle to ≥60s between emissions: lastActivity is a coarse "last successful
    // API call" timestamp; emitting it on every poll/refresh produces ~1440 events/day
    // per device with no information gain. The 60s floor preserves at-a-glance
    // freshness without filling Hubitat's event-history DB.
    Long lastEmittedAt = (state.lastActivityEmittedAt ?: 0L) as Long
    if ((now() - lastEmittedAt) < 60000L) {
        return
    }
    state.lastActivityEmittedAt = now()
    String ts = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")
    sendEvent(name: "lastActivity", value: ts, descriptionText: "${device.displayName} last activity")
    childDevices.each { child ->
        child.setLastActivity(ts)
    }
}

// ---------------------------------------------------------------------------
// Scheduling
// ---------------------------------------------------------------------------

private void schedulePolling() {
    switch (safeStr(settings.pollInterval, "5")) {
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

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

private Map buildApiParams(String method, String path, String jsonBody) {
    Map headers = [
        "Authorization" : "Bearer ${state.accessToken ?: ''}",
        "Api-Version"   : WATTS_API_VERSION,
        "Content-Type"  : "application/json",
        "User-Agent"    : USER_AGENT
    ]

    Map params = [
        uri     : WATTS_API_BASE + path,
        headers : headers,
        timeout : safeRequestTimeout()
    ]

    if (jsonBody != null) {
        params.contentType        = "application/json"
        params.requestContentType = "application/json"
        params.body               = jsonBody
    } else {
        params.contentType = "application/json"
    }
    return params
}

/**
 * Shim to call httpPatch (Hubitat exposes it as httpMethod for PATCH).
 */
private void httpMethod(String method, Map params, Closure callback) {
    switch (method) {
        case "PATCH":
            httpPatch(params, callback)
            break
        case "GET":
            httpGet(params, callback)
            break
        case "POST":
            httpPost(params, callback)
            break
        default:
            log.error "[SunStat] Unsupported HTTP method: ${method}"
    }
}

private Map parseResponseBodyFromString(String s) {
    try {
        if (!s) { return [:] }
        def data = new JsonSlurper().parseText(s)
        if (data instanceof Map) {
            Map m = data as Map
            if (m.containsKey("body") && m.body instanceof Map) {
                return m.body as Map
            }
            return m
        }
    } catch (Exception e) {
        debugLog "parseResponseBodyFromString exception: ${e.message}"
    }
    return [:]
}

private List parseResponseListFromString(String s) {
    try {
        if (!s) { return [] }
        def data = new JsonSlurper().parseText(s)
        if (data instanceof Map) {
            Map m = data as Map
            if (m.containsKey("body") && m.body instanceof List) {
                return m.body as List
            }
        }
        if (data instanceof List) {
            return data as List
        }
    } catch (Exception e) {
        debugLog "parseResponseListFromString exception: ${e.message}"
    }
    return []
}

/**
 * Rate-limited 401 recovery: refreshes tokens at most once per 60 seconds.
 * Returns true if refresh succeeded; false if throttled or refresh failed.
 */
private boolean throttled401Refresh() {
    Long now = currentEpochSeconds()
    Long last = safeLong(state.last401RefreshAt, 0L)
    if ((now - last) < 60L) {
        debugLog "401 token refresh throttled — last refresh was ${now - last}s ago"
        return false
    }
    state.last401RefreshAt = now
    return refreshTokensSync()
}

/**
 * Log a 429 rate-limit warning at most once per 60-second window.
 */
private void rateLimit429Warn(String endpoint) {
    Long now = currentEpochSeconds()
    Long last = safeLong(state.last429WarnAt, 0L)
    if ((now - last) >= 60L) {
        log.warn "[SunStat] HTTP 429 (rate-limited) on ${endpoint} — skipping this cycle"
        state.last429WarnAt = now
    }
}

private Map parseResponseBody(resp) {
    try {
        def data = resp?.data
        if (data instanceof String) {
            data = new JsonSlurper().parseText(data as String)
        }
        if (data instanceof Map) {
            Map m = data as Map
            // Unwrap ApiResponse envelope: { errorNumber, errorMessage, body: {...} }
            if (m.containsKey("body") && m.body instanceof Map) {
                return m.body as Map
            }
            return m
        }
    } catch (Exception e) {
        debugLog "parseResponseBody exception: ${e.message}"
    }
    return [:]
}

private List parseResponseList(resp) {
    try {
        def data = resp?.data
        if (data instanceof String) {
            data = new JsonSlurper().parseText(data as String)
        }
        // Unwrap ApiResponse envelope: { errorNumber, errorMessage, body: [...] }
        if (data instanceof Map) {
            Map m = data as Map
            if (m.containsKey("body") && m.body instanceof List) {
                return m.body as List
            }
        }
        if (data instanceof List) {
            return data as List
        }
    } catch (Exception e) {
        debugLog "parseResponseList exception: ${e.message}"
    }
    return []
}

private String encodePathSegment(String s) {
    return URLEncoder.encode(safeStr(s) ?: "", "UTF-8").replace("+", "%20")
}

// ---------------------------------------------------------------------------
// State / token helpers
// ---------------------------------------------------------------------------

private boolean tokenBootstrapReady() {
    return safeStr(state.refreshToken).size() > 0
}

private Long currentEpochSeconds() {
    return Math.round(now() / 1000.0d) as Long
}

private Integer safeRequestTimeout() {
    Integer t = safeInt(settings.requestTimeout, DEFAULT_TIMEOUT_SECONDS)
    return Math.max(5, Math.min(60, t))
}

private Integer safeStatus(resp) {
    try {
        return resp?.getStatus() as Integer
    } catch (ignored) {
        try {
            return resp?.status as Integer
        } catch (ignoredAgain) {
            return 0
        }
    }
}

private String safeStr(value, String fallback = "") {
    return value == null ? fallback : value.toString()
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

// ---------------------------------------------------------------------------
// Logging helpers
// ---------------------------------------------------------------------------

private void debugLog(String msg) {
    if (settings.logEnable) {
        log.debug "[SunStat] ${msg}"
    }
}

private void infoLog(String msg) {
    if (settings.txtEnable) {
        log.info "[SunStat] ${msg}"
    }
}
