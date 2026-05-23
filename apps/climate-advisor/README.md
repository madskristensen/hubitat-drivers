# Climate Advisor

Predictive "close the windows" alerts for Hubitat — per-zone, SharpTools-ready.

## What it does

Climate Advisor monitors indoor and outdoor temperatures, outdoor trend (rising / falling / steady), window/door contacts, air quality, and a weather device to produce actionable alerts:

| Alert family | Severity | Trigger |
|---|---|---|
| Cooling pre-alert | WARNING (1) | Indoor ≥ (cooling SP − offset) AND outdoor > indoor AND outdoor rising |
| Heating pre-alert | WARNING (1) | Indoor ≤ (heating SP + offset) AND outdoor < indoor AND outdoor falling |
| AQI poor | ALERT (2) | AQI > 100 |
| Rain + windows open | ALERT (2) | Weather attribute contains rain keyword AND any contact open |
| Cool setpoint breach | ALERT (2) | Indoor ≥ cooling setpoint AND outdoor > indoor |
| Heat setpoint breach | ALERT (2) | Indoor ≤ heating setpoint AND outdoor < indoor |

## Architecture

One parent app + one child driver (used for both aggregate and per-zone children):

- **Aggregate child** — house-wide `houseStatus`, `severity`, `outdoorTrend`, `activeAlertCount`.
- **Zone children** — per-zone `indoorTemp`, `indoorTrend`, `openContacts`, `aqi`, `latestMessage`.

## Installation (manual)

1. Install `climate-advisor-device.groovy` driver first.
2. Install `climate-advisor-app.groovy` app.
3. Add a new instance of **Climate Advisor** from Apps.
4. Configure global settings (outdoor sensor, trend window) and zones.
5. Save — child devices are created automatically.

## Preferences

### Global
- **Outdoor temperature device** (required)
- **Weather device** + attribute + rain keyword (optional)
- **Trend window** (default 30 min), rising/falling thresholds (default ±0.2°F/10 min)
- **Indoor trend enabled** (default true)

### Per zone
- Zone name, thermostats (optional), indoor temp sensors (required), contact sensors (optional), AQ sensor, speakers, notification devices
- Cooling/heating pre-alert offsets (default 3°F)

### Notifications
- Global notification devices, throttle (default 60 min), announcement severity threshold (default 1 = warnings+)

## Changelog

See `climate-advisor-app.groovy` header.
