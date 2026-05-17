---
name: "hubitat-heat-only-thermostat"
description: "Capability profile and architecture patterns for heat-only thermostat drivers (electric floor heating, baseboard heat) on Hubitat."
domain: "groovy"
confidence: "high"
source: "earned"
---

## Context
This skill applies when writing a Hubitat driver for a heat-only thermostat device — one that heats but does not cool and has no fan. Examples: SunStat/SunTouch electric floor heating, Stelpro baseboard heaters.

## Capability Profile Decision

**Declare `Thermostat` (combo) — not individual thermostat capabilities.**

The `Thermostat` capability is required for:
- The Hubitat dashboard thermostat tile (setpoint +/− controls)
- Google Home / Alexa thermostat discovery
- Rule Machine thermostat-specific triggers

Individual capabilities (`ThermostatHeatingSetpoint`, `ThermostatMode`, etc.) alone produce a generic device tile and miss voice/RM integration.

**Constrain the combo surface with attribute initialization:**
```groovy
sendEvent(name: "supportedThermostatModes",    value: JsonOutput.toJson(["heat", "off"]))
sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["auto"]))
```
Set these in `installed()`. Hubitat's dashboard and voice integrations respect these lists and hide unavailable modes.

## Standard Capability Set (heat-only)

| Capability | Include? | Notes |
|---|---|---|
| `Actuator` | ✅ | Required base |
| `Sensor` | ✅ | Required base |
| `Thermostat` | ✅ | Full combo for dashboard/RM/voice |
| `TemperatureMeasurement` | ✅ | Ambient temperature attribute |
| `Refresh` | ✅ | Manual state poll |
| `Initialize` | ✅ | Re-register polling on hub restart |
| `ThermostatCoolingSetpoint` | ❌ | Heat-only — skip |
| `ThermostatFanMode` | ❌ | No fan — skip |
| `ThermostatSchedule` | ❌ | Hubitat's shape is undefined; leave to Rule Machine |

## Thermostat Mode Guidance

Minimum mode set for a heat-only device:
```groovy
["heat", "off"]
```
- Add `"auto"` only if the device exposes a schedule-following mode through the API as a distinct state.
- Do **not** use `"emergency heat"` for timed boost/override — use a custom `setBoost(minutes)` command instead. `"emergency heat"` carries HVAC-system semantics that confuse voice assistants on single-zone electric devices.

## ThermostatOperatingState Values

For a heat-only device: `"heating"` and `"idle"` only. Never emit `"cooling"`, `"fan only"`, `"pending heat"`, or `"pending cool"`.

## Custom Attributes for Dual-Sensor Devices

Many electric floor thermostats have both an ambient sensor and a floor probe sensor. Use:
```groovy
attribute "floorTemperature", "number"   // floor probe
// "temperature" (from TemperatureMeasurement) → ambient
```

## Temperature Unit Handling

Always emit temperature events with the hub's configured unit:
```groovy
sendEvent(name: "temperature", value: convertedTemp, unit: location.temperatureScale)
```
Convert API values (typically Celsius) to Fahrenheit if `location.temperatureScale == "F"`.

## Examples
- `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy` (planned v0.1.0 scaffold)

## Anti-Patterns
- Declaring `ThermostatCoolingSetpoint` or `ThermostatFanMode` on a heat-only device.
- Using `"emergency heat"` mode instead of a custom boost command.
- Omitting `supportedThermostatModes` initialization — leaves the dashboard showing all mode options.
- Emitting temperature without specifying `unit:` — Hubitat may display wrong unit in dashboard.
