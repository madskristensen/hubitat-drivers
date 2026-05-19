---
name: "Geospatial Distance Conversion (Lat/Lng to Miles)"
description: "Correct formula for converting lat/lng degree differences to miles, with pole clamp for numerical stability"
domain: "geospatial, math, sensor-averaging"
confidence: "high"
source: "Trinity audit and Tank fix in PurpleAir v0.4.0 (commit 2d62b05)"
---

## Context

Converting latitude/longitude degree differences to distance in miles is a common requirement in geofencing and nearest-sensor averaging. The naive approach (treating lat and lng equally) produces silently wrong results in drivers.

The correct formula requires:
1. Different miles-per-degree for latitude vs. longitude
2. Latitude scaling is roughly constant (~69 mi/deg)
3. Longitude scaling depends on latitude (`cos(latitude)`)
4. Pole clamp at ±89.5° to avoid divide-by-zero

## Patterns

### Safe Distance Calculation

```groovy
/**
 * Calculate distance between two coordinates in miles.
 * 
 * Uses a simplified formula (good enough for ±5 mile neighborhoods):
 * - Latitude: ~69 miles per degree (constant)
 * - Longitude: `cos(latitude) * 69` miles per degree
 * - Clamp latitude to ±89.5° to avoid pole singularity
 * 
 * @param lat1 Starting latitude (degrees)
 * @param lng1 Starting longitude (degrees)
 * @param lat2 Destination latitude (degrees)
 * @param lng2 Destination longitude (degrees)
 * @return Distance in miles (BigDecimal, rounded to 1 decimal)
 */
BigDecimal distance(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
    // Clamp latitudes to avoid poles (tan/cos singularity at ±90°)
    lat1 = lat1.max(-89.5).min(89.5)
    lat2 = lat2.max(-89.5).min(89.5)
    
    // Degree differences
    BigDecimal dLat = (lat2 - lat1).abs()
    BigDecimal dLng = (lng2 - lng1).abs()
    
    // Miles per degree
    BigDecimal latMiles = dLat * 69.0
    BigDecimal lngMiles = dLng * 69.0 * Math.cos(Math.toRadians((lat1 + lat2) / 2.0))
    
    // Euclidean distance
    BigDecimal distMiles = Math.sqrt((latMiles * latMiles) + (lngMiles * lngMiles))
    
    return distMiles.setScale(1, BigDecimal.ROUND_HALF_UP)
}
```

### Usage in Averaging Logic

When averaging sensor values by distance, handle **exact coordinate match** (distance = 0) specially to avoid NaN:

```groovy
/**
 * Weighted average of sensor values by distance.
 * Exact matches (distance = 0) always win; skip distant sensors.
 */
BigDecimal weightedAverageByDistance(List<Map> sensors, BigDecimal lat, BigDecimal lng) {
    List<Map> withDistance = sensors.collect { sensor ->
        [
            value: sensor.value as BigDecimal,
            distance: distance(lat, lng, sensor.lat as BigDecimal, sensor.lng as BigDecimal)
        ]
    }
    
    // Short-circuit: exact match(es) → return their average, skip distant sensors
    List<Map> exactMatches = withDistance.findAll { it.distance == 0 }
    if (exactMatches) {
        return exactMatches.collect { it.value }.sum() / exactMatches.size()
    }
    
    // Inverse distance weighting: weight = 1 / distance^2
    BigDecimal sumWeighted = 0
    BigDecimal sumWeights = 0
    withDistance.each { item ->
        if (item.distance > 0) {
            BigDecimal weight = 1.0 / (item.distance * item.distance)
            sumWeighted += item.value * weight
            sumWeights += weight
        }
    }
    
    return (sumWeights > 0) ? (sumWeighted / sumWeights).setScale(1, BigDecimal.ROUND_HALF_UP) : 0
}
```

## Examples

**Applied in PurpleAir AQI v0.4.0** (commit 2d62b05):
- `distance2degrees()` helper implements pole clamp at ±89.5°
- `sensorAverageWeighted()` short-circuits on distance == 0 to prevent NaN
- Averaging users' hubs' own sensors with exact coordinate match now works correctly

See `drivers/purpleair-aqi/purpleair-aqi.groovy` lines 320–370 (v0.4.0).

## Anti-Patterns

❌ **Treating lat and lng equally:**
```groovy
BigDecimal distance = Math.sqrt((lat2 - lat1)^2 + (lng2 - lng1)^2) * 69
// Wrong: lng degrees become nonsensical at latitudes far from equator
// At 45°N, lng error is 2x larger than lat error
```

❌ **No pole clamp:**
```groovy
BigDecimal lngMiles = dLng * 69.0 * Math.cos(Math.toRadians(lat))
// If lat = 90 (pole), cos(90°) = 0, lng becomes 0-distance (wrong)
// If user somehow gets lat > 89.99, floating point errors cascade
```

❌ **Divide-by-zero in weighted average:**
```groovy
BigDecimal weight = 1.0 / distance  // Crashes if distance == 0
```

❌ **Unguarded averaging on NaN:**
```groovy
BigDecimal sum = 0
sensors.each { s -> sum += 1.0 / 0 }  // → NaN
BigDecimal avg = sum / sensors.size()  // → NaN
```

## Why This Matters

- **Geofencing accuracy:** Wrong distance formulas silently distort geofence boxes, causing apps to trigger at wrong times
- **Neighbor sensor averaging:** Exact coordinate matches (user's hub location = sensor location) become NaN if not guarded
- **Pole edge case:** While rare in residential settings, drivers used globally must handle high latitudes (Seattle, Canada, Scandinavia)
- **Silent bugs:** Distance formula errors go unnoticed until users deploy drivers far from equator

The pole clamp is cheap insurance; exact-match guards are essential for production stability.
