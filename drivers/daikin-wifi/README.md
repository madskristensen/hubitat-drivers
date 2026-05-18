# Daikin WiFi Thermostat

Local LAN control for Daikin WiFi adapters (BRP069B series and similar) via the unauthenticated `/aircon/*` HTTP API on port 80. No cloud account, no polling latency, no external dependency — all control traffic stays on your LAN.

## Supported Hardware

- **Daikin BRP069B series** (BRP069B4x) WiFi adapters
- **Daikin BRP15B61** and other adapters exposing the same local HTTP API
- Any Daikin split system or ducted unit fitted with one of the above adapters

> **Note:** The newer **BRP069C** series uses a cloud module and does **not** expose the local HTTP API. Check the label on your adapter — if the letter after `BRP069` is **C**, this driver will not work.

## Capabilities

| Capability | Details |
|---|---|
| Thermostat | Full attribute set: modes, setpoints, fan mode, operating state |
| TemperatureMeasurement | Indoor temperature (°F or °C per hub setting) |
| RelativeHumidityMeasurement | Emitted only if the unit reports humidity; stays unset otherwise |
| EnergyMeter | Today's energy consumption (kWh) from the weekly power endpoint |
| Switch | `on` / `off` shortcut over thermostat power |
| HealthCheck + `ping()` | LAN reachability probe; marks device offline after 5s timeout |

Custom commands: `setFanRate` (Daikin speed codes A/B/3-7), `setSwingMode` (off/vertical/horizontal/3d), `setSpecialMode` (off/econo/powerful).

Custom attributes: `outsideTemp` (outdoor sensor), `fanRate` (Daikin fan speed code), `swingMode` (off/vertical/horizontal/3d), `specialMode` (off/econo/powerful), `healthStatus`, `lastActivity`.

## Setup

1. **Assign a static IP** to your Daikin adapter in your router's DHCP reservations.
2. In Hubitat, go to **Devices → Add Device → Virtual** and add a new virtual device of type **Daikin WiFi Thermostat**.
3. In **Preferences**, enter the adapter's **IP address** and select a **refresh interval** (5 minutes recommended).
4. Click **Save Preferences**. The driver will poll the adapter in ~2 seconds and populate all attributes.

### Optional: Default settings on power-on

The three *Default … on power-on* preferences let you choose a mode, setpoint, and fan rate that are applied ~2 seconds after Hubitat turns the unit on via `on()` or a thermostat mode command. Leave any blank to keep the adapter's last-known setting (the Daikin unit itself remembers its settings across power cycles).

## Polling Architecture

| Schedule | Endpoints | Purpose |
|---|---|---|
| Fast (1–30 min, configurable) | `get_control_info`, `get_sensor_info` | Mode, setpoints, indoor/outdoor temp, humidity |
| Slow (fixed 30 min) | `get_week_power_ex`, `get_year_power_ex` | Daily energy totals — hourly resolution, no point polling more often |

Both schedules are registered in `initialize()` and survive hub reboots.

## Known Limitations (v0.1.4)

- **No on-device timer** — `get_program` / `set_program` not implemented (use Hubitat rules instead).
- **Setpoint unity** — Daikin's local API exposes a single setpoint register (`stemp`) shared across modes. The driver writes it to both `heatingSetpoint` and `coolingSetpoint` on refresh. You can set them independently via Hubitat commands; the driver uses the mode-appropriate value on each write.
- **`get_model_info` field names are firmware-dependent** — `state.modelInfo` is cached for diagnostics on each `initialize()`. Field mapping (model name, firmware, humidity/swing flags) follows community-documented BRP069B4x names; exact values require hardware confirmation.

## Acknowledgments

This driver was inspired by **eriktack/hubitat-daikin-wifi** — the first community Hubitat driver to map the Daikin BRP069B local HTTP API to a Hubitat thermostat. The protocol structure (endpoint names, field names, mode codes) was documented by that earlier work. This driver is a clean-room implementation written from protocol notes; no code is derived from the original. Credit and thanks to [@eriktack](https://github.com/eriktack/hubitat-daikin-wifi) for the foundational research.

## License

MIT License — original work by Mads Kristensen (2026).
