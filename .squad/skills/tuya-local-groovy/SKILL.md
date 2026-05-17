---
name: "tuya-local-groovy"
description: "Implement Tuya Local v3.3 Groovy drivers on Hubitat using rawSocket, AES-128-ECB, queued retries, and defensive frame parsing."
domain: "hubitat-drivers"
confidence: "high"
source: "earned — Tank Touchstone fireplace scaffold, 2026-05-17; protocol cross-checked against tinytuya XenonDevice/message_helper/header and qwerk's Hubitat Tuya RGBW driver."
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

## kkossev vs tinytuya Guidance

- **Use kkossev patterns for Hubitat style**: lifecycle, logging, defensive parsing, state hygiene
- **Use tinytuya / qwerk for Tuya WiFi framing details**: AES, packet structure, device22 query behavior

Do **not** assume kkossev's Zigbee Tuya drivers contain the WiFi/rawSocket protocol layer.

## Reusable Checklist

- [ ] Preferences: `deviceIP`, `deviceId`, `localKey(password)`, polling, `logEnable`
- [ ] `Switch`, `Refresh`, `Initialize`
- [ ] If the device exposes temperature, add `TemperatureMeasurement`
- [ ] Hex-buffered `parse(String message)`
- [ ] CRC32 validation
- [ ] AES-ECB encrypt/decrypt helpers
- [ ] request queue + one-in-flight guard
- [ ] 5s / 15s / 30s retry backoff
- [ ] idle socket close to reduce Tuya single-client contention
- [ ] delayed refresh after writes when the device has stale post-transition DPs
