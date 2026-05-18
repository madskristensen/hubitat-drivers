# Decisions

Generated 2026-05-18T17:59:14Z

---

## tank-skill-audit-2026-05-17

---
author: tank
date: 2026-05-17T20:29:23-07:00
status: archived
subject: Reskill pass — audit 10 skills, bump healthcheck confidence, capture idempotent-release-workflow
---

### Audit Results
- **Skills audited:** 10 existing skills (all valid, no staleness)
- **Confidence bumped:** hubitat-healthcheck-vs-lastactivity (medium → high) after independent confirmation across three drivers in session
- **New skill:** idempotent-release-workflow (confidence: low, from commit ea2a74f) — handles graceful tag re-creation in release workflows
- **Cross-references added:** tuya-local-groovy ↔ hubitat-sandbox-pitfalls (overlap clarification); idempotent-release-workflow → hpm-release-workflow & hpm-bundle-manifest (dependency links)
- **No deprecations or merges**
- **Next:** Bump idempotent-release-workflow to medium on second independent application

---

## cypher-bosch-home-connect-feasibility

---
author: cypher
date: 2026-05-17T09:31:55-07:00
status: ready-for-review
subject: Bosch Home Connect fridge door-open driver — feasibility & API spec
---
# Decisions

Generated 2026-05-18T17:59:14Z

---

## cypher-bosch-home-connect-feasibility

---
author: cypher
date: 2026-05-17T09:31:55-07:00
status: ready-for-review
subject: Bosch Home Connect fridge door-open driver — feasibility & API spec
---

# Bosch Home Connect — Fridge Door-Open Driver Feasibility

## Sources

| Source | URL |
|---|---|
| Official auth docs | https://api-docs.home-connect.com/authorization |
| Official API general/rate-limit docs | https://api-docs.home-connect.com/general |
| Official events reference | https://api-docs.home-connect.com/events |
| HA integration `__init__.py` | https://github.com/home-assistant/core/blob/dev/homeassistant/components/home_connect/__init__.py |
| HA integration `binary_sensor.py` | https://github.com/home-assistant/core/blob/dev/homeassistant/components/home_connect/binary_sensor.py |
| HA integration `const.py` | https://github.com/home-assistant/core/blob/dev/homeassistant/components/home_connect/const.py |
| HA uses `aiohomeconnect` Python library | https://github.com/MartinHjelmare/aiohomeconnect |

---

## Verdict

> **Yes — feasible on Hubitat, with caveats.**
>
> The API is well-documented, the event keys are confirmed, and polling is a viable substitute for SSE. The main caveats are: (1) SSE is not implementable in Hubitat's sandbox, so polling is required; (2) the 1,000 req/day quota constrains polling cadence to ≥90 seconds; (3) the user must self-register an app at developer.home-connect.com.

---

## 1. Auth Summary

### Flow

Home Connect supports **two OAuth2 flows** suitable for Hubitat:

#### Option A — Device Flow (recommended for Hubitat)
- No redirect URI needed. No web server needed.
- Driver POSTs to the Device Authorization Endpoint, gets back `user_code` + `verification_uri_complete`.
- Hubitat displays these on the device's configuration page (via `log.info` + custom attribute).
- User visits the URL on their phone / PC, enters the code, grants access.
- Driver polls the token endpoint until `authorization_pending` resolves to a token.
- **Best fit for Hubitat** — no Cloud Endpoint plumbing required.

#### Option B — Authorization Code Grant (alternative)
- Requires a `redirect_uri`. Hubitat has `getFullLocalApiServerUrl()` (LAN only) or `getFullApiServerUrl()` (cloud) for this.
- More complex: driver must handle the `?code=` callback in a `mappings` block.
- Standard HA approach, but harder to implement cleanly in Groovy.

### App Registration

The user **must** create a developer account at [developer.home-connect.com](https://developer.home-connect.com) and register an application. This is free. They receive:
- `client_id` (public) — goes into driver preferences as plain text.
- `client_secret` (confidential) — goes into driver preferences as `password` type.

There is **no shared/embedded client_id** possible — each application must register its own. This is a notable UX hurdle vs. SunStat (where the client_id is embedded in the driver).

### Endpoints

| Operation | Method | URL |
|---|---|---|
| Auth redirect (Code flow) | GET | `https://api.home-connect.com/security/oauth/authorize` |
| Device authorization | POST | `https://api.home-connect.com/security/oauth/device_authorization` |
| Token exchange (code or device) | POST | `https://api.home-connect.com/security/oauth/token` |
| Token refresh | POST | `https://api.home-connect.com/security/oauth/token` |

### Token Lifetimes

- **Access token:** `expires_in: 86400` (24 hours) — confirmed in official docs.
- **Refresh token lifetime:** Not explicitly stated in official docs. In practice, refresh tokens are long-lived (months). However, they **do expire** if unused or if the user revokes access.
- **Token refresh body** (important — requires `client_secret`):
  ```
  POST https://api.home-connect.com/security/oauth/token
  Content-Type: application/x-www-form-urlencoded

  grant_type=refresh_token
  &refresh_token={refresh_token}
  &client_secret={client_secret}
  ```
  Response includes a new `access_token` (and may or may not include a new `refresh_token` — the docs show `refresh_token` in the response but don't explicitly state rotation. Treat as rotating to be safe.)

### Scopes Required

For door-monitoring only (read-only, minimal):
```
IdentifyAppliance FridgeFreezer-Monitor
```
`IdentifyAppliance` is always required. `FridgeFreezer-Monitor` gives read access to status and events without control permissions.

If the user wants broader appliance control in future: use `FridgeFreezer` (includes Monitor + Control + Settings).

### Simulator vs Production

- **Simulator** (free): `simulator.home-connect.com` — use during development. Configured via the developer portal. Pre-provisioned virtual appliances.
- **Production**: `api.home-connect.com` — requires the user's actual Bosch/Siemens appliance to be paired to the Home Connect app.
- The driver should have a `simulated` boolean preference that switches the base URL.

---

## 2. Key REST Endpoints

Base URL: `https://api.home-connect.com` (production) / `https://simulator.home-connect.com` (test)

All requests require:
```
Authorization: Bearer {access_token}
Accept: application/vnd.bsh.sdk.v1+json
```

| Purpose | Method | Path |
|---|---|---|
| List paired appliances | GET | `/api/homeappliances` |
| Get single appliance | GET | `/api/homeappliances/{haId}` |
| Get all status values | GET | `/api/homeappliances/{haId}/status` |
| Get single status value | GET | `/api/homeappliances/{haId}/status/{statusKey}` |
| SSE event stream | GET | `/api/homeappliances/{haId}/events` |
| SSE all-appliances stream | GET | `/api/homeappliances/events` |

### List Appliances — Response Shape

```json
{
  "data": {
    "homeappliances": [
      {
        "haId": "BOSCH-HCS05FRF1-XXXXXXXXXXXXXX",
        "name": "My Fridge Freezer",
        "brand": "BOSCH",
        "vib": "HCS05FRF1",
        "connected": true,
        "type": "FridgeFreezer",
        "enumber": "KGN56XLEA/05"
      }
    ]
  }
}
```

Filter: `type == "FridgeFreezer"` to find fridges.

### Status Endpoint — Door State Response

```
GET /api/homeappliances/{haId}/status/Refrigeration.Common.Status.Door.Refrigerator
```
```json
{
  "data": {
    "key": "Refrigeration.Common.Status.Door.Refrigerator",
    "value": "BSH.Common.EnumType.DoorState.Open"
  }
}
```
(Or `"BSH.Common.EnumType.DoorState.Closed"`)

**Note on enum namespace:** The official event docs use `BSH.Common.EnumType.DoorState.Open/Closed` for the generic door state key. However, the HA `const.py` defines refrigeration-specific door states as:
- `Refrigeration.Common.EnumType.Door.States.Closed`
- `Refrigeration.Common.EnumType.Door.States.Open`

This **refrigeration-specific namespace** is what the HA binary_sensor uses. Switch should validate which enum appears on the actual appliance — both namespaces have been seen in the wild.

---

## 3. Door-Open Event Keys (Confirmed from Official API Docs)

### Door State Status Keys (continuous state, polled or streamed)

These arrive as `event: STATUS` on the SSE stream and are returned by `GET /status`:

| Compartment | Key |
|---|---|
| Refrigerator | `Refrigeration.Common.Status.Door.Refrigerator` |
| Refrigerator (2nd door) | `Refrigeration.Common.Status.Door.Refrigerator2` |
| Refrigerator (3rd door) | `Refrigeration.Common.Status.Door.Refrigerator3` |
| Freezer | `Refrigeration.Common.Status.Door.Freezer` |
| Bottle cooler | `Refrigeration.Common.Status.Door.BottleCooler` |
| Chiller | `Refrigeration.Common.Status.Door.Chiller` |
| Flex compartment | `Refrigeration.Common.Status.Door.FlexCompartment` |
| Wine compartment | `Refrigeration.Common.Status.Door.WineCompartment` |

**Values** (door state keys): `BSH.Common.EnumType.DoorState.Open` / `BSH.Common.EnumType.DoorState.Closed`
(Verify: some fridges may use `Refrigeration.Common.EnumType.Door.States.Open/Closed` instead — see note above.)

### Door Alarm Event Keys ("left open too long" signal)

These arrive as `event: EVENT` on the SSE stream, NOT via the status endpoint:

| Event | Trigger |
|---|---|
| `Refrigeration.FridgeFreezer.Event.DoorAlarmRefrigerator` | Refrigerator door left open too long |
| `Refrigeration.FridgeFreezer.Event.DoorAlarmFreezer` | Freezer door left open too long |

**Values** (EventPresentState enum):
- `BSH.Common.EnumType.EventPresentState.Present` — alarm is currently active
- `BSH.Common.EnumType.EventPresentState.Confirmed` — user acknowledged alarm on appliance panel
- `BSH.Common.EnumType.EventPresentState.Off` — alarm cleared (door closed)

**Level:** `"alert"` (highest severity — confirmed in official docs examples).

### Additional Useful Event Key

| Event | Description |
|---|---|
| `Refrigeration.FridgeFreezer.Event.TemperatureAlarmFreezer` | Freezer temperature too high |

---

## 4. Push vs Poll Recommendation

### SSE (Push) — Not Viable on Hubitat

The events endpoint `GET /api/homeappliances/{haId}/events` streams `text/event-stream` (SSE). This is a persistent HTTP connection that stays open indefinitely, pushing newline-delimited event frames as state changes.

**Hubitat cannot consume SSE.** Hubitat's Groovy sandbox provides:
- `httpGet` / `httpPost` — synchronous, blocking, single response per call.
- `asynchttpGet` / `asynchttpPost` — asynchronous but fires a callback on response _completion_, not on streaming data. They are not designed for long-lived connections and will timeout or close after the first chunk.

No current Hubitat API allows a persistent streaming connection. SSE is not feasible.

### Polling (Pull) — Recommended

Poll `GET /api/homeappliances/{haId}/status` on a schedule. This returns all current status values including door state.

**Recommended polling cadence: 90 seconds** (640 requests/day per appliance, well within the 1,000/day quota with headroom for discovery and auth calls).

**Alternative door-alarm-focused approach:** Rather than relying on the appliance's alarm event (which fires after ~3 minutes on most models), Hubitat can watch for door state transitioning to `Open` and start an internal timer. If the door is still open after a user-configurable threshold (e.g., 3 minutes), fire a Hubitat notification. This is more reliable and configurable than the appliance alarm.

**Polling for alarm events:** The `DoorAlarm*` events are only surfaced via the SSE stream (event type `EVENT`), not via the status endpoint. However, since the door STATE is available via the status endpoint, the driver can derive the "left open" condition itself using a rule or timer in Hubitat.

---

## 5. Rate Limits

Source: Official API docs at api-docs.home-connect.com/general

| Limit | Value |
|---|---|
| Requests per day (per client + user) | **1,000** |
| Requests per minute (per client + user) | **50** (blocked for 1 minute if exceeded) |
| Token refreshes per minute | 10 |
| Token refreshes per day | 100 |
| Successive error requests per 10 min | 10 (then blocked 10 min) |
| Parallel SSE monitoring channels | 10 max per client + user |

**Implications for Hubitat:**
- At 90-second polling: ~960 requests/day — **borderline** (one appliance). Safer: 2-minute cadence = 720/day.
- At 2-minute polling with 2 appliances: 1,440/day — **exceeds quota**. Use 3-minute for multi-appliance.
- The 1,000/day limit is per (client_id, Home Connect user account). Multiple Hubitat installs using the same client_id would share this quota — instruct users to register their own application.
- Error rate matters: a misconfigured driver that produces 401/400 errors will be blocked after 10 successive errors. Build in exponential backoff.

---

## 6. Capability Mapping Recommendation

### Parent Device (Cloud Account)
No standard Hubitat capability. Custom commands only.

| Attribute | Type | Notes |
|---|---|---|
| `authStatus` | enum: `"authorized"`, `"needs-auth"`, `"error"` | Displayed on parent tile |
| `lastRefresh` | date | ISO timestamp of last successful poll |

Commands:
- `authorize()` — initiates Device Flow (displays user_code + URL)
- `discoverAppliances()` — scans `/api/homeappliances`, creates child devices
- `refresh()` — polls all children immediately
- `poll()` — internal scheduled method

Preferences:
- `clientId` (text) — from developer portal
- `clientSecret` (password) — from developer portal
- `pollIntervalSecs` (number, default 90) — min 60
- `simulated` (bool, default false) — use simulator

### Child Device (per appliance)
One child per FridgeFreezer. Expose per-door state as separate child devices, or as attributes on one child. **Recommendation: two contact sensor attributes on one child** (simpler UX).

| Capability | Attribute | Values | Notes |
|---|---|---|---|
| `ContactSensor` | `contact` | `open`, `closed` | Primary fridge door (Refrigerator) |
| `Sensor` | — | — | marker capability |
| `Refresh` | — | — | exposes `refresh()` command |

Additional attributes (non-standard, but useful):
| Attribute | Type | Notes |
|---|---|---|
| `freezerContact` | enum: `open`, `closed` | Freezer door state |
| `doorAlarmRefrigerator` | enum: `present`, `confirmed`, `off` | Appliance alarm state |
| `doorAlarmFreezer` | enum: `present`, `confirmed`, `off` | Appliance alarm state |
| `connected` | enum: `true`, `false` | Appliance online/offline |
| `temperatureAlarmFreezer` | enum: `present`, `confirmed`, `off` | Optional bonus |

The primary `ContactSensor` uses `contact` for the fridge door, enabling the built-in "contact open for X minutes" automation in Hubitat's native rules engine. This is cleaner than reinventing the timer.

**Alternative (multi-child):** Create one child per door (fridge door child + freezer door child), both with `ContactSensor`. More tiles on the dashboard, but each door is a standard first-class device. Recommended for users with a FridgeFreezer combo appliance.

---

## 7. Comparison to SunStat Pattern

| Concern | SunStat | Home Connect |
|---|---|---|
| Auth type | Azure AD B2C PKCE (complex initial login) | Standard OAuth2 Auth Code or Device Flow |
| Initial auth | External CLI tool (bootstrap script) | Device Flow can be done entirely in Hubitat |
| client_secret | Not needed (public client) | **Required** — store as `password` preference |
| Access token lifetime | 15 minutes | 24 hours (much simpler — refresh once/day) |
| Refresh rotation | Yes — always persist new refresh_token | Yes — treat as rotating to be safe |
| Token URL | Azure B2C tenant | `api.home-connect.com/security/oauth/token` |
| Parent holds tokens | Yes — `state.accessToken`, `state.refreshToken` | Same pattern |
| Children store cloudId | Yes — `DataValue("cloudDeviceId")` | Same — store `haId` |
| Push vs poll | Poll (no SSE) | Poll (SSE technically exists, not usable) |
| Discovery | GET /User → /Location/{id}/Devices | GET /api/homeappliances (simpler) |
| Rate limits | None documented | 1,000/day hard limit — important constraint |

**Key reusable patterns from SunStat:**
- Parent device holds `state.accessToken`, `state.refreshToken`, `state.tokenExpiresAt`.
- `getValidToken()` guard before every HTTP call; refresh if `now > expiresAt - 300`.
- Children store `haId` as `DataValue("cloudDeviceId")`.
- `isComponent: false` on child creation.
- `parseDeviceState(body)` called by parent to push state to children.

**Key difference:** Home Connect's 24-hour token means refresh is trivial (once per day vs. every 15 min for SunStat). The Driver won't need to refresh as defensively.

**New wrinkle:** Device Flow polling during initial auth — this requires a loop or scheduled retry that isn't needed in the SunStat pattern. Implement as a `runIn(5, "checkDeviceFlowToken")` polling loop.

---

## 8. Open Questions for Switch (Real-Device Validation)

1. **Door enum namespace:** Does the actual Bosch fridge return `BSH.Common.EnumType.DoorState.Open` or `Refrigeration.Common.EnumType.Door.States.Open` for `Refrigeration.Common.Status.Door.Refrigerator`? HA's const.py uses the Refrigeration-specific namespace, but official docs example uses BSH.Common.
2. **Alarm timing:** How many minutes does the fridge wait before firing `DoorAlarmRefrigerator`? Typical is 3 minutes but varies by model.
3. **Status endpoint availability:** Does `GET /api/homeappliances/{haId}/status` return all door state keys even when the door is closed, or only present keys? (i.e., does `Refrigeration.Common.Status.Door.Refrigerator` appear in the status response when closed, or only when open?)
4. **Device Flow support:** Does the developer portal require a special configuration to enable the Device Flow for a registered application, or is it available by default? (Could not confirm without creating a test account.)
5. **Refresh token rotation:** Does a `grant_type=refresh_token` call for Home Connect return a new `refresh_token` in the response, or only a new `access_token`? The docs show `refresh_token` in the response schema but do not explicitly call out rotation.
6. **`client_id` alone for refresh:** Some OAuth2 servers accept `client_id` without `client_secret` for public clients. Does Home Connect's Device Flow produce a public or confidential client token? If public, `client_secret` may not be required for refresh.
7. **Connected state after poll gap:** If the fridge loses WiFi while the door is open, what does `connected: false` look like in the status response? Will the status endpoint 404 or return stale data?

---

## 9. Auth Flow (Device Flow) — Step by Step

For Tank's reference, the complete Device Flow implementation:

### Step 1 — Initiate Device Auth
```http
POST https://api.home-connect.com/security/oauth/device_authorization
Content-Type: application/x-www-form-urlencoded

client_id={client_id}&scope=IdentifyAppliance%20FridgeFreezer-Monitor
```
Response:
```json
{
  "device_code": "Ag_EE...3NuE",
  "user_code": "WDJB-MJHT",
  "verification_uri": "https://api.home-connect.com/security/oauth/device",
  "verification_uri_complete": "https://api.home-connect.com/security/oauth/device?user_code=WDJB-MJHT",
  "expires_in": 300,
  "interval": 5
}
```

### Step 2 — Display to User
Log the `verification_uri_complete` URL and `user_code` in the Hubitat device log. User opens on phone.

### Step 3 — Poll for Token
Every `interval` seconds (minimum 5s), poll:
```http
POST https://api.home-connect.com/security/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=device_code&device_code={device_code}&client_id={client_id}
```
Responses:
- `{"error":"authorization_pending"}` — keep polling (user hasn't approved yet)
- `{"error":"slow_down"}` — back off by adding 5s to interval
- `{"error":"access_denied"}` — user denied; abort
- `{"error":"expired_token"}` — device_code expired (5 min); restart flow
- `200 OK + tokens` — success! Store `access_token`, `refresh_token`, `expires_in`

### Step 4 — Token Refresh (daily)
```http
POST https://api.home-connect.com/security/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token={refresh_token}&client_secret={client_secret}
```

### Superseded by

⚠️ **IMPORTANT:** The feasibility verdict above ("user must self-register an app") was challenged in a subsequent user directive. See entries below:
- `copilot-directive-2026-05-17T093200-no-developer-app` — user request to eliminate developer portal step
- `cypher-bosch-consumer-auth-options` — Cypher's findings on consumer-auth alternatives

**Summary:** No viable consumer-auth-only path exists within Hubitat's sandbox constraints. The developer portal registration remains unavoidable for the official API path. The alternative (external hcpy bridge + MQTT) is more complex than the 5-minute registration step.

---

## copilot-directive-2026-05-17T093200-no-developer-app

---
author: Mads Kristensen (via Copilot)
date: 2026-05-17T09:32:00-07:00
status: review-in-progress
subject: User directive — eliminate developer portal step for Bosch Home Connect driver
---

### 2026-05-17T09:32:00-07:00: User Directive

**By:** Mads Kristensen (via Copilot)

**What:** For the Bosch Home Connect driver, do NOT require the user to register a developer application at developer.home-connect.com. The driver must sign in the same way the consumer Home Connect mobile app does — username/password (or SingleKey ID), no client_id/client_secret entered by the user.

**Why:** UX hurdle. Registering a developer app is a non-trivial onboarding step that defeats the value of an install-and-go Hubitat driver. The fridge already trusts the mobile app's auth — the driver should mirror that.

**Impact:** Supersedes the "register a free app at developer.home-connect.com" step in cypher-bosch-home-connect-feasibility.md. Requires reverse-engineered/community-known consumer auth path (e.g., hcpy, home-assistant community work). May change the API surface (Bosch's consumer back-end may speak WebSocket/MQTT instead of REST + SSE).

---

## cypher-bosch-consumer-auth-options

---
author: Cypher
date: 2026-05-17T09:45:09-07:00
status: ready-for-review
subject: Bosch Home Connect — Consumer Auth Options for Hubitat
triggered_by: copilot-directive-2026-05-17T093200-no-developer-app
---

# Cypher: Bosch Home Connect — Consumer Auth Options for Hubitat

**Author:** Cypher (Integration / Protocol Engineer)  
**Date:** 2026-05-17T09:45:09-07:00  
**Triggered by:** `copilot-directive-2026-05-17T093200-no-developer-app`  
**Question:** Is there a viable path to authenticate as a consumer (username + password, no developer app) that works inside Hubitat's Groovy sandbox?

---

## 1. Candidates Surveyed

### A. `hcpy` / `hcpy2-0` — Local WebSocket via Consumer Auth

**Source:** https://github.com/osresearch/hcpy (original), https://github.com/hcpy2-0/hcpy (maintained fork)

#### What it does

hcpy reverse-engineered the Bosch Home Connect mobile app and found:

1. The BSH mobile app uses a hardcoded `client_id` (not registered by the user):  
   `9B75AC9EC512F36C84256AC47D813E2C1DD0D6520DF774B020E1E6E2EB29B1F3`

2. Auth flow is OAuth2 PKCE against Bosch's consumer SSO at `singlekey-id.com`, using the BSH app's own client identity:
   - GET `https://api.home-connect.com/security/oauth/authorize?client_id=<BSH_APP_ID>&redirect_uri=hcauth://auth/prod&...`
   - The user is redirected to SingleKey ID login at `singlekey-id.com`
   - Originally: script auto-POSTed email + password (with HTML form scraping / CSRF tokens)
   - **After CAPTCHA was added (circa 2024):** automation is broken. User must open the URL in a real browser, use F12 Dev Tools to watch network traffic, and manually copy `code` + `state` from the `hcauth://auth/...` redirect URL.

3. After successful PKCE exchange at `https://api.home-connect.com/security/oauth/token`, the bearer token is used against the **consumer backend**:
   - `https://eu.services.home-connect.com/api/account/v2/accounts/{sub}/paired-appliances` — lists devices
   - `https://eu.services.home-connect.com/api/appliance/v2/appliances/{haId}/encryption-information` — fetches per-device crypto keys (TLS PSK key or AES key + IV)

4. **Then hcpy connects to the appliance locally over WebSocket:**
   - TLS-PSK appliances (most modern Bosch/Siemens): WebSocket over HTTPS using `ECDHE-PSK-CHACHA20-POLY1305` — a non-standard cipher requiring a patched TLS library (`sslpsk`)
   - Older appliances: plain HTTP WebSocket with AES-CBC encrypted frames (binary type `0x82`)
   - All state updates arrive as events over this persistent WebSocket — there is no request/response polling.

#### Developer portal required?

**No**, for the auth token itself — the BSH app's own `client_id` is hardcoded.

**However**, this is unauthorized use of BSH's internal app credentials. BSH's ToS for the developer API explicitly forbids impersonating BSH's own clients. BSH has tightened the consumer flow by adding CAPTCHA precisely to prevent this scripting.

#### Hubitat feasibility

**Non-starter. Two hard blockers:**

1. **CAPTCHA blocks fully automated auth.** The SingleKey ID form now requires CAPTCHA completion in a real browser. The auth flow cannot be automated from Hubitat's HTTP client (no JavaScript runtime, no browser). Even with the original form-scraping approach, Hubitat has no HTML parser and would need to track 2+ rounds of CSRF tokens and multi-step redirects.

2. **Operational protocol is local WebSocket.** After auth, ALL appliance state is delivered over a persistent WebSocket connection. Hubitat's sandbox has no WebSocket client — `httpGet`/`httpPost` and their async variants are request-response only. They cannot hold a long-lived WebSocket connection.

3. **TLS cipher incompatibility.** Even if Hubitat could do WebSockets, the TLS-PSK cipher (`ECDHE-PSK-CHACHA20-POLY1305`) is not available in standard JVM TLS stacks. hcpy requires a specially patched Python library (`sslpsk`) to negotiate it.

4. **No consumer-backend REST polling API.** The consumer backend at `eu.services.home-connect.com` / `na.services.home-connect.com` is only used for device discovery and key retrieval during setup. There is **no REST endpoint on the consumer backend for polling appliance state** — the local WebSocket IS the operational interface.

---

### B. Home Assistant `home_connect_alt` (ekutner/home-connect-hass)

**Source:** https://github.com/ekutner/home-connect-hass

Requires developer app registration. Step 1 of installation is "Creating a Home Connect developer app." Uses the official `api.home-connect.com` developer API with OAuth2 Authorization Code flow. No bypass of developer portal. **Not applicable.**

---

### C. openHAB Home Connect Direct Binding (bruestel)

**Source:** https://github.com/bruestel/openhab-addons/tree/homeconnectdirect/bundles/org.openhab.binding.homeconnectdirect

Uses the `homeconnect-profile-downloader` desktop GUI app (https://github.com/bruestel/homeconnect-profile-downloader) to authenticate against SingleKey ID via a browser window, download per-device profiles (including encryption keys), and import them into openHAB. The binding then speaks the same local WebSocket protocol as hcpy.

- **Developer portal:** Not required — uses consumer flow via desktop app
- **Protocol after auth:** Local WebSocket (TLS-PSK or AES-CBC) — same as hcpy
- **Hubitat feasibility:** Same hard blockers as hcpy. The desktop profile-downloader approach is actually a creative workaround for the CAPTCHA problem — but it's irrelevant here because the local WebSocket is still the operational protocol, and Hubitat can't speak it.

---

### D. Homebridge Plugins

No Homebridge plugin exists for Bosch Home Connect that bypasses the developer portal. All known Homebridge Home Connect plugins (e.g., `homebridge-homeconnect` by nickcoutsos) use the official developer API and require `client_id` + `client_secret`. **Not applicable.**

---

### E. BSH SingleKey ID — Direct Consumer SSO

**What it is:** Bosch's consumer identity platform at `singlekey-id.com`. Used by the Home Connect mobile app and by hcpy.

**Could a Hubitat driver authenticate against it directly?**

Theoretically, the original hcpy approach (form-scraping SingleKey ID) could be re-implemented — but:

1. **CAPTCHA:** Added to the SingleKey ID login form circa 2024. Any fully automated username/password submission is blocked.
2. **Multi-step redirects:** The flow involves 5–8 HTTP 302 redirects across two domains (`api.home-connect.com` and `singlekey-id.com`), each requiring the session cookie from the previous step. Hubitat's HTTP client does not maintain a persistent cookie jar across separate `httpGet`/`httpPost` calls.
3. **CSRF tokens:** Each POST requires a `__RequestVerificationToken` extracted from the previous HTML response. Hubitat has no HTML parser.
4. **Custom redirect scheme:** The final callback is to `hcauth://auth/prod?code=...` — a custom URI scheme. Hubitat cannot be told to intercept this redirect; it would need to manually extract `code` and `state` from the `Location` header of the last `302` before the `hcauth://` step. This is technically possible if the HTTP client stops following redirects at that point, but combined with CAPTCHA it's moot.

**Even if all auth problems were solved:** the consumer backend has no REST polling API for appliance state. A Hubitat driver would successfully authenticate and then have nowhere to go.

---

## 2. Legal / ToS Observations

Community projects (hcpy, openHAB direct binding) use the BSH mobile app's own `client_id` without authorization. BSH has:
- Added CAPTCHA to SingleKey ID to prevent scripted auth (2024)
- Not issued widespread account bans for hcpy users (as of early 2026, no mass-ban reports in the community)
- Rotated internal endpoints — `prod.reu.rest.homeconnectegw.com` in original hcpy became `eu.services.home-connect.com` in hcpy2-0; hcpy users have needed to update periodically

The official ToS forbids reverse-engineering and unauthorized use of BSH's own client credentials. The community treats this as acceptable risk for personal use. A publicly distributed Hubitat driver using these credentials would draw more scrutiny and risk BSH rotating/revoking the hardcoded `client_id` and breaking the driver for all users simultaneously.

---

## 3. Recommendation

**No — there is no viable consumer-auth-only path for Hubitat.**

| Candidate | Developer Portal Required | Hubitat Feasible | Blocker |
|---|---|---|---|
| hcpy consumer auth | No | **No** | CAPTCHA + local WebSocket (no REST polling API) |
| HA `home_connect_alt` | **Yes** | No (irrelevant) | Developer portal required |
| openHAB direct binding | No | **No** | Local WebSocket — same as hcpy |
| SingleKey ID scraping | No | **No** | CAPTCHA + no cookie jar + no REST state API |
| Official developer API | **Yes** | **Yes** | None — this is the viable path |

The fundamental issue is not merely authentication — it is that **Bosch's consumer infrastructure has no cloud REST API for polling appliance state**. The local WebSocket IS the only state channel, and Hubitat cannot speak it. The developer API (`api.home-connect.com/api/`) is architecturally different: it provides a proper cloud-mediated REST interface (GET `/api/homeappliances/{haId}/status`) that is fully compatible with Hubitat's polling model.

**Replacing the developer API path with a consumer path would require:**
1. Solving CAPTCHA (impossible in Hubitat)
2. Implementing a local WebSocket client with non-standard TLS cipher (impossible in Hubitat)
3. Either of which is a permanent platform constraint, not a solvable engineering problem.

---

## 4. Mads' Realistic Options

### Option 1: Developer App (Recommended)

**One-time user onboarding, approximately 5 minutes:**

1. Go to https://developer.home-connect.com
2. Create account (same email as the Home Connect app)
3. Register application → select "Device Flow" → copy `client_id` and `client_secret`
4. Enter both in Hubitat driver preferences

After that, auth is fully automated via the Device Flow (no redirect URI, driver polls until user approves on phone). Token is valid 24 hours, auto-refreshes. **This is the path Tank should implement.**

Friction point: Steps 1–3 are developer portal registration. Estimated time: 3–5 minutes. It is a real hurdle. No way to eliminate it within Hubitat's sandbox constraints.

### Option 2: Python Bridge (hcpy + MQTT)

Run hcpy on a separate always-on device (Pi, NAS, Docker container). hcpy authenticates via consumer flow (requiring one-time manual browser auth due to CAPTCHA), connects to the fridge via local WebSocket, and publishes state to MQTT. Hubitat subscribes via an MQTT driver.

- **Developer portal:** Not required
- **Hubitat complexity:** Requires MQTT driver + hcpy infrastructure
- **Ongoing maintenance:** hcpy is beta, endpoints have changed before; also requires the local WebSocket connection to stay up
- **Verdict:** More infrastructure complexity than registering a developer app; trades one onboarding hurdle for a permanent dependency

### Option 3: Semi-Manual Consumer Token (Fragile, Not Recommended)

Use the BSH app's hardcoded `client_id` and ask Mads to manually complete the browser auth (copy/paste `code` from F12 dev tools into Hubitat preferences), then exchange it for a token via httpPost. The driver would then poll the **developer API endpoint** using this token.

Wait — **this won't work** because the consumer auth token grants access to `eu.services.home-connect.com`, NOT to `api.home-connect.com/api/`. They are different backend systems with different tokens/scopes. You cannot mix them.

**This option is dead.**

---

## 5. If Option 1 (Developer App) Proceeds — Confirmed Auth Spec

This was fully documented in `cypher-bosch-home-connect-feasibility.md` and the `home-connect-oauth-device-flow` skill. Summary:

```
client_id: user-registered (entered in driver preferences)
client_secret: user-registered (entered as password preference)

Device Flow:
  POST https://api.home-connect.com/security/oauth/device_authorization
  body: client_id={id}&scope=IdentifyAppliance%20FridgeFreezer-Monitor

  → Returns: device_code, user_code, verification_uri_complete, interval

  Poll every {interval} seconds:
  POST https://api.home-connect.com/security/oauth/token
  body: grant_type=urn:ietf:params:oauth:grant-type:device_code
        &device_code={device_code}&client_id={id}

  → On success: access_token (24h), refresh_token (months)

State polling:
  GET https://api.home-connect.com/api/homeappliances/{haId}/status
  Authorization: Bearer {access_token}
  Accept: application/vnd.bsh.sdk.v1+json
  → Every 120s (720 req/day, safely under 1,000/day limit)
```

---

## 6. Open Questions for Switch

1. Does Hubitat's `httpPost` follow 302 redirects automatically? (Would affect Option 3, but Option 3 is dead for other reasons — still worth documenting for sandbox reference.)
2. Is there a Hubitat community MQTT bridge driver that Tank should evaluate for Option 2?
3. Can Hubitat's device preference UI mark fields as "password" and "link" types — so the `client_id` field could hyperlink to `https://developer.home-connect.com`? (UX improvement for Option 1.)
4. Does the consumer token from `singlekey-id.com` route to the same `api.home-connect.com` token endpoint, or is it genuinely a different backend? (Clarifies whether semi-consumer auth is even theoretically possible.)

---

## Decision

**Consensus:** Proceed with the developer portal path (Option 1). It is the only viable path given Hubitat's sandbox constraints. The 5-minute one-time onboarding is worth the UX clarity it buys.

The driver will use Device Flow with a user-registered `client_id` and `client_secret`. Tank will implement auth token persistence, token refresh, and state polling per the auth spec above.

---

## trinity-bosch-home-connect-architecture

---
decision_id: trinity-bosch-home-connect-architecture

---
decision_id: trinity-bosch-home-connect-architecture
author: Trinity
date: 2026-05-17T09:31:55-07:00
status: proposed
requested_by: Mads Kristensen
---

# Bosch Home Connect — Driver Architecture Decision

## What Would It Take (Mads' Quick Read)

- **Medium effort, 2–3 sessions.** OAuth is the heaviest lift; contact sensor logic is straightforward once auth is wired.
- **Parent/child model** exactly like SunStat: parent owns OAuth tokens + appliance discovery + poll loop; one child per physical door (fridge door + freezer door = 2 children).
- **ContactSensor per child** is the right capability — each door shows up as its own sensor, drives Rule Machine triggers cleanly, and Hubitat notification apps can target them directly.
- **Polling first, SSE later.** Hubitat can't naturally hold a persistent HTTP stream; poll every 30–60 s is safe and proven. SSE is a v2 option once Cypher confirms feasibility.
- **Biggest blocker:** Bosch requires an exact-match HTTPS redirect URI; Hubitat's Cloud Endpoint URL must qualify — Cypher needs to confirm this before we write a single line of OAuth code.

---

## 1. Recommended Architecture

### Shape
Parent / child split — identical rationale to SunStat:

```
bosch-home-connect-parent (one Hubitat device)
├── Holds: accessToken, refreshToken, tokenExpiresAt  (in state.*)
├── OAuth flow: handles /oauth/initialize and /oauth/callback mappings
├── Discovery: GET /api/homeappliances → creates children
├── Poll loop: schedule("0/30 * * * * ?", "poll") — every 30 s (configurable)
└── Routes events: child.parseApplianceState(eventData)

bosch-home-connect-fridge-door  (child, one per door zone)
├── Capabilities: ContactSensor, Sensor
├── DataValue: haApplianceId  (e.g. "SIEMENS-RS295...")
├── DataValue: doorZone       ("refrigerator" | "freezer")
└── parseApplianceState(data) → sendEvent contact open/closed
```

### OAuth Flow (Hubitat Cloud Endpoint pattern)

Hubitat Apps — not Drivers — can define `mappings {}` with Cloud Endpoints. This means the OAuth flow lives in a **Hubitat App** (the parent *App*, not a raw Driver). The pairing sequence:

1. User installs **Bosch Home Connect** app from HPM.
2. User opens app preferences → taps "Authorize with Bosch."
3. App calls `createAccessToken()` → generates a Hubitat Cloud URL of the form:
   `https://cloud.hubitat.com/api/<hub-id>/apps/<app-id>/oauth/callback`
4. App redirects the browser to Bosch authorization URL with `client_id`, `scope`, and the above `redirect_uri`.
5. User logs in to Bosch/Home Connect → Bosch redirects back to the Hubitat Cloud URL.
6. App's `mappings { path("/oauth/callback") { action: [GET: "oauthCallback"] } }` fires; app exchanges the code for tokens via POST to `https://api.home-connect.com/security/oauth/token`.
7. `accessToken` + `refreshToken` stored in app `state.*`; app calls `discoverAppliances()`.

> **Note:** If we use a raw Driver instead of an App, there are no `mappings {}` available (confirmed platform constraint in our Watts work). We must use a Hubitat App as the OAuth entry point. The parent *Driver* then receives tokens from the App, or the App directly manages child devices. Simplest path: **single App** that behaves like a parent — holds auth + creates/polls children. No separate "parent driver." This is actually more idiomatic for cloud-OAuth devices on Hubitat.

### Push vs Poll

| Option | Mechanism | Hubitat feasibility |
|--------|-----------|-------------------|
| **Polling** | `schedule()` every 30–60 s → GET `/api/homeappliances/{id}/status` | ✅ proven pattern |
| **SSE** | Persistent GET `/api/homeappliances/events` stream | ⚠️ `asynchttpGet` does not hold long-lived connections; would require repeated reconnect on chunk close |

**Decision: start with polling.** Defer SSE design to Cypher's research. If Home Connect rate-limits polling aggressively (anecdotally 1 req/s per appliance), SSE becomes mandatory.

---

## 2. Capability Mapping

### Per-door child device

| Capability / Attribute | Source | Notes |
|------------------------|--------|-------|
| `ContactSensor` (contact: open/closed) | `DoorState` status field | Primary use case |
| `Sensor` | Standard companion to ContactSensor | |
| `doorAlarm` (custom enum: none/alarm) | `DoorAlarm` field if present | "Left open too long" — separate from contact state |
| `deviceOnline` (enum: true/false) | `ConnectionState` | Mark unknown if cloud unreachable |

**Why two children (fridge door + freezer door) over one device with two attributes:**
- Each shows up as its own Contact Sensor in Rule Machine → standard `contactSensor` triggers work without any custom attribute logic.
- Hubitat's built-in Notification app targets ContactSensor devices — no custom rule needed for Mads' alerting use case.
- Users can rename, assign rooms, and dashboard-tile each door independently.
- One device with two custom attributes is a dead end: built-in apps won't see `refrigeratorDoor`, and Rule Machine custom attribute support is less ergonomic.

### Discovery-time child creation

```groovy
// For each appliance returned by GET /api/homeappliances:
// Create one child per door zone it has
["refrigerator", "freezer"].each { zone ->
    String dni = "bosch-${applianceId}-${zone}"
    def existing = getChildDevice(dni)
    if (!existing) {
        def child = addChildDevice("mads", "Bosch Home Connect Door Sensor", dni,
                                   [name: "${applianceName} ${zone.capitalize()} Door", isComponent: false])
        child.updateDataValue("haApplianceId", applianceId)
        child.updateDataValue("doorZone",      zone)
    }
}
```

---

## 3. Folder Layout

```
drivers/
  bosch-home-connect/
    bosch-home-connect-app.groovy        ← Parent App (OAuth + discovery + poll)
    bosch-home-connect-child.groovy      ← Child Driver (ContactSensor per door)
    README.md                            ← Install + OAuth setup steps
    packageManifest.json                 ← HPM manifest (UUID v4, never changed)
    CHANGELOG.md
```

> **Naming note:** Because the OAuth entry point is a Hubitat *App*, the main file is `*-app.groovy`, not `*-parent.groovy`. The child driver is still a Driver. Mirror SunStat conventions everywhere else (namespace `mads`, kebab-case, `logEnable`/`txtEnable` pair, 30-min auto-off).

---

## 4. Effort Estimate

| Work item | Owner | Size | Notes |
|-----------|-------|------|-------|
| Bosch developer portal setup (client_id, redirect URI) | Mads | 15–30 min | One-time; requires exact HTTPS callback URL from hub |
| Parent App: OAuth flow + token refresh | Tank | Medium | New pattern vs SunStat (App not Driver); ~150–200 lines |
| Parent App: appliance discovery + polling | Tank | Small-medium | Very similar to SunStat `discoverDevices()` + `refresh()` |
| Child Driver: ContactSensor + parseApplianceState | Tank | Small | ~100 lines; straightforward attribute mapping |
| SSE design (if polling rate-limited) | Cypher → Tank | Large | Requires platform research; deferred |
| Real-device testing (Mads' fridge) | Switch + Mads | Medium | API response shapes need real data to validate |
| README + install guide + OAuth walkthrough | Link | Small | ~1–2 hours |

**Overall size: Medium — 2 sessions** (Session 1: Cypher scouts API + OAuth redirect URI feasibility; Session 2: Tank implements + Switch tests). Add a Session 3 if SSE becomes mandatory.

---

## 5. Risks & Open Questions

### 🔴 Blocking
1. **Redirect URI exact-match:** Bosch requires a pre-registered HTTPS redirect URI. Does `https://cloud.hubitat.com/api/<hub-id>/apps/<app-id>/oauth/callback` qualify? Hub IDs are per-hub — Bosch typically requires a static URI registered at developer portal time. **→ Cypher: check Bosch Home Connect OAuth docs for redirect URI requirements. Community drivers (e.g. dacobi/hubitat-bosch-home-connect) may have solved this.**

2. **App vs Driver for OAuth:** Confirmed platform rule — only Apps get `mappings {}`. If we want true in-app OAuth (no external tool), the entry point must be a Hubitat App. This is a different pattern than SunStat's `setRefreshToken()` command approach. **Mads: confirm you're comfortable with an App (vs the `setRefreshToken` workaround used for Watts).**

### 🟡 Design Questions
3. **Rate limits:** Home Connect API is documented at ~1 req/s per appliance. At 2 appliances, 30-s poll = 4 req/30 s — should be fine. Cypher to confirm.

4. **Token refresh cadence:** Home Connect access tokens expire in 86400 s (24 h); refresh tokens are long-lived but may require rotation. Pattern from SunStat (`proactiveTokenRefresh` 300 s before expiry via `runIn`) applies directly.

5. **SSE feasibility on Hubitat:** `asynchttpGet` fires a callback on response start, not on stream end. Holding the connection open for SSE requires either a tight reconnect loop or a platform feature that doesn't exist. **→ Cypher: probe whether any Hubitat community driver has successfully used SSE via asynchttpGet or alternative.**

6. **Cloud unreachable / stale state:** When Bosch API is down, should child devices show `contact: unknown` or retain last-known value? Recommendation: emit `deviceOnline: false` and leave `contact` at its last value (rule conditions can gate on `deviceOnline`). Mads to confirm preference.

7. **Single fridge (known topology):** If Mads only ever has one fridge, single-appliance support is enough for v1. Multi-appliance discovery is cheap to add but not needed immediately.

### 🟢 Non-risks (solved by existing patterns)
- Token storage in `state.*` (not preferences) — already our convention.
- Child device naming / `isComponent: false` — already our convention.
- `logEnable`/`txtEnable` pair — already our convention.
- `descriptionText`-prefixed events — already our convention.

---

## 6. Team Assignments (Proposed)

| Agent | Task |
|-------|------|
| **Cypher** | Scout Bosch Home Connect OAuth docs; confirm redirect URI pattern; check community drivers (dacobi/hubitat-bosch-home-connect); assess SSE feasibility; document rate limits |
| **Tank** | Implement App + child driver once Cypher's scout is done |
| **Switch** | Draft test plan from capability map above once Tank has scaffold |
| **Link** | README + OAuth setup walkthrough once real-device shape is known |
| **Mads** | Register Bosch developer account; confirm App-vs-setRefreshToken preference; real-device testing |

---

## References

- Home Connect API: https://developer.home-connect.com/docs/
- Existing community driver: https://github.com/dacobi/hubitat-bosch-home-connect (Cypher: check architecture)
- Sunstat pattern reference: `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy`
- Hubitat Cloud OAuth App pattern: https://docs.hubitat.com/index.php?title=App_OAuth


---

## tank-touchstone-v0128

---
author: tank
date: 2026-05-18T20:29:23-07:00
status: ready-for-review
subject: Touchstone v0.1.28 — parse buffer dedupe (perf todos #2/#4)
---

## 2026-05-18 — Tank — Touchstone v0.1.28 (perf todos #2/#4)

### What changed
- In `drivers/touchstone-fireplace/touchstone-fireplace.groovy`, `parse()` now builds the concatenated receive buffer locally and passes it into `consumeReceiveBuffer(buffer)`; only leftover partial-frame hex is written back to `state.rxBuffer`, and the state key is removed when the chunk was fully consumed.
- Added parse-only event dedupe helpers and routed parsed `heatLevel`, `flameColor`, `flameBrightness`, `charcoalColor`, `flameSpeed`, `heatingSetpoint`, and `temperature` updates through them so unchanged push/refresh frames stop creating duplicate Events rows.
- Left the existing command-path `emitAttribute(..., "digital")` behavior intact, so user-issued writes still produce immediate digital echoes after a real outbound DP write.
- Bumped the Touchstone driver metadata/changelog to `0.1.28`, updated `drivers/touchstone-fireplace/packageManifest.json` to `0.1.28`, and documented the reusable parse-path dedupe rule in `.squad/skills/hubitat-event-hygiene/SKILL.md`.

### Why
- Both requested perf items were on the hot inbound socket path. Avoiding full-buffer state writes and unchanged parse events cuts Hubitat state/database churn on every heartbeat, refresh, and physical-remote push without changing Tuya frame parsing behavior.
- Separating parse dedupe from command echoes preserves UX: automations still get an immediate digital confirmation when they actually changed device state, but the later device echo no longer clutters event history with redundant rows.

---

## tank-gemstone-v0415

---
author: tank
date: 2026-05-18T20:29:23-07:00
status: ready-for-review
subject: Gemstone v0.4.15 — cloneMap copy hygiene + refresh dedupe (perf todos #3/#4)
---

## 2026-05-18 — Tank — Gemstone v0.4.15 (perf todos #3/#4)

### What changed
- In `drivers/gemstone-lights/gemstone-lights.groovy`, `cloneMap()` no longer round-trips through JSON. It now recursively clones only mutable containers (`Map`, `List`) and reuses scalar values, which matches the real hot-path shapes used by Gemstone patterns, queued requests, callback data, and cached effect patterns.
- Added refresh-only event dedupe helpers and routed `handleRefreshResponse()` switch/level/hue/saturation emits through them so unchanged poll payloads stop creating duplicate Events rows.
- Left the existing command-path `sendEvent(..., type: "digital")` behavior alone for `on/off/setLevel/setColor/setColorTemperature` and effect activation; only refresh telemetry now skips unchanged emits.
- Bumped Gemstone metadata/changelog to `0.4.15`, updated `drivers/gemstone-lights/packageManifest.json` to `0.4.15`, and captured the reusable copy-hygiene rule in `.squad/skills/hubitat-hot-path-copy-hygiene/SKILL.md`.

### Why
- Both requested items sat on Gemstone's hot paths. Removing JSON serialization from internal map copies cuts avoidable CPU/GC work during refreshes, retries, queueing, and effect activation while still isolating mutable nested structures from `state`.
- Dedupe on the refresh/poll path keeps Hubitat's event history stat-oriented instead of echoing identical cloud telemetry, without taking away the immediate digital confirmation users expect after a real command.

---

## tank-touchstone-v0129

---
author: tank
date: 2026-05-18T20:29:23-07:00
status: ready-for-review
subject: Touchstone v0.1.29 — drop lastDps + byte copy helpers (perf todos #6/#7)
---

## 2026-05-18 — Tank — Touchstone v0.1.29 (perf todos #6/#7)

### What changed
- Removed the hot-path `state.lastDps = dps` write from `processFrame()` after re-grepping the driver and confirming there are no `state.lastDps` readers to migrate.
- Added one-time `state.remove("lastDps")` cleanup in `initialize()` so upgraded devices drop the stale state key without reintroducing parse-path state churn.
- Reworked `concatBytes()`, `sliceBytes()`, `startsWithBytes()`, and `protocol33HeaderBytes()` to use primitive `int` counters, plus `System.arraycopy(...)` for contiguous copies in concat/slice/header assembly.
- Bumped the Touchstone driver metadata/changelog to `0.1.29`, updated `drivers/touchstone-fireplace/packageManifest.json` to `0.1.29`, and captured the byte-helper optimization pattern in `.squad/skills/tuya-local-groovy/SKILL.md`.

### Why
- Both requested fixes live on the Tuya v3.3 send/receive hot path. Removing dead state writes and boxed byte-copy loops reduces Hubitat overhead without touching AES framing, the pure-Groovy CRC32 implementation, or any reflection-sensitive sandbox areas.

### Guardrails kept
- No reflection APIs introduced.
- Pure-Groovy CRC32 path left unchanged.
- Helper surface stays on plain `byte[]`; only the counter/copy mechanics changed.

---

## tank-sunstat-sc4

---
author: tank
date: 2026-05-18T20:29:23-07:00
status: ready-for-review
subject: SunStat v0.1.10 — cache Floor.W warmth, skip redundant PATCH (SC-4 closes audit)
---

## 2026-05-18 — Tank — SunStat v0.1.10 (SC-4)

### What changed
- In `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy`, `parseDeviceStateInternal()` now caches `data.Schedule.Floor.W` into `state.floorWarmth` alongside the existing `state.floorAway` cache.
- `setFloorMinTemp(temp)` now compares the clamped request against that cached warmth value and returns early with the standard debug skip log when the thermostat already matches, before issuing the read-modify-write PATCH.
- Bumped the synced SunStat parent/child/package-manifest versions to `0.1.10`; the parent change is version-sync only.

### Why
- `setFloorMinTemp()` has to PATCH both `Schedule.Floor.W` and `.A` together. Without a cached warmth value, repeated floor-min assertions still performed a no-op cloud write even when the thermostat already matched.
- This closes SC-4, the last unshipped repo-backed item from Trinity's redundant-write audit, so the SunStat audit board is now empty.

### Guardrails kept
- Null or unknown cached warmth still falls through to the PATCH, so fresh installs and first-poll devices keep working.
- Existing `state.floorAway` read-modify-write behavior is unchanged.
- No user-command digital events were removed; only confirmed no-op writes are skipped.

---

## tank-capability-markers

---
author: tank
date: 2026-05-18T20:29:23-07:00
status: ready-for-review
subject: Cloud driver metadata — add Polling/Actuator capabilities (final perf todo)
---

## 2026-05-18 — Tank — Cloud driver metadata hygiene

### What changed
- In `drivers/gemstone-lights/gemstone-lights.groovy`, added `capability "Polling"` to the metadata definition and bumped the driver + `drivers/gemstone-lights/packageManifest.json` to `0.4.16`.
- In `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy`, added `capability "Polling"` and `capability "Actuator"` to the parent metadata definition and bumped the parent to `0.1.11`.
- Synced `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy` and `drivers/sunstat-thermostat/packageManifest.json` to `0.1.11` so the SunStat release stays parent/child aligned.

### Why
- Both cloud drivers already implement `poll()`, but Hubitat app discovery keys off declared capabilities. Adding `Polling` advertises the existing command without changing behavior.
- The SunStat parent exposes commands (`discoverDevices`, `setHome`, `setAway`, `setAwayMode`, `setRefreshToken`) and should advertise `Actuator` as the marker that it accepts commands. This closes the final perf/quality todo from Tank's 2026-05-18 board.

### Guardrails kept
- No explicit `command "poll"` declarations were added; `capability "Polling"` already defines that contract.
- `capability "Actuator"` is treated as marker-only; no duplicate commands were introduced.
- SunStat child behavior is unchanged in `0.1.11`; the child bump is version-sync only.
