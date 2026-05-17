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


