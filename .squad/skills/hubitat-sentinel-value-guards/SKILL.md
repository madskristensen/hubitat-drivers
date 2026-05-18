# Skill: hubitat-sentinel-value-guards

**Confidence:** high  
**Validated:** 2026-05-18 — Daikin BRP069B driver sensors (htemp, otemp, hhum); Tank applied pattern across v0.1.4-v0.1.7; Cypher confirmed multi-endpoint usage  
**Authors:** Tank, Cypher

---

## Summary

Many IoT device APIs use **sentinel values** (placeholder strings) to indicate unavailable sensors or missing data. The Daikin BRP069B WiFi adapter returns the literal string `"-"` when a sensor is absent (e.g., unit has no humidity sensor, outdoor temperature not available).

A common bug: checking truthiness (`if (value)`) on `"-"` passes (non-null, non-empty string), then attempting numeric parse throws `NumberFormatException` or `NullPointerException`.

**Pattern:** Always guard numeric parses with `.isNumber()` **before** construction, even when a string value is non-null.

---

## Daikin BRP069B Example

The Daikin local HTTP API returns responses like:

```text
ret=OK,htemp=22.5,otemp=-,hhum=-
```

Three fields may return `"-"` when the sensor is unavailable:
- `htemp` (indoor temperature) — rare, but possible on some firmware
- `otemp` (outdoor temperature) — **very common**; many units lack outdoor sensor
- `hhum` (indoor humidity) — **very common**; most units lack humidity sensor

### ❌ Incorrect (crashes when `otemp="-"`)

```groovy
private void handleSensorInfo(Map kv) {
    String otemp = kv.otemp
    if (otemp) {  // ← WRONG: "-" is truthy
        double temp = Double.parseDouble(otemp)  // ← Crashes: NumberFormatException
        sendEvent(name: "outsideTemp", value: temp)
    }
}
```

### ✅ Correct

```groovy
private void handleSensorInfo(Map kv) {
    String otemp = kv.otemp
    if (otemp?.isNumber()) {  // ← Guard before parse
        sendEvent(name: "outsideTemp", value: Double.parseDouble(otemp))
    }
}
```

---

## Groovy `.isNumber()` Behavior

Groovy's built-in `isNumber()` on String returns `true` only for valid numeric representations:

| Value | `isNumber()` | Notes |
|---|---|---|
| `"22.5"` | `true` | Valid decimal |
| `"22"` | `true` | Valid integer |
| `"-5"` | `true` | Valid negative |
| `"22.0"` | `true` | Trailing zero OK |
| `"-"` | `false` | **Sentinel only** |
| `""` | `false` | Empty string |
| `null` | `false` | Null is not numeric |
| `"N/A"` | `false` | Non-numeric string |
| `"22 C"` | `false` | Units not allowed |

---

## Pattern Across Daikin Driver

The pattern appears in multiple methods in `daikin-wifi.groovy`:

```groovy
// Line 578: handleSensorInfo (indoor temp)
if (htemp?.isNumber()) {
    sendEvent(name: "temperature", value: Double.parseDouble(htemp), unit: "°C")
}

// Line 608: handleSensorInfo (outdoor temp)
if (otemp?.isNumber()) {
    sendEvent(name: "outsideTemp", value: Double.parseDouble(otemp), unit: "°C")
}

// Line 615: handleSensorInfo (humidity — nullable sensor)
if (hhum?.isNumber()) {
    sendEvent(name: "humidity", value: Integer.parseInt(hhum))
}

// Line 623: handleControlInfo (setpoint — may be "-" in fan/dry modes)
if (stemp?.isNumber()) {
    sendEvent(name: "thermostatSetpoint", value: Double.parseDouble(stemp), unit: "°C")
}

// Line 647: handleWeekPower (daily kWh — array of slash-separated values)
s_dayw.split("/").each { String dayStr ->
    if (dayStr?.isNumber()) {
        // safe to parse
    }
}
```

---

## When to Use This Pattern

- **Always** when a remote API may return sentinel values ("-", "N/A", "unknown", etc.) to signal unavailable data
- **Always** before calling `Double.parseDouble()`, `Integer.parseInt()`, `BigDecimal(String)`, `Float.parseFloat()`
- **Do not** skip the guard just because a field is supposedly required — defensive coding catches protocol variations and firmware quirks
- **Safe combination:** `if (value?.isNumber()) { /* parse safely */ }`  — the `?.` safe-dereference operator combined with `isNumber()` is idiomatic Groovy

---

## Cross-Driver Applicability

This pattern is reusable across any Hubitat driver that consumes external HTTP APIs:

- **Tuya Local** — devices may return empty or placeholder values for capabilities they don't have
- **Watts Home API** — cloud responses may omit optional fields or use nulls
- **Home Connect** — appliances may not report all sensors
- **Any REST API driver** — sentinel handling is a best practice

---

## References

- daikin-wifi v0.1.4+ — `handleSensorInfo` (lines 578, 608, 615), `handleControlInfo` (line 623), `handleWeekPower` (line 647)
- Decision: `.squad/decisions/closed/tank-daikin-wifi-sentinel-values.md` (if present)
- Pattern validated across all Daikin driver versions (0.1.4 through 0.1.7)
