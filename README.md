# Hubitat Drivers

Community Hubitat Elevation drivers by Mads Kristensen.

> **Current status:** Four beta drivers available — **Daikin WiFi Thermostat** v0.1.5 (LAN local API, Daikin BRP069B series), **Gemstone Lights** v0.4.8 (cloud REST, working in production), **SunStat Connect Plus** v0.1.4 (cloud REST, energy monitoring and schedule control), and **Touchstone / Tuya Fireplace** v0.1.5 (LAN integration, now ready for first public release).

## Install all drivers via one URL (HPM bundle)

Install all of Mads's drivers in a single HPM operation:

1. In Hubitat: **Apps → Hubitat Package Manager → Install → "From a URL"**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/packageManifest.json`
3. HPM will show a checklist of all available drivers — select the ones you want
4. Follow the prompts

> **Note:** Install via the bundle URL **or** via a per-driver URL — not both. Installing a driver through both paths causes duplicate update prompts when that driver is updated. If you already installed a driver via its per-driver URL, skip it in the bundle checklist.

## Install via HPM (per-driver)

1. In Hubitat: **Apps → Hubitat Package Manager → Install → "From a URL"**
2. Paste one of the following packageManifest URLs:
   - **Daikin WiFi Thermostat:** `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/daikin-wifi/packageManifest.json`
   - **Gemstone Lights:** `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json`
   - **SunStat Connect Plus:** `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/packageManifest.json`
   - **Touchstone / Tuya Fireplace:** `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/touchstone-fireplace/packageManifest.json`
3. Follow the prompts

## Listed in the community HPM master list

Status: **Submitted** — PR filed as HubitatCommunity/hubitat-packagerepositories#106.

## Drivers

| Driver | Status | Description | Docs |
|--------|--------|-------------|------|
| **Daikin WiFi Thermostat** | Beta | LAN integration for Daikin BRP069B series WiFi adapters. Local HTTP API control, no cloud account required, indoor/outdoor temperature, humidity, and energy meter. | [Driver README](drivers/daikin-wifi/README.md) |
| **Gemstone Lights** | Beta | Cloud REST integration for Gemstone permanent outdoor lighting, including favorites-first LightEffects, named effects, and color-temperature fallback | [Driver README](drivers/gemstone-lights/README.md) |
| **SunStat Connect Plus** | Beta | Cloud REST integration for SunStat Connect Plus electric floor heating thermostats via the Watts® Home API. Parent/child architecture; auto-discovers thermostats from your Watts account. | [Driver README](drivers/sunstat-thermostat/README.md) |
| **Touchstone / Tuya Fireplace** | Beta | LAN integration for Touchstone Sideline Elite and other Tuya WiFi fireplaces. Flame color, log color, brightness, heater control. Includes in-driver DP discovery for unmapped models. | [Driver README](drivers/touchstone-fireplace/README.md) |

## Manual install

1. Open your **Hubitat hub web UI**.
2. Go to **Drivers Code**.
3. Create a new driver and paste in the driver source code (e.g., `drivers/gemstone-lights/gemstone-lights.groovy`, `drivers/touchstone-fireplace/touchstone-fireplace.groovy`, etc.).
4. Save the driver, then create a virtual device using the corresponding driver type (e.g., **Gemstone Lights**, **Touchstone / Tuya Fireplace**).
5. Open the device and configure **Preferences** according to the per-driver README.
6. See the per-driver README for capabilities, setup details, and Rule Machine examples.

## Compatibility

- **Hub:** Hubitat Elevation C-7, C-8
- **Minimum Platform Version:** 2.3.3.x
- **Network:** Driver-specific; see per-driver README for network requirements

**Driver Compatibility Details:**
- **Daikin WiFi Thermostat** — Requires LAN reachability to the BRP069B series adapter; operates via the local HTTP API (no cloud account)
- **Gemstone Lights** — Requires outbound HTTPS to Gemstone cloud API
- **SunStat Connect Plus** — Requires outbound HTTPS to Watts Home cloud API
- **Touchstone / Tuya Fireplace** — Requires LAN reachability to the Tuya WiFi module; needs the device's Tuya local key (obtained once via `tinytuya wizard`)

## Contributing

Found a bug or have an idea? Open an issue or send a pull request.

When bumping any per-driver version, also bump the root `packageManifest.json` version (patch/minor as appropriate) so HPM bundle users receive update notifications.



## License

These drivers are released under the MIT License. See `LICENSE` for details.

## Authors

- **Mads Kristensen** — Project lead

---

**Questions?** Visit the [Hubitat Community Forums](https://community.hubitat.com/) or open an [issue on GitHub](https://github.com/madskristensen/hubitat-drivers/issues).
