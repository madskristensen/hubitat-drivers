# Decisions

Generated 2026-05-18T17:59:14Z

---

## tank-daikin-wifi-v014-roadmap-complete

---
author: tank
date: 2026-05-18T14:00:15-07:00
status: shipped
subject: Daikin WiFi v0.1.4 — roadmap complete (econo/powerful, get_model_info cache, event hygiene)
---

v0.1.4 bundles the three remaining roadmap items from Trinity's v0.1.0 capability gap memo. The driver is now feature-complete against the initial roadmap.

### What shipped in v0.1.4

1. **setSpecialMode + specialMode attribute** — get_special_mode / set_special_mode implemented; specialMode enum [off/econo/powerful]; polled every fast-refresh cycle.
2. **get_model_info runtime capability cache** — Called once in initialize(); caches model name, firmware, en_hum flag, swing flags in state.modelInfo. Diagnostic only in v0.1.4.
3. **Event hygiene audit** — All five checks passed (emitIfChanged on parse paths, descriptionText on all sendEvent, lastActivity throttle >=60s, no displayed:false, no isStateChange:true). Driver was already clean.

### Protocol details needing hardware verification

- adv field bitmap values: econo=2, powerful=12 (community-documented; unverified on Mads's hardware)
- get_model_info field names: model, rev, en_hum, swing_l, swing_v (community-documented; firmware-variant dependent)
- Compound adv strings like '2-fff10000': split-on-dash defensive parse

### Deferred items

- On-device timer (get_program/set_program) — use Hubitat rules instead
- Parent/child multi-unit — Mads has one unit (Trinity directive)
- EZ Dashboard JSON_OBJECT attributes — v0.1.2 candidate, not yet requested

### Commit

SHA 1dd21fe — feat(daikin-wifi): v0.1.4 econo/powerful + model_info + event hygiene

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

---

## tank-touchstone-v130-arraycopy-fix

---
author: tank
date: 2026-05-18T11:30:22-07:00
status: done
subject: System.arraycopy is Sandbox-Blocked on Hubitat — Touchstone v0.1.30 hotfix
---

# Decision: System.arraycopy is Sandbox-Blocked on Hubitat

**Date:** 2026-05-18  
**Driver:** Touchstone / Tuya Fireplace  
**Versions affected:** v0.1.29 (broken), v0.1.30 (fixed)  
**Author:** Tank

---

## Sandbox Restriction

`java.lang.System.arraycopy` is on the Hubitat driver sandbox's MethodCallExpression blocklist. The Hubitat sandbox rejected v0.1.29 at install time with:

> `Expression [MethodCallExpression] is not allowed: java.lang.System.arraycopy(part, 0, combined, offset, part.length) at line number 1428`

This is the same class of restriction as `java.util.zip.CRC32` (blocked via import allowlist, confirmed v0.1.2) and the reflection API block. The Hubitat sandbox enforces **both** import-level and expression-level restrictions.

---

## Three Blocked Call Sites (v0.1.29)

All three were introduced in v0.1.29 as part of perf todo #7:

| Location | Line (v0.1.29) | Call |
|---|---|---|
| `concatBytes(byte[]... arrays)` | 1428 | `System.arraycopy(part, 0, combined, offset, part.length)` |
| `sliceBytes(byte[] source, int start, int length)` | 1452 | `System.arraycopy(source, start, copy, 0, length)` |
| `protocol33HeaderBytes()` | 1472 | `System.arraycopy(versionBytes, 0, header, 0, versionBytes.length)` |

---

## Revert Pattern (v0.1.30)

Each `System.arraycopy(src, srcOff, dest, destOff, length)` was replaced with a primitive for-loop:

```groovy
for (int i = 0; i < length; i++) { dest[destOff + i] = src[srcOff + i] }
```

The primitive `int` loop counters introduced in v0.1.29 were **retained** — only the `arraycopy` calls were reverted.

Specific replacements:

**concatBytes** (was line 1428):
```groovy
// before (blocked):
System.arraycopy(part, 0, combined, offset, part.length)
// after (safe):
for (int i = 0; i < part.length; i++) { combined[offset + i] = part[i] }
```

**sliceBytes** (was line 1452):
```groovy
// before (blocked):
System.arraycopy(source, start, copy, 0, length)
// after (safe):
for (int i = 0; i < length; i++) { copy[i] = source[start + i] }
```

**protocol33HeaderBytes** (was line 1472):
```groovy
// before (blocked):
System.arraycopy(versionBytes, 0, header, 0, versionBytes.length)
// after (safe):
for (int i = 0; i < versionBytes.length; i++) { header[i] = versionBytes[i] }
```

---

## Perf Todo #7 — PERMANENTLY CLOSED

Perf todo #7 ("switch hot byte-copy helpers to `System.arraycopy()`") is **permanently unachievable on Hubitat**. The sandbox blocks the call expression regardless of import status. Do not attempt to re-open or re-approach this todo.

The primitive for-loop pattern is the correct and final implementation for all byte-copy helpers in Hubitat Groovy drivers. The autoboxing improvement from primitive `int` counters (also part of todo #7) was safe and is retained in v0.1.30.

---

## Rule Going Forward

**Never use `System.arraycopy` in Hubitat Groovy drivers.** Add to the driver review checklist alongside CRC32 and reflection API checks. When reviewing any new byte-copy helper, require a primitive `for` loop.

---

## cypher-daikin-upstream-prs-assessment

---
author: cypher
date: 2026-05-18T12:32:14-07:00
status: ready-for-review
subject: Adopt Upstream Daikin PRs into v0.1.1?
---

# Decision: Adopt Upstream Daikin PRs into v0.1.1?

**Date:** 2026-05-18  
**Author:** Cypher  
**Status:** ✅ Ready for Mads review

---

## Bottom Line

**Do not adopt either PR into v0.1.1.** Our clean-room v0.1.0 already addresses or surpasses both upstream PRs.

- **PR #2 (Dashboard Tiles):** ❌ Uses incorrect JSON serialization (manual escaped quotes). **Skip.** Our v0.1.0 solves this properly with `JsonOutput.toJson()`.
- **PR #3 (EZ Dashboard):** 🟡 Adds useful polish (JSON_OBJECT type declarations + optional setter methods) **but not critical for v0.1.1.** Defer to v0.1.2 if users report EZ Dashboard rendering issues.

---

## v0.1.1 Priority Unchanged

**Current v0.1.1 roadmap** (from `.squad/decisions.md`):
1. Econo mode support
2. get_model_info capability
3. Full event hygiene (descriptionText on all events)

Both upstream PRs are lower priority than these deliverables.

---

## PR-by-PR Verdict

### PR #2: Dashboard Tiles (2023-03-19)

| Aspect | Verdict |
|--------|---------|
| Behavior | Emit `supportedThermostatModes` and `supportedThermostatFanModes` for dashboard recognition |
| Quality | ❌ Footgun: uses manual `["\"auto\"","\"cool\"",...]` instead of proper JSON library |
| Compat | ✅ Already solved in v0.1.0 (using `JsonOutput.toJson()`) |
| Adoption | 🔴 **Skip** — our implementation is superior |

**Why we're ahead:** Our v0.1.0 uses the correct Hubitat pattern (JsonOutput) instead of a workaround that works only via defensive dashboard parsing.

### PR #3: EZ Dashboard (2024-06-26)

| Aspect | Verdict |
|--------|---------|
| Behavior | Declare `supportedThermostatModes`/`supportedThermostatFanModes` as JSON_OBJECT; add `.isNumber()` guard for outdoor-temp unavailable case; optional setter methods |
| Quality | ✅ Good — targets Hubitat 2.3.3+ schema and fixes real crash vector |
| Compat | 🟡 Partially: crash fix already in v0.1.0, but missing attribute type declarations |
| Adoption | 🟡 **Maybe later** — Polish worth v0.1.2 if needed |

**What we're missing:**
- Explicit `attribute ... JSON_OBJECT` declarations (5 min to add)
- Two optional setter methods (useful for RM/dashboard runtime overrides, 15 min)

**What we already have:**
- `.isNumber()` guard for outdoor-sensor-unavailable case ✅

---

## Effort Estimate (if v0.1.2 adopts PR #3)

| Task | Est. Hours |
|------|-----------|
| Add attribute type declarations to metadata | 0.1 |
| Implement `setSupportedThermostatFanModes()` + `setSupportedThermostatModes()` | 0.25 |
| Test on EZ Dashboard + RM | 1 |
| **Total** | **~1.5 hours** |

Low friction — only add if users report dashboard issues.

---

## Recommendation

### v0.1.1: No PR adoption
Focus on econo mode, get_model_info, event hygiene. Both upstream PRs can safely wait.

### v0.1.2: Revisit PR #3 if needed
Monitor user feedback. If EZ Dashboard rendering issues appear, implement PR #3's attribute type declarations + setter methods. Do not adopt PR #2's JSON approach.

### Never: PR #2's JSON pattern
Keep `JsonOutput.toJson()` — it's the correct Hubitat idiom and works properly.

---

## Full Assessment

See `.squad/files/daikin-research/daikin-upstream-prs-assessment.md` for detailed code analysis and citations.


---

## cypher-driver-opportunities-survey

---
author: cypher
date: 2026-05-18T15:28:26-07:00
status: recommendation
subject: Driver opportunity survey + fit rubric (HA-vs-Hubitat gap analysis)
---

# Driver Opportunities — Cypher Shortlist
**Author:** Cypher (Integration / Protocol Engineer)  
**Date:** 2026-05-18  
**Status:** Recommendation — for Mads's review

---

## 1. Executive Summary

Five candidates stand out clearly above the rest. **Enphase Envoy** is the single strongest Pool A pick: a local-first solar gateway with a plain HTTP REST API, zero maintained Hubitat driver, and extremely high PNW market penetration. **Tesla Wall Connector Gen 3** is the easiest win in the entire list — unauthenticated LAN REST, two endpoints, done. In Pool B, **Tibber** is the correct energy-price trigger: the developer token is free, the GraphQL query is four lines, and the API has never been cloud-killed. Rounding out the top tier: **Reolink** (local doorbell-as-trigger, officially API-sanctioned) and **Mitsubishi mini-split via ESPHome** (ESPHome REST pattern already in our skills, biggest PNW HVAC segment). Everything else is either cloud-fragile, binary-protocol hostile, or already covered by Maker API.

---

## 2. Pool A — Physical Device Candidates

| Brand / Device | HA integration | Hubitat status | Protocol shape | Local? | Demand signal | Fit score | Notes |
|---|---|---|---|---|---|---|---|
| **Enphase Envoy / IQ Gateway** | ✅ Active — `ha.io/integrations/enphase_envoy` | ❌ None (stale 2021 community attempt) | `GET /api/v1/production` → JSON; fw7+ adds JWT (12-month cache) | ✅ Full local | Very high — r/Hubitat, Hubitat forum solar threads | **9** | Flag: fw7+ needs one-time cloud token fetch to seed the JWT. After that, purely local. |
| **Tesla Wall Connector Gen 3** | ✅ Active — `ha.io/integrations/tesla_wall_connector` | ❌ None | `GET /api/1/vitals` → JSON — no auth, unauthenticated LAN | ✅ Full local | High — Hubitat EV threads | **9** | Simplest protocol in this list. Returns state, session_energy_wh, grid_v, vehicle_power_w. Mains-connected, always on LAN. |
| **Mitsubishi Electric mini-split (ESPHome CN105 bridge)** | ✅ ESPHome — `ha.io/integrations/esphome` | ❌ None | ESPHome REST (climate domain) — pattern in `hubitat-asynchttpget-pattern` skill | ✅ Full local | Very high — PNW heat pump dominant brand | **8** | Requires user to own CN105-to-ESP bridge (esphome-mitsubishi-cn105 firmware). Driver pattern already coded for Touchstone. Flag for feasibility report. |
| **Reolink camera / doorbell** | ✅ Active (Reolink-authorized) — `ha.io/integrations/reolink` | ❌ None maintained | `POST /api.cgi?cmd=Login` for token; `GET /api.cgi?cmd=GetMdState&token=<t>` for state | ✅ Full local | High — doorbell-as-trigger request in multiple forum threads | **8** | Official Reolink endorsement removes cloud-kill risk. Doorbell motion + visitor press → trigger. |
| **OpenEVSE** | ✅ Active — `ha.io/integrations/openevse` | ❌ None | `GET /status` → JSON, `POST /rapi` for commands; local HTTP, optional basic auth | ✅ Full local | Medium — DIY EV charger audience | **7** | Popular with maker/hobbyist EV crowd. Smaller total addressable audience than Tesla WC but simpler protocol. |
| **Rachio sprinkler** | ✅ Active — `ha.io/integrations/rachio` | ⚠️ Stale community driver (2022) | REST `api.rach.io/1/public/…` with `X-Rachio-Auth` key; webhook push for real-time | ☁️ Cloud only | High — major PNW garden brand | **7** | HA warns: webhook push requires Hubitat to be internet-reachable. Smart hose timers use polling only. Cloud-kill risk: **medium** (Rachio is VC-funded, track record OK so far). |
| **SolarEdge** | ✅ Active — `ha.io/integrations/solaredge` | ⚠️ Stale community driver | Cloud REST `monitoring.solaredge.com/site/{id}/overview` + API key | ☁️ Cloud only | Medium-high — large US install base | **6** | Rate-limited to 300 calls/day. No local API without optional Modbus module. Hubitat can't do Modbus natively. Cloud-kill risk: **low** (vendor wants monitoring). |
| **Husqvarna Automower** | ✅ Active — `ha.io/integrations/husqvarna_automower` | ❌ None | REST with OAuth2 bearer; free developer portal at developer.husqvarnagroup.cloud | ☁️ Cloud only | Medium — EU robotic mower segment | **6** | Free developer registration confirmed active. OAuth2 complexity manageable via `cognito-from-hubitat` skill pattern. EU/Scandinavian fit for Mads's context. |
| **Flo by Moen** | ✅ Active — `ha.io/integrations/flo` | ⚠️ Dead community driver (2020) | Cloud REST (unofficial reverse-engineered API — not a published SDK) | ☁️ Cloud only | Medium | **5** | **Cloud-kill risk: HIGH** — API is unofficial, no developer program. Moen can break it like Chamberlain broke MyQ. Do not invest. |
| **Pentair ScreenLogic (pool)** | ✅ Active — `ha.io/integrations/screenlogic` | ❌ None | Proprietary binary TCP via ScreenLogic gateway (python-screenlogic lib) | ✅ Local | High — pool automation is top requested | **5** | Protocol: binary framing, not HTTP/JSON. Not sandbox-safe for Hubitat without sidecar service. Flag as "requires sidecar" before writing anything. |
| **Velux KLF 200 (motorized windows/blinds)** | ✅ Active — `ha.io/integrations/velux` | ❌ None | Proprietary binary TCP SLIP frames via KLF 200 gateway | ✅ Local gateway | Low-medium | **4** | Same problem as Pentair — binary protocol kills sandbox. Skip unless a REST proxy appears. |
| **Flexit Nordic ERV/HRV** | ✅ Active — `ha.io/integrations/flexit_bacnet` | ❌ None | BACnet over UDP/Ethernet | ✅ Local | Low (EU niche) | **3** | BACnet requires UDP library. Hubitat sandbox has no BACnet support. Skip. |

---

## 3. Pool B — Cloud Service / Trigger Candidates

| Service | HA integration | Hubitat status | Protocol shape | Local option? | Demand signal | Fit score | Notes |
|---|---|---|---|---|---|---|---|
| **Tibber energy price** | ✅ Active — `ha.io/integrations/tibber` | ❌ None | `POST https://api.tibber.com/v1-beta/gql` + Bearer token; GraphQL 4-line query | ☁️ None (price data is inherently cloud) | High — huge Scandinavia user base, Mads is Danish | **9** | Free developer token. Returns `current { total currency level }`. No pagination, no rate limit documented. Cloud-kill risk: **very low** (it's their growth funnel). |
| **PurpleAir AQI** | ✅ Active — `ha.io/integrations/purpleair` | ❌ None | `GET https://api.purpleair.com/v1/sensors/{id}?fields=pm2.5_atm,…` with `X-API-Key` | ☁️ API only (sensor data is in cloud; owned sensors can serve local JSON via LAN too) | High — PNW wildfire smoke season driver | **8** | Free tier: 1M points included. pm2.5, AQI, temp, humidity. Owned PurpleAir sensors also expose local JSON at `http://<sensor-ip>/json`. Local path possible. Cloud-kill risk: **low** (commercial API, active community). |
| **Nord Pool energy price** | ✅ Active — `ha.io/integrations/nordpool` | ❌ None | `GET https://dataportal-api.nordpoolgroup.com/api/DayAheadPrices?market=N2EX_DayAhead&…` — **no auth required** | ☁️ None | Medium — EU spot-price contracts | **7** | Literally no auth. Returns today + tomorrow day-ahead price arrays. EU-only relevance. 15-min MTU transition in progress. Cloud-kill risk: **very low** (market infrastructure). |
| **Ecowitt weather station (gateway push)** | ✅ Active — `ha.io/integrations/ecowitt` | ❌ None | Device pushes HTTP POST to a configurable URL every N seconds; no polling needed | ✅ Local push (to Hubitat app endpoint) | Medium | **7** | **Shape change:** this is a push receiver, not a polling driver. Needs a Hubitat app with `mappings` endpoint, not a driver. Flag: implement as App, not Driver. Would receive wind/rain/temp/soil/lightning from Ecowitt GW2000+. |
| **Rachio webhook (Pool B angle)** | — (same as Pool A row) | — | Webhook `POST` to Hubitat Maker API endpoint on zone start/stop | ☁️ Cloud push | Medium | **6** | This is the Maker API pattern — no driver needed. Rachio sends webhook to Hubitat endpoint on zone events. Document in repo README rather than building a driver. |
| **NOAA weather alerts** | ✅ Active (via `weather` + `template`) | ❌ None | `GET https://api.weather.gov/alerts/active?zone=WAC053` — no auth, REST/GeoJSON | ✅ No auth | Medium — tornado/severe weather alerts for automations | **6** | PNW relevance moderate (storms, not tornadoes). Free, never going away (government). Simple driver: poll every 5 min, fire alert event when active warnings exist. |
| **Octopus Energy (Agile pricing)** | ✅ Active — `ha.io/integrations/octopus_energy` | ❌ None | `GET https://api.octopus.energy/v1/products/AGILE-22-07-22/electricity-tariffs/E-1R-AGILE-22-07-22-L/standard-unit-rates/` — no auth | ☁️ None (UK only) | Low-Medium — UK users only | **5** | UK electricity market only. Mads is in PNW — low personal fit. Good if there's a UK user base request. Cloud-kill risk: **low**. |
| **GitHub Actions webhook receiver** | No HA integration | ❌ None | Maker API pattern covers this | N/A | Very low | **2** | Use Maker API + HMAC signature check in a Rule. No driver needed. |

---

## 4. Top 5 Ranked Picks

### #1 — Enphase Envoy / IQ Gateway (Pool A)
**Why:** Largest addressable audience in the repo after garage doors. PNW has high solar penetration. The local REST endpoint (`/api/v1/production`) requires zero auth on firmware <7 and returns real watts + lifetime Wh in a clean JSON object. Firmware 7+ adds JWT authentication — the JWT is fetched once from Enlighten cloud, valid 12 months, then used purely locally. No community Hubitat driver exists. This is a clean gap with strong demand.  
**Effort:** M — small driver, but need to handle fw7 JWT token seeding (one cloud call in `initialize()`, stored in `state.token`).  
**Key risks:** Enphase keeps changing Envoy firmware auth. The fw7 JWT path requires the user to enter Enlighten cloud credentials at install time; those credentials are only used once. Need robust error message if token expires.  
**Hardware needed:** Enphase IQ Gateway (any Envoy model fw 3.9+). Common — borrow or purchase used.

### #2 — Tesla Wall Connector Gen 3 (Pool A)
**Why:** The simplest protocol in this entire document. `GET http://<ip>/api/1/vitals` → JSON. No auth. No cloud dependency. Auto-discovery via mDNS (HA does this, but polling by IP is fine for Hubitat). Returns charging state, session energy, grid voltage, vehicle power, handle temp. Hubitat capabilities: `EnergyMeter`, `PowerMeter`, custom state attribute. A single-file driver, under 200 lines.  
**Effort:** S — one endpoint, one polling loop, no auth, no parent/child needed.  
**Key risks:** Gen 3 only — Gen 1/2 have no WiFi. If Tesla removes the local API in firmware update, driver breaks (low probability — they've kept it open through 2026).  
**Hardware needed:** Tesla Wall Connector Gen 3 with WiFi. Very common in PNW tech households.

### #3 — Tibber Energy Price (Pool B)
**Why:** Tibber is the dominant smart-electricity provider in Scandinavia, growing rapidly in DE/NL. Mads is Danish. The API is explicitly developer-friendly with a free token at `developer.tibber.com/settings/accesstoken`. One GraphQL POST returns current price, tier (`VERY_CHEAP`/`CHEAP`/`NORMAL`/`EXPENSIVE`/`VERY_EXPENSIVE`), and currency. This is the correct primitive for energy-aware automations (charge EV when CHEAP, run laundry when VERY_CHEAP). No maintained Hubitat driver exists.  
**Effort:** S — single GraphQL query, Bearer token, poll every 60 min (price changes hourly). Parent/child not needed.  
**Key risks:** Tibber is cloud-only by nature (energy pricing is not local). Tibber could restrict the API, but it's their developer acquisition funnel. Cloud-kill risk: very low.  
**Hardware needed:** Tibber customer account. Non-customers can still poll public price data for their region with a demo token.

### #4 — Reolink Camera / Doorbell (Pool A)
**Why:** Reolink officially authorized the HA integration, which means their local HTTP API is sanctioned — not reverse-engineered and at risk of being killed. The CGI API (`/api.cgi?cmd=…`) provides motion detection state, doorbell visitor press events, and camera status over LAN. The doorbell-as-trigger use case is the highest-value automation primitive most users lack. No maintained Hubitat driver exists.  
**Effort:** M — login to get session token, then poll motion + visitor events; multiple entity types (motion sensor, contact sensor for button); token refresh on 401.  
**Key risks:** API is proprietary (not ESPHome standard); firmware updates could change endpoint paths. Official endorsement reduces but doesn't eliminate this risk.  
**Hardware needed:** Any Reolink camera with local API (most 2020+ models). Doorbell models: E1 Outdoor, Video Doorbell WiFi.

### #5 — Mitsubishi Electric Mini-Split via ESPHome CN105 Bridge (Pool A)
**Why:** Mitsubishi Electric dominates the PNW mini-split market. No official local API exists — but the community-maintained `esphome-mitsubishi-cn105` firmware (GitHub: geoffdavis/esphome-mitsubishi-cn105) exposes the unit via ESPHome REST, which is the exact same pattern we already used for the ratgdo driver and know from our skills. Driver writing cost is low.  
**Effort:** S for the driver; M total (user must own CN105-to-ESP32 bridge hardware and flash ESPHome firmware — ~$15 parts + 30 min setup).  
**Key risks:** Requires the user to buy and flash a CN105 bridge; not plug-and-play. Driver is not useful without bridge hardware. Some users may have MelCloud cloud integration instead — that path has cloud-kill risk (Mitsubishi has restricted third-party MelCloud access before).  
**Hardware needed:** CN105-capable ESP32 board (e.g., M5StickC Plus, ~$15) flashed with esphome-mitsubishi-cn105. Driver development does NOT require a real Mitsubishi unit if using ESPHome simulator.

---

## 5. Honorable Mentions

- **OpenEVSE** — Local HTTP REST, solid API, but audience is narrow (DIY EV charger builders, not mainstream).
- **Husqvarna Automower** — Free OAuth2 developer portal, EU/Scandinavian fit, but OAuth2 flow in Hubitat is non-trivial and audience is garden enthusiasts.
- **Nord Pool energy price** — Complements Tibber; no-auth REST, EU spot prices. Low effort but EU-only. Build after Tibber.
- **NOAA weather alerts** — Useful PNW trigger (atmospheric river, freeze warning). No auth. But narrow automation use case — could be done in 1 hour.
- **Rachio sprinkler** — High PNW demand, but the existing stale driver is close to functional, and Rachio's webhook push model requires internet-accessible Hubitat. Lower priority than local-first picks.
- **SolarEdge** — Higher cloud-kill risk than Enphase, rate-limited 300 calls/day. Do Enphase first.

---

## 6. Anti-List — Do Not Pursue

| Candidate | Reason |
|---|---|
| **Ring doorbell** | Cloud-only. Amazon/Ring has a hostile track record with third-party API access. Cat-and-mouse breaking pattern. Apply `cloud-killed-api-evaluation` skill: multiple "broken again" reports, no developer program for individuals. |
| **Wyze devices** | Cloud-only, repeatedly broke unauthorized API access. No local API. Pattern identical to post-2023 MyQ. Do not write a driver. |
| **Nest / Google Home** | Cloud-only. Google requires Works with Google Home approval (commercial process). Individual developers cannot participate. Dead end. |
| **Arlo cameras** | Cloud-only. Arlo broke third-party API access in 2022 and has maintained restrictions since. No local API. |
| **Flo by Moen (water shutoff)** | Unofficial reverse-engineered API — no developer program. Moen can break it without notice (MyQ precedent). The water shutoff value is HIGH but the API risk is unacceptable without a hardware alternative. |
| **Velux KLF 200** | Local, but binary SLIP-framed TCP protocol — no REST abstraction. Not Hubitat-sandbox-safe without a sidecar HTTP proxy. |
| **Flexit Nordic ERV** | BACnet over UDP — not natively supported in Hubitat sandbox. No REST path available. |
| **Pentair ScreenLogic** | Local but binary TCP protocol (python-screenlogic lib). Not Hubitat-sandbox-safe without a sidecar. Flag to revisit if a REST proxy firmware appears. |
| **MelCloud (Mitsubishi cloud)** | Mitsubishi has restricted MelCloud third-party access before. Use the local ESPHome CN105 path instead. |

---

## 7. Sources

| Candidate | URL | Retrieved |
|---|---|---|
| Enphase Envoy HA integration | https://www.home-assistant.io/integrations/enphase_envoy/ | 2026-05-18 |
| Tesla Wall Connector HA integration | https://www.home-assistant.io/integrations/tesla_wall_connector/ | 2026-05-18 |
| ESPHome HA integration | https://www.home-assistant.io/integrations/esphome/ | 2026-05-18 |
| Reolink HA integration | https://www.home-assistant.io/integrations/reolink/ | 2026-05-18 |
| Rachio HA integration | https://www.home-assistant.io/integrations/rachio/ | 2026-05-18 |
| Tibber HA integration | https://www.home-assistant.io/integrations/tibber/ | 2026-05-18 |
| PurpleAir HA integration | https://www.home-assistant.io/integrations/purpleair/ | 2026-05-18 |
| Nord Pool HA integration | https://www.home-assistant.io/integrations/nordpool/ | 2026-05-18 |
| Ecowitt HA integration | https://www.home-assistant.io/integrations/ecowitt/ | 2026-05-18 |
| SolarEdge HA integration | https://www.home-assistant.io/integrations/solaredge/ | 2026-05-18 |
| Husqvarna Automower HA integration | https://www.home-assistant.io/integrations/husqvarna_automower/ | 2026-05-18 |
| Flo by Moen HA integration | https://www.home-assistant.io/integrations/flo/ | 2026-05-18 |
| Pentair ScreenLogic HA integration | https://www.home-assistant.io/integrations/screenlogic/ | 2026-05-18 |
| Velux HA integration | https://www.home-assistant.io/integrations/velux/ | 2026-05-18 |
| OpenEVSE HA integration | https://www.home-assistant.io/integrations/openevse/ | 2026-05-18 |
| esphome-mitsubishi-cn105 (GitHub) | https://github.com/geoffdavis/esphome-mitsubishi-cn105 | 2026-05-18 |
| Tibber developer portal | https://developer.tibber.com/settings/accesstoken | 2026-05-18 |
| Husqvarna developer portal | https://developer.husqvarnagroup.cloud | 2026-05-18 |
| cloud-killed-api-evaluation skill | `.squad/skills/cloud-killed-api-evaluation/SKILL.md` | 2026-05-18 |

---

*Deep feasibility reports flagged: Enphase fw7 JWT auth, Mitsubishi CN105 ESPHome entity shape. Request follow-up from Cypher if pursuing either.*


---

## trinity-driver-fit-rubric

---
author: trinity
date: 2026-05-18T15:28:26-07:00
status: standing-team-policy
subject: Driver-candidate fit rubric — weighted scoring system with hard disqualifiers
---

# Driver Fit Rubric for hubitat-drivers

**Date:** 2026-05-18T15:28:26-07:00  
**Author:** Trinity (Lead/Architect)  
**Audience:** Mads (owner), Cypher (for candidate list), Tank (for implementation)  
**Purpose:** Filter incoming driver candidates — separates "good fit for repo" from "interesting but not for us"

---

## Part 1: House Style — What This Repo IS

**Core thesis:** Single-author-maintainable drivers for home automation, strongly preferring local-first protocols. Every driver must be reliable enough to "install and forget." Users trust Hubitat ecosystem conventions.

### Device Classes We Embrace

- **Thermostats & HVAC** — Daikin (local LAN), SunStat (cloud parent/child pattern)
- **Lighting & Effects** — Gemstone (cloud REST, OAuth2 parent/child pattern, favorites-first UX)
- **Fireplaces & Accessories** — Touchstone (local Tuya TCP socket, persistent connection, real-time push)
- **Actuators & Binary Control** — On/off devices with optional cloud fallback

### Non-Negotiable Patterns

1. **Local-first preference** — If a device offers both local LAN control and cloud, implement local. Cloud is second choice.
2. **Single protocol per driver** — Daikin = HTTP, Touchstone = Tuya TCP, not "HTTP with fallback to cloud."
3. **Parent/child for multi-device clouds** — SunStat parent (auth + polling) + child thermostats. Eliminates per-device setup.
4. **Clean Hubitat ecosystem citizen** — All event + state + scheduler + network behavior pass 7-point audit (see `.squad/skills/hubitat-driver-citizen-checklist`).
5. **Mads owns or will buy the device** — All drivers must be hardware-tested by the author. No "untested but should work" drivers.
6. **Graceful degradation** — Cloud breaks? Local survives. Endpoint missing on some firmware? Driver probes once, then disables, doesn't crash.

### What We Don't Do

- Multi-protocol or complex fallback chains ("try MQTT, fall back to HTTP, then cloud")
- "Works great with a paid gateway" integrations
- Reflection, JNI, or advanced Groovy features blocked by sandbox
- Large external dependencies bundled in driver
- Safety-critical devices without audit-trail logging at `log.info`

---

## Part 2: Scoring Rubric — Weighted Criteria

### Scoring Scale

- **✅ YES (full points)** — Criterion clearly met
- **🟡 PARTIAL (50% points)** — Criterion met with caveats
- **❌ NO (0 points)** — Criterion not met

### Criteria (Max 100 pts)

| Criterion | Max Pts | Details |
|-----------|---------|---------|
| **Local vs. Cloud Protocol** | 20 | **20 pts:** Local LAN HTTP/JSON, Tuya, or similar (Daikin, Touchstone example). **10 pts:** Cloud REST with public stable API (Gemstone, SunStat example). **5 pts:** Cloud API with known stability issues or auth workarounds required. **0 pts:** Cloud-only killed API, OAuth2 browser flow required, MQTT-only. |
| **Mads Can Test** | 15 | **15 pts:** Mads owns the device or can buy for <$100. **7 pts:** Device costs $100–$500 but fills a major gap. **0 pts:** >$500 or requires unavailable hardware. |
| **User Demand Signal** | 15 | **15 pts:** 2+ community forum threads requesting, or abandoned prior driver showing demand. **10 pts:** 1 forum thread or moderate workaround adoption. **5 pts:** Mads noticed it, no community proof yet. **0 pts:** Mads's idea alone, no external signal. |
| **Sandbox-Safe** | 15 | **15 pts:** Pure Groovy + Hubitat SDK, no reflection/JNI, secrets fit in state field. **10 pts:** Secrets need long-secret pattern (parent/child token store, documented). **5 pts:** Requires custom workaround but feasible. **0 pts:** Requires reflection, JNI, MQTT persistent subscriber, or binary protobuf decoding. |
| **Vendor API Stability** | 15 | **15 pts:** Local vendor API stable >3 years (Daikin BRP069B, Tuya v3.3), no known breaking changes, reverse-engineered is OK if community-stable. **10 pts:** Cloud API stable but proprietary (Gemstone, Watts). **5 pts:** Cloud API with history of breakage or undocumented changes. **0 pts:** Known dead/killed API (MyQ post-Oct-2023), frequent breaking changes. |
| **Effort to Ship** | 10 | **10 pts:** Straightforward single-device local control (<40h estimate). **5 pts:** Parent/child cloud or multi-device local (40–80h estimate). **0 pts:** >80h or requires new architectural pattern. |
| **Maintenance Burden** | 10 | **10 pts:** Local protocol, vendor publishes API docs, stable firmware. **5 pts:** Cloud API or community-reverse-engineered, needs monitoring for changes. **0 pts:** Killed API, frequent vendor updates, closed protocol. |

**Total: 100 points**

### Thresholds

| Score | Decision |
|-------|----------|
| **80–100** | ✅ **Strong Fit** — Prioritize. Likely belongs in repo. |
| **65–79** | 🟡 **Conditional Fit** — Check hard disqualifiers; decide based on strategic value. |
| **50–64** | ❌ **Weak Fit** — Interesting but not urgent. Defer until ecosystem matures. |
| **<50** | 🔴 **No Fit** — Recommend not pursuing unless user demand surges. |

---

## Part 3: Hard Disqualifiers

**Any candidate hitting ANY of these is OUT, regardless of score:**

1. **Cloud API is officially dead or hostile to third-party access** (MyQ post-Oct-2023, any vendor issuing C&D to open-source projects)
2. **Requires reflection, JNI, or external native libraries** (nothing can bypass Hubitat sandbox)
3. **Device costs >$500 or is hardware-unavailable** (can't test = can't maintain)
4. **Requires browser OAuth2 redirect** (Hubitat driver model doesn't support browser windows; use parent/child token pattern instead)
5. **Persistent MQTT subscriber** (Hubitat Groovy sandboxed, can't hold open MQTT connection; polling or local socket only)
6. **Binary protocol with no Groovy decoder** (protobuf, CBOR, proprietary binary; JSON/HTTP are OK)
7. **Safety-critical device without audit logging at log.info** (garage door, lock, gate — no debug-only logging for these)
8. **Requires >1KB secrets in driver preferences** (won't fit; use parent/child long-secret pattern instead)
9. **Multi-protocol with undocumented fallback logic** (too complex to maintain; one protocol per driver)
10. **Uses getClass(), reflection, or sandbox-restricted Groovy** (throws SecurityException at runtime)

---

## Part 4: Cloud-Service Trigger Patterns

### Context

Most hub integrations that poll a cloud service fit one of these patterns. Understand the shape so Cypher can recognize candidates and Trinity can pick the right one.

### Pattern A: Cloud-Polling Parent + Child Devices

**Shape:** Parent device polls cloud API on schedule. Children emit events when parent updates state.

- **Example:** SunStat (parent polls Watts API every 5 min → children emit thermostat state)
- **Code reuse:** `.squad/skills/hubitat-parent-child-cloud-driver` — Parent holds tokens, manages auth lifecycle, children are simple state mirrors
- **Hubitat pattern:** `parent.parseDeviceState(body)` → child.emitIfChanged
- **Pros:** Scales to many devices (10+ thermostats on one account). Centralizes credentials. User configures once per account.
- **Cons:** Poll latency (best case ~5 min). No real-time push from cloud.
- **Testing:** Mads needs the cloud account (email/password, refresh token, etc.). Some services (Watts) require API key extraction from Home Depot app.
- **Recommendation:** Pick this if the device class naturally has many units per account (HVAC, lights, locks).

### Pattern B: Cloud-Polling Single-Device (No Parent/Child)

**Shape:** Driver polls cloud API directly, emits attributes on device.

- **Example:** Gemstone (driver polls Gemstone cloud for light state every 5 min)
- **Code reuse:** `.squad/skills/hubitat-cloud-oauth-app` — Handle OAuth2 token refresh, asynchttpGet, callback error guards
- **Hubitat pattern:** `on()` → `asynchttpGet` → callback → `emitIfChanged`
- **Pros:** Simple architecture. One device = one virtual Hubitat device.
- **Cons:** Not scalable if user has multiple controllers (must create one Hubitat device per zone, authenticate independently).
- **Testing:** Mads needs the cloud account. Multi-zone installations need multiple Gemstone account credentials.
- **Recommendation:** Pick this only if device class is single-unit per installation (one set of LED lights, not 10 thermostats).

### Pattern C: Webhook Relay (Cloud → Maker API → Driver Event)

**Shape:** Cloud sends a POST to Hubitat's Maker API. Maker API routes webhook to a relay endpoint handler in the driver. Driver parses and emits event.

- **Example:** Not yet implemented in this repo. Hypothetical doorbell: cloud service POSTs "doorbell pressed" → Maker API `/apps/api/doorbell/press` → driver parses → emits `contact: open`
- **Code reuse:** Not yet documented (would be new pattern for Trinity to define after first use)
- **Hubitat pattern:** Maker API webhook → custom app relay → driver `parse(json)` → `emitIfChanged`
- **Pros:** Near-instant cloud → Hubitat event (no polling latency). Works for event-driven services (doorbells, motion sensors).
- **Cons:** Requires user to enable Maker API. Requires relay app or integration layer. Cloud must send HTTP POST (not all services do). Hubitat public IP exposure risk if not behind firewall.
- **Testing:** Simpler (no account sign-up needed if cloud service has test webhook sender). But requires routing configuration.
- **Recommendation:** Pick this for event-driven services (doorbell, motion) where real-time push is critical. Avoid for state-polling (thermostats, locks).

### Pattern D: Hybrid Polling + Webhook

**Shape:** Cloud service primarily sends real-time webhooks. Driver also polls as a safety-net heartbeat (in case webhook is missed).

- **Example:** Not yet implemented. Similar to webhook relay, but with fallback polling every 30 min.
- **Code reuse:** Combine Pattern C + Pattern B patterns
- **Pros:** Near-instant responsiveness (webhooks). Resilient to webhook miss (polling fallback).
- **Cons:** Complex code. Two separate event paths to debug. Higher cloud load.
- **Recommendation:** Use only if a service actively sends webhooks AND has unreliable delivery (e.g., <99% delivery SLA). Otherwise stick with one pattern.

### Cloud-Service Trigger Pattern Summary Table

| Pattern | Best For | Latency | Complexity | Testing | Example |
|---------|----------|---------|-----------|---------|---------|
| **A: Cloud-Polling Parent/Child** | Many devices per account | 5 min best case | Moderate | Needs cloud account + multi-device setup | SunStat |
| **B: Cloud-Polling Single** | One device per install | 5 min best case | Low | Needs cloud account | Gemstone |
| **C: Webhook Relay** | Event-driven (doorbell, motion) | <1 sec | Moderate | Needs Maker API + test webhook sender | Hypothetical doorbell |
| **D: Hybrid Polling + Webhook** | Mission-critical events | <1 sec (webhook), 30 min (poll) | High | Needs both webhook + account setup | Not yet used |

### DO NOT: Common Antipatterns

- **"Poll every 30 seconds"** — Kills cloud API quota, causes rate-limit blocks. Min 5 min unless service guarantees support.
- **"Webhook + fake auth in webhook body"** — Webhook payloads should NEVER include secrets. Signature verification only.
- **"Try polling, fall back to webhook"** — Pick ONE pattern. Dual-path debugging is a nightmare.
- **"Relay webhook to Rule Machine custom action"** — Defeats the point of real-time. Keep it in the driver.

---

## Part 5: Recommended Workflow

### Step 1: Cypher Surveys & Generates Candidate List

- Gathers forum threads, GitHub issues, Home Assistant integrations
- Produces a **candidate sheet** (name, device class, protocol, effort estimate, user demand signal)
- **Trinity step not involved** — let Cypher work

### Step 2: Trinity Scores Each Candidate

1. **Print the rubric** (this document)
2. **For each candidate:**
   - Check hard disqualifiers first (any YES = OUT)
   - Score each of the 7 weighted criteria
   - Sum points
   - Look up threshold
   - Note "conditional fit" caveats
3. **Output:** Ranked shortlist with scores + reasoning (1–2 lines per candidate)

### Step 3: Mads Makes Final Picks

- Trinity presents: "Top 3 are Daikin-class thermostats (90+ pts), Tuya WiFi lights (78 pts), and a garage door opener (42 pts, hard disqualifiers apply)"
- Mads decides: "Ship the 90+ club. I'll buy a WiFi light to test. Garage door is too risky."
- Mads allocates budget & timelines

### Step 4: Tank Implements

- Trinity docs the chosen candidates in `.squad/decisions.md` with rubric scores
- Tank scopes effort + priority
- Tank ships in iterations (v0.1.0 release per candidate, architecture review by Trinity if needed)

---

## Part 6: Examples (Hypothetical Scoring)

### Example 1: Ecobee Thermostat (Cloud REST, OAuth2 parent/child)

- Local protocol: ❌ NO (0) — cloud-only, no local API
- Mads can test: ❌ NO (0) — would need to buy; not owned
- User demand: 🟡 PARTIAL (10) — 1 forum thread, lots of Ecobee owners
- Sandbox-safe: 🟡 PARTIAL (10) — OAuth2 token refresh OK (parent/child pattern) but no reflection needed
- Vendor stability: ✅ YES (15) — Ecobee API stable, documented
- Effort to ship: 🟡 PARTIAL (5) — Parent/child + OAuth2 ≈ 60h estimate
- Maintenance burden: 🟡 PARTIAL (5) — Cloud API, Ecobee makes breaking changes periodically

**Total: 45 pts (🔴 NO FIT)** — Would be 50–60 range if Mads owned one, but current state is below threshold.

### Example 2: Aqara Zigbee Thermostats (Local Zigbee via Hub Mesh)

- Local protocol: ✅ YES (20) — Zigbee is local LAN
- Mads can test: 🟡 PARTIAL (7) — Can buy a unit but would be $80–$150
- User demand: ❌ NO (0) — No community forum signal yet
- Sandbox-safe: ✅ YES (15) — Pure Groovy, no special features
- Vendor stability: ✅ YES (15) — Zigbee protocol stable for 10+ years
- Effort to ship: ✅ YES (10) — Straightforward local control
- Maintenance burden: ✅ YES (10) — Local protocol = no maintenance risk

**Total: 77 pts (🟡 CONDITIONAL FIT)** — Good candidate IF Mads wants to expand into Zigbee. Check with him on prioritization.

### Example 3: DIY ratgdo Garage Door Opener (Local ESPHome HTTP)

- Local protocol: ✅ YES (20) — Local HTTP REST on ESPHome firmware
- Mads can test: 🟡 PARTIAL (7) — Hardware ~$50, but requires garage door (not all homes have)
- User demand: 🟡 PARTIAL (10) — 1–2 forum threads; garage door opener is common request
- Sandbox-safe: ✅ YES (15) — HTTP + JSON, no special features
- Vendor stability: ✅ YES (15) — ratgdo firmware is well-maintained, open source
- Effort to ship: ✅ YES (10) — Straightforward local control (similar to Daikin)
- Maintenance burden: ✅ YES (10) — Local protocol, stable firmware

**Total: 87 pts (✅ STRONG FIT)** — BUT **HARD DISQUALIFIER APPLIES:** "Safety-critical device without audit logging." Garage door must log all commands at `log.info`. If Trinity adds that requirement, score is still valid.

**Revised recommendation:** 🟡 CONDITIONAL FIT — Assign to Tank only if Mads agrees to add audit logging requirement to spec.

---

## Appendix: Audit Checklist (for Trinity's Review)

When Trinity scores a candidate, verify:

- [ ] Hard disqualifiers checked (use Part 3 checklist above)
- [ ] Cloud vs. local protocol correctly identified
- [ ] Mads ownership or budget availability confirmed
- [ ] User demand signal sourced (forum link, issue #, etc.)
- [ ] Sandbox constraints researched (APIs, auth, secrets)
- [ ] Vendor API docs obtained (if public) or reverse-engineering verified
- [ ] Effort estimate cross-checked with similar drivers (e.g., "similar to Daikin" = ~40h)
- [ ] Maintenance risk flagged if API historically breaks (cloud-only candidates)

---

## References

- **Existing drivers:** `drivers/daikin-wifi` (local HTTP), `drivers/gemstone-lights` (cloud OAuth), `drivers/sunstat-thermostat` (cloud parent/child), `drivers/touchstone-fireplace` (local Tuya socket)
- **Skills & patterns:** `.squad/skills/hubitat-parent-child-cloud-driver`, `.squad/skills/hubitat-cloud-oauth-app`, `.squad/skills/hubitat-driver-citizen-checklist`, `.squad/skills/hubitat-sandbox-pitfalls`
- **MyQ research:** `.squad/decisions.md` — Cypher's garage door feasibility report (demonstrates hard disqualifier reasoning)
- **Daikin case study:** `.squad/decisions.md` — Daikin BRP069B endpoint audit, endpoint graceful degradation pattern

---

**Last updated:** 2026-05-18T15:28:26-07:00  
**Next review:** Post-Cypher-candidate-list (iterate on thresholds if needed)

