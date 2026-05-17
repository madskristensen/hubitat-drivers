# Decisions\n\nGenerated 2026-05-17T09:40:49Z\n\n---\n\n## cypher-bosch-home-connect-feasibility\n\n---
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
\n\n---\n\n## trinity-bosch-home-connect-architecture\n\n---
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

