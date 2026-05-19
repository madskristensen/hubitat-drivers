# Honeywell T6 Pro Thermostat

Fork of **djdizzyd/hubitat** `Advanced Honeywell T6 Pro Thermostat` (Bryan Copeland, v1.2, last commit 2021-01-22). This fork applies three bugs identified by a code audit (Trinity, 2026-05-18) — one BLOCKER and two MAJORs — that affect Mads's installed downstairs thermostat today. No features have been added; the only changes are the minimum necessary bug fixes. The intent is to submit these fixes as a PR upstream to djdizzyd once validated on hardware.

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
4. Go to the device using the old driver → **Edit** → change **Type** to `Advanced Honeywell T6 Pro Thermostat` → **Save**.

## What's Fixed

| # | Severity | Description |
|---|----------|-------------|
| 1 | **BLOCKER** | `txtEnable` was referenced throughout the driver (`if (txtEnable) log.info ...`) but was **never declared as a preference**. It was always `null`/`false`, permanently silencing all informational log statements (battery %, AC mains events, etc.) regardless of user settings. **Fix:** Added `input "txtEnable", "bool", title: "Enable description text logging", defaultValue: true` to the `preferences {}` block. |
| 2 | **MAJOR** | `device.currentValue=="cooling"` appeared in two event handlers (`zwaveEvent(ThermostatFanStateReport)` and `zwaveEvent(BasicSet)`). `device.currentValue` without an argument is a method reference — always truthy in Groovy. The comparison `device.currentValue=="cooling"` is always `false`, silently corrupting the fan-state / operating-state correction logic. **Fix:** Changed both occurrences to `device.currentValue("thermostatOperatingState")=="cooling"`. |
| 3 | **MAJOR** | `configure()` calls `runEvery3Hours("syncClock")` without calling `unschedule("syncClock")` first. `updated()` does call `unschedule()` before registering, but if the user manually triggers `configure()` from the device page (common during initial setup), zombie `syncClock` schedulers accumulate — one per invocation. After 3 invocations, `syncClock` fires 3× every 3 hours. **Fix:** Added `unschedule("syncClock")` at the top of `configure()`, before the `runIn` and `runEvery3Hours` calls. |

## Upstream Status

A PR will be submitted to [djdizzyd/hubitat](https://github.com/djdizzyd/hubitat) once Mads has validated the forked driver on his downstairs Honeywell T6 Pro thermostat. The maintainer has been silent since 2021-01-22 (4+ years); if the PR is unreviewed after a reasonable window, this fork remains the canonical version for this repo.

## Attribution

- **Original driver:** [djdizzyd/hubitat — Advanced-Honeywell-T6-Pro](https://github.com/djdizzyd/hubitat/blob/master/Drivers/Honeywell/Advanced-Honeywell-T6-Pro.groovy)
- **Original author:** Bryan Copeland (`djdizzyd`)
- **License:** MIT — original MIT license is preserved verbatim in the driver file header.
- This fork adds no new code beyond the three targeted bug fixes listed above.

## Changelog

| Version | Date | Notes |
|---------|------|-------|
| 0.1.0 | 2026-05-18 | Initial fork. Apply Trinity audit fixes: add `txtEnable` preference (BLOCKER), fix `device.currentValue("thermostatOperatingState")` in two locations (MAJOR), add `unschedule("syncClock")` in `configure()` (MAJOR). |
