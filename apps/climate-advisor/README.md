# Climate Advisor

Predictive "open or close the windows" advice for Hubitat — per-zone, SharpTools-ready.

## Advisory-only model

Climate Advisor **never sends commands** to thermostats, contacts, or any actuator. It is purely read-only and advisory. It is safe to run alongside webCoRE pistons or Rule Machine rules that control HVAC — there is no write-back path and no race condition is possible. If you want Climate Advisor to trigger actions, use Rule Machine listening to `severity` or `severityText` changes on the child device.

## What it does

Climate Advisor monitors indoor and outdoor temperatures, outdoor trend (heating up / cooling down / steady), window/door contacts, air quality, and a weather device to produce actionable alerts:

| Alert family | Severity | Trigger |
|---|---|---|
| Close for warming weather | WARNING (2) | Windows open, indoor is near/above the cooling setpoint, outdoor is above the cooling setpoint and warmer than indoor, and the predictive path requires outdoor heating up |
| Close for cooling weather | WARNING (2) | Windows open, indoor is below the heating setpoint, and outdoor is below the heating setpoint and colder than indoor |
| Open for cooling | WARNING (2) | Windows closed, outdoor cooling down within the cooling offset, indoor near the cooling setpoint, outdoor cooler than every selected indoor reading, and rain/AQI safe |
| Open for warming | WARNING (2) | Windows closed, outdoor heating up within the heating offset, indoor near the heating setpoint, outdoor warmer than every selected indoor reading, and rain/AQI safe |
| Setpoint breach window action | WARNING (2) | Indoor crosses the setpoint and outdoor air is already beneficial/harmful in the advised direction |
| AQI moderate | WARNING (2) | House AQI reaches the warning threshold (default 51) |
| AQI unhealthy | DANGER (3) | House AQI reaches the danger threshold (default 101) |
| Rain + windows open | DANGER (3) | Weather attribute contains rain keyword AND any contact open |

Only one window action is active per zone, and all temperature-driven window actions remain warning-level even after a setpoint breach. For zones with multiple indoor sensors, opening advice is conservative: outdoor air must benefit every current reading, not merely the zone average. Messages say “getting cooler/warmer” only when the measured trend agrees; breach advice without a matching trend uses neutral “cooler/warmer outside” wording. AQI produces one house-wide advisory rather than duplicate alerts for every zone. Window-action messages are intentionally short and announced once when the action changes.

A physically open contact suppresses “open the windows” advice immediately. It must remain open for the configured qualification delay (default 60 seconds) before it contributes to close-window advice, rain alerts, or open-contact counts. Closing takes effect immediately.

## Idle dashboard line

When no advisories are active, `latestMessage` shows a contextual one-line forecast summary. It can include today's high and rain chance, a materially different feels-like temperature, tomorrow's range and conditions, high UV, and AQI. Missing data is omitted; if nothing notable is available, it shows `No notable weather`.

Forecast, rain-probability, wind, humidity, and UV changes refresh this summary even when the weather condition text and outdoor temperature stay unchanged. Only attributes supported by the selected devices are subscribed.

## Architecture

One parent app + one optional child device (house-wide aggregate):

- **Single child device** — created only when "Create dashboard child device" is enabled. The device appears in both the main **Devices** list and under the app in **App Details** (same platform behavior as Groups and Scenes). `isComponent: true` provides ownership metadata and prevents accidental deletion from the UI; the device is auto-removed when the app is uninstalled.
- Per-zone data is exposed on the single child via `zoneStatuses` JSON and indexed flat attributes (`zone1Name`, `zone1Severity`, `zone1Message` … `zone10Name`).
- Zones are configuration only — no per-zone child devices are created.

## Recommended weather driver

The [**Open-Meteo Weather Enhanced**](../../drivers/open-meteo/open-meteo-weather-enhanced.groovy) driver in this same repo is purpose-built as a drop-in weather source for Climate Advisor. It's free, needs no API key, and emits a human-readable WMO `weather` attribute that Climate Advisor's rain-keyword matcher reads directly, plus next-hour / next-6h precipitation probability helpers. Any weather driver exposing a text weather attribute will work, but Open-Meteo Weather Enhanced is the path of least resistance.

## Installation (manual)

1. Install `climate-advisor-device.groovy` driver first.
2. Install `climate-advisor-app.groovy` app.
3. Add a new instance of **Climate Advisor** from Apps.
4. Configure global settings (outdoor sensor, trend window, AQI thresholds) and zones.
5. Save — if "Create dashboard child device" is enabled, one child device is created automatically.

## SharpTools Dashboard Setup

1. **Hero tile** — add the Climate Advisor Device, pick `latestMessage` as the hero attribute.
2. **Color rules** — create a SharpTools Rule reading `severityText`: set tile color red for `danger`, orange for `warning`, blue for `info`, green for `clear`.
3. **Contact sensor** — the device exposes `contact` (`open` = active alerts, `closed` = all clear). Use it in HomeKit via tonesto7 bridge or as a Rule Machine trigger.
4. **Per-zone data** — `zone1Severity` … `zone10Severity` and `zone1Message` … `zone10Message` are individual attributes for per-zone tiles. `zoneStatuses` JSON exposes all zone data for Custom Tiles.

## Preferences

### Global
- **Outdoor temperature device** (required)
- **Weather device** + attribute + rain keyword (optional)
- **Air quality sensor** (optional — house-wide; single device for the whole house)
- **AQI warning threshold** (default 51), **AQI danger threshold** (default 101). If entered in reverse order, Climate Advisor treats the lower value as warning and the higher as danger.
- **Trend window** (default 30 min), heating up / cooling down thresholds (default ±0.2° in local temperature units per 10 min). Trend remains `unknown` until two in-window samples span at least five minutes.
- **Window/door open qualification delay** (default 60 seconds)
- **Create dashboard child device** (default false — opt in for SharpTools/dashboard users)

### Per zone
- Zone name, thermostats (optional), indoor temp sensors (optional — falls back to thermostat temp), contact sensors (optional), speakers, notification devices
- Cooling/heating anticipation offsets for both indoor and outdoor proximity (default 3° in local temperature units)

### Notifications
- Global notification devices and global speakers (optional); window actions are announced once when changed
- Announcement severity threshold (default 2 = warnings+; 3 = danger only). Selecting 3 suppresses all temperature-driven window announcements.

## Changelog

See `climate-advisor-app.groovy` header.
