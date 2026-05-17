# Hubitat Drivers

Community Hubitat Elevation drivers by Mads Kristensen. First public release: **Gemstone Lights**.

> **Current status:** Gemstone Lights v0.4.0 is beta software, working in production, and tested by the author on a real controller.

## Install via HPM (recommended)

1. In Hubitat: **Apps → Hubitat Package Manager → Install → "From a URL"**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json`
3. Follow the prompts

## Listed in the community HPM master list

Status: **Submitted** — PR filed as HubitatCommunity/hubitat-packagerepositories#106.

## Drivers

| Driver | Status | Description | Docs |
|--------|--------|-------------|------|
| **Gemstone Lights** | Beta | Cloud REST integration for Gemstone permanent outdoor lighting, including favorites-first LightEffects, named effects, and color-temperature fallback | [Driver README](drivers/gemstone-lights/README.md) |

## Manual install

1. Open your **Hubitat hub web UI**.
2. Go to **Drivers Code**.
3. Create a new driver and paste in `drivers/gemstone-lights/gemstone-lights.groovy`.
4. Save the driver, then create a virtual device using type **Gemstone Lights**.
5. Open the device and enter your Gemstone account email and password in **Preferences**.
6. See the per-driver README for capabilities, setup details, and Rule Machine examples.

## Compatibility

- **Hub:** Hubitat Elevation C-7, C-8
- **Platform:** Recent Hubitat platform releases
- **Network:** Driver-specific; Gemstone Lights currently requires outbound HTTPS to the Gemstone cloud REST API

## Contributing

Found a bug or have an idea? Open an issue or send a pull request.

## License

These drivers are released under the MIT License. See `LICENSE` for details.

## Authors

- **Mads Kristensen** — Project lead

---

Questions? Visit the [Hubitat Community Forums](https://community.hubitat.com/).
