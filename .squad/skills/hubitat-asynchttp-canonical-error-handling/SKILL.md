---
name: "Async HTTP Canonical Error Handling"
description: "Standard guard pattern for async HTTP response callbacks in Hubitat drivers"
domain: "error-handling, http, async"
confidence: "high"
source: "validated in PurpleAir v0.4.0 production release (commit 2d62b05)"
---

## Context

Async HTTP callbacks in Hubitat can fail silently if response handling is incomplete. The standard pattern guards against three categories of errors:
1. Null response (network failure, timeout)
2. HTTP error status (4xx, 5xx)
3. Empty or malformed JSON body

Missing any guard can crash the driver or leave it in an inconsistent state.

## Patterns

### Canonical Guard Sequence

```groovy
void parseHttpResponse(resp) {
    // Guard 1: Null response (network/timeout failure)
    if (!resp) {
        log.warn "No response from API — scheduling retry"
        scheduleRetry()
        return
    }
    
    // Guard 2: HTTP error status
    if (resp.hasError()) {
        log.warn "HTTP ${resp.getStatus()}: ${resp.getErrorMessage()}"
        // Note: if disabled, respect the "don't reschedule" directive
        if (settings.updateInterval != "0") {
            scheduleRetry()
        }
        return
    }
    
    // Guard 3: JSON parse — empty body or malformed JSON
    String body = resp?.getData()?.trim()
    if (!body) {
        log.warn "Empty response body — no data to parse"
        return  // Don't retry on empty body (likely a transient condition)
    }
    
    try {
        def json = parseJson(body)
        // ... process json ...
    } catch (e) {
        log.warn "JSON parse failed: ${e.message}"
        return
    }
}
```

### Retry Scheduling with Disabled-State Guard

When polling is disabled (update interval = "0"), do NOT reschedule even on error:

```groovy
void scheduleRetry() {
    Integer intervalMinutes = settings.updateInterval?.toString()?.isNumber() ? 
        settings.updateInterval.toString().toInteger() : 60
    
    // Key: respect disabled state even on error paths
    if (intervalMinutes != 0) {
        Integer backoffSeconds = calculateBackoff()
        runIn(backoffSeconds, "refresh")
    }
}
```

## Examples

**Applied in PurpleAir AQI v0.4.0** (commit 2d62b05):
- `httpResponse()` handler checks `!resp` before accessing resp methods
- `hasError()` branch guards HTTP status codes and logs diagnostic status
- `parseJson()` wrapped in try/catch after `?.trim()` guards
- `scheduleRetry()` respects disabled polling setting (`update_interval == "0"`)

See `drivers/purpleair-aqi/purpleair-aqi.groovy` lines 160–210 (v0.4.0).

## Anti-Patterns

❌ **No null-response guard:**
```groovy
def json = parseJson(resp.getData())  // NPE if resp is null
```

❌ **No hasError() check:**
```groovy
def json = parseJson(resp.getData())  // Ignores HTTP 500 errors
```

❌ **No empty-body guard:**
```groovy
parseJson("")  // Crashes with JSONException on empty string
```

❌ **Unconditional retry on disabled polling:**
```groovy
if (resp.hasError()) {
    runIn(60, "refresh")  // Violates disabled-state contract
}
```

## Why This Matters

- **Cloud REST APIs** are flaky by nature (network, rate limits, maintenance windows)
- **Empty responses** from APIs happen more often than expected (transient server issues)
- **Disabled polling** is user intent; retrying violates it even on error
- **Silent failures** leave drivers in zombie state (users don't know they're not getting data)

A single guard omission can turn a minor network hiccup into a cascade: driver crashes, manual hub restart required, retry storm if disabled state is ignored.
