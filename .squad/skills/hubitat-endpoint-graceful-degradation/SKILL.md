# Skill: hubitat-endpoint-graceful-degradation

**Confidence:** low (single production case — Daikin v0.1.7)  
**First validated:** 2026-05-18 (Daikin BRP069B, `get_special_mode` 404 on Mads's real hardware)

---

## Problem

LAN and cloud drivers often poll endpoints that may or may not exist depending on firmware version, hardware SKU, or account tier. If the driver assumes the endpoint always exists, users on unsupported firmware see repeated WARN logs and may believe their device is broken.

---

## Pattern: Probe-Then-Disable-Via-State-Flag

### 1. Attempt normally on first run
Make the HTTP request as usual. No upfront capability detection needed.

### 2. In the callback: detect the "not supported" signal
Common signals:
- HTTP 404 (`response.getStatus() == 404`)
- `response.getErrorMessage().contains("Not Found")`
- `kv.ret == "ERR"` with specific error payload
- Empty response or missing field

**Key:** Do NOT rely on `checkHttpOk` for this case — the standard helper logs WARN for all errors. The "not found" signal here is *expected behavior*, not an error.

### 3. Set the flag + log INFO once
```groovy
if (!state.specialModeUnsupported) {
    state.specialModeUnsupported = true
    log.info "[Driver] Unit does not support X endpoint — disabling Y feature on this device"
}
return
```
- Use **log.info**, not log.warn — it's expected behavior on this firmware.
- Guard with `!state.{endpoint}Unsupported` to log only once.

### 4. Guard the poll path
```groovy
def doPollX() {
    if (state.xUnsupported) { traceLog "xUnsupported — skipping"; return }
    sendGet("/endpoint/x", "handleX")
}
```

### 5. Guard the command path
```groovy
def setX(String value) {
    if (state.xUnsupported) {
        log.warn "[Driver] setX: this device does not support X — ignoring"
        return
    }
    // ... normal command logic
}
```

### 6. Reset in `initialize()`
```groovy
def initialize() {
    // ...
    state.xUnsupported = false   // reset so firmware update re-probes
    // ...
}
```

### 7. Do NOT emit a fake attribute value
Leave the attribute at its initial state. The user's dashboard will show the device's `installed()` default (typically "off" or null). Do not emit a fake "off" to pretend the feature exists.

---

## Naming Convention

Use `state.{camelCaseEndpointName}Unsupported` as the flag key:
- `state.specialModeUnsupported` (for `get_special_mode`)
- `state.demandControlUnsupported` (hypothetical)
- `state.modelInfoUnsupported` (hypothetical)

---

## When to Use This Pattern

✅ Use when:
- An optional feature endpoint may not exist on all firmware variants of the same hardware
- The "not found" response is deterministic (always 404 when unsupported)
- The feature is non-critical (device works without it)

❌ Do NOT use when:
- The endpoint is required for basic operation
- The failure is transient (network error, timeout) — use retry logic instead
- The driver should fail loudly if the endpoint is missing

---

## Production Reference

**Driver:** `drivers/daikin-wifi/daikin-wifi.groovy` v0.1.7  
**Endpoint:** `/aircon/get_special_mode`  
**Flag:** `state.specialModeUnsupported`  
**Trigger:** HTTP 404 on Mads's real BRP069B hardware (firmware does not expose endpoint)  
**Commit:** 6c8ea41
