# Squad Decisions

## Active Decisions

### 2026-05-16: Hubitat static field correction for v0.2.2
**By:** Tank
**Status:** Proposed correction
**Supersedes:** Earlier `2026-05-16: Hubitat static field init gotcha`

**Correction**
The prior rule was incomplete: `+` concatenation does **not** avoid the Hubitat sandbox error when one `@Field static final` initializer references another `@Field` constant.

**Verified Rule**
**Hubitat sandbox rule (verified across v0.2.0 → v0.2.1 → v0.2.2 fix attempts):** `@Field static final` initializers in Hubitat drivers MUST NOT reference any other `@Field` constant — not via GString `${X}`, not via concatenation `+ X`, not at all. The Hubitat sandbox parser rejects both forms with the same "static scope" error.

**Use This**
```groovy
@Field static final String USER_AGENT = "Hubitat Gemstone Lights/0.2.2"
```

Or compute at use-site inside a method body:
```groovy
headers["User-Agent"] = "Hubitat Gemstone Lights/${DRIVER_VERSION}"
```

**Do Not Use**
```groovy
@Field static final String USER_AGENT = "Hubitat Gemstone Lights/${DRIVER_VERSION}"
@Field static final String USER_AGENT = "Hubitat Gemstone Lights/" + DRIVER_VERSION
```

**Why**
Hubitat's sandbox static-scope analyzer runs before sibling `@Field` constants are bound for initializer lookup, so any cross-`@Field` reference is rejected.

**Applied To**
- `drivers/gemstone-lights/gemstone-lights.groovy` v0.2.2
- `.squad/skills/hubitat-sandbox-pitfalls/SKILL.md`
- `.squad/agents/tank/history.md`

---

### 2026-05-16: Gemstone Lights v0.2.3 Hubitat sandbox audit
**By:** Tank
**Status:** Completed
**Scope:** `drivers/gemstone-lights/gemstone-lights.groovy`

**Why**
v0.2.2 cleared the parse-time static-field issue, but Hubitat then failed at runtime on `System.currentTimeMillis()`. That proved the driver needed a full end-to-end sandbox audit across both parse-time and runtime restrictions.

**Audit Sweep**
Scanned the full driver for:
- `System.`
- `Thread.`
- `Runtime.`
- `Class.` / `.getClass(`
- `Date.parse` / `new Date()`
- `File(` / `Files.` / file I/O
- `Eval.` / `GroovyShell` / `GroovyClassLoader` / `MetaClass` / `.eval(`
- `UUID.randomUUID`
- `URLEncoder.encode`

**Findings and Actions**
| Pattern / API | Hubitat-safe replacement | Result in v0.2.3 |
|---|---|---|
| `System.currentTimeMillis()` | `now()` | Replaced all token-time calculations with a shared `currentEpochSeconds()` helper backed by `now()`. |
| `UUID.randomUUID()` | `now()`-based helper when sandbox behavior is uncertain | Replaced both pattern id call sites with `generatePatternId()` using `now()` + state sequence. |
| `Thread.sleep(ms)` | `pauseExecution(ms)` | Not present. |
| `Thread.*` / executors | `runIn`, `runEvery*`, scheduler callbacks | Not present. |
| `Runtime.*` | Hubitat scheduling / HTTP / state APIs | Not present. |
| reflection (`Class.forName`, `.getClass()`) | direct references / explicit branching | Not present. |
| `new Date()` / `Date.parse(...)` | `new Date(now())`, `timeToday`, `toDateTime` | Not present. |
| `File`, `Files`, `java.nio.*` | `state`, `atomicState`, HTTP, preferences | Not present. |
| `System.out.*` / `System.err.*` | `log.info`, `log.warn`, `log.error`, `log.debug` | Not present. |
| `URLEncoder.encode(...)` | Usually allowed in Hubitat | Retained for query-string encoding; no sandbox failure evidence for this API in this driver. |

**Driver Changes Applied**
- Bumped driver version metadata and inlined user agent literal from `0.2.2` to `0.2.3`.
- Added changelog entry documenting the sandbox audit.
- Replaced all `System.currentTimeMillis()` usage.
- Replaced `UUID.randomUUID()` with a `now()`-based helper.
- Updated per-driver README, testing note, and HPM manifest to `0.2.3`.

**Team Knowledge Updated**
- Renamed the skill to `.squad/skills/hubitat-sandbox-pitfalls/SKILL.md` and expanded it to cover both parse-time static-field failures and runtime JDK blocklist issues.
- Appended Tank history with the two-layer Hubitat sandbox learning.

**Outcome**
Gemstone Lights v0.2.3 now avoids the known parse-time `@Field` trap and the runtime `System.*` trap, with the full driver re-scanned for additional sandbox-risk APIs before shipping.

---

### 2026-05-16T15:30:00-07:00: Framework Fingerprint: Gemstone Lights Controller Local API
**By:** Cypher
**What:** Attempted to identify the HTTP framework powering the Gemstone Lights Gen 2 controller's local API at 192.168.1.238:80. Analysis of 404 response (`{"error": "Invalid route."}`), missing `Server:` header, and `Connection: close` behavior.

**Findings:**
- `"Invalid route."` error string: Generic across frameworks (Express, Koa, Flask, custom); no unique match. Likely Express-derived or minimal Node.js.
- NO `Server:` header: **Strongest signal.** Suggests intentional suppression or custom/minimal framework. Standard embedded servers include this header.
- `Connection: close`: Simple server, resource-constrained, no keep-alive.
- `Content-Type: application/json`: API-first design.

**Most Likely Stack:** Custom lightweight Node.js or Go HTTP router (Restana, find-my-way, or raw `net/http`), following REST conventions. Rust (actix-web) lower likelihood but not ruled out.

**Conclusion:** Framework is unidentifiable from verbose curl headers alone. No unique fingerprint found. Web search for `"Invalid route."` yielded 100+ generic matches across all frameworks.

**Recommended Next Steps (ordered by yield-per-effort):**

**Phase 1: HTTP Method Variation & OPTIONS (5 min)**
- `OPTIONS /` to discover allowed methods
- `POST /` with Content-Type: application/json
- `PUT /` with Content-Type: application/json
- `HEAD /` to test for hidden headers

**Phase 2: Common REST Paths (10 min)**
- `/api/v1/` root (most likely pattern per Gemstone community)
- `/api/v1/status` (Gemstone community pattern)
- `/lights` (generic IoT endpoint)
- `/info` or `/about` (device metadata)

**Phase 3: Framework Debug/Introspection (5 min)**
- `/_routes` or `/__routes__` (Route list middleware)
- `/admin` or `/debug` (Admin panel)

**Phase 4: Alternate API Versioning (5 min)**
- `/v1/status` (alternate convention)
- `/rest/status` (JAX-RS convention)

**Port Scan: Alternate Ports (10 min)**
If port 80 yields nothing: scan 3000, 3001, 5000, 5001, 8000, 8001, 8080, 8081, 8443, 8888, 9000, 9001, 9090, 81, 443

**mitmproxy Gate: Next Step**
- **GO to mitmproxy** if all probes return `"Invalid route."` on all open ports
- **SKIP mitmproxy** if any probe returns valid JSON (200, 201, route list, etc.)

**Why:** The absence of a `Server:` header and generic error string make traditional framework fingerprinting ineffective. HTTP method variation and REST path enumeration are the highest-yield, lowest-effort next steps. mitmproxy is the fallback if passive probing exhausts all REST patterns.

**References:**
- Cloud API spec: `.squad/decisions/inbox/cypher-gemstone-protocol-spec.md`
- Local capture playbook: `.squad/decisions/inbox/cypher-local-capture-playbook.md`
- Probe history: `.squad/agents/cypher/history.md`

---

### 2026-05-16T14:08:16-07:00: Repo folder structure for multi-driver layout
**By:** Trinity
**What:** Use a top-level `drivers/` folder (lowercase) with one subfolder per driver, named in `kebab-case`. The Gemstone Lights driver lives at `drivers/gemstone-lights/`. Each subfolder holds only the files for that driver — no cross-driver files below `drivers/`. The repo root holds `repository.json` (HPM author index) once we register with HPM. The exact path for the first driver file will be `drivers/gemstone-lights/gemstone-lights.groovy`.

Layout:
```
hubitat-drivers/
├── drivers/
│   └── gemstone-lights/
│       ├── gemstone-lights.groovy       ← Tank drops this here
│       ├── packageManifest.json         ← HPM manifest (per-driver)
│       └── README.md                    ← per-driver user docs
├── repository.json                      ← HPM author index (root, add when publishing)
├── README.md
├── LICENSE
└── .gitignore
```

**Why:** `lowercase/kebab-case` is the safest choice for cross-platform git repos (avoids Windows/macOS case collisions) and matches how dcmeglio structures single-integration repos. The per-driver subfolder pattern (jshimota01, bptworld) is the dominant community pattern and keeps each driver fully self-contained for HPM packaging — HPM's `packageManifest.json` path and raw GitHub URL are stable even as the repo grows.
**References:**
- https://github.com/jshimota01/hubitat (per-driver subfolders, `Drivers/<snake_case>/`)
- https://github.com/bptworld/Hubitat (per-driver subfolders, `Drivers/<Title Case>/`)
- https://github.com/dcmeglio/hubitat-bond (flat `drivers/` for single-package repos)
- https://github.com/HubitatCommunity/hubitat-packagerepositories (HPM global index structure)


---

### 2026-05-16T14:08:16-07:00: Repo-level conventions for hubitat-drivers
**By:** Trinity
**What:** Define exactly what lives where and how things are named.

**Repo root — required files:**
| File | Purpose |
|------|---------|
| `README.md` | Repo overview, links to each driver's folder |
| `LICENSE` | MIT (Hubitat community default) |
| `.gitignore` | Standard Groovy/IDE ignores |
| `repository.json` | HPM author index — add when first driver is published to HPM; until then, omit |

**Repo root — do NOT add:**
- Per-driver docs or code (everything driver-specific stays under its subfolder)
- `hpm.exe` or other binaries
- `CHANGELOG.md` at root level (per-driver READMEs carry the changelog for their own driver)

**Per-driver folder — required files:**
| File | Purpose |
|------|---------|
| `<driver-name>.groovy` | The driver code |
| `README.md` | User-facing docs: what it does, how to install, preferences, known issues |
| `packageManifest.json` | HPM manifest — include from day one even before HPM publishing; keeps structure consistent |

**Per-driver folder — optional:**
| File | When to add |
|------|------------|
| `assets/` subfolder | Screenshots, diagrams referenced from the README |
| `CHANGELOG.md` | When release notes outgrow the README |

**File naming conventions:**
- **Driver folder:** `kebab-case` — e.g., `gemstone-lights/`
- **Driver `.groovy` file:** `kebab-case` matching the folder — e.g., `gemstone-lights.groovy`. Rationale: lowercase kebab is unambiguous on all platforms, consistent with how the `drivers/` top-level folder is named, and is the safest choice for raw GitHub URL stability in HPM manifests.
- **No version number in the filename** — version lives in the `definition` block inside the `.groovy` file and in `packageManifest.json`. Old versions are tracked via git history, not by keeping renamed copies.
- **README.md** — always Title Case, Markdown, standard name.
- **packageManifest.json** — always this exact name (HPM requirement).

**HPM manifest conventions:**
- `namespace`: `"mads"` (consistent across all drivers in this repo)
- `id`: generate a UUID v4 once per driver; never change it (HPM tracks installations by this ID)
- `location`: must be a `raw.githubusercontent.com` URL pointing to the `.groovy` file on `main` branch
- `minimumHEVersion`: `"2.3.0"` (current safe baseline for C-7/C-8)
- `communityLink`: add once a Hubitat community thread exists; placeholder `""` until then

**Why:** Conventional over novel — these choices match what the most-active multi-driver community repos do, keep HPM packaging frictionless from the start, and eliminate ambiguity when Tank or Cypher create new driver files. Kebab-case filenames are the one place where this repo diverges from camelCase/PascalCase tradition — it's the right call for URL stability and cross-platform safety.
**References:**
- https://github.com/jshimota01/hubitat (most complete example of per-driver subfolder + HPM manifest convention)
- https://github.com/dcmeglio/hubitat-bond/blob/master/packageManifest.json (HPM manifest structure reference)
- https://github.com/HubitatCommunity/hubitat-packagerepositories (HPM global index — shows how `repository.json` plugs in)


---

### 2026-05-16T14:08:16-07:00: Gemstone Lights driver capabilities and design
**By:** Trinity
**What:** Single driver (no parent/child split yet). Declare the following Hubitat capabilities:

**Capabilities:**
- `Actuator` — required base for any command-issuing driver
- `Switch` — on/off
- `SwitchLevel` — brightness (0–100)
- `ColorControl` — hue/saturation for solid color mode
- `ColorTemperature` — warm/cool white (if Gemstone supports it; add only if confirmed)
- `LightEffects` — for scene/effect selection (maps to Gemstone zone/pattern programs)
- `Refresh` — manual state poll
- `Initialize` — run at hub startup to restore polling schedule

**On parent/child:** Defer. Gemstone has multiple zones/segments, but until Cypher confirms whether zones can be independently addressed over the API, a single driver controlling the full string is the right starting point. A parent/child split is the right answer only if zones are independently controllable AND users want per-zone Hubitat devices. Revisit after protocol research.

**Command surface:**
- `on()` / `off()` — full strip on/off
- `setLevel(level)` — brightness 0–100
- `setColor(colorMap)` — hue/saturation/level map, Hubitat standard
- `setColorTemperature(colorTemp, level?, transitionTime?)` — if supported
- `setEffect(effectNumber)` — selects a numbered scene from `lightEffects` JSON list
- `refresh()` — explicit state poll
- `initialize()` — set up polling schedule

**State attributes (beyond capability-required):**
- `effectName` (string) — human-readable name of active effect

**Polling/refresh strategy:**
- Use `runEvery5Minutes` (or configurable preference 1/5/10 min) scheduled via `initialize()`
- Optimistic updates: update Hubitat state attributes immediately on command send; reconcile on next poll
- On hub restart, `initialize()` is called automatically; re-register the schedule there

**Logging convention:**
- `logEnable` boolean preference (default: false), auto-disabled after 30 minutes via `runIn(1800, logsOff)`
- `debugLog()` helper that gates on `logEnable`
- INFO level: state changes (on/off, color, effect), polling errors
- WARN level: unexpected HTTP response codes
- ERROR level: parse failures, null device state

**Namespace and metadata:**
- `namespace`: `"mads"`  (short, matches owner — changeable but keep consistent across drivers in this repo)
- `author`: `"Mads Kristensen"`
- `version` attribute in driver metadata (top of .groovy `definition` block), matching `packageManifest.json`
- Start at `version: "0.1.0"`, bump minor on new features, patch on bug fixes

**Why:** Single driver keeps complexity low until the protocol is understood. LightEffects is the right capability for Gemstone's scene-based programming model — it gives a clean UI in the Hubitat dashboard. Optimistic updates + reconciliation polling is the standard pattern for LAN-polling drivers; avoids stale state without hammering the device.
**References:**
- https://docs.hubitat.com/index.php?title=Driver_Capability_List (capability reference)
- https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/GenericZigbeeRGBWBulb.groovy (color + level driver pattern)
- https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/httpGetSwitch.groovy (LAN HTTP polling pattern)


---

### 2026-05-16T14:08:16-07:00: Gemstone HTTP API protocol spec

**By:** Cypher
**Source reference:**
- `sslivins/hass-gemstone` @ `a4abbf029ba8631caa445789e598e51da8bb7721` — https://github.com/sslivins/hass-gemstone
- `sslivins/pygemstone` @ `263ee41a8e8195c9384e277266db317f94dba641` — https://github.com/sslivins/pygemstone

**What:** Complete Gemstone HTTP API as derived from `hass-gemstone` and its underlying `pygemstone` library. Both repos were reverse-engineered from iOS app `com.gemstone.lights` v0.6.03 via mitmproxy WireGuard capture. This spec covers 21 REST endpoints plus an AppSync GraphQL stub. It ALSO documents a critical finding: the reference implementation is **cloud-only**, not local.

---

## ⚠️ Critical Finding: Cloud API vs. Local API

The `hass-gemstone` integration is `iot_class: cloud_polling`. **It speaks to AWS, not to the device IP.** The integration never opens a connection to the device's LAN address — every command and poll goes to `https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod`.

Mads has enabled **"Allow local commands"** in the Gemstone app and the controller is at `192.168.1.238`. This "Allow local commands" feature suggests the device MAY also accept direct HTTP commands on the LAN. **But that local protocol is not documented anywhere in the reference implementation.** No local endpoint URL, no local auth, no local payload schema appears in `pygemstone` or `hass-gemstone`.

### Recommended Hubitat path

**Option A — Cloud API (documented, works today):** Hubitat implements the AWS REST API described below. Requires internet access; add a Cognito token-refresh step. State lag is ~30–60 s (polled cloud). This is fully implementable from this spec alone.

**Option B — Local API (unknown, fastest for LAN):** To discover the local API Mads must sniff traffic between the Gemstone app and `192.168.1.238` while sending commands. Use the Gemstone app on a phone connected to a WireGuard/mitmproxy proxy, enable "Allow local commands," and capture the HTTP calls to the device's IP. Until that capture exists the local API is unknown.

**This spec documents Option A completely. Option B is flagged in Unverified Assumptions.**

---

## Connection (Cloud API)

- **Transport:** HTTPS, TLS 1.2+
- **Base URL:** `https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod`
- **Port:** 443 (standard HTTPS)
- **Auth:** AWS Cognito User Pool SRP — results in a `Bearer` JWT access token sent as `Authorization: Bearer {access_token}` on every request
- **Auth provider:** Cognito User Pool `us-west-2_rr5lY7Etr`, App Client `2647t144niotrl53vvru0ivno7`, region `us-west-2`
- **Discovery:** None required. User provides email + password. No mDNS or SSDP.
- **Timeout:** 30 s recommended (reference default)
- **User-Agent:** Reference uses `Dart/3.9 (dart:io)` (official app); pygemstone uses `pygemstone/0.0.1`

---

## Authentication Flow

The app uses **AWS Cognito USER_SRP_AUTH** (Secure Remote Password). No plaintext password ever crosses the wire to the Gemstone backend.

### Step 1 — SRP login (Cognito, not Gemstone)

This is handled by the Cognito SDK / `pycognito` library. In Groovy/Hubitat you would call the Cognito `InitiateAuth` API directly:

```
POST https://cognito-idp.us-west-2.amazonaws.com/
Headers:
  Content-Type: application/x-amz-json-1.1
  X-Amz-Target: AmazonCognitoIdentityProvider.InitiateAuth

Body:
{
  "AuthFlow": "USER_SRP_AUTH",
  "ClientId": "2647t144niotrl53vvru0ivno7",
  "AuthParameters": {
    "USERNAME": "user@example.com",
    "SRP_A": "<SRP public value A>"
  }
}
```

This involves multiple SRP challenge-response rounds (standard Cognito SRP). The final result is a `TokenSet`:

```json
{
  "AccessToken": "eyJ...",
  "IdToken": "eyJ...",
  "RefreshToken": "...",
  "ExpiresIn": 3600
}
```

**For Hubitat:** SRP math is non-trivial in Groovy. Easiest path is to implement `USER_PASSWORD_AUTH` (plaintext) if the Cognito pool allows it — worth testing against `2647t144niotrl53vvru0ivno7` with `AuthFlow: "USER_PASSWORD_AUTH"`. If the pool blocks it, you'll need a SRP implementation or a local proxy helper.

### Step 2 — Use the access token

Every Gemstone REST call includes:
```
Authorization: Bearer {AccessToken}
Accept: application/json
Content-Type: application/json   (for PUT requests)
```

### Step 3 — Token refresh

Access tokens expire in 3600 s. Refresh using:
```
POST https://cognito-idp.us-west-2.amazonaws.com/
Headers:
  Content-Type: application/x-amz-json-1.1
  X-Amz-Target: AmazonCognitoIdentityProvider.InitiateAuth

Body:
{
  "AuthFlow": "REFRESH_TOKEN_AUTH",
  "ClientId": "2647t144niotrl53vvru0ivno7",
  "AuthParameters": {
    "REFRESH_TOKEN": "{refresh_token}"
  }
}
```

Response contains new `AccessToken` + `IdToken` (refresh token is reused).

---

## Endpoints

All paths are relative to `https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod`.

---

### 1. List Home Groups

Used during setup to discover the user's `homegroupId`.

- **Method:** GET
- **Path:** `/homegroup/list`
- **Query params:** none
- **Request body:** none
- **Response body:**
```json
{
  "data": [
    {
      "id": "hg-uuid",
      "name": "My Home",
      "role": "owner",
      "scannedDeviceIds": {},
      "homegroupUserIds": ["uid1"],
      "createdAt": 1700000000
    }
  ]
}
```
- **Status:** 200 on success
- **Used by Hubitat for:** initial setup — get `homegroupId` to pass to device list

---

### 2. List Devices

Returns the physical controllers registered to a home group.

- **Method:** GET
- **Path:** `/homegroup/devices`
- **Query params:** `homegroupId={hg-uuid}`
- **Request body:** none
- **Response body:**
```json
{
  "data": [
    {
      "id": "device-uuid",
      "name": "Front House",
      "homegroupId": "hg-uuid",
      "firmware": "1.2.3",
      "disconnectReason": null,
      "lastUpdatedAt": 1700000000
    }
  ]
}
```
- **Status:** 200 on success
- **Used by Hubitat for:** initial setup — get `deviceId` for subsequent control calls

---

### 3. Get Device State (poll endpoint)

The primary state poll. Returns current on/off state and the playing pattern.

- **Method:** GET
- **Path:** `/deviceControl/currentlyPlaying`
- **Query params:** `deviceOrGroupId={device-uuid}` (also accepts a device-group UUID)
- **Request body:** none
- **Response body:**
```json
{
  "data": {
    "id": "device-uuid",
    "onState": true,
    "lastUpdatedAt": 1700000000,
    "pattern": {
      "id": "pattern-uuid",
      "name": "Twinkle Red",
      "colors": [16711680, 0, 0],
      "animation": "twinkle",
      "brightness": 200,
      "speed": 128,
      "direction": 0,
      "backgroundColor": 0,
      "referencePatternId": null
    }
  }
}
```
- **Status:** 200 on success
- **Known lag:** Cloud state is 30–60 s behind after a command. The HA integration uses optimistic local updates to work around this.
- **Used by Hubitat for:** `Switch` (on/off), `SwitchLevel` (brightness), `LightEffects` (pattern name)

---

### 4. Turn On / Turn Off

Sets the device power state.

- **Method:** PUT
- **Path:** `/deviceControl/onState`
- **Query params:** `deviceOrGroupId={device-uuid}`
- **Request headers:** `Content-Type: application/json`
- **Request body:**
```json
{ "onState": true }
```
  or
```json
{ "onState": false }
```
- **Response body:**
```json
{
  "data": {
    "txId": "some-transaction-uuid"
  }
}
```
- **Status:** 200 on success
- **Notes:** `txId` is returned but the reference implementation ignores it (no ack polling).
- **Used by Hubitat for:** `Switch.on()`, `Switch.off()`

---

### 5. Play Pattern (brightness + color + animation)

Sends a full pattern object to the device. This is the one call that controls brightness, colors, animation, speed, and direction simultaneously.

- **Method:** PUT
- **Path:** `/deviceControl/play/pattern`
- **Query params:** `deviceOrGroupId={device-uuid}`
- **Request headers:** `Content-Type: application/json`
- **Request body:**
```json
{
  "pattern": {
    "id": "pattern-uuid",
    "name": "Twinkle Red",
    "colors": [16711680, 0, 0],
    "animation": "twinkle",
    "brightness": 200,
    "speed": 128,
    "direction": 0,
    "backgroundColor": 0,
    "referencePatternId": null
  }
}
```
- **Pattern field types:**
  | Field | Type | Notes |
  |---|---|---|
  | `id` | string UUID | pattern ID from catalogue; or any UUID for a new pattern |
  | `name` | string | human name |
  | `colors` | array of int | ARGB-packed 32-bit integers (e.g. `0xFF0000` = red). List length = number of active colors in pattern |
  | `animation` | string | animation name (see animations list below) |
  | `brightness` | int | 0–255 |
  | `speed` | int | 0–255 (128 = mid) |
  | `direction` | int | 0 or 1 |
  | `backgroundColor` | int | ARGB int; 0 = black/off background |
  | `referencePatternId` | string or null | links to a source pattern in Gemstone catalogue |

- **Response body:**
```json
{
  "data": {
    "txId": "some-transaction-uuid"
  }
}
```
- **Status:** 200 on success
- **Critical pattern behavior:** The HA integration always echoes back the **full raw payload** received from `currentlyPlaying` when changing just brightness. It does not send a partial update — it sends the entire pattern object with only `brightness` changed. **Unknown fields in the raw payload are preserved.**
- **Used by Hubitat for:** `SwitchLevel.setLevel()` (brightness), `ColorControl` (colors), `LightEffects` (animation)

---

### 6. Get Account Profile

Returns the signed-in user's account info.

- **Method:** GET
- **Path:** `/account/profile`
- **Query params:** none
- **Request body:** none
- **Response body:**
```json
{
  "data": {
    "id": "user-uuid",
    "username": "johndoe",
    "email": "john@example.com",
    "emailOptIn": false,
    "cancelledDeletion": false,
    "scannedDeviceIds": {},
    "createdAt": 1700000000,
    "lastUpdatedAt": 1700000000
  }
}
```
- **Status:** 200 on success
- **Used by Hubitat for:** optional — not needed for device control

---

### 7. List Home Group Users

- **Method:** GET
- **Path:** `/homegroup/users`
- **Query params:** `homegroupId={hg-uuid}`
- **Request body:** none
- **Response body:** `{ "data": [ { "userId", "homegroupId", "homegroupName", "role", "invitationStatus", "username", "email", "createdAt", "lastUpdatedAt" } ] }`
- **Status:** 200
- **Used by Hubitat for:** not needed for device control

---

### 8. List Invitations

- **Method:** GET
- **Path:** `/homegroup/invitation`
- **Query params:** `invitationStatus=pending`
- **Request body:** none
- **Response body:** `{ "data": [] }` (empty list observed; schema unknown)
- **Status:** 200
- **Used by Hubitat for:** not needed

---

### 9. List Device Groups

Multi-device zones.

- **Method:** GET
- **Path:** `/deviceGroup/list`
- **Query params:** `homegroupId={hg-uuid}`
- **Request body:** none
- **Response body:** `{ "data": [] }` (empty list observed; schema unknown)
- **Status:** 200
- **Notes:** `deviceOrGroupId` on endpoints 3–5 can accept a device GROUP id here, not just a single device id. Useful for controlling all lights in a zone at once.
- **Used by Hubitat for:** potential zone control support

---

### 10. List Folders

User's pattern folders (containers for saved patterns).

- **Method:** GET
- **Path:** `/folders/list`
- **Query params:** none
- **Request body:** none
- **Response body:**
```json
{
  "data": [
    {
      "folderId": "folder-uuid",
      "name": "Christmas",
      "icon": "🎄",
      "ownerId": "user-uuid",
      "gemstoneManaged": false,
      "referenceFolderId": null,
      "backgroundColor": 16711680,
      "hidden": false,
      "createdAt": 1700000000,
      "lastUpdatedAt": 1700000000
    }
  ]
}
```
- **Status:** 200
- **Used by Hubitat for:** building pattern/scene picker

---

### 11. List Folder Patterns (paginated)

All user-saved patterns across all folders. Used to build the pattern catalogue.

- **Method:** GET
- **Path:** `/folders/pattern/list`
- **Query params:** `page={1-based int}`
- **Request body:** none
- **Response body:**
```json
{
  "data": [
    {
      "id": "fp-uuid",
      "folderId": "folder-uuid",
      "ownerId": "user-uuid",
      "patternData": {
        "id": "pattern-uuid",
        "name": "Twinkle Red",
        "colors": [16711680],
        "animation": "twinkle",
        "brightness": 200,
        "speed": 128,
        "direction": 0,
        "backgroundColor": 0,
        "referencePatternId": null
      },
      "referencePatternId": null,
      "referenceFolderId": null,
      "isFavorite": false,
      "hidden": false,
      "gemstoneManaged": false,
      "createdAt": 1700000000,
      "lastUpdatedAt": 1700000000
    }
  ]
}
```
- **Status:** 200; empty `data: []` signals last page
- **Pagination:** call repeatedly, incrementing `page`, until response returns empty `data`
- **Used by Hubitat for:** `LightEffects` command list (pattern names → effects)

---

### 12. Save / Update Folder

- **Method:** PUT
- **Path:** `/folders/save`
- **Query params:** `folderId={folder-uuid}`
- **Request body:** full Folder object (see Folder schema above; must include `folderId`, `ownerId`, `name`, `icon`, `backgroundColor`, `isSynchronized`, `createdAt`, `newFolder`)
- **Response body:** `{ "data": { ...Folder object... } }`
- **Status:** 200
- **Used by Hubitat for:** not needed for driver

---

### 13. List Swatches

Named color palettes.

- **Method:** GET
- **Path:** `/swatches/list`
- **Query params:** none
- **Response body:** `{ "data": [ { "id", "name", "ownerId", "swatchesColorData": [ { "color": int, "name": str } ], "createdAt", "lastUpdatedAt" } ] }`
- **Status:** 200
- **Used by Hubitat for:** optional color palette support

---

### 14. List Downloadable Folders (paginated)

Gemstone-curated pattern folders available for download.

- **Method:** GET
- **Path:** `/downloads/folders/listGemstoneManaged`
- **Query params:** `page={1-based int}`
- **Response body:** `{ "data": [ { "id", "name", "folderName", "icon", "category", "uploaderId", "downloads", "backgroundColor", "badge", "createdAt", "lastUpdatedAt", "approvedAt" } ] }`
- **Status:** 200; empty `data: []` = last page
- **Used by Hubitat for:** optional — fetching community patterns

---

### 15. List Downloadable Patterns (paginated)

- **Method:** GET
- **Path:** `/downloads/folders/pattern/listGemstoneManaged`
- **Query params:** `page={1-based int}`
- **Response body:** `{ "data": [ { "id", "patternData": {Pattern}, "downloadableFolderId", "category", "patternName", "uploaderId", "downloads", "badge", "createdAt", "lastUpdatedAt", "approvedAt" } ] }`
- **Status:** 200
- **Used by Hubitat for:** optional

---

### 16. Get Events / Autopilot Settings

Daily on/off schedule and subscribed holiday categories.

- **Method:** GET
- **Path:** `/events/settings`
- **Query params:** `homegroupId={hg-uuid}`
- **Response body:**
```json
{
  "data": {
    "homegroupId": "hg-uuid",
    "categoryIds": ["holiday-uuid"],
    "deviceIds": ["device-uuid"],
    "setupYet": true,
    "allowStaticPatterns": true,
    "allowAnimatedPatterns": true,
    "schedule": {
      "onTime": "sunset",
      "offTime": "23:00",
      "onOffsetInMinutes": 0,
      "offOffsetInMinutes": 0
    },
    "createdAt": 1700000000,
    "lastUpdatedAt": 1700000000
  }
}
```
- **Status:** 200
- **Used by Hubitat for:** not needed for basic driver

---

### 17. List Subscribed Events (paginated)

- **Method:** GET
- **Path:** `/events/listSubscribed`
- **Query params:** `homegroupId={hg-uuid}&page={0-based int}`
- **Response body:** `{ "data": [ { SubscribedEvent with staticPatterns/animatedPatterns/selectedPattern } ] }`
- **Status:** 200; empty `data` = last page
- **Note:** page is 0-based here (not 1-based like other paginated endpoints)
- **Used by Hubitat for:** not needed for basic driver

---

### 18. List Event Categories

All holiday/sport/event categories available for autopilot.

- **Method:** GET
- **Path:** `/events/listCategories`
- **Query params:** none
- **Response body:** `{ "data": [ { "id", "name", "description", "group", "icon", "backgroundColor", "suggested" } ] }`
- **Status:** 200
- **Used by Hubitat for:** not needed

---

### 19. List Timers

Scheduled on/off timers with optional pattern.

- **Method:** GET
- **Path:** `/timer/listByHomegroup`
- **Query params:** `homegroupId={hg-uuid}`
- **Response body:**
```json
{
  "data": [
    {
      "id": "timer-uuid",
      "name": "Evening On",
      "homegroupId": "hg-uuid",
      "assigneeId": "device-uuid",
      "enabled": true,
      "timerData": {
        "timerType": "daily",
        "onTime": "19:00",
        "offTime": "23:00"
      },
      "timerPatternData": {
        "pattern": { ...Pattern object... }
      },
      "txId": "",
      "createdAt": 1700000000,
      "lastUpdatedAt": 1700000000
    }
  ]
}
```
- **Status:** 200
- **Used by Hubitat for:** not needed (Hubitat has its own scheduler)

---

### 20. List Announcements

In-app marketing/informational announcements.

- **Method:** GET
- **Path:** `/announcements`
- **Query params:** none
- **Response body:** `{ "data": [ { "id", "title", "descriptionText", "icon", "startDate", "endDate", ... } ] }`
- **Status:** 200
- **Used by Hubitat for:** not needed

---

### 21. AppSync GraphQL endpoint (EXPERIMENTAL / NOT IN USE)

- **HTTP endpoint:** `https://uaa3jxaxnvghha5qeyb254furu.appsync-api.us-west-2.amazonaws.com/graphql`
- **WebSocket endpoint:** `wss://uaa3jxaxnvghha5qeyb254furu.appsync-realtime-api.us-west-2.amazonaws.com/graphql`
- **Status:** The official iOS app was captured in mitmproxy and **never opened a GraphQL connection**. Only two unauthenticated `GET /ping` calls were observed. AppSync auth (if any) is unknown — all four Cognito JWT modes tested returned 401.
- **Used by Hubitat for:** Do not implement. REST polling is the only confirmed path.

---

## Pattern Color Encoding

Colors in the `colors` array are ARGB-packed 32-bit integers:
- `0xFF0000` (16711680) = red
- `0x00FF00` (65280) = green
- `0x0000FF` (255) = blue
- `0xFFFFFF` (16777215) = white
- `0x000000` (0) = off/black

The `backgroundColor` field works the same way. It sets the "base" color that non-active pixels show (typically 0 = off).

Known animation name strings (from live captures / reference):
- `"motionless"` — static, no animation
- `"twinkle"` — random twinkle
- `"chase"` — chasing sequence
- `"fade"` — crossfade
- `"wave"` — wave effect

⚠️ **Unverified:** The full animation name catalogue is not enumerated in the reference code. `animation` is stored and echoed as a raw string.

---

## State Polling

- **Method:** REST polling only — no push/WebSocket/SSE confirmed in real-device captures
- **Endpoint:** `GET /deviceControl/currentlyPlaying?deviceOrGroupId={id}`
- **HA poll interval:** 30 seconds (`DEFAULT_SCAN_INTERVAL = timedelta(seconds=30)`)
- **Recommended Hubitat poll interval:** 30 seconds
- **State lag:** After sending a command, the cloud's `currentlyPlaying` endpoint returns **stale state for approximately 30–60 seconds**. The HA integration works around this with optimistic local state updates (it updates local state immediately and lets the next poll reconcile). Hubitat driver should do the same.
- **State fields returned per device:**
  - `id` (device UUID)
  - `onState` (boolean)
  - `lastUpdatedAt` (unix timestamp)
  - `pattern` object (full pattern, or null if none playing):
    - `id`, `name`, `colors`, `animation`, `brightness`, `speed`, `direction`, `backgroundColor`, `referencePatternId`

---

## Quirks & Gotchas

1. **Cloud lag is real and significant.** After any command (on/off or play/pattern), the `/deviceControl/currentlyPlaying` endpoint returns the *previous* state for 30–60 s. If Hubitat polls immediately after a command, it will read stale state. Implement optimistic state: update local device state immediately on command send, then let the next scheduled poll reconcile.

2. **Auth is Cognito SRP by default.** The reference uses `pycognito` to handle the SRP math. In Groovy, SRP is non-trivial. Test whether `USER_PASSWORD_AUTH` works against client ID `2647t144niotrl53vvru0ivno7` first — if the Cognito pool allows it, that's much simpler to implement.

3. **play/pattern requires a FULL pattern object.** There is no partial-update API. To change only brightness, you must: (a) read the current pattern from `currentlyPlaying`, (b) mutate the `brightness` field, (c) send the full pattern back via `play/pattern`. Do NOT construct a minimal pattern object — the device expects the full raw payload.

4. **colors is a list of ARGB ints.** Multiple colors in the list defines a multi-color pattern. Single-element list = solid color. The number of elements appears to be variable (no documented max; reference code uses `list(payload.get("colors", []))`).

5. **deviceOrGroupId on control endpoints accepts a device group ID too.** If the user has device groups configured, controlling the group ID sends the command to all devices in the group simultaneously.

6. **No local API documented.** The integration is entirely cloud-routed. The "Allow local commands" option in the Gemstone app may enable a direct local HTTP API on the device but this is NOT in the reference source. Needs traffic capture to confirm (see Unverified Assumptions #1).

7. **Access tokens expire in ~60 minutes.** Hubitat driver must store the refresh token and re-obtain the access token transparently. The refresh token does NOT expire (based on reference code; this is Cognito's default — months to years).

8. **`txId` in command responses is ignored.** There is no ack polling after commands. The integration treats commands as fire-and-forget at the API level (then applies optimistic state locally).

9. **Pagination is inconsistent:** `/events/listSubscribed` uses 0-based page; all other paginated endpoints use 1-based page.

10. **AppSync credentials are public constants** (not secrets). `COGNITO_USER_POOL_ID` and `COGNITO_CLIENT_ID` are in every install of the iOS app — recoverable from any packet capture or decompile. The user's email/password are the only secrets.

---

## Hubitat Capability Mapping

| Hubitat Capability | Hubitat Command | Gemstone API Call | Notes |
|---|---|---|---|
| `Switch` | `on()` | `PUT /deviceControl/onState {onState:true}` | |
| `Switch` | `off()` | `PUT /deviceControl/onState {onState:false}` | |
| `SwitchLevel` | `setLevel(level)` | Read current pattern → mutate `brightness` → `PUT /deviceControl/play/pattern` | level 0–100 → brightness 0–255 |
| `ColorControl` | `setColor(map)` | Convert hue/sat/lev to ARGB → set `colors[0]` + `brightness` → `PUT /deviceControl/play/pattern` | set animation to `"motionless"` for solid color |
| `ColorControl` | `setHue(hue)` | Same as setColor with current sat | |
| `ColorControl` | `setSaturation(sat)` | Same as setColor with current hue | |
| `LightEffects` | `setEffect(effectNumber)` | Map effect number → named pattern from catalogue → `PUT /deviceControl/play/pattern` | Build effects list from `/folders/pattern/list` |
| Custom: `playPattern` | `playPattern(patternId)` | `PUT /deviceControl/play/pattern` with pattern from catalogue by ID | |
| Custom: `setAnimation` | `setAnimation(name)` | Mutate `animation` on current pattern → `PUT /deviceControl/play/pattern` | |
| `Polling` / `Refresh` | `refresh()` | `GET /deviceControl/currentlyPlaying` | |
| `ColorTemperature` | — | **Not supported** | No white temperature channel found in reference |

### Recommended minimal implementation for Tank

For the first driver version, implement only:
1. Auth (Cognito) + token refresh
2. Setup: `homegroups()` → `devices()` → store `deviceId`
3. `Switch`: on/off via `/deviceControl/onState`
4. `SwitchLevel`: brightness via read-modify-write pattern
5. `Refresh`: poll via `/deviceControl/currentlyPlaying` every 30 s
6. `LightEffects`: load pattern list from `/folders/pattern/list`, expose as named effects, send via `/deviceControl/play/pattern`

`ColorControl` is possible but requires ARGB conversion math; defer to v2.

---

## Unverified Assumptions

1. **Local API existence:** The Gemstone app's "Allow local commands" option likely enables a local HTTP server on the device (port unknown; probably 80 or 8080). This is NOT documented in the reference. To verify: set up mitmproxy/Wireshark on the LAN and capture traffic from the app to `192.168.1.238` while sending commands. If a local API exists, it would let Hubitat skip Cognito auth entirely.

2. **`USER_PASSWORD_AUTH` availability:** The reference uses SRP auth. If the Cognito pool allows `USER_PASSWORD_AUTH`, Hubitat can authenticate with a simple HTTPS POST to `cognito-idp.us-west-2.amazonaws.com` with `{"AuthFlow":"USER_PASSWORD_AUTH","ClientId":"2647t144niotrl53vvru0ivno7","AuthParameters":{"USERNAME":"...","PASSWORD":"..."}}`. This has NOT been confirmed against the real backend.

3. **Color encoding:** Colors are stored as ARGB ints but the Alpha channel behavior is unknown. Setting `colors = [0xFF0000]` (no alpha byte) vs `[0xFFFF0000]` (alpha=255) may differ. Reference code stores them as raw ints from the API without masking — echo back unchanged.

4. **Animation name catalogue:** The full set of valid `animation` strings is not documented. The reference treats it as an opaque string. Known values from pattern names in the UI: `motionless`, `twinkle`, `chase`, `fade`, `wave`. Others likely exist. Mads should capture a live `currentlyPlaying` response while switching animations in the app.

5. **`play/pattern` while device is off:** Unknown whether sending a pattern command also implicitly turns the device on, or whether `onState: true` must be sent first. The HA integration always calls `turn_on()` before `play_pattern()` if `on_state` is false — suggesting the device does NOT auto-power-on from a pattern command alone.

6. **State lag value:** "30–60 s" cloud lag is stated in the HA code comments (`# the cloud's currentlyPlaying endpoint will return stale state for ~30-60s`). This was observed empirically during development but exact timing may vary by device firmware version.

7. **Brightness range:** The HA code clips brightness to `max(0, min(255, int(data.pattern.brightness)))` — treating 0–255 as the valid range. Behavior at `brightness = 0` (does device turn off? go dark but stay "on"?) is unconfirmed.

8. **`deviceOrGroupId` with group IDs:** Accepted by control endpoints per code inspection but behavior with a real device group has not been confirmed.


---

# Documentation Conventions for hubitat-drivers

**Date:** 2026-05-16
**Author:** Link (DevRel)
**Status:** Proposed

---

## Executive Summary

This proposal standardizes documentation for the `hubitat-drivers` repo, based on a survey of 5 well-regarded Hubitat repositories. Recommend per-driver README files inside driver folders, optional HPM manifests, a lightweight top-level README as index, and MIT licensing.

---

## Survey Findings

| Repository | Owner | Structure | Per-Driver README | Top-Level README | HPM Manifest | License |
|-----------|-------|-----------|------------------|------------------|--------------|---------|
| **Hubitat** | bptworld | Drivers in named subfolders | Minimal (links to Docs/) | Yes (index) | Yes (central `repositories.json`) | MIT |
| **HubitatPublic** | hubitat | Flat + subfolders (apps, examples) | No | Minimal (just title) | No | MIT |
| **konnected-security** | konnected-io | Feature-organized (firmware, scripts, src) | No (docs in wiki/help site) | Comprehensive | No | Apache 2.0 |
| **mi_connector** | fison67 | Mixed (devicetypes, smartapps, dth) | No (doc/ folder) | Yes (detailed) | No | MIT |
| **Hubitat (Elevation)** | Official examples | Flat structure in example folders | No | Minimal | Optional per-package | Various |

### Key Observations

1. **Folder Structure:** Most multi-driver repos use **named subfolders per driver** (e.g., `Drivers/Asthma Forecaster/`). This scales well and keeps code isolated.
2. **Per-Driver README:** Only `bptworld/Hubitat` has per-driver READMEs, but they're minimal. Most point to centralized docs.
3. **Top-Level README:** All repo have one; ranges from trivial (hubitat/HubitatPublic) to detailed (fison67). Consensus: use it as an **index** + install quickstart.
4. **HPM Manifests:** Common for packages intended for HPM distribution. Not required; can be added per driver.
5. **Licensing:** MIT is most common for Hubitat drivers (4/5 surveyed). Apache 2.0 also used.
6. **`.gitignore`:** Most repos include OS files (.DS_Store), editor files (.vscode, .idea), and IDE backups.

---

## Recommended Convention for hubitat-drivers

### Folder Structure

```
hubitat-drivers/
├── README.md                    # Top-level index + install quickstart
├── .gitignore                   # OS, IDE, Hubitat-specific files
├── LICENSE                      # MIT recommended
├── CHANGELOG.md                 # Single log (optional per-driver supplements)
└── drivers/
    ├── gemstone-lights/
    │   ├── gemstone-lights.groovy
    │   ├── README.md            # Setup, preferences, examples
    │   ├── packageManifest.json  # Optional if using HPM
    │   └── CHANGELOG.md          # Optional per-driver log
    └── [future-drivers]/
        ├── [driver-name].groovy
        ├── README.md
        └── packageManifest.json (optional)
```

### Top-Level README

- **1 paragraph** explaining what the repo is and who it's for
- **Table of drivers** with links to per-driver READMEs
- **Generic Hubitat install instructions** (the standard pattern)
- **Compatibility note** (hub models, platform version)
- **License, author, contributing notes** (brief)

**Do NOT include per-driver setup details here.** That's the job of the per-driver README.

### Per-Driver README

Each driver's `README.md` includes:
- What the driver does
- Hardware / integrations it supports
- Preferences and their defaults
- Installation steps (if driver-specific beyond standard pattern)
- Troubleshooting or examples (if helpful)
- Any links to external docs or community threads

### HPM Manifests

- Create `packageManifest.json` **only if the driver will be distributed via HPM**
- Place it in the driver folder
- Not required for initial release; can be added later

### Licensing

**Recommendation: MIT License**

- Most permissive for end users
- Standard in Hubitat community
- File: `LICENSE` at repo root (single file for all drivers)

### .gitignore

Keep it focused:
- OS files: `.DS_Store`, `Thumbs.db`, `.Trashes`
- Editor files: `.vscode/`, `.idea/`, `*.swp`, `*~`
- IDE backups: `*.backup`, `*.bak`
- Squad runtime: already in place (orchestration logs, etc.)

### Changelog

- Optional single `CHANGELOG.md` at repo root if releases are coordinated
- Can also include per-driver `CHANGELOG.md` if drivers version independently
- GitHub Releases are fine as an alternative

---

## Rationale

1. **Per-driver folders** keep code and docs together as drivers scale
2. **Minimal top-level README** respects reader time and makes the index clear
3. **Per-driver README** empowers users to set up without leaving the driver folder
4. **MIT License** is familiar to the Hubitat community
5. **Lightweight .gitignore** prevents IDE/OS noise without being overly strict

---

## Next Steps

1. Apply this structure to the first driver (Gemstone Lights)
2. Write the per-driver README once Trinity finalizes the folder path and Cypher's protocol is settled
3. If HPM distribution is planned, add per-driver `packageManifest.json` before first release

---

## Questions / Discussion

- Should we version drivers independently or as a suite? (Affects changelog strategy)
- Will HPM distribution be a goal? (Affects whether `packageManifest.json` is required)
- Any Hubitat-specific preferences or sections we should document? (Per driver's integration)


---

### 2026-05-16T14:45:13-07:00: Per-driver README structure + MIT license

**By:** Link

**What:** Delivered `drivers/gemstone-lights/README.md` and `LICENSE` (MIT) for the hubitat-drivers repo. The per-driver README follows a clear pattern: what it does → status banner → capabilities → preferences table → install steps → current limitations → discovering the local API → testing link → troubleshooting → credits. The LICENSE is canonical MIT (2026, Mads Kristensen) at repo root.

**Why:**
- **Status banner:** Users immediately see this is v0.1.0 and why commands are stubbed (local API not yet sniffed). Honest scaffolding prevents misinstalls and support confusion.
- **Preferences table:** Matches the driver's `preferences {}` block. New users know exactly what each setting does and its default value.
- **Installation steps:** 3-step flow (add driver → create device → set IP). Matches the Hubitat IDE standard and the community conventions surveyed (bptworld, konnected, etc.).
- **Current Limitations + Discovering the Local API:** Educates users on the intentional tech choices (cloud API not implemented, local API pending). Invites collaboration — users understand the next milestone.
- **Testing link:** Delegates functional verification to Switch's `TESTING.md`; keeps the README focused on setup, not test procedures.
- **Credits:** Acknowledges `sslivins/hass-gemstone` and `sslivins/pygemstone` (cloud API reverse-engineering), establishing credibility and nod to prior art.
- **MIT License:** Most permissive for Hubitat community; single file at repo root scales to future drivers.

**References:**
- `link-doc-conventions.md` — survey findings + recommendation for per-driver README structure
- `trinity-gemstone-driver-design.md` — driver capabilities and preferences (source of truth for what to document)
- `switch-test-plan-notes.md` (future) — TESTING.md location + link target


---

### 2026-05-16T14:08:16-07:00: Manual test plan for Gemstone Lights driver

**By:** Switch
**Context:** Hubitat driver testing approach for LAN-based HTTP polling drivers.

---

**What's covered:**

The manual test plan (`drivers/gemstone-lights/TESTING.md`) exercises the full driver lifecycle: installation, preference changes, invalid input handling, optimistic state updates with polling reconciliation, all Hubitat capabilities (Switch, SwitchLevel, ColorControl, LightEffects, Refresh, Initialize), network failure recovery, hub reboot, uninstall cleanup, and debug log auto-off. Each of the 13 test sections includes step-by-step instructions, expected logs, and what to watch for in the Hubitat dashboard and IDE logs. The plan includes a "watch list" for common failures: stack traces, HTTP timeouts, unexpected response codes, and state desync.

**What's deferred:**

Protocol-level tests (HTTP endpoints, status codes, authentication, payload formats, error responses) are deferred until **Cypher completes local API discovery**. Zone-based addressing tests (per-segment control, parent/child split) are deferred until confirmed in the Gemstone API. The test plan includes a clear "When to Update" section describing what new tests to add once those discoveries are complete.

**Testing philosophy:**

Hubitat drivers are tested manually via the device UI and logs view, not by automated CI/CD pipelines. The tests are designed so Mads (or any user) can run them locally against the real 192.168.1.238 Gemstone controller once the driver is installed on the hub. Each test is human-readable and describes what to observe in the logs and device tile, making failures obvious and reproducible.

**Key Hubitat gotchas accounted for:**

- **Optimistic updates:** Tests verify that Hubitat updates state immediately upon command send, then reconciles on the next poll.
- **Hub startup:** `initialize()` is called automatically on hub reboot, re-registering the polling schedule.
- **Uninstall cleanup:** `uninstalled()` must properly deregister schedules to avoid orphaned tasks.
- **Debug log timeout:** Tests account for the 30-minute auto-disable of debug logging.
- **Network resilience:** Tests ensure the driver doesn't crash or hang on connection failure, and recovers cleanly when network is restored.


### 2026-05-16T15:13:32-07:00: v0.1.0 Behavior Confirmation
**By:** Tank
**Status:** Ready for team review

Confirmed: v0.1.0 emits events without HTTP (no network traffic). Every command handler follows the pattern: log.info → sendEvent (optimistic state update) → sendCommand (stub, no-op).

**Log entry → code path table:**
| Log entry | Method | Line(s) |
|---|---|---|
| `Gemstone switch → on` | `on()` → `infoLog` | 132 |
| `Gemstone Lights v0.1.0 preferences updated` | `updated()` → `log.info` | 78 |
| `Gemstone color → hue=0 sat=91 level=97` | `setColor()` → `infoLog` | 163 |
| `Gemstone effect → 1` | `setEffect()` → `infoLog` | 185 |
| `Gemstone switch → off` | `off()` → `infoLog` | 138 |
| `Gemstone level → 50` | `setLevel()` → `infoLog` | 149 |

**"On after preferences saved" — no driver-side explanation exists.** Three plausible non-driver causes: (1) Controller was already on. (2) Companion automation independently sent on command. (3) Controller boot/reconnect resume triggered by network disruption.

**Protocol data for v0.2.0:** Cypher's cloud API spec is fully documented. We can ship v0.2.0 using cloud API without additional protocol research. Local API remains blocked pending mitmproxy capture.

**Recommendations for v0.1.0 polish:** Add `log.warn` at warn level (always visible, not gated on logEnable) inside sendCommand() before return with message: `"[${device.displayName}] v${DRIVER_VERSION} SCAFFOLD — '${params.action}' was NOT sent to the device (HTTP endpoint not yet wired). This is expected for v0.1.0."` **Await Mads' go-ahead before implementing.**

---

### 2026-05-16T15:13:32-07:00: Gemstone Local API Discovery Playbook
**By:** Cypher (Integration / Protocol)
**Status:** Ready for Mads to execute
**Objective:** Capture LAN traffic to reveal the local API protocol that the Gemstone app uses when "Allow local commands" is enabled.

**Summary:** The Gemstone controller at 192.168.1.238 supports "Allow local commands," suggesting a local HTTP server is available on the LAN. Playbook provides five discovery techniques in order of accessibility.

**Recommended sequence:**
1. **Port scan** (PowerShell Test-NetConnection) — 5 min, high yield. Finds listening services on common HTTP ports (80, 8080, 8888, 443, 5353, 22, 23, 3000, 5000, 8000, 8081, 8082, 8090, 9000).
2. **Probe common paths** (curl) — 5 min, medium yield. Tests endpoints like `/`, `/api`, `/api/v1`, `/status`, `/control`, `/lights`.
3. **mDNS discovery** (Bonjour Browser GUI) — 10 min, low-medium yield. Locates advertised services and hostnames.
4. **Wireshark passive capture** (optional if 1–3 fail) — 30 min, low yield without port mirroring.
5. **mitmproxy phone-to-PC capture** (best signal if app uses TLS without pinning) — 45 min, high yield. Phone proxy at PC:8080, install mitmproxy CA cert on phone, trigger Gemstone commands, inspect decrypted flows.

**Pre-flight checklist:**
- Enable "Allow local commands" in Gemstone app
- Confirm controller IP is 192.168.1.238 (ping from PowerShell)
- Phone and PC on same Wi-Fi network
- Phone not on guest VLAN
- Disable VPNs on phone during capture

**What to do with captures:** Save .pcapng (Wireshark) or curl/mitmproxy transcript. Before sharing: scrub Authorization headers, x-amz-* headers, MAC addresses, and personally identifying info. Send to chat or commit to `.squad/research/`.

**Known unknowns:** Local API may not exist (cloud-only); controller may speak non-HTTP protocol; TLS with certificate pinning would block mitmproxy decryption; controller may require authentication; IP may be different; phone and controller may be on different network segments.

---

## Governance

- All meaningful changes require team consensus
- Document architectural decisions here
- Keep history focused on work, decisions focused on direction


---

### 2026-05-16T15:24:15-07:00: User directive — Local API only, no cloud
**By:** Mads Kristensen (via Copilot)
**What:** The Gemstone Lights driver will target the **local LAN protocol only**. The cloud API (AWS Cognito + REST, fully specced by Cypher) is documented but will NOT be implemented. v0.2.0 ships against the local controller at 192.168.1.238 or it does not ship.
**Why:** User preference confirmed mid-session 2026-05-16. Reasons: no cloud creds required, sub-second response, no AWS dependency, simpler driver. Mads has already confirmed the controller exposes a live JSON-speaking HTTP API on port 80 (returned `{"error": "Invalid route."}` to `curl /`). The cloud-mirror path guesses (`/deviceControl/onState`, `/deviceControl/currentlyPlaying`) all returned the same `Invalid route.` error, so the local API does NOT mirror cloud endpoint naming.

**Implications:**
- Tank: do NOT wire Cognito auth or AWS REST calls. All `sendCommand()` / `parse()` work targets the local IP.
- Cypher: protocol discovery now requires either (a) more endpoint guessing informed by `Server:` header / port scan, or (b) mitmproxy capture of the Gemstone app talking to the controller.
- Next concrete asks for Mads: `curl -v http://192.168.1.238/` (headers reveal framework) and a port scan to confirm port 80 is the only listening port.


---

### 2026-05-16T15:24:15-07:00: Scaffold Warn Banner Added to sendCommand()
**By:** Tank (Driver Developer)
**Status:** Implemented in v0.1.1

---

## Exact log.warn line added

```groovy
log.warn "[Gemstone] v${DRIVER_VERSION} SCAFFOLD — '${params.action}' was NOT sent to the device (local HTTP endpoint not yet wired). This is expected until v0.2.0."
```

Inserted as the first executable statement in `sendCommand(Map params)` (line 246 in the updated file), before IP validation guards, so it fires on every command invocation.

---

## Versioning decision: bumped to v0.1.1

**Decision:** Bump from v0.1.0 → v0.1.1 (not stay at v0.1.0).

**Rationale:** This is a user-visible change — a new log line now appears in the Hubitat log that was absent before. A patch bump (0.1.0 → 0.1.1) correctly signals "observable behavior changed without new features." Staying at v0.1.0 would make it harder to confirm which build a user is running when troubleshooting.

**Locations updated consistently (all four):**
1. File-header `* Version:` comment — → 0.1.1
2. `@Field static final String DRIVER_VERSION` — → "0.1.1"
3. Changelog block — new entry: `0.1.1 — 2026-05-16 — Added scaffold transparency warn banner`
4. `packageManifest.json` — top-level `version` and inner driver `version` — both → "0.1.1"

---

## Completion status

This change completes the **"v0.1.0 UX polish"** recommendation captured in Tank's 2026-05-16T15:13:32-07:00 log mapping session. The silent-no-op problem is resolved: with `logEnable=false` (default), users previously saw `switch → on` in their log and had zero indication nothing reached the device. Now every command produces an unconditional `log.warn` making the stub state unmistakable.

---

## Scope directive (permanent)

This driver targets **local API only**. No Cognito/cloud implementation will be added. The controller at 192.168.1.238 is confirmed alive; local endpoint paths are unknown pending probe results. All prior notes about a Cognito v0.2.0 path are superseded and should be disregarded.

---

### 2026-05-16T15:34:12-07:00: User directive — Squad runs LAN probes directly

### 2026-05-16T15:34:12-07:00: User directive — Squad runs LAN probes directly
**By:** Mads Kristensen (via Copilot)
**What:** When Squad has shell access on Mads' machine and the task is a LAN probe / port scan / curl run against the local network (e.g., the Gemstone controller at 192.168.1.238), Squad runs it directly rather than asking Mads to paste commands and report back. This is a process preference, not a scope change.
**Why:** Mads' Copilot CLI runs on his Windows machine and has PowerShell access on his LAN — relaying commands through him adds latency for no benefit. Cypher's interpretive work (deciding what to probe, fingerprinting responses, planning next steps) remains a Cypher task; mechanical shell execution can be inline.


---

### 2026-05-16T15:34:12-07:00: Gemstone Local API Fingerprint — Final Findings

# Gemstone Local API Fingerprint — Final Findings

**Session:** 2026-05-16T15:34:12-07:00
**Agent:** Cypher (Integration / Protocol)
**Status:** Discovery stalled on routing mechanism. Ready for mitmproxy.

---

## A) Canonical Findings Synthesis

### Confirmed Protocol Facts

The Gemstone controller at **192.168.1.238:80** is a **live HTTP API server** with these characteristics:

1. **Single open TCP port:** 80. Scanned 20 alternates (81, 443, 3000–9090, 5353, 7681–7777, 1883) — all closed.
2. **HTTP method restriction:** Only **POST** and **GET** accepted. Server explicitly rejects PUT/HEAD/DELETE with `405 Method Not Allowed: {"error": "Invalid HTTP method. Only POST and GET are supported."}`
3. **No Server header.** Response headers omit `Server:` identifier. `Connection: close` (no keep-alive). Suggests minimal embedded framework or Node.js with header suppression.
4. **OPTIONS times out.** Server never responds to OPTIONS requests (curl verified). May indicate incomplete HTTP/1.1 compliance or intentional method blocking at framework level.
5. **Content-Type enforcement:** Requests must be `application/json`. Form-urlencoded, text/plain, empty body, or non-JSON → `400 "Invalid JSON body format"`.
6. **JSON structure requirement:** Must be a JSON **object** (not array, string, bool, or number at top level).
7. **ALL URL paths return identical 404.** Tested 30+ paths (/, /api, /api/v1/*, /lights/*, /control/*, /status, etc.). Response: `404 {"error": "Invalid route."}` regardless of path. **Routing is NOT in the URL.**
8. **Routing mechanism unknown.** Tested ~70 payload shapes with common routing keys (`action, method, command, cmd, type, event, name, path, route, endpoint, target, op, operation, request, etc.`). All returned `404 "Invalid route."` identically.
9. **Cloud API mirror patterns fail.** Shapes from `pygemstone` cloud client (`{"deviceId":"...","onState":true}`) also return 404.
10. **Custom headers ineffective.** X-Action, X-Command, X-Method, X-Route, X-Path, Authorization Bearer, X-API-Key, API-Key — no impact on response.
11. **Response timing consistent:** 404 errors take ~120–160 ms. Occasional `STATUS:000` curl timeouts are transient; retries yield clean 404. No "almost-recognized" signal detected.

### Interpretation

- **Server is functional and parsing.** It validates HTTP method, Content-Type, and JSON syntax. It's **not dead code.**
- **Routing is opaque.** Either (a) a vendor-specific field name (not discoverable by brute-force), (b) behind a handshake/pairing step not yet triggered, or (c) requires a token/header established during pairing with the Gemstone iOS/Android app.
- **Minimal error surface intentional.** The server provides ZERO diagnostic hints—consistent with security-conscious or vendor-locked firmware.

### What We Don't Know

- **Handshake sequence:** Does the app perform a pairing/discovery exchange before sending commands?
- **Token mechanism:** Is a session token, API key, or capability exchange required?
- **Actual routing format:** What field name or header triggers command dispatch?

---

## B) Mitmproxy Capture Plan for Mads

### Goal
Intercept HTTPS traffic between the Gemstone mobile app and the local controller (and any cloud endpoints) to reveal the routing mechanism and command payload structure.

### Minimum Tooling Installation

**On Windows:**
```powershell
# Install mitmproxy via pip
pip install mitmproxy

# Verify installation
mitmproxy --version
```

**Npcap/Wireshark NOT needed.** mitmproxy decrypts HTTPS at the proxy layer (Certificate Authority interception). Wireshark would only see encrypted traffic.

### Setup: Phone Proxy + Certificate Installation

**Goal:** Route the phone's traffic through Windows mitmproxy to decrypt HTTPS payloads.

**Steps:**

1. **Start mitmproxy on Windows** (run as Administrator):
   ```powershell
   mitmproxy --listen-host 0.0.0.0 --listen-port 8080
   ```
   Note the Windows machine's LAN IP (e.g., `192.168.1.50`). mitmproxy will show `Listening on 0.0.0.0:8080` in the console.

2. **Configure phone proxy:**
   - **iOS:** Settings → Wi-Fi → (connected network) → Configure Proxy → Manual → Server: `192.168.1.50` (your Windows IP), Port: `8080`
   - **Android:** Settings → Wi-Fi → (long-press network) → Modify → Proxy → Manual → Proxy hostname: `192.168.1.50`, Port: `8080`

3. **Install mitmproxy CA certificate on phone:**
   - **iOS:** On phone, open browser, go to `http://mitm.it` → download CA cert → Settings → General → VPN & Device Management → Install profile → Trust the cert
   - **Android:** Browser → `http://mitm.it` → download cert (looks like `.pem` or `.der`) → Settings → Security → Install from storage

4. **⚠️ Cert Pinning Warning:** If Gemstone app uses **certificate pinning** (validates that the server cert matches a hardcoded fingerprint), mitmproxy decryption will **fail immediately** with TLS handshake errors in the mitmproxy console:
   ```
   [clientconnect]
   tls.clienthello
   tls.establish_client_tls_first
   ERROR: 'X509Error'
   ```
   If you see these, the app is pinning certs and mitmproxy cannot decrypt. Report this — we proceed to Fallback C.

### Capture Sequence

**Exact steps:**

1. **Kill the Gemstone app completely** on your phone (force-quit or restart phone if needed).
2. **Start mitmproxy capture** in the Windows terminal:
   ```powershell
   mitmproxy --listen-host 0.0.0.0 --listen-port 8080 -w gemstone-app-capture.flow
   ```
   (The `-w` flag writes flows to a `.flow` file for later export.)
3. **Launch the Gemstone app** on the phone. Wait for it to **find and connect to the controller at 192.168.1.238.** This is where the handshake (if any) happens. Watch mitmproxy console for incoming requests.
4. **Tap "On"** once to turn the light on. Observe the POST/GET request in mitmproxy.
5. **Tap "Off"** once to turn the light off. Observe the second request.
6. **Stop the capture** by pressing `Q` in mitmproxy (then `q` to confirm quit). The `.flow` file will be closed and ready.

### Export and Anonymization

**Export flows to readable format:**
```powershell
mitmweb --listen-host 0.0.0.0 --listen-port 8081 -r gemstone-app-capture.flow
# Then open http://localhost:8081 in browser and export to JSON or view inline
```

Or use command-line dump:
```powershell
mitmdump -r gemstone-app-capture.flow -q --flow-detail 4
```

**Drop the `.flow` file here:**
```
C:\Users\madsk\GitHub\hubitat-drivers\.squad\research\gemstone-app-capture.flow
```

**Before sharing, scrub:**
- Any `Authorization: Bearer ...` tokens
- Any `x-amz-*` AWS headers
- Any `x-api-key` or custom auth headers
- Timestamps or device IDs if they appear sensitive

**⚠️ .squad/research directory should be .gitignored.** Add this to `.gitignore` if not already present:
```
.squad/research/
```

---

## C) Fallback Options (if mitmproxy fails)

### If Certificate Pinning Blocks Decryption

**Option 1: ARP Spoof + Router-Level Capture (Most reliable, most complex)**
- **Prerequisites:** Admin access to router (or ability to SSH into OpenWrt/UniFi box), knowledge of network topology.
- **Method:** Use `arpspoof` (or router port mirroring) to intercept all traffic from the phone and controller. Capture with `tcpdump` on router:
  ```bash
  sudo tcpdump -i <interface> -w gemstone-router.pcap 'host 192.168.1.238 or host <phone-ip>'
  ```
  Transfer `.pcap` to Windows, open in Wireshark. You'll see plain HTTP requests (no decryption needed if local controller uses HTTP). For HTTPS, you're still blocked unless controller uses plaintext.
- **Yield:** High if controller uses plain HTTP locally. Zero if HTTPS + pinned.
- **Effort:** 1–2 hours setup.

### Option 2: Decompile Android APK (Android only, no iOS equivalent)

**Prerequisite:** Android-only. iOS apps cannot be trivially decompiled without Mac + Xcode.

- **Method:** Download APK from Google Play (use `apktool` or `jadx` to extract source). Search for:
  - String literals for IP, port, endpoint paths
  - Constants for API methods or commands
  - Hardcoded bearer tokens or API keys
- **Tools:**
  ```bash
  # Extract APK
  apktool d Gemstone.apk

  # Or decompile to Java-like source
  jadx -d output_dir Gemstone.apk
  ```
- **Yield:** Medium-High. May reveal routing keys or endpoint patterns directly from source.
- **Effort:** 30 min to 1 hour.

### Option 3: tcpdump on Router (no decryption, plain HTTP only)

- **Method:** If controller exposes a plain HTTP endpoint (no HTTPS), SSH into router and run:
  ```bash
  sudo tcpdump -i <interface> -A 'host 192.168.1.238 and tcp port 80'
  ```
  Filter output for human-readable requests.
- **Yield:** Medium if local control is HTTP. Zero if HTTPS or binary protocol.
- **Effort:** 15–30 min if router is accessible.

### Recommended Sequence

1. **Try mitmproxy first** (this doc, 30 min). If cert pinning blocks → go to #2.
2. **If Android: Decompile APK** (30 min, highest signal for source reveal).
3. **If iOS or APK fails: ARP spoof + router capture** (1–2 hours, overkill but thorough).
4. **Last resort: Router tcpdump on port 80** (assume plain HTTP, 15 min).

---

## Next Steps for Mads

1. **Run mitmproxy capture** using steps in **Section B.** Report:
   - mitmproxy console output (any requests captured?)
   - Cert pinning errors (if yes → Fallback C)
   - Payload structure of "On" and "Off" commands
2. **If successful:** Share the scrubbed `.flow` file or transcribed JSON payloads. Cypher will reverse the routing mechanism and advise Tank on endpoint implementation.
3. **If mitmproxy blocked by cert pinning:** Escalate to Fallback C. Priority: APK decompile (Android) or ARP spoof (iOS).

---

## References

- **pygemstone (cloud):** GitHub `sslivins/pygemstone` — cloud endpoints only, no local protocol info.
- **Home Assistant Gemstone forum:** Limited community reverse engineering; no local API documented.
- **Known unknowns:** Gemstone Lights does not publish local API specs. The `"Allow local commands"` app feature exists but is a black box.

---

**Compiled by:** Cypher (Integration / Protocol), 2026-05-16T15:34:12-07:00
**Handoff to:** Mads Kristensen (capture execution) → Tank (endpoint wiring after capture analysis)

---

### 2026-05-16T15:44:56-07:00: SDDP Discovery + Gemstone Control4 Integration Path

**Decision Owner:** Cypher (Integration/Protocol)
**Status:** ACTIONABLE — Next milestone gated on SDDP capture or .c4z extraction

**Breakthrough:** Gemstone broadcasts SDDP (Simple Device Discovery Protocol) every ~5 minutes on multicast UDP 239.255.255.250:1902. This is Control4's standard discovery protocol, and **Gemstone has a published Control4 driver on DriverCentral**. This changes the integration unlock path entirely.

**Key Finding:** The SDDP NOTIFY packet contains fields (especially LOCATION, CONFIG-URL) that may reveal the local control port, config endpoint, or pairing handshake — **solving the "invalid route" mystery on TCP 80**.

**SDDP Protocol (canonical packet format):**
```
NOTIFY * HTTP/1.1
HOST: 239.255.255.250:1902
CACHE-CONTROL: max-age=900
LOCATION: http://192.168.1.5:8080/device.xml
USN: uuid:Control4-C4OS3-2.10.6-XXX
ST: urn:Control4:device:Media_Controller:1
SERVER: Control4/2.10.6 (OS 3.2.2)
```

**Key fields to watch:** LOCATION (URL endpoint), CONFIG-URL (alternate config endpoint), PRIMARY-TOKEN / EVENT-TOKEN (pairing hints), USN (unique ID).

**Gemstone + Control4 Integration (CONFIRMED):**
1. Published driver: https://drivercentral.io/platforms/control4-drivers/lighting/gemstone-lights/ (free download, integrator-only)
2. Official support: https://www.gemstonelights.com/support/control4/
3. Driver format: `.c4z` is a ZIP archive containing driver.lua (Lua source), driver.xml (metadata), assets
4. Local control flow: SDDP discovery → Composer auto-populates device IP → "Allow Local Commands" enabled → Driver communicates via local HTTP/TCP

**Hypothesis: Why TCP 80 Returns "Invalid route"**
1. **Routing key provisioned during pairing:** C4 driver performs handshake (likely to LOCATION URL from SDDP), handshake provisions session token or routing key, subsequent API calls include this key in body or header
2. **Local control on different port:** SDDP LOCATION URL reveals actual control port (e.g., 8800, 9000), port 80 is "discovery only" endpoint
3. **Both:** SDDP reveals alternate port + handshake required for tokens

**Next Actions:**
- **CAPTURE SDDP broadcast:** Start UDP listener on multicast 239.255.255.250:1902, wait 5 minutes for broadcast, extract and parse packet, analyze LOCATION and CONFIG-URL for alternate port hints
- **OR extract .c4z driver:** Download from DriverCentral, rename to .zip, unzip, read driver.lua with text editor, search for HTTP endpoints and JSON payload patterns

**Outcome Mapping:**
| Scenario | Result |
|----------|--------|
| SDDP reveals alternate port (e.g., 8800) | New port hypothesis → port scan + curl probe |
| SDDP reveals LOCATION URL with config path | Handshake endpoint found → reverse-engineer pairing flow |
| .c4z extracted, Lua readable | Full protocol revealed → skip mitmproxy; implement from .c4z source |
| .c4z obfuscated or unavailable | Normal path → proceed with mitmproxy capture |
| Neither SDDP nor .c4z yields new info | No unlock → mitmproxy remains only option |

**References:**
- SDDP standard: Control4 SSDP/UPnP variant; multicast 239.255.255.250:1902 (UDP)
- Gemstone DriverCentral: https://drivercentral.io/platforms/control4-drivers/lighting/gemstone-lights/
- c4z extraction: .c4z = ZIP; rename and unzip to access Lua source

---

### 2026-05-16T15:55:00-07:00: Gemstone Lights Control4 Protocol — "Official API" Banner + pygemstone Cloud Endpoints

**Decision Owner:** Cypher (Integration/Protocol)
**Status:** FINDINGS DELIVERED — Awaiting Tank's decision on Local vs. Cloud path

**Executive Summary:**

Web search research confirms **three critical findings:**

1. **DriverCentral page displays "Official API" + "Local Communication" badges** — signaling that Gemstone has published local protocol support for Control4 integration.
2. **pygemstone library (GitHub: sslivins/pygemstone) reverse-engineered the CLOUD API** — full REST endpoint catalog exposed, including `/deviceControl/play/pattern` and `/deviceControl/onState`.
3. **No public local API documentation found** — despite the "Official API" banner, Gemstone has NOT released specs. Local protocol remains opaque; mitmproxy capture is the only proven unlock path.

**Strongest Find: DriverCentral "Official API" Banner**

URL: https://drivercentral.io/platforms/control4-drivers/lighting/gemstone-lights/

The two badges confirm Gemstone INTENDS local control support. However, badges do NOT link to documentation; searching gemstonelights.com for `/developers`, `/api`, `/api-docs` returns 404 or blank pages.

**Conclusion:** "Official API" exists in the driver (likely Control4 DriverWorks abstraction) but the HTTP/JSON protocol it uses is not publicly documented anywhere.

**Runner-Up: pygemstone Cloud API Endpoints**

URL: https://github.com/sslivins/pygemstone
Status: Reverse-engineered, AWS Amplify backend (Cognito + API Gateway)
**Critical Note:** Cloud-only; does NOT contain local API code.

**Reverse-Engineered Cloud Endpoints:**
- Base: https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod
- `PUT /deviceControl/onState` — { "id": "<device_id>", "onState": true/false }
- `PUT /deviceControl/play/pattern` — { "id": "<device_id>", "pattern": { "patternId": "...", "colors": [...] } }
- `GET /deviceControl/currentlyPlaying` — { "id": "<device_id>" }
- Auth: AWS Cognito SRP (pycognito handles handshake)

**Why cloud endpoints are irrelevant to Tank's local v0.2.0:** These are cloud endpoints. Control4 driver does NOT use them; driver uses local HTTP to port 80 (currently returning "Invalid route" 404).

**What's NOT Public:**

Searched:
- gemstonelights.com/developers → 404
- gemstonelights.com/api → JSON {"status":"ok",...} but no endpoint docs
- GitHub (sslivins/hass-gemstone, sslivins/pygemstone) → cloud-only; zero local API code
- c4forums.com — no threads with captured local JSON payloads
- Home Assistant community — all integration via pygemstone (cloud) or hardcoded /api/v1/* guesses

**Conclusion:** Gemstone Lights has NOT published local protocol specs anywhere. The "Official API" banner on DriverCentral is marketing; the real protocol remains hidden in the driver.lua file (which is PKCS#7-encrypted in the published .c4z).

**Roadmap: Two Paths Forward**

**Path A: Extract & Decrypt .c4z Driver**
- Status: BLOCKED
- Reason: driver.lua.encrypted is PKCS#7 enveloped data (ASN.1 OID 1.2.840.113549.1.7.3)
- Decryption requirement: Controller's RSA private key (not publicly available)
- Fallback: Older unencrypted versions? (Wayback Machine search yielded no results)

**Path B: mitmproxy Phone-to-PC Capture**
- Status: RECOMMENDED (UNCHANGED)
- Unlock: Capture app traffic to controller at 192.168.1.238, scrub auth tokens, extract JSON payload shape
- Timeline: ~45 minutes setup + capture

**Path C: SDDP Broadcast Analysis**
- Status: PARALLEL OPPORTUNITY (see cypher-sddp-gemstone.md)
- Unlock: Parse SDDP NOTIFY packet fields (LOCATION, CONFIG-URL) for alternate port or config endpoint hints
- Timeline: ~5 minutes listening + analysis

**Recommendation for Tank:**

1. If Mads has access to mitmproxy + phone: Proceed with Path B immediately
2. If Mads wants to attempt SDDP first (Path C): Cost is only 5 minutes listening
3. Do NOT wait for Gemstone to publish API docs

**Sources Cited:**
1. https://drivercentral.io/platforms/control4-drivers/lighting/gemstone-lights/ — "Official API" + "Local Communication" badges ✓
2. https://github.com/sslivins/pygemstone — Cloud API reverse engineering ✓
3. https://github.com/sslivins/hass-gemstone — Home Assistant custom integration (cloud-only) ✓

**Next Checkpoint:**

Waiting for: Tank's decision on Path B (mitmproxy) vs. Path C (SDDP) vs. hybrid approach.
Deliverable: Once local protocol is captured (JSON shape for on/off and play pattern), Cypher will deliver JSON schema + Tank implements HubAction wiring in v0.2.0.

---

### 2026-05-16T16:42:00-07:00: Gemstone control architecture confirmed — cloud-only via AWS IoT MQTT
**By:** Mads (via Copilot, with Mads's gateway/AP SSH credentials)

**What:** Direct evidence the Gemstone mobile app never speaks the local HTTP API:

- **Hardware fact**: controller `192.168.1.238` (MAC `90:f4:21:01:53:ca`) maintains an ESTABLISHED long-lived TCP connection to `44.241.31.78:8883` (AWS us-west-2, MQTT-over-TLS). Conntrack at the time of testing showed 24,536 packets / ~1.98 MB on that one socket.
- **Negative evidence**: 60-second tcpdump captures during active phone taps showed **zero** packets to/from `.238` on:
  1. Gateway `-i any` filter `host 192.168.1.238`
  2. All three U7 Pro APs (`-i any`, same filter) — controller's L2 MAC was STALE in every AP's neighbor table
  3. Specifically the U7 Pro upstairs (where the controller is associated)
- **Logical conclusion**: The phone → AWS REST → AWS IoT MQTT publish → controller (subscriber) → lights. The local HTTP API on port 80 is reserved for Control4/ELAN integrations whose drivers are encrypted (PKCS#7 / Cindev binary respectively), so the routing-envelope shape remains unknown and cannot be obtained by sniffing.

**Why:** Settles the v0.2.0 architecture decision. Local-only Hubitat driver is **not feasible** without one of: (a) Gemstone disclosing the local API spec, (b) successful firmware extraction from a controller (UART/JTAG — out of scope), or (c) reverse-engineering an encrypted vendor driver (Control4 encryption=2 requires the controller's private key — uncrackable).

**Implication for the driver:** v0.2.0 must use the cloud REST path that Cypher already documented in `decisions.md` (Cognito auth → REST control endpoints → cloud publishes MQTT for us). Latency ~300-500ms, internet-dependent, but functional today. v0.3.0 can revisit local if Gemstone shares the spec.

**Side benefit during this investigation:** documented the UCG Ultra → mongo (ace database) → `setting.{key: 'mgmt'}` extraction path for AP SSH credentials (x_ssh_username / x_ssh_password). Reusable skill for future UniFi-managed environments.

---

### 2026-05-16T16:48:00-07:00: User directive — never commit secrets to disk
**By:** Mads (via Copilot)

**What:** Remove any mention of Mads's passwords or other sensitive information from all on-disk files in the repo. Going forward, never write live credentials, hashes, mgmt-keys, authkeys, x_api_tokens, or any rotatable secret to any committed file — `.squad/decisions.md`, agent `history.md`, session logs, orchestration logs, inbox drops, or session-state plan files. If a secret is materially relevant, redact to `[REDACTED]` and describe the field name only.

**Why:** Security hygiene — committed files travel with the repo (and may sync to GitHub) and live forever in git history.

---

### 2026-05-16T16:49:00-07:00: Scope amendment — v0.2.0 uses cloud REST (supersedes "local-only" directive)
**By:** Mads (via Copilot)

**What:** The prior "Local-only scope (no cloud REST implementation)" directive is amended, not deleted. v0.2.0 ships using Gemstone's cloud REST API (Cognito auth + REST control endpoints) because we definitively established the mobile app never uses the local HTTP API — see `copilot-mqtt-architecture-2026-05-16.md` for the evidence. Pure-local remains the long-term ideal: a parallel email to Gemstone asking for the local API spec may unlock v0.3.0 as a pure-LAN drop-in.

**Why:** Confirmed via packet capture across gateway + all three U7 Pro APs that phone↔controller traffic is zero on LAN — controller speaks MQTT-S to AWS IoT. Local protocol cannot be discovered by sniffing because there is no traffic to sniff. Cloud is the only feasible v0.2.0 path.

---

### 2026-05-16T16:50:00-07:00: Gemstone v0.2.0 cloud driver shape
**By:** Tank

**What:** The first shippable cloud driver binds one Hubitat device to one Gemstone cloud controller through automatic discovery. After Cognito auth succeeds, the driver walks the user's home groups in order until it finds one with devices, then binds the first device in that group. The v0.2.0 public command surface is `Switch`, `SwitchLevel`, `ColorControl`, `Refresh`, and `Initialize`; `effectName` remains display-only and pattern/effect browsing is deferred.

**Why:** This meets the confirmed v0.2.0 scope (real on/off, dimming, color, refresh) without shipping a broken LightEffects UI. Gemstone accounts can contain multiple home groups or controllers, so deterministic first-device selection plus an explicit log message is safer than silently binding a random controller.

**Implications:**
- Hubitat logs show which Gemstone cloud device was selected.
- Multi-controller selection is future work, not undefined behavior.
- `play/pattern` stays an internal implementation detail behind color/brightness helpers until a catalogue-backed effects UX lands.


### 2026-05-16: Tank Cognito 408 Diagnostic Logging v0.2.4
**By:** Tank
**Status:** Implemented; awaiting live Hubitat confirmation
**Driver:** drivers/gemstone-lights/gemstone-lights.groovy

## Findings
- The login and refresh JSON bodies were already shaped correctly: bare JSON, PascalCase AuthFlow / ClientId / AuthParameters, uppercase USERNAME / PASSWORD, and REFRESH_TOKEN on refresh.
- Content-Type: application/x-amz-json-1.1 was already correct.
- The X-Amz-Target header was wrong. The driver used AmazonCognitoIdentityProvider.InitiateAuth; direct Cognito InitiateAuth requests should use AWSCognitoIdentityProviderService.InitiateAuth.
- Auth failures were too lossy: the old path could surface only Gemstone authentication failed (HTTP 408)., which hid whether Hubitat saw a transport error, a real HTTP status, or a malformed Cognito response.

## v0.2.4 Changes
- Added a shared Cognito request builder used by both password auth and refresh-token auth.
- Corrected X-Amz-Target to AWSCognitoIdentityProviderService.InitiateAuth for both Cognito POST paths.
- Kept the request body as bare JSON with the correct AWS field names.
- Raised the Cognito timeout floor to 30 seconds with safeCognitoTimeout().
- Added secret-safe failure diagnostics that log request method + URL, Content-Type and X-Amz-Target, body shape only (never values),
esp.hasError,
esp.status,
esp.errorMessage,
esp.headers,
esp.data,
esp.errorJson.
- Truncated ClientId logging to the first 8 characters and added redaction guards for password/token-like fields.
- Adjusted auth error classification so
esp.hasError() on timeout-style 408 responses is treated as a likely transport/network problem before it is treated as a clean HTTP failure.

## What Was Wrong
Confirmed wrong and corrected:
- X-Amz-Target

Checked and left as-is because it was already correct:
- Content-Type
- bare JSON body (no extra envelope)
- ClientId / AuthParameters field casing
- USERNAME / PASSWORD auth-parameter casing
- refresh-token request body shape

## Validation
- Mechanical validation only in this session: grep/view confirmed the corrected Cognito target, shared login/refresh request builder, 30-second Cognito timeout floor, new diagnostic log block, and 0.2.4 version bump.
- No live Hubitat runtime or credentials were available here, so one real auth attempt is still needed to confirm whether the root cause was the wrong target header or a transport/TLS/DNS issue that the new logs will now expose.

---

### 2026-05-16: Tank Hubitat HTTP Encoder Quirk Fix v0.2.5
**By:** Tank
**Status:** Implemented
**Driver:** drivers/gemstone-lights/gemstone-lights.groovy

## Root Cause
Hubitat's synchttpPost rejected Cognito before the request left the hub when the params map used contentType: application/x-amz-json-1.1.
The live diagnostic error was: No encoder found for request content type application/x-amz-json-1.1.
The synthesized status=408 / hasError=true was therefore a local Hubitat send failure, not an AWS timeout.
Both Cognito paths (USER_PASSWORD_AUTH and REFRESH_TOKEN_AUTH) already shared one request builder, so one fix covered both calls.

## v0.2.5 Changes
- Kept the required wire header Content-Type: application/x-amz-json-1.1 in the headers map for Cognito.
- Switched Hubitat's encoder hint to contentType: application/json and
equestContentType: application/json.
- Continued pre-serializing the Cognito payload with JsonOutput.toJson(...) and passing it as a String ody.
- Reused the same JSON-string body helper for Gemstone API Gateway requests so every outbound JSON body is serialized before Hubitat sends it.
- Added a clearer network-failure message for the No encoder found ... case so future regressions explain that Hubitat blocked the request before AWS.

## Gemstone API Audit
- Re-checked .squad/decisions.md and confirmed the cloud REST spec uses Authorization: Bearer {AccessToken}.
- The driver already used state.accessToken for Gemstone API Gateway calls; no change to idToken was needed.
- Gemstone API request bodies still go out as JSON strings with Content-Type: application/json.

## Validation
- Mechanical validation in this session confirmed the shared Cognito builder now emits:
  - wire header Content-Type: application/x-amz-json-1.1
  - contentType: application/json
  -
equestContentType: application/json
  - pre-serialized String ody
- Because both login and refresh auth call the same builder, the fix applies to both USER_PASSWORD_AUTH and REFRESH_TOKEN_AUTH.
- Also rechecked the Gemstone API builder: bearer auth remains state.accessToken and JSON bodies are serialized before dispatch.
- Repo-local validation available here is static/mechanical (grep / iew plus git diff --check); this repo does not include a dedicated Groovy build or automated test harness for the Hubitat driver.

### ### 2026-05-17T00:53Z: User directive — favorites take priority in effect catalogs
**By:** Mads (via Copilot)
**What:** When the Gemstone driver lists effects (in logs, in `lightEffects`/`effectCatalog` attributes, in help output, or anywhere user-facing), **user-marked favorites must appear FIRST and be clearly distinguishable from the full catalog**. Mads uses favorites most heavily; they're the primary daily-use surface, not the long-tail of every built-in pattern. The driver should fetch favorites separately from the full catalog if the Gemstone cloud API exposes them as a distinct collection, and merge intelligently otherwise.
**Why:** Daily usability — favorites are the curated short list Mads actually drives lights from. Burying them in alphabetical or insertion order of 100+ patterns makes the driver harder to use than the official app.
**Implementation guidance:** (1) If Gemstone cloud API has a `/favorites` or equivalent endpoint, hit it. (2) If only a single `/patterns` endpoint exists, check each pattern record for a `favorite` boolean / star flag and partition. (3) Expose `state.favorites` (Map of name → patternId) separately from `state.effectCatalog`. (4) When logging available effects, log favorites first, then the rest. (5) Consider exposing `favorites` as a Hubitat attribute (comma-separated names) so dashboards can build favorites-first pickers.


---

### # 2026-05-16: Gemstone effect catalog cache for v0.3.0

**By:** Tank
**Status:** Completed
**Scope:** `drivers/gemstone-lights/gemstone-lights.groovy`

## Decision

For named-effect control, cache a flattened Gemstone effect catalog in driver `state` with:

- `state.effectCatalog` as `name -> patternId`
- `state.effectPatterns` as `patternId -> compact Pattern payload`
- `state.effectCatalogFetchedAt = now()` for a 1-hour TTL

Fetch both sources from Cypher's cloud spec:

1. User presets: `GET /folders/pattern/list?page=N`
2. Gemstone-managed built-ins: `GET /downloads/folders/pattern/listGemstoneManaged?page=N`

Merge them into one case-insensitive lookup table. If both sources expose the same visible name, prefer the user preset and log that collision once at info level.

## Why

- Mads needs to call effects by human-readable name from Rule Machine, not by opaque pattern UUID.
- The activation path still uses the proven `PUT /deviceControl/play/pattern` endpoint, so the driver must retain the compact full pattern payload for the selected `patternId`.
- A 1-hour TTL keeps the catalog fresh enough for newly saved presets without hammering the cloud on every rule execution.
- Saved presets should beat built-ins on collisions because they are more specific to the user's intent.

## Consequences

- First `setEffect()` after an empty/stale cache becomes a two-step flow: refresh catalog asynchronously, then resolve the queued effect name.
- Unknown names are self-discoverable because the driver logs the available effect list.
- Color/level flows remain unchanged and continue to reuse the cached `lastPattern` payload.


---

### # 2026-05-16: Gemstone v0.4.0 LightEffects + ColorTemperature + favorites-first

**By:** Tank
**Status:** Completed
**Scope:** `drivers/gemstone-lights/gemstone-lights.groovy`

## Decision

For v0.4.0, the Gemstone driver layers Hubitat's standard capabilities on top of the v0.3.0 named-effect path:

1. **Add `LightEffects`** and keep the existing `setEffect(String name)` overload.
   - `lightEffects` is built as a favorites-first JSON map with numeric string keys (`"0"`, `"1"`, ...).
   - `setEffect(BigDecimal index)`, `setNextEffect()`, and `setPreviousEffect()` resolve through `state.effectIndex`.
   - Index `0` is the **first favorite**, not an `Off` placeholder; normal power-off still uses the `Switch` capability.

2. **Surface favorites separately from the full catalog.**
   - `state.favorites` stores ordered `name -> patternId` favorites in Gemstone's returned order.
   - `state.effectCatalog` stores the full `name -> patternId` map for all effects.
   - `favoriteEffects` exposes the starred favorites as a comma-separated Hubitat attribute.
   - Favorite display decoration (`⭐ `) happens only on user-facing surfaces, not in the raw lookup map.

3. **Discover favorites from the existing pattern payload, not a separate endpoint.**
   - Cypher's captured spec shows `isFavorite` on `GET /folders/pattern/list` records.
   - No separate `/favorites` endpoint is documented in the repo's API spec.

4. **Add `ColorTemperature` through RGB fallback.**
   - Cypher's spec still documents no native Kelvin/CCT endpoint.
   - `setColorTemperature(kelvin, level, transitionTime)` converts Kelvin to an RGB white-spectrum value and sends it via the existing `PUT /deviceControl/play/pattern` path.
   - The driver marks `colorMode = CT` explicitly and updates `colorTemperature` + `colorName` even though the wire payload is still RGB.

5. **Track active mode explicitly.**
   - `setColor()` → `RGB`
   - `setColorTemperature()` → `CT`
   - `setEffect(...)` / next / previous → `EFFECTS`
   - `refresh()` re-infers mode from catalog IDs plus pattern heuristics.

## Why

- Hubitat dashboards understand `LightEffects` natively, so Mads gets a proper dropdown without giving up the v0.3.0 by-name Rule Machine path.
- Favorites are the primary daily-use surface; keeping them separate prevents the raw catalog map from being polluted with display-only `⭐` prefixes.
- The RGB fallback is honest about the Gemstone API limitation while still unlocking Hubitat automations that speak in Kelvin.
- Explicit `colorMode` lets rules distinguish between solid colors, white-temperature intent, and animated effects.

## Consequences

- `refreshEffectCatalog()` now rebuilds `state.favorites`, `state.effectCatalog`, `state.effectPatterns`, `state.effectIndex`, `lightEffects`, and `favoriteEffects` together.
- The driver generates a fresh pattern id whenever switching from an effect to RGB/CT solid color so refreshes do not misclassify the current mode as the old effect.
- Runtime favorite/total counts are now logged on every catalog refresh rather than hard-coded in docs.


---

### 2026-05-17T01:47Z: User directive — .squad/ excluded from public/committed scope [SUPERSEDED]
**By:** Mads (via Copilot)
**Status:** Superseded by 2026-05-17T02:01Z: User directive — REVERSAL
**What:** The .squad/ folder (team coordination state — decisions, agent histories, orchestration logs, casting registry, session logs) is **internal-only** and must be excluded from the public GitHub repo via .gitignore. Squad memory is for the AI team's continuity across sessions; it should never travel with the published driver. The same applies to other internal/transient artifacts: .copilot/ (local Copilot session state), *.pcap files, any .squad/research/ outputs, and similar local-only artifacts.
**Why (original):** Privacy and signal-to-noise — team-coordination notes are not user-facing project content. The published repo should contain only the driver, docs, manifest, license, and contribution scaffolding.
**Implementation guidance (original):** .gitignore should include at minimum: .squad/, .copilot/, *.pcap, 
ode_modules/, and OS junk (Thumbs.db, .DS_Store). Keep the published repo focused on drivers/, README.md, LICENSE, and any top-level scaffolding necessary for HPM consumers.

---

### 2026-05-17T02:01Z: User directive — REVERSAL — .squad/ should be committed, not gitignored
**By:** Mads (via Copilot)
**Status:** Active
**What:** Supersedes the earlier directive copilot-directive-squad-gitignored-2026-05-16.md. The .squad/ folder is now **part of the public repository content** — committed and pushed alongside the driver. Do NOT add .squad/ to .gitignore. If Tank-10 already added it to .gitignore, Tank-11 must remove that line and force-add the existing .squad/ files in a follow-up commit.
**Why:** Mads's call — the AI-team coordination state (charters, decisions, history, orchestration logs, casting) is interesting/valuable for readers of the repo to see; demonstrates how a Squad-style multi-agent team operates. Effectively turns the repo into both a driver release AND a coordination-pattern reference.
**Implementation guidance:**
- .gitignore should NOT contain .squad/. Keep everything else from the earlier gitignore (.copilot/, *.pcap, OS junk, etc.) — those are still excluded.
- .copilot/ stays excluded (local CLI session memory).
- *.pcap stays excluded (large research artifacts, not part of the driver story).
- For follow-up commits Scribe makes to .squad/decisions.md / agent histories / logs: those commits DO go to the public repo now. Scribe should continue its commit-and-skip-push pattern; Mads pushes when ready.
- Re-evaluate the no-secrets directive (copilot-directive-no-secrets-2026-05-16.md) — it's still in force, and now MORE important since .squad/ is public. The earlier sweep already redacted creds from .squad/; future Scribe writes must continue redaction discipline.

---

### 2026-05-17T01:59Z: User directive — agents do not push or open PRs without explicit per-task approval
**By:** Mads (via Copilot)
**Status:** Active
**What:** Agents may prepare files, commit locally, and document exact next-step commands, but must NOT execute git push, gh pr create, gh repo create --push, or any other operation that mutates a remote (origin or upstream forks) on Mads's behalf. The user owns all remote-touching operations manually after reviewing the local state.
**Why:** Mads wants visibility/control over what lands in his GitHub account, especially for cross-org actions like opening PRs against community repos (e.g., the HPM hubitat-packagerepositories master list). Local commits are fine — they're easy to undo with git reset. Pushes and PRs aren't.
**Implementation guidance:**
- Agents should run all local prep: git init, edits, git add, git commit, gh repo fork --clone (to a local clone is fine, but no upstream push afterwards)
- Agents must NOT run: git push, gh pr create, gh repo create --push, gh release create --target=<remote>
- Agents output a clearly-marked "🚀 NEXT STEPS FOR MADS" block at the end of their report listing the exact commands to run, in order, to publish what they prepared
- **Carve-out exception:** This directive does NOT apply to Tank-10, which was already mid-flight when the rule was set. v0.4.0 may already be on GitHub via that earlier run.

---

### 2026-05-16: Tank HPM release infrastructure
**By:** Tank
**Status:** Completed
**What:** Add the repo-level HPM publishing kit: root epository.json, .github/workflows/release.yml, RELEASING.md, a top-level README HPM install section, and elease-tools/ handoff files for the one-time HubitatCommunity master-list PR.
**Why:** v0.4.0 is already public, but HPM still needs the publisher index plus a repeatable tag/release flow that does not rely on manual tagging each version.
**Shape:**
- epository.json is the publisher index that points Hubitat Package Manager at drivers/gemstone-lights/packageManifest.json.
- elease.yml derives tags as <driver-folder>-v<version>, parses the matching .groovy header changelog entry, and creates the annotated tag + GitHub Release automatically.
- RELEASING.md documents the six version touchpoints in the driver/manifest pair.
- elease-tools/ carries the JSON snippet, PR body, and manual command list Mads needs for the one-time HubitatCommunity epositories.json submission.
**Operating model:** Per the no-agent-pushes directive, agents stop at the local commit. Mads runs the push, optional manual workflow dispatch, and community-list PR commands himself.

---

### 2026-05-16: Tank public release — Gemstone Lights v0.4.0
**By:** Tank
**Commit SHA:** b2ba84e915241e4f4c902427296d9de943342b69 (later pushed as 6f2f85e65c43e6eb7a2165383a70cdba37d4e156)
**Status:** Released
**What:** Root-level HPM publishing kit and public repo structure. Committed locally; subsequent push authorization was given as one-time exception per no-agent-pushes directive.
**Files included:**
- .gitignore (removed .squad/ per reversal directive Tank-11)
- LICENSE
- README.md (added HPM install section)
- drivers/gemstone-lights/gemstone-lights.groovy
- drivers/gemstone-lights/README.md
- drivers/gemstone-lights/TESTING.md
- drivers/gemstone-lights/packageManifest.json
- epository.json (HPM publisher index)
- .github/workflows/release.yml (automated tag + release creation)
- RELEASING.md (version bump checklist)
- elease-tools/ (community PR handoff files)
- .squad/ content (decisions, agent charters, histories, etc.)
**HPM install URL:** https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/repository.json

---

### 2026-05-16T23:14:20Z: Link v0.4.0 Release & HPM Community List Submission
**By:** Link
**Status:** Completed
**Authorization:** One-time push approval from Mads for v0.4.0 publish cycle (exception per no-agent-pushes directive)
**Execution:**
1. **Push to GitHub:** git push origin main — commit 6f2f85e landed on origin ✅
2. **Release workflow:** gh workflow run release.yml (Run ID: 25978959810) — completed successfully ✅
   - **Tag created:** gemstone-lights-v0.4.0
   - **Release URL:** https://github.com/madskristensen/hubitat-drivers/releases/tag/gemstone-lights-v0.4.0
   - **Body:** Auto-populated from driver Changelog v0.4.0 entry
3. **Community list PR:** Forked HubitatCommunity/hubitat-packagerepositories, branched dd-madskristensen-hubitat-drivers, surgical JSON edit to preserve file format, pushed, opened PR
   - **PR URL:** https://github.com/HubitatCommunity/hubitat-packagerepositories/pull/106
   - **Entry added:** {"name": "Mads Kristensen", "location": "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/repository.json"}
**Key insight:** PowerShell's ConvertTo-Json reformats entire JSON structure (spaces instead of tabs); surgical text replacement via regex was used to preserve community file format and keep diff clean (4 insertions only).
**Live URLs:**
- HPM-friendly manifest: https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/repository.json
- Direct driver manifest: https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json
**Status:** Awaiting HubitatCommunity maintainer merge of PR #106 for inclusion in master list.

