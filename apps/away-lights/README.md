# Away Lights

Away Lights is a Hubitat app that simulates occupancy by automatically turning a set of lights on and off during a configurable time window whenever the hub is in Away mode. When you leave home, it waits a short debounce period before checking the clock — if the time is within your window it switches the lights on and can send a push notification. At the end of the window (or when you return home with the option enabled) the lights go back off.

The app turns the selected lights on together, optionally after a random delay. It does not rotate scenes or randomly toggle individual lights during the window. Always-on lights bypass the start delay.

## Installation

### Via Hubitat Package Manager (recommended)

1. **Apps → Hubitat Package Manager → Install → "From a URL"**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/away-lights/packageManifest.json`
3. Follow the prompts.

### Manual import

1. **Apps Code → New App → Import**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/away-lights/away-lights.groovy`
3. Save, then create a new app instance under **Apps → Add User App → Away Lights**.

## Configuration

| Setting | Description | Default |
|---|---|---|
| **Lights to control** | All switch devices that will be managed for occupancy simulation. These lights will be turned on/off according to the schedule. | *(required)* |
| **Always-On Lights** | Optional lights that turn on immediately when simulation starts, without the random start delay. They need not also be selected in the main list; shutdown includes both lists. | *(optional)* |
| **Turn on at** | Fixed time of day when lights should turn on (used when "Use sunset" is disabled). Good starting point: one hour before sunset, or when family typically arrives home. | 4:00 PM |
| **Use sunset instead of fixed on-time** | When enabled, lights turn on relative to sunset, which changes daily with the season. Useful in climates with significant seasonal daylight shifts—the schedule automatically stays realistic year-round without manual updates. | false |
| **Sunset offset (minutes)** | Fine-tune the sunset trigger. Negative values = before sunset (e.g., −30 to light up as dusk approaches), positive = after sunset (e.g., +15 to simulate arriving after dark). Leave at 0 for natural dusk lighting. | 0 |
| **Turn off at** | Time when all lights turn off, ending the occupancy simulation window. Choose a time when you'd normally go to bed (10–11 PM is typical). | 10:00 PM |
| **Minutes in Away before turning on** | Debounce delay after entering Away mode. Prevents lights from flickering on during brief Away transitions. Set to 5–15 minutes for realistic "settling in" time; use 0 only if you want immediate response. | 10 |
| **Random time offset (minutes)** | Adds unpredictability to the schedule by randomly delaying each on/off event. Set to 15–30 minutes for believable variation; 0 disables randomization. Higher values look more natural to an observer but may feel less responsive. | 0 |
| **Away mode name** | The Hubitat location mode that triggers occupancy simulation. Must match your mode name exactly (case-sensitive). | Away |
| **Turn lights off when leaving Away** | Immediately turns all lights off when you return home (mode leaves Away). Disable if you want lights to stay on until the scheduled off-time. | false |
| **Notification devices** | Optional devices (phones, tablets) to receive a push alert when the occupancy simulation starts. Useful for confirming Away mode was triggered correctly. | *(optional)* |
| **Notification message** | Custom text for push notifications. Leave as default or personalize (e.g., "Away mode activated – lights on"). | Away lights on |
| **Enable debug logging** | Enables verbose logging in the Hubitat app logs for troubleshooting. Disable after setup to reduce log clutter. | false |

## How it works

- **Mode to Away:** starts the configured debounce timer, then checks the time window and optional maximum start time. Always-on lights turn on immediately; the remaining lights turn on together after a random delay. A notification is sent when the main lights turn on.
- **Daily start:** uses the same window and maximum-start checks. Sunset scheduling is recalculated each day at noon.
- **Daily end:** cancels pending starts and turns off both light lists, optionally after the random shutdown delay. This schedule survives Home/Away transitions. A delayed start cannot relight the lights after the window ends.
- **Mode leaves Away:** cancels pending starts. With turn-off-on-home enabled, both lists turn off immediately; otherwise lights already started by the app remain on until the scheduled shutdown.
- **Overnight windows:** windows such as 23:00-06:00 are supported. A maximum start of 01:00 belongs to the following morning for that window.

## Changelog

- **0.8.2 (2026-10-25):** preserve daily shutdown across mode changes, clean up always-on-only selections, prevent stale delayed starts, honor maximum start time on every start path, and correct obsolete scene-rotation documentation.
