# SunStat Connect Plus Driver

Integrates SunStat Connect Plus electric floor heating thermostats with Hubitat Elevation via the Watts® Home cloud REST API.

**Compatibility:** Hubitat Elevation C-7, C-8 | Platform 2.3.3.x or later | MIT License

> **Status: beta — v0.1.6. Cloud REST integration via the Watts® Home API; verified against the Watts iOS app for API shape and auth bootstrap; requires one-time external token capture via the `homebridge-tekmar-wifi` CLI (see Setup). Token is now set via the `setRefreshToken` command (bypasses Hubitat's ~1024-char preference limit). Parent/child architecture; auto-discovers all thermostats in your Watts account. Latest: pseudo-boost (`setBoost`/`cancelBoost`) implemented as driver-managed temporary setpoint overrides. See [releases](https://github.com/madskristensen/hubitat-drivers/releases) for details.**

## Hardware Compatibility

- **SunStat Connect Plus** — Thermostat for electric floor heating systems (heat-only, 24V AC, North American market)
- **⚠️ European users:** The EU **Watts Vision** product uses a different API (`smarthome.wattselectronics.com`) and is **not supported** by this driver. Use the `watts_vision` Home Assistant integrations instead.

## Architecture

This driver uses a **parent/child pattern**:

- **Parent device** — Holds cloud credentials (refresh token), manages auth token lifecycle, polls the Watts API, and auto-discovers all thermostats in your account
- **Child devices** — One per thermostat in your Watts account; each child controls a single SunStat thermostat

**Why parent/child?** Most homes with SunStat have multiple thermostats (bathroom, kitchen, entryway, etc.), all on the same Watts account. The parent/child pattern centralizes credentials and eliminates per-thermostat setup burden.

**If you have one thermostat:** the parent creates one child device — the UX is still correct.

## Capabilities

### Child Device Capabilities

| Capability | What it does |
|---|---|
| **Thermostat** | Heat / Off modes (cool/auto/emergency heat intentionally omitted — heat-only device) |
| **ThermostatHeatingSetpoint** | Set room target temperature (e.g., 72°F) |
| **ThermostatOperatingState** | Read heating / idle state |
| **TemperatureMeasurement** | Read ambient room temperature |
| **EnergyMeter** | Report heating energy consumption in kWh (via `energy` attribute — today's usage; use custom attributes for yesterday/month/last month) |
| **Refresh** | Manually poll the Watts API for current state |
| **Initialize** | Rebuild polling schedule on hub restart |

### Custom Attributes (Child)

| Attribute | Type | What it means |
|---|---|---|
| **floorTemperature** | number | Temperature reading from the floor sensor (probe). If disconnected, may read 212°F (100°C) — the driver logs a warning and skips updates. |
| **boostActive** | enum (`on` / `off`) | Whether a timed boost override is currently active |
| **boostUntil** | string | ISO-8601 datetime when boost expires (empty string if no boost active) |
| **scheduleEnabled** | enum (`on` / `off` / `unknown`) | Whether the device is following its programmed schedule. Toggle with `setScheduleEnabled("on"\|"off")` to control manual vs. programmed mode. |
| **thermostatHold** | enum (`holding` / `following` / `unknown`) | Whether a manual hold is overriding the device's schedule. `holding` means a user-initiated setpoint; `following` means the device is tracking the program. |
| **outdoorTemperature** | number | Outdoor probe reading (if equipped), in hub units. May not update if the optional outdoor sensor is not installed. |
| **outdoorSensorStatus** | enum (`okay` / `unavailable`) | Whether the optional outdoor probe is connected. `unavailable` if the device lacks an outdoor sensor or it's disconnected. |
| **setpointStep** | number | Device's setpoint increment (typically 1°F or 0.5°C). The driver rounds `setHeatingSetpoint` and `setFloorMinTemp` to this step before sending. |
| **deviceOnline** | enum (`true` / `false`) | Whether the thermostat is connected to Wi-Fi |
| **awayMode** | enum (`"home"`, `"away"`, `"unsupported"`, `"unknown"`) | Current away mode at this location. Read-only on child devices (mirrors the parent value). |

## Commands

### Child Device Commands

| Command | Parameters | What it does |
|---|---|---|
| **setThermostatMode** | `"heat"` or `"off"` | Switch thermostat on (heat) or off |
| **setHeatingSetpoint** | temperature (°F or °C per location) | Set room target temperature (rounded to `setpointStep` before sending) |
| **setFloorMinTemp** | temperature | Set minimum floor warmth temperature (clamped to device bounds, typically 40–85°F) |
| **setScheduleEnabled** | `"on"` or `"off"` | Enable or disable the device's programmed schedule. `"off"` = manual mode (device ignores program); `"on"` = programmed mode (device returns control to the schedule). |
| **refresh** | none | Manually poll Watts API |
| **setBoost** | minutes (1–120) | Activate timed boost override — raises the heating setpoint by the configured `boostDelta` for the given duration, then auto-restores |
| **cancelBoost** | none | Cancel an active boost immediately and restore the previous setpoint |

### Parent Device Commands

| Command | Parameters | What it does |
|---|---|---|
| **setRefreshToken** | token (string) | Store your Watts Home refresh token. Paste the full token (~1660 chars) from `homebridge-tekmar-wifi` CLI. The driver immediately initializes after the token is set. Token is stored in device state and auto-rotated. |
| **discoverDevices** | none | Re-scan your Watts account and create/update child devices for all thermostats |
| **refresh** | none | Poll all child devices and update their state |
| **setHome** | none | Turn off away mode for the entire Watts account location (affects all thermostats) |
| **setAway** | none | Enable away mode for the entire location (affects all thermostats) |
| **setAwayMode** | `"home"` or `"away"` | Set away mode explicitly (useful for Rule Machine with variables) |

### Child Device Commands

### Parent Device Attributes

| Attribute | Type | What it means |
|---|---|---|
| **awayMode** | enum (`"home"`, `"away"`, `"unsupported"`, `"unknown"`) | Current away mode for this Watts location (affects all thermostats at once) |
| **locationSupportsAway** | enum (`"true"`, `"false"`) | Whether this Watts location supports away mode (detected on first discovery) |

## Install via HPM (recommended)

1. In Hubitat: **Apps → Hubitat Package Manager → Install → "From a URL"**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/packageManifest.json`
3. Follow the prompts
4. See **Setup** below

## Manual Install

If you prefer not to use HPM:

1. Open your **Hubitat hub web UI**
2. Go to **Drivers Code**
3. Click **New Driver**
4. Paste the contents of `sunstat-thermostat-parent.groovy` and save
5. Click **New Driver** again
6. Paste the contents of `sunstat-thermostat-child.groovy` and save
7. Go to **Devices** and create a virtual device of type **SunStat Connect Plus** (the parent driver)
8. See **Setup** below

**Important:** Install the parent driver first, then the child driver. Hubitat requires the child driver to be registered before the parent can create child devices.

## Setup — Auth Bootstrap (one-time)

The Watts® Home API uses **Azure AD B2C PKCE** for authentication, which is too complex to implement inside Hubitat's sandboxed Groovy runtime. Instead, you use an **external CLI tool once** to obtain a refresh token (Step 1), then hand that token to the driver via the `setRefreshToken` command (Step 4). The driver handles all subsequent token lifecycle — refresh, rotation, persistence — automatically.

### Step 1: Obtain your refresh token

Use the `homebridge-tekmar-wifi` CLI tool to log in once. It writes your tokens to a local `tokens.json` file that you'll then read.

```bash
# Clone the homebridge-tekmar-wifi repository
git clone https://github.com/seanami/homebridge-tekmar-wifi.git
cd homebridge-tekmar-wifi

# Install dependencies
npm install

# Build the CLI
npm run build

# Run the login tool — prompts for email + password
node dist/cli/index.js login
```

Enter your **Watts Home email** and **password** when prompted. The CLI authenticates against the Watts B2C tenant and writes a `tokens.json` file in the current directory. **The tokens are NOT printed to stdout** — you'll find them in the file.

Open `tokens.json`. It looks like this:

```json
{
  "access_token": "eyJhbGci...",
  "refresh_token": "eyJraWQ...",
  "expires_at": 1768718583,
  "refresh_token_expires_at": 1776492183
}
```

**Copy the `refresh_token` value** (the long string starting with `eyJraWQ...`) — the JSON value, without the surrounding quotes. You'll need it in Step 4. The `refresh_token` is ~1660 characters; the full string is required.

Keep `tokens.json` on disk — the bundled `get-location-id.ps1` helper (Step 4b below) reads from it.

### Step 2: Create the parent device in Hubitat

1. Go to **Devices** in Hubitat
2. Click **Create Virtual Device**
3. Set:
   - **Device Name:** e.g., `SunStat Connect Plus`
   - **Device Type:** `SunStat Connect Plus` (parent)
   - **Device Namespace:** `mads`
4. Click **Create Device**

### Step 3: Configure the parent device

1. Open the newly created parent device
2. Scroll to **Preferences**
3. Set **Polling Interval** to `5 minutes` (default; safe for home automation)
4. Set **Request Timeout** to `30 seconds` (adjust if your hub is on a slow WAN link)
5. Optionally enable **Enable Debug Logging** for detailed API diagnostics (auto-disables after 30 minutes)
6. Click **Save Preferences**
7. Watch the **Logs** page. You should see `SunStat Connect Plus: Parent installed`

### Step 4: Set your refresh token

After the driver is configured and saved, go to your **SunStat Connect Plus** parent device page and click the **Commands** tab.

1. Scroll down and find **setRefreshToken**
2. Paste your full refresh token (from Step 1) into the **token** input field
3. Click the **setRefreshToken** button (the command name itself acts as the run button)

The driver will log `Refresh token stored (NNNN chars)` and immediately begin initializing and polling.

> **Why a command, not a preference?** Watts Home refresh tokens are ~1660 characters. Hubitat's Preferences page has a ~1024-character limit on saved values, which causes a silent "failed to save preferences" error. Running the command bypasses this limit entirely.

### Step 4b: Fetch your locationId (if auto-discovery doesn't find it)

v0.1.4 auto-discovers your `locationId` from the Watts API after `setRefreshToken` runs. **For most users this just works** — skip to Step 5.

If discovery fails with `Could not resolve a Watts location ID`, you need to provide the locationId manually. The repo ships a helper script that fetches it for you:

**From a PowerShell terminal on the same machine as your `tokens.json`:**

```powershell
pwsh "C:\path\to\hubitat-drivers\drivers\sunstat-thermostat\scripts\get-location-id.ps1"
```

The script:
1. Reads `refresh_token` from `tokens.json` (default path: `C:\Users\YOUR-NAME\source\repos\homebridge-tekmar-wifi\tokens.json` — edit the `$TokensFile` variable at the top of the script if yours is elsewhere)
2. Refreshes the access token at the Watts B2C endpoint
3. Calls `GET https://home.watts.com/api/Location` and prints every location attached to your account

Output looks like:

```
✓ Read refresh_token from tokens.json (1660 chars)
→ Refreshing access token at Watts B2C endpoint…
✓ Got fresh access_token (1941 chars)
→ Calling GET https://home.watts.com/api/Location …

===== LOCATIONS FOUND =====
  [1] locationId : 6294b787-ae9d-4174-b3d5-2bed8e4812dd
       name       : Misty Gray
       isDefault  : True
       devices    : 2
============================
```

**Copy the `locationId` value** (typically a UUID) and paste it into the parent device's **Preferences → Watts Home location ID** field, then **Save Preferences**.

> **Why a separate helper?** The Watts API's location list is only accessible with a fresh access token, which the driver can't easily query from outside Hubitat. The script handles the token dance and the API call in one shot so you don't have to.

### Step 5: Discover your thermostats

1. On the parent device page, scroll to the **Commands** section
2. Click the **discoverDevices** button
3. Watch the **Logs** for output — e.g., `Auto-selected locationId: ...` and `Discovered 2 thermostat(s) — 2 created, 0 updated`
4. Go back to **Devices** — you should now see child devices for each thermostat

### Refresh token rotation

Watts tokens are valid for **90 days**. Each time the driver polls, it automatically refreshes the token in the background. The new token is stored in the device's state and used next poll.

**Gotcha:** If your Hubitat hub reboots and loses state (e.g., no persistent backing store), you'll need to re-run the `setRefreshToken` command. This is rare but possible on older hardware. If you see `Auth failed` after a reboot, re-run the command with your refresh token.

## Preferences

### Parent Device

The refresh token is set via the `setRefreshToken` command (see Setup, Step 4). All other configuration lives in preferences below.

| Preference | Type | Default | Description |
|---|---|---|---|
| **Polling Interval** | enum | `5 minutes` | How often to fetch state from the Watts API. Options: Disabled, 1 minute, 5 minutes, 10 minutes. (Higher intervals reduce API calls; 5 min is a safe default.) |
| **Request Timeout (seconds)** | number | `30` | HTTP timeout for Watts API calls. Increase if your hub is behind a slow WAN link. |
| **Enable Debug Logging** | bool | `false` | Log detailed API calls and responses. Auto-disables after 30 minutes. |
| **Enable Description Text (info logging)** | bool | `true` | Log human-readable state changes (e.g., "Master Bath Floor heating"). |

### Child Device

| Preference | Type | Default | Description |
|---|---|---|---|
| **Enable Debug Logging** | bool | `false` | Log detailed command execution. Auto-disables after 30 minutes. |
| **Enable Description Text (info logging)** | bool | `true` | Log human-readable state changes (e.g., "heating", "idle"). |
| **Boost delta (°F or °C)** | decimal | `5` | Degrees above the current setpoint when boost is activated. Change to `3` if your hub is configured for °C. |
| **Default boost duration (minutes)** | number | `30` | Duration used when `setBoost()` is called without an argument (e.g., from a dashboard button). |
| **Suppress schedule during boost** | bool | `true` | Turn off the device's programmed schedule for the boost duration so the schedule does not overwrite the boosted setpoint. The schedule is automatically re-enabled when boost expires or is cancelled. |

## Home/Away Mode

Away mode is a **location-level setting** in your Watts account (matches the away button in the Watts Home app). When toggled, it affects **all thermostats at that location simultaneously** — there is no per-thermostat away setting.

**Use cases:**
- Sync your Hubitat location mode (Away / Home) with SunStat away mode
- Integrate SunStat with presence sensors to auto-enable away when no one is home

**How to control it:**

1. **From the parent device:** Call `setHome()` to turn off away mode, or `setAway()` to enable it.
2. **From Rule Machine with a variable:** Call `setAwayMode("home")` or `setAwayMode("away")` — useful if you want to drive it dynamically.

**Reading the current state:**

- **Parent device:** Read the `awayMode` attribute (shows `"home"`, `"away"`, `"unsupported"`, or `"unknown"`).
- **Child devices:** Each child also exposes `awayMode` as read-only — this mirrors the parent value for dashboard convenience.
- **Location support:** Read `locationSupportsAway` on the parent to check if your Watts location supports away mode. This is detected automatically on the first device discovery.

**If your location doesn't support away mode:** The driver detects this and sets `awayMode = "unsupported"`. Commands will be accepted but have no effect.

## Energy Monitoring

Electric floor heating represents a significant portion of home heating utility bills. This driver now tracks heating energy consumption and exposes it via the **EnergyMeter** capability plus custom attributes.

**What's tracked:**
- `energy` — Today's heating energy consumption (kWh). Resets daily at midnight (UTC) on the Watts backend.
- `energyYesterday` — Yesterday's total heating (kWh).
- `energyMonth` — Month-to-date heating (kWh).
- `energyLastMonth` — Previous calendar month's total (kWh).

**Using this data:**
1. **Dashboard monitoring** — Drag the `energy` attribute onto a Hubitat dashboard tile to see real-time kWh burn.
2. **Rule Machine alerts** — Create a rule that triggers if `energyMonth` exceeds 250 kWh to notify you when usage trends high.
3. **Historical tracking** — Periodically log `energyMonth` to an external database or Google Sheets for long-term analytics.

**Note:** The Watts API's energy arrays may contain `0.0` entries for days when the device was offline. The driver passes these through as-is. If `data.Energy.Heat` is missing from the API response, the energy attributes won't update—the driver silently guards against this.

## Schedule Control

The SunStat thermostat normally runs on a **programmed daily schedule** (e.g., 65°F nights, 72°F weekdays). You can override this from Hubitat.

**Use cases:**
- Run in pure manual mode from Hubitat (device ignores its program)
- Return control to the device's schedule after manual adjustments
- Integrate with presence/occupancy logic

**How to control it:**
- **Manual mode:** Call `setScheduleEnabled("off")` on a child device. The device ignores its program and runs only on commands you send from Hubitat.
- **Programmed mode:** Call `setScheduleEnabled("on")` to return control to the device's schedule.

**Reading the state:**
- Check the `scheduleEnabled` attribute (`on` / `off` / `unknown`)
- Combined with `thermostatHold`, you can detect if a user manually held a setpoint: `thermostatHold` will show `holding` while the schedule is disabled

## Boost

`setBoost(minutes)` temporarily raises the heating setpoint by a configurable delta and then automatically restores the previous setpoint when the timer expires.

> **Note: This is a driver-managed pseudo-boost.** The Watts Home API does not expose a native boost endpoint (confirmed via full reverse-engineering of the API surface — no `Boost`, `BoostMinutes`, `BoostUntil`, or `BoostExpiration` field exists). Boost is implemented entirely in driver state using Hubitat's `runIn` timer.

### How it works

1. Saves the current `heatingSetpoint` to `state.preBoostSetpoint`
2. Sends `PATCH /Device/{id}` with `{ Heat: <current + boostDelta> }` (clamped to the device's maximum setpoint)
3. Optionally sends `PATCH /Device/{id}` with `{ SchedEnable: "Off" }` if **Suppress schedule during boost** is enabled
4. Schedules `boostExpired()` via `runIn(minutes * 60, ...)` to auto-restore
5. Emits `boostActive = "on"` and `boostUntil = <ISO timestamp>` for dashboards

On expiry (or manual `cancelBoost()`):
- Restores the saved setpoint
- Re-enables the schedule if it was suppressed
- Clears `boostActive` and `boostUntil`

### Boost preferences

| Preference | Default | Notes |
|---|---|---|
| **Boost delta** | `5` | °F or °C above current setpoint. Set to `3` for metric hubs. |
| **Default boost duration** | `30 min` | Used when `setBoost()` is called without an argument (e.g., from a dashboard tile or voice command). |
| **Suppress schedule during boost** | `true` | Recommended — without this, the next scheduled period overrides the boosted setpoint. |

### Hub-restart recovery

`runIn` callbacks are lost if the hub reboots mid-boost. The driver mitigates this two ways:

- **On `initialize()`** (called after every hub restart): if `state.boostActive` is true, the remaining boost time is recomputed from `state.boostUntil`. If time remains, the expiry timer is re-armed; if the boost window has already passed, the setpoint is restored immediately.
- **On each poll callback** (`parseDeviceState`): if `state.boostActive` is true and `state.boostUntil` is in the past, the boost is cancelled. This catches the case where the hub was offline long enough that `initialize()` didn't fire during the boost window.

### Important caveat — it's a real setpoint change

The boosted setpoint is sent as a real `Heat` setpoint to the device. Any external automation that watches `heatingSetpoint` will see the elevated value during boost. If you have Rule Machine rules that react to `heatingSetpoint` changes, be aware they will fire during boost activation and cancellation.

## Setpoint Precision

The SunStat device supports different temperature increment steps depending on its configuration:
- **°F users:** Typically 1°F increments
- **°C users:** Typically 0.5°C increments

The driver now reads the device's `Steps` field and automatically rounds setpoint commands to match this precision. For example, if you call `setHeatingSetpoint(72.3)` on a device with 1°F steps, the driver rounds to 72°F before sending. **No user-visible change for °F users; °C users may see finer granularity.**

The current step size is exposed in the `setpointStep` attribute.

## Floor Sensor Bounds

The **setFloorMinTemp** command now respects the device's physical bounds (typically 40–85°F). If you request a temperature outside these bounds, the command:
1. Clamps the value to the allowed range
2. Logs a warning (e.g., `"setFloorMinTemp(100) clamped to max 85°F"`)
3. Sends the clamped value to the device

This prevents accidentally requesting impossible setpoints and helps catch logic errors in your Rule Machine rules.



The driver polls the Watts API on the interval you set in **Polling Interval** (default 5 minutes). Each poll fetches the current state of all your thermostats and updates their Hubitat attributes.

Access tokens are refreshed **automatically every 15 minutes** (well before the 900-second expiry), even if you disable polling. Refresh tokens rotate on each refresh — the driver persists the new token automatically.

## Rule Machine Examples

### Example 1: Turn on floor heating if outside temp drops

Create a rule: "If [Outside Temp Sensor] temperature drops below 30°F, set [Master Bath Floor] heating setpoint to 75°F"

```
Triggers:
  - [Outside Temp Sensor] temperature drops below 30°F

Actions:
  - [Master Bath Floor] set heating setpoint to 75°F
```

### Example 2: Sync Hubitat location mode with SunStat away mode

Create a rule: "If [Location] changes to Away, enable away mode on SunStat"

```
Triggers:
  - [Location] changes to Away

Actions:
  - [SunStat Connect Plus] call setAway
```

And a companion rule: "If [Location] changes to Home, disable away mode on SunStat"

```
Triggers:
  - [Location] changes to Home

Actions:
  - [SunStat Connect Plus] call setHome
```

### Example 3: Enable away mode when everyone leaves (presence sensor)

Create a rule: "If [Everyone Sensor] becomes inactive, enable away mode"

```
Triggers:
  - [Everyone Sensor] becomes inactive

Actions:
  - [SunStat Connect Plus] call setAway
```

### Example 4: Turn off all thermostats when you leave home

Create a rule: "If [Location] changes to Away, turn off all SunStat thermostats"

```
Triggers:
  - [Location] changes to Away

Actions:
  - [Master Bath Floor] set thermostat mode to Off
  - [Kitchen Floor] set thermostat mode to Off
  - (repeat for each thermostat)
```

### Example 5: Automation with schedule enable/disable

You can read `scheduleEnabled` to trigger automations when the user disables the programmed schedule:

```
Triggers:
  - [Master Bath Floor] scheduleEnabled changes

Actions:
  - If [Master Bath Floor] scheduleEnabled is true: log "User re-enabled schedule"
  - If [Master Bath Floor] scheduleEnabled is false: log "User disabled schedule"
```

### Example 6: Alert on high monthly energy consumption

Monitor monthly heating energy and send a notification if usage trends high:

```
Triggers:
  - [Master Bath Floor] energyMonth changes

Conditions:
  - [Master Bath Floor] energyMonth is greater than 250

Actions:
  - Send push notification: "SunStat floor heating: High usage alert! This month: 250+ kWh"
```

### Example 7: Detect and log manual holds

If `thermostatHold` changes to `holding`, someone has manually overridden the schedule:

```
Triggers:
  - [Master Bath Floor] thermostatHold changes to holding

Actions:
  - Log: "Manual hold detected on Master Bath Floor — user is in control"
  - (Optional) Send push notification or send to logging service
```

## Troubleshooting

### Refresh token expired

**Symptom:** Parent device logs `Auth failed` or `401` errors.

**Fix:** You must re-capture a token. Run the `homebridge-tekmar-wifi` CLI again:

```bash
node dist/cli/index.js login
```

Then go to your parent device's **Commands** tab, find **setRefreshToken**, paste the new token into its input field, and click the **setRefreshToken** button to execute.

**Why this happens:** Refresh tokens are valid for 90 days. If you don't poll (or the hub loses state), the token may expire. Unlike access tokens, refresh tokens do not auto-renew from an expired state.

### Floor sensor reads 212°F (disconnected probe)

**Symptom:** `floorTemperature` attribute shows an unrealistic value like 212°F or 100°C.

**Fix:** The floor sensor is not physically connected to the thermostat or is malfunctioning. Inspect the probe wiring. The driver logs a warning and skips the erratic reading — you will see `[warn] Floor sensor disconnected` in Logs.

### Thermostat shows offline

**Symptom:** `deviceOnline` shows `false`; commands don't work.

**Cause:** The thermostat has lost Wi-Fi connection or power.

**Fix:**
1. Check that your Wi-Fi network is broadcasting (not hidden or temporarily down)
2. Verify the thermostat is powered on (check the display on the device)
3. Restart the thermostat (flip the breaker or unplug)
4. Check the Watts mobile app — does it show the device online?
5. If the Watts app shows offline too, the issue is with your Wi-Fi or the device hardware, not the Hubitat driver

### Wrong number of thermostats discovered

**Symptom:** Only 2 of 3 thermostats appeared in Hubitat.

**Fix:**
1. Run **Discover Devices** again on the parent device
2. Check the Watts app to confirm all thermostats are active on your account
3. Check the parent device logs for discovery errors
4. If a thermostat is showing `offline` in the Watts app, it won't be discovered until it reconnects

### Commands timeout

**Symptom:** Logs show `timed out after 30 seconds`.

**Fix:** Check your internet connection and increase the **Request Timeout** preference on the parent device to 60 seconds.

### "Could not resolve a Watts location ID"

**Symptom:** After `discoverDevices`, the log shows `[SunStat] Could not resolve a Watts location ID. Set one in preferences.`

**Cause (pre-v0.1.4):** A bug prevented auto-resolution.

**Fix:** Update to v0.1.4. If you're already on v0.1.4 and still see this, run `pwsh "drivers/sunstat-thermostat/scripts/get-location-id.ps1"` to fetch your locationId, then paste it into the `Watts Home location ID` preference on the parent device.

### "Illegal character in path"

**Symptom:** `GET /Location/Some Name/Devices exception: Illegal character in path at index NN`

**Cause (pre-v0.1.4):** The Watts API uses your location's display name as the locationId. Names with spaces broke URL parsing.

**Fix:** Update to v0.1.4 — locationIds are now URL-encoded in all API paths.

## Known Limitations

- **Initial auth requires external CLI tool** — There is no built-in Azure AD B2C PKCE flow in Hubitat's Groovy sandbox. You must bootstrap the refresh token via the `homebridge-tekmar-wifi` CLI once, then set it via the `setRefreshToken` command on the parent device.
- **Schedule editing not exposed** — You can read `scheduleEnabled` but cannot edit the programmed schedule from Hubitat. Use the Watts app to manage schedules.
- **Boost (`setBoost` / `cancelBoost`) is stubbed in v0.1.2** — The command exists but logs a warning and does nothing. The API shape for boost is still being verified against real hardware. Boost support is planned for v0.2.0.
- **Mode enum unconfirmed** — The driver logs a warning if it sees unexpected thermostat modes at startup (e.g., `Auto` on a heat-only SunStat). Support for such modes is pending real-device verification.
- **modelId unconfirmed** — Watts API documentation is reverse-engineered. If your device shows unexpected model numbers in the logs, please file an issue.
- **No local API path** — v0.1.2 is cloud-only. A local LAN bypass (if Watts Home firmware supports it) is a future consideration.
- **Energy reporting depends on API response** — If `data.Energy.Heat` is missing from the Watts API response, the energy attributes won't update. The driver silently guards against this. Check the Logs if energy attributes appear stale.

## Testing

See `TESTING.md` for the manual verification plan covering auth bootstrap, device discovery, thermostat control, and error recovery.

## References

- **Capability Reference:** [Thermostat capability](https://docs.hubitat.com/index.html#capabilities/Thermostat), [TemperatureMeasurement](https://docs.hubitat.com/index.html#capabilities/TemperatureMeasurement), [Refresh](https://docs.hubitat.com/index.html#capabilities/Refresh)
- **Hubitat Documentation:** [Rule Machine Thermostat Triggers](https://docs.hubitat.com/index.html#docs/rule-machine/creating-an-automation), [Virtual Devices](https://docs.hubitat.com/index.html#docs/virtual-device)

## Credits

- **[homebridge-tekmar-wifi](https://github.com/seanami/homebridge-tekmar-wifi)** by seanami — The reference implementation that made this driver possible. This open-source Homebridge plugin revealed the Watts Home API shape and auth bootstrap strategy.
- **Watts Water Technologies** — Maker of the SunStat Connect Plus thermostat and Watts Home app

## License

MIT License. See `LICENSE` at the repo root.

---

**Questions?** Open an issue on [GitHub](https://github.com/madskristensen/hubitat-drivers) or visit the [Hubitat Community Forums](https://community.hubitat.com/).
