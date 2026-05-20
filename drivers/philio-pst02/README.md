# Philio PST02 Enhanced

Hubitat driver for Philio PST02 A/B/C sensors with a guided configuration UX for parameters 5/6/7.

## Why this fork

The original PST02 drivers often expose raw bitmask integers (`para5`, `para6`, `para7`) that are difficult to remember and maintain.  
This fork keeps proven device behavior but adds a guided preference model that calculates those parameter values automatically.

## Highlights

1. **Guided mode (recommended):** Human-readable toggles/dropdowns for parameter 5, 6, and 7 behavior.
2. **Variant-aware handling:** Auto-detects PST02-B vs PST02-A/C and applies only relevant bit options.
3. **Advanced raw mode:** Still available for power users who want explicit numeric values.
4. **HA/Z-Wave JS aligned labels:** Guided options use the same parameter naming model used by modern Z-Wave JS device configs.

## Install

### HPM

1. Apps → Hubitat Package Manager → Install → **From a URL**
2. Use:
   `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/philio-pst02/packageManifest.json`

### Manual

Import:
`https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/philio-pst02/philio-pst02.groovy`

Then assign the device driver type:
**Philio PST02 Enhanced**

## Changelog

| Version | Date       | Notes |
|---------|------------|-------|
| 1.4.1   | 2026-05-19 | Fix temperature unit mismatch on some PST02 devices by deriving incoming report unit from SensorMultilevel scale (0=C, 1=F) instead of p5TempScale preference. |
| 1.4.0   | 2026-05-19 | Add missing `commandClassVersions` map (was null — Z-Wave frames were parsed without version hints); add P21 prefix to temperatureDifferential label; auto-set pendingResync in updated() so preference changes sync on next wakeup without a manual Configure press. |
| 1.3.0   | 2026-05-19 | Fix temperature conversion bug: P5 bit 3 temperature scale logic was inverted (device ran in Fahrenheit mode even when Celsius was selected), causing readings like 149°F instead of 63°F. Unit detection no longer relies on cmd.scale since PST02 always sends 0 regardless. |
| 1.2.0   | 2026-05-19 | Performance: eliminate implicit globals, typed returns, cache isPst02BVariant(), remove redundant configurationGet(12), reduce map allocations. |
| 1.1.0   | 2026-05-19 | Fix implicit global variable in SecurityMessageEncapsulation; remove duplicate case 12 and dangling break in ConfigurationReport; fix log.warn misuse in configure/refresh/updated; guard WakeUpNotification debug log with logEnable; re-enable auto-disable of debug logging after 30 min; remove German upstream comments. |
| 1.0.0   | 2026-05-19 | Initial Mads fork with guided parameter 5/6/7 UX, variant-aware bitmask calculation, and optional advanced raw override. |
