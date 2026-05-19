---
name: "auth-before-dedup"
description: "Never let cached-state dedup bypass session validity in cloud-backed Hubitat drivers."
domain: "auth"
confidence: "low"
source: "earned"
---

## Context
Use this when a Hubitat driver has command handlers that compare requested values with `device.currentValue(...)` or cached `state`, but the actual auth/queue logic lives deeper in `sendCommand()` / `executeOrQueueRequest()`.

## Rule
Check session validity before any dedup early-return. If the session is expired, missing, or still discovering the target device, skip the dedup return and fall through to the normal command path so the request can queue itself and trigger re-auth / session setup.

## Pattern
```groovy
private boolean ensureSession(boolean requiresDevice = true) {
    if (hasUsableAccessToken() && (!requiresDevice || state.deviceId)) {
        return true
    }
    continueSessionSetup()
    return false
}

def on() {
    boolean sessionReady = ensureSession()
    if (sessionReady && device.currentValue("switch") == "on") {
        return
    }
    sendCommand(action: "on")
}
```

## Why
A stale access token can hide behind a cache hit: if dedup returns first, the command never reaches the queue/auth layer, so the user sees a silent no-op until some non-deduped path (like `refresh()`) repairs the session.

## Anti-Patterns
- `if (device.currentValue("switch") == "on") { return }` before any auth/session check
- Dedup guards that assume cached Hubitat state proves the cloud session is still valid
- Helpers that detect a bad session but then cause the caller to return before the request reaches the queue/re-auth path

## Example
- `drivers/gemstone-lights/gemstone-lights.groovy` v0.4.17: `ensureSession()` gates switch/level/color/effect dedup so expired Cognito tokens still recover on the next user command.
