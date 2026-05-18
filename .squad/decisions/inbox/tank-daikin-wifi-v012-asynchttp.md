# Decision: daikin-wifi v0.1.2 — Switch to asynchttpGet

**Filed by:** Tank  
**Date:** 2026-05-18  
**Status:** Adopted  
**Driver:** `drivers/daikin-wifi/daikin-wifi.groovy`

---

## Context

v0.1.0 used `HubAction(Map, Protocol, Map)` (3-arg) for LAN HTTP GET. That constructor does not exist on Mads's firmware. v0.1.1 switched to `HubAction(Map, Protocol)` (2-arg). That constructor **also** does not exist on Mads's firmware.

Error on v0.1.1:
```
warn [Daikin] sendGet failed (/aircon/get_control_info): Could not find matching constructor for: hubitat.device.HubAction(java.util.LinkedHashMap, hubitat.device.Protocol)
```

Two iterations of HubAction constructor guessing failed. The Map-based HubAction API is broken on Mads's firmware. We stop guessing.

---

## Decision

Switch `sendGet()` from `HubAction` to `asynchttpGet` — the modern, documented Hubitat HTTP-over-LAN API.

All six callback handlers updated to `(hubitat.scheduling.AsyncResponse response, Map data)` signature. Body extraction changed from `response?.body` to `response.getData()`. `checkHttpOk()` updated to use `response.hasError()` instead of `response.status != 200`.

---

## Correction to Earlier Team Memo

**Trinity's v0.1.0 performance memo stated:** "asynchttpGet is for cloud HTTPS calls."

**That claim is incorrect.** `asynchttpGet` works for any HTTP URL including local LAN (e.g., `http://192.168.1.50/aircon/get_control_info`). It is the documented, stable Hubitat API for all async HTTP on device drivers, LAN and cloud alike. The correct scope distinction is:
- `asynchttpGet` — any HTTP URL, LAN or cloud, async callback pattern
- `HubAction(LAN)` — raw Hubitat socket dispatch; useful for non-HTTP protocols (e.g., raw TCP, UDP, Zigbee commands)

Future drivers (Sunstat, Gemstone cloud calls, any new LAN HTTP driver) should use `asynchttpGet` by default.

---

## Reusable Pattern

See `.squad/skills/hubitat-asynchttpget-pattern/SKILL.md` for the canonical `sendGet` + callback template applicable to future LAN HTTP drivers.

---

## Files Changed

- `drivers/daikin-wifi/daikin-wifi.groovy` — v0.1.2 (commit follows)
- `drivers/daikin-wifi/packageManifest.json` — version bumped to 0.1.2
