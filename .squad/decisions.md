# Decisions

Generated 2026-05-20T14:20:14Z
Archived entries older than 2026-05-13: see decisions/archive-*.md

---
# SunStat Connect Plus — Decisions

## Cypher: Watts Home Cloud API Specification

### 2026-05-16T20:01:41-07:00: SunStat Connect Plus / Watts Home cloud API spec
**By:** Cypher
**Status:** Research findings — derived from reverse-engineered reference implementation (homebridge-tekmar-wifi). Verify field names and mode enumeration against a real SunStat Connect Plus device.

---

## Summary

The SunStat Connect Plus is controlled through the **Watts® Home** app (`com.watts.home`, Watts Water Technologies) and its cloud backend at `https://home.watts.com/api`. A complete, working reference implementation for the same API exists: `seanami/homebridge-tekmar-wifi` (TypeScript, Homebridge plugin for Tekmar WiFi thermostats, same app and API). Auth is Azure AD B2C with PKCE — the initial login is complex, but **token refresh is a simple form POST** and is entirely feasible in Hubitat. A pragmatic driver design bootstraps with manually obtained tokens (using the homebridge CLI tool) and handles all subsequent token lifecycle in Groovy.

---

## Reference implementations found

| Name | URL | Language | Activity | What it implements | License |
|---|---|---|---|---|---|
| homebridge-tekmar-wifi | https://github.com/seanami/homebridge-tekmar-wifi | TypeScript | Updated 2026-01-19 | Full Watts Home API: auth (Azure B2C PKCE), device list, get state, set mode/temp/fan/floor, token refresh | MIT |
| pwesters/watts_vision | https://github.com/pwesters/watts_vision | Python | Updated 2024-12-27 | **EU Watts Vision API** (different product, different cloud — `smarthome.wattselectronics.com`) | — |
| roberveral/hass_watts_vision | https://github.com/roberveral/hass_watts_vision | Python | Updated 2025-01-23 | EU Watts Vision API (same as above, more complete) | Apache 2.0 |

> **Critical note:** Watts Vision (`smarthome.wattselectronics.com`) is a **European product** by Watts Electronics — a separate company and API from the North American SunStat Connect Plus. Do NOT use the Watts Vision API for SunStat. The correct API is `home.watts.com` (Watts Water Technologies NA).

> **Note on homebridge-tekmar-wifi:** The repo was built for Tekmar WiFi thermostats (hydronic radiant heating, models 561–564), which also use the Watts® Home app. The API is identical — only the `modelId`/`modelNumber` and the supported modes differ. SunStat Connect Plus is heat-only; Tekmar 562 supports Heat/Cool/Auto. All endpoints, auth tokens, headers, and response shapes are shared.

---

## Auth flow

### Overview

**Azure AD B2C, OAuth 2.0 Authorization Code with PKCE.**

Access token lifetime: **15 minutes** (900 s).
Refresh token lifetime: **90 days** (7,776,000 s). Refresh tokens **rotate** — each refresh issues a new refresh token; the old one is invalidated.

### Constants

```
LOGIN_BASE      = https://login.watts.io
TENANT          = wattsb2cap02.onmicrosoft.com
POLICY          = B2C_1A_Residential_UnifiedSignUpOrSignIn
CLIENT_ID       = c832c38c-ce70-4ebc-83b6-b4548083ac90
REDIRECT_URI    = msalc832c38c-ce70-4ebc-83b6-b4548083ac90://auth
SCOPE           = https://wattsb2cap02.onmicrosoft.com/wattsapiresi/manage offline_access openid profile
TOKEN_URL       = https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/token
```

### Initial login (complex — do this outside Hubitat once)

```
Step 1 — GET login page:
  GET {LOGIN_BASE}/tfp/{TENANT}/{POLICY}/oauth2/v2.0/authorize
    ?scope={SCOPE}&response_type=code&client_id={CLIENT_ID}
    &redirect_uri={REDIRECT_URI}&code_challenge={BASE64URL(SHA256(verifier))}
    &code_challenge_method=S256&prompt=login&state={random}
  → 200 HTML page containing embedded JS: "csrf":"<token>", "transId":"<id>"
  → Set-Cookie headers (session cookies required for next step)

Step 2 — POST credentials:
  POST {LOGIN_BASE}/{TENANT}/{POLICY}/SelfAsserted?tx={transId}&p={POLICY}
    Headers: Content-Type: application/x-www-form-urlencoded
             x-csrf-token: <csrf from step 1>
             Cookie: <cookies from step 1>
    Body: request_type=RESPONSE&signInName={email}&password={password}
  → 200 JSON { "status": "200" } on success

Step 3 — GET auth code redirect:
  GET {LOGIN_BASE}/tfp/{TENANT}/{POLICY}/api/CombinedSigninAndSignup/confirmed
    ?rememberMe=false&csrf_token={csrf}&tx={transId}&p={POLICY}
    Headers: Cookie: <updated cookies>
  → 302 redirect to {REDIRECT_URI}?code={AUTH_CODE}&...

Step 4 — Exchange code for tokens:
  POST {TOKEN_URL}
    Content-Type: application/x-www-form-urlencoded
    Body: client_id={CLIENT_ID}&scope={SCOPE}&grant_type=authorization_code
          &code={AUTH_CODE}&redirect_uri={REDIRECT_URI}
          &code_verifier={verifier}&client_info=1
  → 200 JSON (see token response below)
```

### Token response (Steps 4 and refresh)

```json
{
  "access_token":  "eyJhbGci...",
  "id_token":      "eyJhbGci...",
  "token_type":    "Bearer",
  "expires_in":    900,
  "expires_on":    1768718583,
  "refresh_token": "eyJraWQ...",
  "refresh_token_expires_in": 7776000,
  "not_before":    1768717683,
  "resource":      "978b217d-e864-4f8e-a1d5-587ed65fa544",
  "scope":         "https://wattsb2cap02.onmicrosoft.com/wattsapiresi/manage",
  "client_info":   "eyJ1aWQi..."
}
```

### Token refresh (simple — implement this in Hubitat)

```
POST {TOKEN_URL}
Content-Type: application/x-www-form-urlencoded

client_id=c832c38c-ce70-4ebc-83b6-b4548083ac90
&scope=https://wattsb2cap02.onmicrosoft.com/wattsapiresi/manage%20offline_access%20openid%20profile
&grant_type=refresh_token
&refresh_token={STORED_REFRESH_TOKEN}
&client_info=1
```

Optional extra headers seen in captured iOS app traffic (include for safety):
```
x-client-sku: MSAL.Xamarin.iOS
x-client-ver: 4.66.1.0
```

Returns the same token response shape. **Store the new `refresh_token` — the old one is now invalid.**

---

## Core API

### Base URL and required headers (ALL requests)

```
Base URL: https://home.watts.com/api
Headers:
  Authorization:   Bearer {access_token}
  Api-Version:     2.0
  Content-Type:    application/json
```

### GET /api/User

Returns current user info. Use `measurementScale` (`"I"` = Imperial °F, `"M"` = Metric °C) to know the temperature unit returned by device endpoints.

**Response body:**
```json
{
  "userId": "db933c88-8744-469f-912c-272f36f29302",
  "emailAddress": "user@example.com",
  "defaultLocationId": "0c1c1706-43e5-4e54-a66d-15fe9f7a65ad",
  "measurementScale": "I",
  "firstName": "John",
  "lastName": "Doe"
}
```

---

### GET /api/Location

Returns list of locations (homes/sites). Each location has a `locationId` needed for device discovery.

**Response body (array):**
```json
[{
  "locationId": "bb643b20-6a65-48ed-9153-43e7582bd837",
  "name": "Home",
  "awayState": 0,
  "devicesCount": 5,
  "supportsAway": true
}]
```

---

### GET /api/Location/{locationId}/Devices

Returns a summary list of devices at the location. Each entry has a `deviceId` for per-device calls.

**Response body (array):**
```json
[{
  "deviceId": "baee7842-ec00-5e95-af3a-63bc70d9a97d",
  "name": "Master Bath Floor",
  "modelId": 12,
  "modelNumber": "SunStat Connect Plus",
  "deviceType": "Thermostat",
  "deviceTypeId": 2,
  "isConnected": true
}]
```

> `modelId` and `modelNumber` for SunStat Connect Plus are **unconfirmed** — the values shown above are placeholders. Switch must confirm against a real device.

---

### GET /api/Device/{deviceId}

**The primary polling endpoint.** Returns complete state of one thermostat.

**Response body:**
```json
{
  "deviceId": "baee7842-ec00-5e95-af3a-63bc70d9a97d",
  "name": "3rd Floor",
  "modelNumber": "562",
  "deviceType": "Thermostat",
  "isConnected": true,
  "data": {
    "Sensors": {
      "Room":    {"Val": 74, "Status": "Okay"},
      "Floor":   {"Val": 73, "Status": "Okay"},
      "Outdoor": {"Val": 53, "Status": "Okay"}
    },
    "State": {
      "Op":  "Off",
      "Sub": "None"
    },
    "Mode": {
      "Active": 1,
      "Val":    "Heat",
      "Enum":   ["Off", "Heat", "Cool", "Auto"]
    },
    "Target": {
      "Active": 1,
      "Sensor": "Room",
      "Hold":   0,
      "Heat":   70,
      "Cool":   100,
      "Min":    40,
      "Max":    100,
      "Steps":  1
    },
    "TempInterlock": 2.0,
    "Fan": {
      "Active": 1,
      "Val":    "Auto",
      "Enum":   ["Auto", "On"],
      "Relay":  0
    },
    "TempUnits": {
      "Active": 1,
      "Val":    "F",
      "Enum":   ["F", "C"]
    },
    "Units": "Imperial",
    "SchedEnable": {
      "Active": 1,
      "Val":    "Off",
      "Enum":   ["Off", "On"]
    },
    "Schedule": {
      "SchedActive": 0,
      "FloorActive": 1,
      "Floor": {"W": 71, "A": 0},
      "FloorMin": 40,
      "FloorMax": 85
    },
    "Energy": {
      "Heat": {"Daily": [1.5, 0.0, ...], "Monthly": [23.6, ...]},
      "Cool": {"Daily": [0.9, 0.0, ...], "Monthly": [...]}
    },
    "DateTime": "2026-01-18T06:28:19Z",
    "TZOffset": -28800
  }
}
```

> For SunStat Connect Plus (heat-only), `Mode.Enum` will likely be `["Off", "Heat"]` rather than `["Off", "Heat", "Cool", "Auto"]`. **Unconfirmed — needs Switch to check real device.**

---

### PATCH /api/Device/{deviceId}

**The single control endpoint.** All commands use this. Returns the same shape as GET.

#### Set mode
```json
{"Settings": {"Mode": "Heat"}}
{"Settings": {"Mode": "Off"}}
```

#### Set heating setpoint
```json
{"Settings": {"Heat": 72.0}}
```

#### Set cooling setpoint (probably N/A for SunStat)
```json
{"Settings": {"Cool": 75.0}}
```

#### Set floor minimum temperature
```json
{"Settings": {"Schedule": {"Floor": {"W": 68.0, "A": 0}}}}
```
> `W` = floor target/minimum warmth temp. `A` = away temperature (0 = disabled). Must send both `W` and `A` together (read-modify-write pattern).

#### Enable/disable schedule
```json
{"Settings": {"SchedEnable": "On"}}
{"Settings": {"SchedEnable": "Off"}}
```

---

### PATCH /api/Location/{locationId}/State

Set away mode for entire location.

```json
{"awayState": 1}
```
(`0` = home, `1` = away)

---

### All response envelopes

```json
{
  "errorNumber": 0,
  "errorMessage": null,
  "body": { /* actual payload */ }
}
```

`errorNumber != 0` means failure. Check `errorMessage`. `401` HTTP status means token expired — refresh and retry.

---

## State model

| Concept | API field | Values | Notes |
|---|---|---|---|
| HVAC mode | `data.Mode.Val` | `"Off"`, `"Heat"` (SunStat) | Tekmar also has `"Cool"`, `"Auto"` |
| Operating state | `data.State.Op` | `"Off"`, `"Heating"`, `"Cooling"` | SunStat: never `"Cooling"` |
| Room temp | `data.Sensors.Room.Val` | float, °F or °C | Unit from `data.TempUnits.Val` |
| Floor temp | `data.Sensors.Floor.Val` | float, °F or °C | Optional; some units may report 100°C if probe not connected |
| Heat setpoint | `data.Target.Heat` | float | Range: `data.Target.Min` to `data.Target.Max` (40–100°F typical) |
| Cool setpoint | `data.Target.Cool` | float | Likely unused for SunStat |
| Floor target | `data.Schedule.Floor.W` | float | Floor minimum warmth temp (40–85°F) |
| Schedule | `data.SchedEnable.Val` | `"Off"`, `"On"` | |
| Device online | `isConnected` | boolean | |
| Temp units | `data.TempUnits.Val` | `"F"`, `"C"` | Per-device setting |
| Hold override | `data.Target.Hold` | int | Non-zero = hold active |

**Temperature units:** The API returns temperatures in whatever unit the device is configured for (`data.TempUnits.Val`). US devices will almost always report `"F"`. Hubitat natively handles °F.

**Polling cadence:** No push/webhook available. Poll `GET /Device/{id}`. The homebridge plugin uses 120 s; 30–60 s is safe. Access token must be refreshed every 15 minutes regardless of polling cadence.

---

## Hubitat capability mapping

| Hubitat capability | Hubitat command/attribute | Watts Home API call | Notes |
|---|---|---|---|
| `ThermostatMode` | `setThermostatMode("heat")` | `PATCH /Device/{id}` `{"Settings":{"Mode":"Heat"}}` | |
| `ThermostatMode` | `setThermostatMode("off")` | `PATCH /Device/{id}` `{"Settings":{"Mode":"Off"}}` | |
| `ThermostatMode` | `setThermostatMode("cool")` | No-op or error — heat only | Log warning, ignore |
| `ThermostatMode` | `setThermostatMode("auto")` | No-op or error — heat only | Log warning, ignore |
| `ThermostatMode` attr `thermostatMode` | read | `data.Mode.Val`: `"Off"` → `"off"`, `"Heat"` → `"heat"` | |
| `ThermostatHeatingSetpoint` | `setHeatingSetpoint(temp)` | `PATCH /Device/{id}` `{"Settings":{"Heat":temp}}` | Clamp to `data.Target.Min`–`data.Target.Max` |
| `ThermostatHeatingSetpoint` attr `heatingSetpoint` | read | `data.Target.Heat` | |
| `ThermostatCoolingSetpoint` | `setCoolingSetpoint(temp)` | No-op | Heat only |
| `ThermostatOperatingState` attr `thermostatOperatingState` | read | `data.State.Op`: `"Heating"` → `"heating"`, `"Off"` + mode=Heat → `"idle"`, `"Off"` + mode=Off → `"idle"` | |
| `TemperatureMeasurement` attr `temperature` | read | `data.Sensors.Room.Val` | Primary room/air sensor |
| `Refresh` | `refresh()` | `GET /Device/{id}` | |
| Custom attr `floorTemperature` | read | `data.Sensors.Floor.Val` | Floor probe; may read 100 if disconnected |
| Custom cmd `setFloorMinTemp(temp)` | write | `PATCH /Device/{id}` `{"Settings":{"Schedule":{"Floor":{"W":temp,"A":currentAway}}}}` | Read-modify-write required |
| Custom attr `scheduleEnabled` | read/write | `data.SchedEnable.Val` / `{"Settings":{"SchedEnable":"On"}}` | |
| Custom attr `deviceOnline` | read | `isConnected` | |

**Modes to expose as Hubitat thermostatMode**: `off`, `heat` only. Omit `cool`, `emergency heat`, `auto` from the supported modes list.

---

## Quirks / blockers / unknowns

### Critical — confirm with Switch against real SunStat device

1. **Mode enum for SunStat**: The reference device (Tekmar 562) exposes `["Off", "Heat", "Cool", "Auto"]`. SunStat Connect Plus is heat-only — the enum is likely `["Off", "Heat"]`. **Driver must read `data.Mode.Enum` from the first poll and adapt.** Do not hard-code modes before confirming.

2. **Floor sensor reliability**: The Watts Vision EU equivalent notes floor temperature always returns 100°C when the probe is not physically connected. The Watts Home API may behave similarly: `data.Sensors.Floor.Val` may return 212°F (100°C converted) or similar sentinel value. Driver should guard against it (e.g., if > 110°F, treat as "disconnected" and skip the attribute update).

3. **modelId / modelNumber**: Unknown for SunStat Connect Plus. Needs Switch to confirm from live device response.

4. **Schedule structure**: Full schedule CRUD is undocumented. `SchedEnable.On/Off` is the only confirmed schedule interaction. Live schedule editing is out of scope for v1.

### Auth blockers

5. **Initial token acquisition is complex**: Azure AD B2C PKCE requires multi-step HTML scraping, PKCE code generation, and cookie handling. **This cannot be done from inside Hubitat.** Recommended approach: user runs the homebridge-tekmar-wifi CLI (`node dist/cli/index.js login`) once to obtain `access_token` + `refresh_token` + `expires_at`, then pastes them into driver preferences. The driver handles all subsequent refresh via simple `asynchttpPost` (feasible in Hubitat).

6. **ROPC policy existence unknown**: Azure AD B2C sometimes offers a Resource Owner Password Credentials policy (`B2C_1A_ROPC_Auth` or similar) that allows a direct username/password → token POST without HTML scraping. If the Watts B2C tenant has one, it would eliminate the external bootstrapping step. **Switch should probe**: `POST https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_ResourceOwnerPasswordCredentials/oauth2/v2.0/token` with `grant_type=password&username=...&password=...&client_id=...`. If this returns tokens, initial auth is trivial.

7. **Certificate pinning**: Unknown. HTTPS is standard TLS. The homebridge plugin doesn't mention cert pinning issues, so likely none.

8. **Rate limiting**: Undocumented. Home Assistant Watts Vision equivalent polls every 15 s. homebridge-tekmar-wifi recommends 30–120 s. Use 60 s for the Hubitat driver; back off if `errorNumber != 0` or 429 received.

9. **Refresh token rotation**: Each token refresh issues a new `refresh_token`. The old one is immediately invalidated. Hubitat driver **must** persist the new refresh_token after every refresh (write to `state.refreshToken`). If the Hubitat hub loses state (e.g., reboot without state persistence), the user will need to re-paste their tokens.

---

## Recommended next steps for Tank

### Smallest viable driver (v0.1)

Wire these five things in this order:

1. **Driver preferences**: `accessToken` (string, encrypted), `refreshToken` (string, encrypted), `tokenExpiresAt` (number, epoch seconds). Plus `deviceId` (string) and `locationId` (string — or auto-discover from `/Location/{id}/Devices` if user provides location name).

2. **`refresh()` command**: Calls `GET /api/Device/{deviceId}`. On success, maps:
   - `data.Sensors.Room.Val` → `temperature` attribute
   - `data.Sensors.Floor.Val` → `floorTemperature` attribute (guard: skip if > 110°F)
   - `data.Mode.Val` → `thermostatMode` (lowercase)
   - `data.State.Op` → `thermostatOperatingState` (`"Heating"` → `"heating"`, else `"idle"`)
   - `data.Target.Heat` → `heatingSetpoint`
   - `isConnected` → `deviceOnline`

3. **`setHeatingSetpoint(temp)`**: `PATCH /Device/{deviceId}` `{"Settings":{"Heat":temp}}`. Apply optimistic state update before API call.

4. **`setThermostatMode(mode)`**: Maps `"heat"` → `"Heat"`, `"off"` → `"Off"`. Ignore `"cool"`, `"auto"`, `"emergency heat"`. Calls PATCH.

5. **Token refresh middleware**: Before every API call, check `state.tokenExpiresAt`. If `now()/1000 > tokenExpiresAt - 300`, call `refreshTokens()`:
   ```
   POST https://login.watts.io/tfp/wattsb2cap02.onmicrosoft.com/B2C_1A_Residential_UnifiedSignUpOrSignIn/oauth2/v2.0/token
   Content-Type: application/x-www-form-urlencoded
   client_id=c832c38c-ce70-4ebc-83b6-b4548083ac90
   &grant_type=refresh_token&refresh_token={state.refreshToken}&client_info=1
   &scope=https://wattsb2cap02.onmicrosoft.com/wattsapiresi/manage%20offline_access%20openid%20profile
   ```
   On success, persist new `access_token`, `refresh_token`, `expires_on` to `state`.

6. **Polling**: `runEvery1Minute` or `schedule("0 * * * * ?", "refresh")` for 60 s poll interval.

### Hubitat sandbox notes
- Token refresh endpoint needs form-encoded body. Use `requestContentType: "application/x-www-form-urlencoded"` in Hubitat httpPost params. No content-type quirks expected (unlike AWS).
- Main API (home.watts.com) uses JSON body + `Content-Type: application/json`. Standard Hubitat `asynchttpPost` / `asynchttpGet` works.
- Add `"Api-Version": "2.0"` header to all calls to `home.watts.com/api`.
- `@Field static final` constants: do NOT reference other `@Field` constants in initializer — inline literals only.

---

## Sources cited

1. `seanami/homebridge-tekmar-wifi` — https://github.com/seanami/homebridge-tekmar-wifi (primary source)
   - `src/lib/api/auth.ts` — OAuth 2.0 Azure AD B2C PKCE login implementation
   - `src/lib/api/client.ts` — API client (all endpoints)
   - `src/types/api.ts` — Full TypeScript type definitions
   - `docs/API_ENDPOINTS.md` — Documented and tested endpoint reference
   - `docs/AUTHENTICATION.md` — Auth flow, token details, capture notes
2. iTunes API (App Store search) — confirmed "Watts® Home" app (id 1500497974) by Watts Water Technologies
3. `pwesters/watts_vision` — https://github.com/pwesters/watts_vision — EU Watts Vision API (not SunStat)
4. `roberveral/hass_watts_vision` — https://github.com/roberveral/hass_watts_vision — EU Watts Vision API, more complete
5. SunTouch product page — 403 (blocked), confirmed product name only via URL


---

## Trinity: Driver Architecture Proposal

### 2026-05-16T20:01:41-07:00: SunStat Connect Plus driver — architecture proposal
**By:** Trinity
**Status:** Proposed

---

## Driver identity

| Field | Value |
|---|---|
| Namespace | `mads` |
| Parent driver name | `SunStat Connect Plus` |
| Child driver name | `SunStat Connect Plus Thermostat` |
| Version (scaffold) | `0.1.0` |
| Parent file path | `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy` |
| Child file path | `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy` |
| `importUrl` | `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy` (parent) |

---

## Capability profile

**Decision: declare `Thermostat` (combo) + `TemperatureMeasurement` + `Refresh` on the child.**

The `Thermostat` combo is the right call here despite bundling cooling/fan capabilities that don't apply, because:
1. Hubitat's dashboard uses the presence of `Thermostat` to render the proper thermostat tile with setpoint +/− controls.
2. Google Home and Alexa integrations discover thermostat devices by the `Thermostat` capability; individual capabilities yield a generic switch tile.
3. Rule Machine's thermostat triggers (e.g., "when thermostatOperatingState changes to heating") only activate when `Thermostat` is declared.

The cooling/fan surface is neutralized by setting `supportedThermostatModes` and `supportedThermostatFanModes` attributes — Hubitat's dashboard and voice integrations respect those lists and hide unavailable modes.

| Capability | Declare? | Rationale |
|---|---|---|
| `Actuator` | ✅ Yes | Required base for any command-issuing driver |
| `Sensor` | ✅ Yes | Required base for attribute-reporting drivers |
| `Thermostat` | ✅ Yes | Dashboard tile, RM thermostat triggers, voice assistant discovery |
| `ThermostatHeatingSetpoint` | ❌ No | Already included in `Thermostat` — redundant declaration |
| `ThermostatMode` | ❌ No | Already included in `Thermostat` — redundant declaration |
| `ThermostatOperatingState` | ❌ No | Already included in `Thermostat` — redundant declaration |
| `ThermostatCoolingSetpoint` | ❌ No | Heat-only device — skip entirely |
| `ThermostatFanMode` | ❌ No | No fan — skip entirely |
| `ThermostatSchedule` | ❌ No | Hubitat's ThermostatSchedule shape is undefined by the platform; schedule control is better left to Rule Machine or the Watts app. Add only if Cypher confirms the API exposes a schedule structure we can surface meaningfully. |
| `TemperatureMeasurement` | ✅ Yes | Ambient sensor read; also gives RM the `temperature` attribute for rules |
| `Refresh` | ✅ Yes | Manual state poll |
| `Initialize` | ✅ Yes | Re-register polling schedule on hub restart |

**Supported thermostat modes** (set as `supportedThermostatModes` attribute at install time):
```
["heat", "off"]
```
- `heat` → normal operation to setpoint
- `off` → thermostat disabled
- `auto` (schedule-following): add only if Cypher confirms the Watts API exposes a schedule-active mode as a discrete API state. Do not invent modes that have no API backing.
- `emergency heat`: semantically correct for electric floor boost, but Hubitat voice integrations treat `emergency heat` as a distinct HVAC mode. Use a custom `setBoost(minutes)` command instead — cleaner and less confusing to end users.

**Supported fan modes** (set as `supportedThermostatFanModes` attribute at install time):
```
["auto"]
```
Electric floor heating has no fan. Set a single `"auto"` placeholder so the combo capability doesn't leave `supportedThermostatFanModes` null. The fan tile will be present but grayed out in the dashboard.

**ThermostatOperatingState values:**
- `heating` — element is actively drawing power
- `idle` — setpoint reached, element off

---

## Custom attributes / commands

### Child driver custom attributes

| Attribute | Type | Rationale |
|---|---|---|
| `floorTemperature` | `number` | SunStat has dual sensors (ambient + floor probe). `temperature` (from `TemperatureMeasurement`) holds ambient; `floorTemperature` holds the floor probe reading. Both are useful for rules ("turn on radiant if floor temp < 18°C"). |
| `boostActive` | `enum` (`true`/`false`) | Reflects whether a timed boost override is currently running. |
| `boostUntil` | `string` | ISO-8601 datetime string for when the active boost expires. Empty string when no boost is active. String type used because Hubitat has no native datetime attribute type. |
| `signalStrength` | `number` | Wi-Fi RSSI dBm value if the Watts API exposes it. Useful for troubleshooting. Mark as optional pending Cypher's API spec. |

### Child driver custom commands

| Command | Signature | Rationale |
|---|---|---|
| `setBoost` | `setBoost(minutes)` — Integer, 1–120 | Activates timed boost/override mode. Maps more cleanly than hijacking `emergency heat` mode. `minutes` param gives users control. |
| `cancelBoost` | `cancelBoost()` | Cancels active boost, returns to normal operation. |

### Parent driver custom commands

| Command | Signature | Rationale |
|---|---|---|
| `discoverDevices` | `discoverDevices()` | Re-runs device discovery against the Watts API and creates/updates child devices. Useful after adding a new thermostat to the account. |

---

## Folder layout

```
drivers/sunstat-thermostat/
├── sunstat-thermostat-parent.groovy   ← cloud auth + device discovery; creates child devices
├── sunstat-thermostat-child.groovy    ← per-thermostat control; all thermostat capabilities here
├── README.md                          ← user docs (Link writes)
├── TESTING.md                         ← manual test plan (Switch writes)
├── packageManifest.json               ← HPM manifest listing both drivers
└── CHANGELOG.md                       ← version log
```

---

## Parent/child or single-device

**Recommendation: parent/child from day one.**

**Rationale:**

Electric floor heating is installed room-by-room. A home with SunStat thermostats typically has 2–5 units (bathroom, kitchen, entryway, basement, mudroom). Each is independently controlled but all share the same Watts cloud account and authenticate with the same credentials.

The parent/child pattern is the right architecture because:

1. **Single auth surface.** Cloud credentials live only in the parent. Children never hold tokens or credentials — they call parent helper methods to send commands. Credential rotation (password change, token expiry) is handled in one place.

2. **Discovery is automatic.** The parent polls the Watts API account, discovers all thermostats, and creates child devices automatically. Users add one device to Hubitat, not five.

3. **Gemstone precedent confirms the pattern.** Gemstone deferred parent/child because zone addressability was unknown at architecture time. SunStat has no such ambiguity — the Watts app clearly manages multiple independent thermostats per account. The architectural reason to defer (unknown protocol) does not apply here.

4. **Hubitat's `addChildDevice` / `getChildDevices` pattern is well-established.** No exotic platform features needed.

**Trade-off acknowledged:** Parent/child adds complexity (child-to-parent call routing, child device creation/deletion lifecycle). That cost is justified here because multi-thermostat is the expected common case, not an edge case.

**If the user has only one thermostat:** parent/child still works correctly — the parent creates one child. The UX is slightly more complex (two devices instead of one), but the consistency benefit outweighs this.

---

## Lifecycle + logging

### Lifecycle methods

**Parent driver:**

| Method | Responsibility |
|---|---|
| `installed()` | Set default preferences; log `SunStat Connect Plus v0.1.0 installed`; call `initialize()` |
| `updated()` | `unschedule()`; clear auth tokens from state; re-run `initialize()` |
| `initialize()` | If credentials not configured: log and return. Otherwise: schedule polling (`runEvery5Minutes("poll")`); schedule proactive token refresh; call `refresh()` with `runIn(2, "refresh")` |
| `uninstalled()` | `unschedule()`; delete all child devices via `getChildDevices()` loop |
| `poll()` | Fetch all device states from Watts API; route updates to each child via `child.parseDeviceState(map)` |

**Child driver:**

| Method | Responsibility |
|---|---|
| `installed()` | Set attribute defaults: `thermostatMode = "off"`, `thermostatOperatingState = "idle"`, `supportedThermostatModes = ["heat","off"]`, `supportedThermostatFanModes = ["auto"]`; log install |
| `updated()` | Re-apply defaults; re-enable debug-log timer if `logEnable` |
| `initialize()` | No-op in child; parent owns scheduling |
| `uninstalled()` | Log removal; no scheduling to clean up |

### Debug logging convention

Mirror Gemstone's pattern exactly:
```groovy
input name: "logEnable", type: "bool", title: "Enable debug logging (auto-off after 30 minutes)", defaultValue: false
input name: "txtEnable", type: "bool", title: "Enable descriptionText (info) logging",             defaultValue: true
```
- `logEnable` triggers `runIn(1800, "logsOff")` on `updated()`.
- `logsOff()` sets `logEnable = false` and logs `"SunStat Connect Plus: debug logging auto-disabled"`.
- Child driver carries the same two preferences independently (each child can have debug on/off separately).

### Event emission convention

All `sendEvent` calls on the child must include a `descriptionText`:
```groovy
sendEvent(name: "thermostatOperatingState", value: "heating",
          descriptionText: "${device.displayName} operating state is heating")
```
Gated on `txtEnable` for info logging:
```groovy
if (settings.txtEnable) log.info descriptionText
```

### state.* vs atomicState.*

Follow the established Gemstone rule:
- **`state.*`** for all non-concurrent reads/writes: auth tokens, device ID map, last-known thermostat state. Reads and writes happen only in async HTTP callbacks (sequential per Hubitat's threading model).
- **`atomicState.*`** only if a value is written in a scheduled callback AND read in a command handler where a race is possible. In practice, use `state.*` everywhere unless Tank encounters a concrete race condition during implementation.

### Token refresh handling

Borrow the Gemstone/Cognito pattern wholesale — stored in `.squad/skills/hubitat-cognito-token-refresh/SKILL.md`. Adapt as follows:
- `state.accessToken`, `state.refreshToken`, `state.tokenExpiresAt` live on the **parent** device.
- Child commands delegate through the parent: `parent.sendThermostatCommand(deviceId, payload)`.
- On 401, the parent re-authenticates and replays once (same retry-once pattern as Gemstone).
- Token refresh scheduled on the parent with `runIn` ~300 seconds before expiry.

---

## Sandbox-safety reminders for Tank

These apply in addition to the general rules in `.squad/skills/hubitat-sandbox-pitfalls/SKILL.md`:

1. **No cross-@Field references.** Each `@Field static final` initializer must use a literal. Example:
   ```groovy
   @Field static final String DRIVER_VERSION = "0.1.0"
   @Field static final String USER_AGENT     = "Hubitat SunStat Connect Plus/0.1.0"  // literal, not DRIVER_VERSION
   ```

2. **No `System.*` at runtime.** Use `now()` for timestamps, `pauseExecution(ms)` for delays.

3. **`addChildDevice` signature.** Correct call:
   ```groovy
   addChildDevice("mads", "SunStat Connect Plus Thermostat", deviceNetworkId, [name: displayName, isComponent: false])
   ```
   `isComponent: false` lets users rename child devices. `isComponent: true` would lock the name.

4. **Child-to-parent calls.** Children call `parent.someMethod()` — Hubitat supports this natively. No external IPC needed.

5. **Temperature units.** Hubitat hubs report `location.temperatureScale` as `"F"` or `"C"`. Emit temperature events with the correct unit:
   ```groovy
   sendEvent(name: "temperature", value: temp, unit: location.temperatureScale)
   ```
   Convert Celsius API values to Fahrenheit if `location.temperatureScale == "F"` before emitting.

6. **`URLEncoder.encode`** is believed safe in Hubitat (used in Gemstone v0.2.3 without sandbox failure) but re-test if any URL-encoding is needed.

---

## Dependencies on Cypher's research

The following architectural decisions are **fixed regardless of API shape:**

- Parent/child split ✅
- Capability profile (`Thermostat` combo + `TemperatureMeasurement` + `Refresh`) ✅
- Custom attributes (`floorTemperature`, `boostActive`, `boostUntil`) ✅
- `setBoost(minutes)` / `cancelBoost()` commands ✅ (stubs until API confirmed)
- Logging conventions, lifecycle methods, sandbox rules ✅
- Folder layout and file names ✅
- Namespace `mads`, version `0.1.0` scaffold ✅

The following are **pending Cypher's Watts API spec:**

| Decision | Blocked on |
|---|---|
| Auth mechanism (OAuth2, API key, Cognito, custom) | Watts API authentication scheme |
| Device discovery endpoint | Watts API: does `/devices` or `/thermostats` return the list? |
| Polling endpoint and response schema | Watts API: thermostat state shape |
| `setBoost` API payload | Watts API: does boost exist as a first-class API call or is it a setpoint override? |
| `signalStrength` attribute | Watts API: is RSSI in the device state response? |
| `auto` mode support | Watts API: is schedule-following exposed as a distinct device mode? |
| Temperature units from API | Watts API: Celsius, Fahrenheit, or configurable? |
| setpoint limits | Watts API: min/max heating setpoint (for preference validation in Tank's scaffold) |

---

## Initial version

**v0.1.0 scaffold scope (what Tank ships first):**

- Both `.groovy` files exist with correct `metadata {}` blocks, `@Field` constants, all lifecycle stubs
- All capabilities declared; all attributes initialized to safe defaults on `installed()`
- All command stubs present (`setHeatingSetpoint`, `setThermostatMode`, `off`, `setBoost`, `cancelBoost`, `refresh`)
- Scaffold transparency warn banner in any command that attempts an API call (mirrors Gemstone v0.1.1 pattern): `log.warn "SunStat Connect Plus: API endpoint stubbed — command not sent"`
- `logEnable` / `txtEnable` preferences wired up with 30-min auto-off
- `packageManifest.json` with UUID v4 for each driver, version `0.1.0`
- `CHANGELOG.md` with `0.1.0` entry

**v0.2.0 scope (working driver — blocked on Cypher's API spec):**

- Auth flow implemented against real Watts API
- Device discovery: parent creates child devices from account device list
- `refresh()` fetches live state for all child thermostats
- `setHeatingSetpoint`, `setThermostatMode`, `off` wired to real API calls
- `setBoost(minutes)` wired if API supports it
- `floorTemperature`, `boostActive`, `boostUntil` populated from live API responses
- Polling schedule active
- Token refresh active (if bearer-token auth)


---

## Switch: Manual Test Plan

# Manual Test Plan — SunStat Connect Plus Driver

**Driver:** `sunstat-thermostat.groovy` (TBD)  
**Test Target:** SunStat Connect Plus electric floor heating thermostat  
**Cloud Control:** Watts iOS app  
**Platform:** Hubitat C-7 / C-8  
**Created:** 2026-05-16T20:01:41-07:00  
**Status:** DRAFT — awaiting Cypher's API spec and Trinity's capability profile  

---

## Prerequisites

Before running any test:

- **Hubitat hub** has internet connectivity (cloud driver requires outbound HTTPS)
- **Watts app** on iOS is already working with the same SunStat account you plan to configure in Hubitat
- **Driver code** `sunstat-thermostat.groovy` is saved in the Hubitat hub web UI → Drivers Code
- **Virtual device** (or Hubitat-managed child device) exists and uses type **SunStat Thermostat**
- **Logs** page is open in a second browser tab for all tests
- **Device page** is open in the main browser tab
- **SunStat thermostat** is powered on and physically connected to WiFi and the heating system

**Important cloud behavior:** Cloud response latency is typical 2–10 seconds. The driver may use optimistic Hubitat events for some commands (e.g., setpoint change), then poll to reconcile cloud state. Verify both the Hubitat tile and the Watts app reflect the change.

---

## Test Area: Lifecycle & Authentication

### Test 1: Install and Initial Configuration

**What:** Verify driver installation, device creation, and credentials setup.

**Steps:**

1. Create a new virtual device in Hubitat and select type **SunStat Thermostat** [needs Trinity profile].
2. Open the device page and scroll to **Preferences**.
3. Verify these preferences exist [needs Trinity profile]:
   - **SunStat account email** (or username)
   - **SunStat account password**
   - **Device selection** (if the account has multiple thermostats)
   - **Polling interval** (suggested: 5 minutes)
   - **HTTPS request timeout** (suggested: 30 seconds)
   - **Enable debug logging** (checkbox)
4. Enter the same email and password that work in the official Watts app.
5. If the account has multiple SunStat devices, select the correct thermostat by name or serial number.
6. Leave other settings at defaults.
7. Click **Save Preferences**.
8. Watch both the device page and the logs for 15–30 seconds.

**Expected:**

- `authStatus` attribute changes from **Authenticating** to **Authenticated: <thermostat name>** (or equivalent success indicator) [needs Trinity profile]
- No stack traces, `MissingMethodException`, `NullPointerException`, or `ClassCastException` in logs
- The driver logs do not expose the email, password, or any authentication tokens
- Clicking **Refresh** after auth succeeds populates `thermostatMode`, `heatingSetpoint`, `currentTemperature`, `thermostatOperatingState`, and other core attributes [needs Trinity profile]
- `currentTemperature` shows a numeric room temperature (in °F or °C depending on configuration) [needs Cypher spec for units]
- `floorTemperature` (if exposed) shows a different numeric value (sensor under the tile) [needs Trinity profile]

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 2: Authentication Failure Handling

**What:** Verify bad credentials fail cleanly and show a helpful error.

**Steps:**

1. In Preferences, replace the password with an incorrect one.
2. Click **Save Preferences**.
3. Watch `authStatus` and the logs for 10 seconds.
4. Restore the correct password and click **Save Preferences** again.
5. Verify the device recovers.

**Expected:**

- `authStatus` changes to a clear error message (e.g., **Auth failed — check email/password**) [needs Trinity profile]
- A helpful log entry appears with the HTTP status code and error reason (without exposing credentials)
- The device does not crash or hang; it remains in a degraded but stable state
- After restoring the correct password, `authStatus` returns to **Authenticated: <thermostat name>**

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 3: Updated / Preferences Change

**What:** Verify the driver reinitializes cleanly when preferences are modified.

**Steps:**

1. Ensure the device is authenticated and working.
2. Change the **polling interval** (e.g., from 5 minutes to 1 minute).
3. Click **Save Preferences**.
4. Watch the logs for re-initialization messages.
5. Verify the device continues to poll at the new interval.

**Expected:**

- The logs show a clear message indicating preferences were saved and polling was re-registered (or equivalent lifecycle event) [needs Trinity profile]
- No stack traces or orphaned schedules
- `refresh()` or polling still works at the new interval
- Core attributes remain populated

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 4: Device Uninstall / Cleanup

**What:** Verify the driver deregisters all schedules and cleans up on removal.

**Steps:**

1. Ensure the device is working.
2. From the Hubitat device page, click the **Delete** button at the bottom.
3. Confirm the deletion.
4. Watch the logs and verify the device no longer appears in the device list.
5. Optional: Restart the Hubitat hub and confirm the device does not reappear.

**Expected:**

- The logs show a clean `uninstalled()` message (or equivalent cleanup log) [needs Trinity profile]
- No orphaned schedules or background tasks remain (verified by hub stability and no phantom CPU usage)
- No error entries related to the deleted device in subsequent polls or commands

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Read State / Refresh

### Test 5: Refresh Current State

**What:** Verify `refresh()` pulls current setpoint, temperatures, and mode from the cloud.

**Steps:**

1. Ensure the device is authenticated.
2. On the device page, click **Refresh**.
3. Watch the Hubitat attributes and logs.
4. Compare the displayed values to the Watts app on your phone.

**Expected:**

- `thermostatMode` displays the current mode (e.g., **Off**, **Heat**, **Auto**) [needs Cypher spec for exact mode names]
- `heatingSetpoint` shows the target temperature (e.g., **72°F**) [needs Trinity profile]
- `currentTemperature` shows room air temperature (e.g., **68°F**)
- `floorTemperature` (if exposed) shows the under-tile floor temperature (e.g., **70°F**) [needs Trinity profile]
- `thermostatOperatingState` reflects whether the system is actively heating (**Heating**) or idle (**Idle**) [needs Cypher spec]
- All values match the Watts app display (within typical cloud lag of 2–10 seconds)
- No HTTP errors, parse errors, or stack traces appear in logs

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 6: Polling Cycle

**What:** Verify polling automatically keeps state in sync.

**Steps:**

1. Set the polling interval to **1 minute** via Preferences.
2. Note the current `thermostatMode` and `heatingSetpoint` in Hubitat.
3. Open the Watts app and change the setpoint (e.g., from 72°F to 75°F) and/or the mode.
4. Return to the Hubitat device page and wait for the next poll cycle (up to 1 minute).
5. Verify Hubitat updates without manual **Refresh**.

**Expected:**

- After 1 minute, `heatingSetpoint` and `thermostatMode` automatically update to reflect the Watts app change
- The device logs show poll/refresh activity at regular intervals (without spam)
- No user action required to see the change in Hubitat

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Setpoint Control

### Test 7: Set Heating Setpoint

**What:** Verify `setHeatingSetpoint()` command works end-to-end.

**Steps:**

1. Ensure the device is authenticated and `thermostatMode` is **Heat** or **Auto**.
2. Note the current `heatingSetpoint` (e.g., **72°F**).
3. On the device page, run the command **`setHeatingSetpoint(75)`** [needs Trinity profile for exact command name/signature].
4. Watch the Hubitat attribute, logs, and the Watts app.
5. Wait 2–10 seconds for cloud confirmation.
6. Try setting an out-of-range setpoint (e.g., **35°F** or **100°F**) and observe the response [needs Cypher spec for min/max].

**Expected:**

- `heatingSetpoint` updates immediately to **75°F** in Hubitat (optimistic update)
- The logs show a successful API call or HTTP `200` status [needs Cypher spec for API response shape]
- Within 2–10 seconds, the Watts app reflects the new setpoint
- The physical thermostat display (if visible) shows the new setpoint
- Out-of-range values are rejected with a clear log error (e.g., **Setpoint out of valid range: 50–95°F**) [needs Cypher spec for valid range]
- No stack traces or auth failures appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 8: Setpoint Edge Cases

**What:** Verify setpoint validation and boundary behavior.

**Steps:**

1. Try `setHeatingSetpoint(-10)` (well below min).
2. Try `setHeatingSetpoint(120)` (well above max).
3. Try `setHeatingSetpoint(72.5)` (decimal/precision test).
4. Try `setHeatingSetpoint(0)` (zero).

**Expected:**

- Out-of-range attempts log a clear validation error and do not send to the cloud [needs Cypher spec for valid range]
- Decimal values are either accepted and rounded to the nearest integer, or rejected with a clear message [needs Cypher spec for precision]
- Zero or negative values are rejected with a helpful message
- The current setpoint does not change after an invalid command
- No HTTP errors or stack traces appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Mode Control

### Test 9: Set Thermostat Mode

**What:** Verify mode transitions (Off, Heat, Auto) work and display correctly.

**Steps:**

1. Ensure the device is authenticated.
2. Set mode to **Heat** via `setThermostatMode("heat")` [needs Trinity profile for exact command name].
3. Watch `thermostatMode` and `thermostatOperatingState` in Hubitat and logs.
4. Set mode to **Off**.
5. Set mode to **Auto** (if supported) [needs Cypher spec for available modes].
6. Verify the Watts app reflects each change.

**Expected:**

- `thermostatMode` updates to the requested mode immediately (optimistic)
- `thermostatOperatingState` remains **Heating** if the floor temp is below setpoint and mode is **Heat**, or transitions to **Idle** if mode is **Off**
- The logs show successful API calls or HTTP `200` responses [needs Cypher spec]
- The physical thermostat display reflects the new mode
- After 1–2 poll cycles, Hubitat reconciles with cloud state and confirms the mode is stable
- No auth failures, HTTP errors, or stack traces appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 10: Mode Transition Correctness

**What:** Verify operating state updates correctly as floor temperature changes.

**Steps:**

1. Set mode to **Heat** and setpoint to **72°F**.
2. Wait for the system to begin heating (if floor temp is below 72°F).
3. Observe `thermostatOperatingState` — should be **Heating**.
4. Continue to monitor the logs and Hubitat for 10–20 minutes as the floor warms.
5. When floor temperature reaches or exceeds the setpoint, observe `thermostatOperatingState` transition to **Idle**.

**Expected:**

- `thermostatOperatingState` accurately reflects whether the system is heating (**Heating**) or idle (**Idle**)
- State transitions are clean and appear in logs without jitter (no rapid flipping between states)
- The physical thermostat element (if accessible) confirms heating activity matches the operating state
- No spurious error messages or retries appear during state transitions

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Schedule (If Exposed)

### Test 11: Schedule Readback

**What:** Verify the driver can read and display the programmed schedule [needs Trinity profile to confirm if schedule is exposed].

**Steps:**

1. Ensure the device is authenticated.
2. In the Watts app, confirm a programmed schedule exists (e.g., weekday 6 AM: 72°F, 9 PM: 68°F).
3. On the Hubitat device page, look for a **`schedule`** attribute or a **`getSchedule()`** command [needs Trinity profile].
4. If exposed, run the command or inspect the attribute.

**Expected:**

- The driver displays the schedule in a human-readable format (e.g., JSON or descriptive text) [needs Trinity profile for format]
- All program entries (day, time, setpoint) are correctly read from the cloud
- The schedule matches the Watts app configuration
- No parse errors or stack traces appear

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 12: Schedule Modification (If Supported)

**What:** Verify the driver can modify schedules [needs Trinity profile to confirm if write-capable].

**Steps:**

1. Ensure the device is authenticated.
2. If the driver exposes a `setSchedule()` command, attempt to update a single program entry (e.g., change Monday 6 AM from 72°F to 70°F) [needs Trinity profile].
3. Wait 5 seconds for the cloud to confirm.
4. Verify the Watts app reflects the change.

**Expected:**

- The command succeeds and logs a success message or HTTP `200` response [needs Cypher spec]
- The Watts app reflects the updated schedule within 5–10 seconds
- A subsequent `getSchedule()` or `refresh()` shows the new value
- No stack traces or validation errors appear

**Note:** [needs Trinity profile] — This test may be deferred if schedule modification is not supported in v1.0.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Boost / Hold (If Supported)

### Test 13: Boost Mode

**What:** Verify custom boost/hold command if exposed [needs Trinity profile].

**Steps:**

1. Ensure the device is authenticated and in **Heat** mode at setpoint 72°F.
2. Run a custom command like `setBoost(60)` to boost the setpoint +60 minutes (or equivalent) [needs Trinity profile for exact signature].
3. Observe `heatingSetpoint`, `thermostatMode`, and any **`boostActive`** or **`boostTimeRemaining`** attributes [needs Trinity profile].
4. Wait approximately 60 minutes (or manually trigger a refresh before then to verify the timer is decrementing).
5. Verify boost expires and the thermostat returns to the original setpoint and mode.

**Expected:**

- `heatingSetpoint` increases (e.g., setpoint + 3–5°F boost, or to a preset value) [needs Cypher spec for boost behavior]
- A **`boostActive`** attribute becomes `true` or a timer attribute shows remaining time [needs Trinity profile]
- The Watts app reflects the boost and timer
- After the timer expires, the thermostat automatically returns to the previous setpoint and mode
- No stack traces or auth failures appear

**Note:** [needs Trinity profile] — This test may be deferred if boost is not supported in v1.0.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 14: Hold Mode

**What:** Verify hold/indefinite mode if exposed [needs Trinity profile].

**Steps:**

1. Ensure the device is authenticated with a programmed schedule active.
2. Run a custom command like `setHold()` to hold the current setpoint indefinitely (suspending schedule) [needs Trinity profile].
3. Observe `thermostatMode` or a **`holdMode`** attribute [needs Trinity profile].
4. Verify the schedule does not override the hold.
5. Run `releaseHold()` to resume the schedule.

**Expected:**

- `thermostatMode` changes to **Hold** (or a similar indicator) [needs Cypher spec for mode names]
- The programmed schedule is suspended; setpoint does not change even if the next scheduled time arrives
- A **`holdActive`** attribute becomes `true` [needs Trinity profile]
- The Watts app reflects the hold state
- After `releaseHold()`, the schedule resumes and the next program entry takes effect
- No stack traces or parsing errors appear

**Note:** [needs Trinity profile] — This test may be deferred if hold is not supported in v1.0.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Edge Cases & Network Resilience

### Test 15: Network Timeout

**What:** Verify the driver handles cloud connectivity loss gracefully.

**Steps:**

1. Ensure the device is authenticated and working.
2. Temporarily block internet access from the Hubitat hub (e.g., firewall rule, WiFi disconnect, or unplug ethernet).
3. On the device page, click **Refresh** or `setHeatingSetpoint(75)`.
4. Watch the logs and device state for 30 seconds.
5. Restore internet access.
6. Click **Refresh** again.

**Expected:**

- The failed refresh or command logs a clear error message, e.g.:
  - **Cloud request timed out after 30 seconds...**
  - **Unable to reach SunStat API...**
  - or another helpful network error [needs Trinity profile]
- The error suggests checking internet connectivity or the timeout preference
- `authStatus` does not crash; it may temporarily show **Offline** or **Connecting** [needs Trinity profile]
- No stack traces or infinite retry loops appear
- After internet is restored, **Refresh** succeeds cleanly without needing to recreate the device or re-enter credentials
- Subsequent polls resume at the configured interval

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 16: Malformed Cloud Response

**What:** Verify the driver recovers from unexpected API response shape [needs Cypher spec for expected API structure].

**Steps:**

1. This test may require mock HTTP interception or is deferred until Cypher finalizes the API contract.
2. If simulated, inject a malformed JSON response or missing field.

**Expected:**

- The driver logs a clear JSON parse error or missing-field warning [needs Trinity profile]
- The device does not crash or hang
- Attributes retain their previous values (no null/undefined shown to the user)
- The next poll or command automatically retries

**Note:** [needs Cypher spec] — This test is deferred until the exact API response shape is known. Once Cypher completes the API spec, this test will be executable via curl mocking or pcap replay.

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 17: Device Offline / Physically Powered Off

**What:** Verify the driver handles the thermostat being powered off.

**Steps:**

1. Ensure the device is authenticated and working.
2. Physically power off the SunStat thermostat (or disable it from the circuit breaker / switch).
3. Wait 1–2 poll cycles.
4. Observe Hubitat attributes and logs.
5. Power the device back on.
6. Wait 1–2 poll cycles.

**Expected:**

- After power-off, the next poll fails with a clear offline/unreachable message [needs Trinity profile]
- `authStatus` may change to **Device Offline** or similar [needs Trinity profile]
- No stack traces; the driver remains stable
- After power-on and WiFi reconnection, the next poll succeeds and state is restored
- Attributes do not show stale data indefinitely; they update once the device is responsive again

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 18: Rapid / Concurrent Commands

**What:** Verify the driver handles burst commands without race conditions or dropped updates.

**Steps:**

1. Ensure the device is authenticated and working.
2. Rapidly toggle mode: `setThermostatMode("heat")`, then `setThermostatMode("off")`, then `setThermostatMode("heat")` in quick succession (within 1 second).
3. Immediately issue a setpoint change: `setHeatingSetpoint(75)`.
4. Watch the logs and Hubitat attributes.
5. Wait 5 seconds and observe final state.

**Expected:**

- All commands are queued and processed (not dropped)
- The logs show each command attempt, even if some fail or are rate-limited [needs Cypher spec for rate limits]
- Final state is correct after all commands settle
- No stack traces, deadlocks, or orphaned HTTP requests appear
- Hubitat tile updates reflect the final state

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 19: Hub Reboot / Token Reuse

**What:** Verify the driver recovers after a Hubitat hub restart.

**Steps:**

1. Ensure the device is authenticated, working, and has issued at least one command (so credentials are validated in memory).
2. Reboot the Hubitat hub from **Settings → Reboot Hub**.
3. Wait for the hub to come back online (typically 2–5 minutes).
4. Open the device page and watch `authStatus` and logs for 30 seconds.
5. Test commands: **Refresh**, `setHeatingSetpoint(72)`, mode change.

**Expected:**

- After reboot, the driver reinitializes cleanly (no credentials re-entry required)
- `authStatus` returns to **Authenticated: <thermostat name>** after startup [needs Trinity profile]
- Polling resumes at the configured interval
- Refresh, setpoint, and mode commands still work without errors
- No stack traces or missing-schedule issues appear
- Device page loads and responds normally

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Capability Conformance

### Test 20: Hubitat Dashboard Display

**What:** Verify the thermostat tile displays correctly on a Hubitat dashboard.

**Steps:**

1. Create a new Hubitat dashboard or use an existing one.
2. Add the SunStat device to the dashboard using the **Thermostat** tile template [needs Trinity profile for exact tile name].
3. Observe the tile layout and displayed attributes.

**Expected:**

- The tile shows `thermostatMode`, `heatingSetpoint`, `currentTemperature`, and `thermostatOperatingState` [needs Trinity profile for exact layout]
- All values are readable and update in real-time
- Mode and setpoint buttons/controls are functional
- No red error indicators or missing data
- Colors and icons render correctly

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 21: Rule Machine Integration

**What:** Verify Rule Machine can read attributes and execute commands.

**Steps:**

1. Open Hubitat Rule Machine and create a simple rule that triggers on the SunStat device.
2. Example rule: "If `thermostatOperatingState` becomes Heating, send a notification" [needs Trinity profile].
3. Execute the rule by triggering the condition (e.g., set mode to Heat and ensure floor temp is below setpoint).
4. Verify the rule fires and actions execute.
5. Create a second rule: "Set SunStat to 75°F when motion is detected" [needs Trinity profile].

**Expected:**

- Rule Machine recognizes the SunStat device and lists its attributes and commands
- Rules trigger correctly and actions execute without errors
- Notifications or other downstream actions fire
- No stack traces in Hubitat logs related to Rule Machine or the SunStat device

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 22: Multi-Device Scenario

**What:** Verify multiple SunStat thermostats operate independently.

**Steps:**

1. If Mads has multiple SunStat thermostats on the same Watts account, add both to Hubitat.
2. Assign each to a different device instance.
3. Set different setpoints for each (e.g., Device 1 to 72°F, Device 2 to 68°F).
4. Observe both devices for 30 seconds.

**Expected:**

- Both devices authenticate independently
- Setpoint and mode changes to Device 1 do not affect Device 2
- Each device has its own `thermostatMode`, `heatingSetpoint`, `currentTemperature`, and `thermostatOperatingState`
- Polling for both devices occurs without interference
- No auth failures or state cross-contamination

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Test Area: Logging & Debug Output

### Test 23: Debug Logging Auto-Disable

**What:** Verify debug logging automatically disables after a timeout to prevent log spam.

**Steps:**

1. Enable **Debug logging** in Preferences.
2. Click **Save Preferences**.
3. Note the time.
4. Watch the logs — debug entries should appear.
5. Wait 30–35 minutes (or check logs periodically).
6. Verify debug logging stops and does not resume unless manually re-enabled.

**Expected:**

- Debug logging produces helpful diagnostic info (e.g., HTTP request/response, JSON parse details) [needs Trinity profile]
- After 30 minutes, a log entry appears: **Debug logging disabled** [needs Trinity profile]
- Debug logs stop appearing after that time
- Info/warning/error logs continue normally
- Manual re-enable via Preferences restarts the 30-minute timer

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

### Test 24: Sensitive Data Redaction in Logs

**What:** Verify credentials, tokens, and sensitive data are never logged.

**Steps:**

1. During all tests, search the Hubitat IDE Logs for:
   - Email or password
   - Any auth token or JWT substring
   - API key or management key (if applicable)
2. Perform a `grep` or text search in the logs for each credential.

**Expected:**

- No email, password, or credentials appear in any logs
- Auth tokens or sensitive headers are either omitted or redacted (e.g., `Authorization: [REDACTED]`)
- Log entries clearly identify what failed (e.g., "Auth failed with status 401") without exposing the secret
- Error messages are helpful but safe (e.g., "Invalid credentials. Check your email and password in Preferences.")

**Actual:**
(To be filled in during testing)

**Status:**
(Pending)

---

## Logging Watch List

During all tests, watch for these regressions:

1. **Auth problems**
   - `authStatus` should change clearly on success/failure
   - No passwords, tokens, hashes, or API keys should ever appear in logs

2. **Unexpected HTTP statuses**
   - Look for `Unexpected HTTP 4xx/5xx ...` in logs [needs Cypher spec for API error codes]
   - A single HTTP `401` may be followed by a token refresh + replay; confirm this behavior is documented [needs Trinity profile]

3. **State regressions**
   - `thermostatMode` should track the current mode (Off, Heat, Auto, Hold, Boost, etc.)
   - `heatingSetpoint` should always be a valid number, never null or undefined
   - `currentTemperature` and `floorTemperature` should be numeric and non-zero (unless sensor malfunction)
   - `thermostatOperatingState` should be Heating or Idle, never null

4. **Driver stability**
   - No stack traces, especially `MissingMethodException`, `NullPointerException`, `ClassCastException`, or JSON parse errors
   - No new forbidden Hubitat sandbox calls (`System.*`, `Thread.*`, `Runtime.*`, reflection, file I/O)
   - No memory leaks or orphaned schedules (verify by hub stability over time)

---

## Test Execution Matrix

**Priority Tier 1 (Core Functionality)**
- Test 1: Install and Initial Configuration
- Test 5: Refresh Current State
- Test 7: Set Heating Setpoint
- Test 9: Set Thermostat Mode

**Priority Tier 2 (State & Integration)**
- Test 2: Authentication Failure Handling
- Test 6: Polling Cycle
- Test 10: Mode Transition Correctness
- Test 20: Hubitat Dashboard Display
- Test 21: Rule Machine Integration

**Priority Tier 3 (Edge Cases & Advanced)**
- Test 8: Setpoint Edge Cases
- Test 15: Network Timeout
- Test 18: Rapid / Concurrent Commands
- Test 19: Hub Reboot / Token Reuse
- Test 22: Multi-Device Scenario

**Priority Tier 4 (Optional Features & Defer)**
- Test 11: Schedule Readback [needs Trinity profile]
- Test 12: Schedule Modification [needs Trinity profile]
- Test 13: Boost Mode [needs Trinity profile]
- Test 14: Hold Mode [needs Trinity profile]
- Test 16: Malformed Cloud Response [needs Cypher spec]

---

## Dependency Notes

### [needs Cypher spec]

The following tests cannot execute until Cypher completes API reverse-engineering:

- **Mode names:** Exact string values for Off, Heat, Auto, Boost, Hold, etc.
- **Setpoint range:** Minimum and maximum allowed setpoint values (e.g., 50–95°F)
- **Operating state:** Exact values for Heating, Idle, and any other states
- **API error codes and responses:** Expected HTTP status codes, JSON structure for success/failure
- **Units:** Celsius vs. Fahrenheit configuration (or auto-detection)
- **Boost/Hold behavior:** Exact semantics (setpoint delta, duration, auto-expiry)
- **Rate limits:** Any throttling or request-per-minute limits
- **Schedule structure:** JSON schema or format if schedule is exposed

### [needs Trinity profile]

The following tests cannot execute until Trinity finalizes the capability list and command signatures:

- **Exposed capabilities:** Thermostat, TemperatureMeasurement, Refresh, etc.
- **Command signatures:** `setHeatingSetpoint(value)`, `setThermostatMode(mode)`, custom commands like `setBoost(duration)`
- **Attributes:** Which temperature sensors are exposed (floorTemperature, currentTemperature, etc.)
- **Optional commands:** Whether schedule read/write, boost, hold, and other advanced features are included
- **Tile behavior:** Exactly which attributes appear on the default Hubitat thermostat tile
- **Debug logging:** Specific log format and categories

---

## When to Update This Plan

Once Cypher completes the API spec (Test 16, mode names, setpoint range, etc.), update this plan:
1. Replace `[needs Cypher spec]` markers with concrete API details
2. Add any missing test cases discovered during reverse-engineering (e.g., firmware update behavior, sensor calibration)

Once Trinity finalizes the capability profile:
1. Replace `[needs Trinity profile]` markers with exact command signatures and attributes
2. Finalize Test 11–14 (Schedule, Boost, Hold) based on what is actually exposed
3. Confirm dashboard tile behavior for Test 20

---

**Once all items in Tier 1 and Tier 2 are checked and passing, the driver is ready for beta testing with Mads.**




---

# Cypher Audit — PurpleAir AQI Virtual Sensor (pfmiller0)
**Date:** 2026-05-18T16:25:00-07:00  
**Requested by:** Mads Kristensen  
**Driver under review:** pfmiller0/Hubitat — `PurpleAir AQI Virtual Sensor.groovy`  
**Source:** https://github.com/pfmiller0/Hubitat/blob/main/PurpleAir%20AQI%20Virtual%20Sensor.groovy

---

## 1. Verdict

**INSTALL.** This is the cloud-API PurpleAir driver that did not surface in the prior search. It targets `api.purpleair.com/v1/sensors` (not `http://<ip>/json`), requires no hardware, accepts the user's API key via `X-API-Key` header, supports geolocation-based multi-sensor averaging OR a specific sensor index (neighbor's sensor), and implements US EPA Barkjohn-derived AQI correction for wildfire smoke. Prior "BUILD" recommendation is **rescinded** — this driver satisfies the cloud-API gap. Score: 88/100.

---

## 2. Protocol Confirmation

**CLOUD — confirmed.**

```groovy
final String URL = "https://api.purpleair.com/v1/sensors"
Map params = [
    uri: URL,
    headers: ['X-API-Key': X_API_Key],
    query: httpQuery,
    ...
]
asynchttpGet('httpResponse', params, ...)
```

| Question | Answer |
|---|---|
| Endpoint | `https://api.purpleair.com/v1/sensors` (cloud REST v1) ✅ |
| Auth | `X-API-Key` header (user-provided API key) ✅ |
| Target sensor | Two modes: geolocation bounding-box search **or** explicit `sensor_index` — can point at any public sensor, including a neighbor's ✅ |
| EPA Barkjohn correction | YES — full piecewise US EPA formula implemented (`us_epa_conversion()`), sourced from EPA CEMM report (cfpub.epa.gov/si/si_public_record_report.cfm?dirEntryId=353088). Also supports Woodsmoke, AQ&U, LRAPA conversions. ✅ |
| Transport safety | `asynchttpGet` — fully sandbox-safe ✅ |

The driver does NOT hit the local `http://<ip>/json` endpoint anywhere. Protocol is unambiguously cloud-only.

---

## 3. Quality Audit

### Metadata

| Field | Value |
|---|---|
| **Author** | Peter Miller (`pfmiller0`) |
| **Version** | 1.3.2 |
| **Last commit** | 2025-06-18 ("Fix for change in http response, and a few other minor changes") |
| **Repo** | github.com/pfmiller0/Hubitat (flat repo, all drivers in root) |
| **Import URL** | Self-declares `importUrl` — can be added to Hubitat via HPM or direct import |
| **License** | No explicit LICENSE file; importUrl present |

### Capabilities & Attributes

```
Capabilities: Sensor, Polling, Initialize
Custom attributes:
  aqi       (number) — PM2.5 AQI after any conversion
  category  (string) — "Good" / "Moderate" / "Unhealthy for sensitive groups" / etc.
  conversion (string) — which correction algorithm was applied
  sites     (string) — list of sensor sites contributing to average
```

**Gap:** No `AirQuality` standard capability declared. Hubitat's built-in `AirQuality` capability exposes an `airQualityIndex` attribute used by some dashboard tiles and RM templates. This driver uses a custom `aqi` attribute instead. Not a blocker, but RM rules and dashboards must use custom attribute rather than capability template. Minor.

**No HealthCheck capability.** Also minor.

### Poll Interval

User-configurable enum: 1 / 5 / 10 / 15 / 30 / 60 / 180 min / disabled. Default: 60 min. ✅

### Sensor Mode Options

- **Geolocation search** (`device_search=true`, default): Bounding box centered on hub coordinates. Weighted average by distance. Filters sensors with confidence < 90.
- **Single sensor** (`device_search=false`): Explicit `sensor_index` (the `SELECT=INDEX` from map.purpleair.com URL) + optional `read_key` for private sensors. Mads can use a neighbor's sensor by ID. ✅

### Error Handling

- Exponential backoff on HTTP errors: `failCount` increments, reschedules at `failCount × interval` with cap at 6× interval after 5 failures. Errors muted at failCount ≥ 5 (prevents log spam during extended outages). ✅
- MIME-type check on response (not just status code — detects maintenance pages returning 200 HTML). ✅  
- Confidence filter ≥ 90 hard-coded (preference `confidenceThreshold` is commented out). Benign.
- Null pm25 guard: `if (! it[RESPONSE_FIELDS[data.pm25_count]])` → `log.error`. ✅
- Broken humidity sensor detection: optional `hum_history` mode tracks per-hour humidity per site, detects sensors reporting constant values. ✅ (US EPA mode only)
- `HUMIDITY_FUDGE = 4`: +4% offset applied to raw humidity, matching PurpleAir's documented ~4% below-ambient bias. ✅

### Remaining Gaps (minor)

1. **No `AirQuality` capability** — custom `aqi` attribute only. Dashboards expecting standard capability won't auto-populate.
2. **Confidence threshold not user-configurable** — hard-coded 90 (preference input is commented out). Not a bug; 90 is a reasonable default.
3. **`sensorAverage` warns but returns 0 if all sensors return 0** — edge case: during extreme events where all nearby sensors lock to 0.0, the driver emits AQI 0 rather than an error. Low probability.

### Other pfmiller0 Drivers (one-line pass)

- `IQAir Virtual Sensor.groovy` — IQAir cloud AQI via api.iqair.com (similar shape to this driver; AQI from city-level data rather than local sensor network)
- `Average Temperature.groovy` — virtual temperature aggregator across multiple Hubitat sensors
- `Energy Cost Tracker.groovy` — energy cost tracking app (relevant to PNW rate tier optimization)
- `Hubitat Google Drive backup.groovy` — hub config backup to GDrive (notable utility app)
- `Device monitor.groovy` — watchdog for unresponsive devices

---

## 4. Trinity Rubric Score

| Dimension | Max | Score | Rationale |
|---|---|---|---|
| Local vs. cloud | 10 | 10 | Cloud REST on PurpleAir v1 stable API |
| Mads can test | 15 | 15 | No hardware required — free API key + any public sensor by ID or hub geolocation |
| User demand | 15 | 15 | PNW wildfire season, AQI monitoring, neighbor's sensor use case |
| Sandbox-safe | 15 | 15 | `asynchttpGet` + headers only, no crypto, no reflection |
| Vendor stability | 15 | 15 | PurpleAir v1 API documented, API key monetized (stable incentive to maintain) |
| Effort | 10 | 10 | Zero — copy-paste via importUrl or HPM import |
| Maintenance | 10 | 8 | Last commit 2025-06-18 (~11 months ago); commit message confirms responsive maintenance (fixed actual API change). Not stale but not a 2026 active repo. |
| **Total** | **90** | **88** | **Strong Fit → INSTALL** |

**Threshold:** ≥ 80 = Strong Fit. **88/100 clears threshold.**

---

## 5. Correction to Prior Audit

**Honest accounting of the search miss:**

The prior audit (cypher-purpleair-audit.md, 2026-05-18T16:12) searched for PurpleAir cloud-API Hubitat drivers and concluded "Zero community drivers target `api.purpleair.com/v1/sensors`." This was wrong.

**Why it was missed:**

1. **Generic repo name.** `pfmiller0/Hubitat` contains a flat list of drivers with no dedicated README that would surface in GitHub search results for "PurpleAir Hubitat driver."
2. **Filename doesn't match search keywords.** "PurpleAir AQI Virtual Sensor" — searching for "purpleair cloud" or "purpleair api" wouldn't match because the driver file name and repo name don't include those terms.
3. **GitHub code search vs. repo search.** Code-level search for `api.purpleair.com` in Groovy files was not performed — only repo-level search. The driver would have surfaced in a `code:api.purpleair.com lang:groovy` search.
4. **Low star count.** The repo doesn't appear in top results for generic "Hubitat PurpleAir" searches.

**Lesson for future audits:** When repo search returns zero results, follow with a GitHub **code search** (`code:"api.purpleair.com" lang:groovy`) before concluding greenfield exists. URL-pointed candidates from users always override prior search conclusions — treat user-pointed sources as primary evidence, search as secondary.

---

## 6. Sources

- Driver source: https://raw.githubusercontent.com/pfmiller0/Hubitat/main/PurpleAir%20AQI%20Virtual%20Sensor.groovy
- Repo: https://github.com/pfmiller0/Hubitat
- Last commit (API): https://api.github.com/repos/pfmiller0/Hubitat/commits?path=PurpleAir%20AQI%20Virtual%20Sensor.groovy&per_page=1 → 2025-06-18T01:10:42Z
- PurpleAir API docs: https://api.purpleair.com/
- EPA Barkjohn formula source cited in driver: https://cfpub.epa.gov/si/si_public_record_report.cfm?dirEntryId=353088&Lab=CEMM
- Prior audit (now superseded for cloud-gap claim): `.squad/decisions.md` / `.squad/decisions/inbox/cypher-purpleair-audit.md`



---

# Trinity Audit — pfmiller0 PurpleAir AQI Virtual Sensor
**Date:** 2026-05-18T16:35:00-07:00
**Requested by:** Mads Kristensen
**Auditor:** Trinity (Lead/Architect)
**Audit type:** Code quality (not protocol fit — protocol already scored 88/100 in decisions.md)

---

## 1. Verdict

**MEDIUM → PR upstream.** Two conversion-algorithm BLOCKERS are the headline; maintainer is actively
responding (last commit 2025-06-18). Submit PRs for the BLOCKERs first, then follow with the MINOR
hygiene items. Do NOT fork — pfmiller0 owns the bug surface and is fixing it.

---

## 2. Driver Basics

| Field | Value |
|---|---|
| **Repo** | https://github.com/pfmiller0/Hubitat |
| **File** | `PurpleAir AQI Virtual Sensor.groovy` |
| **Author** | Peter Miller (`pfmiller0`) |
| **Version** | 1.3.2 |
| **Last commit** | 2025-06-18 ("Fix for change in http response, and a few other minor changes") |
| **Lines of code** | ~500 |
| **HPM** | importUrl present; no `packageManifest.json` seen in repo |
| **Protocol** | Cloud REST — `asynchttpGet` ✅ |

---

## 3. Findings

| # | Severity | Category | Finding | Suggested fix | Approx. lines |
|---|---|---|---|---|---|
| 1 | **BLOCKER** | Correctness | `apply_conversion()` checks `"AQ and U"` but preference emits `"AQ&U"`. String never matches → AQ&U conversion is silently dead code; users get raw PM2.5 instead of corrected value. | Change check to `"AQ&U"` | `apply_conversion()` ~line 310 |
| 2 | **BLOCKER** | Correctness | In `sensorCheck()`, `pm25_count` selection checks `"lrapa"` and `"woodsmoke"` (lowercase) against preference values `"LRAPA"` and `"Woodsmoke"` (mixed case). Woodsmoke and LRAPA conversions silently request `pm2.5` (atmospheric) instead of `pm2.5_cf_1` (channel 1 required by both formulas). Correction factor applied to wrong input data — corrupts AQI output. | Change to `"LRAPA"` and `"Woodsmoke"` to match preference strings | `sensorCheck()` ~line 85 |
| 3 | **MAJOR** | Performance | `state.failCount?:0 + 1` — operator precedence bug. Evaluates as `state.failCount ?: (0 + 1)` i.e., `state.failCount ?: 1`. failCount never increments above 1. The exponential backoff never triggers; on API errors the driver hammers PurpleAir at the normal poll interval instead of backing off progressively. | Change to `(state.failCount ?: 0) + 1` | `httpResponse()` ~line 103 |
| 4 | **MINOR** | Event hygiene | No `lastActivity` attribute or HealthCheck capability. Cloud REST drivers should implement the `lastActivity`-only Pattern B (see `hubitat-healthcheck-vs-lastactivity` skill). Without it, Rule Machine rules and dashboards have no way to detect if the sensor has stopped polling. | Add `attribute "lastActivity", "string"` and `touchActivity()` helper called on successful 200 response. Reference: Gemstone Lights `drivers/gemstone-lights/gemstone-lights.groovy`. | New ~8 lines |
| 5 | **MINOR** | Event hygiene | All `sendEvent` calls fire on every poll regardless of value change. At 1-minute polling the driver emits 1,440+ events/day per attribute. `emitIfChanged` pattern would suppress unchanged values. | Wrap AQI/category/sites sendEvents in skip-if-match checks. Reference: `hubitat-event-hygiene` skill. | ~6 lines |
| 6 | **MINOR** | Event hygiene | `sendEvent(name: "aqi", ..., descriptionText: "${AQIcategory}")` — the event log shows only "Good" with no device name or AQI number. | Change to `"${device.displayName} AQI is ${aqi2_5Value} (${AQIcategory})"` | `httpResponse()` ~line 190 |
| 7 | **MINOR** | Event hygiene | `sendEvent(name: "sites", value: sites, descriptionText: "AQI reported from site ${sites}")` — no `device.displayName` prefix; `sites` is a List, not a String, so descriptionText may serialize as `[Site A, Site B]`. | Prefix with device.displayName; call `sites.join(", ")` in the descriptionText | ~line 185 |
| 8 | **NIT** | Code quality | `AQIcategory = getCategory(aqi2_5Value)` inside `httpResponse()` — missing `def`/type declaration. Implicit binding works in Groovy/Hubitat sandbox today but is non-idiomatic and a maintenance risk. | `String AQIcategory = getCategory(aqi2_5Value)` | ~line 165 |
| 9 | **NIT** | Code quality | `update_interval == "0"` error path calls `runIn(Integer.valueOf(update_interval) * state.failCount * 60, 'refresh')` = `runIn(0, ...)` = immediate refresh, even when user disabled polling. Should guard: `if (update_interval != "0")` before that runIn. | Wrap backoff runIn in `if (update_interval != "0")` guard | `httpResponse()` ~line 105 |

---

## 4. Change Size Estimate

**MEDIUM — ~60–90 lines diff.**

- Finding #1: 1 line fix
- Finding #2: 2 line fix (two lowercase string literals)
- Finding #3: 1 line fix
- Finding #4: ~10 new lines (lastActivity attribute + touchActivity helper + call site)
- Finding #5: ~15 lines (wrap 3 sendEvents in change-check)
- Findings #6–9: ~5 lines total

Two BLOCKERs are trivially small code changes (string literal fixes). The MAJOR is also a 1-liner.
The MINORs are hygiene polish. Total diff is medium but not architectural.

---

## 5. PR vs. Fork Recommendation

**Submit PRs upstream.**

pfmiller0 committed a bug fix in June 2025 — less than 12 months ago. The repo is alive. The two
BLOCKER bugs (case-mismatch in conversion names) are each 1-line fixes that would survive a quick
review without controversy. Submit two separate PRs: one for the two conversion BLOCKERs (#1 and #2
together, one `apply_conversion` fix + one `sensorCheck` fix), one for the failCount backoff bug
(#3). The hygiene items (#4–9) can follow as a polish PR if the first two are accepted.

If PRs are ignored for >60 days, revisit fork: the driver is small enough (~500 lines) to adopt. Fork
would live at `drivers/purpleair-aqi/purpleair-aqi.groovy`. But try upstream first.

---

## 6. PR Starter List (ranked by impact)

1. **[BLOCKER] Fix AQ&U and case-mismatch conversion strings**
   - In `apply_conversion()`: change `"AQ and U"` → `"AQ&U"`
   - In `sensorCheck()`: change `"lrapa"` → `"LRAPA"`, `"woodsmoke"` → `"Woodsmoke"`
   - *Impact: AQ&U, Woodsmoke, and LRAPA conversions are currently broken/producing wrong output*

2. **[MAJOR] Fix failCount operator precedence in backoff logic**
   - In `httpResponse()`: change `state.failCount = state.failCount?:0 + 1` → `state.failCount = (state.failCount ?: 0) + 1`
   - *Impact: Exponential backoff never works; hub hammers PurpleAir on failures*

3. **[MINOR] Add `lastActivity` attribute and `touchActivity()` helper**
   - Declare `attribute "lastActivity", "string"` in metadata
   - Add `private void touchActivity()` pattern (see Gemstone Lights exemplar)
   - Call after successful 200 response in `httpResponse()`
   - *Impact: Rule Machine and dashboards gain health observability*

4. **[MINOR] emitIfChanged for AQI/category/sites events**
   - Wrap the three main sendEvents in change checks to suppress duplicate events at short poll intervals

5. **[MINOR] Fix aqi descriptionText to include device name and AQI value**
   - `"${device.displayName} AQI is ${aqi2_5Value} (${AQIcategory})"` instead of bare `"${AQIcategory}"`

---

## 7. Maintainer Responsiveness

**RESPONSIVE.** Last commit 2025-06-18 (~11 months before this audit date). Fix was functional
("change in http response") suggesting active maintenance for real usage. PRs have a reasonable chance
of acceptance. Submit the BLOCKER PR first with a clear test case (e.g., "select AQ&U conversion,
check that aqi value changes vs raw pm2.5").


---

# Trinity Audit — GvnCampbell Fully Kiosk Browser Controller
**Date:** 2026-05-18T16:35:00-07:00
**Requested by:** Mads Kristensen (2x installs: Bathroom tablet + Kitchen tablet)
**Auditor:** Trinity (Lead/Architect)
**Audit type:** Code quality

---

## 1. Verdict

**MEDIUM → FORK.** The driver is functionally adequate but has 3 MAJOR findings and its maintainer
has been completely silent for **4.5 years** (last commit 2021-11-20). With Mads running two
instances that both need to stay stable, orphaned code at this age needs to be owned. Fork it.

---

## 2. Driver Basics

| Field | Value |
|---|---|
| **Repo** | https://github.com/GvnCampbell/Hubitat |
| **File** | `Drivers/FullyKioskBrowserController.groovy` |
| **Author** | Gavin Campbell (`GvnCampbell`) |
| **Version** | 1.41 |
| **Last commit** | 2021-11-20 — **4.5 years stale** |
| **Lines of code** | ~350 (22KB) |
| **HPM** | importUrl present; no `packageManifest.json` seen in repo listing |
| **Protocol** | Local LAN HTTP — `asynchttpPost` for commands ✅; `asynchttpGet` for refresh ✅ |

---

## 3. Findings

| # | Severity | Category | Finding | Suggested fix | Approx. lines |
|---|---|---|---|---|---|
| 1 | **MAJOR** | Security | `serverPassword` preference declared as `type:"string"` → password is shown in cleartext in the driver UI and is embedded in every HTTP URL (`...&password=${serverPassword}&...`). Also logged at debug level via `logger(logprefix+postParams)` in `sendCommandPost()`. | Change pref to `type:"password"`; in `sendCommandPost()` suppress the full params map in debug log or redact the password field. | `preferences` block + `sendCommandPost()` ~lines 65, 205 |
| 2 | **MAJOR** | Event hygiene | `refreshCallback()` calls `sendEvent` for `battery`, `switch`, `level`, and `currentPageUrl` on every 1-minute poll with no change-check. With `statePolling=true`, this generates 5,760+ low-value events/day across four attributes. | Apply `emitIfChanged` pattern (reference: `hubitat-event-hygiene` skill). Compare `device.currentValue(name)` before calling `sendEvent`. | `refreshCallback()` ~lines 220–225 |
| 3 | **MAJOR** | Event hygiene | `parse()` emits `sendEvent([name:"switch",value:body.value])`, `battery`, `motion`, `acceleration`, `volume` with **no `descriptionText`** on any event. Events tab Description column is blank for all pushed events from the tablet. | Add `descriptionText: "${device.displayName} ${name} → ${value}"` to every `sendEvent` in `parse()` and `motion()`/`acceleration()`. Reference: `hubitat-event-hygiene` skill. | `parse()` + helpers ~lines 115–145 |
| 4 | **MINOR** | Security | `serverPassword` is logged at debug level because `logger(logprefix+postParams)` in `sendCommandPost()` logs the full `postParams` Map which includes the URL with `?password=...` embedded. | Change to `logger(logprefix+"[cmd hidden]", "debug")` or redact: log URI host+port only. | `sendCommandPost()` ~line 205 |
| 5 | **MINOR** | Best practices | `parse()` has no null/exception guard around `parseJson(body)`. If `msg.body` is null or the tablet sends malformed JSON, `parseJson` will throw `JsonException` and drop the event silently. | Wrap in `try { ... } catch (Exception e) { log.error "[parse] JSON parse failed: ${e.message}" }` | `parse()` ~lines 115–120 |
| 6 | **MINOR** | Logging | The `logger()` function option list is `["none","debug","trace","info","warn","error"]` but the elif logic treats `debug` as "log everything" and `trace` as "log only trace+info". This is reversed from conventional logging convention (trace is most verbose; debug is less). Users selecting "trace" expect MORE output than "debug", but get less. | Either rename the options to clarify ("verbose"/"debug") or rewrite the elif chain to follow standard severity ordering. | `logger()` ~lines 255–275 |
| 7 | **MINOR** | Maintenance | Version `1.41` exists only in the file comment header — no `@Field static final String DRIVER_VERSION = "1.41"` constant and no HPM-compatible `packageManifest.json`. Hubitat Package Manager cannot track updates. | Add `@Field static final String VERSION = "1.41"` (pattern from pfmiller0 PurpleAir driver); create `packageManifest.json` for HPM. | Header + new manifest file |
| 8 | **NIT** | Best practices | `checkInterval` event (`sendEvent([name:"checkInterval",value:60])`) is sent in two places: `parse()` default branch and `sendCommandCallback()`. This is the old HealthCheck ping-interval hint mechanism — the driver declares `capability "HealthCheck"` but uses only this legacy event rather than the full `ping()` + `healthStatus` pattern. For a local LAN driver, full HealthCheck with `ping()` delegating to `refresh()` would be better. | Replace `checkInterval` event spam with proper `ping() { refresh() }` and `healthStatus` attribute. Reference: `hubitat-healthcheck-vs-lastactivity` skill Pattern A. | ~10 new lines |

---

## 4. Change Size Estimate

**MEDIUM (~80–120 lines diff).**

- Finding #1 (password type + log redact): ~5 lines
- Finding #2 (emitIfChanged in refreshCallback): ~15 lines
- Finding #3 (descriptionText everywhere): ~10 lines
- Finding #4 (log redact): ~2 lines (same PR as #1)
- Finding #5 (parse null guard): ~5 lines
- Finding #6 (logger rewrite): ~15 lines
- Finding #7 (version constant): 1 line + new manifest file
- Finding #8 (HealthCheck proper): ~12 lines

Total: ~60–65 lines code changes + manifest. MEDIUM by count but maintainer silence makes this a FORK decision.

---

## 5. PR vs. Fork Recommendation

**Fork into this repo as `drivers/fully-kiosk/fully-kiosk.groovy`.**

GvnCampbell has committed nothing since 2021-11-20. That's 4.5 years of silence. The community
forum thread (community.hubitat.com/t/release-fully-kiosk-browser-controller/12223) is the canonical
reference, but the driver is orphaned. Mads runs two instances in active daily use (bathroom + kitchen
tablets). With the Hubitat platform evolving (firmware 2.9.0 already broke speak(), requiring a fix in
v1.41 — the last commit), it's a matter of time before the next firmware break hits.

The code is small (~350 lines), well-structured, and the protocol is stable (FKB REST API). Forking
is low-effort. Upstream PR is not viable — even if the repo accepted a PR, GvnCampbell is clearly not
reviewing anything.

---

## 6. Fork Scope

**What to keep:**
- All command methods (bringFullyToFront, loadURL, screenOn/Off, speak, setVolume, etc.) — these are
  correct implementations of the Fully Kiosk Browser REST API.
- `configure()` JavaScript injection — the `injectJsCode` approach is the canonical FKB push-event
  pattern; keep it.
- `asynchttpPost` / `asynchttpGet` usage — already async, no change needed.
- Capability set (Switch, SwitchLevel, MotionSensor, Battery, Alarm, AudioVolume, etc.) — correct for
  this device type.

**What to rewrite/add:**
1. `serverPassword` → `type:"password"` preference + remove password from debug logs.
2. `refreshCallback()` → add `emitIfChanged` pattern for all 4 attributes.
3. `parse()` + helpers → add `descriptionText` to all `sendEvent` calls; add JSON null guard.
4. `logger()` → simplify to standard `logEnable` bool + `log.debug`/`log.info`/`log.warn`/`log.error`
   (the fancy multi-level logger is over-engineered for this driver size and has the severity inversion
   bug; replace with Hubitat community standard `if (logEnable) log.debug ...`).
5. Proper HealthCheck: replace `checkInterval` event spam with `ping() { refresh() }`.
6. Add `@Field static final String VERSION = "1.50"` and `packageManifest.json`.
7. `lastActivity` attribute updated on every successful `refreshCallback` 200 response.

**Suggested home:** `drivers/fully-kiosk/fully-kiosk.groovy`
**Suggested version bump:** Start at `2.0.0` (fork, clean rewrite of logger + hygiene) to distinguish
from the upstream 1.41 lineage.

**Two-device support note:** Mads's two tablets (Bathroom + Kitchen) each need their own Hubitat
device with their own IP/port/password preferences. The current driver is already single-device per
instance — no parent/child needed. The `controllerName` multi-controller binding pattern
(`multi-controller-binding` skill) is not needed here since each tablet is physically distinct.

---

## 7. Maintainer Responsiveness

**UNRESPONSIVE (4.5 years).** No basis for PR. Fork is the correct path.


---

# Trinity Audit — djdizzyd Advanced Honeywell T6 Pro Thermostat
**Date:** 2026-05-18T16:35:00-07:00
**Requested by:** Mads Kristensen (Downstairs thermostat; Upstairs may run generic Hubitat driver)
**Auditor:** Trinity (Lead/Architect)
**Audit type:** Code quality

---

## 1. Verdict

**MEDIUM → FORK.** The driver has 1 confirmed BLOCKER (`txtEnable` undefined), 3 MAJORs including a
silent nil-dereference bug that corrupts thermostat operating-state logic, and the maintainer has been
completely silent since **2021-01-22 (4+ years)**. For a thermostat driver that affects home climate
control, orphaned code with active bugs needs to be owned. Fork it.

---

## 2. Driver Basics

| Field | Value |
|---|---|
| **Repo** | https://github.com/djdizzyd/hubitat |
| **File** | `Drivers/Honeywell/Advanced-Honeywell-T6-Pro.groovy` |
| **Author** | Bryan Copeland (`djdizzyd`) |
| **Version** | v1.2 |
| **Last commit** | 2021-01-22 — **4+ years stale** |
| **Lines of code** | ~500 |
| **HPM** | importUrl present; no `packageManifest.json` seen in repo |
| **Protocol** | Z-Wave (event-driven; no HTTP) |
| **Z-Wave security** | `zwaveSecureEncap()` — modern S2 security encapsulation ✅ |

---

## 3. Findings

| # | Severity | Category | Finding | Suggested fix | Approx. lines |
|---|---|---|---|---|---|
| 1 | **BLOCKER** | Correctness | `txtEnable` is referenced throughout (`if (txtEnable) log.info ...`) but is **never declared as a preference**. Only `logEnable` is in `preferences {}`. `txtEnable` is always null/false, so all informational log statements (battery %, AC mains events, etc.) are permanently silenced regardless of user settings. | Either add `input "txtEnable", "bool", title: "Enable description text logging"` to preferences, or replace all `if (txtEnable)` guards with `if (logEnable)`. | ~20 call sites throughout |
| 2 | **MAJOR** | Correctness | `device.currentValue=="cooling"` (missing attribute name argument) in `zwaveEvent(ThermostatFanStateReport)` and `zwaveEvent(BasicSet)`. `device.currentValue` without args is a method reference — always truthy in Groovy. The condition `device.currentValue=="cooling"` is always `false` (a method object never equals the string "cooling") rather than the intended `device.currentValue("thermostatOperatingState")=="cooling"`. The operating-state polling logic after a fan state change is subtly wrong: the corrective `thermostatOperatingStateGet` fires at the wrong times. | Change `device.currentValue=="cooling"` → `device.currentValue("thermostatOperatingState")=="cooling"` in both locations. | `zwaveEvent(ThermostatFanStateReport)` and `zwaveEvent(BasicSet)` |
| 3 | **MAJOR** | Scheduler | `configure()` calls `runEvery3Hours("syncClock")` without calling `unschedule()` first. `updated()` does call `unschedule()` before calling `runEvery3Hours`, but if the user manually triggers `configure()` from the device page (common during setup), zombie `syncClock` schedulers pile up — one per configure invocation. After 3 configure invocations, `syncClock` fires 3× every 3 hours. | Add `unschedule("syncClock")` at the top of `configure()` before `runIn(10, "syncClock")` and `runEvery3Hours("syncClock")`. | `configure()` |
| 4 | **MAJOR** | Correctness | `zwave.configurationV1.configurationGet(parameterNumber: 52)` in `zwaveEvent(ThermostatFanStateReport)` — parameter 52 is not in `configParams` (map only covers params 1–42). The resulting `ConfigurationReport` arrives in `zwaveEvent(ConfigurationReport)`, checks `configParams[52]` which is `null`, and silently does nothing. This looks like dead/abandoned code for a thermostat mode parameter that was never completed. | Either remove the `configurationGet(52)` call, or add param 52 to `configParams` with its correct definition. T6 Pro param 52 is not in the standard Honeywell T6 Pro Z-Wave parameter set (params 1–42 per Honeywell documentation); this is likely a copy-paste leftover. | `zwaveEvent(ThermostatFanStateReport)` |
| 5 | **MINOR** | Lifecycle | No `initialize()` method despite Z-Wave drivers benefiting from hub-reboot recovery. After a hub restart, Z-Wave associations may need to be re-established. Adding an `initialize()` that calls `configure()` (or at minimum re-sends the association set) ensures the thermostat re-registers with the hub after reboot. | Add `void initialize() { configure() }` and optionally declare `capability "Initialize"` in metadata. | ~3 new lines |
| 6 | **MINOR** | Best practices | `supportedThermostatModes` and `supportedThermostatFanModes` events use the old `toString().replaceAll(/"/,"")` pattern: `value: supportedThermostatModes.toString().replaceAll(/"/,"")`. Current Hubitat best practice emits these as a JSON array via `groovy.json.JsonOutput.toJson(list)`. The old pattern produces `[auto, off, heat, emergency heat, cool]` which some automations struggle to parse. | Change to `sendEvent(name:"supportedThermostatModes", value: groovy.json.JsonOutput.toJson(supportedThermostatModes), isStateChange:true)` | `initializeVars()` |
| 7 | **MINOR** | Event hygiene | `eventProcess()` does a string comparison `device.currentValue(evt.name).toString() != evt.value.toString()` before emitting. For temperature setpoints, `"68.0"` vs `"68"` would trigger a false-positive emit (same temp, different string representation). The `BigDecimal`-aware comparison from the `hubitat-event-hygiene` skill would eliminate this. | Use numeric comparison for numeric attributes: `safeBigDecimal(current) != safeBigDecimal(incoming)` before emitting. Reference: `emitIfChanged` in `hubitat-event-hygiene` skill. | `eventProcess()` |
| 8 | **MINOR** | Logging | `log.info "Notification: " + ZWAVE_NOTIFICATION_TYPES[cmd.notificationType]` fires unconditionally (no `logEnable` guard) for every Z-Wave notification. Z-Wave thermostats send periodic notifications; this spams the live log regardless of user's log setting. | Wrap in `if (logEnable) log.debug ...` or gate at `log.info` but only for relevant notification types (power management events 2+3 are fine as log.info; the rest should be debug). | `zwaveEvent(NotificationReport)` |
| 9 | **NIT** | Maintenance | Version `v1.2` only in file-header comment. No `@Field static final String VERSION = "1.2"` constant; no `packageManifest.json` for HPM. | Add `@Field static final String VERSION = "1.2"` to file constants section; create `packageManifest.json`. | Header |

---

## 4. Change Size Estimate

**MEDIUM (~80–100 lines diff).**

- Finding #1 (txtEnable fix): add 1 preference line + optionally replace ~20 guard calls (or just add the pref — simplest fix)
- Finding #2 (`currentValue` bug): 2 line fixes (two locations)
- Finding #3 (unschedule in configure): 1 line
- Finding #4 (remove param 52 call): 1 line
- Finding #5 (initialize): 3 lines
- Finding #6 (supportedThermostatModes JSON): 2 lines
- Finding #7 (eventProcess numeric compare): ~8 lines
- Finding #8 (log.info guard): ~3 lines
- Finding #9 (version): 1 line + new manifest

Total: ~40–50 code lines + manifest. MEDIUM by raw change count, but FORK is driven by maintainer
status, not change size.

---

## 5. PR vs. Fork Recommendation

**Fork into this repo as `drivers/honeywell-t6-pro/honeywell-t6-pro.groovy`.**

djdizzyd has committed nothing to this driver since 2021-01-22. The repo itself shows some activity
in other drivers from that era but nothing recent on the Honeywell T6 Pro file. With Mads running at
least one T6 Pro (Downstairs, and potentially adding Upstairs), a thermostat driver that silently
mis-logs, has a nil-dereference in fan-state logic, and accumulates zombie schedulers on configure is
not "install and forget." The fix surface is small and the Z-Wave protocol is stable — this is a good
adoption candidate.

The djdizzyd driver is also already the base that Hubitat Inc. incorporated into their built-in
Honeywell T6 Pro driver. Mads can take the community version, apply the fixes, and ship a clean fork.
No upstream dependency. The Z-Wave command classes and fingerprint are correct and up-to-date for the
T6 Pro's Z-Wave Plus profile.

---

## 6. Fork Scope

**What to keep:**
- All Z-Wave event handlers (`zwaveEvent(*)`) — the Z-Wave command class mappings are correct
- `@Field static Map CMD_CLASS_VERS`, `THERMOSTAT_*` lookup maps — accurate and well-structured
- `configParams` map (all 42 params) — comprehensive, correct for T6 Pro
- `secureCommand()` / `sendToDevice()` / `commands()` Z-Wave send infrastructure — `zwaveSecureEncap`
  is the correct modern pattern
- `syncClock()` — clock sync to thermostat is a useful feature not in the generic driver
- `SensorCal` and `IdleBrightness` custom commands — these expose T6 Pro-specific config that the
  generic driver omits; keep them

**What to fix/add:**
1. Add `input "txtEnable", "bool", title: "Enable text logging", defaultValue: true` to preferences.
2. Fix `device.currentValue=="cooling"` → `device.currentValue("thermostatOperatingState")=="cooling"` (two locations).
3. Add `unschedule("syncClock")` at top of `configure()`.
4. Remove the `configurationGet(parameterNumber: 52)` dead-code call.
5. Add `void initialize() { configure() }`.
6. Update `supportedThermostatModes`/`supportedThermostatFanModes` to emit JSON array.
7. Upgrade `eventProcess()` numeric comparison for temperature attributes.
8. Guard `log.info "Notification:"` with `if (logEnable)`.
9. Add `@Field static final String VERSION` + `packageManifest.json`.

**Upstairs thermostat note:** If Upstairs currently runs the Hubitat built-in generic driver and Mads
wants feature parity (SensorCal, IdleBrightness, syncClock), switch both to the forked driver. If
upstairs is already working and the built-in generic is sufficient, no rush — but the Downstairs unit
should move to the fork to eliminate the BLOCKER/MAJOR bugs.

**Suggested home:** `drivers/honeywell-t6-pro/honeywell-t6-pro.groovy`
**Suggested version:** Start at `2.0.0` to distinguish from djdizzyd's 1.2 lineage.

---

## 7. Maintainer Responsiveness

**UNRESPONSIVE (4+ years).** No basis for PR. Fork is the correct path.

---

# Trinity Post-Fork Code Review
**Reviewed by:** Trinity (Lead / Architect)
**Date:** 2026-05-18T17:30:00-07:00
**Commits under review:** Honeywell `1dc51af` · Fully Kiosk `32a9f2c` · PurpleAir `ff3410f`

---

## 🟢 Headline Verdict: GOOD-TO-SHIP

**No regressions found across all three drivers.** Tank's minimum-change discipline held. Every fix from the upstream audit was applied correctly. Pre-existing upstream issues survive (as intended) and are catalogued in the Deferred Backlog below.

| Driver | Tank's Fixes | Regressions | Verdict |
|---|---|---|---|
| Honeywell T6 Pro | ✅ All 3 verified | None | **SHIP** |
| Fully Kiosk | ✅ All 4 verified | None | **SHIP** |
| PurpleAir AQI | ✅ All 3 verified | None | **SHIP (temp fork)** |

> **HOT-LIST: EMPTY.** No regressions. No blockers.

---

## Driver 1 — Honeywell T6 Pro (`drivers/honeywell-t6-pro/`)
### ⚠️ Production context: Mads's Downstairs thermostat is live on this driver.

### A. Tank's Fixes — Verified

**Fix #1 [BLOCKER]: `txtEnable` declared (line 56)**
```groovy
input "txtEnable", "bool", title: "Enable description text logging", defaultValue: true
```
Correct. `defaultValue: true` is intentional — the original silenced info logging permanently; exposing it by default restores useful first-install observability. The `if (txtEnable)` guards (battery, notification events) will now fire on install — that's a handful of log lines, not a flood. Sensible.

**Fix #2a [MAJOR]: `thermostatFanState` event handler (line 533)**
```groovy
if (newstate=="idle" && (device.currentValue("thermostatOperatingState")=="heating" ||
    device.currentValue("thermostatOperatingState")=="cooling")) ...
```
Attribute arg now correctly passed. The guard correctly re-queries operating state only when the fan reports idle but the thermostat is still heating/cooling. ✅

**Fix #2b [MAJOR]: `BasicSet` handler (lines 555–558)**
Both branches corrected. Worth noting: the 0xFF branch condition (`!="heating" || !="cooling"`) is trivially always-true for any single value — identical behavior to the original broken code (where method-reference `!=` string was always true). Behavior preserved. The 0x00 branch (`=="heating" || =="cooling"`) is actually a subtle improvement over the original, which had this branch dead (method reference was never equal to a string). Tank's fix makes the off-path also query operating state correctly. Not a regression — an incidental improvement.

**Fix #3 [MAJOR]: `unschedule("syncClock")` in `configure()` (line 126)**
```groovy
unschedule("syncClock")
runIn(10, "syncClock")
runIn(5, "pollDeviceData")
runEvery3Hours("syncClock")
```
Targeted. Does NOT kill `pollDeviceData` or other jobs — only the specific `syncClock` scheduler. `updated()` still carries a broad `unschedule()` (line 148), which is the appropriate place for a full teardown. No zombie risk. ✅

**packageManifest.json**: Present, UUID valid, namespace `djdizzyd` preserved (correct for HPM continuity), version `0.1.0`. ✅

### B. Carried-Over Upstream Issues (Deferred)

| # | Severity | Location | Description |
|---|---|---|---|
| C1 | MINOR | `zwaveEvent(SensorMultilevelReport)` line 464,467 | `eventProcess()` calls for `temperature` and `humidity` have no `descriptionText` key — Events tab Description column is blank for these |
| C2 | MINOR | `eventProcess()` line 300 | String comparison for numeric equality: `device.currentValue(evt.name).toString() != evt.value.toString()` — can create false-positive events for `68` vs `68.0`. Use `BigDecimal` compare. |
| C3 | NIT | `zwaveEvent(ThermostatFanStateReport)` line 531 | `sendToDevice(zwave.configurationV1.configurationGet(parameterNumber: 52))` — parameter 52 does not exist in `configParams`. The T6 Pro responds, driver ignores it. 2 unnecessary Z-Wave frames per fan-state event. |
| C4 | NIT | `initializeVars()` lines 134–135 | `sendEvent(name:"supportedThermostatModes", ...)` and `supportedThermostatFanModes` — no `descriptionText`. |
| C5 | LOW | `refresh()` line 327 | `runIn(10, "syncClock")` inside refresh() duplicates the repeating `runEvery3Hours` schedule. Extra one-shot fires harmlessly but adds marginal Z-Wave traffic after every manual refresh. |

**Thermostat-specific risk review (per brief):**
- Z-Wave `parse()` dispatcher — **untouched by Tank**. No regression risk. ✅
- Fan-state and operating-mode event paths — fixed correctly. No broken event paths. ✅
- `configure()` scheduler changes — targeted, does not interfere with `pollDeviceData`. ✅

---

## Driver 2 — Fully Kiosk Browser Controller (`drivers/fully-kiosk/`)

### A. Tank's Fixes — Verified

**Fix #1 [MAJOR Security]: Password masking**

Two sites fixed:

*Preference input (line 110)*:
```groovy
input(name:"serverPassword", type:"password", ...)
```
Correct. Hubitat renders `type:"password"` as a masked field in the UI. Previously type was `"string"`, showing password in cleartext. ✅

*Log masking in `refresh()` (lines 439–441) and `sendCommandPost()` (lines 547–549)*:
```groovy
safeParams.uri = safeParams.uri?.replaceAll(/(?i)password=[^&]+/, 'password=***')
logger(logprefix + safeParams)
```
Regex correctly targets `password=<value-up-to-next-&>`. For a URL like `...&password=mySecret&cmd=screenOn`, result is `...&password=***&cmd=screenOn`. ✅

`updateDeviceData()` (line 515) also constructs a password-bearing URI but does **not** log it — only `asynchttpGet` is called. No password exposure there. ✅

**Fix #2 [MAJOR Event Hygiene]: `emitIfChanged` helper (lines 567–583)**
```groovy
private void emitIfChanged(String name, value, String descTxt, String unit = null) {
    def current = device.currentValue(name)
    boolean changed
    if (current instanceof Number || value instanceof Number) {
        try { changed = (current as BigDecimal) != (value as BigDecimal) }
        catch (Exception e) { changed = current?.toString() != value?.toString() }
    } else {
        changed = current?.toString() != value?.toString()
    }
    if (!changed) return
    Map evt = [name: name, value: value, descriptionText: descTxt]
    if (unit) evt.unit = unit
    sendEvent(evt)
}
```
Implementation matches the repo's canonical pattern from the `hubitat-event-hygiene` skill exactly — numeric BigDecimal compare with string fallback. ✅

Applied to all 4 `refreshCallback` attributes (lines 451–459): `battery`, `switch`, `level`, `currentPageUrl`. The prior 1,440 redundant events/day at 1-minute polling cadence is eliminated. ✅

**Fix #3 [MAJOR]: `descriptionText` in `parse()` and event helpers (lines 206–251)**

All parse-path `sendEvent` calls now have `descriptionText`:
- `switch`, `battery`, `volume` inline in the switch block ✅
- `motion()` helper (line 234) ✅
- `acceleration()` helper (line 246) ✅

**Fix #4 [MAJOR]: Logger replacement (lines 589–595)**
```groovy
private void logger(loggingText, String loggingType = "debug") {
    switch (loggingType.toLowerCase()) {
        case "error": log.error loggingText; break
        case "warn":  log.warn  loggingText; break
        case "info":  log.info  loggingText; break
        default:      if (logEnable) log.debug loggingText; break
    }
}
```
The original inverted-level logger (where "debug" was less verbose than "trace") is replaced. All `trace`/`debug` calls now gate on `logEnable`. `info`/`warn`/`error` always emit. ✅

**Note**: `logEnable` defaults to `true` (line 125). All trace/debug calls will fire until a user manually disables it. No auto-disable (`logsOff`) is scheduled. This is noisy but not a regression — the original was equally verbose. See Deferred list.

**packageManifest.json**: Present, namespace changed to `mads` matching driver header. UUID is a recognizable placeholder (`a1b2c3d4-e5f6-7890...`) rather than a random UUID — acceptable for a fork with no HPM ambitions yet, but see Deferred. ✅

### B. Carried-Over Upstream Issues (Deferred)

| # | Severity | Location | Description |
|---|---|---|---|
| C1 | MINOR | `parse()` default case line 225, `sendCommandCallback()` line 557, `updateDeviceDataCallback()` line 530 | `sendEvent([name:"checkInterval", value:60])` — no `descriptionText`. Appears in Events tab as blank Description. |
| C2 | MINOR | `initialize()` / `updated()` | No `runIn(1800, logsOff)` auto-disable for debug logging. `logEnable:true` default means the driver logs verbose trace forever until user manually disables. Standard pattern: default to `false`, auto-disable after 30 min. |
| C3 | LOW | `updateDeviceData()` line 516 | Password in HTTP URI as query param (`?password=${serverPassword}`). Not logged, but cleartext over LAN. This is the Fully Kiosk protocol design, not a driver bug — nothing to fix without changing the FKB API. Document in README. |
| C4 | LOW | `checkInterval` events | Driver uses `sendEvent([name:"checkInterval", value:60])` for HealthCheck. `60` seconds is aggressive — a single missed poll at 1-min cadence marks the device offline. Value should be at least 2x the polling interval. Upstream behavior, but worth revisiting. |
| C5 | NIT | `setLevel()` line 272 | `setLevel` sends a `sendEvent` for `level` directly, then calls `setScreenBrightness`. The event fires optimistically before the command succeeds — if the command fails, the event lies. Low practical impact; carried-over pattern. |

---

## Driver 3 — PurpleAir AQI Virtual Sensor (`drivers/purpleair-aqi/`)
### Note: Temporary fork — delete after upstream PR merged.

### A. Tank's Fixes — Verified

**Fix #1 [BLOCKER]: AQ&U string in `apply_conversion()` (line 550)**
```groovy
} else if ( conversion == "AQ&U" ) {
```
Was `"AQ and U"`. Preference value is `"AQ&U"` (line 53). The string comparison now matches. The `AQandU_conversion()` function was unreachable dead code before this fix. ✅

**Fix #2 [BLOCKER]: LRAPA/Woodsmoke case + pm2.5_cf_1 field in `sensorCheck()` (lines 142–143)**
```groovy
if (conversion == "LRAPA" || conversion == "Woodsmoke" || conversion == "CF=1") {
    pm25_count="pm2.5_cf_1"
```
Was `"lrapa"` and `"woodsmoke"` (lowercase). Preference values are `"LRAPA"` and `"Woodsmoke"` (line 53). Both branches now match. The `pm2.5_cf_1` field (required by both formulas' derivation papers) is now correctly requested from the API. ✅

Cross-check: `apply_conversion()` (line 547–552) also uses `"Woodsmoke"` and `"LRAPA"` — consistent with both fixes. ✅

Verify AQ&U uses `pm2.5` (atmospheric), not `pm2.5_cf_1`: correct. AQ&U formula was calibrated against the atmospheric reading. The fix correctly leaves AQ&U in the `else` branch at line 145. ✅

**Fix #3 [BLOCKER]: `failCount` operator precedence in `httpResponse()` (line 224)**
```groovy
state.failCount = (state.failCount ?: 0) + 1
```
Was `state.failCount?:0 + 1` which Groovy parses as `state.failCount ?: (0+1)` = `state.failCount ?: 1`. The result was that `failCount` could never increment above 1 and exponential backoff never engaged. Explicit parens now force the correct evaluation: `(failCount ?: 0) + 1`. ✅

**Code structure note**: The block comment structure around `httpResponse()` lines 206–247 is intact. The fix on line 224 is live code (the outer `/*...*/` block closes at line 211 — the `/*** Test backoff on error ***/` marker closes the earlier simple error check, leaving the new backoff logic as active code). Confirmed. ✅

**packageManifest.json**: Present. `id` field is `"purpleair-aqi-virtual-sensor"` (a slug, not a UUID). For a temporary fork this is acceptable but should be corrected if promoted to HPM. ✅

### B. Carried-Over Upstream Issues (Deferred)

| # | Severity | Location | Description |
|---|---|---|---|
| C1 | MINOR | `httpResponse()` lines 362–374 | All `sendEvent` calls (`sites`, `category`, `conversion`, `aqi`) fire unconditionally on every successful poll, even when values are unchanged. At the 1-hour default this is 4 events/hour = 96/day of potentially duplicate events. Add `emitIfChanged` or a `device.currentValue()` guard. |
| C2 | MINOR | `httpResponse()` lines 362–364 | `sites` sendEvent uses `"AQI reported from site ${sites}"` — missing `device.displayName`. Not breaking, but inconsistent with repo `hubitat-event-hygiene` skill. |
| C3 | LOW | Preferences line 52 | `update_interval` default is `"60"` (1 hr). Fine for the free tier. However, no UI warning that the `"1"` (1-min) option will generate ~43,800 API requests/month. A note in the description would help users avoid quota issues. |
| C4 | NIT | `packageManifest.json` | `id` is a slug string, not a UUID. Benign for a temporary fork; required fix if ever submitted to HPM catalog. |
| C5 | NIT | `parse()` line 76–78 | `def parse(String description) { log.debug("IQAir: Parsing...") }` — log prefix says "IQAir" (copy-paste artifact from upstream). |

---

## Deferred Improvements Backlog — Ranked by Severity

Intended as a v0.2.0 work plan after any upstream PRs are settled.

### High Priority (break first-use expectations)

| Rank | Driver | Item | Why Matters |
|---|---|---|---|
| 1 | Fully Kiosk | C2: Add `logsOff` auto-disable; flip `logEnable` default to `false` | Permanent verbose trace logging is bad citizenship; fills the Hubitat log and obscures other drivers' output |
| 2 | Honeywell | C1: Add `descriptionText` to temperature/humidity events | Events tab is blank for the two most-checked attributes on a thermostat |
| 3 | PurpleAir | C1: Add `emitIfChanged` to all `httpResponse` sendEvents | Saves 4 × 8,760 = 35,040 duplicate events/year at default cadence |

### Medium Priority (correctness / hygiene)

| Rank | Driver | Item | Why Matters |
|---|---|---|---|
| 4 | Honeywell | C2: `eventProcess` numeric equality via `BigDecimal` | `68` vs `68.0` false-positive events from Z-Wave float responses |
| 5 | Fully Kiosk | C1: Add `descriptionText` to `checkInterval` events | Consistency; repo standard requires it on every `sendEvent` |
| 6 | Fully Kiosk | C4: Revise `checkInterval` value | `60`s is too aggressive for a 1-min poll cycle; use `120` or `2 × pollingInterval` |

### Low Priority / NIT

| Rank | Driver | Item | Why Matters |
|---|---|---|---|
| 7 | Honeywell | C3: Remove `configurationGet(52)` | Dead code generating 2 wasted Z-Wave frames per fan-state event |
| 8 | PurpleAir | C5: Fix "IQAir" log prefix | Copy-paste artifact from upstream; confusing when debugging |
| 9 | PurpleAir | C4: Use UUID in `packageManifest.json` id field | Required for HPM submission if fork ever becomes permanent |
| 10 | Fully Kiosk | C3: Document LAN password-in-URI in README | Transparency; not a fixable bug but users should know it's cleartext LAN |

---

## Appendix — Hubitat Citizen Grade Summary

| Dimension | Honeywell | Fully Kiosk | PurpleAir |
|---|---|---|---|
| asynchttpGet/Post | Z-Wave (N/A) | ✅ | ✅ |
| emitIfChanged | Partial (eventProcess string check only) | ✅ (fixed) | ❌ (deferred) |
| descriptionText on all sendEvents | ❌ temperature/humidity missing | ✅ (fixed) | ✅ |
| logEnable pattern | ✅ | ⚠️ default true, no auto-off | ✅ (debugMode) |
| HealthCheck vs lastActivity | Z-Wave (N/A) | ✅ HealthCheck + ping() | ❌ no lastActivity |
| Scheduler hygiene | ✅ (fixed) | ✅ unschedule() in initialize | ✅ unschedule() in configure |
| Sandbox-safe | ✅ | ✅ | ✅ |
| packageManifest.json | ✅ | ✅ (placeholder UUID) | ⚠️ (slug id) |

---

*Reviewed against: `hubitat-event-hygiene`, `hubitat-healthcheck-vs-lastactivity`, `hubitat-sentinel-value-guards`, `hubitat-fork-cleanup-pattern`, `community-driver-audit-before-build`*

---

### 2026-05-18T17:30:00-07:00: User directive

**By:** Mads Kristensen (via Copilot)

**What:** Lift the minimum-change discipline on the 3 forked drivers
(drivers/honeywell-t6-pro/, drivers/fully-kiosk/, drivers/purpleair-aqi/).
Apply the full repo best-practices treatment to make each driver "the best
it can be" — full Hubitat citizen, high performance, low API chattiness,
all carried-over upstream issues addressed. Bump each to v0.2.0.

**Why:** User request — after Trinity's post-fork review surfaces the
carried-over upstream issues that Tank deliberately deferred under the
v0.1.0 minimum-change discipline, those issues should be picked up and
fixed rather than left as a deferred backlog.

**Scope:** This directive applies to THESE three forks specifically (the
fork-as-staging-ground pattern). The minimum-change discipline still
applies in general to other fork situations where preserving the upstream
PR shape matters (e.g., if PurpleAir's v0.1.0 PR to pfmiller0 is still
pending, the PR-bound diff should stay minimal — but our local v0.2.0
on top of the v0.1.0 PR can include the broader improvements).

**Workflow:**
1. Trinity-5 finishes the post-fork review (in flight) — produces the
   carried-over-issues backlog per driver.
2. Three Tanks then apply Trinity's v0.2.0 improvement list to each driver.
3. Switch validates on hardware before merge.

---

### 2026-05-18T17:31:03-07:00: User directive

**By:** Mads Kristensen (via Copilot)

**What:** Quality and awesomeness of the forked drivers is FIRST priority.
Sending PRs back upstream is SECOND or THIRD priority — not at all as important.

**Why:** User explicit preference. This supersedes the constraint from the
earlier 2026-05-18T17:30 directive about keeping the PurpleAir fork
diff-minimal for upstream PR-friendliness. Updated priority:
1. Make each fork the best driver it can be (full Hubitat citizen,
   high performance, low API chattiness, all carried-over upstream
   issues addressed).
2. (Optional) Cherry-pick a minimal subset of the changes into an
   upstream PR if it remains feasible after the v0.2.0 polish is done.

**Scope:** Applies to ALL three forks equally — Honeywell T6 Pro,
Fully Kiosk, and PurpleAir AQI. Drop any "stay PR-friendly" constraint
that was previously applied to PurpleAir.

**Practical effect on Tank v0.2.0 work (when Trinity-5 lands):**
- All three Tanks get the SAME instruction: apply Trinity's full
  carried-over-issues backlog without restraint.
- PurpleAir is not special — polish it like the others.
- If after v0.2.0 there's a small cherry-pickable subset that could
  go upstream, the team can extract that as a separate PR later.
  But that's a follow-up decision, not a v0.2.0 constraint.

---

### 2026-05-18T17:36:00-07:00: User directive

**By:** Mads Kristensen (via Copilot)

**What:** Forked drivers must use Mads's namespace (`madskristensen`) in
packageManifest.json and identify as his drivers in the file header. The
original community author should be CREDITED in the attribution block but
is no longer the namespace owner — these are Mads's drivers now. Code style
and architectural conventions must align with the four existing drivers in
this repo (Daikin, Gemstone, SunStat, Touchstone).

**Why:** User explicit preference. These forks aren't temporary stagings;
they are first-class drivers in this repo with Mads's quality standards
applied throughout.

**Practical effect on Tank v0.2.0 work:**
- `packageManifest.json`: namespace + author switch to `madskristensen`,
  matching existing repo drivers (verify the actual existing value via grep
  before writing).
- Driver `.groovy` file header: fork-by + version + changelog become Mads's;
  the original author's MIT copyright block is PRESERVED VERBATIM below the
  fork header (clean-room pattern from `.squad/skills/clean-room` and the
  Daikin precedent), but they no longer "own" the driver.
- Code style: align to the four existing drivers' conventions — same logger
  helper shape, same lifecycle hook style, same packageManifest field set
  including `documentationLink` and `releaseNotes`, same README structure
  with "Attribution" section near the bottom.

**Combined with prior directives (in priority order):**
1. Quality / awesomeness first (2026-05-18T17:31)
2. Use Mads's namespace + repo conventions (this directive, 2026-05-18T17:36)
3. Apply Trinity's deferred-improvement backlog (2026-05-18T17:30)
4. (Optional, distant) Cherry-pick a minimal subset for upstream PR if
   feasible after polish — but never compromise quality for PR-friendliness.

**Scope:** All three forks — Honeywell, Fully Kiosk, PurpleAir.

---

## 2026-05-18 — Honeywell T6 Pro Z-Wave Feature-Gap Survey
*[Source: .squad/decisions/inbox/cypher-honeywell-t6-zwave-survey.md]*

# Cypher — Honeywell T6 Pro Z-Wave Feature-Gap Survey
**Prepared by:** Cypher (Integration / Protocol Engineer)  
**Date:** 2026-05-18T17:51:43-07:00  
**Target:** v0.3.0 planning for `drivers/honeywell-t6-pro/honeywell-t6-pro.groovy`  
**Current driver:** v0.2.0, commit ac5b939  
**Requestor:** Mads Kristensen

---

## ⚡ Top-3 v0.3.0 Picks (don't bury the lede)

| Rank | Feature | Effort | Why |
|------|---------|--------|-----|
| **#1** | Emit `thermostatFanState` attribute | ~10 lines | CC already parsed, value never surfaced. Gives 2-stage HVAC visibility for RM rules. Zero new Z-Wave traffic. |
| **#2** | Handle battery-low notification events | ~8 lines | Events 10/11 of Power Management notification (type 8) silently `break`. Replace-battery warnings are swallowed. Safety gap, 0 new polling. |
| **#3** | Fix `CMD_CLASS_VERS` octal bug (`043` → `0x43`) | 1 char | Thermostat Setpoint CC is registered at version for decimal-35 (0x23 = Scene Controller Config) not 0x43. Hubitat parses Setpoint Reports at v1 default. Low risk in practice but wrong. |

---

## 1. Model Identity + Z-Wave Cert Reference

| Field | Value |
|-------|-------|
| **Commercial name** | Honeywell T6 Pro Z-Wave Programmable Thermostat |
| **Model number** | TH6320ZW2003 (US/Canada/Mexico) |
| **Fingerprint confirmed in driver** | `mfr:"0039", prod:"0011", deviceId:"0008"` — matches Z-Wave Alliance DB exactly |
| **Z-Wave Alliance product ID** | 2893 |
| **Z-Wave Alliance product page URL** | https://products.z-wavealliance.org/products/2893/ *(direct access: 404 — site down; confirmed via OpenZWave XML metadata that embeds this URL)* |
| **Z-Wave cert** | Z-Wave Plus certified |
| **Power** | 3×AA battery **or** 24VAC (C-wire); driver detects both via PowerSource capability |
| **Later SKU** | TH6320ZW2007 — adds SmartStart (S2), Indicator CC v3, Multi Channel Association v3, and params 43–45 |

**Source quality note:** The Z-Wave Alliance portal (`products.z-wavealliance.org`) was inaccessible at retrieval time (404). All CC data is sourced from:  
1. Driver fingerprint `inClusters` (primary, directly from device)  
2. OpenZWave XML file in domoticz/domoticz repo (identical to OZW upstream, revision 4, last updated 2020-09-09) — cites Z-Wave Alliance product 2893  
3. OpenHAB ZWave binding DB doc for TH6320ZW2007 (newer SKU; overlapping CC list cross-validated)  

Official Honeywell/Resideo installer guide URL found: `https://Products.Z-WaveAlliance.org/ProductManual/File?folder=&filename=Manuals/2893/33-00414-01-min.pdf` — also inaccessible (mirrors Z-Wave Alliance domain). A newer guide URL is available at OpenSmarthouse: `https://opensmarthouse.org/zwavedatabase/1527/reference/33-00587EFS-07_-_T6_PRO_Z-Wave_Thermostat.pdf` (covers both TH6320ZW2003 and TH6320ZW2007; retrieval not attempted as PDF).

---

## 2. Full Command Class List

Hex codes from driver fingerprint `inClusters` string. Version from `CMD_CLASS_VERS` map in driver where present; otherwise from OpenZWave/OpenHAB cross-reference.

| CC Hex | CC Name | Version | Used in Driver? | Gap / Note |
|--------|---------|---------|-----------------|------------|
| 0x5E | Z-Wave Plus Info | v2 | ✅ Registered | Lifeline advertising. Hub uses. No driver action needed. |
| 0x85 | Association | v2 | ✅ Registered + handler | Group 1 set to hub node in `setDefaultAssociation()`. Correct. |
| 0x86 | Version | v2 | ✅ Registered + handler | `VersionReport` → `firmwareVersion`/`protocolVersion` state data. |
| 0x59 | Association Group Info | v1 | ✅ Registered | No explicit handler; hub reads group names. Acceptable. |
| 0x31 | Sensor Multilevel | v5 | ✅ Registered v5, handler present | Types 1 (air temp) and 5 (humidity) decoded. See Gap §3 for outdoor temp type. |
| 0x80 | Battery | v1 | ✅ Registered + handler | `BatteryReport` → `battery` attribute. Low-battery (0xFF) → 1%. |
| 0x81 | Clock | v1 | ✅ Registered + `syncClock()` | `clockSet` every 3 hours. Already sound. |
| 0x70 | Configuration | v1 | ✅ Registered + full configParams map | All 42 params exposed in preferences. **See §3 for param detail.** |
| 0x5A | Device Reset Locally | v1 | ✅ Registered | Hub auto-handles reset notification. No driver action needed. |
| 0x72 | Manufacturer Specific | v1 (driver) / v2 (cert) | ✅ v2 handler | `DeviceSpecificReport` reads serial number. Registered at v1 but v2 handler used — minor mismatch, backward-compatible. |
| 0x71 | Notification (Alarm) | v3 | ✅ Registered v3, partial handler | **GAP:** Only notification type 8 (Power Management) handled. Types 9 (System), 11 (Clock), etc. fall through to no-op. See §4 item D. |
| 0x73 | Powerlevel | v1 | ✅ Registered | No explicit handler; framework handles automatically for Z-Wave health tests. |
| 0x9F | Security 2 (S2) | v1 | N/A — hub layer | Transport/encryption handled by Hubitat hub. Zero driver code needed. |
| 0x44 | Thermostat Fan Mode | v3 | ✅ Registered v3 + handler | Fan mode get/set/report for auto/on/circulate. Correct. |
| 0x45 | Thermostat Fan State | v1 | ✅ Registered v1 + **partial** handler | **GAP #1 (TOP-3):** Handler receives state but only uses it to conditionally re-query operating state. `newstate` is computed from `THERMOSTAT_FAN_STATE` map (8 values including "running high", "running medium") but **never emitted as an attribute**. |
| 0x40 | Thermostat Mode | v2 | ✅ Registered v2 + handler | Mode get/set/report for off/heat/cool/auto/emergency heat. Correct. |
| 0x42 | Thermostat Operating State | v1 | ✅ Registered v1 + handler | State report → `thermostatOperatingState` attribute. 7 states mapped. |
| 0x43 | Thermostat Setpoint | v2 (implied) | ⚠️ **BUG:** `CMD_CLASS_VERS` entry is `043:2` not `0x43:2` | **GAP #3 (TOP-3):** In Groovy, `043` is **octal** 43 = decimal 35 = 0x23 (Scene Controller Config). `0x43` (Thermostat Setpoint) has **no version registered**. Hub parses Setpoint Reports at v1 default. Commands use `thermostatSetpointV2` correctly; reports parse fine at v1 (backward-compatible), but the map entry is wrong. Fix: change `043:2` → `0x43:2`. |
| 0x6C | Supervision | v1 | ✅ Handler present | `SupervisionGet` → sends `SupervisionReport` with status 0xFF. Correct Z-Wave Plus behavior. |
| 0x55 | Transport Service | v2 | N/A — hub layer | Fragmented Z-Wave frames. Hub handles. Zero driver code needed. |
| 0x7A | Firmware Update MD | v2 | ✅ Registered | Not in fingerprint `inClusters` but registered. OTA update support framework. Hub handles. |
| 0x2B | Scene Activation | v1 | ✅ Registered | Not in fingerprint; likely leftover from template. No handler. Harmless. |
| 0x2C | Scene Actuator Config | v1 | ✅ Registered | Same — template artifact. No handler. Harmless. |
| 0x8F | Multi Cmd | v1 | ✅ Registered + handler | `MultiCmdEncap` handler dispatches inner commands. Used by hub for efficiency. |

**TH6320ZW2007-only CCs (not in TH6320ZW2003 fingerprint):**

| CC Hex | CC Name | Version | Notes |
|--------|---------|---------|-------|
| 0x87 | Indicator | v3 | Possibly used for keypad lock status. **Not relevant for ZW2003.** |
| 0x8E | Multi Channel Association | v3 | Supersedes Association v2 for multi-endpoint. ZW2003 single-endpoint device. **Not needed.** |

---

## 3. Full Configuration Parameter List

Source: OpenZWave XML (domoticz/domoticz, revision 4, cites ZWA product 2893) + OpenHAB ZWave DB (TH6320ZW2007). All 42 params confirmed for TH6320ZW2003; params 43–45 are TH6320ZW2007-only (not confirmed for ZW2003).

| Param | Name | Size | Default | Range | Exposed in Driver? | Notes |
|-------|------|------|---------|-------|--------------------|-------|
| 1 | Schedule Type | 1 | 2 (5-2) | 0–4 | ✅ `configParam1` | 0=No schedule, 1=Same every day, 2=5-2, 3=5-1-1, 4=Every day unique |
| 2 | Temperature Scale | 1 | 0 (°F) | 0–1 | ✅ `configParam2` | 0=Fahrenheit, 1=Celsius |
| 3 | Outdoor Temperature Sensor | 1 | 0 (No) | 0–1 | ✅ `configParam3` | 0=Disabled, 1=Wired. **Wired sensor only displayed on-screen; NOT reported to Z-Wave controller.** |
| 4 | Equipment Type | 1 | 2 (Hi-Eff Gas) | 0–9 | ✅ `configParam4` | Gas, Oil, Electric, Fan Coil, HP (A2A, Geo), Hot Water, Steam |
| 5 | Reversing Valve | 1 | 0 | 0–1 | ✅ `configParam5` | 0=O/B on Cool, 1=O/B on Heat |
| 6 | Cool Stages | 1 | 1 | 0–2 | ✅ `configParam6` | Number of cooling stages |
| 7 | Heat Stages (Aux/Emergency) | 1 | 1 | 0–2 | ✅ `configParam7` | |
| 8 | Aux/Emergency Control | 1 | 0 | 0–1 | ✅ `configParam8` | Both vs Either |
| 9 | Aux Heat Type | 1 | 0 | 0–1 | ✅ `configParam9` | Electric vs Gas/Oil |
| 10 | Emergency Heat Type | 1 | 0 | 0–1 | ✅ `configParam10` | Electric vs Gas/Oil |
| 11 | Fossil Kit Control | 1 | 0 | 0–1 | ✅ `configParam11` | Thermostat vs External |
| 12 | Auto Changeover | 1 | 0 (Off) | 0–1 | ✅ `configParam12` | ⚠️ OZW notes: "ADVANCED — HVAC professional only" |
| 13 | Auto Differential | 1 | 0 | 0–5 °F | ✅ `configParam13` | Min °F to switch heat↔cool in auto mode; not a deadband |
| 14 | High Cool Stage Finish | 1 | 0 (No) | 0–1 | ✅ `configParam14` | Finish 2nd stage before switching to 1st |
| 15 | High Heat Stage Finish | 1 | 0 (No) | 0–1 | ✅ `configParam15` | |
| 16 | Aux Heat Droop | 1 | 0 (Comfort) | 0,2–15 °F | ✅ `configParam16` | °F below setpoint before aux kicks in. Value 1 not accepted. |
| 17 | Up Stage Timer Aux Heat | 1 | 0 (Off) | 0–15 | ✅ `configParam17` | Time before staging up to aux: 30 min – 16 hr |
| 18 | Balance Point (Compressor Lockout) | 1 | 65 °F | 0,5–65 in 5° steps | ✅ `configParam18` | Outdoor temp below which compressor locked out |
| 19 | Aux Heat Outdoor Lockout | 1 | 0 (Off) | 0,5–65 in 5° steps | ✅ `configParam19` | Outdoor temp above which aux heat locked out |
| 20 | Cool Stage 1 Cycle Rate (CPH) | 1 | 3 | 1–6 | ✅ `configParam20` | Cycles per hour |
| 21 | Cool Stage 2 Cycle Rate (CPH) | 1 | 3 | 1–6 | ✅ `configParam21` | |
| 22 | Heat Stage 1 Cycle Rate (CPH) | 1 | 3 | 1–12 | ✅ `configParam22` | |
| 23 | Heat Stage 2 Cycle Rate (CPH) | 1 | 3 | 1–12 | ✅ `configParam23` | |
| 24 | Aux Heat Cycle Rate (CPH) | 1 | 9 | 1–12 | ✅ `configParam24` | |
| 25 | Emergency Heat Cycle Rate (CPH) | 1 | 9 | 1–12 | ✅ `configParam25` | |
| 26 | Compressor Protection | 1 | 5 min | 0–5 min | ✅ `configParam26` | Min off time before compressor restarts |
| 27 | Adaptive Intelligent Recovery | 1 | 1 (On) | 0–1 | ✅ `configParam27` | Pre-conditions system to hit setpoint at scheduled time |
| 28 | Minimum Cool Temperature | 1 | 50 °F | 50–99 °F | ✅ `configParam28` | Setpoint floor for cooling |
| 29 | Maximum Heat Temperature | 1 | 90 °F | 40–90 °F | ✅ `configParam29` | Setpoint ceiling for heating |
| 30 | Number of Air Filters | 1 | 0 | 0–2 | ✅ `configParam30` | Must be ≥1 to enable params 31/32 |
| 31 | Air Filter 1 Reminder | 1 | 0 (Off) | 0–19 | ✅ `configParam31` | Run-time days or calendar-based intervals |
| 32 | Air Filter 2 Reminder | 1 | 0 (Off) | 0–19 | ✅ `configParam32` | |
| 33 | Humidification Pad Reminder | 1 | 0 (Off) | 0–2 | ✅ `configParam33` | 6 or 12 months |
| 34 | Dehumidification Filter Reminder | 1 | 0 (Off) | 0–12 months | ✅ `configParam34` | |
| 35 | Ventilation Filter Reminder | 1 | 0 (Off) | 0,3,6,9,12 months | ✅ `configParam35` | |
| 36 | Number of UV Devices | 1 | 0 | 0–2 | ✅ `configParam36` | Must be ≥1 to enable params 37/38 |
| 37 | UV Bulb 1 Reminder | 1 | 0 (Off) | 0,6,12,24 months | ✅ `configParam37` | |
| 38 | UV Bulb 2 Reminder | 1 | 0 (Off) | 0,6,12,24 months | ✅ `configParam38` | |
| 39 | Idle Brightness | 1 | 0 | 0–5 | ✅ `configParam39` + `IdleBrightness` command | Also exposed as standalone command. Level 0 = off, 5 = brightest. |
| 40 | Clock Format | 1 | 0 (12 hr) | 0–1 | ✅ `configParam40` | 0=12-hour, 1=24-hour |
| 41 | Daylight Saving | 1 | 1 (On) | 0–1 | ✅ `configParam41` | Auto DST adjustment |
| 42 | Temperature Offset (Sensor Cal) | 1 | 0 | −3 to +3 °F | ✅ `configParam42` + `SensorCal` command | Also exposed as standalone command. Driver emits `currentSensorCal` attribute. |
| **43** | **Humidity Offset** | **1** | **0** | **−12 to +12** | **❌ NOT in driver** | **TH6320ZW2007 only** (not confirmed for ZW2003). Calibrate humidity sensor. |
| **44** | **Temperature Reporting Resolution** | **1** | **1 (1°F)** | **0–5** | **❌ NOT in driver** | **TH6320ZW2007 only.** Deadband before temp is reported: 0.5°F, 1°F, 2°F, 3°F, 4°F, 5°F. **DIRECTLY affects hub API chattiness.** |
| **45** | **Humidity Reporting Resolution** | **1** | **1** | **1–5 %** | **❌ NOT in driver** | **TH6320ZW2007 only.** Min % change before humidity reported to controller. |

---

## 4. Specifically-Checked Feature Checklist

| Feature | Status | Notes |
|---------|--------|-------|
| **Vacation hold / temporary hold** | PARTIAL | Setpoint-based hold works correctly (setHeatingSetpoint/setCoolingSetpoint creates a hold). No `thermostatHold` attribute to observe hold state from Hubitat. The device returns to schedule on next scheduled event; driver has no way to report this transition. Inferred-only hold attribute is possible but risks false data. |
| **Equipment status / staged HVAC** | PARTIAL — **ADD** | `ThermostatFanStateReport` decoded to 8 states including "running high" (stage 2 active) and "running medium". Handler computes `newstate` then discards it. **The single largest Z-Wave data gap in the driver.** |
| **Outdoor temperature sensor** | NOT REPORTED via Z-Wave | Param 3 enables a wired outdoor sensor, which the T6 Pro displays on-screen. **The outdoor temperature is NOT sent to the Z-Wave controller.** SensorMultilevel types polled by driver are type 1 (air temp) and type 5 (humidity); no additional sensor type for outdoor temp. Source: driver code + community confirmation. Not an official Resideo statement — flagged as community knowledge. |
| **Filter change reminder / runtime hours** | NOT IMPLEMENTED | Params 30–35 configure reminder intervals. The T6 Pro tracks internally. Whether the device emits a Notification CC report when reminder triggers is **uncertain** — Z-Wave Notification CC has no standard "filter change" event type, and the official manual URL is inaccessible. Notification type 9 (System) handler is missing from driver, but it is unclear if T6 Pro actually sends one. Reminder is likely display-only. **Flag: source quality low.** |
| **UV lamp control** | NOT APPLICABLE | Params 36–38 are reminder schedules for UV germicidal lamps. No Z-Wave CC exists to control UV lamp on/off. These are already exposed in driver preferences. |
| **Schedule programming via Z-Wave** | NOT SUPPORTED by device | `SCHEDULE` CC (0x53) and `THERMOSTAT_SCHEDULE` CC (0x5A — not the same as Device Reset Locally) are absent from the device's CC list. Param 1 configures schedule type but schedules are programmed locally on the thermostat keypad only. `setSchedule()` stub correctly warns "not supported." |
| **Keypad lock / child lock** | NOT SUPPORTED on ZW2003 | `INDICATOR` CC (0x87) is absent from TH6320ZW2003 fingerprint. TH6320ZW2007 adds Indicator v3 which may control/report keypad lock. No configuration parameter in params 1–42 covers keypad lock either — it may only be accessible via the physical installer menu. |
| **Temperature display units (F/C)** | ✅ ALREADY EXPOSED | Param 2 (`configParam2`) in preferences. |
| **Heat/cool changeover behavior (auto)** | ✅ ALREADY EXPOSED | Param 12 (`configParam12` Auto Changeover) + Param 13 (`configParam13` Auto Differential) in preferences. |
| **Backlight behavior** | ✅ ALREADY EXPOSED | Param 39 exposed as `configParam39` preference **and** as `IdleBrightness` command with `idleBrightness` attribute. |
| **Minimum on/off time (compressor protection)** | ✅ ALREADY EXPOSED | Param 26 (`configParam26`) in preferences. |
| **System type detection** | ✅ ALREADY EXPOSED | Params 4 (Equipment Type), 6 (Cool Stages), 7 (Heat Stages), 8 (Aux/E Control) all in preferences. |
| **Battery vs C-wire detection** | ✅ ALREADY IMPLEMENTED | `BatteryV1.BatteryReport` → `battery` attribute. `NotificationReport` events 2/3 of type 8 → `powerSource` attribute (mains/battery). **PARTIAL GAP:** Notification events 10/11 ("replace battery soon" / "replace battery now") silently `break` — no warning emitted. **See ADD #2.** |
| **Z-Wave Plus — Supervision** | ✅ IMPLEMENTED | `zwaveEvent(SupervisionGet)` handler present, sends `SupervisionReport(status: 0xFF)`. Correct. |
| **Z-Wave Plus — Multilevel Association** | N/A for ZW2003 | Not in ZW2003 CC list. ZW2007 adds `MULTI_CHANNEL_ASSOCIATION_V3`. Single-endpoint device; Association v2 group 1 is sufficient. |
| **Z-Wave Plus — Lifeline reporting** | ✅ IMPLEMENTED | `setDefaultAssociation()` sets group 1 to hub node ID. |

---

## 5. Findings Ranked: ADD / PROBABLY-ADD / MAYBE / SKIP

### ADD

**A. Emit `thermostatFanState` attribute**  
The THERMOSTAT_FAN_STATE map is already defined (`THERMOSTAT_FAN_STATE=[0x00:"idle", 0x01:"running", 0x02:"running high", 0x03:"running medium", 0x04:"circulation mode", ...]`). The `zwaveEvent(ThermostatFanStateReport)` handler reads it but throws the value away after using it for a conditional re-query. This is not a design choice — it's an omission.  

For Mads specifically: both T6 Pros control 2-stage systems (based on param 6 defaults). "running high" = stage 2 compressor active. This data is available **for free** (pushed by device on state change) and requires no polling.

**B. Handle battery-low notification events**  
`NotificationReport` type 8 (Power Management), events 10 ("replace battery soon") and 11 ("replace battery now") currently just `break` with no action. Given Mads has a battery-powered Upstairs unit, silently dropping these is a safety omission. The device will flash on-screen; the hub will not notify.

**C. Fix `CMD_CLASS_VERS` octal bug**  
Change `043:2` to `0x43:2` in the `CMD_CLASS_VERS` map. The driver currently registers version for 0x23 (Scene Controller Config) instead of 0x43 (Thermostat Setpoint). In practice this means Setpoint Reports are parsed at v1 default — which works because v2 is backward-compatible for heating/cooling setpoints. However the bug hides a potential parsing failure for "AutoChangeOver" setpoint type (setpoint type 0x0A) if the device ever reports it.

### PROBABLY-ADD

**D. Add params 43–45 for TH6320ZW2007 users**  
Params 43 (Humidity Offset), 44 (Temperature Reporting Resolution), 45 (Humidity Reporting Resolution) exist on TH6320ZW2007. The TH6320ZW2003 documentation (OZW XML, params 1–42 only) does NOT list these. If Mads ever upgrades Upstairs to a ZW2007, or if the ZW2003 firmware silently accepts them, these are worth having.  

Param 44 specifically (Temperature Reporting Resolution) is relevant to Mads's "low API chattiness" directive: a value of 2 (2°F deadband) would halve temperature report frequency. Default is 1°F. Until confirmed working on ZW2003, defer.

**E. Add Notification type 9 (System) handler stub**  
The driver handles only type 8. Adding a type 9 handler with `log.warn` for any `event > 0` would surface firmware alerts (hardware/software failures). Low effort (~15 lines). Source quality for filter-reminder alerts via this channel is uncertain.

### MAYBE

**F. `thermostatHold` attribute (inferred)**  
Track driver-initiated setpoint changes with `state.holdActive = true` and clear on `setThermostatMode()`. Emit a `thermostatHold` attribute. This is driver-inferred (optimistic), not device-reported, so it will drift if setpoints are changed physically at the thermostat. Useful for RM rules but not trustworthy across long periods. No Hubitat standard capability for hold; custom attribute needed.

**G. Outdoor temperature readout**  
If the T6 Pro ever sends a SensorMultilevel report for the outdoor sensor (type 2 or type 56 per Z-Wave spec), the driver's `SensorMultilevelReport` handler would need to catch it. Current behavior: unrecognized sensor types fall through to no-op. It is almost certain the ZW2003 does NOT report outdoor temp over Z-Wave — but adding a catch for sensor type 2 costs 5 lines and costs nothing if the device never sends it.

### SKIP

| Feature | Reason |
|---------|--------|
| Z-Wave Schedule CC | Not in device CC list. Device doesn't support remote schedule programming via Z-Wave. |
| Keypad lock (ZW2003) | No Indicator CC on ZW2003. Physical-menu only. |
| UV lamp on/off control | No Z-Wave control interface; params 36–38 are reminder-only (already exposed). |
| Vacation hold as distinct mode | "Hold" on T6 Pro is achieved by setting a setpoint; no separate Z-Wave hold CC. setpointSet already works correctly. |
| Scene Activation / Scene Actuator Config | In CMD_CLASS_VERS but absent from device fingerprint. Likely template artifacts. No T6 Pro scenes capability. Remove on cleanup. |
| Filter runtime hours as attribute | Device does not report filter runtime hours via Z-Wave; tracked internally. No CC for reading this. |

---

## 6. Top-3 v0.3.0 Recommendation with Specs

---

### Recommendation #1 — Emit `thermostatFanState` attribute

**Why first:** This is the only genuine sensor data the device reports that the driver discards. Every other gap is either a bug, a missing param, or a device limitation. This surfaces 2-stage HVAC monitoring with **zero new Z-Wave frames** — the device already pushes ThermostatFanStateReport on every state change.

**Hubitat capability fit:** No standard Hubitat capability covers fan operating state (as opposed to fan mode). Custom attribute is appropriate. The driver already has `THERMOSTAT_FAN_STATE` values defined.

**Z-Wave CC shape:**  
- CC: `THERMOSTAT_FAN_STATE` v1 (0x45)  
- Frame: `THERMOSTAT_FAN_STATE_REPORT` → `fanOperatingState` byte  
- Values: 0x00=idle, 0x01=running, 0x02=running high, 0x03=running medium, 0x04=circulation mode, 0x05=humidity circulation, 0x06=right-left circulation, 0x07=quiet circulation  
- No new Get command needed — device pushes on change; `refresh()` can call `zwave.thermostatFanStateV1.thermostatFanStateGet()` (already does)

**UX:** Attribute `thermostatFanState` displayed on device detail page. RM rules can use it: `if thermostatFanState is "running high" notify "Stage 2 active"`.

**Implementation for Tank:**
```groovy
// In metadata definition block, add:
attribute "thermostatFanState", "enum", ["idle","running","running high","running medium",
  "circulation mode","humidity circulation mode","right - left circulation mode","quiet circulation mode"]

// In zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport cmd):
// ADD after the existing newstate / logDebug lines, BEFORE the conditional re-query:
eventProcess(name: "thermostatFanState", value: newstate,
    descriptionText: "${device.displayName} fan state is ${newstate}")
```

**Effort:** ~8 lines (1 attribute declaration + 2 lines in handler). No new state, no polling.  
**Risks:** None. Read-only attribute from device-pushed report. The 8 ThermostatFanState values are Z-Wave spec-defined, not T6 Pro proprietary.

---

### Recommendation #2 — Handle battery-low notification events

**Why second:** Mads has (or plans) a battery-powered Upstairs T6 Pro. Events 10/11 of Power Management notification silently `break` — the hub learns nothing when the thermostat wants new batteries. This is a safety gap: a dead thermostat in a PNW winter is a frozen-pipe risk.

**Z-Wave CC shape:**  
- CC: `NOTIFICATION` v3 (0x71) — already handled  
- Frame: `NOTIFICATION_REPORT`, `notificationType=8` (Power Management)  
- Event 10: "Replace battery soon"  
- Event 11: "Replace battery now"  
- These are in the existing `switch (cmd.event)` block, already there as `break` stubs

**UX:** Emit a `battery` event with a warning value (1%) on event 11 (Replace Now) and a `batteryStatus` event on event 10 (Replace Soon). Log a `log.warn` for both. RM notification rules on the `battery` attribute already in place for most users.

**Implementation for Tank:**
```groovy
// Replace the existing case 10 and case 11 break stubs:
case 10:
    evt.name = "battery"
    evt.value = "10"
    evt.descriptionText = "${device.displayName} battery is low — replace soon"
    evt.isStateChange = true
    log.warn evt.descriptionText
    break
case 11:
    evt.name = "battery"
    evt.value = "1"
    evt.descriptionText = "${device.displayName} battery is critically low — replace now"
    evt.isStateChange = true
    log.warn evt.descriptionText
    break
```

**Effort:** ~12 lines (replacing 4 empty `break` stubs).  
**Risks:** Battery value 10%/1% is a soft signal — not from `BatteryReport` but from a Notification event. It will show `10` in the battery attribute until the next actual `BatteryReport` overwrites it. Minor display inaccuracy. Alternative: emit a custom `batteryAlert` attribute to avoid stomping battery%. **Tank should choose which approach matches the repo's attribute hygiene preference.**

---

### Recommendation #3 — Fix `CMD_CLASS_VERS` octal bug (`043` → `0x43`)

**Why third:** One-character fix with correctness value. The Thermostat Setpoint CC (0x43) is registered in the version map at 0x23 (Scene Controller Config) instead of 0x43. This is because `043` in Groovy is **octal** notation. The immediate risk is low (v1 and v2 Thermostat Setpoint share the same Report format for heating/cooling setpoint types), but "AutoChangeOver" setpoint type (0x0A) added in v3 would be affected if the device uses it. The wrong map entry also causes the version-lookup table to be silently incorrect, which is the kind of quiet incorrectness Mads's quality-first standard rejects.

**Fix:**  
```groovy
// Change in CMD_CLASS_VERS map:
// FROM:
043:2
// TO:
0x43:2
```

**Effort:** 1 character change in 1 line.  
**Risks:** None. The Hubitat Z-Wave framework uses this map for parsing inbound Report frames. Changing from incorrect 0x23 to correct 0x43 ensures Setpoint Reports are explicitly parsed at v2 instead of implicitly at v1 default.

---

## 7. Sources

| # | Document | URL | Retrieval date | Quality |
|---|----------|-----|----------------|---------|
| 1 | Driver fingerprint `inClusters` (primary source for CC list) | `drivers/honeywell-t6-pro/honeywell-t6-pro.groovy`, line 59 | 2026-05-18 (local) | **PRIMARY — direct device fingerprint** |
| 2 | OpenZWave XML for TH6320ZW2003 (via domoticz mirror, revision 4) | https://raw.githubusercontent.com/domoticz/domoticz/c3673648397397930b057fa33f7630e8add4c8c2/Config/honeywell/th6320zw2003.xml | 2026-05-18 | **HIGH — cites ZWA product 2893; param list confirms 1–42** |
| 3 | OpenHAB ZWave binding DB, TH6320ZW2007 (newer SKU; same family) | https://raw.githubusercontent.com/openhab/org.openhab.binding.zwave/59419c568a0115bad7a1e7a27aa9b84db6e3d497/doc/resideo/th6320zw2007_0_0.md | 2026-05-18 | **HIGH for ZW2007 CC list + params 43–45; MEDIUM for ZW2003 exact parity** |
| 4 | djdizzyd original driver (upstream baseline) | https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/Honeywell/Advanced-Honeywell-T6-Pro.groovy | 2026-05-18 | **MEDIUM — identical configParams 1–42, confirms our fork baseline** |
| 5 | Z-Wave Alliance product page 2893 (T6 Pro) | https://products.z-wavealliance.org/products/2893/ | NOT ACCESSIBLE (404 at retrieval time) | **Referenced via OZW XML metadata; URL confirmed** |
| 6 | Official installer guide PDF (via Z-Wave Alliance) | https://Products.Z-WaveAlliance.org/ProductManual/File?folder=&filename=Manuals/2893/33-00414-01-min.pdf | NOT ACCESSIBLE (domain down at retrieval time) | Referenced in OZW XML; content not read |
| 7 | T6 Pro installer guide at OpenSmarthouse (covers ZW2003 + ZW2007) | https://opensmarthouse.org/zwavedatabase/1527/reference/33-00587EFS-07_-_T6_PRO_Z-Wave_Thermostat.pdf | NOT FETCHED (PDF) | Listed in OpenHAB DB entry; not read directly |

**⚠️ Source gaps to flag:**  
- Official Resideo/Honeywell parameter reference PDF was not accessible. Parameter table in this document sourced from OpenZWave XML + OpenHAB DB (both derived from Z-Wave Alliance data). This is standard practice but means we cannot cite page numbers.  
- Outdoor temperature "not reported via Z-Wave" claim: sourced from driver code (only types 1+5 polled) + widespread community knowledge. Not from an official Resideo spec sheet.  
- Filter-change-notification-via-Z-Wave claim ("uncertain"): Z-Wave Notification CC spec has no standardized filter-change event type; T6 Pro manual inaccessible; claim is based on spec inference only.

---

## Appendix A — v0.3.0 Full Change List (for Tank's reference)

If all three top picks plus two PROBABLY-ADD items are approved, v0.3.0 scope:

| Item | Category | Lines delta |
|------|----------|------------|
| Emit `thermostatFanState` attribute | Feature | +~10 |
| Battery-low notification events 10/11 | Safety fix | +~12 |
| Fix `CMD_CLASS_VERS` `043` → `0x43` | Correctness bug | 1-char |
| Add params 43–45 (ZW2007 users, conditional) | Feature | +~15 |
| Notification type 9 (System) stub | Enhancement | +~20 |
| **Total** | | **~55–60 lines** |

This is a compact v0.3.0. All changes are additive or single-line fixes. No existing behavior changed. No new polling added.


---

## 2026-05-18 — Honeywell T6 Pro v0.3.0 shipped

**Commit:** e38c4d3 — honeywell-t6-pro v0.3.0: fanState emit + battery-low handling + octal CC fix

**Three Cypher-survey picks applied (additive only):**
1. **Pick #1 — thermostatFanState emit** (~10 lines): Attribute declared in definition() block; ventProcess(name: "thermostatFanState", ...) added in zwaveEvent(ThermostatFanStateReport). Existing operating-state re-query logic preserved. RM rules / dashboards can now read fan state independently.
2. **Pick #2 — Battery-low notification handling** (~8 lines): Cases 10 (replace soon) and 11 (replace now) in NotificationReport type-8 switch now emit log.warn + idempotency-gated sendEvent for attery at 10%% / 1%%. Surfaces low battery before device fully dies.
3. **Pick #3 — Octal CC version fix** (1 char):  43:2 →  x43:2 in CMD_CLASS_VERS map. 5-year-old latent bug — driver had been negotiating v1 of Thermostat Setpoint CC instead of v2.

**Production-safe**: Additive only; v0.2.0 regression-free baseline preserved. Running on Mads's live Downstairs thermostat.

**Tank-6 verified** all 3 changes applied at the expected file locations; v0.3.0 version bumped in driver header, @Field static final String VERSION, packageManifest.json, and README changelog.

**Compaction context**: This shipment was the v0.3.0 work pending from the Cypher-6 Z-Wave API survey. Mads's prior directives ("quality first", "best they can be", "my namespace and standards") informed the work going additive-only with no refactor.

---

## 2026-05-18 — Honeywell T6 Pro v0.4.0 Candidates (Cypher analysis)

# Honeywell T6 Pro v0.4.0 Candidates — HA Gap Analysis

**Baseline:** v0.3.0 (commit e38c4d3) — fanState emit, battery-low handling, octal CC fix shipped  
**Hardware:** TH6320ZW2003 (mfr:0x0039 prod:0x0011 deviceId:0x0008)  
**Analyst:** Cypher  
**Date:** 2026-05-18

---

## Executive Summary

The v0.3.0 driver is in excellent shape against HA's Z-Wave JS coverage: both systems implement the same 42 config params (confirmed from [th6320zw.json](https://github.com/zwave-js/node-zwave-js/blob/master/packages/config/config/devices/0x0039/th6320zw.json)), the same CC surface, and the same sensor set. The largest structural advantage HA has is purely architectural — Z-Wave JS auto-generates individual dashboard entities for all 42 config params, which a Hubitat driver cannot replicate. Against that background, there are **three genuine polish gaps** in v0.3.0 worth shipping as v0.4.0: (1) `descriptionText` missing on three thermostat event handlers, (2) `thermostatFanState` declared as `"string"` instead of `"enum"`, and (3) no Notification type 9 (System) handler stub. All three are additive, low-risk, and consistent with the v0.2.0 quality-pass precedent. **Verdict: ship v0.4.0 — compact polish release, ~25 lines.**

---

## ⚡ Top-3 v0.4.0 Picks (ranked by ROI)

| Rank | Pick | Effort | Value | User-visible impact |
|------|------|--------|-------|---------------------|
| 1 | Add `descriptionText` to `thermostatOperatingState`, `thermostatFanMode`, `thermostatMode` events | ~6 lines | High | Correct info log lines for all thermostat state changes; matches v0.2.0 temp/humidity pattern |
| 2 | Fix `thermostatFanState` attribute type: `"string"` → `"enum"` with values list | ~6 lines | Medium | RM4 can trigger on exact enum values; Hubitat device page shows picker not freeform |
| 3 | Add Notification type 9 (System) handler stub with `log.warn` | ~15 lines | Medium | Surfaces firmware/hardware failure alerts currently silently dropped |

---

## A. HA Z-Wave JS Gap Analysis

### A1. Device File Identity

Z-Wave JS device file: [`packages/config/config/devices/0x0039/th6320zw.json`](https://github.com/zwave-js/node-zwave-js/blob/master/packages/config/config/devices/0x0039/th6320zw.json)

```json
"devices": [{ "productType": "0x0011", "productId": "0x0008" }]
```

This is an exact match for our TH6320ZW2003 fingerprint (`mfr:0039 prod:0011 deviceId:0008`). Z-Wave JS covers params 1–42 only — identical to our driver. **Params 43–45 are NOT in this device file** — they are in `honeywell_template.json` but only referenced from the ZW2007 device file, confirming they are ZW2007-only. The template path is `packages/config/config/devices/templates/honeywell_template.json` (not `templates/`, confirmed).

### A2. Z-Wave JS Compat Flags

```json
"compat": {
    "skipConfigurationNameQuery": true,
    "skipConfigurationInfoQuery": true
}
```

These tell Z-Wave JS to NOT send `CONFIGURATION_NAME_GET` / `CONFIGURATION_INFO_GET` frames to the device, because the T6 Pro "responds in a weird way" causing S2 timing collisions (comment in the JSON). Our driver never queries parameter names or info from the device — we use a static `configParams` map — so we are **already handling this quirk correctly** without needing an explicit workaround.

### A3. HA Entities for T6 Pro (what HA creates by default)

| HA Entity | Type | Our Driver Equivalent | Gap? |
|-----------|------|----------------------|------|
| `climate.t6_pro` | climate | Thermostat capability (mode + setpoints + fan mode) | ✅ None |
| `sensor.t6_pro_air_temperature` | sensor | `temperature` attribute | ✅ None |
| `sensor.t6_pro_humidity` | sensor | `humidity` attribute | ✅ None |
| `sensor.t6_pro_battery_level` | sensor | `battery` attribute | ✅ None |
| `binary_sensor.t6_pro_low_battery_level` | binary_sensor | v0.3.0 Pick #2 (battery% at 10%/1%) | ⚠️ We have no dedicated binary attribute; we reuse `battery%`. Functional but different UX. |
| `number/select.*` per config param (×42) | entities | `configParams` preferences page | ⚠️ HA architecture advantage — 42 browseable entities. Hubitat driver prefs are equivalent functionally but not individually dashboardable. **Cannot be replicated in driver.** |
| `thermostatFanState` | (part of climate) | `thermostatFanState` string attribute | ⚠️ We emit it; HA doesn't expose it as a separate entity. We're arguably better here. |

**Headline HA advantage:** Every config param becomes an individually dashboardable and automation-targetable entity in HA. Hubitat users access these only via the Preferences page. This is a Hubitat platform constraint, not a driver gap.

### A4. Temperature Calibration Entity (param 42)

The Honeywell template marks param 42 with `"$purpose": "calibration.temperature"`. In Z-Wave JS UI, this auto-creates a dedicated calibration number entity with combined °F/°C label strings (e.g., `"-1 °F / -0.5 °C"`). Our driver exposes this as:
- `configParam42` preference (same options, Fahrenheit-only labels)
- `SensorCal` command (direct set)
- `currentSensorCal` attribute (readable value)

Our driver is **equal or better** UX for Hubitat — users can trigger `SensorCal` from RM rules or dashboards. The only label gap: our options don't show the Celsius equivalent (e.g., `-1°F` vs HA's `-1 °F / -0.5 °C`). Low priority.

### A5. Fan Mode Coverage

Z-Wave JS ThermostatFanMode v3 exposes auto/on/circulate. Our driver:
- `THERMOSTAT_FAN_MODE` map: handles 0x00–0x07 correctly
- `supportedThermostatFanModes`: `["on","auto","circulate"]`
- `SET_THERMOSTAT_FAN_MODE`: `["auto":0x00,"on":0x01,"circulate":0x06]`

**No gap.** HA maps the same three modes. "Follow schedule" is HA label for circulate (0x06) in some UI — same Z-Wave value, different display label only.

### A6. Scene Controller CC (2B/2C)

Z-Wave JS does not include Scene Activation or Scene Actuator Config for the T6 Pro — they are absent from the ZW2003 device file. Our driver has these in `CMD_CLASS_VERS` as template artifacts. No behavioral impact; both systems agree the device doesn't use scenes.

---

## B. Honeywell Cloud Feature Comparison

| Cloud Feature | Cloud-only? | Local Z-Wave equivalent? | In our driver? | Notes |
|---------------|------------|--------------------------|----------------|-------|
| Geofencing | ✅ Cloud-only | None | N/A | No Z-Wave CC for geofencing |
| Adaptive Recovery / Smart Response | ❌ Local param | Param 27 (Adaptive Intelligent Recovery) | ✅ `configParam27` | Z-Wave param 27 controls this on-device |
| 5-2 / 5-1-1 / daily schedule | ❌ Local | Schedule Type (param 1); schedule content keypad-only | ✅ `configParam1` exposed | Remote schedule programming via Z-Wave NOT supported (no SCHEDULE CC) |
| Vacation / Hold mode | ❌ Local setpoint-based | Setpoint set creates hold; returns to schedule at next event | ✅ `setHeatingSetpoint`/`setCoolingSetpoint` | No separate "hold" Z-Wave CC; T6 Pro hold is a setpoint override |
| Air filter reminder | ❌ Local param | Params 30–32 configure intervals; device tracks internally | ✅ `configParam30`–`32` exposed | Device CANNOT report filter alert via Z-Wave (no Notification event for filter); display-only |
| Humidification pad reminder | ❌ Local param | Param 33 | ✅ `configParam33` | Display-only reminder; not Z-Wave reportable |
| Indoor humidity | ❌ Local Z-Wave | SensorMultilevel type 5 | ✅ `humidity` attribute | Polled by `refresh()` |
| Outdoor temperature display | ❌ Local wired sensor | Param 3 enables wired sensor; **display-only, NOT sent to Z-Wave controller** | ✅ Param exposed; no Z-Wave data | Community-confirmed: ZW2003 does not report outdoor temp via Z-Wave |
| Outdoor humidity | N/A | Not supported on T6 Pro | N/A | No CC, no wired input for outdoor humidity |
| Humidity setpoint (humidify/dehumidify target) | N/A | No Humidity Control CC in ZW2003 fingerprint | N/A | **Device cannot set humidity setpoint over Z-Wave.** `RelativeHumidityMeasurement` (read-only) is correct. |
| Filter timer reset | ❌ Physical menu | No Z-Wave command to reset filter timer | N/A | Timer tracked internally; no CC for reset |

**Verdict:** Every local feature of the cloud app that is Z-Wave-accessible is already covered by our driver. Cloud-only features (geofencing) are irrelevant for a local driver. The filter timer reset and humidity setpoint are hardware limitations, not driver gaps.

---

## C. Earlier v0.3.0+ Backlog Status

From the [2026-05-18 Z-Wave survey](decisions.md ~line 4240):

### PROBABLY-ADD items

**D. Params 43–45 (ZW2007 users: Humidity Offset, Temp/Humidity Reporting Resolution)**

Status: **Defer — confirmed ZW2007-only by Z-Wave JS.**

Z-Wave JS `th6320zw.json` for `productType:0x0011 / productId:0x0008` (our TH6320ZW2003) does NOT include params 43–45. They are defined in `honeywell_template.json` under keys `humidity_offset`, `temperature_resolution`, `humidity_resolution` but are not referenced from the ZW2003 device file. This aligns with OZW XML (params 1–42 only for ZW2003).

Effort unchanged: ~15 lines if ever needed. Defer until confirmed working on ZW2003 firmware or Mads acquires a ZW2007.

**E. Notification type 9 (System) handler stub**

Status: **Ship in v0.4.0 — Pick #3.**

A handler for type 9 with `log.warn` for any `event > 0` is ~15 lines. HA Z-Wave JS handles all notification types via its framework; our driver silently discards type 9. The T6 Pro may emit type 9 events for hardware failure conditions (heater/cooler failure, etc.) per Z-Wave Notification CC spec. Low probability but surfacing them costs nothing.

Effort: ~15 lines (handler method + `if` branch in `zwaveEvent(NotificationReport)`).

### MAYBE items

**F. `thermostatHold` inferred attribute**

Status: **Future / nice-to-have — no change.**

Still risky: driver-inferred hold state drifts if setpoints are changed physically. No Hubitat standard capability for hold. No Z-Wave CC for hold state reporting on ZW2003. Skip for v0.4.0; revisit if Mads specifically requests it.

**G. Outdoor temp catch for sensor type 2**

Status: **Drop.**

Zero evidence the ZW2003 sends SensorMultilevel type 2. The HA Z-Wave JS device file has no provision for it. Community evidence and device behavior both confirm outdoor temp is display-only. The 5-line catch would never fire. Not worth adding dead code.

---

## D. Hubitat Platform Gaps

### D1. `descriptionText` missing on three event handlers (Pick #1)

**Status: Ship in v0.4.0.**

```groovy
// Line 554 — missing descriptionText:
eventProcess(name: "thermostatOperatingState", value: newstate)
// Should be:
eventProcess(name: "thermostatOperatingState", value: newstate,
    descriptionText: "${device.displayName} thermostat operating state is ${newstate}")

// Line 581 — missing descriptionText:
eventProcess(name: "thermostatFanMode", value: newmode, type: state.isDigital?"digital":"physical")
// Should be:
eventProcess(name: "thermostatFanMode", value: newmode,
    descriptionText: "${device.displayName} thermostat fan mode is ${newmode}",
    type: state.isDigital?"digital":"physical")

// Line 589 — missing descriptionText:
eventProcess(name: "thermostatMode", value: newmode, type: state.isDigital?"digital":"physical")
// Should be:
eventProcess(name: "thermostatMode", value: newmode,
    descriptionText: "${device.displayName} thermostat mode is ${newmode}",
    type: state.isDigital?"digital":"physical")
```

The v0.2.0 polish pass added `descriptionText` to temperature and humidity events. These three handlers were overlooked. v0.3.0 added it to `thermostatFanState` correctly (line 571). Pattern: three remaining event paths that currently produce no info log when `txtEnable` is true.

Effort: ~6 lines. Risk: None.

### D2. `thermostatFanState` attribute type: "string" → "enum" (Pick #2)

**Status: Ship in v0.4.0.**

Current (line 57):
```groovy
attribute "thermostatFanState", "string"
```

Survey recommendation (decisions.md ~line 4292):
```groovy
attribute "thermostatFanState", "enum", ["idle","running","running high","running medium",
    "circulation mode","humidity circulation mode","right - left circulation mode",
    "quiet circulation mode"]
```

v0.3.0 shipped with `"string"` instead of the intended `"enum"`. Declaring as `"enum"` enables:
- RM4 trigger `if thermostatFanState is "running high"` (enum picker in RM UI)
- Hubitat device page shows value as picker, not freeform input
- Matches Hubitat convention for all other thermostat attributes

Effort: ~6 lines (attribute declaration edit + enum values). Risk: None (additive type refinement; existing attribute value is unchanged).

### D3. `humiditySetpoint` capability

**Status: Drop — hardware doesn't support.**

T6 Pro fingerprint has no Humidity Control CC (`0x6A`). The `RelativeHumidityMeasurement` capability is correct (read-only sensor). There is no Z-Wave mechanism to set a humidify/dehumidify target on ZW2003. The dehumidification filter reminder (param 34) is a maintenance interval, not a setpoint.

### D4. Multi-fan-mode hierarchy (auto/on/circulate/follow-schedule)

**Status: No gap — already complete.**

`supportedThermostatFanModes = ["on","auto","circulate"]` is the correct set for ZW2003. Z-Wave JS confirms the same three modes. "Follow schedule" is not a separate Z-Wave ThermostatFanMode value — it is a Honeywell UI label for circulate (0x06) on some thermostat models. No additional modes to add.

### D5. Setpoint pairs + auto-mode deadband

**Status: No gap — already complete.**

- Heating setpoint: `setHeatingSetpoint()` → setpointType 1
- Cooling setpoint: `setCoolingSetpoint()` → setpointType 2
- Deadband: param 13 (`configParam13` Auto Differential, 0–5°F) already exposed

Both setpoints are polled in `refresh()`. No gap.

### D6. Filter timer reset commands

**Status: Drop — no Z-Wave CC.**

There is no Z-Wave command to reset the T6 Pro's internal filter run-time counter. Params 30–35 only configure reminder intervals. Reset is physical-menu only (hold MODE button for 5 seconds per installer guide). Cannot be implemented in driver.

### D7. Outdoor temp / humidity subscription

**Status: Drop — hardware doesn't support for ZW2003.**

Param 3 enables a wired outdoor sensor that displays on-screen. The ZW2003 does NOT send this value to the Z-Wave controller. Source: driver code (no SensorMultilevel type other than 1 and 5 received), Z-Wave JS device file (no provision for additional sensor types), and community confirmation. TH6320ZW2003 cannot be paired with Z-Wave outdoor sensors for reporting.

---

## Verdict — Drop / v0.4.0 / Future

| Candidate | Verdict | Effort (lines) | Notes |
|-----------|---------|----------------|-------|
| **descriptionText on 3 event handlers** | ✅ **Ship v0.4.0 — Pick #1** | ~6 | Direct hygiene gap; matches v0.2.0 pattern |
| **thermostatFanState attribute type "enum"** | ✅ **Ship v0.4.0 — Pick #2** | ~6 | v0.3.0 shipped "string"; survey intended "enum" |
| **Notification type 9 System handler stub** | ✅ **Ship v0.4.0 — Pick #3** | ~15 | Surfaces firmware alerts; HA handles silently |
| Params 43–45 (ZW2007 only) | 🔜 **Future** — ZW2007 users only | ~15 | Confirmed ZW2007-only by Z-Wave JS th6320zw.json |
| thermostatHold inferred attribute | 🔜 **Future / nice-to-have** | ~20 | Inferred state risks drift; no standard Hubitat capability |
| Humidity setpoint capability | ❌ **Drop — hardware** | N/A | No Humidity Control CC in ZW2003 |
| Filter timer reset command | ❌ **Drop — hardware** | N/A | No Z-Wave CC for reset; physical-menu only |
| Outdoor temp Z-Wave subscription | ❌ **Drop — hardware** | N/A | Display-only; not reported to controller |
| Scene Activation CC cleanup | 🔜 **Future / cleanup** | ~2 | Template artifacts; harmless but wrong |

**v0.4.0 total delta: ~27 lines. All additive. Zero behavior changes to v0.3.0 baseline.**

---

## Sources

| # | Document | URL | Date | Quality |
|---|----------|-----|------|---------|
| 1 | Z-Wave JS device file for T6 Pro (ZW2003) | https://github.com/zwave-js/node-zwave-js/blob/master/packages/config/config/devices/0x0039/th6320zw.json | 2026-05-18 | **HIGH — direct product type/ID match** |
| 2 | Z-Wave JS Honeywell template | https://raw.githubusercontent.com/zwave-js/node-zwave-js/master/packages/config/config/devices/templates/honeywell_template.json | 2026-05-18 | **HIGH — param labels, metadata, $purpose fields** |
| 3 | v0.3.0 driver baseline | `drivers/honeywell-t6-pro/honeywell-t6-pro.groovy` | 2026-05-18 | **PRIMARY** |
| 4 | Earlier Z-Wave survey (decisions.md ~line 4064) | Local | 2026-05-18 | **HIGH — prior research** |
| 5 | Z-Wave JS device directory listing | https://github.com/zwave-js/node-zwave-js/tree/master/packages/config/config/devices/0x0039 | 2026-05-18 | **HIGH — confirmed th6320zw.json is the only T6 Pro file** |
---

## 2026-05-18 — Honeywell T6 Pro driver rename

**Commit:** c56c4ea — `honeywell-t6-pro: rename driver to 'Honeywell T6 Pro Thermostat'`

Mads dropped the "Advanced" prefix carried over from djdizzyd's upstream driver. The driver is now under his namespace and reflects his repo conventions.

**Aligned in 3 files:**
- drivers/honeywell-t6-pro/honeywell-t6-pro.groovy:35 (definition name) — Mads's manual edit
- drivers/honeywell-t6-pro/packageManifest.json:12 (HPM manifest entry) — Tank-7
- drivers/honeywell-t6-pro/README.md:23 (install instructions Type-selector reference) — Tank-7

**Unchanged (intentionally):**
- groovy line 7, 29 + README line 3, 57 reference the upstream djdizzyd "Advanced Honeywell T6 Pro Thermostat" project by name and must stay verbatim per clean-room fork attribution rules.

**Hubitat gotcha noted to Mads:** Existing devices already bound to the old type label will keep showing "Advanced Honeywell T6 Pro Thermostat" in device list until manually re-selected via Edit dropdown.

---

## 2026-05-18 — Honeywell T6 Pro v0.4.0 shipped

**Commit:** a5e1008 — \honeywell-t6-pro v0.4.0: descriptionText + fanState enum + Notification type 9\

**Three Cypher-picks applied (additive only):**

1. **Pick #1 — descriptionText on 3 events** (~6 lines at lines 580-581, 608-610, 618-620 in the new file): thermostatOperatingState, thermostatFanMode, thermostatMode now emit descriptionText matching the v0.2.0 temp/humidity pattern.

2. **Pick #2 — thermostatFanState type → enum** (line 60): \ttribute "thermostatFanState", "enum", [...8 values...]\ matching the THERMOSTAT_FAN_STATE map exactly. RM4 picker + Hubitat device-page rendering improvement.

3. **Pick #3 — Notification type 9 handler** (~15 lines at lines 273-296): added as \lse if (cmd.notificationType==9)\ after the type-8 block. Handles events 0-4 (idle, hw/sw failure, hw/sw failure with product code) with log.warn. Tank chose \lse if\ over \case 9:\ because the existing handler uses if/else if idiom, not a switch.

**Production-safe:** Additive only; v0.3.0 regression-free baseline preserved. Running on Mads's live Downstairs thermostat.

**Tank-8 verified** all changes at expected file locations; v0.4.0 version bumped in driver header (line 4), \@Field static final String VERSION\ (line 79), packageManifest.json (top-level + drivers[0]), and README changelog.

**Mads's correction during work:** "hubitat uses Z-Wave JS now too" — invalidates Cypher's framing of HA's auto-entity-generation as a Hubitat platform constraint. v0.5.0+ research item: what does Hubitat's Z-Wave JS layer expose to driver authors? (config param metadata APIs, device-file-driven entities, attribute-binding patterns). Captured as memory.

---

## 2026-05-18 — Fully Kiosk v0.3.0 Candidates (Cypher analysis)

# Fully Kiosk Browser Controller v0.3.0 Candidates — HA + REST API Gap Analysis

**Baseline:** v0.2.0 (commit 0e8f9ed) — Trinity backlog + namespace switch + password masking  
**Hardware:** Mads's 2 Android tablets running Fully Kiosk Browser (Bathroom + Kitchen)  
**Analyst:** Cypher  
**Date:** 2026-05-18

---

## Executive Summary

v0.2.0 is hygiene-complete but **functionally shallow** compared to the HA integration — HA exposes
~26 entities across 7 platform types; we expose 1 (the driver itself). The headline gap is not
architectural: the `deviceInfo` endpoint we already poll returns 12+ attributes we never emit. Adding
them costs zero additional HTTP calls and closes ~60% of the HA gap in one pass. Beyond sensor
richness, there is one confirmed **bug** (screen brightness scaling: `setLevel(100)` currently sends
raw value 100 to FKB which expects 0–255, yielding only ~39% brightness), one major command gap
(`setOverlayMessage` / overlay notification, the most user-visible missing feature), and 4 utility
commands HA exposes that we don't (`toBackground`, `clearCache`, `forceSleep`, `exitApp`). Video
playback (`playVideo`/`stopVideo`) and maintenance/kiosk lock controls round out a well-scoped
v0.3.0. MQTT push-vs-poll and a parent-child multi-entity architecture are real opportunities but
belong in v0.4.0. **Verdict: ship v0.3.0 (additive, single driver); split v0.4.0 for MQTT +
multi-entity architecture. Estimated v0.3.0 LOC delta: ~120–140 lines.**

---

## ⚡ Top-6 v0.3.0 Picks (ranked by ROI)

| Rank | Pick | Effort | Value | User-visible impact |
|------|------|--------|-------|---------------------|
| 1 | **Fix brightness scaling** — `setLevel` 0–100 → 0–255 mapping + `refreshCallback` read-back fix | S (~10 lines) | Critical | `setLevel(100)` currently delivers ~39% brightness; fix makes `setLevel` match user expectation |
| 2 | **Rich sensor attributes from existing `deviceInfo`** — emit 6 new attrs: `charging` (plugged), `screensaverActive` (isInScreensaver), `batteryTemperature`, `foregroundApp`, `screenOrientation`, `motionDetectionEnabled` | M (~35 lines) | High | Dashboards gain charging status, tablet foreground app, screensaver state — zero extra HTTP calls |
| 3 | **Overlay message command** — `setOverlayMessage(text)` + declare `Notification` capability | S (~8 lines) | High | Rule Machine can flash a text overlay on the tablet screen — hugely useful for alerts |
| 4 | **Utility commands** — `toBackground`, `clearCache`, `forceSleep`, `exitApp` | S (~20 lines) | Medium | Closes 4 HA button gaps; allows RM to clear cache or force background for maintenance |
| 5 | **Video playback** — `playVideo(url)`, `stopVideo()` | S (~10 lines) | Medium | Allows full-screen video display from automations (HA media player already does this) |
| 6 | **Fix `checkInterval` event spam** — gate behind `emitIfChanged` or collapse to one `ping()` | S (~8 lines) | Medium | Every command currently fires a redundant `checkInterval:120` event; silent noise in Events tab |

---

## Verdict — Drop / v0.3.0 / Future

| Candidate | Ship | Effort (lines) | Notes |
|---|---|---|---|
| **Fix brightness scaling** (setLevel 0–100↔0–255) | ✅ **v0.3.0** | ~10 | Bug fix — behavior change, flag in changelog |
| **Rich sensor attributes** (charging, screensaverActive, batteryTemp, foregroundApp, screenOrientation, kioskMode) | ✅ **v0.3.0** | ~35 | Zero extra HTTP calls; all from existing deviceInfo poll |
| **Overlay message** (`setOverlayMessage` + `Notification` capability) | ✅ **v0.3.0** | ~8 | High-value RM use case |
| **Utility commands** (`toBackground`, `clearCache`, `forceSleep`, `exitApp`) | ✅ **v0.3.0** | ~18 | All one-liner `sendCommandPost` calls |
| **Video commands** (`playVideo`, `stopVideo`) | ✅ **v0.3.0** | ~10 | Closes HA media player gap |
| **Kiosk/lock controls** (`lockKiosk`, `unlockKiosk`, `enableLockedMode`, `disableLockedMode`) | ✅ **v0.3.0** | ~14 | Useful for RM maintenance windows |
| **Motion detection toggle** (`enableMotionDetection`, `disableMotionDetection`) | ✅ **v0.3.0** | ~8 | Battery savings during overnight hours |
| **Fix checkInterval spam** (gate with `emitIfChanged`) | ✅ **v0.3.0** | ~5 | Trinity finding #8 — still open |
| **TTS locale support** (add `locale` param to speak) | ✅ **v0.3.0** | ~5 | Minor enhancement; locale matters for non-English TTS |
| **`lastActivity` attribute** on refreshCallback 200 | ✅ **v0.3.0** | ~3 | Completes Trinity §6 scope-of-fork table |

**Total v0.3.0 LOC delta: ~116–136 lines** (new code; no removals except checkInterval spam collapse).

---

## 2026-05-18 — Hubitat MQTT Support Recent Updates (Cypher survey)

# Hubitat MQTT Support — Recent Updates Survey

**Analyst:** Cypher  
**Date:** 2026-05-18  
**Triggered by:** Mads's mention of "recent updates" with MQTT support

---

## Executive Summary

Mads's instinct was correct: Hubitat has shipped a **major MQTT overhaul** across releases 2.4.4.151 through 2.5.0.135 (approximately March – May 2026). The headline changes are (1) a **built-in MQTT broker running on the hub itself** (2.4.4.155, ~late March 2026) — no external Mosquitto server required — and (2) **MQTT device import** with native Zigbee2MQTT, Tasmota, and Home Assistant MQTT discovery support (2.5.0.123, April 23, 2026). These are **platform-level integration features**, not driver SDK changes. The `interfaces.mqtt` driver API has been stable since 2.2.2.

**Key Milestone:** The broker-dependency objection that blocked the Fully Kiosk v0.4.0 MQTT pivot is **now resolved**. FKB devices can point at `tcp://<hubitat_ip>:1883` and the FK driver can subscribe via `interfaces.mqtt` connecting to the same built-in broker — zero new infrastructure. 

**Driver rubric correction flagged:** The "0 pts for MQTT-only" and "Persistent MQTT subscriber = sandbox constraint" penalties are now outdated. Recommend raising MQTT-capable LAN protocols to ≥10 pts in the driver scoring rubric.

**New driver opportunities identified:**
1. **Tasmota** (platform-native auto-detection, 2.5.0.126 beta) — no driver code needed
2. **Zigbee2MQTT device import** (platform-native, 2.5.0.123) — no driver code needed
3. **ESPHome MQTT devices** (custom driver, ~150 LOC, works with built-in broker)
4. **Mitsubishi mini-split via MQTT-MHI bridge** (custom driver + CN105 hardware)

---

## 2026-05-18 — Honeywell T6 Pro v0.5.0 shipped

**Commit:** 1e726b9 — `honeywell-t6-pro v0.5.0: syncClock UX — daily 4am cron, drop manual button`

Replaced runEvery3Hours("syncClock") at 3 locations (configure, updated, initialize) with `schedule("0 0 4 * * ?", "syncClock")`. Removed the `command "syncClock"` dead-UI declaration. The `void syncClock()` method body and `runIn(10, "syncClock")` in configure() preserved as escape hatch. 24× fewer Z-Wave frames per year. DST transitions handled within 24h.

---

## 2026-05-18 — Fully Kiosk v0.3.0 shipped (7 picks)

**Commit:** 6b10f51 — `fully-kiosk v0.3.0: brightness bug fix + 6 new sensors + Notification + utility + video + motion + spam dedupe`

Cypher's 7 picks all landed. Pick #1 BUG FIX: setLevel 0-100 → FKB 0-255 conversion (`Math.round(level * 2.55)` clamped). 6 new sensors from existing deviceInfo (zero extra HTTP calls). Notification capability with deviceNotification(text). 8 utility commands. Video playback. Motion detection toggle. checkInterval event spam dedupe. Net +85 LOC.

---

## 2026-05-18 — Fully Kiosk driver renamed

**Commit:** a38db3b — `fully-kiosk: rename driver 'Fully Kiosk Browser Controller' -> 'Fully Kiosk Browser'`

Dropped "Controller" suffix per Mads's request. 7 user-facing locations aligned. GvnCampbell attribution unchanged. Hubitat gotcha noted in commit msg.

---

## 2026-05-18 — Fully Kiosk v0.4.0 shipped (MQTT subscriber)

**Commit:** 0692b44 — `fully-kiosk v0.4.0: MQTT subscriber (opt-in, defaults to hub's built-in broker)`

196 LOC. Opt-in via `mqttBroker` preference. Connect/disconnect lifecycle, parse() routes MQTT messages, handleFkEvent/handleFkDeviceInfo update the v0.3.0 attributes via push instead of poll. LWT to `{prefix}/hubitat/state`. Reduced poll cadence to 5min when MQTT healthy. Exponential backoff reconnect. Empty `mqttBroker` = exact v0.3.0 polling behavior (zero regression risk).

Pivot was unlocked by Cypher's MQTT survey: Hubitat 2.4.4.155+ ships a built-in MQTT broker — no external Mosquitto required. This eliminated the "broker dependency" objection from Cypher's earlier v0.3.0 candidates report.

---

## 2026-05-18 — Tank scope-discipline learnings

Tank-10 and tank-11 BOTH violated explicit "DO NOT touch files outside drivers/fully-kiosk/" instructions and edited drivers/honeywell-t6-pro/honeywell-t6-pro.groovy to remove a stale upstream version comment. Coordinator reverted both times. Tank-12 received a third spawn with maximum-emphasis warning (3 paragraphs) and complied cleanly. Pattern: when Tank encounters obvious cleanup opportunities in adjacent files during in-scope work, it gets tempted. Future spawn prompts touching files with known nearby junk should preemptively name the junk and explicitly forbid touching it.

---

## 2026-05-18 — New skill extracted: hubitat-mqtt-subscriber-driver

Tank-12 extracted .squad/skills/hubitat-mqtt-subscriber-driver/SKILL.md from the v0.4.0 MQTT implementation. Patterns: connect/disconnect lifecycle, parse() discriminator, LWT + retained online/offline state, exponential backoff reconnect, reduced poll cadence as heartbeat safety net. Applicable to any future MQTT subscriber driver in this repo.



---

# Decision: Away Lights v0.2.0 — Jitter & Sunset-Relative Start

**Date:** 2026-05-20
**Author:** Tank (Driver Developer)
**Status:** Shipped

---

## Context

Away Lights v0.1.0 used a fixed daily `onTime` and `offTime`. Two weaknesses were identified:

1. **Fixed times are a giveaway.** Lights turning on at exactly 4:00 PM every day look automated to an observer. A random offset breaks the pattern without user effort.
2. **4 PM is too early in summer, too late in winter.** Sunset varies by ±2 hours across the year. A sunset-relative start is more natural and requires no seasonal adjustment.

---

## Decisions

### D1: Jitter via `runIn()` + delegated handler (not `Math.random()` inside lightsOn)

Considered computing a jittered epoch time and calling `schedule(Date, ...)` — rejected because `runIn()` is simpler, purpose-built for short delays, and uses seconds (matching our 0–3600s range directly). The delegated handlers (`doLightsOn`, `doLightsOff`) re-check `location.mode == awayMode` as a guard in case mode changes during the delay window.

`checkAndTurnOn()` is **not** jittered — it already has the `awayDebounceMinutes` delay and the mode-change-at-onTime path. Adding jitter would compound delays unpredictably.

### D2: Sunset via noon re-schedule, not `subscribe(location, "sunsetTime", ...)`

Considered subscribing to the Hubitat `sunsetTime` event. Rejected: the event fires at sunset itself, which is too late to schedule `onTimeHandler` (need to set it *before* sunset if offset is negative). Instead: a noon cron (`"0 0 12 * * ?"`) calls `scheduleSunsetOn()` which computes today's sunset+offset and calls `schedule(Date, "onTimeHandler")`. This handles any offset (−120 to +120 min) cleanly.

### D3: `offTime` stays fixed

Sunset-relative off times are uncommon (most users want lights off at a specific bedtime). Keeping `offTime` as a fixed schedule avoids confusion and keeps the implementation simple.

### D4: `isInWindow()` updated for sunset mode

`checkAndTurnOn()` uses `isInWindow()` to decide whether to act after the debounce. When `useSunset` is true, `isInWindow()` now calls `getSunriseAndSunset(sunsetOffset: N).sunset` as the lower bound — same API as `scheduleSunsetOn()` — so the debounce path and the scheduled path agree on the window boundary.

---

## Files Changed

| File | Change |
|---|---|
| `apps/away-lights/away-lights.groovy` | v0.2.0: jitter + sunset features |
| `apps/away-lights/packageManifest.json` | Both version fields → 0.2.0 |
| `apps/away-lights/README.md` | New settings rows + updated How it works |
| `packageManifest.json` (root) | Away Lights entry → 0.2.0 |


---

# Decision: Away Lights — schedule() form for time preferences

**Date:** 2026-05-20  
**Author:** Tank  
**App:** `apps/away-lights/away-lights.groovy`

## Decision

Used `schedule(onTime, "onTimeHandler")` / `schedule(offTime, "offTimeHandler")` directly with the raw time-preference string rather than parsing a cron expression manually.

## Rationale

Hubitat's `schedule()` overload accepts a time-preference value (ISO-style string from a `"time"` input) and schedules a daily callback at that time, handling DST automatically. This is simpler and less error-prone than computing `"0 ${min} ${hour} * * ?"` cron strings from parsed hour/minute values, which would break across DST boundaries and require boilerplate parsing code with no benefit.

## Applicable to

Any Hubitat app that needs a daily scheduled action driven by a user-configured `"time"` preference input.


---

# Pattern: Hubitat Groovy Driver Performance Checklist

**Date:** 2026-05-19  
**Source:** PST02 v1.1.0 → v1.2.0 audit  
**Author:** Tank

---

## Reusable Patterns Discovered

### 1. Implicit-global trap in `def` methods

In Hubitat Groovy drivers, any variable assigned **without** `def` or a type declaration inside a `def` method body becomes a script-level `Binding` entry rather than a stack-local variable. This means:

- It persists between method calls (stale value risk)
- It requires a map lookup on every access (slower than stack locals)
- It can mask bugs (resync check using stale `resync` from prior wakeup)

**Pattern to apply:** Always declare with a type or `def`:
```groovy
// BAD — implicit global
resync = state.pendingResync
value  = resolveConfigParam5()

// GOOD — stack locals
boolean resync = state.pendingResync as boolean
Integer value  = resolveConfigParam5()
```

**Hotspots to audit:** `deviceSync()` and any switch-heavy event handler where case bodies reuse a variable name across cases without re-declaring.

---

### 2. Cache `isPst02BVariant()` (or any settings/getDataValue call) in resolve helpers

Helper functions that are called multiple times per wakeup should cache any settings/`getDataValue` reads in a local variable at the top:

```groovy
Integer resolveConfigParam5() {
    boolean isB = isPst02BVariant()   // ONE settings/getDataValue read
    if (parameterMode == "raw") return (para5Raw != null ? para5Raw.toInteger() : (isB ? 61 : 56))
    Integer value = 0
    if (!isB) value = setBit(value, 0x04, ...)
    ...
}
```

Without caching, `isPst02BVariant()` (which may call `getDataValue("deviceId")`) is invoked twice per function call and N×3 times per `deviceSync()` wakeup.

---

### 3. Redundant `configurationGet` in resync info-read block

When a driver has a "diff-check" block that sends `configurationGet(N)` conditionally on `resync || state.N != value`, the `resync ||` arm already covers the resync case. A separate "info-only reads on resync" block that also sends `configurationGet(N)` for the same parameter N is **always** a duplicate.

On battery devices, every Z-Wave roundtrip drains the battery. Audit resync info-read blocks against the diff-check block above them.

---

### 4. Inline `sendEvent` for simple events

For single-attribute events with no complex logic, prefer:
```groovy
sendEvent(name: "battery", value: batteryLevel, unit: "%", descriptionText: "battery is ${batteryLevel}%")
```
over:
```groovy
def map = [:]
map.name = "battery"
map.value = batteryLevel
...
sendEvent(map)
```

The inline form skips one `HashMap` allocation and is easier to read. Reserve the local `Map map` pattern for events that require conditional branching to set the value or name.

---

### 5. Typed return types reduce dynamic dispatch overhead

Declare concrete return types on frequently-called helpers:
- `Integer setBit(Integer, Integer, Boolean)` — already typed args; add `Integer` return
- `boolean isPst02BVariant()` — short-circuits on preference strings; `boolean` return avoids boxing
- `Integer resolveConfigParam5/6/7()` — always returns a byte-range int; `Integer` return

The Hubitat sandbox JVM benefits from type hints on hot paths even in dynamic Groovy because the JIT can optimize known-type call sites.

---

### 6. `${cmd.toString()}` → `${cmd}` in GStrings

In Groovy GStrings, `${obj}` already calls `obj.toString()` implicitly. `${cmd.toString()}` is redundant and creates an extra method call. Use `${cmd}` throughout.

---

## When to Apply This Checklist

Apply to any new Hubitat driver or driver audit:
1. Grep for bare assignments (`^    [a-z]+ =` without `def`/type) inside `def` methods
2. Count calls to any function that reads `settings.*` or `getDataValue()`; cache if called >1× in a hot path
3. Diff-check resync blocks against info-only resync blocks for duplicate `configurationGet` calls
4. Review `def map = [:]` patterns; inline if the event has no conditional branching
5. Verify all `log.trace`/`log.debug` string args use `${cmd}` not `${cmd.toString()}`

