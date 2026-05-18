# Daikin WiFi Thermostat

A Hubitat driver for Daikin split-system air conditioners equipped with a BRP069B-series WiFi adapter. Provides full thermostat control (mode, setpoints, fan rate, fan direction), indoor/outdoor temperature monitoring, and energy reporting — all over local LAN with no cloud dependency.

**Forked from** [eriktack/hubitat-daikin-wifi](https://github.com/eriktack/hubitat-daikin-wifi) (MIT). Original driver by eriktack, based on Ben Dews' SmartThings port. This fork ships via Hubitat Package Manager (HPM) with bug fixes, lifecycle improvements, and energy polling optimisation.

---

## Supported Hardware

- **BRP069B-series adapters** (BRP069B41, BRP069B42, BRP069B45, …) — local HTTP API on port 80
- Likely also compatible with the BRP15B61 (based on community reports)

> **Not supported:** BRP069C-series (cloud-connected module). The letter after `BRP069` in the model number tells you which series you have. `B` = local HTTP (supported); `C` = cloud only (not supported).

---

## Setup

1. Find the IP address of your Daikin WiFi adapter on your local network (check your router's DHCP table, or use the Daikin mobile app to locate it).
2. Install the driver via **Hubitat Package Manager** → *Install* → search for "Daikin WiFi Thermostat".
3. In Hubitat: *Devices* → *Add Device* → *Virtual* → select **Daikin WiFi Thermostat**, give it a name, click Save.
4. On the device preferences page, enter the **IP address** (and port, default `80`) of your WiFi adapter.
5. Set your preferred **Refresh Interval** (1, 5, 10, 15, or 30 minutes).
6. Click **Save Preferences**. The driver will poll the adapter immediately and start the refresh schedule.

---

## Features

| Feature | Detail |
|---------|--------|
| Thermostat modes | auto, cool, heat, dry, fan, off |
| Fan rate | auto, silent, 1–5 |
| Fan direction | Off, Vertical, Horizontal, 3D |
| Temperature | Indoor (°C / °F), outdoor (when available) |
| Energy reporting | Today, yesterday, this year, last year, rolling 12 months (kWh) |
| EnergyMeter capability | Standard `energy` attribute → today's kWh; discoverable by energy apps |
| HealthCheck | `ping()` → 5 s timeout; `healthStatus` online/offline/unknown |
| `lastActivity` | Timestamp of last successful device response (throttled to ≥60 s) |
| Post-reboot polling | `initialize()` lifecycle restores polling after hub restart |

---

## Known Limitations (v0.1.0)

- **Econo / powerful mode** (`set_special_mode`) — not yet exposed as commands. Planned for v0.1.1.
- **Model-info capability detection** (`get_model_info`) — the driver doesn't query `get_model_info` at startup, so commands for unsupported features (e.g., swing on units without swing hardware) are still surfaced. Planned for v0.1.1.
- **Full event hygiene** — `descriptionText` is present on health/activity events and temperature changes; remaining events lack it. Full sweep planned for v0.1.1.
- **Humidity** (`RelativeHumidityMeasurement`) — requires `get_model_info` to confirm sensor presence. Deferred.

---

## Attribution & License

Original driver copyright **© 2018 Ben Dews** ([bendews.com](https://bendews.com)) with contribution by **RBoy Apps**, ported to Hubitat by **eriktack**. Forked and maintained in this repo by **Mads Kristensen** from 2026-05-18.

Licensed under the **MIT License** — see the [LICENSE](../../LICENSE) file and the file header in `daikin-wifi.groovy` for the full copyright notice. The original copyright must be preserved in all copies per the MIT license terms.
