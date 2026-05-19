# Skill: Cognito Token Lifecycle in Hubitat Drivers

**Category:** Auth / Token Management  
**Applies to:** Any Hubitat driver that uses AWS Cognito for cloud authentication

---

## The Problem Pattern

Hubitat drivers that cache Cognito AccessTokens must handle **three failure modes**, not just one:

1. **Proactive timer failure** — the `runIn()` task fires but both `REFRESH_TOKEN_AUTH` and the `USER_PASSWORD_AUTH` fallback fail (network blip). No new timer is scheduled; token silently expires.
2. **Dedup-before-auth ordering** — command handlers that check cached device state before checking token validity can return early without ever triggering re-auth.
3. **Unknown HTTP status on expiry** — Cognito-backed APIs don't all return 401 for an expired token. Some return 403. If the driver only retries on 401, a 403 is treated as a hard error and the command is dropped.

---

## The Correct Refresh Architecture

### Token storage
```groovy
state.accessToken  // Cognito AccessToken
state.refreshToken // Cognito RefreshToken (30-day default TTL)
state.tokenExpiry  // Unix epoch seconds: now() + ExpiresIn
```

### Validity check (with leeway)
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
Schedule this on **every auth success** and on **`initialize()`** (hub restart recovery).  
`runIn()` in Hubitat IS persistent across reboots, but the timer is only rescheduled on success — a failure leaves no new timer.

### Fallback chain on proactive refresh failure
```
REFRESH_TOKEN_AUTH fails → startPasswordAuth() → USER_PASSWORD_AUTH
USER_PASSWORD_AUTH fails → handleAuthFailure() → clearAuthTokens() + clearPendingRequests()
```
After `handleAuthFailure()`: no token, no timer. Recovery depends entirely on the next user command reaching the auth gate.

---

## The Critical Rule: Auth Gate BEFORE Dedup

**Wrong order** (current Gemstone driver bug):
```groovy
def on() {
    if (device.currentValue("switch") == "on") return  // ← dedup BEFORE auth check
    sendCommand(action: "on")  // ← auth gate is inside here
}
```

**Correct order:**
```groovy
def on() {
    if (!hasUsableAccessToken()) {
        // Token expired — skip dedup, proceed to command path for re-auth
        sendCommand(action: "on")
        return
    }
    if (device.currentValue("switch") == "on") return  // dedup only when token is valid
    sendCommand(action: "on")
}
```

Or extract an `ensureSession()` call at the top of every handler that triggers `continueSessionSetup()` if the token is gone, then returns — leaving dedup to guard only the case where the session is already healthy.

---

## The 401 vs 403 Risk

Cognito-backed APIs should return 401 for expired tokens but some return 403. Always handle **both**:

```groovy
if (status == 401 || status == 403) {
    // re-auth + retry
}
```

Until confirmed via live traffic, document this as an open question in the driver.

---

## Checklist for New Drivers Using Cognito

- [ ] Token stored in `state.accessToken` + `state.tokenExpiry` + `state.refreshToken`
- [ ] `hasUsableAccessToken()` uses a leeway window (300s recommended)
- [ ] `scheduleTokenRefresh()` called on every auth success AND in `initialize()`
- [ ] `initialize()` resets `state.authInFlight = false` (prevents stuck-flag lockout)
- [ ] Auth gate (`hasUsableAccessToken()`) checked **before** any dedup guard
- [ ] 401 AND 403 both trigger re-auth + retry
- [ ] `handleAuthFailure()` does NOT leave a zombie timer; `clearAuthTokens()` unschedules
- [ ] On re-auth success: `flushPendingRequests()` dispatches queued commands
