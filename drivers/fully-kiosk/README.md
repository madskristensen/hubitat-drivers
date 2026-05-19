# Fully Kiosk Browser

A Hubitat driver for controlling **Fully Kiosk Browser** on wall-mounted Android tablets via the local REST API.

> **Fork of:** [GvnCampbell/Hubitat](https://github.com/GvnCampbell/Hubitat/blob/master/Drivers/FullyKioskBrowserController.groovy) (v1.41)
> **Upstream status:** Maintainer silent since 2021-11-20 (4.5+ years). Not accepting changes.

---

## What this is

Controls a Fully Kiosk Browser instance over your local network. The tablet pushes events (motion, screen state, battery, volume) back to Hubitat via HTTP, and Hubitat sends commands to the tablet (screen on/off, brightness, load URL, TTS, siren, etc.).

**v0.4.2** adds `clearOverlayMessage()` — call it after `setOverlayMessage(text)` or `deviceNotification(text)` to dismiss the popup.

**v0.4.1** fixes three bugs reported from live tablet testing: NPE guard in `beep()` when `toneFile` is unset; HTTP 408/5xx callbacks demoted from `error` to `warn` (transient unreachable is normal); and **⚠️ BREAKING** removal of `setScreenBrightness` — migrate RM rules to `setLevel(0-100)`.

**v0.4.0** adds opt-in MQTT subscription: when configured, screen state, motion, battery, charging, foreground app, and all other v0.3.0 sensor attributes update near-instantly via push instead of waiting for the next poll cycle. Leave the MQTT broker preference blank for exact v0.3.0 polling behavior.

Tested on Mads Kristensen's two wall-mounted tablets (Bathroom + Kitchen).

---

## Install

### Hubitat Package Manager (HPM)

1. Open HPM → **Install** → **Search by URL**
2. Paste:
   ```
   https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/fully-kiosk/packageManifest.json
   ```
3. Follow the prompts.

### Manual

1. Open **Drivers Code** in Hubitat → **New Driver**
2. Paste the contents of [`fully-kiosk.groovy`](fully-kiosk.groovy) → **Save**
3. Create a new virtual device and select **Fully Kiosk Browser** as the driver.

### Configuration

1. Set **Server IP Address**, **Server Port** (default: 2323), and **Server Password** in Preferences.
2. Click **Save Preferences**.
3. Click **Configure** once — this injects the JavaScript event hooks into Fully Kiosk Browser so the tablet reports motion, screen, battery, and volume events back to Hubitat.
4. Enable **State Polling** if your tablet's start page uses HTTPS (prevents the tablet from pushing back to a non-HTTPS Hubitat hub endpoint).

### MQTT setup (v0.4.0, optional)

Requires Fully Kiosk Browser v1.34+ and Hubitat firmware 2.4.4.155+ (built-in MQTT broker).

1. **In the Fully Kiosk Browser app** (on each tablet): open Settings → MQTT → enable MQTT and set the broker URL to `tcp://{your-hub-ip}:1883`. Set the topic prefix (e.g. `fully-bathroom` for unique per-device filtering).
2. **In Hubitat**, open the driver Preferences:
   - Set **MQTT broker URL** to `tcp://localhost:1883` (hub's built-in broker) or `tcp://{hub-ip}:1883`.
   - Set **MQTT topic prefix** to match the FKB setting (e.g. `fully-bathroom`). **Use a unique prefix per tablet** when multiple tablets share the same broker — otherwise both driver instances receive each other's events.
   - Optionally set a custom Client ID, username, and password if your broker requires authentication.
3. Click **Save Preferences**. The driver connects immediately; the live log shows `MQTT connected`.

> **Tip:** The hub's built-in broker (firmware 2.4.4.155+) requires no external Mosquitto server. Simply use `tcp://localhost:1883` as the broker URL on both the tablet and the driver.

**What changes with MQTT:** Screen on/off, motion, battery level, charging state, and foreground app events arrive near-instantly instead of waiting for the polling interval. REST commands (brightness, load URL, TTS, etc.) are unchanged — FKB receives commands via REST only.

**What stays polling:** `level` (screen brightness), `currentPageUrl`, `screensaverActive`, `batteryTemperature`, `screenOrientation`, `kioskMode` are updated from FKB's `deviceInfo` MQTT publish. The poll interval is reduced to 5 minutes as a heartbeat safety net while MQTT is connected (restored to 1 minute if MQTT disconnects).

---

## What's fixed (v0.1.0 vs upstream v1.41)

### Fix 1 — Security: Password no longer leaked in debug logs (MAJOR)

**Original:** `serverPassword` was declared as `type:"string"` (cleartext in the UI) and the full `postParams` map — including the URI with `?password=<yourpassword>` in the query string — was dumped to the debug log on every command and refresh call.

**Fix:** Changed preference to `type:"password"` (masked in the Hubitat UI). Before logging `postParams`, the URI is cloned and the password value is replaced with `***`:
```groovy
Map safeParams = postParams.clone()
safeParams.uri = safeParams.uri?.replaceAll(/(?i)password=[^&]+/, 'password=***')
logger(logprefix + safeParams)
```
Applies to both `sendCommandPost()` and `refresh()`.

### Fix 2 — Event hygiene: `refreshCallback()` now uses emitIfChanged (MAJOR)

**Original:** Every 1-minute poll (when State Polling is enabled) unconditionally called `sendEvent` for `battery`, `switch`, `level`, and `currentPageUrl` — even when nothing changed. This generated **5,760+ low-value events per day** across 4 attributes per device (× 2 tablets = 11,520+ events/day).

**Fix:** All four attributes in `refreshCallback()` are now gated through `emitIfChanged()`, which compares the incoming value to `device.currentValue()` before firing. Events are only emitted when the value actually changes.

### Fix 3 — Event hygiene: `parse()` sendEvents missing descriptionText (MAJOR)

**Original:** All `sendEvent` calls in `parse()`, `motion()`, and `acceleration()` omitted `descriptionText`, leaving the **Description column blank** in Hubitat's Events tab for every tablet-pushed event.

**Fix:** Added `descriptionText: "${device.displayName} ${attribute} is ${value}"` to every `sendEvent` in the parse path.

### Fix 4 — Logger severity inversion (MINOR)

**Original:** The `logger()` function used a custom multi-level enum (`none/debug/trace/info/warn/error`) with an inverted severity hierarchy: selecting `debug` logged *everything*, while selecting `trace` logged *less* (only trace and info types). This is the opposite of standard logging convention.

**Fix:** Replaced with the Hubitat community-standard `logEnable` boolean preference. All `trace`/`debug` output is gated by `logEnable`; `info`/`warn`/`error` always emit regardless of the setting.

---

## Security note

The Fully Kiosk Browser REST API requires the device password as a plain-text query parameter in every HTTP request (e.g. `?password=yourpassword&cmd=...`). This is **FKB's own protocol design** — the driver cannot change how FKB authenticates requests. The password travels in cleartext on your local LAN.

Practical mitigations:
- Keep your Fully Kiosk Browser device on a trusted LAN segment (not exposed to the Internet).
- Use a dedicated, non-reused password for your FKB devices.
- The `serverPassword` preference is stored as `type:"password"` in Hubitat, so it is masked in the Hubitat UI and not accessible to other drivers.
- Debug logs never expose the password: every URI is cloned and the password value is replaced with `***` before logging (see Fix 1 in the v0.1.0 changelog below).

---

## Upstream status

**NOT planned for upstream PR.** GvnCampbell has made no commits to [GvnCampbell/Hubitat](https://github.com/GvnCampbell/Hubitat) since 2021-11-20 (4.5+ years of silence). Issues and PRs go unreviewed. This fork is the active maintenance path.

---

## Attribution

Original driver written by **Gavin Campbell (GvnCampbell)** — version 1.41.
- Source: https://github.com/GvnCampbell/Hubitat/blob/master/Drivers/FullyKioskBrowserController.groovy
- Community thread: https://community.hubitat.com/t/release-fully-kiosk-browser-controller/12223

The original source has no explicit license file. The copyright block from the original is preserved verbatim inside [`fully-kiosk.groovy`](fully-kiosk.groovy). This fork is maintained under the same spirit of open community sharing.

Fork maintained by **Mads Kristensen** — https://github.com/madskristensen

---

## Changelog

| Version | Date       | Notes |
|---------|------------|-------|
| 0.4.5   | 2026-05-18 | **BUG: invalid `mqttConnect()` signature causing live connect failure.** v0.4.0 called `interfaces.mqtt.connect(brokerUrl, clientId, username, password, optionsMap)` — a 5-arg form with a `Map` for LWT that Hubitat does not recognise (live error: *"No signature of method … applicable for argument types … java.util.LinkedHashMap"*). Switched to Hubitat's valid 8-arg form: `interfaces.mqtt.connect(brokerUrl, clientId, username, password, lwtTopic, lwtQos, lwtRetained, lwtPayload)`. LWT behaviour (`{prefix}/hubitat/state = "offline"`, retained, QoS 1) is fully preserved. |
| 0.4.4   | 2026-05-18 | **BUG: defensive leading-slash handling for MQTT topics.**FKB docs publish to `/fully/event/...` with a leading slash; in MQTT, `/fully/...` and `fully/...` are technically different topic namespaces. Driver now subscribes to both `{prefix}/#` and `/{prefix}/#` so either form is received, then strips any leading slash at the top of `parseMqttMessage` so the downstream dispatch works identically either way. Belt-and-suspenders fix for the topic-namespace ambiguity in the FKB documentation. |
| 0.4.3   | 2026-05-18 | **BUG: fix 4 event-name mismatches in `handleFkEvent()`.** Driver was listening for `motionDetected`/`unpluggedAC`/`pluggedAC`/`batteryLevel` but FKB publishes `onMotion`/`unplugged`/`pluggedAC`/`onBatteryLevelChanged` (verified against official FKB MQTT docs by Cypher). Renamed cases accordingly. Removed dead `foregroundApp` case — FKB does not publish that as an event; it arrives via the `deviceInfo` topic and is handled in `handleFkDeviceInfo()`. MQTT push events for motion, charging state, and battery level now actually fire. |
| 0.4.2   | 2026-05-18 | New command: `clearOverlayMessage()` — clears any active overlay message popup on the tablet. Calls FKB's `setOverlayMessage` REST endpoint with empty `text` parameter (the documented dismiss pattern). Complements `setOverlayMessage(text)` and `deviceNotification(text)` — both show overlays, `clearOverlayMessage()` dismisses them. |
| 0.4.1   | 2026-05-18 | **BUG: NPE guard in `beep()`**— when `toneFile` preference is unset, logs a `warn` instead of throwing NPE. **Transient HTTP errors demoted:** 408 (timeout) and 5xx responses in all HTTP callbacks now log at `warn` instead of `error` (tablet sleeping or network blip is normal transient behavior). **⚠️ BREAKING: `setScreenBrightness(N)` command removed.** This command accepted raw 0–255 values and was confusing alongside `setLevel(0-100)` (SwitchLevel capability). **Migrate any Rule Machine rules that call `setScreenBrightness(N)` to use `setLevel(N)` with N in the 0–100 range instead.** |
| 0.4.0   | 2026-05-18 | **MQTT subscriber (opt-in).** When `mqttBroker` preference is set, driver connects to the broker via `interfaces.mqtt`, subscribes to `{prefix}/#`, and routes FK event pushes (`screenOn`, `screenOff`, `motionDetected`, `pluggedAC`, `unpluggedAC`, `batteryLevel`, `foregroundApp`) plus full `deviceInfo` payloads to the existing `emitIfChanged` plumbing — all v0.3.0 sensor attributes update near-instantly instead of poll-cadence-bound. Poll cadence reduced to 5-min heartbeat while MQTT is healthy; restored to 1-min on disconnect. LWT published to `{prefix}/hubitat/state` (`online`/`offline`, retained). Exponential-backoff reconnect (20s → 40s → … → 300s cap). Leaving `mqttBroker` blank = exact v0.3.0 behavior, zero regression. Recommended broker: hub's built-in at `tcp://localhost:1883` (firmware 2.4.4.155+, no Mosquitto required). |
| 0.3.0   | 2026-05-18 | **Brightness BUG FIX** ⚠️ behavior change: `setLevel(N)` now correctly delivers N% brightness (0–100 converted to FKB's 0–255 scale). Previously `setLevel(100)` sent raw 100 to FKB which interpreted it as ~39%. Existing Rule Machine rules using `setLevel` will now get the brightness the user actually intended. New sensor attributes from existing `deviceInfo` poll — zero extra HTTP calls: `charging` (plugged state), `screensaverActive`, `batteryTemperature`, `foregroundApp`, `screenOrientation`, `kioskMode`. New `Notification` capability: `deviceNotification(text)` / `setOverlayMessage(text)` flash a text overlay popup on the tablet from Rule Machine. Utility commands: `toBackground`, `clearCache`, `forceSleep`, `exitApp`, `lockKiosk`, `unlockKiosk`, `enableLockedMode`, `disableLockedMode`. Video: `playVideo(url)`, `stopVideo`. Motion detection toggle: `enableMotionDetection` / `disableMotionDetection` (battery savings overnight). `checkInterval` event spam dedupe (Trinity finding #8): now gated with `emitIfChanged` — value never changes so only the first emit per session fires. |
| 0.2.0   | 2026-05-18 | v0.2.0 polish pass: (C1) descriptionText on all checkInterval sendEvents; (C2) logsOff auto-disable after 30 min, logEnable default → false; (C3) Security note in README documenting LAN password-in-URI as FKB protocol design; (C4) checkInterval value 60 → 120 (2× poll cadence, avoids false offline on single missed poll); (C5) setLevel() level event now fires from setLevelCallback after HTTP success, not optimistically before the call. UUID in packageManifest.json replaced placeholder. |
| 0.1.0   | 2026-05-18 | Initial fork from GvnCampbell v1.41. Apply Trinity audit fixes: password masking in debug logs (security), emitIfChanged in refreshCallback (event hygiene), descriptionText on all parse-path sendEvent calls, replace inverted logger with standard logEnable bool. |
