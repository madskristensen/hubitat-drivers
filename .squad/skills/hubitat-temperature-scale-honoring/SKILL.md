---
name: "Hub Temperature Scale Honoring"
description: "Convert sensor temperatures to hub's configured scale (°C or °F) before emitting events"
domain: "events, temperature, units"
confidence: "high"
source: "validated in PurpleAir v0.4.0 (commit 2d62b05) and Honeywell T6 Pro production use"
---

## Context

Hubitat hubs can be configured in either Celsius or Fahrenheit. When a driver receives temperature data from an API or device (often in a specific unit), it must convert to the hub's configured scale before emitting the attribute.

Failing to do this confuses apps (they expect temp in hub scale), breaks automations (thermostat rules assume matching scale), and creates poor UX (dashboard shows nonsensical values).

## Patterns

### One-time Scale Discovery

Store the hub's temperature scale on driver install or update:

```groovy
void updated() {
    unschedule()
    
    // Discover hub temperature scale once
    String tempScale = location.temperatureScale ?: "F"
    
    // ... rest of initialization ...
}
```

### Conversion Helper

Create a reusable helper for API-to-hub conversion:

```groovy
/**
 * Convert temperature from one scale to another.
 * 
 * @param temp Temperature value (BigDecimal for precision)
 * @param fromScale Source scale ("C" or "F")
 * @param toScale Target scale ("C" or "F")
 * @return Converted temperature (BigDecimal)
 */
BigDecimal convertTemperature(BigDecimal temp, String fromScale, String toScale) {
    if (fromScale == toScale) return temp
    
    if (fromScale == "F" && toScale == "C") {
        return ((temp - 32) * 5 / 9).setScale(2, BigDecimal.ROUND_HALF_UP)
    } else if (fromScale == "C" && toScale == "F") {
        return ((temp * 9 / 5) + 32).setScale(2, BigDecimal.ROUND_HALF_UP)
    }
    
    return temp  // No conversion needed
}
```

### Event Emission with Correct Unit

```groovy
void parseApiResponse(def json) {
    // API returns temp in Fahrenheit
    BigDecimal apiTemp = json.temperature as BigDecimal
    String hubScale = location.temperatureScale ?: "F"
    
    // Convert if needed
    BigDecimal emitTemp = convertTemperature(apiTemp, "F", hubScale)
    String unitString = hubScale == "C" ? "°C" : "°F"
    
    sendEvent(
        name: "temperature",
        value: emitTemp.toFloat(),
        unit: unitString,
        descriptionText: "Temperature is ${emitTemp}${unitString}"
    )
}
```

## Examples

**Applied in PurpleAir AQI v0.4.0** (commit 2d62b05):
- Conversion on temperature and humidity attributes from API response
- Correct unit string emitted based on hub scale

**Applied in Honeywell T6 Pro v0.4.0+**:
- Z-Wave thermostat readings converted to hub scale
- All temperature events use `location.temperatureScale`

## Anti-Patterns

❌ **Hard-coded unit assumption:**
```groovy
sendEvent(name: "temperature", value: 72, unit: "°F")  // Breaks if hub is °C
```

❌ **Missing conversion:**
```groovy
BigDecimal apiTemp = json.temperature  // API in °C, hub expects °F
sendEvent(name: "temperature", value: apiTemp, unit: location.temperatureScale)
```

❌ **Conversion at every emit:**
```groovy
for (sensor in sensors) {
    BigDecimal temp = convertTemperature(sensor.temp, "F", location.temperatureScale)
    // ... repeat in multiple event handlers ...
}
```
Instead: store the hub scale once, reuse it.

## Why This Matters

- **User expectation:** Hubs are configured for a specific scale; drivers must respect that
- **Automation breakage:** Z-Wave and climate rules often compare temp > 70 (assuming °F); wrong scale silently breaks logic
- **App confusion:** Dasboards, rules engines, notifications all expect hub scale
- **Data integrity:** Storing "72" with unit "°C" is nonsensical; must be "22" or "72°F"

A missed conversion can go unnoticed for months if a user has only one temperature device and few automations. Scale-aware auditing should be part of any temperature-emitting driver review.
