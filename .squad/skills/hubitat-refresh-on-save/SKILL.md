---
name: "Cloud Poll Refresh-on-Save"
description: "Cloud polling drivers should call refresh() from updated() for immediate user feedback"
domain: "cloud-drivers, user-experience, polling"
confidence: "high"
source: "validated in PurpleAir v0.4.0 (commit 2d62b05) and production Fully Kiosk polling"
---

## Context

When a user adjusts a cloud polling driver's settings (e.g., polling interval, API credentials, sensor ID) and clicks "Save Preferences", they expect to see updated data immediately, not wait for the next scheduled poll.

Without this pattern, users wait 5–60 minutes for a manual poll—degraded UX and higher support burden.

## Patterns

### Lifecycle Pattern

```groovy
void updated() {
    log.debug "Preferences updated"
    unschedule()      // Cancel all pending scheduled tasks
    
    // Re-register polling schedule
    schedulePolling()
    
    // KEY: Trigger immediate refresh so user sees updated data
    refresh()
}

void schedulePolling() {
    Integer intervalMinutes = settings.pollInterval?.toString()?.toInteger() ?: 60
    
    if (intervalMinutes != 0) {
        switch(intervalMinutes) {
            case 1: runEvery1Minute("refresh"); break
            case 5: runEvery5Minutes("refresh"); break
            case 15: runEvery15Minutes("refresh"); break
            case 30: runEvery30Minutes("refresh"); break
            default: runEveryNMinutes(intervalMinutes, "refresh")
        }
    }
}

void refresh() {
    // Async HTTP call to API
    asynchttpGet("httpResponse", [...]
}
```

## Examples

**Applied in PurpleAir AQI v0.4.0** (commit 2d62b05):
- `updated()` calls `unschedule()` then `refresh()` after re-registering polling schedule
- Users see air quality update immediately when they save new search coordinates or sensor ID
- See `drivers/purpleair-aqi/purpleair-aqi.groovy` lines 100–125 (v0.4.0)

**Applied in Fully Kiosk Browser Controller** (polling mode):
- `updated()` refreshes device status immediately after saving interval preference
- Users don't wait 1–5 minutes to confirm their tablet polling is working

## Anti-Patterns

❌ **No refresh on save:**
```groovy
void updated() {
    unschedule()
    schedulePolling()
    // User waits until next scheduled poll to see updated data
}
```

❌ **Conditional refresh that breaks when polling is disabled:**
```groovy
void updated() {
    unschedule()
    schedulePolling()
    if (pollInterval != 0) {
        refresh()  // If user just set polling to "disabled", no refresh happens
    }
}
```
Better: Always call `refresh()` unconditionally; let `refresh()` itself guard disabled state if needed.

❌ **Refresh before rescheduling:**
```groovy
void updated() {
    refresh()  // May race with old scheduler
    unschedule()
    schedulePolling()
}
```
Better: Reschedule first, then refresh.

## Why This Matters

- **Immediate feedback loop:** User sees result of config change within seconds, not minutes
- **Faster troubleshooting:** If new API key is wrong, user discovers it immediately, not after waiting 30 minutes
- **Professional UX:** Standard pattern across cloud-connected devices (AWS IoT, Nest, etc.)
- **Reduced support burden:** Users won't ask "why hasn't it updated?" if they see fresh data on save

## When NOT to Use This

- **Local/LAN polling:** Drivers that poll local devices can often rely on existing discover/device-load queries
- **Quota-sensitive APIs:** If refresh() is expensive or rate-limited, consider a lighter-weight validation query instead
- **Manual-only polling:** If polling is only via manual refresh button (no scheduled polling), this pattern doesn't apply
