# Skill: Hubitat Health Monitoring — HealthCheck vs lastActivity

**Domain:** Hubitat Groovy Driver Development
**Applies to:** All drivers in this repo

---

## Summary

Two patterns for driver health observability. Choose based on whether the underlying transport is local (free probing) or cloud REST (rate-limited).

---

## Pattern A — Full HealthCheck (local TCP drivers)

**When:** Driver uses a persistent LAN socket (e.g., raw TCP, WebSocket, Telnet).

**Why:** Local probing is free — no API quota, sub-millisecond RTT. Sockets can silently die between periodic heartbeats without triggering `socketStatus()`. `ping()` lets Hubitat's HealthCheck machinery force an on-demand probe.

### Canonical Implementation (Touchstone)

```groovy
// metadata
capability "HealthCheck"
attribute "healthStatus", "enum", ["online", "offline", "unknown"]
attribute "lastActivity", "string"

// ping() — reuse existing heartbeat frame builder
def ping() {
    state.pingPending = true
    state.pingRequestedAt = now()
    if (state.socketOpen == true) {
        sendHeartbeat()   // sends frame immediately, reschedules periodic heartbeat
    } else {
        openSocket()      // reconnect; first ack clears pingPending
    }
    unschedule("pingTimeout")
    runIn(5, "pingTimeout")
}

def pingTimeout() {
    if (state.pingPending != true) { return }
    Long requestedAt = safeLong(state.pingRequestedAt, 0L)
    Long lastEvent   = safeLong(state.lastSocketEventTs, 0L)
    if (lastEvent < requestedAt) {
        sendEvent(name: "healthStatus", value: "offline",
                  descriptionText: "${device.displayName} did not respond to ping within 5s")
    }
    state.pingPending = false
}

// In parse() — every successful inbound frame:
String tsActivity = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")
sendEvent(name: "lastActivity", value: tsActivity,
          descriptionText: "${device.displayName} last activity")
if (state.pingPending == true) {
    state.pingPending = false
    sendEvent(name: "healthStatus", value: "online",
              descriptionText: "${device.displayName} responded to ping")
} else if (safeStr(device.currentValue("healthStatus")) != "online") {
    sendEvent(name: "healthStatus", value: "online",
              descriptionText: "${device.displayName} health status is online")
}

// In scheduleReconnect() — flip offline after 2 failures, not 1:
state.reconnectAttempts = (attempt + 1)
if (safeInt(state.reconnectAttempts, 0) >= 2 &&
    safeStr(device.currentValue("healthStatus")) != "offline") {
    sendEvent(name: "healthStatus", value: "offline",
              descriptionText: "${device.displayName} health status is offline")
}

// In initialize() / updated() — clear orphan ping state:
state.pingPending = false
state.pingRequestedAt = 0L

// In ensureDefaultAttributes():
if (device.currentValue("healthStatus") == null) {
    sendEvent(name: "healthStatus", value: "unknown", ...)
}
if (device.currentValue("lastActivity") == null) {
    sendEvent(name: "lastActivity", value: "", ...)
}
```

### Key Guardrails

- Do NOT declare `command "ping"` explicitly — `capability "HealthCheck"` provides the zero-arg signature automatically.
- The 5s `pingTimeout` is independent of the heartbeat interval. Any inbound frame (push frame, status response) satisfies the ping — not just a heartbeat ack.
- `ping()` does not add new network calls — it reuses the existing heartbeat frame builder.
- "Don't flicker" rule: use `state.reconnectAttempts >= 2` threshold (not 1) before declaring `healthStatus = offline`.

---

## Pattern B — lastActivity Only (cloud REST drivers)

**When:** Driver communicates via cloud REST API (e.g., AWS Cognito + HTTP, Watts Home API).

**Why:** Cloud API calls consume rate-limited quota. Hubitat's HealthCheck machinery fires `ping()` on its own schedule, which would generate one cloud round-trip per hub health-check cycle. The `lastActivity` timestamp is purely passive — it advances on every 2xx response with zero additional API calls. Rule Machine rules and dashboards can detect stale cloud connectivity using `lastActivity`.

### Canonical Implementation (Gemstone, SunStat)

```groovy
// metadata
attribute "lastActivity", "string"

// Helper — call on every confirmed 2xx success path:
private void touchActivity() {
    sendEvent(name: "lastActivity",
              value: new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX"),
              descriptionText: "${device.displayName} last activity")
}
```

**Hook points:**
- After parsing a successful API response body (NOT on 4xx, 5xx, timeout, or token-refresh-only calls)
- For async callbacks: after all error guards have been passed and the response body has been parsed

**NEVER call on:**
- `401` auth failure paths
- `429` rate-limit paths
- Network error / timeout callbacks
- Token refresh failure paths

### Parent/Child Variant (SunStat)

When a parent driver cascades activity to child devices:

```groovy
// Parent touchActivity() — updates parent AND all children:
private void touchActivity() {
    String ts = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")
    sendEvent(name: "lastActivity", value: ts,
              descriptionText: "${device.displayName} last activity")
    childDevices.each { child ->
        child.setLastActivity(ts)
    }
}

// Child setLastActivity() — child controls its own event:
void setLastActivity(String timestamp) {
    sendEvent(name: "lastActivity", value: timestamp,
              descriptionText: "${device.displayName} last activity")
}
```

---

## Decision Flowchart

```
Is the driver using a local persistent socket (TCP/WebSocket/Telnet)?
  YES → Use Pattern A (full HealthCheck + ping() + lastActivity)
  NO  → Is it a cloud REST driver with rate-limited API?
    YES → Use Pattern B (lastActivity only)
    NO  → Evaluate case by case
```

---

## Reference Implementations

| Driver | Version | Pattern | Files |
|--------|---------|---------|-------|
| Touchstone / Tuya Fireplace | v0.1.21 | A (full HealthCheck) | `drivers/touchstone-fireplace/touchstone-fireplace.groovy` |
| Gemstone Lights | v0.4.11 | B (lastActivity only) | `drivers/gemstone-lights/gemstone-lights.groovy` |
| SunStat Connect Plus | v0.1.7 | B (lastActivity only, parent+child) | `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy`, `sunstat-thermostat-child.groovy` |
