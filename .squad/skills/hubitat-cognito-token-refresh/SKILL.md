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

## Three Failure Modes for Cognito Token Drivers
Drivers that cache Cognito access tokens must handle three distinct failure modes:

1. **Proactive timer failure** — the `runIn()` refresh task fires but both `REFRESH_TOKEN_AUTH` and `USER_PASSWORD_AUTH` fallback fail (network blip). No new timer is scheduled; token silently expires and stays expired until user action.
2. **Dedup-before-auth ordering** — command handlers checking cached device state before checking token validity can return early without triggering re-auth. See *See also* section below.
3. **Unknown HTTP status on expiry** — Cognito-backed APIs return either 401 or 403 for expired tokens. Drivers that only handle 401 silently drop 403 responses. Always check for both HTTP statuses.

## Token Storage & Validity Architecture

### Token storage in state
```groovy
state.accessToken  // Cognito AccessToken
state.refreshToken // Cognito RefreshToken (30-day default TTL)
state.tokenExpiry  // Unix epoch seconds: now() + ExpiresIn
```

### Validity check with leeway
```groovy
private boolean hasUsableAccessToken() {
    return safeString(state.accessToken) && !tokenExpiresSoon()
}
private boolean tokenExpiresSoon() {
    Long expiresAt = safeLong(state.tokenExpiry, 0L)
    if (!expiresAt) return true
    return (expiresAt - TOKEN_REFRESH_LEEWAY_SECONDS) <= currentEpochSeconds()
}
```
`TOKEN_REFRESH_LEEWAY_SECONDS = 300` creates a 5-minute proactive window.

### Proactive refresh timer
```groovy
private void scheduleTokenRefresh() {
    Long delay = Math.max(5, expiresAt - nowSeconds - TOKEN_REFRESH_LEEWAY_SECONDS)
    runIn(delay as Integer, "refreshAccessTokenTask")
}
```
Schedule on **every auth success** and in **`initialize()`** (hub restart recovery). `runIn()` in Hubitat IS persistent across reboots, but a failed refresh leaves no new timer.

### Fallback chain on proactive refresh failure
```
REFRESH_TOKEN_AUTH fails → startPasswordAuth() → USER_PASSWORD_AUTH
USER_PASSWORD_AUTH fails → handleAuthFailure() → clearAuthTokens() + clearPendingRequests()
```
After `handleAuthFailure()`: no token, no timer. Recovery depends entirely on the next user command reaching the auth gate.

## Patterns
- Store `accessToken`, `refreshToken`, `idToken`, and absolute expiry in `state`.
- Use `USER_PASSWORD_AUTH` first when the Cognito app client allows it, then use `REFRESH_TOKEN_AUTH` for renewals.
- Queue outbound API requests in `state.pendingRequests` while auth or discovery is in flight, then flush once both the token and the target device id exist.
- Schedule proactive token refresh roughly 300 seconds before expiry with `runIn`.
- On REST `401` or `403`, queue the failed request, refresh once, mark the retry request with `allowRetry401 = false`, then replay exactly once.
- Cache the last raw pattern payload and deep-clone it before mutating brightness/color fields so unknown wire keys survive.
- Emit a visible `authStatus` attribute, but only when its value actually changes, so polling does not spam the device event history.

## Examples
- `drivers/gemstone-lights/gemstone-lights.groovy`
  - `ensureSession()` v0.4.17+
  - `cognitoAuthCallback`
  - `apiResponseCallback`
  - `scheduleTokenRefresh`
  - `buildLevelRequest`
  - `buildColorRequest`

## Anti-Patterns
- Logging passwords, tokens, or auth request bodies.
- Refreshing the access token on every request instead of using expiry plus a 401/403 fallback.
- Handling only 401 HTTP status, ignoring 403 (common on Cognito-backed APIs with expired tokens).
- Sending partial `play/pattern` payloads when the API expects the full raw pattern object.
- Emitting `authStatus` on every poll even when the value has not changed.
- Command handlers that check cached device state before checking token validity (causes silent failures when tokens expire).

## New-Driver Cognito Checklist
- [ ] Token stored in `state.accessToken` + `state.tokenExpiry` + `state.refreshToken`
- [ ] `hasUsableAccessToken()` uses a leeway window (300s recommended)
- [ ] `scheduleTokenRefresh()` called on every auth success AND in `initialize()`
- [ ] `initialize()` resets `state.authInFlight = false` (prevents stuck-flag lockout)
- [ ] Auth gate (`hasUsableAccessToken()`) checked before any dedup guard
- [ ] 401 AND 403 both trigger re-auth + retry
- [ ] `handleAuthFailure()` does NOT leave a zombie timer; `clearAuthTokens()` unschedules
- [ ] On re-auth success: `flushPendingRequests()` dispatches queued commands

## See also
- `.squad/skills/auth-before-dedup/` — Critical rule: never let cached-state dedup bypass session validity checks
