# Gemstone Lights Driver

Integrates Gemstone permanent outdoor LED strings with Hubitat Elevation via the Gemstone cloud REST API.

**Compatibility:** Hubitat Elevation C-7, C-8 | Platform 2.3.3.x or later | MIT License

> **Status: beta — working, author-tested in production on a real Gemstone controller.**
>
> **Killer feature:** favorites-first `LightEffects` plus named-effect support. Dashboards get the standard `lightEffects` dropdown, while Rule Machine and custom actions can call `setEffect("Pulse")` or `setEffect("⭐ Pulse")`.
>
> **Architecture:** Authenticates with your Gemstone account email/password via AWS Cognito `USER_PASSWORD_AUTH`, caches tokens in driver `state`, binds to a configurable Gemstone controller (or the first one found), and maps Hubitat `setColorTemperature()` to an RGB white-spectrum fallback because the public reverse-engineered Gemstone cloud API does not expose a native CCT endpoint. Supports multi-zone setups via the `Controller name` preference.

## Supported Capabilities

- **Switch** — Turn the lights on and off
- **SwitchLevel** — Adjust brightness from 0–100% (mapped to Gemstone's 0–255 wire brightness)
- **ColorControl** — Set solid RGB colors from Hubitat's hue/saturation controls
- **ColorTemperature** — Set Kelvin values; the driver converts them to RGB white-spectrum colors before calling `/deviceControl/play/pattern`
- **LightEffects** — Exposes Hubitat's standard `lightEffects` JSON attribute with favorites first, plus `setEffect(index)`, `setNextEffect()`, and `setPreviousEffect()`
- **Refresh** — Poll `currentlyPlaying` from the Gemstone cloud
- **Initialize** — Rebuild schedules after hub startup

## Key Attributes

- **`authStatus`** — Human-readable auth state such as `Authenticating`, `Authenticated: Front House`, or `Auth failed — check email/password`
- **`effectName`** — The current Gemstone pattern/effect name. Updated on `refresh()` and after successful RGB / CT / effect changes.
- **`colorMode`** — One of `RGB`, `CT`, or `EFFECTS`
- **`favoriteEffects`** — Comma-separated favorite names with a `⭐` prefix
- **`lightEffects`** — Standard Hubitat JSON map (`{"0":"⭐ Pulse",...}`) built favorites-first for dashboards
- **`colorTemperature` / `colorName`** — Updated on `setColorTemperature()` and when a matching CT fallback pattern is seen during `refresh()`
- **`lastActivity`** — ISO 8601 timestamp of the last successful Gemstone cloud API response

## Command Surface

### Standard Hubitat commands

- **`on()` / `off()`**
- **`setLevel(level)`**
- **`setColor(colorMap)`**
- **`setColorTemperature(kelvin, level = null, transitionTime = null)`**
- **`setEffect(effectNumber)`**
- **`setNextEffect()`**
- **`setPreviousEffect()`**
- **`refresh()`**
- **`initialize()`**

### Custom commands

- **`refreshEffectCatalog()`** — Loads Gemstone effect names into the driver's cache from the cloud and rebuilds `lightEffects`, `favoriteEffects`, `state.favorites`, and `state.effectCatalog`.
- **`setEffect(String name)`** — Matches `name` case-insensitively after trimming and accepts either the raw name (`Pulse`) or the dashboard label (`⭐ Pulse`).
- **`playEffectByName(String name)`** — Same as `setEffect("name")` but exposed as a distinct command for WebCoRE. WebCoRE inspects capability metadata and only sees the numeric `setEffect(NUMBER)` signature, so the String overload of `setEffect` is invisible from WebCoRE's action picker. Use this command instead from WebCoRE pistons. Accepts the raw name (`Pulse`) or the dashboard label (`⭐ Pulse`).

## Installation

### Option A: Hubitat Package Manager (recommended)

1. In **Hubitat Package Manager**, choose the option to install a package by URL.
2. Paste this manifest URL:
   - `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json`
3. Install the package, then create a virtual device using the **Gemstone Lights** driver type.

### Option B: Manual install

1. Open your **Hubitat hub web UI**.
2. Go to **Drivers Code**.
3. Click **New Driver**.
4. Paste the contents of `gemstone-lights.groovy`.
5. Click **Save**.

### Create the virtual device

1. Go to **Devices** in Hubitat.
2. Click **Create Virtual Device**.
3. Use:
   - **Device Name:** e.g. `Gemstone Lights`
   - **Device Type:** `Gemstone Lights`
   - **Device Namespace:** `mads`
4. Click **Create Device**.

### Enter Gemstone cloud credentials

1. Open the new device page.
2. Scroll to **Preferences**.
3. Enter your **Gemstone account email** and **password**.
4. Leave **Polling interval** at `5 minutes` unless you want a different cadence.
5. Leave **HTTPS request timeout** at `30` unless your internet connection needs a longer timeout.
6. Click **Save Preferences**.
7. Watch the **Logs** page and the device's **authStatus** attribute.

This driver is **cloud REST only** in v0.4.0. You do **not** enter a controller IP address or local API key.

A healthy first-time setup looks like:
- `authStatus` changes from **Authenticating** to **Authenticated: <device name>**
- the driver logs which Gemstone cloud device it bound to
- **Refresh** pulls switch/level/color state without stack traces

## Multiple Zones / Controllers

Gemstone installations often have multiple physical controllers — for example, *Front of House*, *Eaves*, and *Soffit*. Each controller is an independent zone with its own LED string. The v0.4.10 driver supports this with the **Controller name** preference.

### How it works

The new **Controller name (zone)** preference (in **Devices → your Gemstone device → Preferences**) tells this Hubitat device which physical controller to bind to.

- **Blank (default):** Preserves v0.4.9 behavior — binds to the first controller found. Single-zone users do not need to change anything.
- **Set to a name (e.g., `Eaves`):** After discovery, the driver binds to the controller whose name matches (case-insensitive, leading/trailing spaces trimmed). All commands — on/off, brightness, color, effects — route exclusively to that controller.

### Multi-zone setup (one Hubitat device per controller)

1. **Create a Hubitat virtual device** for each Gemstone controller:
   - Device 1 — name it `Gemstone Front of House`, set **Controller name** to `Front of House`
   - Device 2 — name it `Gemstone Eaves`, set **Controller name** to `Eaves`
   - Device 3 — name it `Gemstone Soffit`, set **Controller name** to `Soffit`
2. Each device uses the **same Gemstone account email/password**. Each authenticates independently and caches its own Cognito tokens.
3. Each device polls the Gemstone cloud on its own schedule (default: every 5 minutes). With 3 devices that is 3× the cloud requests, still well within Gemstone's undocumented rate budget.

### Finding your controller names

The exact spelling must match the name configured in the Gemstone mobile app. Two ways to find it:

- **Gemstone mobile app:** Open the app → your home → each zone/controller name appears in the UI.
- **Install once without Controller name:** Save Preferences, let auth complete, then open the device page and inspect **State Variables** → `availableControllers`. This field lists all controller names for your account (comma-joined, alphabetically sorted). Copy the exact spelling from there into the **Controller name** preference.

### What happens if the name doesn't match

If **Controller name** is set but no controller with that name is found after discovery, the driver:

1. Logs a `log.warn` that lists all available controller names.
2. Falls back to the first controller found (graceful degradation — the driver continues to work, just bound to the wrong controller).
3. Updates `state.availableControllers` so you can see the correct spelling.

Correct the **Controller name** preference spelling and click **Save Preferences** to rebind.





`refreshEffectCatalog()` builds the two state structures that make named effects and dashboard effect pickers work together:

- **`state.favorites`** — ordered `name -> patternId` favorites in Gemstone's returned order
- **`state.effectCatalog`** — full `name -> patternId` catalog for all effects

Favorites are discovered from the **`isFavorite`** flag returned on `GET /folders/pattern/list`; the current reverse-engineered API docs do **not** describe a separate favorites endpoint.

After a refresh, Hubitat receives a standard `lightEffects` attribute similar to:

```json
{"0":"⭐ Pulse","1":"⭐ Sparkle","2":"⭐ Twinkle","3":"3D Color Cubes","4":"Aurora Borealis"}
```

Notes:
- **Index `0` is the first favorite**. Use the normal **Switch** capability for power off; there is no separate `Off` slot in `lightEffects`.
- **Dashboards auto-render the dropdown** from `lightEffects`.
- **Rule Machine / custom actions** can still call `setEffect("Pulse")` by raw name.
- **Starred names are display-only.** The driver strips the prefix before resolving the pattern ID.

## Color Temperature (Kelvin) Control

Current reverse-engineered Gemstone cloud API docs still show **no native CCT / white-temperature endpoint** (`/deviceControl/play/cct`, `/deviceControl/play/whiteTemperature`, etc. were not present). v0.4.0 therefore wires Hubitat's `ColorTemperature` capability through the existing pattern endpoint:

1. Convert Kelvin to an RGB white-spectrum color
2. Send that single-color pattern to `PUT /deviceControl/play/pattern`
3. Mark `colorMode` as **`CT`**
4. Update `colorTemperature` and `colorName`

`colorName` uses these bands:
- **Warm White** — 2200K to under 3000K
- **White** — 3000K to under 4000K
- **Cool White** — 4000K to under 6500K
- **Daylight** — 6500K and above

Because this is an RGB fallback, the exact warm/cool rendering still depends on Gemstone's RGB engine rather than a dedicated white-temperature channel.

## Rule Machine examples

- **When motion →** `setEffect("Pulse")`
- **When sunset →** `setColorTemperature(2700, 80)`
- **When effect changes →** notify if `colorMode == 'EFFECTS'`

## Using from WebCoRE

WebCoRE's action picker exposes commands based on capability metadata. The `LightEffects` capability declares `setEffect` as taking a number, so WebCoRE only offers the numeric form. To invoke effects by name from WebCoRE, use `playEffectByName` instead — it's a separate command that takes a STRING parameter and is fully visible to WebCoRE.

In your piston: pick the Gemstone device → action → `playEffectByName` → enter the effect name (e.g., `Pulse` or `⭐ Pulse`).

Rule Machine and Hubitat rules can use either `setEffect("Pulse")` or `playEffectByName("Pulse")` — both work.

## What v0.4.1 Does

- Logs into Gemstone's AWS Cognito user pool with your email/password
- Stores `accessToken`, `refreshToken`, and `idToken` in driver `state`
- Refreshes the access token proactively about 5 minutes before expiry
- Uses `Authorization: Bearer <accessToken>` for Gemstone's API Gateway requests, matching the documented cloud spec
- Pre-serializes Cognito and Gemstone JSON bodies before handing them to Hubitat's HTTP client; Cognito still receives `Content-Type: application/x-amz-json-1.1` on the wire
- Retries once on HTTP `401` by refreshing the token, then replaying the request
- Drives `on()`, `off()`, `setLevel()`, `setColor()`, `setColorTemperature()`, `refresh()`, `refreshEffectCatalog()`, `setEffect(name)`, `setEffect(index)`, `setNextEffect()`, `setPreviousEffect()`, and `playEffectByName()` against the cloud REST API
- Caches effect names for 1 hour and automatically reloads them when the catalog is empty or stale
- Merges built-in Gemstone-managed effects with your saved presets, preferring the saved preset when names collide
- Surfaces favorites first everywhere user-facing: `lightEffects`, `favoriteEffects`, info logs, and the ordered in-memory catalog
- Uses optimistic Hubitat events so the UI feels instant while the cloud catches up
- Exposes `playEffectByName()` as a separate command for WebCoRE compatibility

## Current Limitations

- **Cloud-only:** v0.4.10 does not use Gemstone's local HTTP path
- **One controller per Hubitat device:** create one device per zone and set the **Controller name** preference; see [Multiple Zones / Controllers](#multiple-zones--controllers) above
- **ColorTemperature is an RGB fallback:** the Gemstone cloud spec still exposes no native Kelvin/CCT endpoint
- **Favorites require a catalog refresh:** the driver auto-refreshes on demand, but manual `refreshEffectCatalog()` is still the fastest way to pick up newly starred/unstarred patterns
- **Cloud lag exists:** Gemstone's `currentlyPlaying` endpoint can trail a just-sent command by roughly 30–60 seconds, so an immediate manual refresh may briefly show stale state

## Testing

See `TESTING.md` for the manual plan covering:
- first-time auth setup
- auth failure visibility
- on/off control
- dimming
- RGB color control
- favorites-first `lightEffects` + `setEffect(name/index/next/previous)`
- color-temperature fallback + `colorMode`
- refresh / reconciliation
- timeout handling
- reboot recovery

## Troubleshooting

### `authStatus` says `Auth failed — check email/password`
- Re-enter the Gemstone email/password in Preferences and click **Save Preferences**
- Confirm the same credentials work in the official Gemstone mobile app
- Check Hubitat logs for the accompanying Cognito diagnostic block (`Content-Type`, `X-Amz-Target`, response status/error fields)
- The driver never logs the password, tokens, or the full Cognito client id

### `authStatus` says `Not configured — add email/password`
- The driver will not talk to Gemstone until both preferences are populated
- Open the device page, fill in email + password, and save again

### `setEffect()` says `No effect named 'X'`
- Run **`refreshEffectCatalog()`** from the device page
- Check the logs for favorites-first catalog lines and the generated `lightEffects` dropdown
- Try either the raw name (`Pulse`) or the starred name (`⭐ Pulse`)
- If a built-in effect and a saved preset share the same name, the saved preset wins

### `setColorTemperature()` does not look like a perfect Kelvin white
- That is expected today: Gemstone's cloud API exposes no native CCT endpoint in the current reverse-engineered API docs
- v0.4.0 converts Kelvin to RGB and plays a single-color pattern as a fallback
- Try a nearby Kelvin value (for example 2700K vs 3000K) if you want a warmer/cooler white appearance

### Commands time out
- Look for `timed out after ... seconds` in the logs
- Confirm the Hubitat hub has internet access
- Increase **HTTPS request timeout** if your WAN path is slow

### The wrong Gemstone controller was selected

- Set the **Controller name** preference to the exact name of the controller you want (e.g., `Eaves`). Spelling is case-insensitive and leading/trailing spaces are trimmed.
- Leave **Controller name** blank to use the first controller found (v0.4.9 behavior).
- After saving preferences, check `state.availableControllers` on the device page to see the names discovered for your account.
- If you have multiple zones, create one Hubitat device per controller and set a different **Controller name** on each.

### Refresh shows old state right after a command
- This is a Gemstone cloud behavior, not a local parsing bug
- Wait about a minute and refresh again, or let the next scheduled poll reconcile state

## Credits

This driver was informed by the Gemstone cloud API reverse engineering in:
- [`sslivins/hass-gemstone`](https://github.com/sslivins/hass-gemstone)
- [`sslivins/pygemstone`](https://github.com/sslivins/pygemstone)



## License

MIT License. See `LICENSE` at the repo root.

---

**Questions?** Open an issue on [GitHub](https://github.com/madskristensen/hubitat-drivers) or visit the [Hubitat Community Forums](https://community.hubitat.com/).
