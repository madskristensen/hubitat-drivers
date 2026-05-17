---
name: "tuya-key-extraction-auth-paths"
description: "2026 map of all known Tuya local_key extraction paths — which are portal-free, which require iot.tuya.com, and which are broken. Must re-verify annually."
domain: "protocol"
confidence: "high"
source: "earned — Cypher session 7, 2026-05-17. Sources: make-all/tuya-local cloud.py + const.py, tuya-device-sharing-sdk user.py, tuyapi SETUP.md, localtuya README."
---

## Context

Use this skill when a user asks how to get the local_key for a Tuya device, or when evaluating whether a Tuya-based Hubitat driver can be built without requiring the user to register at iot.tuya.com.

## The Core Problem

Every Tuya v3.3+ device encrypts all LAN traffic with AES-128-ECB using a 16-byte `local_key`. The key is assigned by Tuya's cloud during device provisioning and is NOT broadcast over the LAN. You must retrieve it from Tuya's cloud — the question is which cloud endpoint and what credentials it requires.

## 2026 Path Map

| Path | Portal-free? | Works? | Prerequisites |
|---|---|---|---|
| `make-all/tuya-local` cloud-auth | **Yes** | Yes — but fragile | Home Assistant installed + SmartLife app |
| `tinytuya wizard` | **No** | Yes | iot.tuya.com developer account (free, 20 min) |
| `localtuya` (HA) cloud config | **No** | Yes | iot.tuya.com Client ID + Secret + User ID |
| `tuya-cli link` (MITM) | Yes | **No — broken** | DEPRECATED; Tuya encrypts app traffic |
| Smart Life ADB backup | Yes | **No — blocked** | `allowBackup=false`; requires rooted phone |
| BLE provisioning channel | Yes | **Not applicable** | Local key not transmitted over BLE |
| `tuyapi` consumer API | Yes (in theory) | **No working tool** | No maintained path in 2026 |

## Portal-Free Path: `make-all/tuya-local` Cloud-Auth

### How it works (confirmed from source code)
1. HA calls `POST https://apigw.iotbing.com/v1.0/m/life/home-assistant/qrcode/tokens?clientid=HA_3y9q4ak7g4ephrvke&usercode={code}&schema=haauthorize`
2. Returns a QR token. HA displays as QR image.
3. User scans with **Smart Life** or **Tuya Smart** app (not OEM brand apps).
4. HA polls `GET /v1.0/m/life/home-assistant/qrcode/tokens/{token}?...` until user scans.
5. Returns `access_token`, `refresh_token`, `endpoint`. HA stores as auth cache.
6. HA calls consumer Smart Life API to enumerate devices → returns list with `local_key` per device.
7. User picks device. `local_key` stored in HA config entry.

### Fragility factors
- `client_id = "HA_3y9q4ak7g4ephrvke"` is hardcoded in `custom_components/tuya_local/const.py`. Tuya can revoke it. If that happens, all `make-all/tuya-local` users lose the cloud-auth path simultaneously.
- Requires Home Assistant — not a standalone CLI tool.
- `apigw.iotbing.com` is the consumer Smart Life API gateway, separate from `iot.tuya.com`. Changes to that API can break the flow.
- SmartLife app must be the one scanning. OEM brand apps will not authenticate against `haauthorize`.

## Portal Path: `tinytuya wizard`

1. Create free account at [iot.tuya.com](https://iot.tuya.com/)
2. Create Cloud Project; subscribe IoT Core + Authorization + Smart Home Scene Linkage APIs
3. Link Tuya App Account via QR code in the project
4. Run `python -m tinytuya wizard` with API Key + Secret
5. `devices.json` output contains `id`, `key` (local_key), `ip` per device

Key is permanent until device is factory-reset or re-paired. Developer portal trial is now **1 month**, renewable every 6 months — only affects re-extraction, not usage of an already-extracted key.

## Dead Ends (2026)

### `tuya-cli` MITM
Explicitly deprecated in the [tuyapi SETUP.md](https://github.com/codetheweb/tuyapi/blob/master/docs/SETUP.md): *"This method is deprecated because Tuya-branded apps have started to encrypt their traffic in an effort to prevent MITM attacks like this one."* Broken since ~2022.

### Smart Life ADB backup
SmartLife app (`com.tuya.smartlife`) sets `allowBackup=false` in its manifest. `adb backup` is blocked at the OS level. Rooted phone direct filesystem access (`/data/data/com.tuya.smartlife/`) would expose a SQLite DB, but it is encrypted with SQLCipher; key derivation requires APK reversing. Not practical for users.

### BLE provisioning
Tuya BLE provisioning sends WiFi SSID + password from phone to device. The `local_key` is a server-side-generated value returned by Tuya cloud during the claim handshake — it is **never transmitted over BLE**. No BLE-based key extraction exists.

### `tuyapi` consumer API
No maintained reverse-engineered tool authenticates against `a1.tuyaus.com` or `px1.tuyaus.com` and returns `local_key` values. The API surface changes with app updates. MITM (the only previous approach) is broken.

## Key Characteristics of the `local_key`

- 16 bytes (32 hex chars), assigned by Tuya cloud at device provisioning
- Stable — does NOT change on reboots or firmware updates
- **Changes only when the device is factory-reset, unpaired, or re-paired to a different account**
- Directly usable in Hubitat drivers (no periodic re-extraction needed under normal use)
- v3.3 uses AES-128-ECB with this key; v3.4/v3.5 uses it as a base key for session-key negotiation (more complex)

## Advice for Hubitat Drivers

When writing a Hubitat driver README for a Tuya device:
1. **Recommend `tinytuya wizard`** as the primary path — it is well-documented, has a step-by-step PDF, and does not depend on HA infrastructure. The iot.tuya.com registration is free and takes ~20 minutes.
2. **Mention `make-all/tuya-local` cloud-auth** as an alternative for users who already run HA — faster UX, but note the HA prerequisite explicitly.
3. **Do not mention MITM, ADB, or BLE** paths — they are all broken or inapplicable.
4. **Note that the key is permanent** — users only need to extract it once.

## When to Re-Verify This Skill

- If `make-all/tuya-local` starts failing cloud-auth (check their GitHub issues)
- If Tuya announces changes to the consumer Smart Life API
- Annually — Tuya changes their developer portal policies and API surfaces frequently
