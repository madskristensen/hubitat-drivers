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
