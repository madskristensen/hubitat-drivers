# Hubitat Drivers

Community Hubitat Elevation drivers by Mads Kristensen.

**4 production-ready drivers** • 🌐 2 Local LAN • ☁️ 2 Cloud API • 📦 HPM Ready

---

## Available Drivers

### 🌡️ Climate Control

| Driver                     | Type         | Key Features                                                                 | Docs                                            |
| -------------------------- | ------------ | ---------------------------------------------------------------------------- | ----------------------------------------------- |
| **Daikin WiFi Thermostat** | 🌐 Local LAN | No cloud required • Indoor/outdoor temp • Humidity • Energy meter • HTTP API | [📖 Docs](drivers/daikin-wifi/README.md)        |
| **SunStat Connect Plus**   | ☁️ Cloud API | Floor heating • Auto-discovery • Parent/child • Watts® Home API              | [📖 Docs](drivers/sunstat-thermostat/README.md) |

### 🏠 Home Ambiance

| Driver                          | Type         | Key Features                                                                   | Docs                                              |
| ------------------------------- | ------------ | ------------------------------------------------------------------------------ | ------------------------------------------------- |
| **Gemstone Lights**             | ☁️ Cloud API | Permanent outdoor lighting • Named effects • Favorites support • Color control | [📖 Docs](drivers/gemstone-lights/README.md)      |
| **Touchstone / Tuya Fireplace** | 🌐 Local LAN | Flame & log colors • Brightness • Heater • DP discovery • No cloud required    | [📖 Docs](drivers/touchstone-fireplace/README.md) |

**Legend:** 🌐 Local LAN = Runs entirely on your network • ☁️ Cloud API = Requires internet connection

---

## Quick Start

### Option 1: Install All Drivers (Recommended)

**Install via HPM bundle URL:**

1. **Apps → Hubitat Package Manager → Install → "From a URL"**
2. **Paste:** `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/packageManifest.json`
3. **Select** the drivers you want from the checklist
4. **Follow** the prompts

> **💡 Tip:** Install via the bundle URL **or** per-driver URL — not both. Installing a driver through both paths causes duplicate update prompts.

### Option 2: Install Individual Drivers

**Install specific drivers via HPM:**

1. **Apps → Hubitat Package Manager → Install → "From a URL"**
2. **Paste** one of these URLs:

   ```text
   Daikin WiFi:      https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/daikin-wifi/packageManifest.json
   Gemstone Lights:  https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json
   SunStat Connect:  https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/sunstat-thermostat/packageManifest.json
   Touchstone:       https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/touchstone-fireplace/packageManifest.json
   ```

3. **Follow** the prompts

### Option 3: Manual Installation

For advanced users or offline installations:

1. **Open** your Hubitat hub web UI
2. **Navigate to** Drivers Code
3. **Create** a new driver and paste the source code from `drivers/<driver-name>/<driver-name>.groovy`
4. **Save** and create a virtual device using the driver
5. **Configure** device preferences (see per-driver README)

---

## Compatibility

**Hub Requirements:**

- ✅ Hubitat Elevation C-7, C-8
- ✅ Platform Version: 2.3.3.x or newer

**Network Requirement| :**

| Driver    | Network        | Type | Details                                                          |
| ---------------- | -------------- | ----------------------------------------------------------------------- |
| -- |
| Daikin Wi | i    | 🌐 Loca | LAN | Direct IP access to BRP069B adapter • No cloud account needed     |
| | Gemstone       | ☁️ Clou        | API | Outbound HTTPS to Gemstone cloud                                  |
| | SunStat        | ☁️ Clou        | API | Outbound HTTPS to Watts® Home cloud                               |
| | Touchston      | 🌐 Loca        | LAN | Direct IP access to device • Requires Tuya local key (one-time se |up)  |

---

## Contributing

**Found a bug or have an idea?** Open an [issue](https://github.com/madskristensen/hubitat-drivers/issues) or send a pull request!

**Version bump reminder:** When updating a driver, also bump the root `packageManifest.json` version so HPM bundle users get notified.

---

## 💬 Support & Community

- **Questions?** Visit the [Hubitat Community Forums](https://community.hubitat.com/)
- **Issues?** Open a [GitHub issue](https://github.com/madskristensen/hubitat-drivers/issues)
- **HPM Status:** Submitted to community master list (PR [#106](https://github.com/HubitatCommunity/hubitat-packagerepositories/pull/106))
