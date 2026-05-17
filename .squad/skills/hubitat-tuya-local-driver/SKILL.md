# SKILL: hubitat-tuya-local-driver

**Pattern:** WiFi IoT device controlled via Tuya Local (LAN) on Hubitat — single driver with `Switch` + `SwitchLevel` + `ColorControl` + custom secondary-color commands, using `interfaces.rawSocket` TCP + AES + DP encoding.

---

## When to Use

Apply this pattern when:
- The target device is a Tuya-based WiFi device (fireplace, LED controller, smart plug, etc.)
- Local LAN control is preferred over Tuya Cloud API (always true for Hubitat drivers)
- The device has one or more color zones, brightness, and power on/off

Avoid this pattern if:
- The device only supports cloud control (fall back to Tuya Cloud App + child driver pattern)
- The device is Zigbee or Z-Wave (use Hubitat's built-in inclusion)

---

## Architectural Shape

**Single Groovy driver.** No parent/child needed for single-device products. Parent/child is only justified when managing a fleet of Tuya devices from one Hubitat entry point.

---

## Capability Set

| Capability | Purpose |
|---|---|
| `Actuator` | Always include — marks device as controllable |
| `Switch` | Power on/off |
| `SwitchLevel` | Brightness 0–100% (normalize from Tuya's 0–1000 range) |
| `ColorControl` | Primary color in HSV; **only use if the device exposes a free-form RGB/HSV DP**. If the device uses palette indices (string `"1"`/`"2"` etc.), use named custom commands instead — ColorControl will produce confusing rounding behavior against palette-only DPs. |
| `Refresh` | Explicit state re-poll |
| `Initialize` | Reconnect rawSocket, re-register scheduling |

**Custom secondary color zones:** Use a custom attribute (e.g. `logColor` as hex string) and custom command (`setLogColor(hex)`) for additional color zones beyond what `ColorControl` can express. One ColorControl capability = one color slot.

---

## Protocol Layer

### Connection
```groovy
interfaces.rawSocket.connect(settings.deviceIP, 6668, byteInterface: true)
```
- Port 6668 is standard for Tuya Local
- `byteInterface: true` required for binary framing

### Message Framing (Tuya 3.3)
```
[prefix: 0x000055AA][seqNo: 4B][cmd: 4B][len: 4B][payload_encrypted][suffix: 0x0000AA55]
```
- Payload is JSON, AES-128-ECB encrypted, PKCS#7 padded
- `localKey` (16 bytes) is the AES key
- SeqNo is monotonically incrementing per session

### Protocol Versions
| Version | Encryption | Notes |
|---|---|---|
| 3.3 | AES-128-ECB | Most WiFi devices; localKey from tinytuya |
| 3.4 | AES-128-GCM + session key | Newer modules; session key negotiation needed |
| 3.5 | AES-128-GCM + session key | Latest; rare in 2024 |

Confirm version from `tinytuya scan` output before implementing.

### Groovy AES-128-ECB (sandbox-safe)
```groovy
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

String tuyaEncrypt(String plaintext, String localKey) {
    byte[] keyBytes = localKey.getBytes("UTF-8")
    SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES")
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    return cipher.doFinal(plaintext.getBytes("UTF-8")).encodeBase64().toString()
}
```
`javax.crypto` is available in the Hubitat sandbox (confirmed by community drivers).

---

## DP (Data Point) Map

DPs are device-firmware-specific. Extract from `tinytuya wizard` or `tinytuya scan`:

```bash
pip install tinytuya
python -m tinytuya wizard   # interactive — may require Tuya cloud account
python -m tinytuya scan     # passive scan — no account needed for IP/version
```

Typical DP assignments (NOT universal — always verify):

| DP | Common Meaning |
|---|---|
| 1 | Power (bool: true/false) |
| 2 or 22 | Mode (e.g. "colour", "white", "scene") |
| 3 or 23 | Brightness (0–1000) |
| 5 or 24 | Color (HSV hex string "HHHHSSSSVVVV" — 4-digit each) |
| 20 | Power (bool) — newer devices |

For LED fireplaces with dual color zones, a second color DP (e.g. DP 6 or 25) controls the secondary zone. Confirm from tinytuya output.

---

## rawSocket Stability Pattern

Hubitat's rawSocket drops silently on idle. Required mitigations:

1. **Keepalive ping every 20 seconds** — send a Tuya heartbeat command (cmd = 0x09, empty payload)
2. **Reconnect in `parse()`** — if `parse()` receives a close notification (or stops being called), call `interfaces.rawSocket.connect(...)` again
3. **Reconnect in `sendCommand()`** — if socket is not connected when sending, reconnect first, then send
4. **Exponential back-off** — don't hammer reconnect on repeated failure; use `runIn(30, "reconnect")` pattern

```groovy
def socketStatus(String message) {
    if (message.contains("receive error") || message.contains("Connection reset")) {
        log.warn "${device.displayName} socket disconnected — scheduling reconnect"
        runIn(5, "reconnect")
    }
}

def reconnect() {
    try {
        interfaces.rawSocket.connect(settings.deviceIP, 6668, byteInterface: true)
    } catch (e) {
        log.error "${device.displayName} reconnect failed: ${e.message}"
        runIn(30, "reconnect")
    }
}
```

---

## Local Key Extraction (One-Time Setup)

User must extract their device's `localKey` before the driver can function:

1. Install Python: `pip install tinytuya`
2. Run: `python -m tinytuya wizard` (may require Tuya IoT Platform account for key retrieval)
   - Alternative: `python -m tinytuya scan` discovers IP + protocol version without an account, but may not expose localKey
3. Copy `deviceId`, `ip`, and `localKey` from the output JSON to Hubitat driver preferences

**Risk (2026):** Tuya has tightened cloud API access. Confirm whether wizard's key-retrieval path requires a Tuya IoT developer account. If yes, evaluate whether that's an acceptable user burden before shipping the driver.

---

## Borrow Sources

Protocol layer (rawSocket + AES framing) — **no ready-made Hubitat Tuya WiFi driver found in community search (2026-05-17)**. Implement the framing from spec, using tinytuya `core.py` or `rospogrigio/localtuya` Python code as the reference:
- **tinytuya/core.py** — canonical Python framing for v3.1–3.5; translate to Groovy
- **rospogrigio/localtuya** — battle-tested Python framing for v3.1–3.4

**kkossev/Hubitat** — his drivers are **Zigbee Tuya only** (use `sendZigbeeCommands()`, not rawSocket). Do NOT borrow the protocol layer from kkossev. His repo IS useful for: DP value encoding patterns, state management conventions, Groovy sandbox patterns.

---

## Touchstone Sideline — Confirmed DP Map

Source: `make-all/tuya-local` `touchstone_sideline_fireplace.yaml` (product ID `qhwld7e4eqvu5fbp`)  
Captured from real device — confirmed 2026-05-17.

| DP | Type | Meaning | Values |
|---|---|---|---|
| 1 | boolean | Power | `true`/`false` |
| 2 | integer | Temp setpoint | 19–30°C or 67–86°F |
| 3 | integer | Current temp | read-only |
| 5 | string | Preset | `"0"`=eco, `"1"`=comfort, `"2"`=boost |
| 13 | string | Temp unit | `"c"` or `"f"` |
| 101 | string | **Flame effect/color** | `"1"`=Orange, `"2"`=Blue, `"3"`=Yellow, `"4"`=Orange+Blue, `"5"`=Orange+Yellow, `"6"`=Blue+Yellow |
| 102 | string | **Flame brightness** | `"1"`–`"5"` → 20%–100% |
| 103 | string | **Flame speed** | `"1"`=Slow, `"2"`=Medium, `"3"`=Fast |
| 104 | string | **Ember/log color** | `"1"`=orange, `"2"`=red, `"3"`=blue, `"4"`=yellow, `"5"`=green, `"6"`=purple, `"7"`=teal, `"8"`=pink, `"9"`=white, `"10"`=peachpuff, `"11"`=off, `"12"`=grey |
| 105 | string | **Log brightness** | `"1"`–`"12"` = 8%–100% |
| 107 | boolean | Heat-disable flag | separates heat/fan_only mode |
| 108 | boolean | Child lock | `true`=locked |
| 109 | string | **Ember brightness** | `"L0"`=off, `"L1"`–`"L5"` = 20%–100% |

> ⚠️ Colors are **named palette indices**, not RGB or HSV. Do NOT use `ColorControl` capability. Use named custom commands (`setFlameColor(name)`, `setLogColor(name)`).


---

## Driver Preferences Template

```groovy
preferences {
    input name: "deviceIP",   type: "text",     title: "Device IP address", required: true
    input name: "deviceId",   type: "text",     title: "Device ID (from tinytuya)", required: true
    input name: "localKey",   type: "password", title: "Local Key (16 chars, from tinytuya)", required: true
    input name: "pollInterval", type: "enum",
          options: ["1":"1 minute","5":"5 minutes (recommended)","10":"10 minutes","0":"Disabled"],
          defaultValue: "5", required: true
    input name: "logEnable",  type: "bool", title: "Enable debug logging (auto-off 30 min)", defaultValue: false
    input name: "txtEnable",  type: "bool", title: "Enable descriptionText logging", defaultValue: true
}
```

---

## Conventions (match Gemstone)

- `@Field static final` for all constants; no cross-field initialization
- `logEnable`/`txtEnable` pair; `runIn(1800, "logsOff")` auto-disable
- Optimistic `sendEvent` before sending command
- `state.*` for caching device state; `atomicState.*` only on demonstrated race condition
- `descriptionText`-prefixed event descriptions; gate info events on `txtEnable`
- `installed()` sets default preferences; `updated()` unschedules and reinitializes; `initialize()` wires scheduling and reconnects socket
- Namespace: `"mads"`; folder + file: `kebab-case`
