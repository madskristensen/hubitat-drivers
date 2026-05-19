# Decisions

Generated 2026-05-18T17:59:14Z

---

## 2026-05-18 — Fully Kiosk v0.5.0 rollback

- Decision: Fully Kiosk v0.5.0 removes MQTT support after v0.4.x attempts failed at the broker handshake layer
- Rationale: Cloud-poll architecture worked reliably; MQTT added complexity without benefit
- Future: If MQTT is revisited, prototype against a verified broker (e.g., test.mosquitto.org) before integrating into the driver

---

## 2026-05-18 — Fully Kiosk v0.4.5 shipped (mqtt.connect signature fix)

**Commit:** 47ba616 — `fully-kiosk v0.4.5: BUG fix invalid interfaces.mqtt.connect() signature`

v0.4.0's mqttConnect() passed `interfaces.mqtt.connect(broker, clientId, user, pass, lwtMap)` — a 5-arg form with a Map for LWT options. Hubitat rejects this:

> `No signature of method: hubitat.helper.interfaces.Mqtt.connect() is applicable for argument types: (java.lang.String, GStringImpl, null, null, LinkedHashMap)`

Switched to Hubitat's documented 8-arg form: `interfaces.mqtt.connect(broker, clientId, user, pass, lwtTopic, lwtQos, lwtRetained, lwtPayload)`. LWT design preserved — `{prefix}/hubitat/state` publishes `offline` (retained QoS 1) on disconnect, `online` on connect-success in mqttClientStatus.

**Tank-17 minor bug:** wrote its history entry to `tank/history.md` at repo root instead of `.squad/agents/tank/history.md`. Coordinator caught this, Scribe relocated. Tank's MultiEdit/Create tool may be using CWD-relative paths instead of TEAM_ROOT-anchored paths under some conditions. Future spawn prompts should reinforce: history.md path is ALWAYS `.squad/agents/{name}/history.md` regardless of CWD.

---

## 2026-05-18 — Fully Kiosk MQTT Reality Check (Cypher follow-up)

---
author: cypher
date: 2026-05-18T18:30:00-07:00
status: completed
subject: FKB MQTT topic/event verification — identified 4 event-name mismatches
---

# Fully Kiosk MQTT — Reality Check

**Triggered by:** Mads's live testing shows only Broker URL / Username / Password fields. v0.4.0 driver assumed more.
**Analyst:** Cypher
**Date:** 2026-05-18

---

## TL;DR

FKB's MQTT feature IS a PLUS feature but is free to try indefinitely. The default topic structure
(`fully/deviceInfo/{deviceId}` and `fully/event/{eventType}/{deviceId}`) is **confirmed correct**,
and the v0.4.0 driver's wildcard subscription to `fully/#` will receive everything. However, the
driver has **wrong event-type name mappings** (`motionDetected`, `unpluggedAC`, `batteryLevel`, and
`foregroundApp` do not match FKB's actual published event names), so several push events will be
silently dropped. The next step is a v0.4.3 patch to fix those four event-name mappings.

---

## What FKB Actually Exposes (free vs PLUS)

**Source:** Official FKB documentation at https://www.fully-kiosk.com/en/#mqtt
(fetched 2026-05-18; "Other Settings" section + dedicated "## MQTT Integration" section)

MQTT is explicitly marked **PLUS** in the feature list:
> "MQTT integration (ver. 1.34+)" — listed under PLUS features

However, the same page states:
> "All PLUS features are unlimited FREE to try."

So MQTT should be accessible without paying. The settings appear under:
**Settings → Other Settings → MQTT Integration (PLUS)**

### Fields the app exposes

| Field | Notes |
|---|---|
| Enable MQTT | Toggle — turns the entire feature on/off |
| MQTT Broker URL | e.g. `tcp://broker:1883` or `ssl://…:8883` |
| MQTT Broker Username | Optional |
| MQTT Broker Password | Optional |
| MQTT Client ID | Optional; random if blank; supports `$deviceId` variable. **Added in FKB 1.41+** |
| MQTT Device Info Topic | Full topic string; default `$appId/deviceInfo/$deviceId`. **Added in FKB 1.41+** |
| MQTT Event Topic | Full topic string; default `$appId/event/$event/$deviceId`. **Added in FKB 1.41+** |

### Why Mads only sees three fields

Two most likely explanations (in order of probability):

1. **The "Enable MQTT" toggle must be switched ON first.** Android settings commonly hide
   sub-fields until the parent toggle is enabled. Mads may have opened the MQTT section but
   not yet flipped the toggle, so only the basic connection fields are shown.
2. **The Client ID and Topic fields require scrolling** within the MQTT sub-section. They were
   added in FKB 1.41 and appear below the Password field.

**There is no evidence that the free-trial tier hides these fields.** The docs list all seven
fields without a license-tier qualifier inside the MQTT sub-section.

---

## Actual Topic Structure

**Source:** "## MQTT Integration" section of https://www.fully-kiosk.com/en/#mqtt (fetched 2026-05-18)

### Default topics (no customization)

> "The complete device info will be published every 60 seconds as
> `fully/deviceInfo/[deviceId]` topic (retaining, QOS=1).
> Events will be published as `fully/event/[eventId]/[deviceId]` topic (non-retaining, QOS=1)."

| Topic pattern | Direction | Retained | QOS |
|---|---|---|---|
| `fully/deviceInfo/{deviceId}` | FKB → broker | Yes | 1 |
| `fully/event/{eventType}/{deviceId}` | FKB → broker | No | 1 |

The `$appId` placeholder defaults to `fully`. Users running FKB 1.41+ can override the full topic
strings, but the **default is always `fully/…`**.

### Configurable via FKB 1.41+

If a user sets "MQTT Device Info Topic" to e.g. `mytablet/deviceInfo/$deviceId`, the default
`fully/…` prefix no longer applies. The v0.4.0 driver's `mqttTopicPrefix` preference handles this
case — the user must match the custom prefix they set in FKB.

---

## Payload Format

**Source:** Official docs + JS-event API cross-reference (same page, fetched 2026-05-18)

### deviceInfo topic payload

Full JSON object — identical to the REST `?cmd=getDeviceInfo` response. Contains fields including
`batteryLevel`, `screenOn`, `screenBrightness`, `currentPage`, `isPlugged`, `appVersionName`, etc.
Published every 60 seconds (retained). The v0.4.0 `handleFkDeviceInfo()` method correctly parses
this JSON.

### Event topic payloads

The docs do not specify event-topic payload format explicitly. Based on the JS interface event
signatures (same page), payloads are likely:

| Event | Likely payload |
|---|---|
| `screenOn`, `screenOff` | Empty or `{}` |
| `onBatteryLevelChanged` | JSON with `$level` (e.g. `{"level":85}`) or plain integer string |
| `pluggedAC`, `pluggedUSB`, `pluggedWireless`, `unplugged` | Empty or `{}` |
| `onMotion` | Empty or `{}` |
| `onWebUrlChanged` | URL string or JSON |
| `foreground`, `background` | Empty or `{}` |

**Note:** Battery and screen-state changes are also reliably delivered via the 60-second
`deviceInfo` retained publish. The event topics provide real-time notification; `deviceInfo`
provides ground truth.

---

## Complete List of FKB MQTT Events

**Source:** "## MQTT Integration" section of https://www.fully-kiosk.com/en/#mqtt (fetched 2026-05-18)

> `screenOn, screenOff, pluggedAC, pluggedUSB, pluggedWireless, unplugged,
> networkReconnect, networkDisconnect, internetReconnect, internetDisconnect,
> powerOn, powerOff, showKeyboard, hideKeyboard, onMotion, onDarkness, onMovement,
> volumeUp, volumeDown, onQrScanCancelled, onBatteryLevelChanged,
> onScreensaverStart, onScreensaverStop, onDaydreamStart, onDaydreamStop,
> onItemPlay, onPlaylistPlay, facesDetected, foreground, background,
> onWebUrlChanged, kioskLocked, kioskUnlocked`

---

## Implications for v0.4.0 Driver

### ✅ What is CORRECT in v0.4.0

| Feature | Status |
|---|---|
| Default `mqttTopicPrefix` = `"fully"` | ✅ Correct — matches FKB's default `$appId` |
| Wildcard subscription `{prefix}/#` | ✅ Correct — catches both `fully/deviceInfo/…` and `fully/event/…` |
| `${prefix}/event/` branch in `parseMqttMessage()` | ✅ Correct topic prefix check |
| `${prefix}/deviceInfo` branch in `parseMqttMessage()` | ✅ Correct topic prefix check |
| `parts[2]` for `eventType` in event topic `fully/event/{eventType}/{deviceId}` | ✅ Correct index |
| `handleFkDeviceInfo()` parsing the 60-second JSON snapshot | ✅ Correct |
| MQTT subscribe-only (no publish of commands) | ✅ Correct — FKB does not subscribe to any topics |

### ❌ What is WRONG in v0.4.0 — event-name mismatches

The driver's `handleFkEvent()` switch uses wrong event-type strings. FKB publishes different names:

| Driver case label | FKB actual event name | Effect |
|---|---|---|
| `"motionDetected"` | `"onMotion"` | Motion events **silently dropped** |
| `"unpluggedAC"` | `"unplugged"` | Unplug events **silently dropped** |
| `"batteryLevel"` | `"onBatteryLevelChanged"` | Real-time battery events **silently dropped** |
| `"foregroundApp"` | *(not a real FKB event)* | Dead code — never fires |

**Additional unhandled events** worth adding:

| FKB event | Suggested attribute |
|---|---|
| `pluggedUSB` | `charging` → `"true"` |
| `pluggedWireless` | `charging` → `"true"` |
| `onWebUrlChanged` | `currentURL` attribute |
| `foreground` / `background` | driver-state metadata (optional) |

### Is `mqttTopicPrefix` Meaningful?

Yes — it maps to FKB's configurable "MQTT Device Info Topic" / "MQTT Event Topic" prefix. The
default `"fully"` is correct for out-of-the-box FKB installs. The preference IS useful for users
who customise their FKB topic strings (FKB 1.41+). No change needed here.

---

## Does FKB Support MQTT Commands FROM Hubitat?

**No.** FKB is **publish-only** on MQTT. The documentation describes no subscription capability.
All commands (screenOn, loadUrl, restart, etc.) must go via the **REST API** at
`http://{deviceIp}:2323/?cmd=…`. The v0.4.0 driver already handles commands via REST — this is
correct and should not change.

---

## Recommended Action

### Option (a): v0.4.3 patch — fix event-name mappings ✅ RECOMMENDED

Four event-name mismatches need fixing in `handleFkEvent()`. This is a pure bug fix; the topic
structure, subscription pattern, and REST command path are all correct.

**Changes required in `drivers/fully-kiosk/fully-kiosk.groovy`:**

```groovy
// BEFORE (wrong)          // AFTER (correct FKB event name)
"motionDetected"     →     "onMotion"
"unpluggedAC"        →     "unplugged"
"batteryLevel"       →     "onBatteryLevelChanged"
"foregroundApp"      →     remove (dead code) or map to "onWebUrlChanged"
```

Also add `pluggedUSB` and `pluggedWireless` cases to set `charging = true` (currently only
`pluggedAC` is handled).

**Battery payload note:** The driver's existing `onBatteryLevelChanged` handler tries to parse a
`batteryLevel` JSON key from the payload. The actual payload format is unconfirmed by official
docs; if events fire but battery doesn't update, fallback to the 60-second `deviceInfo` poll
(which definitely contains `batteryLevel`) is already in place via `handleFkDeviceInfo()`.

### For Mads immediately — verify FKB settings

In FKB on the tablet:
1. Go to **Settings → Other Settings → MQTT Integration**
2. **Enable the "Enable MQTT" toggle** — additional fields (Client ID, topic customisation)
   should appear below the Password field after enabling
3. Confirm Broker URL is set correctly
4. Scroll down to verify Client ID and topic fields are left at defaults (blank = use `fully/…`)

---

## Sources

- https://www.fully-kiosk.com/en/#mqtt — official FKB documentation (fetched 2026-05-18)
  - "Other Settings" section: MQTT field list
  - "## MQTT Integration" section: topic structure, event names, payload notes
- `drivers/fully-kiosk/fully-kiosk.groovy` v0.4.0: driver source reviewed 2026-05-18
- Home Assistant `python-fullykiosk` library: uses REST exclusively, no MQTT code found

---

## 2026-05-18 — Fully Kiosk v0.4.1 shipped

**Commit:** 325d44e — `fully-kiosk v0.4.1: bug-fix patch`

Three live-testing fixes from Mads's tablet:
- BUG: NPE guard in beep() — toneFile preference null on fresh install
- LOG NOISE: 408 timeout demoted from error→warn at 4 callback sites (transient by nature; tablet briefly unreachable)
- BREAKING: Removed setScreenBrightness command — dual-scale UX (setLevel 0-100 vs setScreenBrightness 0-255) was confusing. setLevel(0-100) is the only brightness command now.

---

## 2026-05-18 — Fully Kiosk v0.4.2 shipped

**Commit:** 85d6aea — `fully-kiosk v0.4.2: add clearOverlayMessage()`

Mads asked how to dismiss overlay messages after setting them. Added clearOverlayMessage() command calling FKB's setOverlayMessage endpoint with empty text (documented FKB dismissal pattern). Complements setOverlayMessage(text) and deviceNotification(text).

---

## 2026-05-18 — Fully Kiosk v0.4.3 shipped (MQTT event-name fix)

**Commit:** 6bae12f — `fully-kiosk v0.4.3: fix MQTT event-name mismatches`

After v0.4.0 MQTT pivot, Cypher's reality-check uncovered 4 event-name mismatches in handleFkEvent() — driver was listening for the WRONG event names (motionDetected/unpluggedAC/batteryLevel/foregroundApp) instead of what FKB actually publishes (onMotion/unplugged/onBatteryLevelChanged; foregroundApp comes via deviceInfo not as event). Architecture was correct; just the case labels needed renaming.

---

## 2026-05-18 — Fully Kiosk v0.4.4 shipped (leading-slash defensive)

**Commit:** fc10867 — `fully-kiosk v0.4.4: defensive leading-slash handling for MQTT topics`

FKB docs publish topics with leading slash (/fully/event/...). MQTT semantically treats /fully/ and fully/ as different topic spaces. Belt-and-suspenders: subscribe to both `${prefix}/#` and `/${prefix}/#`, strip leading slash in parseMqttMessage before dispatch. Driver now robust to either FKB convention.

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
