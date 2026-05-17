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

## Repo Release Workflow Changelog Format

This repo's `.github/workflows/release.yml` parses each driver's top-of-file `Changelog:` block with the regex `^(\d+\.\d+\.\d+)\s+[—-]\s+(\d{4}-\d{2}-\d{2})\s+[—-]\s+(.*)$` (see line ~106).

When adding or editing driver changelog entries:
- use `version — YYYY-MM-DD — description`
- do **not** use full ISO 8601 timestamps like `2026-05-17T12:22:15-07:00`
- keep the parsable entries in the doc-comment `Changelog:` block even if separate prose `// vX.Y.Z` comments also exist

## Reusable Checklist

- [ ] Preferences: `deviceIP`, `deviceId`, `localKey(password)`, polling, `logEnable`
- [ ] Add `Device Profile` when only one model is fully mapped (`Tested` / `Generic` / `Custom`)
- [ ] `Switch`, `Refresh`, `Initialize`
- [ ] If the device exposes temperature, add `TemperatureMeasurement`
- [ ] Discovery commands: `discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`
- [ ] Hex-buffered `parse(String message)`
- [ ] CRC32 validation
- [ ] AES-ECB encrypt/decrypt helpers
- [ ] request queue + one-in-flight guard
- [ ] 5s / 15s / 30s retry backoff
- [ ] idle socket close to reduce Tuya single-client contention
- [ ] delayed refresh after writes when the device has stale post-transition DPs
