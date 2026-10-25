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

Custom commands: `setFanRate` (Daikin speed codes A/B/3-7), `setSwingMode` (off/vertical/horizontal/3d), `setSpecialMode` (off/econo/powerful), `setAutoSetpoint`, `dehumidify`, `tempUp`, `tempDown`.

Custom attributes: `outsideTemp` (outdoor sensor), `fanRate` (Daikin fan speed code), `swingMode` (off/vertical/horizontal/3d), `specialMode` (off/econo/powerful), `autoSetpoint`, `energyYesterday`, `energyThisYear`, `energyLastYear`, `healthStatus`, `lastActivity`.

## Setup

1. **Assign a static IP** to your Daikin adapter in your router's DHCP reservations.
2. In Hubitat, go to **Devices → Add Device → Virtual** and add a new virtual device of type **Daikin WiFi Thermostat**.
3. In **Preferences**, enter the adapter's **IP address** and select a **refresh interval** (5 minutes recommended).
4. Click **Save Preferences**. The driver will poll the adapter in ~2 seconds and populate all attributes.

### Optional: Default settings on power-on

The three *Default … on power-on* preferences let you choose a mode, setpoint, and fan rate that are applied ~2 seconds after Hubitat calls `on()`. Thermostat mode commands apply their requested mode directly, without applying these defaults. Leave any blank to retain the confirmed adapter fields. Calling `off()` cancels pending power-on defaults.

### Control confirmation

Power-only writes preserve the adapter's last confirmed raw mode and setpoint, including while the displayed thermostat mode is off. Before the first write after initialization, the driver reads control information rather than guessing the previous mode. Writes are serialized; queued changes are sent after control readback. Power attributes update from readback, and explicit power commands remain retryable after transport or adapter rejection. Other command attributes can be optimistic until readback reconciles them.

Debug and trace logging auto-disable after 30 minutes, including after saving preferences.

## Polling Architecture

| Schedule | Endpoints | Purpose |
|---|---|---|
| Fast (1–30 min, configurable) | `get_control_info`, `get_sensor_info` | Mode, setpoints, indoor/outdoor temp, humidity |
| Slow (fixed 30 min) | `get_week_power_ex`, `get_year_power_ex` | Daily energy totals — hourly resolution, no point polling more often |

Both schedules are registered in `initialize()` and survive hub reboots.

## Known Limitations (v0.1.10)

- **No on-device timer** — `get_program` / `set_program` not implemented (use Hubitat rules instead).
- **Per-mode setpoints depend on firmware fields** — When firmware exposes `dt1/dt3/dt4/dt7`, the driver keeps auto/cool/heat targets separate. On units that omit these fields, the driver falls back to active-mode `stemp` behavior.
- **`get_model_info` field names are firmware-dependent** — `state.modelInfo` is cached for diagnostics on each `initialize()`. Field mapping (model name, firmware, humidity/swing flags) follows community-documented BRP069B4x names; exact values require hardware confirmation.
- **`setSpecialMode` graceful 404 fallback** — Some BRP069B firmware versions do not expose the special-mode endpoint. The driver gracefully handles a 404 response without throwing an error; the command completes silently if unsupported on your adapter.
- **Auto setpoint persistence** — Auto-mode setpoint targets persist until the next auto-mode activation, preventing mode-switch conflicts.
- **Operating demand is not reported by this API** — Heat/cool operating states are mode-based estimates, not compressor telemetry. Auto mode uses the capability-valid `idle` fallback; it does not claim to know actual heating/cooling demand.
- **Firmware-dependent writes** — Per-mode `dt*` writes and special modes still require confirmation on the target adapter. Local tests do not validate the adapter's HTTP implementation or energy units.

## Changelog

| Version | Date | Notes |
|---------|------|-------|
| 0.1.10 | 2026-10-25 | Cache confirmed adapter controls separately from optimistic attributes; preserve mode/setpoint on power changes; serialize writes and reconcile failures; retain queued auto targets until accepted; fix operating-state enum and logging timeouts. |

## Reliability validation

Local Groovy regression tests load this production source with stubbed asynchronous HTTP callbacks. They cover initial control discovery, power-mode preservation, transport/rejection retries, queued opposite commands, auto-target persistence, and logging timers without opening network connections. Test entry point: `tests\device-drivers\local-reliability\ReliabilityTest.groovy` (run from the repository root).

## Acknowledgments

This driver was inspired by **eriktack/hubitat-daikin-wifi** — the first community Hubitat driver to map the Daikin BRP069B local HTTP API to a Hubitat thermostat. The protocol structure (endpoint names, field names, mode codes) was documented by that earlier work. This driver is a clean-room implementation written from protocol notes; no code is derived from the original. Credit and thanks to [@eriktack](https://github.com/eriktack/hubitat-daikin-wifi) for the foundational research.

## License

MIT License — original work by Mads Kristensen (2026).
