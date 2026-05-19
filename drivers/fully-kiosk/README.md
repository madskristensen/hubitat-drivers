# Fully Kiosk Browser

A Hubitat driver for controlling **Fully Kiosk Browser** on wall-mounted Android tablets via the local REST API.

> **Fork of:** [GvnCampbell/Hubitat](https://github.com/GvnCampbell/Hubitat/blob/master/Drivers/FullyKioskBrowserController.groovy) (v1.41)
> **Upstream status:** Maintainer silent since 2021-11-20 (4.5+ years). Not accepting changes.

---

## What this is

Controls a Fully Kiosk Browser instance over your local network. The tablet pushes events (motion, screen state, battery, volume) back to Hubitat via HTTP, and Hubitat sends commands to the tablet (screen on/off, brightness, load URL, TTS, siren, etc.).

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
| 0.3.0   | 2026-05-18 | **Brightness BUG FIX** ⚠️ behavior change: `setLevel(N)` now correctly delivers N% brightness (0–100 converted to FKB's 0–255 scale). Previously `setLevel(100)` sent raw 100 to FKB which interpreted it as ~39%. Existing Rule Machine rules using `setLevel` will now get the brightness the user actually intended. `setScreenBrightness(value)` still accepts raw 0–255 for expert use. New sensor attributes from existing `deviceInfo` poll — zero extra HTTP calls: `charging` (plugged state), `screensaverActive`, `batteryTemperature`, `foregroundApp`, `screenOrientation`, `kioskMode`. New `Notification` capability: `deviceNotification(text)` / `setOverlayMessage(text)` flash a text overlay popup on the tablet from Rule Machine. Utility commands: `toBackground`, `clearCache`, `forceSleep`, `exitApp`, `lockKiosk`, `unlockKiosk`, `enableLockedMode`, `disableLockedMode`. Video: `playVideo(url)`, `stopVideo`. Motion detection toggle: `enableMotionDetection` / `disableMotionDetection` (battery savings overnight). `checkInterval` event spam dedupe (Trinity finding #8): now gated with `emitIfChanged` — value never changes so only the first emit per session fires. |
| 0.2.0   | 2026-05-18 | v0.2.0 polish pass: (C1) descriptionText on all checkInterval sendEvents; (C2) logsOff auto-disable after 30 min, logEnable default → false; (C3) Security note in README documenting LAN password-in-URI as FKB protocol design; (C4) checkInterval value 60 → 120 (2× poll cadence, avoids false offline on single missed poll); (C5) setLevel() level event now fires from setLevelCallback after HTTP success, not optimistically before the call. UUID in packageManifest.json replaced placeholder. |
| 0.1.0   | 2026-05-18 | Initial fork from GvnCampbell v1.41. Apply Trinity audit fixes: password masking in debug logs (security), emitIfChanged in refreshCallback (event hygiene), descriptionText on all parse-path sendEvent calls, replace inverted logger with standard logEnable bool. |
