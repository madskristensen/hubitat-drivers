# Touchstone / Tuya Fireplace

Local LAN control for the **Touchstone Sideline Elite** electric LED fireplace — and other **Tuya WiFi fireplaces** — via the Tuya Local protocol v3.3 (AES-128-ECB over raw TCP port 6668).

**Compatibility:** Hubitat Elevation C-7, C-8 | Platform 2.3.3.x or later | MIT License

> **Status: v0.1.13 — beta. Hardware-tested LAN control of the Touchstone Sideline Elite. Generalizable to other Tuya WiFi fireplace models via Device Profile selection and in-driver DP discovery.**
>
> **Latest: v0.1.13** — reverted to named ENUM dropdowns for `setFlameBrightness`, `setFlameColor`, and `setLogColor`. Non-numeric labels sidestep the Hubitat Commands-tab +1 quirk AND give meaningful names.
>
> **Killer feature:** Works out-of-the-box for Touchstone Sideline Elite; adapts to other Touchstone models (Steel, Forte, Onyx, etc.) and generic Tuya WiFi fireplaces via configurable Device Profiles and in-driver discovery — no Python, no manual tinytuya wizard needed.

## Supported Devices

| Device | Status | Note |
|---|---|---|
| **Touchstone Sideline Elite** | ✅ Verified | Fully mapped and tested. Default Device Profile. |
| **Other Touchstone lines** (Sideline Steel, Sideline Linear, Forte, Onyx, etc.) | 🟡 Likely works | Use Device Profile = **Custom** + in-driver discovery; see [Got a Different Touchstone?](#got-a-different-touchstone-map-it-yourself) section. |
| **Generic Tuya WiFi fireplaces** | 🟡 Basic control | Use Device Profile = **Generic Tuya Fireplace**; controls power, heat level, and temperature only (DPs 1, 2, 5). |
| **Pre-WiFi Touchstones** (RF-only, no Tuya WiFi module) | ❌ Not supported | Requires a Tuya WiFi module and local network connectivity. |

## Supported Capabilities

- **Switch** — Turn the fireplace on and off
- **Refresh** — Poll device status on demand
- **Initialize** — Establish socket connection on hub startup
- **Polling** — Scheduled status updates (configurable interval)
- **TemperatureMeasurement** — Read current room temperature
- **Optional power-on defaults** — Flame color, log color, flame brightness, temperature setpoint (heater intentionally excluded — see Safety)

## Key Attributes

- **`switch`** — Device power state (`on` / `off`) — canonical on/off attribute
- **`heatLevel`** — Heat setting (`off` / `low` / `high`)
- **`heatingSetpoint`** — Target room temperature (°F or °C, depending on preference)
- **`temperature`** — Current room temperature (from device sensor)
- **`tempUnit`** — Preferred temperature unit (`F` or `C`)
- **`flameColor`** — Current flame effect color/pattern (Sideline Elite profile)
- **`flameBrightness`** — Flame lighting level (Sideline Elite profile)
- **`flameSpeed`** — Flame animation speed: `Slow` / `Medium` / `Fast` (Sideline Elite DP 103)
- **`logColor`** — Ember/log color (Sideline Elite profile)
- **`online`** — Connection status (`online` / `offline` / `unknown`)

> **Breaking change (v0.1.6):** The `power` attribute has been removed. It was an exact duplicate of `switch` and emitted two identical events per state change. Use `switch` for all on/off automations.

## Command Reference

### Standard Commands

| Command | Parameters | Description |
|---|---|---|
| **`on()`** | none | Turn the fireplace on |
| **`off()`** | none | Turn the fireplace off |
| **`refresh()`** | none | Poll device status immediately |
| **`initialize()`** | none | Reconnect socket and rebuild schedules (called on hub startup) |

### Heating & Temperature Commands

| Command | Parameters | Description |
|---|---|---|
| **`setHeatLevel(level)`** | `"off"` / `"low"` / `"high"` | Set heater intensity (Sideline Elite: DP 5) |
| **`setHeatingSetpoint(temperature)`** | integer | Set target room temperature; driver writes the °F or °C DP based on preference |

### Flame & Lighting Commands (Sideline Elite Profile)

| Command | Parameters | Description |
|---|---|---|
| **`setFlameColor(color)`** | `"Red"` / `"Orange"` / `"Yellow"` / `"Green"` / `"Blue"` / `"Purple"` | Set flame effect palette (DP 101). ⚠️ Labels are placeholders — see note below. |
| **`setFlameBrightness(level)`** | `"Dimmest"` / `"Dim"` / `"Medium"` / `"Brighter"` / `"Brightest"` | Set flame lighting level (DP 102) |
| **`setFlameSpeed(speed)`** | `"Slow"` / `"Medium"` / `"Fast"` | Set flame animation speed (Sideline Elite DP 103) |
| **`setLogColor(color)`** | `"Crimson"` / `"Coral"` / `"Amber"` / `"Gold"` / `"Lime"` / `"Mint"` / `"Teal"` / `"Sky"` / `"Indigo"` / `"Violet"` / `"Magenta"` / `"Rose"` | Set log/ember color palette (DP 104). ⚠️ Labels are placeholders — see note below. |

> **Hubitat Commands-tab dropdown +1 quirk:** The dropdown +1 quirk is sidestepped by using non-numeric ENUM labels — see v0.1.13 changelog. The bug is triggered specifically by ENUM constraints containing purely numeric-string values (e.g., `["1","2","3"]`); named labels like `"Dimmest"` / `"Dim"` / `"Medium"` do not trigger it.

> **⚠️ Color palette names are placeholders:** The flame color labels (`Red`, `Orange`, `Yellow`, `Green`, `Blue`, `Purple`) and log color labels (`Crimson`, `Coral`, `Amber`, etc.) are best-guess names for each palette index. The actual on-device color for each index (1–6 for flame, 1–12 for logs) requires hardware verification. If you test them and find the labels don't match reality, please open a GitHub issue with the correct mapping and they'll be updated in a future version.

> **DP 105 (log brightness):** The Sideline Elite firmware appears to treat DP 105 as read-only — writes are silently dropped. The `setLogBrightness` command was removed in v0.1.11; the actual write target for log/ember brightness control on this model is unknown. Pending further investigation.

### Advanced / Discovery Commands

| Command | Parameters | Description |
|---|---|---|
| **`discoverDPs()`** | none | Log all current DP values; use when mapping an unknown device |
| **`captureBaseline()`** | none | Snapshot all DP values; compare with `captureDiff()` after pressing a remote button |
| **`captureDiff()`** | none | Log which DPs changed since `captureBaseline()`; helps identify unmapped remote commands |
| **`setRawDP(dpId, value)`** | dpId (number), value (string) | Advanced: write a raw DP value directly; used for experimentation and custom DP discovery |

## Installation

### Option A: Hubitat Package Manager (recommended, once published)

1. In **Hubitat Package Manager**, choose **Install → From a URL**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/touchstone-fireplace/packageManifest.json`
3. Follow the prompts
4. See **Setup** section below

### Option B: Manual Install

1. Open your **Hubitat hub web UI**
2. Go to **Drivers Code** → **New Driver**
3. Paste the full contents of `touchstone-fireplace.groovy` and click **Save**
4. Go to **Devices** → **Add Device** → **Virtual** → **Touchstone / Tuya Fireplace**
5. Fill in the device name and click **Create Device**
6. See **Setup** section below

## Setup

### Step 1: Get Your Tuya Local Key

Your Tuya device requires a **local key** (16-character password) to enable LAN control. This is a one-time extraction. Choose one of these paths:

#### Method A: Via Tuya IoT Cloud Portal (Durable — Recommended)

This method is the most reliable and does not depend on third-party integrations.

1. **Create a free Tuya IoT developer account** (no credit card required):
   - Go to [iot.tuya.com](https://iot.tuya.com) → **Register** → select **Western America DC** if you're in the US
   - Sign up with email/password
   - Create a new **Cloud Project** (default settings are fine)

2. **Enable required APIs** (this is a critical step that blocks many users):
   - In the Cloud Project, go to **All Services** and manually subscribe to:
     - **IoT Core**
     - **Authorization Token Management**
     - **Smart Home Basic Service**
     - **Device Status Notification**
   - All are free trials; no card required

3. **Link your Smart Life account**:
   - In the Cloud Project → **Services** → **Link Devices** → scan the QR code with the **Smart Life app**
   - Tap **Confirm** in the app

4. **Extract keys using tinytuya**:
   - Install Python 3.7+ on your computer (if not already installed)
   - Open a terminal and run:
     ```bash
     pip install tinytuya
     python -m tinytuya wizard
     ```
   - Follow the wizard prompts; it will read your Tuya IoT project credentials from the portal
   - The wizard outputs a `devices.json` file with your fireplace's `deviceId`, `ip`, and `local_key`

5. **Copy your device's credentials from `devices.json`** and save them — you'll use them in Step 2 below

For detailed guidance, see `.squad/skills/tuya-cloud-key-extraction/SKILL.md` in this repository.

#### Method B: Via Home Assistant + tuya-local (Fast, HA-dependent)

If you already run **Home Assistant** and have the `tuya-local` integration installed:

1. Open Home Assistant → **Settings** → **Devices & Services** → **Tuya Local** → **Create Config**
2. Scan the QR code from the **Smart Life app** (QR button in app settings)
3. The integration automatically extracts your local key and displays it

**Caveat:** This method relies on a Tuya-issued hardcoded client_id that Tuya can revoke unilaterally. Method A is more durable.

### Step 2: Configure the Driver

1. Open the **Touchstone / Tuya Fireplace** device in Hubitat
2. Scroll to **Preferences** and fill in:
   - **Device IP address:** Static LAN IP of your fireplace (e.g., `192.168.1.38`). Recommended: set a DHCP reservation on your router so the IP doesn't change.
   - **Device ID:** Tuya Device ID from your `devices.json` (e.g., `70223053e8db84d10b53`)
   - **Local key (16 chars):** Your 16-character Tuya local key from `devices.json` (shown as a password field; never logged or displayed in cleartext)
   - **Device Profile:** Leave as **Sideline Elite (tested)** if you have a Sideline Elite; see [Got a Different Touchstone?](#got-a-different-touchstone-map-it-yourself) for other models
   - **Preferred setpoint / temperature unit:** `Fahrenheit` (recommended for US Touchstone units) or `Celsius`
   - **Polling interval:** Default `60 seconds` — adjust if you want less frequent updates
   - **Enable debug logging:** Leave off unless troubleshooting (auto-disables after 30 minutes)

3. Click **Save Preferences**
4. Watch the **Logs** page — you should see status messages confirming the driver connects and polls your device

A healthy first-time setup looks like:
- `online` attribute changes from `unknown` to `online`
- No error messages in logs about connection refused or invalid key
- `power`, `temperature`, and `heatLevel` attributes populate with real values

## Preferences Reference

| Preference | Type | Default | Description |
|---|---|---|---|
| **Device IP address** | text | (required) | Static LAN IP of the fireplace's Tuya WiFi module |
| **Device ID** | text | (required) | Tuya device identifier (from tinytuya or HA integration) |
| **Local key (16 chars)** | password | (required) | 16-character Tuya local key (never hardcoded; stored securely) |
| **Device Profile** | enum | Sideline Elite (tested) | `Sideline Elite (tested)` / `Generic Tuya Fireplace` / `Custom` |
| **Preferred setpoint / temperature unit** | enum | Fahrenheit | `Fahrenheit` (°F) or `Celsius` (°C) |
| **Polling interval** | enum | 60 seconds | How often to query device status; 0 disables polling |
| **Enable debug logging** | bool | off | Verbose debug output (auto-disables after 30 min) |
| **Enable descriptionText (info) logging** | bool | on | Info-level logs (command execution, state changes) |

### Custom Device Profile DP Overrides

If you select **Device Profile = "Custom"**, these additional preferences appear (override the default DP mappings for the Sideline Elite):

| Preference | Type | Default | Description |
|---|---|---|---|
| **Flame Color DP** | number | 101 | DP number for flame color/effect selection |
| **Flame Brightness DP** | number | 102 | DP number for flame lighting level |
| **Log Color DP** | number | 104 | DP number for log/ember color |
| **Heat Level DP** | number | 5 | DP number for heater intensity (off/low/high) |
| **Temperature Setpoint (°F) DP** | number | 14 | DP number for Fahrenheit setpoint |
| **Temperature Setpoint (°C) DP** | number | 2 | DP number for Celsius setpoint |
| **Power DP** | number | 1 | DP number for on/off control |

## Power-on Defaults

The driver can automatically apply optional settings when the device powers on via Hubitat (the `on()` command). Each setting is independent — if left blank, the fireplace keeps its last-known value.

**Optional power-on default preferences:**
- **`defaultFlameColor`** — Applied ~1.5s after device turns on (when using Sideline Elite profile); select from named palette labels (⚠️ placeholders)
- **`defaultFlameBrightness`** — Applied ~1.5s after device turns on; select from `Dimmest` / `Dim` / `Medium` / `Brighter` / `Brightest`
- **`defaultLogColor`** — Applied ~1.5s after device turns on (when using Sideline Elite profile); select from named palette labels (⚠️ placeholders)
- **`defaultHeatingSetpoint`** — Sets the target room temperature on power-on; does NOT enable the heater (the heater remains off until you explicitly call `setHeatLevel()`)

> **⚠️ Upgrade note (from v0.1.6–v0.1.12):** If you previously saved a numeric default (e.g., `"3"` for flame brightness), that value no longer matches the new label list. The preference dropdown will appear blank. Simply re-pick from the new named dropdown and save.

**How it works:**
1. When you call `on()` in Hubitat, the driver immediately sends the power command (DP 1)
2. After ~1.5 seconds, any preferences you've configured are applied in sequence
3. The delay allows the fireplace's firmware time to settle from the power transition
4. Blank preferences are skipped — the fireplace keeps whatever value it remembered

**Device Profile gating:**
- Flame/color/brightness defaults are only shown and applied when **Device Profile = Sideline Elite (tested)** or **Custom**
- If you select **Generic Tuya Fireplace**, only the temperature setpoint default is available (since flame/log control isn't mapped in that profile)

## Safety

**Why doesn't the driver auto-start the heater?**

By design. The heater is a radiant heat element — auto-enabling it on power-on, via scenes, or through any other implicit path is a fire/burn risk. The heater changes state **ONLY** when you explicitly call `setHeatLevel("low"|"high"|"off")` or use a Hubitat rule that calls it directly.

There is no `defaultHeatLevel` preference and there never will be. Even if the device supports it, the driver will not expose it, because the risk of accidental heat activation outweighs the convenience.

If you want the heater to come on at a specific time or condition, create an explicit Hubitat **Rule** that calls `setHeatLevel()` — this makes the automation visible and intentional.

## Got a Different Touchstone? Map It Yourself

Your Touchstone model (Sideline Steel, Forte, Onyx, etc.) likely uses the same Tuya WiFi module as the Sideline Elite but with different DP assignments for lighting effects. **The driver includes built-in discovery commands — no Python or manual tinytuya needed.**

### Step-by-Step Discovery

1. **Set Device Profile to "Custom"**:
   - Open the device in Hubitat
   - Change **Device Profile** from `Sideline Elite (tested)` to `Custom`
   - Click **Save Preferences**
   - New DP-override preferences appear

2. **Run `discoverDPs()` to see all current values**:
   - Open the device page
   - Scroll down and click the **discoverDPs** command button
   - Check the **Logs** page — you'll see a block like:
     ```
     DP 1   = true (bool)          [power]
     DP 2   = 22 (int)             [temperature setpoint, C]
     DP 3   = 24 (int)             [current temperature, C]
     DP 5   = "2" (string)         [heat level]
     DP 101 = "1" (string)         [flame color]
     ...
     ```

3. **Capture a baseline**:
   - Click the **captureBaseline** command button
   - This saves a snapshot of all DP values

4. **Press a remote button** (e.g., flame color up):
   - Use your physical remote control to press a button you want to map

5. **Run `captureDiff()` to see what changed**:
   - Click the **captureDiff** command button
   - Check the **Logs** page — you'll see which DPs changed:
     ```
     DP 101 changed from "1" to "2" — likely Flame Color
     ```

6. **Manually test unmapped DPs** with `setRawDP(dpId, value)`**:
   - For example, if you want to confirm DP 103 is flame speed, click **setRawDP** and enter:
     - dpId: `103`
     - value: `"1"` (for Slow)
   - Watch the fireplace and logs to confirm the effect

7. **Update the Custom DP preferences** with your discovered values:
   - Open **Preferences** and fill in the DP numbers you've discovered
   - Click **Save Preferences**

8. **Test your commands**:
   - Use `setFlameColor("1")`, `setFlameBrightness("5")`, etc. to confirm they work
   - Open a **GitHub Issue** with your DP map so we can ship it as a preset for your model

### Example: Sideline Steel Mapping

If you discover that your Sideline Steel uses:
- DP 1 = power (matches Sideline Elite) ✓
- DP 5 = heat level (matches Sideline Elite) ✓
- DP 14 = temperature setpoint °F (matches Sideline Elite) ✓
- DP 101 = flame color (matches Sideline Elite) ✓
- DP 102 = flame brightness (matches Sideline Elite) ✓

Then the default DP assignments already work! Just set **Device Profile** back to **Sideline Elite (tested)** and save.

## Known Quirks & Limitations

### Single TCP Connection

**Tuya WiFi modules allow only one LAN connection at a time.** If the driver can't connect:
- Close the **Smart Life app** on your phone/tablet
- Open the Hubitat device page and click **Refresh** (or wait for the next polling cycle)
- The driver will reconnect

If the app restarts while the driver is connected, you'll see error logs mentioning "error 901" — that's the Tuya module's way of saying "another connection tried to open." This is normal and self-heals.

### Temperature Setpoint Persistence

The device may reset the temperature setpoint across power cycles or after resuming from idle. This is a Tuya WiFi module behavior, not a driver bug. **Workaround:** If your setpoint keeps resetting, open a GitHub Issue and we can investigate adding a periodic setpoint-sync command.

### Separate °F and °C Tracking

The device maintains **two separate temperature setpoints**: DP 2 (Celsius) and DP 14 (Fahrenheit). The driver writes to whichever one matches your **Preferred setpoint unit** preference. If you switch units, the device may briefly show the old setpoint until you call `refresh()` or wait for the next poll cycle. **This is expected behavior.**

### Remote Buttons Without Tuya Equivalents

Some remote buttons (log brightness, flame tempo, remote timer) don't map to Tuya DPs and won't sync to Hubitat. The driver surfaces discovery commands (`discoverDPs()`, `captureDiff()`) so you can verify whether a button has a DP — if it doesn't appear in logs, the Tuya WiFi module doesn't expose it.

## Troubleshooting

### Device Offline / No Response

**Symptom:** `online` attribute says `offline`; logs show `Connection refused` or `Timeout`.

**Troubleshooting:**
1. Confirm the device IP is correct: open a terminal and ping `192.168.1.38` (or your configured IP)
   - If ping fails, the device is off the network — check WiFi and router
   - If ping succeeds but Hubitat still can't connect, move on to step 2
2. Confirm the device ID and local key are correct (copy/paste from `devices.json` or Smart Life app settings)
3. **Close the Smart Life app** — it may be holding the only TCP slot
4. Open the Hubitat device page and click **Refresh**
5. Check the logs — if you still see "error 901," wait 30 seconds and try again

### "Importing [java.util.zip.CRC32] is not allowed"

**Symptom:** Driver fails to save with this error in the IDE.

**Root cause:** You're on Hubitat driver v0.1.2 or earlier. CRC32 import is required for Tuya packet validation.

**Fix:** Ensure you're running **v0.1.5 or later**. If you see this error, you have an old version. Delete the driver and re-import from GitHub.

### `Expression [MethodCallExpression] is not allowed: e.getClass()`

**Symptom:** Driver fails to save in the Hubitat IDE with this error.

**Root cause:** You're on Hubitat driver v0.1.2 or v0.1.3. Hubitat's Groovy sandbox blocks reflection-style method calls (`.getClass()`, `.getMethods()`, etc.). v0.1.5 removes the offending code.

**Fix:** Update to **v0.1.5 or later**. Delete the driver and re-import from GitHub.

### Wrong DP Responses / Commands Don't Work

**Symptom:** `setFlameColor("1")` logs but the fireplace doesn't respond, or state doesn't update.

**Troubleshooting:**
1. Switch **Device Profile** to **Custom**
2. Run **`discoverDPs()`** and check the logs for the current DP values
3. Are the raw string values what you expect (e.g., DP 101 shows `"1"` when flame is orange)?
4. If DPs are missing or show unexpected types, your model may have a different DP map — open a GitHub Issue with your `discoverDPs()` output

### "No signature of method: Script1.paragraph()"

**Symptom:** Driver fails to save with this error in the Hubitat IDE.

**Root cause:** You're on Hubitat driver v0.1.4 or earlier. The preferences block included a `paragraph()` construct, which is app-only UI (not allowed in drivers per the Hubitat sandbox restrictions).

**Fix:** Update to **v0.1.5 or later**. The `paragraph()` construct has been removed, and descriptions have been folded into individual input field descriptions. Delete the driver and re-import from GitHub.

### Logs Show "power on suppressed by transition window"

**Symptom:** You ran a command (e.g., `setFlameColor`) right after turning on the fireplace, and nothing happened.

**Root cause:** The driver waits 10 seconds after a power state change before allowing other DP writes. This prevents the Tuya WiFi module from dropping the setpoint or overwriting concurrent writes.

**Expected behavior:** Wait a few seconds and try again, or call `refresh()` to force an update.

## Credits

- **Tuya v3.3 protocol & local encryption:** Pattern sourced from kkossev's Hubitat community drivers and the `jasonacox/tinytuya` project
- **tinytuya:** Python local key extraction tool by Jason Cox ([@jasonacox](https://github.com/jasonacox))
- **Empirical DP mapping & testing:** Mads Kristensen

## Changelog

- **v0.1.11 (2026-05-17):** Removed `setLogBrightness` command, `logBrightness` attribute, and `defaultLogBrightness` preference. Real-hardware testing on the Sideline Elite confirmed DP 105 is read-only or unimplemented on this firmware — writes are silently dropped. Added defensive input-validation guards to `setFlameColor`, `setFlameBrightness`, and `setLogColor` (unknown values now log-warn and bail before writing to device).
- **v0.1.6 (2026-05-17):** Added `setFlameSpeed(speed)` command + `flameSpeed` attribute (DP 103); added `setLogBrightness(level)` command + `logBrightness` attribute (DP 105); removed duplicate `power` attribute — use `switch` for all on/off automations.
- **v0.1.5 (2026-05-17):** BUGFIX — removed `paragraph()` from preferences block (Hubitat driver allowlist; app-only construct). No behavior changes; previous defaults UI text moved into per-field descriptions.
- **v0.1.4 (2026-05-17):** Adds optional power-on defaults (flame color, brightness, log color, temperature setpoint); removes auto-starting heater for safety; fixes Hubitat sandbox reflection error blocking v0.1.3.
- **v0.1.2 (2026-05-17):** Core driver release with Device Profiles and discovery commands.
- **v0.1.1 (2026-05-17):** Generalized device profiles (Sideline Elite / Generic Tuya / Custom), in-driver DP discovery commands (`discoverDPs()`, `captureBaseline()`, `captureDiff()`), custom DP mapping via preferences, auditable raw DP writes (`setRawDP()`).
- **v0.1.0 (2026-05-17):** Initial Tuya Local scaffold for power, heat level, flame/log lighting, temperature polling, and socket retry/backoff.

## License

MIT License. See `LICENSE` at the repo root.

---

**Questions?** Open an issue on [GitHub](https://github.com/madskristensen/hubitat-drivers) or visit the [Hubitat Community Forums](https://community.hubitat.com/).
