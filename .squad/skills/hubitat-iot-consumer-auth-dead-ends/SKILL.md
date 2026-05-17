---
name: "hubitat-iot-consumer-auth-dead-ends"
description: "Why reverse-engineered consumer auth flows (WebSocket/MQTT/SSO scraping) are dead ends for Hubitat drivers — and what to check before going down that path."
domain: "protocol"
confidence: "high"
source: "earned — Bosch Home Connect consumer auth research, 2026-05-17"
---

## Context

Use this skill when evaluating whether a Hubitat driver can avoid using an official developer API by mimicking the consumer mobile app's auth flow.

## The Pattern That Always Fails

Consumer IoT apps typically follow this architecture:

1. **Auth:** OAuth2 / SSO via brand's consumer identity portal (often with CAPTCHA) → bearer token
2. **Setup:** Token used against a provisioning backend to fetch per-device crypto keys or pairing tokens
3. **Operation:** Direct local WebSocket / MQTT / TLS-PSK to the appliance using those keys

Step 3 is the killer for Hubitat. The cloud is only involved at setup; the operational interface is a persistent local protocol.

## Hubitat Sandbox Hard Constraints

| Constraint | Impact |
|---|---|
| No persistent WebSocket client | Any IoT device that pushes state over a local WebSocket (Bosch Home Connect, Tuya local, etc.) cannot be polled or subscribed to |
| No MQTT client | Same as WebSocket — any MQTT-based local protocol is unreachable |
| No browser / JS runtime | Browser-based SSO (OAuth2 Authorization Code flow, SAML redirect, CAPTCHA) cannot be completed from a driver |
| No persistent cookie jar across HTTP calls | Multi-step OAuth form-scraping that requires cookie continuity fails — each `httpGet`/`httpPost` call is independent |
| No HTML parser | CSRF token extraction from form responses requires an HTML parser; Hubitat has none |
| No custom TLS ciphers | Non-standard ciphers like `ECDHE-PSK-CHACHA20-POLY1305` (Bosch Home Connect) cannot be negotiated by the JVM TLS stack |
| Request-response HTTP only | SSE streams (`text/event-stream`), long-polling, and chunked streaming are not supported |

## The Test to Apply Before Researching Consumer Auth

Before spending time on reverse-engineering, answer:

1. **What protocol does the device use for real-time state updates?**
   - If WebSocket, MQTT, SSE, or any persistent connection → **stop. Hubitat cannot do this.**
   - If REST polling (device answers to GET with current state) → potentially viable.

2. **Does the official/developer API provide a pollable REST interface?**
   - If yes → use the developer API, even if it requires user registration.
   - Registration friction is almost always lower than the engineering cost of working around it.

3. **Is there a consumer-backend REST polling endpoint (not just setup endpoints)?**
   - Most consumer backends are provisioning-only at the cloud layer. The actual telemetry goes local.
   - Confirm explicitly — don't assume a bearer token gives you a polling API.

## Bosch Home Connect — Confirmed Dead End (Reference Case)

- **Consumer auth path:** OAuth2 PKCE using BSH mobile app's `client_id` via SingleKey ID at `singlekey-id.com`
- **Why auth is blocked:** CAPTCHA added ~2024 to SingleKey ID prevents automated scripting. Multi-step redirect chain with CSRF tokens requires browser-level session.
- **Why even successful auth doesn't help:** Consumer backend (`eu.services.home-connect.com`) exposes device discovery + encryption key endpoints for setup only. There is no REST endpoint for polling appliance state. Operational interface is a local WebSocket using `ECDHE-PSK-CHACHA20-POLY1305` (patched TLS required) or AES-CBC over WebSocket.
- **Developer API:** `api.home-connect.com/api/` is a separate, properly REST-accessible system with `GET /api/homeappliances/{haId}/status`. Requires developer app registration (5 minutes). This is the viable path.

## Viable Alternatives When Consumer Path Is Blocked

1. **Official/developer API with user registration** — almost always the right answer if one exists
2. **Python bridge** (e.g., hcpy + MQTT → Hubitat) — works but adds infrastructure; user must run a sidecar service
3. **Accept the limitation** — document that the capability requires a sidecar or is not feasible on Hubitat

## Anti-Patterns

- Spending research time on consumer auth without first confirming a REST polling endpoint exists on the consumer backend
- Assuming a consumer bearer token gives the same API surface as the developer token
- Trying to scrape SSO login pages in Groovy without an HTML parser or cookie jar
- Implementing a local WebSocket protocol inside a Hubitat driver (sandbox will not permit persistent connections)
