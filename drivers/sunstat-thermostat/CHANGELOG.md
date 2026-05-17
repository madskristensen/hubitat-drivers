# Changelog — SunStat Connect Plus

All notable changes to this project will be documented in this file.

## [0.1.2] — 2026-05-16

- **Energy reporting** (`EnergyMeter` capability). Reads `data.Energy.Heat.Daily[]` and `data.Energy.Heat.Monthly[]`; emits `energy` (today), `energyYesterday`, `energyMonth`, and `energyLastMonth` in kWh. Gracefully skipped when absent (older firmwares).
- **Schedule enable/disable** (`setScheduleEnabled("on"|"off")` command). Reads `data.SchedEnable.Val`; populates the `scheduleEnabled` attribute (now `"on"/"off"/"unknown"` instead of `"true"/"false"`). Sends `PATCH /api/Device/{deviceId}` with `{"Settings":{"SchedEnable":"On"|"Off"}}`.
- **Hold mode** (`thermostatHold` attribute, read-only). Reads `data.Target.Hold`; emits `"holding"` when non-zero, `"following"` when zero, `"unknown"` when absent.
- **Outdoor temperature** (`outdoorTemperature` + `outdoorSensorStatus` attributes, read-only). Reads `data.Sensors.Outdoor.Val` and `.Status`; applies the same unit conversion as room temperature. Probe absence emits `"unavailable"`.
- **Setpoint precision** (`setpointStep` attribute). Reads `data.Target.Steps`; rounds `setHeatingSetpoint` and `setFloorMinTemp` inputs to the nearest device step before clamping and patching.
- **Floor bounds clamping**. Persists `data.Schedule.FloorMin` / `FloorMax` from each poll; `setFloorMinTemp` clamps input to these bounds and logs a warning when clamping occurs.

## [0.1.1] — 2026-05-16

- **Added home/away mode** (location-level). New commands on the parent: `setHome`, `setAway`, `setAwayMode("home"|"away")`. New attributes: `awayMode`, `locationSupportsAway`. The child mirrors `awayMode` as a read-only attribute. Away mode is per-Watts-account-location and toggles all thermostats at once, matching the away button in the Watts app.
- Updated discovery to capture location-level `awayState` and `supportsAway`.
- Polling cycle now refreshes location state on each tick (single GET, minimal overhead). Skipped automatically on locations that don't support away.

## [0.1.0] — 2026-05-16

### Added
- Parent driver (`sunstat-thermostat-parent.groovy`): cloud auth, token refresh, device discovery, polling, and command routing for the Watts® Home API (`https://home.watts.com/api`).
- Child driver (`sunstat-thermostat-child.groovy`): per-thermostat Thermostat capability surface with `setHeatingSetpoint`, `setThermostatMode`, `heat`, `off`, `refresh`, `initialize`, and custom commands `setBoost`, `cancelBoost`, `setFloorMinTemp`.
- Azure AD B2C token refresh via `POST` to the Watts B2C token endpoint. Access token lifetime is 15 minutes; proactive refresh is scheduled ~5 minutes before expiry. Refresh tokens rotate on every refresh.
- Parent auto-discovers thermostats via `GET /api/User` → `GET /api/Location/{id}/Devices` and creates one Hubitat child device per thermostat found.
- `discoverDevices()` command on the parent for on-demand re-discovery.
- Per-poll state mapping in `child.parseDeviceState()`: room temperature, floor temperature (with disconnected-probe guard), thermostat mode, operating state, heating setpoint, cooling setpoint, schedule enabled, device online.
- Temperature unit conversion between the device's configured unit (`data.TempUnits.Val`) and `location.temperatureScale`.
- Floor probe sentinel guard: if floor temperature exceeds 110 °F / 43 °C, the `floorTemperature` attribute is cleared and a warning is logged.
- Setpoint clamping using `data.Target.Min` / `data.Target.Max` stored from each poll.
- `supportedThermostatModes = ["heat", "off"]` and `supportedThermostatFanModes = ["auto"]` initialized on `installed()`.
- `logEnable` / `txtEnable` debug toggle pattern with 30-minute auto-disable on both parent and child.
- `setBoost` and `cancelBoost` shipped as stubs pending API discovery (see TESTING.md).

### Known limitations (v0.1.0)
- `setBoost(minutes)` / `cancelBoost()` are stubs. The Watts Home API does not expose a documented boost endpoint; these commands log a warning until confirmed.
- `setCoolingSetpoint()`, `fanAuto()`, `fanOn()`, `fanCirculate()`, `setThermostatFanMode()`, and `emergencyHeat()` are no-ops; SunStat is heat-only.
- Initial token acquisition requires the homebridge-tekmar-wifi CLI external tool. See README.
