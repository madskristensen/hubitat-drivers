# PurpleAir AQI Virtual Sensor

Reads AQI data from the [PurpleAir cloud API](https://api.purpleair.com/) using your API key. Supports geolocation-based multi-sensor averaging (uses nearby sensors automatically) or a specific sensor index. Implements US EPA Barkjohn 2021 AQI correction for wildfire smoke accuracy during wildfire season.

## Capabilities

| Attribute | Details |
|---|---|
| `aqi` | Current AQI value (PM 2.5) |
| `airQualityIndex` | Same AQI value exposed via Hubitat's `AirQuality` capability |
| `pm2_5` | Raw PM2.5 mass concentration (`µg/m³`) averaged from the contributing sensor set |
| `temperature` | PurpleAir ambient temperature converted to the hub's temperature scale (rounded to 1 decimal) |
| `humidity` | PurpleAir relative humidity (`%`) |
| `confidence` | Selected sensor confidence, or the lowest contributing sensor confidence when averaging |
| `category` | Air quality category (Good / Moderate / Unhealthy / etc.) |
| `conversion` | Active conversion algorithm name |
| `sites` | Sensor sites contributing to the reading |
| `lastActivity` | ISO 8601 timestamp of last successful API response |

Capabilities: `Sensor`, `Polling`, `Initialize`, `AirQuality`, `TemperatureMeasurement`, `RelativeHumidityMeasurement`.

## Setup

1. **Get a PurpleAir API key** — visit [develop.purpleair.com](https://develop.purpleair.com/).
2. In Hubitat, go to **Devices → Add Device → Virtual** and create a new virtual device using the **PurpleAir AQI Virtual Sensor** driver.
3. In **Preferences**, enter your API key.
4. Choose a sensor mode:
   - **Search for devices** (default): uses hub GPS coordinates to find and average nearby public sensors within the configured range.
   - **Specific sensor**: uncheck *Search for devices* and enter a sensor index from the map URL (`?select=INDEX` at [map.purpleair.com](https://map.purpleair.com/)).
5. Choose a conversion algorithm (US EPA is recommended for wildfire smoke accuracy).
6. Set an update interval. **60 minutes is recommended** for normal operation — see Polling Architecture below.
7. Click **Save Preferences**. The driver refreshes immediately, then starts polling on the selected interval.

### Via Hubitat Package Manager (HPM)

1. In HPM choose **Install → From URL**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/purpleair-aqi/packageManifest.json`

## Polling Architecture

| Update interval | API requests/month | Notes |
|---|---|---|
| 1 min | ~43,800 / sensor | ⚠️ Near the free tier's 1M-points/month limit if multiple sensors queried |
| 5 min | ~8,760 / sensor | Acceptable for a single sensor |
| 60 min (default) | ~730 / sensor | Recommended; well within free tier |

The driver uses `asynchttpGet` (Hubitat's async HTTP pattern). `emitIfChanged` suppresses duplicate events when the AQI, category, conversion, sites, pm2_5, temperature, humidity, and confidence are unchanged between polls — at the 1-hour default this eliminates a large amount of duplicate event noise compared to unconditional `sendEvent`.

## What's Fixed (vs. upstream v1.3.2)

### Fix 1 — AQ&U conversion was dead code (BLOCKER)

The code checked for `"AQ and U"` but the preference dropdown emits `"AQ&U"`. String never matched → `AQandU_conversion()` was unreachable dead code. Selecting AQ&U silently returned raw PM2.5.

### Fix 2 — LRAPA and Woodsmoke requested wrong PM2.5 field (BLOCKER)

Lowercase `"lrapa"` and `"woodsmoke"` never matched preference values `"LRAPA"` and `"Woodsmoke"`. Both fell through to the `else` branch and requested `pm2.5` (atmospheric) instead of `pm2.5_cf_1` (required by both conversion formulas).

### Fix 3 — failCount never incremented; exponential backoff never triggered (MAJOR)

Operator-precedence bug: `state.failCount?:0 + 1` evaluates as `state.failCount ?: (0+1)`. failCount could never grow above 1 — the driver hammered the API at normal cadence during outages instead of progressively backing off.

## Known Limitations

- **PurpleAir free tier quota:** The API grants 1M points/month. Each sensor field requested costs 1 point per sensor returned in the result set. At 1-minute interval with a large search radius, you can approach this limit quickly. Use 60-minute intervals for typical operation.
- **Outdoor sensors only:** PurpleAir's public API returns outdoor-type sensors (`location_type=0`). Indoor sensors require different API calls.
- **Confidence threshold hardcoded:** Sensors below 90% confidence are filtered out. This is not currently configurable.

## Attribution

**Original driver:** [PurpleAir AQI Virtual Sensor](https://github.com/pfmiller0/Hubitat/blob/main/PurpleAir%20AQI%20Virtual%20Sensor.groovy) by **Peter Miller** (`pfmiller0`), version 1.3.2. The original upstream copyright block is preserved verbatim in the driver file. No license was declared in the upstream repo; used here for bug-fix contribution under fair-use assumptions.

**This fork** applies Hubitat best-practice improvements (namespace `mads`, `emitIfChanged`, `lastActivity`, sentinel guards, `logsOff`) and is maintained by Mads Kristensen as a permanent driver in this repo. Core bug fixes remain cherry-pickable as a minimal PR to pfmiller0 if desired — see `UPSTREAM-PR-DRAFT.md`.

## License

MIT License — fork maintained by Mads Kristensen (2026).

## Changelog

| Version | Date | Notes |
|---------|------|-------|
| 0.4.0 | 2026-05-18 | Bug fixes for failCount string math, disabled-poll retry storms, lat/lng degree math, and weighted-average NaN at distance=0; polish for refresh-on-save, canonical async error handling, AirQuality capability, runEvery schedules, hub temperature scale, cleaner sites output, AQI units, and lastActivity throttling |
| 0.3.0 | 2026-05-18 | parseJson guard for blank search_coords + empty API bodies; API key docs now point to develop.purpleair.com; add pm2_5, temperature, humidity, and confidence attributes (confidence reports the lowest contributing sensor score in averaging mode) |
| 0.2.0 | 2026-05-18 | namespace → mads; emitIfChanged on all poll events; descriptionText on sites uses device.displayName; 1-min interval quota warning; UUID in packageManifest; fix IQAir→PurpleAir log prefix; logsOff auto-disable after 30 min; lastActivity (Pattern B); sentinel .isNumber() guards on pm2.5 field |
| 0.1.0 | 2026-05-18 | Initial fork from pfmiller0 v1.3.2; Trinity audit fixes: AQ&U string mismatch, LRAPA/Woodsmoke case + wrong PM2.5 field, failCount operator-precedence breaking exponential backoff |
