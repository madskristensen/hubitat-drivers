# Skill: Hubitat Active-TCP Discovery (DHCP Recovery)

**Created:** 2026-05-17  
**Used in:** Touchstone / Tuya Fireplace v0.1.20

---

## Problem

Hubitat drivers using `interfaces.rawSocket` store a device IP address in preferences. If the device gets a new IP from DHCP, the driver silently fails. Passive UDP broadcast listening (the native Tuya discovery mechanism) is **not supported on Hubitat** — the hub can send UDP but cannot bind a socket to receive unsolicited broadcasts.

## Solution: Active-TCP /24 Scan

When the stored IP becomes unreachable, offer a `discover()` command button that:
1. Probes the local /24 subnet on the device's known port
2. Sends a lightweight identification query to each responding host
3. Matches on a stored device identifier
4. Updates the IP preference automatically on match

## Implementation Pattern

### State variables

```groovy
state.discoveryMode = true/false          // gates routing in socketStatus/parse/heartbeat
state.discoveryProbeQueue = [1, 2, ...]   // pre-built ordered list of octets to try
state.discoveryPrefix = "192.168.1."       // /24 prefix extracted from last-known IP
state.discoveryCurrentIp = "192.168.1.X"  // IP currently being probed
state.discoveryFoundIp = null             // set on match
```

### Probe queue construction

```groovy
// Smart phase (±20 from known IP) first, then full 1-254 sweep
List<Integer> probeOrder = []
if (knownOctet != null) {
    (-20..20).each { int offset ->
        int c = knownOctet + offset
        if (c >= 1 && c <= 254) probeOrder << c
    }
}
Set<Integer> smartSet = probeOrder.toSet()
(1..254).each { int i -> if (!(i in smartSet)) probeOrder << i }
state.discoveryProbeQueue = probeOrder
```

### discoveryProbeNext()

```groovy
def discoveryProbeNext() {
    if (state.discoveryMode != true) return
    List queue = state.discoveryProbeQueue instanceof List ? (List) state.discoveryProbeQueue : []
    if (queue.isEmpty()) { discoveryComplete(); return }

    Integer nextOctet = safeInt(queue.remove(0), null)
    state.discoveryProbeQueue = queue
    if (nextOctet == null || nextOctet < 1 || nextOctet > 254) {
        runIn(1, "discoveryProbeNext"); return
    }

    String targetIp = "${state.discoveryPrefix}${nextOctet}"
    state.discoveryCurrentIp = targetIp

    // Suppress socketStatus disconnect callback from previous probe's close
    state.intentionalCloseAt = now()
    state.socketOpen = false
    try { interfaces.rawSocket.close() } catch (ignored) {}

    try {
        interfaces.rawSocket.connect(targetIp, PORT, byteInterface: true, readDelay: 150)
        state.socketOpen = true
        // Send identification query (protocol-specific)
        interfaces.rawSocket.sendMessage(buildIdentificationQueryHex())
        unschedule("discoveryProbeTimeout")
        runIn(3, "discoveryProbeTimeout")
    } catch (Exception e) {
        state.socketOpen = false
        runIn(1, "discoveryProbeNext")  // immediate skip, no Tuya device here
    }
}
```

### Routing hooks

Add to `socketStatus()`:
```groovy
if (state.discoveryMode == true) {
    if (text.toLowerCase().contains("disconnect") || text.toLowerCase().contains("error")) {
        state.socketOpen = false
        unschedule("discoveryProbeTimeout")
        runIn(1, "discoveryProbeNext")
    }
    return
}
```

Add to `parse()` after `consumeReceiveBuffer()`:
```groovy
if ((processed ?: 0) > 0 && state.discoveryMode == true) return
```

Add to `processFrame()` before normal DP dispatch:
```groovy
if (state.discoveryMode == true) {
    discoveryHandleResponse(response)
    return true
}
```

Guards in `openSocket()`, `reconnectSocket()`, `sendHeartbeat()`:
```groovy
if (state.discoveryMode == true) { debugLog "skipped — discovery in progress"; return }
```

### Matching logic (fail-closed)

```groovy
private void discoveryHandleResponse(Map response) {
    unschedule("discoveryProbeTimeout")
    String responseDevId = safeStr(response?.devId)?.trim()
    String storedDevId = deviceIdValue()?.trim()

    if (!responseDevId) {
        // No identifier in response — fail closed, skip
        runIn(1, "discoveryProbeNext"); return
    }
    if (responseDevId == storedDevId) {
        // Match found
        device.updateSetting("deviceIPPrefName", [type: "text", value: state.discoveryCurrentIp])
        sendEvent(name: "networkAddress", value: state.discoveryCurrentIp)
        state.discoveryFoundIp = state.discoveryCurrentIp
        discoveryComplete()
    } else {
        runIn(1, "discoveryProbeNext")  // wrong device, skip
    }
}
```

### Cleanup

```groovy
private void discoveryComplete() {
    state.discoveryMode = false
    unschedule("discoveryProbeTimeout")
    if (state.discoveryFoundIp) {
        log.info "[Driver] Discovery complete — found device at ${state.discoveryFoundIp}"
    } else {
        log.warn "[Driver] Discovery complete — no match found. Enter IP manually."
    }
    initialize()  // restores normal socket and heartbeat
}
```

## Performance Expectations

| Scenario | Estimated time |
|----------|----------------|
| IP shifted by ≤20 octets (smart range hit) | < 1 minute |
| IP shifted beyond smart range (full sweep) | 1–8 minutes |
| Hub sandbox rate-limits connections | Unknown — test on real hardware |

## Caveats

- Identification relies on the device responding with a parseable identifier (`devId`, `gwId`, or similar). If the device echoes back an opaque binary frame or nothing, the timeout handles it.
- Only works within the same /24 subnet. If DHCP assigned an IP in a different subnet, this won't help — set a DHCP reservation instead.
- The scan is sequential (one connection at a time) due to Hubitat's single-threaded driver sandbox.
- **Recommended primary solution:** DHCP reservation. Discovery is a fallback for when that's not set up.
