# Away Lights

Away Lights is a Hubitat app that simulates occupancy by automatically turning a set of lights on and off during a configurable time window whenever the hub is in Away mode. When you leave home, it waits a short debounce period before checking the clock — if the time is within your window it switches the lights on and can send a push notification. At the end of the window (or when you return home with the option enabled) the lights go back off.

**Version 0.4.0 adds multi-scene rotation:** Instead of simple on/off toggling, you can now cycle through preset Hubitat scenes for more realistic occupancy simulation. Each scene holds for a randomized duration before rotating to the next.

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
| **Always-On Lights** | Optional subset of lights to keep on continuously (or at all times within the active window). These lights are separate from the random rotation and always turn on when Away mode is entered. Use this for believable occupancy—having a light permanently on (e.g., a hallway) makes the absence less obvious. | *(optional)* |
| **Scenes to rotate through** | **(v0.4.0+)** Optional list of Hubitat scenes to cycle through instead of simple light on/off. Each scene activates in order with a randomized hold time (5–20 minutes by default) before advancing to the next. Provides more realistic occupancy simulation. If empty, falls back to standard light toggling. | *(optional)* |
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

- **Mode → Away:** starts a debounce timer (`awayDebounceMinutes`). When it fires, checks that the mode is still Away and the current time falls inside the `[onTime, offTime)` window (or `[sunset±offset, offTime)` when sunset mode is active). If so, turns **always-on lights immediately on**, starts the random lights (or scene rotation if configured) with jitter, and sends a notification.
- **Always-on lights vs. random lights:** Always-on lights (if configured) turn on immediately when Away mode is entered and stay on for the entire window, creating constant ambient light for believable occupancy. Random lights are the remaining switches, which turn on/off with random delays to simulate variable activity. This two-tier approach lets you maintain a baseline (e.g., hallway on constantly) while still varying other lights to avoid predictability.
- **Scene Rotation (v0.4.0+):** When scenes are configured, the app activates them in order instead of simple light toggling. Each scene holds for a randomized duration (5–20 minutes by default, logged for debugging). When the next rotation time arrives, the app advances to the next scene. At the end of the day's window, all lights turn off. Scene rotation provides more sophisticated occupancy simulation—e.g., a "Movie Night" scene, then "Kitchen Cooking" scene, creating realistic activity patterns.
- **Daily at `onTime` (or sunset±offset):** if the hub is currently in Away mode, ensures always-on lights are on and starts scene rotation or randomizes any pending random-light triggers. When **Use sunset** is enabled, the on-time is recalculated every day at noon so the trigger automatically drifts with the season.
- **Daily at `offTime`:** if the hub is currently in Away mode, turns all lights off (and cancels any pending scene rotations).
- **Random jitter:** when `randomizeMinutes > 0`, each light on/off trigger (or scene rotation start) is delayed by a random 0–N minute offset, so the schedule never looks exactly the same to an observer. Always-on lights are not affected by jitter—they stay on for the full window.
- **Mode leaves Away** *(optional)*: cancels any pending debounce and turns all lights off immediately if `turnOffOnHome` is enabled.