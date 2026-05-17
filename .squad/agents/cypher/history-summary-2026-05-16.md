# Cypher History Summary (Archived 2026-05-16T16:50Z)

## Overview

**Agent:** Cypher (Integration / Protocol Research)
**Period:** 2026-05-16
**Original size:** 22,303 bytes → Summarized to consolidate research findings

## Key Learnings Delivered

### 1. Cloud API Protocol (Comprehensive)
- **Source:** sslivins/pygemstone reverse engineering from iOS app v0.6.03 capture
- **Architecture:** AWS Amplify (Cognito User Pool us-west-2_rr5lY7Etr + REST API us-west-2)
- **Auth:** Cognito USER_SRP_AUTH (complex) or USER_PASSWORD_AUTH (if pool allows)
- **Critical endpoints for Hubitat:**
  - `PUT /deviceControl/onState` — on/off (body: {"onState": true/false})
  - `PUT /deviceControl/play/pattern` — brightness + color + animation (full pattern object, read-modify-write required)
  - `GET /deviceControl/currentlyPlaying` — state poll
  - `GET /homegroup/list` → `GET /homegroup/devices?homegroupId=...` — discovery
- **Polling:** 30 s interval; 30–60 s cloud lag → use optimistic state updates
- **Full spec:** 21 REST endpoints documented; 5 minimum for Hubitat v0.2.0

### 2. Local API Discovery (Exhaustive)
- **Status:** Passive probing completed; active capture required
- **11 Confirmed Facts:**
  1. Single open TCP port: 80
  2. HTTP method restriction: POST/GET only; PUT/HEAD/DELETE → 405 error
  3. No Server header; Connection: close
  4. OPTIONS times out (incomplete HTTP/1.1 compliance)
  5. Content-Type must be application/json; non-JSON → 400 error
  6. JSON must be object (not array/string/bool/number at top level)
  7. **All URL paths return identical 404 "Invalid route"** — routing NOT in URL path
  8. ~70 payload shapes tested; none triggered any error other than 404
  9. Cloud API mirror patterns fail (pygemstone structures all return 404)
  10. Custom headers (X-Action, X-Command, X-Route, X-API-Key, Bearer auth) ineffective
  11. Response time consistent ~120–160 ms; no "almost-recognized" signal

- **Interpretation:** Server is live, parsing JSON, enforcing HTTP method/content-type. Routing mechanism is opaque — requires either (a) vendor-specific field not discoverable by brute-force, (b) pairing/handshake to provision token, or (c) alternate port disclosed in SDDP NOTIFY packet.

### 3. Discovery Methods Explored
- **Framework fingerprinting:** Missing Server header + generic "Invalid route" error = no unique fingerprint. Likely custom lightweight Node.js or Go HTTP router.
- **HTTP method/path enumeration:** 12-probe runbook covers POST/PUT/OPTIONS, nested REST paths, framework introspection — all returned 404.
- **SDDP broadcast analysis:** Control4 uses SDDP UDP 239.255.255.250:1902 for device discovery; LOCATION URL likely reveals control port or config endpoint.
- **mitmproxy playbook:** 6-technique capture plan (port scan → curl → mDNS → mitmproxy → ARP spoof → tcpdump); cert-pinning handling documented.

### 4. Control4 Integration Path (Dead End for Hubitat)
- Gemstone published driver on DriverCentral with "Official API" + "Local Communication" badges
- driver.lua is PKCS#7-encrypted; source not publicly available
- No local API specs published; reverse engineering only path
- Hypothesis: SDDP NOTIFY contains LOCATION URL revealing alternate control port or pairing endpoint

## Scope Amendments Applied

**2026-05-16T16:42:00-07:00 (Mads packet capture):** Controller at 192.168.1.238 maintains persistent MQTT-over-TLS connection to AWS IoT (44.241.31.78:8883). Zero local LAN traffic observed during phone taps → mobile app routes all commands through cloud MQTT, never uses local HTTP API.

**2026-05-16T16:49:00-07:00 (Scope amendment):** v0.2.0 scope changed from "local-only" to **cloud REST** (Cognito + REST endpoints). Local API remains blocked on vendor disclosure. Pure-local deferred to v0.3.0 if specs become available.

## Deliverables Produced

1. **Cloud API spec** (27 KB) — Full Gemstone REST surface mapped to Hubitat capabilities
2. **Local API fingerprint final** (11 confirmed facts + mitmproxy capture plan + 3 fallback methods)
3. **SDDP + Control4 research** (protocol format, integration hypothesis, .c4z extraction path)
4. **Web search findings** — DriverCentral badge confirmation + gemstonelights.com API endpoint stub

## Current Status

- **Cloud API:** Complete specification ready for Tank's v0.2.0 implementation (Cognito SRP + REST endpoints)
- **Local API:** Passive probing exhausted. Packet capture would reveal routing envelope, but now deferred indefinitely (scope amendment to cloud-only)
- **Reference materials:** All research documented in .squad/decisions.md for future v0.3.0+ reference

## Next Checkpoints

1. **Tank:** Implements v0.2.0 cloud driver (Cognito SRP auth, REST control, 30 s polling)
2. **Cypher:** Research phase archived. If local API discovery resumes (user request, Gemstone disclosure), use SDDP capture or mitmproxy playbook.
3. **Team:** All findings locked in decisions.md for posterity.

---

**Archived from:** `.squad/agents/cypher/history.md` (22,303 bytes)
**Summary created:** 2026-05-16T16:50:00-07:00 by Scribe
**Rationale:** HISTORY SUMMARIZATION gate (>15,360 bytes); full history retained in `.squad/history-archive/` if needed
