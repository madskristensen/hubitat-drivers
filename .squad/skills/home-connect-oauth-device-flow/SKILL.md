---
name: "home-connect-oauth-device-flow"
description: "OAuth2 Device Flow for Bosch Home Connect API — app registration, Device Flow auth (no redirect URI), token refresh, rate limits, and fridge door event keys."
domain: "protocol"
confidence: "high"
source: "earned — Bosch Home Connect feasibility research, 2026-05-17"
---

## Context

Use this skill when implementing a Hubitat driver for **Bosch/Siemens Home Connect** appliances. Home Connect uses standard OAuth2 with a **Device Flow** variant that requires no redirect URI — ideal for Hubitat where hosting a callback URL is complex.

## App Registration (User Must Do This)

Every Home Connect integration requires a developer-registered application. No shared/embedded client_id is possible.

1. User creates account at https://developer.home-connect.com
2. Registers a new application → gets `client_id` + `client_secret`
3. Enters both in the Hubitat driver preferences (`clientId` plain text, `clientSecret` password)

**This is a harder UX requirement than SunStat** where the client_id is embedded in the driver.

## Base URLs

| Environment | Base URL |
|---|---|
| Production | `https://api.home-connect.com` |
| Simulator | `https://simulator.home-connect.com` |

## Auth Constants

```
TOKEN_URL = https://api.home-connect.com/security/oauth/token
DEVICE_AUTH_URL = https://api.home-connect.com/security/oauth/device_authorization
```

## Token Lifetimes

- **Access token:** 86400 seconds (24 hours) — much simpler than SunStat's 15-min tokens
- **Refresh token:** Long-lived (months). Treat as rotating — always persist the response's `refresh_token` on each refresh call.

## Device Flow — Complete Sequence

### Step 1: Initiate

```
POST https://api.home-connect.com/security/oauth/device_authorization
Content-Type: application/x-www-form-urlencoded

client_id={client_id}&scope=IdentifyAppliance%20FridgeFreezer-Monitor
```

Response:
```json
{
  "device_code": "Ag_EE...3NuE",
  "user_code": "WDJB-MJHT",
  "verification_uri_complete": "https://api.home-connect.com/security/oauth/device?user_code=WDJB-MJHT",
  "expires_in": 300,
  "interval": 5
}
```

### Step 2: Display to User

Log the URL to the Hubitat device page so the user can open it on their phone/PC.

### Step 3: Poll for Token (Groovy pattern)

```groovy
// Store: state.deviceCode, state.deviceFlowInterval = 5
// Schedule: runIn(state.deviceFlowInterval, "checkDeviceFlowToken")

private void checkDeviceFlowToken() {
    String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code" +
                  "&device_code=${state.deviceCode}" +
                  "&client_id=${settings.clientId}"
    try {
        httpPost([uri: TOKEN_URL, requestContentType: "application/x-www-form-urlencoded",
                  contentType: "application/json", body: body]) { resp ->
            if (resp.status == 200) {
                state.accessToken    = resp.data.access_token
                state.refreshToken   = resp.data.refresh_token
                state.tokenExpiresAt = (now() / 1000L) + resp.data.expires_in
                sendEvent(name: "authStatus", value: "authorized")
                runIn(2, "discoverAppliances")
            }
        }
    } catch (e) {
        String err = e?.response?.data?.error ?: ""
        if (err == "authorization_pending") {
            runIn(state.deviceFlowInterval ?: 5, "checkDeviceFlowToken")
        } else if (err == "slow_down") {
            state.deviceFlowInterval = (state.deviceFlowInterval ?: 5) + 5
            runIn(state.deviceFlowInterval, "checkDeviceFlowToken")
        } else if (err == "expired_token") {
            log.warn "Device code expired — re-run authorize()"
            sendEvent(name: "authStatus", value: "needs-auth")
        } else {
            log.error "Device flow error: ${err}"
            sendEvent(name: "authStatus", value: "error")
        }
    }
}
```

### Step 4: Token Refresh

```groovy
private void refreshTokens() {
    String body = "grant_type=refresh_token" +
                  "&refresh_token=${state.refreshToken}" +
                  "&client_secret=${settings.clientSecret}"
    httpPost([uri: TOKEN_URL, requestContentType: "application/x-www-form-urlencoded",
              contentType: "application/json", body: body]) { resp ->
        if (resp.status == 200) {
            state.accessToken    = resp.data.access_token
            // Refresh token may rotate — always persist:
            if (resp.data.refresh_token) state.refreshToken = resp.data.refresh_token
            state.tokenExpiresAt = (now() / 1000L) + resp.data.expires_in
        } else {
            log.error "Token refresh failed: ${resp.status}"
            sendEvent(name: "authStatus", value: "needs-auth")
        }
    }
}
```

## Scopes

```
IdentifyAppliance FridgeFreezer-Monitor
```
- `IdentifyAppliance` — always required
- `FridgeFreezer-Monitor` — read-only access to fridge status + events
- `FridgeFreezer` — full access (monitor + control + settings) if control is needed

## Request Headers

All API calls:
```
Authorization: Bearer {access_token}
Accept: application/vnd.bsh.sdk.v1+json
```

## Key Endpoints

```
GET  /api/homeappliances                         → list appliances
GET  /api/homeappliances/{haId}/status           → all current status values
GET  /api/homeappliances/{haId}/events           → SSE stream (NOT usable in Hubitat)
```

## Fridge Door Event Keys (Confirmed from Official Docs)

### Status Keys (available via GET /status — pollable)
| Key | Description |
|---|---|
| `Refrigeration.Common.Status.Door.Refrigerator` | Fridge door open/closed |
| `Refrigeration.Common.Status.Door.Freezer` | Freezer door open/closed |

Values: `BSH.Common.EnumType.DoorState.Open` / `BSH.Common.EnumType.DoorState.Closed`
⚠️ Some appliances may use: `Refrigeration.Common.EnumType.Door.States.Open` / `...Closed` — verify on device.

### Event Keys (SSE only — derive from door state via polling instead)
| Key | Description |
|---|---|
| `Refrigeration.FridgeFreezer.Event.DoorAlarmRefrigerator` | Fridge left open too long — level: "alert" |
| `Refrigeration.FridgeFreezer.Event.DoorAlarmFreezer` | Freezer left open too long — level: "alert" |
| `Refrigeration.FridgeFreezer.Event.TemperatureAlarmFreezer` | Freezer temperature too high |

Event values (EventPresentState): `BSH.Common.EnumType.EventPresentState.Present/Confirmed/Off`

## Rate Limits (Critical)

| Limit | Value |
|---|---|
| Requests per day per (client + user) | **1,000** |
| Requests per minute per (client + user) | **50** |
| Token refreshes per day | 100 |
| Successive errors before block | 10 errors → 10-min block |

**Polling cadence:** Use ≥120 seconds (720 req/day for one appliance). At 90s you're at 960/day — borderline.

## SSE — Viable But Fragile on Hubitat

The `GET /api/homeappliances/{haId}/events` endpoint is a persistent `text/event-stream` (SSE) connection.

**`asynchttpGet` cannot consume it** (request-response only — correct).

**BUT Hubitat's EventStream interface (`interfaces.eventStream` / `parse()` callback + `eventStreamStatus()`) CAN consume SSE** — this is a platform-level feature, not raw HTTP. The craigde/hubitat-homeconnect-v3 Stream Driver uses this and runs on real Hubitat hubs as of 2026-03.

**Caveat: SSE via EventStream is fragile.** The craigde driver required 22+ patch releases in 65 days (Jan–Mar 2026) to stabilize: keep-alive detection failures, silent stream drops, Hubitat stripping newlines from parse() chunks, double-processing, reconnect race conditions. A production driver using SSE MUST implement:
- A watchdog cron job (check for data gaps > 3 minutes, since HC sends KEEP-ALIVE every ~55s)
- Exponential backoff on reconnect
- Rate-limit state guards (don't reconnect-burst when already 429)
- `connectionStatus` correction in `parse()` (Hubitat may fire STOP while stream is still alive)

If your use case tolerates 2-3 minute delayed updates, **polling `GET /status` at 120s intervals (720 req/day)** is simpler and more reliable than EventStream SSE. Use polling for simple drivers; use SSE only if real-time updates are required and you're prepared to implement the full watchdog infrastructure.

**Polling cadence reminder:** Use ≥120 seconds (720 req/day for one appliance). At 90s you're at 960/day — borderline safe.

## Comparison to SunStat

| | SunStat | Home Connect |
|---|---|---|
| Access token lifetime | 15 min | **24 hours** (simpler) |
| client_secret needed | No (public PKCE) | **Yes** — store as `password` pref |
| Embedded client_id | Yes | **No** — user registers own app |
| Initial auth in Hubitat | No (external CLI) | **Yes** via Device Flow |

## Anti-Patterns

- Polling faster than 90s — will hit 1,000/day limit, get blocked.
- Forgetting to persist the new `refresh_token` on refresh — may rotate.
- Embedding `client_id`/`client_secret` in the driver code — these are per-user credentials.
- Using `asynchttpGet` with the SSE events endpoint expecting streaming data.
