---
name: "hubitat-cognito-token-refresh"
description: "Use Hubitat async HTTP plus a request queue to authenticate with Cognito, cache tokens in state, refresh proactively, and replay a single 401-failed request."
domain: "auth"
confidence: "medium"
source: "earned"
tools:
  - name: "asynchttpPost"
    description: "Hubitat async POST for Cognito InitiateAuth requests"
    when: "Logging in or refreshing tokens"
  - name: "asynchttpGet"
    description: "Hubitat async GET for state and discovery calls"
    when: "Reading cloud state or discovery endpoints"
  - name: "asynchttpPut"
    description: "Hubitat async PUT for control calls"
    when: "Sending on/off or play-pattern commands"
---

## Context
This skill applies when a Hubitat driver must talk to a Cognito-backed cloud API without blocking the device thread, especially when commands can arrive before authentication or target-device discovery has completed.

## Patterns
- Store `accessToken`, `refreshToken`, `idToken`, and absolute expiry in `state`.
- Use `USER_PASSWORD_AUTH` first when the Cognito app client allows it, then use `REFRESH_TOKEN_AUTH` for renewals.
- Queue outbound API requests in `state.pendingRequests` while auth or discovery is in flight, then flush once both the token and the target device id exist.
- Schedule proactive token refresh roughly 300 seconds before expiry with `runIn`.
- On REST `401`, queue the failed request, refresh once, mark the retry request with `allowRetry401 = false`, then replay exactly once.
- Cache the last raw pattern payload and deep-clone it before mutating brightness/color fields so unknown wire keys survive.
- Emit a visible `authStatus` attribute, but only when its value actually changes, so polling does not spam the device event history.

## Examples
- `drivers/gemstone-lights/gemstone-lights.groovy`
  - `cognitoAuthCallback`
  - `apiResponseCallback`
  - `scheduleTokenRefresh`
  - `buildLevelRequest`
  - `buildColorRequest`

## Anti-Patterns
- Logging passwords, tokens, or auth request bodies.
- Refreshing the access token on every request instead of using expiry plus a 401 fallback.
- Sending partial `play/pattern` payloads when the API expects the full raw pattern object.
- Emitting `authStatus` on every poll even when the value has not changed.
