---
name: "hubitat-cloud-oauth-app"
description: "Template for a Hubitat App + child Driver pair that performs OAuth2 authorization code flow against a cloud API, discovers appliances, and exposes per-appliance children as ContactSensor (or other capability)."
domain: "groovy"
confidence: "medium"
source: "earned — Bosch Home Connect architecture design (trinity-bosch-home-connect-architecture.md)"
---

## Context

Use this skill when a Hubitat integration needs **OAuth2 authorization code flow** (browser redirect, not ROPC/refresh-token-only). Unlike SunStat (ROPC flow handled entirely in a Driver), OAuth2 code flow requires a `mappings {}` block — which is only available in a **Hubitat App**, not a Driver.

## Architecture

```
Hubitat App (one app instance)
├── Holds: accessToken, refreshToken, tokenExpiresAt  in state.*
├── mappings { path("/oauth/initialize") { action: [GET: "oauthInitialize"] }
│             path("/oauth/callback")   { action: [GET: "oauthCallback"]   } }
├── oauthInitialize(): createAccessToken() → redirect to cloud auth URL
├── oauthCallback(): exchange code → store tokens → discoverDevices()
├── discoverDevices(): GET /api/devices → addChildDevice() per appliance feature
├── poll(): GET /api/devices/{id}/status → child.parseDeviceState(data)
└── Token refresh: proactiveTokenRefresh() via runIn before expiry

Child Driver (one per physical device feature — e.g., one per door zone)
├── Capabilities: ContactSensor, Sensor (or other as needed)
├── DataValue: cloudApplianceId
├── DataValue: deviceZone  (e.g. "refrigerator" | "freezer")
└── parseDeviceState(Map data) → sendEvent contact open/closed
```

## OAuth Flow Implementation

```groovy
// In App metadata:
oauthPage()  // Hubitat built-in — enables OAuth for this app

mappings {
    path("/oauth/initialize") { action: [GET: "oauthInitialize"] }
    path("/oauth/callback")   { action: [GET: "oauthCallback"]   }
}

def oauthInitialize() {
    def state = UUID.randomUUID().toString()
    atomicState.oauthState = state
    String callbackUri = URLEncoder.encode("${getApiServerUrl()}/apps/api/${app.id}/oauth/callback?access_token=${state.accessToken}", "UTF-8")
    String authUrl = "${CLOUD_AUTH_URL}?client_id=${CLIENT_ID}&response_type=code&redirect_uri=${callbackUri}&scope=${SCOPE}&state=${state}"
    redirect(location: authUrl)
}

def oauthCallback() {
    String code  = params.code
    String st    = params.state
    if (st != atomicState.oauthState) { render contentType: "text/plain", data: "State mismatch — possible CSRF"; return }
    exchangeCodeForTokens(code)
    render contentType: "text/html", data: "<html><body><h1>Authorized! Return to Hubitat.</h1></body></html>"
}

private void exchangeCodeForTokens(String code) {
    // Standard authorization_code exchange
    httpPost([uri: TOKEN_URL, requestContentType: "application/x-www-form-urlencoded",
              body: "grant_type=authorization_code&code=${code}&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}&redirect_uri=${callbackUri}"]) { resp ->
        state.accessToken    = resp.data.access_token
        state.refreshToken   = resp.data.refresh_token
        state.tokenExpiresAt = now() + (resp.data.expires_in as Long) * 1000
    }
    discoverDevices()
}
```

## Child Creation Pattern (per feature/zone)

```groovy
DEVICE_ZONES.each { zone ->
    String dni = "${DRIVER_PREFIX}-${applianceId}-${zone}"
    def existing = getChildDevice(dni)
    if (!existing) {
        def child = addChildDevice("mads", "My Cloud Child Sensor", dni,
                                   [name: "${displayName} ${zone.capitalize()}", isComponent: false])
        child.updateDataValue("cloudApplianceId", applianceId)
        child.updateDataValue("deviceZone", zone)
    }
}
```

## Key Rules

1. **Apps get `mappings {}`, Drivers do not.** Any cloud OAuth2 code-flow driver MUST be implemented as a Hubitat App.
2. **One child per discrete sensor zone** — not one child with multiple custom attributes. Rule Machine and built-in notification apps target devices, not custom attributes.
3. **Verify CSRF state parameter** in `oauthCallback()` — `atomicState` survives the redirect round-trip; regular `state` may not.
4. **Redirect URI must be pre-registered with the cloud provider** — Hubitat's cloud endpoint is per-hub; check whether the provider allows wildcard subpaths or requires exact match.
5. **Token refresh** via `runIn(secondsUntilExpiry - TOKEN_REFRESH_LEEWAY, "proactiveTokenRefresh")` — same pattern as SunStat, just triggered from `exchangeCodeForTokens()` and after every token refresh.

## Redirect URI Note

Hubitat Cloud Endpoint callback URL format:
```
https://cloud.hubitat.com/api/{hub-id}/apps/{app-id}/oauth/callback
```
Hub ID changes per hub installation. For providers that require exact URI pre-registration (e.g. Bosch Home Connect), this is a blocker — research whether they support wildcard or if the community has a workaround (proxy, static relay URL).

## Examples

- Architecture design: `.squad/decisions/inbox/trinity-bosch-home-connect-architecture.md`
- Closest working analog: `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy` (ROPC variant — no redirect, but all other patterns identical)

## Anti-Patterns

- Putting `mappings {}` in a Driver — not supported by Hubitat platform.
- Using `state` (not `atomicState`) for the OAuth CSRF state parameter — state may not survive the redirect.
- One device with two custom door attributes — breaks Rule Machine and notification apps.
- Forgetting to rotate the refresh token after each token refresh call (if the provider rotates).
