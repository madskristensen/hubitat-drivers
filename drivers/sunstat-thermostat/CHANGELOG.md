# Changelog — SunStat Connect Plus

All notable changes to this project will be documented in this file.

## 0.1.6 — 2026-05-17 — pseudo-boost implementation
- Implemented `setBoost(minutes)` and `cancelBoost()` as driver-managed temporary setpoint overrides — the Watts Home API does not expose a native boost endpoint, so boost is achieved by raising the heat setpoint by a configurable delta and auto-cancelling via `runIn`
- Added `boostActive` and `boostUntil` attributes for dashboard visibility
- Added child preferences: `boostDelta` (default +5°F/+3°C), `boostDefaultMinutes` (default 30), `boostSuppressSchedule` (default true)
- Hub-restart recovery: if `state.boostActive` is true after reboot, the expiry timer is re-armed from `state.boostUntil`; if the boost has already overrun the reboot, it is cancelled on the next poll
- Cypher confirmed via the homebridge-tekmar-wifi reference implementation (commit 553ce89) that no `Boost`, `BoostMinutes`, or `BoostUntil` field exists in the Watts API surface — the `Target.Hold` field is GET-only and reserved for future investigation

## 0.1.5 — 2026-05-17 — SunStat reliability improvements
- Migrated polling fan-out from synchronous `httpGet` to `asynchttpGet` callbacks to prevent hub-thread stalls on slow Watts API responses
- Proactive token refresh timer is now rescheduled on `initialize()` when a usable token is already in state — prevents silent token expiry after hub reboot
- Added basic HTTP 429 (rate-limit) handling: warn-and-skip instead of immediate retry storm
- Synced child driver version to parent (both now v0.1.5); no behavior change in child

## [0.1.4] — 2026-05-16

- **Fix API envelope unwrapping.** All Watts API responses wrap the payload in `{errorNumber, errorMessage, body: <payload>}`. `parseResponseBody()` was returning the whole envelope instead of `.body`, causing `defaultLocationId` to always be null and discoverDevices to always fail with "Could not resolve a Watts location ID". Fixed `parseResponseBody()` to unwrap the envelope; added `parseResponseList()` helper for List-body endpoints (GET /Location, GET /Location/{id}/Devices).
- **Fix URL-encoding of locationId in URL paths.** Watts accounts can use a location display name (e.g. "Misty Gray") as the locationId. Spaces in the locationId caused Java's URI parser to reject the URL with "Illegal character in path". All URL path segments that include the locationId now pass through `encodePathSegment()` (URLEncoder + `+` → `%20`). Affected: `discoverDevicesAtLocation()` and `setAwayModeInternal()`.
- **Add diagnostic info logging in discovery path.** `runDiscovery()` now logs `userId`, `defaultLocationId`, and `measurementScale` from GET /User at info level (not debug-gated). On locationId resolution failure, logs all three resolution attempts before the error. `fetchFirstLocationId()` logs the count of locations returned and the selected locationId.

## [0.1.3] — 2026-05-16

- **Replace refreshToken password preference with `setRefreshToken(String)` command** to bypass Hubitat's ~1024-char preference size limit. Existing installations with the token already stored in `state.refreshToken` are unaffected.


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
