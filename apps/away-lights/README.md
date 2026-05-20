# Away Lights

Away Lights is a Hubitat app that simulates occupancy by automatically turning a set of lights on and off during a configurable time window whenever the hub is in Away mode. When you leave home, it waits a short debounce period before checking the clock — if the time is within your window it switches the lights on and can send a push notification. At the end of the window (or when you return home with the option enabled) the lights go back off.

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
| **Lights to control** | Switch devices to turn on/off | *(required)* |
| **Turn on at** | Start of the daily time window (used when "Use sunset" is off) | 4:00 PM |
| **Use sunset instead of fixed on-time** | When enabled, lights turn on at sunset ± offset instead of the fixed time | false |
| **Sunset offset (minutes)** | Shift the sunset trigger; negative = before sunset, positive = after (e.g. −30, 15) | 0 |
| **Turn off at** | End of the daily time window / off time | 10:00 PM |
| **Minutes in Away before turning on** | Debounce delay after entering Away (0 = immediate) | 10 |
| **Random time offset (minutes)** | Randomly delays on/off by 0–N minutes to avoid a predictable pattern (0 = disabled) | 0 |
| **Away mode name** | Location mode that counts as "away" | Away |
| **Turn lights off when leaving Away** | Switches lights off immediately on mode return | false |
| **Notification devices** | Push-notification devices to alert when lights turn on | *(optional)* |
| **Notification message** | Text of the push notification | Away lights on |
| **Enable debug logging** | Logs actions to the Hubitat log | false |

## How it works

- **Mode → Away:** starts a debounce timer (`awayDebounceMinutes`). When it fires, checks that the mode is still Away and the current time falls inside the `[onTime, offTime)` window (or `[sunset±offset, offTime)` when sunset mode is active), then turns lights on and sends a notification.
- **Daily at `onTime` (or sunset±offset):** if the hub is currently in Away mode, turns lights on and notifies. When **Use sunset** is enabled, the on-time is recalculated every day at noon so the trigger automatically drifts with the season.
- **Daily at `offTime`:** if the hub is currently in Away mode, turns lights off.
- **Random jitter:** when `randomizeMinutes > 0`, the on/off trigger is delayed by a random 0–N minute offset each day, so the schedule never looks exactly the same to an observer.
- **Mode leaves Away** *(optional)*: cancels any pending debounce and turns lights off immediately if `turnOffOnHome` is enabled.
