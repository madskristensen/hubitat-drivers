---
name: "tuya-local-groovy"
description: "Implement Tuya Local v3.3 Groovy drivers on Hubitat using rawSocket, AES-128-ECB, queued retries, and defensive frame parsing."
domain: "hubitat-drivers"
confidence: "high"
source: "earned — Touchstone v0.1.2 verified the Hubitat import-allowlist CRC32 fix pattern on 2026-05-17; protocol cross-checked against tinytuya XenonDevice/message_helper/header and qwerk's Hubitat Tuya RGBW driver."
---

## When to Use

Use this skill when a Hubitat driver must talk directly to a Tuya WiFi device over the LAN using a `deviceId`, `localKey`, and raw TCP port `6668`.

This skill is specifically for:
- **Tuya protocol v3.3** (`55AA` framing + AES-128-ECB)
- **Groovy / Hubitat** drivers using `interfaces.rawSocket`
- single-device drivers where you want local control, not Tuya Cloud

Do **not** use this skill for:
- Tuya Zigbee devices (kkossev's Zigbee patterns are separate)
- Tuya v3.4 / v3.5 session-key negotiation (`6699` framing)
- devices that only expose cloud APIs

## Core Transport Pattern

### 1. rawSocket shape on Hubitat

```groovy
interfaces.rawSocket.connect(settings.deviceIP, 6668, byteInterface: true, readDelay: 150)
```

**Important:** even with `byteInterface: true`, Hubitat commonly delivers the payload to `parse(String message)` as a **hex string**. Treat the incoming stream as hex text, not as JSON or a byte array callback.

### 2. Buffer + frame split

Keep a hex buffer in `state.rxBuffer`, append each `parse()` chunk, and process complete frames only when you have enough bytes:

1. Find prefix `000055AA`
2. Read bytes `12..15` (`length` field)
3. Total frame size = `16 + length`
4. Wait for the full frame before parsing
5. Validate suffix `0000AA55`
6. Validate CRC32 over header + payload (excluding CRC + suffix)

Never assume one `parse()` call equals one Tuya frame.

## Tuya v3.3 Payload Rules

### Packet format

```text
[prefix 0x000055AA][seq 4B][cmd 4B][length 4B][payload][crc32 4B][suffix 0x0000AA55]
```

### Encryption rule

1. Serialize JSON with **no extra spaces**
2. Encrypt the JSON bytes with `AES/ECB/PKCS5Padding` using the 16-byte `localKey`
3. For most commands, prepend ASCII `3.3` + 12 zero bytes **after** encryption
4. Then wrap in the `55AA` frame

### Commands that skip the `3.3` header

For v3.3, these commands should **not** get the protocol header prepended:
- heartbeat (`0x09`)
- legacy DP query (`0x0a`)

Commands like control (`0x07`) and device22/new query (`0x0d`) **do** carry the `3.3` + zero-padding header.

## AES Helpers

```groovy
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private byte[] aesEncrypt(byte[] plaintext, byte[] keyBytes) {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    return cipher.doFinal(plaintext)
}

private byte[] aesDecrypt(byte[] ciphertext, byte[] keyBytes) {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)
    return cipher.doFinal(ciphertext)
}
```

## `device22` Query Gotcha

If the Tuya `deviceId` is 22 characters long, be ready for the `device22` status-query edge case:

- standard status query: command `0x0a`
- `device22` query: command `0x0d`
- payload should include a null-valued `dps` map, for example:

```groovy
[
    devId: settings.deviceId,
    uid: settings.deviceId,
    t: currentEpochSecondsString(),
    dps: ["1": null, "2": null, "101": null]
]
```

If the device responds with `data unvalid`, switch to `0x0d` mode and retry once. This mirrors tinytuya's `device22` handling.

## Recommended Hubitat Driver Behavior

### Lifecycle
- `installed()` → set defaults → `initialize()`
- `updated()` → `unschedule()` → reset socket/queue state → `initialize()`
- `initialize()` should schedule polling and queue an initial `refresh()`

### Logging
- Use the standard `logEnable` auto-off pattern (`runIn(1800, "logsOff")`)
- Keep `parse()` defensive: log + bail on bad CRC / bad suffix / short frames
- Do not log the `localKey`

### Queue + retry
Tuya WiFi devices are often effectively **single-client**. If Smart Life or another client holds the socket, your driver may time out or drop the connection.

Recommended pattern:
- queue outbound requests in `state.pendingRequests`
- send one request at a time
- mark `state.awaitingResponse = true`
- if no answer within ~5 seconds, requeue and back off
- retry delays: **5s, 15s, 30s**
- close the socket quickly after idle to reduce contention

This is the Hubitat/Groovy equivalent of the tinytuya `901` busy/unavailable scenario.

## Power-Transition Setpoint Guard

For Touchstone-style fireplaces, DP `14` (°F setpoint) may momentarily revert to a default right after a power transition.

Safer pattern:
- emit the optimistic setpoint event locally when the user writes it
- record `state.lastPowerTransitionAt`
- suppress immediate DP14-driven setpoint updates for a short settle window
- schedule a delayed `refresh()` after writes (especially power on/off)

## Hubitat Sandbox Families (Verified)

Hubitat driver failures cluster into three verified sandbox families. Audit all three whenever a driver hits one sandbox-related install or runtime problem.

### 1. Import allowlist

Hubitat rejects many JDK imports in drivers, including `java.util.zip.*`, `ByteArrayOutputStream`, and large parts of `java.io.*`, `java.nio.*`, and `java.security.*`.

For Tuya v3.3 CRC32, keep the implementation in pure Groovy:
- build a 256-entry lookup table once with `@Field static final long[] CRC32_TABLE`
- use canonical CRC-32/ISO-HDLC settings: init `0xFFFFFFFFL`, reversed polynomial `0xEDB88320L`, reflected byte updates, xor-out `0xFFFFFFFFL`
- return the unsigned 32-bit CRC as a `long`/`Long`, then write it big-endian into the Tuya frame
- keep the table at file scope, not inside the checksum method, so parse/send paths do not rebuild it on every frame

Reference shape:

```groovy
@Field static final long[] CRC32_TABLE = (0..255).collect { int n ->
    long c = n as long
    8.times {
        c = ((c & 1L) != 0L) ? (0xEDB88320L ^ (c >>> 1)) : (c >>> 1)
    }
    c & 0xFFFFFFFFL
} as long[]

private long crc32(byte[] data) {
    long crc = 0xFFFFFFFFL
    for (byte b : data) {
        crc = CRC32_TABLE[((int) (crc ^ (b & 0xFF))) & 0xFF] ^ (crc >>> 8)
    }
    return (crc ^ 0xFFFFFFFFL) & 0xFFFFFFFFL
}
```

### 2. Reflection blocked

Hubitat's sandbox restrictions are not limited to imports. Reflection-style runtime inspection is blocked in drivers too.

Avoid patterns like:
- `.getClass()` on exceptions or arbitrary values
- instance `.class` reads
- `.metaClass`, `.respondsTo()`, and `.hasProperty()`
- method/field introspection (`getMethods()`, `getFields()`, `getDeclaredMethods()`, etc.)
- `Class.forName()` and similar runtime type discovery
- method-pointer syntax (`someObj.&methodName`) when it depends on runtime method lookup

Safer replacements:
- log `e.message` instead of trying to print the exception class name
- use explicit `instanceof` checks or typed `catch (...)` blocks when behavior truly differs by type
- if the reflection was only diagnostic, log a generic fallback such as `"object"` instead of probing runtime metadata

### 3. App-only preference UI

Hubitat driver `preferences {}` is much narrower than app UI.

Safe driver patterns:
- use `input` only, with Hubitat-confirmed driver types: `bool`, `decimal`, `email`, `enum`, `number`, `password`, `phone`, `text`, `time`
- conditional gating with `if (settings.someField == ...) { input ... }` is valid in drivers

Do not use app-only constructs in drivers:
- `paragraph`
- `section`
- `href`
- `app`
- `mode`
- `pageDefault`

If you need explanatory copy for a group of driver preferences, fold it into each `input`'s `description:` instead of trying to render a section header or paragraph block.

These three families travel together: if a Hubitat driver trips one sandbox rule, audit the file for the other two before shipping.

## kkossev vs tinytuya Guidance

- **Use kkossev patterns for Hubitat style**: lifecycle, logging, defensive parsing, state hygiene
- **Use tinytuya / qwerk for Tuya WiFi framing details**: AES, packet structure, device22 query behavior

Do **not** assume kkossev's Zigbee Tuya drivers contain the WiFi/rawSocket protocol layer.

## Device Profile + DP Discovery Pattern

When only one Tuya model has been fully verified, prefer a three-tier profile strategy:

- **Tested profile** — hardcode the confirmed DP map and make it the default.
- **Generic profile** — only wire commands whose DPs are broadly stable for the category (for example power, heat level, or temperature setpoint).
- **Custom profile** — reveal per-role DP number inputs with `if (settings?.deviceProfile == "Custom")` and let users override only the roles that vary.

Hubitat-specific gotchas:
- Driver preference gating is reevaluated only after the user saves/reopens the device page, so Custom-only inputs need code-side defaults.
- Resolve DPs through a helper like `dpFor(role)` at command/parse time, not once during `updated()`, so new preference values apply immediately.

Recommended discovery commands:
- `discoverDPs()` — run a status query and log the typed DP dump at info level.
- `captureBaseline()` + `captureDiff()` — snapshot current DPs, then diff after a remote/app action.
- `setRawDP(dpId, value)` — allow auditable direct writes with bool/int/string coercion.

This keeps the driver honest about what is tested while still giving adjacent Tuya models a workable self-service mapping path inside Hubitat.

## Persistent Socket Pattern (v0.1.18+)

For drivers where real-time push updates matter (physical remotes, multi-controller setups), use a persistent socket rather than the open→send→close pattern.

### Lifecycle

```groovy
def initialize() {
    unschedule()
    closeSocket(false)          // stamps intentionalCloseAt; suppresses socketStatus callback
    // ... reset state ...
    runIn(1, "openSocket")      // 1s gap lets OS release the port
}

def openSocket() {
    try {
        interfaces.rawSocket.connect(settings.deviceIP, 6668, byteInterface: true, readDelay: 150)
        state.socketOpen = true
        state.reconnectAttempts = 0
        updateSocketState("open")
        unschedule("sendHeartbeat")
        runIn(HEARTBEAT_INTERVAL_SECONDS, "sendHeartbeat")
        runIn(2, "refresh")
    } catch (Exception e) {
        state.socketOpen = false
        updateSocketState("error")
        scheduleReconnect()
    }
}
```

### Heartbeat (20 s self-rescheduling)

> **Interval rationale:** Cypher's protocol research (`.squad/agents/cypher/history.md`) confirmed 20s is a verified-stable keep-alive interval for Tuya v3.3 Sideline hardware. Earlier guidance used 10s; bumping to 20s halves the scheduled-job + sendEvent load with no observed socket drops. Do not exceed 25s without hardware verification — Tuya idles connections at ~30s.

```groovy
@Field static final Integer HEARTBEAT_INTERVAL_SECONDS = 20

def sendHeartbeat() {
    if (!preferencesReady() || state.socketOpen != true) return
    try {
        byte[] frame = buildTuyaFrame(TUYA_CMD_HEARTBEAT, "")
        interfaces.rawSocket.sendMessage(hubitat.helper.HexUtils.byteArrayToHexString(frame))
        debugLog "Heartbeat sent"
    } catch (Exception e) {
        log.warn "[Driver] Heartbeat failed: ${e.message}"
        state.socketOpen = false
        unschedule("sendHeartbeat")
        scheduleReconnect()
        return
    }
    runIn(HEARTBEAT_INTERVAL_SECONDS, "sendHeartbeat")
}
```

> **Critical:** Tuya cmd 9 heartbeat must have truly empty payload (0 bytes, no AES encryption). Add this guard to `encryptTuyaPayload()`:
> ```groovy
> if (cmd == TUYA_CMD_HEARTBEAT) { return new byte[0] }
> ```
> Without it, `AES/ECB/PKCS5Padding` produces 16 bytes of padding for empty input — an invalid Tuya heartbeat.

### Reconnect with backoff

```groovy
@Field static final List<Integer> RECONNECT_DELAYS_SECONDS = [5, 30, 60, 300]

private void scheduleReconnect() {
    Integer attempt = safeInt(state.reconnectAttempts, 0)
    Integer delay = RECONNECT_DELAYS_SECONDS[Math.min(attempt, RECONNECT_DELAYS_SECONDS.size() - 1)]
    state.reconnectAttempts = (attempt + 1)
    updateSocketState("reconnecting")
    unschedule("reconnectSocket")
    runIn(delay, "reconnectSocket")
}

def reconnectSocket() {
    if (state.socketOpen == true) return
    openSocket()
}
```

### Suppress intentional-close callbacks

```groovy
private void closeSocket(Boolean markOffline) {
    state.intentionalCloseAt = now()    // 3-second grace window
    state.socketOpen = false
    unschedule("sendHeartbeat")
    try { interfaces.rawSocket.close() } catch (ignored) {}
    updateSocketState("closed")
}

def socketStatus(String message) {
    Long intentionalAt = safeLong(state.intentionalCloseAt, 0L)
    if ((now() - intentionalAt) < 3000L) {
        debugLog "socketStatus (intentional close, suppressed): ${message}"
        return
    }
    // handle real disconnect → scheduleReconnect()
}
```

> A boolean `intentionalClose` flag has a race condition: `initialize()` resets it before `socketStatus()` fires. The timestamp approach is safe — stale values from previous sessions have a large `(now() - intentionalAt)` diff and never suppress.

### pumpQueue without on-demand connect

In the persistent model, `pumpQueue()` should check a flag rather than try to connect:

```groovy
if (state.socketOpen != true) {
    debugLog "pumpQueue: socket not open; waiting for reconnect"
    return
}
```

After `openSocket()` succeeds, call `runIn(2, "refresh")` which triggers `enqueueRequest()` → `pumpQueue()` — any queued writes drain first (FIFO queue), then the refresh.

### responseTimeout — do NOT close socket

In the persistent model, do NOT close the socket on `responseTimeout`. Just requeue and retry:

```groovy
def responseTimeout() {
    if (state.awaitingResponse != true) return
    requeueInFlight()
    scheduleRetry("No response within timeout. Retrying.")
    // Do NOT call closeSocket() — keep the persistent connection alive
}
```

### socketState attribute

Add `attribute "socketState", "enum", ["open", "closed", "reconnecting", "error"]` to expose connection health on dashboards. Emit at info level on state transitions only:

```groovy
private void updateSocketState(String value) {
    String current = safeStr(device.currentValue("socketState"))
    if (current != value) {
        log.info "[Driver] socketState → ${value}"
        sendEvent(name: "socketState", value: value)
    }
}
```

### Push frame handling — free

Existing `parse()` → `processFrame()` → `applyDps()` pipeline already handles spontaneous STATUS frames (cmd 8). No separate push handler needed — the only change is keeping the socket open so those frames can arrive.

---



This repo's `.github/workflows/release.yml` parses each driver's top-of-file `Changelog:` block with the regex `^(\d+\.\d+\.\d+)\s+[—-]\s+(\d{4}-\d{2}-\d{2})\s+[—-]\s+(.*)$` (see line ~106).

When adding or editing driver changelog entries:
- use `version — YYYY-MM-DD — description`
- do **not** use full ISO 8601 timestamps like `2026-05-17T12:22:15-07:00`
- keep the parsable entries in the doc-comment `Changelog:` block even if separate prose `// vX.Y.Z` comments also exist

## Log Hygiene — trace vs debug split

Tuya protocol drivers produce two distinct categories of log output. Mixing them makes `logEnable=true` unreadable in production.

### Two-tier logging pattern

Add a `traceEnable` preference (bool, default false) alongside `logEnable`. Wire a `traceLog()` helper gated on `traceEnable` / `log.trace` that mirrors the existing `debugLog()` / `log.debug` helper.

```groovy
input name: "traceEnable", type: "bool",
      title: "Trace logging — very chatty (protocol-level wire debug only; auto-off after 30 minutes)",
      defaultValue: false
```

```groovy
private void traceLog(String message) {
    if (settings.traceEnable) {
        log.trace "[Driver] ${message}"
    }
}
```

Mirror the auto-disable pattern: in `updated()`, schedule `runIn(1800, "traceOff")` if `traceEnable`. Provide `traceOff()` that calls `device.updateSetting("traceEnable", [value: "false", type: "bool"])`.

### What goes at each level

**traceLog (firehose — off by default):**
- "Heartbeat sent" (every 20 s on a persistent socket)
- Heartbeat ACK received (cmd == TUYA_CMD_HEARTBEAT path)
- "Queued / Sent Tuya cmd N for refresh" — only when reason == "refresh" (periodic polling)
- "Decoded Tuya payload: {...}" — raw wire dump; always trace, never debug
- Read-only DP echoes (DPs confirmed unimplemented on a given firmware)
- Per-DP echo lines where the decoded value **equals** the current device attribute (nothing changed)

**debugLog (stays at debug — readable in production):**
- User-initiated writes: on/off, setFlameColor, setHeatLevel, etc.
- Per-DP echo lines where the decoded value **changed** from current device attribute
- Socket lifecycle: open, reconnect attempt, discovery mode transitions
- Protocol mode switches (device22 fallback, etc.)
- Errors / warnings — these are already at `log.warn`; leave them alone

### "Log only on change" rule for DP echoes

Before calling `debugLog` in `applyDps`, compare the decoded label against `device.currentValue(attributeName)`:

```groovy
if (device.currentValue("flameColor") != label) {
    debugLog "applyDps: DP ${dpId} = '${raw}' → '${label}' (changed)"
} else {
    traceLog "applyDps: DP ${dpId} = '${raw}' → '${label}' (unchanged)"
}
```

Apply this pattern to every DP handler that has an explicit log line in `applyDps`.

### Heartbeat ACK split

The heartbeat received-frame log must be split: route the TUYA_CMD_HEARTBEAT path to `traceLog` before the normal `debugLog` for all other commands:

```groovy
if (cmd == TUYA_CMD_HEARTBEAT) {
    traceLog "Received Tuya cmd ${cmd} retcode=${retcode} payloadLen=${payload.length}"
    return true
}
debugLog "Received Tuya cmd ${cmd} retcode=${retcode} payloadLen=${payload.length}"
```

### Refresh chatter

Periodic-refresh queue/send pairs should be trace, not debug:

```groovy
if (reason == "refresh") {
    traceLog "Queued Tuya cmd ${cmd} for ${reason}; pending=${queue.size()}"
} else {
    debugLog "Queued Tuya cmd ${cmd} for ${reason}; pending=${queue.size()}"
}
```

Apply the same conditional to the "Sent Tuya cmd" log in `pumpQueue()`.

---



## Idempotent Default Application

When a driver applies user-configured defaults at a lifecycle event (power-on, scene activation, schedule trigger), guard each DP write with a current-attribute check so the write is skipped if the device is already in the desired state.

### Pattern

```groovy
String current = device.currentValue("flameColor")
if (current != null && current == configuredDefault) {
    traceLog "applyOnDefaults: skipping defaultFlameColor — already '${configuredDefault}'"
} else {
    debugLog "applyOnDefaults: applying defaultFlameColor = '${configuredDefault}' (was '${current}')"
    // proceed with sendDpWrite(...)
}
```

### Rules

1. **null current = proceed.** `device.currentValue()` returns null when the driver has no prior observation (just installed, or no STATUS received yet). Treat null as "state unknown → apply the default". Skipping when state is unknown would silently fail to apply the user's preference.
2. **Each default is independent.** Evaluate all configured defaults separately. If flameColor matches but flameBrightness doesn't, skip only the flameColor write.
3. **Log hygiene:** skipped paths → `traceLog`; applied paths → `debugLog`. Consistent with the v0.1.22 trace/debug taxonomy.
4. **Profile guard unchanged.** The existing `if (!dpId) { log.warn }` branches apply before this check — skip-if-match only executes when the DP is mapped for the active profile.
5. **No protocol behavior change.** Timing, ordering, and the enqueue/pump mechanism are unchanged. This is purely a conditional guard around the existing write path.

### Why this matters on Tuya hardware

Some Tuya fireplace (and heater) models visibly flicker or emit an audible click when receiving a DP write even if the value is already set. Unconditional writes on every power-on are perceptible to the user. This guard eliminates the artifact while keeping the semantics correct.

### Applicable drivers

- **Touchstone Fireplace** — flameColor, flameBrightness, flameSpeed, charcoalColor (v0.1.23)
- **Gemstone Smart Heater** — zone default attributes applied at zone-activate time
- **SunStat Solar Control** — setpoint/mode defaults applied at schedule trigger
- Any future driver with a `defaultFoo` preference written at a lifecycle event

---



- [ ] Preferences: `deviceIP`, `deviceId`, `localKey(password)`, polling, `logEnable`, `traceEnable`
- [ ] Add `Device Profile` when only one model is fully mapped (`Tested` / `Generic` / `Custom`)
- [ ] `Switch`, `Refresh`, `Initialize`
- [ ] If the device exposes temperature, add `TemperatureMeasurement`
- [ ] Discovery commands: `discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`
- [ ] Hex-buffered `parse(String message)`
- [ ] CRC32 validation
- [ ] AES-ECB encrypt/decrypt helpers
- [ ] request queue + one-in-flight guard
- [ ] 5s / 15s / 30s retry backoff (command-level)
- [ ] **Persistent socket:** `openSocket()` + 20 s self-rescheduling heartbeat + reconnect backoff [5s, 30s, 60s, 300s]
- [ ] `socketState` attribute surfaced on dashboard
- [ ] `intentionalCloseAt` timestamp guard in `closeSocket()` / `socketStatus()` to suppress spurious reconnects
- [ ] delayed refresh after writes when the device has stale post-transition DPs
- [ ] **Idempotent defaults:** guard each `defaultFoo` DP write with a `currentValue` check (skip-if-match + traceLog)

---

## Write-Idempotency Audit Checklist

Apply this checklist when reviewing any Hubitat driver for redundant device writes. Earned from 2026-05-18 4-driver audit (Touchstone, Gemstone, SunStat parent+child).

### Questions to ask for every device-write path

1. **Is the write lifecycle-driven or user-explicit?**  
   Lifecycle writes (power-on defaults, timer callbacks, initialize recovery) fire without direct user intent. These are 🔴 candidates. User-explicit commands (setFlameColor, setHeatingSetpoint) are 🟡 — the user asked for it, but automation loops can still cause repetition.

2. **Does the driver check `device.currentValue()` or `state.*` before sending?**  
   If not: flag as redundant. The check must gate the actual device write, not just the `sendEvent` call.

3. **Is `emitIfChanged` present but the write below it unconditional?**  
   `emitIfChanged` deduplicates Hubitat events. It does NOT skip the device write. Both guards must be present independently. SunStat child `setScheduleEnabled` is the canonical example of the half-fix.

4. **Does the device produce a visible or audible side effect on receiving a no-op write?**  
   - Tuya local: yes — fireplaces click on any DP write regardless of value match → 🔴  
   - Cloud REST: usually no visible artifact → 🟡 (API quota only)  
   - Exception: `PUT /play/pattern` on Gemstone restarts animation → 🔴

5. **Is the non-idempotent write intentional (state-assertion)?**  
   "Restore after override" paths (boost cancel, reconnect recovery, asserting state after cloud drift) are BY-DESIGN. The write must happen unconditionally because the point is to defeat drift. Do not flag as a bug; document as BY-DESIGN.

6. **Does the path fire on every poll or periodic schedule?**  
   Periodic refresh handlers that *write* state (not just *read* it) are always 🔴 — they generate constant traffic. Refresh handlers that only call `sendEvent` based on read data are fine (Hubitat deduplicates events).

7. **For composite writes (pattern, color), can you check a single attribute?**  
   Some writes bundle multiple values (Gemstone pattern = colors + brightness + animation). A single `currentValue` check may not be sufficient. Options: compare `effectName` attribute, compare `state.lastPattern.id`, or accept 🟡 and skip the guard.

### Severity classification

| Signal | Severity |
|---|---|
| Tuya DP write causes audible click or visible artifact | 🔴 |
| Cloud API `play/pattern` restarts animation | 🔴 |
| Lifecycle-driven path (fires automatically, not on explicit user command) | 🔴 |
| Cloud REST call consumes API quota; no visible device effect | 🟡 |
| Explicit user command; user intent is clear | 🟡 |
| `sendEvent` without `emitIfChanged` (Hubitat event flood only; no device write) | 🟢 |
| Write is intentional state-assertion to defeat drift or recovery | BY-DESIGN |

### Fix pattern (skip-if-match)

```groovy
// Example: guard a user command before the device write
String current = safeStr(device.currentValue("flameColor"))
if (current != null && current == label) {
    debugLog "setFlameColor: already '${label}' — skipping DP write"
    return
}
// proceed with sendDpWrite(...)
```

Null-current rule: `device.currentValue()` returns `null` when the driver has no prior observation. Treat `null` as "state unknown → apply the write." Only skip when current matches the target.

## Hot-path Byte Helper Hygiene

When Tuya v3.3 drivers assemble or slice frames on every send/receive cycle, keep the helpers on plain `byte[]` plus primitive `int` math. Boxed `Integer` loop counters inside `concatBytes()`, `sliceBytes()`, `startsWithBytes()`, and `protocol33HeaderBytes()` add avoidable autoboxing overhead in the hottest part of the driver.

### Preferred pattern

- Use `System.arraycopy(...)` for contiguous copies (`concatBytes`, `sliceBytes`, protocol header prepend).
- Reserve manual loops for byte-by-byte comparisons only, and make the counter a primitive `int`.
- Do **not** swap to `ByteArrayOutputStream`, `java.nio`, or reflection-based helpers; the Hubitat sandbox/import allowlist makes the simple `byte[]` helpers the safest portable choice.

```groovy
private byte[] sliceBytes(byte[] source, int start, int length) {
    byte[] copy = new byte[length]
    System.arraycopy(source, start, copy, 0, length)
    return copy
}

private Boolean startsWithBytes(byte[] data, byte[] prefix) {
    if (!data || !prefix || data.length < prefix.length) {
        return false
    }
    for (int i = 0; i < prefix.length; i++) {
        if (data[i] != prefix[i]) {
            return false
        }
    }
    return true
}
```
