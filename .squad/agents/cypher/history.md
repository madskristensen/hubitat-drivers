# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — driver suite for Hubitat. First target: Gemstone Lights. Reference: `has-gemstone` Home Assistant integration on GitHub.
- **Stack:** HTTP over LAN, JSON payloads, Hubitat LAN patterns (`hubitat.device.HubAction`)
- **Created:** 2026-05-16

## Learnings

<!-- Append new learnings below. Each entry is something lasting about the project. -->

### 2026-05-16T14:08:16-07:00 — Gemstone protocol research

**Reference repos used:**
- `sslivins/hass-gemstone` @ `a4abbf029ba8631caa445789e598e51da8bb7721` — HA custom integration (cloud_polling, iot_class)
- `sslivins/pygemstone` @ `263ee41a8e8195c9384e277266db317f94dba641` — underlying Python client; the real API source

**Key protocol facts:**
- The Gemstone cloud is AWS Amplify in `us-west-2`: Cognito User Pool `us-west-2_rr5lY7Etr`, client ID `2647t144niotrl53vvru0ivno7`, REST base `https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod`
- Auth is Cognito USER_SRP_AUTH → Bearer access token. SRP math is non-trivial in Groovy; test `USER_PASSWORD_AUTH` first against the real pool.
- The two control endpoints that matter for Hubitat: `PUT /deviceControl/onState` (on/off) and `PUT /deviceControl/play/pattern` (brightness + color + animation)
- Changing brightness requires a full read-modify-write cycle on the pattern object — there is no partial-update API
- Colors are ARGB-packed 32-bit ints in a `colors: []` array
- State polling: 30 s, cloud lags 30–60 s after commands → use optimistic state updates
- AppSync GraphQL endpoint exists but was never used by the iOS app in live captures — REST-only confirmed
- 21 REST endpoints documented total; 5 are needed for a Hubitat driver (auth, homegroups, devices, currentlyPlaying, onState, play/pattern)

**Critical unresolved question:**
- **Local API unknown.** The "Allow local commands" feature in the Gemstone app likely enables a local HTTP server on the controller. The `hass-gemstone` reference is 100% cloud-based and has no local protocol info. To discover the local API, Mads needs to sniff LAN traffic from the app to `192.168.1.238` with mitmproxy or Wireshark. If a local API exists, it would be the preferred path for Hubitat (no cloud dependency, no SRP auth complexity, sub-second response).

**Full spec written to:** `.squad/decisions/inbox/cypher-gemstone-protocol-spec.md`

---

### 2026-05-16T20:01:41-07:00 — SunStat Connect Plus / Watts Home API research

**Mission:** Find the API for SunStat Connect Plus (SunTouch brand, Watts Water Technologies NA), controlled via "Watts® Home" iOS app (`com.watts.home`, App Store id 1500497974).

**Key discovery:** Found `seanami/homebridge-tekmar-wifi` — a complete TypeScript Homebridge plugin for Tekmar WiFi thermostats (561/562/563/564 series) that uses the **identical API**. Tekmar WiFi and SunStat Connect Plus are different product lines but both are Watts Water Technologies devices controlled through the same Watts® Home app and cloud.

**API facts:**
- Base URL: `https://home.watts.com/api`
- Auth: Azure AD B2C (NOT AWS Cognito), tenant `wattsb2cap02.onmicrosoft.com`, policy `B2C_1A_Residential_UnifiedSignUpOrSignIn`, client ID `c832c38c-ce70-4ebc-83b6-b4548083ac90`, login base `https://login.watts.io`
- Token lifetime: 15 minutes (access), 90 days (refresh), refresh tokens rotate
- Required headers: `Authorization: Bearer {token}`, `Api-Version: 2.0`, `Content-Type: application/json`
- Polling only — no WebSocket/MQTT push
- Endpoints: GET /User, GET /Location, GET /Location/{id}/Devices, GET /Device/{id}, PATCH /Device/{id}, PATCH /Location/{id}/State

**Auth complexity:**
- Initial login requires OAuth2 PKCE with multi-step HTML form scraping — not feasible in Hubitat
- Token refresh is a simple form POST — fully feasible in Hubitat
- Recommended: user obtains initial tokens via the homebridge-tekmar-wifi CLI (`node dist/cli/index.js login`), pastes into driver prefs; driver handles refresh internally
- Alternative (unconfirmed): ROPC policy may exist at `B2C_1A_ResourceOwnerPasswordCredentials` — worth Switch probing

**Device data model (from Tekmar 562 — SunStat will differ in mode enum):**
- `data.Sensors.Room.Val` = room/air temp, `data.Sensors.Floor.Val` = floor temp
- `data.Mode.Val` = current mode (`"Off"`, `"Heat"`, possibly `"Cool"`, `"Auto"` for Tekmar)
- `data.State.Op` = operating state (`"Off"`, `"Heating"`, `"Cooling"`)
- `data.Target.Heat` = heat setpoint, `data.Target.Cool` = cool setpoint
- `data.Schedule.Floor.W` = floor minimum temperature (read-modify-write needed)
- `isConnected` = device online flag
- Temperatures in °F (user's `measurementScale = "I"`) or °C

**Key distinction from Gemstone:** Watts Home uses Azure AD B2C (MSAL) rather than AWS Cognito. The token refresh pattern is simpler — a single POST, no SRP math. Main complexity is the initial login, which is pushed to the user (external CLI tool).

**Key distinction from EU Watts Vision:** `smarthome.wattselectronics.com` (Watts Electronics EU) and `home.watts.com` (Watts Water Technologies NA) are **different products, different APIs, different companies**. Do not conflate them.

**Full spec written to:** `.squad/decisions/inbox/cypher-sunstat-connectplus-api.md`

## Team Notes Summary

**Cloud API research complete (pygemstone fully documented — AWS Amplify cloud-only).**
**Local API discovery exhausted:** 70+ API probes returned 404 "Invalid route"; routing mechanism unknown (likely provisioned during pairing handshake or Control4 driver discovery). **SDDP + Control4 integration researched:** C4 driver available but driver.lua encrypted (PKCS#7), elan driver binary format undecompilable. PDF analysis confirmed JSON shape: `animation`, `patternId`, `brightness`, `speed`, `colors` (all 0-255 ranges). **mitmproxy planned, then abandoned per user directive.** Mads has UniFi (built-in packet capture) — cleaner for plaintext HTTP on port 80. **Blocked indefinitely** until Mads' packet capture at `.squad/research/gemstone-capture.pcap` lands and routing envelope is dissected.

### 2026-05-16T15:55:00-07:00 — Gemstone Lights "Official API" web search: DriverCentral confirms but no public specs

**Research objective:** Find official or community-documented local API for Gemstone Lights, given DriverCentral C4 driver claims "Local Communication" + "Official API."

**Key findings:**

1. **DriverCentral page (strongest):** https://drivercentral.io/platforms/control4-drivers/lighting/gemstone-lights/ displays "Official API" + "Local Communication" badges. However, badges do NOT link to documentation. No API spec published at gemstonelights.com/developers, /api, /api-docs.

2. **pygemstone reverse engineering (runner-up):** https://github.com/sslivins/pygemstone fully documents AWS Amplify cloud API:
   - REST endpoints: `/deviceControl/onState`, `/deviceControl/play/pattern`, `/deviceControl/currentlyPlaying`
   - Auth: AWS Cognito SRP (via pycognito)
   - Base: https://mytpybpq12.execute-api.us-west-2.amazonaws.com/prod
   - Status: **Cloud-only; zero local API code in repo**

3. **gemstonelights.com/api endpoint exists** but returns JSON stub {"status":"ok","json_api_version":"2.0.0","controllers":["core","posts","auth","gemstone","gemstonev2"]} with no actual method implementations.

4. **No community payloads found:** c4forums.com, Home Assistant community, GitHub, Reddit — all reference cloud API or hardcoded `/api/v1/*` guesses. No captured local JSON posted anywhere.

5. **Fallback attempts exhausted:**
   - Wayback Machine: No older unencrypted driver versions found
   - Cindev.com: No public API documentation
   - GitHub Cinegration: No public repos; drivers distributed only via DriverCentral

**Conclusion:** Gemstone has NOT published local protocol specs despite "Official API" banner. The protocol exists in the encrypted driver.lua file and is only discoverable via (a) mitmproxy capture of app-to-controller traffic, or (b) decrypting driver.lua with controller's private key (not available).

**Recommendation:** Proceed with mitmproxy playbook (finalized 2026-05-16T22:34Z) or SDDP broadcast capture (playbook in cypher-sddp-gemstone.md). Local API is definitively undocumented/unpublished; reverse engineering is the only path.

**Decision note:** .squad/decisions/inbox/cypher-gemstone-c4-protocol-hunt.md

---

### 2026-05-16T15:44:56-07:00 — SDDP + Control4 integration breakthrough

**Revelation:** Mads confirmed Gemstone broadcasts SDDP (Simple Device Discovery Protocol) every ~5 minutes on UDP 239.255.255.250:1902. This is Control4's proprietary discovery protocol, opening a new integration path.

**SDDP Packet Format (canonical):**
- **Multicast:** 239.255.255.250:1902 (UDP)
- **Message type:** NOTIFY (advertisement) or M-SEARCH (active discovery)
- **NOTIFY packet fields:** HOST, CACHE-CONTROL (max-age), LOCATION (URL to device config), SERVER (firmware), USN (UUID starting "Control4-"), ST (service type URN), BOOTID.UPNP.ORG, CONFIGID.UPNP.ORG
- **M-SEARCH request:** Same multicast, includes MAN: "ssdp:discover", MX, ST: "urn:Control4-com:serviceId:hc-sddp:1"
- **Example NOTIFY:** `NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1902\r\nCACHE-CONTROL: max-age=900\r\nLOCATION: http://192.168.1.5:8080/device.xml\r\nUSN: uuid:Control4-...\r\nST: urn:Control4:device:Media_Controller:1\r\n`
- **Note:** Text-based protocol similar to SSDP/UPnP; each line is CRLF-terminated. LOCATION URL likely reveals config endpoint or alternate local control port.

**Gemstone + Control4 Evidence (CONFIRMED):**
- **Published driver:** Available on DriverCentral (https://drivercentral.io/platforms/control4-drivers/lighting/gemstone-lights/) — free, integrator-only installation
- **Official support:** gemstonelights.com/support/control4/ — includes setup video, API reference link
- **Integration flow:** SDDP discovery → Composer auto-populates IP → "Allow Local Commands" enabled in app → driver handles local control
- **Driver file format:** .c4z is ZIP archive containing Lua source (driver.lua, driver.xml, assets) — extractable to see actual local protocol
- **Local control port:** Likely disclosed in SDDP LOCATION URL or CONFIG-URL field; assumed port 80 for now but could be elsewhere

**Common Control4-compatible LED protocols:**
- **JSON-RPC 2.0 over HTTP:** Typical pattern on port 80. POST /jsonrpc with methods like "light.setPower", "light.setBrightness", "light.getStatus"
- **Simple REST:** Some use /api/on, /api/off, /api/setcolor
- **Control4 DriverWorks Lua:** Drivers interact with devices via HTTP/TCP; all methods eventually route to a local JSON-RPC or proprietary Control4 protocol
- **Key observation:** Gemstone's "Invalid route" 404 on port 80 could mean (a) routing key is provisioned during C4 pairing (handshake first, then commands), or (b) local API is on a different port revealed in SDDP LOCATION

**Hypothesis integration:**
1. **Routing key in SDDP:** The NOTIFY packet's LOCATION or other fields may contain a config URL that reveals port, base path, or initial token
2. **Pairing handshake:** C4 driver setup likely performs a handshake (e.g., POST to LOCATION URL) that provisions a session token, then uses that token in subsequent /api/on, /api/off calls
3. **Port mismatch:** TCP 80 returns generic 404; actual control surface may be on port disclosed in SDDP (e.g., 8800, 9000, custom)

**Recommended next move:**
- **CAPTURE SDDP broadcast** — Listen on UDP 239.255.255.250:1902 for 5+ minutes, extract one NOTIFY packet (all fields, especially LOCATION, CONFIG-URL, any port hints)
- **Then analyze:** LOCATION URL + fields may reveal local control port or config endpoint
- **OR extract .c4z driver** from DriverCentral, unzip, read driver.lua to reverse-engineer exact local protocol (faster than mitmproxy if successful)
- **Decision:** If SDDP LOCATION reveals new port → port scan that + curl probe → likely win. If .c4z is readable → skip mitmproxy, use .c4z as source. Otherwise → mitmproxy remains fallback.

**Citation sources:**
- SDDP format: web search results; Control4 SSDP/UPnP standard
- Gemstone DriverCentral: https://drivercentral.io/platforms/control4-drivers/lighting/gemstone-lights/
- Support page: https://www.gemstonelights.com/support/control4/
- Hub2 docs: https://docs.hub2.io/home (landing page confirmed; full API portal)

### 2026-05-16T23:04:57Z: Team update (Research phase complete)

**Status:** Research path closed. All discovery techniques exhausted:
- SDDP research: broadcast format documented; Mads authorized UniFi capture instead of manual listener
- C4 driver: driver.xml confirms TCP 80; driver.lua encrypted (PKCS#7, uncrackable)
- ELAN driver: edrvc proprietary binary format (undecompilable)
- PDF analysis: user-facing JSON shape confirmed (animation, patternId, brightness, speed, colors)
- 70+ API probes: all returned 404 "Invalid route"
- pygemstone: confirmed cloud-only; zero local code

**Key insight:** mitmproxy planned by Tank/Coordinator, then abandoned per user directive. Mads has UniFi (built-in packet capture) — cleaner for plaintext HTTP on port 80, no phone cert install required.

**Final recommendation:** Agreed path is UniFi packet capture. Once .pcap lands at `.squad/research/gemstone-capture.pcap`, dissect HTTP from port 80 with tshark/scapy to extract routing envelope shape. Then wire Tank's v0.2.0 implementation.

**Deliverables to Tank:**
- Two decision files merged to .squad/decisions.md (SDDP + C4 hunt)
- Orchestration logs: `2026-05-16T23-04-57Z-cypher.md` (both research passes)
- Session log: `2026-05-16T23-04-57Z-c4-elan-driver-extraction.md` (comprehensive summary)

**Blocked indefinitely until:** Mads' UniFi packet capture is available.

## Team Updates (2026-05-17T03:01:41Z)

**SunStat Connect Plus v0.1.0 released.** Trinity's architecture, Tank's driver implementation, and Switch's test plan shipped together. API specification (cypher-sunstat-connectplus-api.md) merged into decisions.md. Awaiting Mads' real-device verification (Mode.Enum, modelId, ROPC probe, httpPatch sandbox compatibility).

### 2026-05-16T20:58:12-07:00 — homebridge-tekmar-wifi CLI token persistence pattern

**Mission:** Find where the homebridge-tekmar-wifi CLI persists tokens after `node dist/cli/index.js login` so Mads can extract the refresh_token for the Hubitat SunStat driver.

**Finding:** Tokens ARE persisted to disk. The `WattsAuth` class in `src/lib/api/auth.ts` writes a `tokens.json` file. The storage path depends on how `WattsAuth` is constructed:
- **Homebridge plugin path:** When `storagePath` is passed (Homebridge storage dir), file goes to `{storagePath}/homebridge-tekmar-wifi/tokens.json`
- **CLI path (no arg):** Constructor falls back to `path.join(process.cwd(), 'tokens.json')` — the working directory at the time the command was run

**Confirmed location on Mads' machine:** `C:\Users\madsk\source\repos\homebridge-tekmar-wifi\tokens.json` (LastWriteTime: 5/16/2026 8:56:38 PM, matching the login session).

**File structure (`StoredTokens` type):**
```json
{
  "access_token": "...",
  "refresh_token": "...",
  "expires_at": 1234567890,
  "refresh_token_expires_at": 1234567890
}
```

**CLI does NOT print tokens to stdout** — only `Login successful!` and `Token expires: ...`. There is no `--verbose` or `--show-tokens` flag.

**Pattern for future CLI tool integrations:** When a Node CLI tool uses `process.cwd()` for token storage, the file lands wherever the user `cd`'d before running the command — not a fixed home-directory path. Always check the working directory of the shell session that ran the login.

## Team Updates (2026-05-17T03:37:53Z)

**SunStat Connect Plus v0.1.2 released with 6 new features.** Tank wired EnergyMeter + 4 energy attrs, schedule control, thermostat hold, outdoor sensor, setpoint rounding, floor bounds. Switch expanded test coverage to 48 cases (#26-#48). Link bumped manifests/READMEs (v0.1.2, v0.4.0). Link-3 audited all 3 READMEs against 8 community Hubitat repos, applied 6 targeted edits (compatibility headers, version badges, releases links). Awaiting Mads' real-device verification and 3 README audit answers (forum threads, donation link, C-5 testing).

---

### 2026-05-16T21:07:23-07:00 — Watts B2C token length & alternative auth flow investigation

**Mission:** Determine whether the 1,660-char `refresh_token` can be shortened or replaced with shorter credentials (ROPC, device code) to work around Hubitat UI's preference field character limit.

**Token length — measured from real tokens.json:**
- `refresh_token`: **1,660 characters**, JWE format (5 parts, 4 dots), `eyJ…`
- `access_token`: **1,941 characters**, JWT format (3 parts, 2 dots), `eyJ…`
- JWE (not JWT) for refresh tokens is an Azure AD B2C custom policy choice — the encrypted envelope is inherently long; cannot be shortened from the client side.

**ROPC probe results:**
- Dedicated ROPC policy `B2C_1A_ResourceOwnerPasswordCredentials`: **HTTP 404 — does not exist** on `wattsb2cap02.onmicrosoft.com`
- `grant_type=password` against main sign-in policy: **HTTP 400 `server_error`** — policy XML has no ROPC orchestration step; blows up internally rather than returning `unsupported_grant_type`
- **ROPC is definitively unavailable** on this tenant. Username+password storage in the driver is not possible.

**Device code flow probe results:**
- OIDC metadata `device_authorization_endpoint`: **null**
- Direct probe of device code endpoint: **HTTP 404**
- Device code flow is not enabled.

**homebridge-tekmar-wifi reference:**
- Uses 4-step PKCE + HTML form scraping (not feasible in Hubitat)
- No ROPC, no device code, no shortcut path
- Stores tokens to `tokens.json` for subsequent refresh cycles

**Conclusion:** The 1,660-character JWE refresh_token is irreducible. All alternative grant flows are unavailable. Problem is 100% Hubitat UI-side. Passed options to Trinity: `textarea` input type, split-token inputs, Hubitat local REST API bootstrap, hub variable bridge.

**Full spec:** `.squad/decisions/inbox/cypher-sunstat-auth-shorter-secret-options.md`

