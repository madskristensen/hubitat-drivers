# Hubitat Drivers & Apps

Community Hubitat Elevation drivers and apps by Mads Kristensen.

**11 production-ready drivers + 3 apps** • 🌐 6 Local LAN • ☁️ 5 Cloud API • 📦 HPM Ready

## Available Drivers

### 🌡️ Climate Control

| Driver                     | Type  | Key Features                                                                 | Docs                                         |
| -------------------------- | ----- | ---------------------------------------------------------------------------- | -------------------------------------------- |
| **Climate Advisor** | App | Predictive "close the windows" alerts • Per-zone monitoring • Weather/AQI aware | [Docs](apps/climate-advisor/README.md) |
| **Daikin WiFi Thermostat** | Local | No cloud required • Indoor/outdoor temp • Humidity • Energy meter • HTTP API | [Docs](drivers/daikin-wifi/README.md)        |
| **Honeywell T6 Pro** | Local | Z-Wave thermostat • Multi-point climate control • Battery-powered • Fan mode | [Docs](drivers/honeywell-t6-pro/README.md)        |
| **SunStat Connect Plus**   | Cloud | Floor heating • Auto-discovery • Parent/child • Watts® Home API              | [Docs](drivers/sunstat-thermostat/README.md) |

### 🏠 Home Ambiance

| Driver                          | Type  | Key Features                                                                   | Docs                                           |
| ------------------------------- | ----- | ------------------------------------------------------------------------------ | ---------------------------------------------- |
| **Gemstone Lights**             | Cloud | Permanent outdoor lighting • Named effects • Favorites support • Color control | [Docs](drivers/gemstone-lights/README.md)      |
| **Touchstone / Tuya Fireplace** | Local | Flame & log colors • Brightness • Heater • DP discovery • No cloud required    | [Docs](drivers/touchstone-fireplace/README.md) |

### 📱 Mobile & Tablet

| Driver                     | Type  | Key Features                                                                 | Docs                                         |
| -------------------------- | ----- | ---------------------------------------------------------------------------- | -------------------------------------------- |
| **Fully Kiosk Browser**    | Local | Tablet remote • Browser control • Motion detection • Local REST API • Screen commands | [Docs](drivers/fully-kiosk/README.md)        |

### 🌍 Air Quality & Weather

| Driver                     | Type  | Key Features                                                                 | Docs                                         |
| -------------------------- | ----- | ---------------------------------------------------------------------------- | -------------------------------------------- |
| **Open-Meteo Weather Enhanced** | Cloud | Free • No API key • Current + hourly forecast • WMO conditions • Next-hour precipitation helpers • Today's high/low | [Docs](drivers/open-meteo/README.md)        |
| **PurpleAir AQI Virtual Sensor** | Cloud | Real-time AQI • Multi-sensor averaging • EPA/Woodsmoke correction • Geolocation support | [Docs](drivers/purpleair-aqi/README.md)        |

### 📅 Calendar & Productivity

| Driver                     | Type  | Key Features                                                                 | Docs                                         |
| -------------------------- | ----- | ---------------------------------------------------------------------------- | -------------------------------------------- |
| **Calendar Todo Switch** | Cloud | Subscribes to any iCal/webcal feed • Switch turns on at event start when title starts with ✅ (configurable) • Recurring events (RRULE) expanded with BYDAY + EXDATE support • Publishes cleaned title as `todo` attribute for SharpTools tiles • Standard Switch on/off commands | [Docs](drivers/calendar-todo/README.md) |

### 🔌 Power & Outlets

| Driver                     | Type  | Key Features                                                                 | Docs                                         |
| -------------------------- | ----- | ---------------------------------------------------------------------------- | -------------------------------------------- |
| **Minoston Smart Plug 2-Channel (MP24Z)** | Local | Dual-outlet child endpoints • Parent/child sync hardening • Z-Wave associations • Auto-off per channel | [Docs](drivers/minoston-mp24z/README.md)        |
| **Philio PST02 Enhanced** | Local | Motion/contact/temp/lux sensor • Guided parameter UI (no raw bitmask math) • Variant-aware A/B/C handling | [Docs](drivers/philio-pst02/README.md)        |

## Available Apps

| App | Key Features | Docs |
| --- | --- | --- |
| **Auto Away Mode** | Home/Away decisions from selected presence + motion + contact sensors • Simple inactivity thresholds (Home 0 / Away 20 default) • Optional lock/unlock actions on mode changes • Event-driven, debounced evaluation • Presence/motion/contact changes count as activity • Presence not-present has its own timer • Decision explainability | [Docs](apps/auto-away-mode/README.md) |
| **Away Lights** | Simulates occupancy in Away mode • Configurable on/off time window • Debounced mode-change trigger • Optional push notification • Optional auto-off on return home | [Docs](apps/away-lights/README.md) |
| **Climate Advisor** | Predictive "close the windows" alerts • Monitors indoor/outdoor temperature trends • Window/door and air quality integration • SharpTools-ready per-zone alerts | [Docs](apps/climate-advisor/README.md) |

## Quick Start

### Option 1: Install All Drivers (Recommended)

**Install via HPM bundle URL:**

1. **Apps → Hubitat Package Manager → Install → "From a URL"**
2. **Paste:** `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/packageManifest.json`
3. **Select** the drivers you want from the checklist
4. **Follow** the prompts

> **💡 Tip:** Install via the bundle URL **or** per-driver URL — not both. Installing a driver through both paths causes duplicate update prompts.

### Option 2: Install Individual Drivers/Apps

**Install specific drivers via HPM:**

1. **Apps → Hubitat Package Manager → Install → "From a URL"**
2. **Paste** one of these URLs:

   ```text
   Calendar Todo:    https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/calendar-todo/packageManifest.json
   Daikin WiFi:      https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/daikin-wifi/packageManifest.json
   Fully Kiosk:      https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/fully-kiosk/packageManifest.json
   Gemstone Lights:  https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json
     Honeywell T6:     https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/honeywell-t6-pro/packageManifest.json
     Minoston MP24Z:   https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/minoston-mp24z/packageManifest.json
      Open-Meteo Enhanced: https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/open-meteo/packageManifest.json
      Philio PST02:      https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/philio-pst02/packageManifest.json
       PurpleAir AQI:    https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/purpleair-aqi/packageManifest.json
    SunStat Connect:  https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/packageManifest.json
    Touchstone:       https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/touchstone-fireplace/packageManifest.json
    Auto Away Mode:   https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/auto-away-mode/packageManifest.json
    Away Lights:      https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/apps/away-lights/packageManifest.json
    ```

3. **Follow** the prompts

### Option 3: Manual Installation

For advanced users or offline installations:

1. **Open** your Hubitat hub web UI
2. **Navigate to** Drivers Code
3. **Create** a new driver and paste the source code from `drivers/<driver-name>/<driver-name>.groovy`
4. **Save** and create a virtual device using the driver
5. **Configure** device preferences (see per-driver README)

## Contributing

**Found a bug or have an idea?** Open an [issue](https://github.com/madskristensen/hubitat-drivers/issues) or send a pull request!

**Version bump reminder:** When updating a driver, also bump the root `packageManifest.json` version so HPM bundle users get notified.

## Support & Community

- **Questions?** Visit the [Hubitat Community Forums](https://community.hubitat.com/)
- **Issues?** Open a [GitHub issue](https://github.com/madskristensen/hubitat-drivers/issues)
- **HPM Status:** Submitted to community master list (PR [#106](https://github.com/HubitatCommunity/hubitat-packagerepositories/pull/106))
