# Climate Advisor

Predictive "close the windows" alerts for Hubitat — per-zone, SharpTools-ready.

## Advisory-only model

Climate Advisor **never sends commands** to thermostats, contacts, or any actuator. It is purely read-only and advisory. It is safe to run alongside webCoRE pistons or Rule Machine rules that control HVAC — there is no write-back path and no race condition is possible. If you want Climate Advisor to trigger actions, use Rule Machine listening to `severity` or `severityText` changes on the child device.

## What it does

Climate Advisor monitors indoor and outdoor temperatures, outdoor trend (rising / falling / steady), window/door contacts, air quality, and a weather device to produce actionable alerts:

| Alert family | Severity | Trigger |
|---|---|---|
| Comfort-open suggestion | INFO (1) | All contacts closed AND outdoor in comfort band AND not raining AND AQI ok |
| Cooling pre-alert | WARNING (2) | Indoor ≥ (cooling SP − offset) AND outdoor > indoor AND outdoor rising |
| Heating pre-alert | WARNING (2) | Indoor ≤ (heating SP + offset) AND outdoor < indoor AND outdoor falling |
| AQI moderate | WARNING (2) | AQI > warn threshold (default 51) |
| AQI hazardous | DANGER (3) | AQI > danger threshold (default 101) |
| Rain + windows open | DANGER (3) | Weather attribute contains rain keyword AND any contact open |
| Cool setpoint breach | DANGER (3) | Indoor ≥ cooling setpoint AND outdoor > indoor |
| Heat setpoint breach | DANGER (3) | Indoor ≤ heating setpoint AND outdoor < indoor |

## Architecture

One parent app + one optional child device (house-wide aggregate):

- **Single child device** — created only when "Create dashboard child device" is enabled.
- Per-zone data is exposed on the single child via `zoneStatuses` JSON and indexed flat attributes (`zone1Name`, `zone1Severity`, `zone1Message` … `zone10Name`).
- Zones are configuration only — no per-zone child devices are created.

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
- **AQI warning threshold** (default 51), **AQI danger threshold** (default 101)
- **Trend window** (default 30 min), rising/falling thresholds (default ±0.2°F/10 min)
- **Indoor trend enabled** (default true)
- **Create dashboard child device** (default false — opt in for SharpTools/dashboard users)

### Per zone
- Zone name, thermostats (optional), indoor temp sensors (optional — falls back to thermostat temp), contact sensors (optional), AQ sensor + attribute, speakers, notification devices
- Cooling/heating pre-alert offsets (default 3°F)

### Notifications
- Global notification devices, global speakers (optional), throttle (default 60 min)
- Announcement severity threshold (default 2 = warnings+; 3 = danger only)

## Changelog

See `climate-advisor-app.groovy` header.
