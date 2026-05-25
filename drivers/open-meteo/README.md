# Open-Meteo Weather Enhanced

Free Hubitat weather driver backed by [open-meteo.com](https://open-meteo.com) — **no API key required**.

> **Note:** Hubitat ships a built-in "Open-Meteo" driver. This one is named **Open-Meteo Weather Enhanced** to avoid a name clash, and exposes additional attributes (next-hour / next-6h precipitation probability, today's high/low, hourly forecast JSON) that the built-in driver does not provide.

Designed as a drop-in weather source for the [Climate Advisor](../../apps/climate-advisor/README.md) app, but useful as a general weather sensor for any rule or dashboard.

## Features

- **No API key, no account, no rate limit headaches** — Open-Meteo is free for non-commercial use
- **Current conditions** — temperature, humidity, pressure, "feels like", cloud cover, wind speed/direction/gusts, UV index, sunrise/sunset
- **Human-readable weather text** — standard WMO descriptions ("Partly cloudy", "Light rain", "Thunderstorm") in the `weather` attribute, ready for Climate Advisor's keyword matcher
- **Next-hour & next-6h precipitation helpers** — `precipitationNextHour`, `precipitationProbabilityNextHour`, `precipitationProbabilityNext6h` for smarter rain automations
- **Today's hourly forecast** — exposed as a JSON blob (`hourlyForecast`) for future dashboard tiles
- **Auto-detects hub location and temperature scale** — both overridable
- **Single API call per poll** — current + hourly + sunrise/sunset all in one request

## Install

1. **Apps → Hubitat Package Manager → Install → From a URL**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/open-meteo/packageManifest.json`
3. Add a virtual device, set driver to **Open-Meteo Weather Enhanced**, save preferences

## Preferences

| Setting | Default | Notes |
|---|---|---|
| Latitude | hub location | Leave blank to use hub |
| Longitude | hub location | Leave blank to use hub |
| Units | Auto | Auto follows hub temperature scale; or force imperial / metric |
| Poll interval | 30 min | 15 / 30 / 60. Open-Meteo updates hourly, so 30 is the sweet spot |
| Debug logging | off | Auto-disables after 30 min |
| Info logging | on | Per-event descriptive log lines |

## Attributes

### Current
| Attribute | Type | Notes |
|---|---|---|
| `temperature` | number | TemperatureMeasurement capability |
| `humidity` | number | RelativeHumidityMeasurement capability, % |
| `pressure` | number | PressureMeasurement capability, hPa |
| `ultravioletIndex` | number | UltravioletIndex capability |
| `weather` | string | WMO description — Climate Advisor reads this |
| `weatherCode` | number | Raw WMO code |
| `apparentTemperature` | number | "Feels like" |
| `cloudCover` | number | % |
| `windSpeed` | number | mph or km/h |
| `windDirection` | number | degrees |
| `windGust` | number | mph or km/h |
| `precipitationRate` | number | Current hour, in or mm |
| `isDay` | string | "day" / "night" |
| `sunrise` | string | ISO local time |
| `sunset` | string | ISO local time |

### Precipitation helpers
| Attribute | Type | Notes |
|---|---|---|
| `precipitationNextHour` | number | Sum, in or mm |
| `precipitationProbabilityNextHour` | number | Max %, next 60 min |
| `precipitationProbabilityNext6h` | number | Max %, next 6 hours |

### Forecast
| Attribute | Type | Notes |
|---|---|---|
| `temperatureMax` | number | Today's forecast high |
| `temperatureMin` | number | Today's forecast low |
| `hourlyForecast` | string | JSON array of today's remaining hours: `[{time, temp, precip, precipProb, code, condition, wind, uv}, …]` |

### Status
| Attribute | Type | Notes |
|---|---|---|
| `lastUpdated` | string | ISO timestamp of last successful poll |
| `status` | string | `ok` or `error: <message>` |

## Data attribution

Weather data by [Open-Meteo.com](https://open-meteo.com) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
