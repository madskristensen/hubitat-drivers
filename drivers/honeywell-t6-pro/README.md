# Honeywell T6 Pro Thermostat

A Hubitat driver for the Honeywell T6 Pro Z-Wave thermostat. Forked from **djdizzyd/hubitat** `Advanced Honeywell T6 Pro Thermostat` (Bryan Copeland, v1.2, last commit 2021-01-22) and maintained with repo-standard quality practices.

## What It Is

Z-Wave thermostat driver for the Honeywell T6 Pro (model TH6320ZW2003). Supports heating, cooling, auto, and emergency heat modes; fan on/auto/circulate; configurable Z-Wave parameters; battery status; and humidity measurement. Controls Mads's downstairs thermostat in production.

## Install

### HPM (Hubitat Package Manager)

1. Open HPM → **Install** → **Search by URL**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/honeywell-t6-pro/packageManifest.json`
3. Follow the prompts; HPM will install the driver.

### Manual

1. In Hubitat, go to **Drivers Code** → **New Driver** → **Import**
2. Paste the raw URL:
   `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/honeywell-t6-pro/honeywell-t6-pro.groovy`
3. Click **Import** then **Save**.
4. Go to the device using the old driver → **Edit** → change **Type** to `Honeywell T6 Pro Thermostat` → **Save**.

## Capabilities

- Thermostat (heat / cool / auto / emergency heat / off)
- ThermostatFanMode (auto / on / circulate)
- ThermostatOperatingState (idle / heating / cooling / fan only / pending heat / pending cool)
- TemperatureMeasurement
- RelativeHumidityMeasurement
- Battery
- PowerSource (mains / battery switchover events)
- Configuration (42 Z-Wave parameters)
- Initialize (hub-restart-safe scheduling)
- Refresh
- Sensor / Actuator

## What's Fixed (v0.1.0 — initial fork)

| # | Severity | Description |
|---|----------|-------------|
| 1 | **BLOCKER** | `txtEnable` was referenced throughout the driver but never declared as a preference — permanently silencing all informational log statements. Fixed: added `input "txtEnable"` preference with `defaultValue: true`. |
| 2 | **MAJOR** | `device.currentValue` without an argument (method reference) compared to a string — always `false`. Fixed: `device.currentValue("thermostatOperatingState")` in two locations. |
| 3 | **MAJOR** | `configure()` called `runEvery3Hours("syncClock")` without first calling `unschedule("syncClock")`, causing zombie schedulers to accumulate. Fixed: added `unschedule("syncClock")` at the top of `configure()`. |

## Changelog

| Version | Date | Notes |
|---------|------|-------|
| 0.4.0 | 2026-05-18 | Add `descriptionText` to `thermostatOperatingState`, `thermostatFanMode`, `thermostatMode` event handlers (Pick #1); change `thermostatFanState` attribute type from `"string"` to `"enum"` with 8 values matching Z-Wave Notification Fan State CC (Pick #2); add Notification type 9 (System) handler stub with `log.warn` for hardware/software fault events (Pick #3). |
| 0.3.0 | 2026-05-18 | Emit `thermostatFanState` attribute from `ThermostatFanStateReport` (Pick #1); handle battery-low notification events 10/11 with `log.warn` + `sendEvent` for `battery` (Pick #2); fix `CMD_CLASS_VERS` octal bug `043` → `0x43` for Thermostat Setpoint CC (Pick #3). |
| 0.2.0 | 2026-05-18 | Polish pass (Trinity backlog C1–C5): add `descriptionText` to temperature and humidity events (C1); BigDecimal equality in `eventProcess()` to prevent `68` vs `68.0` false events (C2); remove dead `configurationGet(parameterNumber: 52)` saving 2 Z-Wave frames per fan-state event (C3); add `descriptionText` to `supportedThermostatModes` and `supportedThermostatFanModes` sendEvents (C4); remove redundant `runIn(10,"syncClock")` from `refresh()` (C5). Namespace switched to `mads`. Added `Initialize` capability. |
| 0.1.0 | 2026-05-18 | Initial fork. Apply Trinity audit fixes: add `txtEnable` preference (BLOCKER), fix `device.currentValue("thermostatOperatingState")` in two locations (MAJOR), add `unschedule("syncClock")` in `configure()` (MAJOR). |

## Attribution

- **Original driver:** [djdizzyd/hubitat — Advanced-Honeywell-T6-Pro](https://github.com/djdizzyd/hubitat/blob/master/Drivers/Honeywell/Advanced-Honeywell-T6-Pro.groovy)
- **Original author:** Bryan Copeland (`djdizzyd`)
- **License:** MIT — original MIT license is preserved verbatim in the driver file header.
- This fork applies targeted bug fixes and quality improvements listed in the changelog above.

## License

MIT — see driver file header for the original license text (preserved verbatim per clean-room policy).

