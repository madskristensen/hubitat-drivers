---
name: "hubitat-mqtt-subscriber-driver"
description: "Add opt-in MQTT subscriber support to a Hubitat Groovy driver: connect/disconnect lifecycle, LWT, exponential-backoff reconnect, poll-cadence reduction, and parse() routing."
domain: "groovy"
confidence: "high"
source: "earned"
---

## Context

Applies when adding MQTT subscriber support to a Hubitat driver that already works via polling or LAN push. The pattern is opt-in: a `mqttBroker` preference gates all MQTT code so empty preference = exact prior behavior.

Requires Hubitat firmware 2.4.4.155+ for the built-in broker (`tcp://hub_ip:1883`) — no external Mosquitto needed. `interfaces.mqtt` API has been stable since Hubitat 2.1.2 (connect, subscribe, publish, disconnect, isConnected, parseMessage).

## Patterns

### 1. Opt-in gate — all MQTT code is preference-gated

```groovy
// In initialize():
if (settings.mqttBroker?.trim()) {
    mqttConnect()
    if (settings.statePolling) runEvery5Minutes("refresh")   // heartbeat cadence
} else {
    state.mqttConnected = false
    if (settings.statePolling) runEvery1Minute("refresh")    // original cadence
}
```

### 2. Connection lifecycle

```groovy
void mqttConnect() {
    try {
        def clientId = settings.mqttClientID?.trim() ?: "hubitat-{driver}-${device.deviceNetworkId}"
        def prefix   = settings.mqttTopicPrefix?.trim() ?: "default"
        Map options  = [
            lastWillTopic:   "${prefix}/hubitat/state",
            lastWillMessage: "offline",
            lastWillQos:     1,
            lastWillRetain:  true,
            cleanSession:    false
        ]
        def safeBroker = settings.mqttBroker?.replaceAll(/(:\/\/[^:@\/]+:)[^@\/]+(@)/, '$1***$2')
        logger("[mqttConnect] Connecting to ${safeBroker}", "info")
        interfaces.mqtt.connect(settings.mqttBroker, clientId,
                                settings.mqttUsername ?: null,
                                settings.mqttPassword ?: null, options)
    } catch (Exception e) {
        logger("[mqttConnect] Failed: ${e.message}", "error")
        mqttReconnect()
    }
}

void mqttDisconnect() {
    try { if (interfaces.mqtt.isConnected()) interfaces.mqtt.disconnect() }
    catch (Exception e) { logger("[mqttDisconnect] ${e.message}", "error") }
    state.mqttConnected = false
}
```

Always call `mqttDisconnect()` from `updated()` (before `initialize()`) and from `uninstalled()`.

### 3. Status callback + poll-cadence adjustment

```groovy
void mqttClientStatus(String status) {
    if (status.startsWith("Error") || status.contains("Connection lost")) {
        state.mqttConnected = false
        if (settings.statePolling) { unschedule("refresh"); runEvery1Minute("refresh") }
        mqttReconnect()
    } else if (status.startsWith("Status")) {
        state.mqttConnected = true
        state.mqttRetryDelay = null
        def prefix = settings.mqttTopicPrefix?.trim() ?: "default"
        interfaces.mqtt.subscribe("${prefix}/#", 0)
        interfaces.mqtt.publish("${prefix}/hubitat/state", "online", 1, true)
        if (settings.statePolling) { unschedule("refresh"); runEvery5Minutes("refresh") }
    }
}
```

Hubitat status strings: `"Status: Connection succeeded"` on connect, `"Error: ..."` or `"Status: Connection lost"` on failure.

### 4. Exponential-backoff reconnect

```groovy
void mqttReconnect() {
    def delay = Math.min(((state.mqttRetryDelay ?: 10) as Integer) * 2, 300)
    state.mqttRetryDelay = delay
    logger("[mqttReconnect] Retry in ${delay}s", "info")
    runIn(delay, "mqttConnect")
}
```

Sequence: 20s → 40s → 80s → 160s → 300s → 300s (capped).

### 5. parse() routing — MQTT vs LAN messages

Hubitat delivers MQTT messages to `parse(String description)` — the same callback used by LAN HTTP push. Distinguish by checking if description starts with `"mqtt"`:

```groovy
def parse(description) {
    if (description?.startsWith("mqtt")) {
        parseMqttMessage(description)
        return
    }
    // ... existing LAN parsing ...
}

private void parseMqttMessage(String description) {
    def msg   = interfaces.mqtt.parseMessage(description)
    def topic = msg.topic as String
    def payload = msg.payload as String
    // route on topic segments ...
}
```

### 6. LWT topics

Use `{prefix}/hubitat/state` for the LWT topic (not per-device). Publish `"online"` (retained, QoS 1) on successful connect; broker publishes `"offline"` automatically on disconnect.

### 7. Password masking in logs

Never log the raw broker URL — it may contain credentials in `mqtt://user:pass@host` form:

```groovy
def safeBroker = settings.mqttBroker?.replaceAll(/(:\/\/[^:@\/]+:)[^@\/]+(@)/, '$1***$2')
```

### 8. Multi-device prefix strategy

When multiple FK tablets share the same broker, each driver instance should use a unique `mqttTopicPrefix` (e.g. `fully-bathroom`, `fully-kitchen`) to prevent cross-device attribute bleed. Document this in the preference description.

## FK MQTT topic structure (Fully Kiosk Browser v1.34+)

| Topic | Direction | Payload |
|-------|-----------|---------|
| `{prefix}/event/{eventType}/{deviceID}` | FKB → Hub | JSON event object |
| `{prefix}/deviceInfo/{deviceID}` | FKB → Hub | JSON (same as REST `cmd=deviceInfo`) |
| `{prefix}/status/{deviceID}` | FKB LWT | `"online"` / `"offline"` |

Event types: `screenOn`, `screenOff`, `motionDetected`, `foregroundApp`, `pluggedAC`, `unpluggedAC`, `batteryLevel`.

**Caveat:** FKB's `{deviceID}` in topics is FKB's own internal identifier, which may differ from Hubitat's `device.deviceNetworkId` (MAC address). Subscribing to `{prefix}/#` avoids this uncertainty; use unique prefixes per device for filtering instead.

## Anti-Patterns

- **Never skip `mqttDisconnect()` in `updated()`** — stale connections accumulate if broker URL changes.
- **Never log the raw broker URL** — may contain password in URL form.
- **Never subscribe without considering multi-device bleed** — `{prefix}/#` on a shared prefix means all driver instances on the same hub receive all FK device events.
- **Never call `mqttConnect()` without a catch block** — a bad broker URL throws synchronously.
- **Never forget `uninstalled()`** — orphaned MQTT connections hold broker resources.

## Examples

Reference implementation: `drivers/fully-kiosk/fully-kiosk.groovy` v0.4.0 (2026-05-18).
