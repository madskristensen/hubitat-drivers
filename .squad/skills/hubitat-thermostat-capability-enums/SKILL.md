# Skill: Hubitat Thermostat Capability — Enum Value Validation

**Confidence:** low  
**First validated:** 2026-05-18 — Daikin WiFi v0.1.7 (rejected invalid `thermostatOperatingState` value)  
**Author:** Tank

---

## Summary

The Hubitat **Thermostat** capability defines several attributes as enums with a **fixed set of valid values**. Emitting values outside this set may cause:
- Dashboard tiles to not display or show unexpected rendering
- Rule Machine conditionals to behave erratically
- Automations to fail silently

**Key attributes:**
- `thermostatMode` — valid enum defined in capability
- `thermostatOperatingState` — **strictly constrained enum** (NOT just any string)
- `thermostatFanMode` — valid enum
- `supportedThermostatModes` — JSON array of valid mode strings
- `supportedThermostatFanModes` — JSON array of valid fan mode strings

---

## thermostatOperatingState — The Strict Enum

This attribute has a **fixed set of 6 valid values** per the Hubitat Thermostat specification:

| Value | Meaning | Notes |
|-------|---------|-------|
| `"heating"` | Actively heating | Compressor/heat strip running |
| `"cooling"` | Actively cooling | Compressor/fans running |
| `"fan only"` | Circulation mode | Fan running, no heating/cooling |
| `"pending heat"` | Preparing to heat | Startup delay or sensor warmup |
| `"pending cool"` | Preparing to cool | Startup delay or sensor warmup |
| `"idle"` | Idle / standby | No active operations |

### ❌ Invalid Values (Causes Issues)

| Invalid Value | Why It's Wrong |
|---|---|
| `"drying"` | Not in the spec; dashboards may reject or misinterpret |
| `"fan"` | Ambiguous; should be `"fan only"` |
| `"dry"` | Device-specific term; use `"fan only"` instead |
| `"unknown"` | Not in the spec |
| `""` (empty) | Valid technically, but conveys no information; use `"idle"` |

### ✅ Correct Mapping (Daikin Example)

Daikin BRP069B supports 6 modes:

```groovy
private void updateOperatingState() {
    String mode = device.currentValue("thermostatMode")
    String acPower = device.currentValue("switch")
    
    String operatingState
    if (acPower == "off") {
        operatingState = "idle"
    } else if (mode == "heat") {
        operatingState = "heating"
    } else if (mode == "cool") {
        operatingState = "cooling"
    } else if (mode == "auto") {
        // Auto mode — emit "pending heat" or "pending cool" until actual heating/cooling detected
        // For simplicity without real-time power monitoring, emit "idle" for now
        operatingState = "idle"
    } else if (mode == "dry") {
        operatingState = "fan only"   // ← Dry mode = circulation, no heating/cooling
    } else if (mode == "fan") {
        operatingState = "fan only"
    } else {
        operatingState = "idle"
    }
    
    sendEvent(name: "thermostatOperatingState", value: operatingState,
              descriptionText: "${device.displayName} operating state → ${operatingState}")
}
```

**Key correction:** `mode == "dry"` maps to `operatingState = "fan only"`, NOT `"drying"`.

---

## thermostatMode — The Capability-Defined Enum

`thermostatMode` is also an enum, but **the valid values depend on which modes the device supports**. Declare supported modes in `installed()` and `updated()`:

```groovy
void installed() {
    // Daikin supports: off, heat, cool, auto, dry, fan
    sendEvent(name: "supportedThermostatModes", 
              value: JsonOutput.toJson(["off", "heat", "cool", "auto", "dry", "fan"]),
              type: "digital")
    
    // Default to heat mode
    sendEvent(name: "thermostatMode", value: "heat", type: "digital")
}
```

Emitting `thermostatMode = "sleep"` (example) when `supportedThermostatModes` does not include `"sleep"` will confuse Rule Machine and dashboards.

---

## thermostatFanMode — The Fan-Specific Enum

Similar to `thermostatMode`, but constrained to fan operation:

| Valid Value | Meaning |
|---|---|
| `"auto"` | Fan runs on demand (default) |
| `"on"` | Fan runs continuously |
| `"circulate"` | Low-speed circulation (some thermostats) |

Daikin maps its `f_rate` (fan rate) to Hubitat fan modes:

```groovy
// Daikin f_rate values: "A" (auto), "B", "C", "D", "E" (speeds 1-4)
// Map to Hubitat thermostat fan modes:
private String daikinFRateToFanMode(String fRate) {
    switch (fRate) {
        case "A":
            return "auto"
        case ~"[B-E]":
            return "on"     // ← Any speed 1-4 = continuous operation
        default:
            return "auto"
    }
}
```

---

## Declare Supported Enums at Install

Always call `sendEvent()` to declare supported modes **before** the device becomes functional:

```groovy
void installed() {
    // Modes the AC unit supports (Daikin's full set)
    sendEvent(name: "supportedThermostatModes", 
              value: JsonOutput.toJson(["off", "heat", "cool", "auto", "dry", "fan"]),
              type: "digital")
    
    // Fan modes the unit supports
    sendEvent(name: "supportedThermostatFanModes", 
              value: JsonOutput.toJson(["auto", "on"]),
              type: "digital")
    
    // Set initial values
    sendEvent(name: "thermostatMode", value: "off", type: "digital")
    sendEvent(name: "thermostatOperatingState", value: "idle", type: "digital")
    sendEvent(name: "thermostatFanMode", value: "auto", type: "digital")
}
```

---

## Audit Checklist

When implementing a thermostat driver:

1. ✅ Declare `supportedThermostatModes` in `installed()` — matches device capabilities
2. ✅ Declare `supportedThermostatFanModes` in `installed()` — matches device capabilities
3. ✅ Every `sendEvent(name: "thermostatMode", ...)` uses a value in `supportedThermostatModes`
4. ✅ Every `sendEvent(name: "thermostatOperatingState", ...)` uses **ONLY** values from: `"heating"`, `"cooling"`, `"fan only"`, `"pending heat"`, `"pending cool"`, `"idle"`
5. ✅ Device-specific mode names (e.g., `"drying"`) are mapped to spec values (e.g., `"fan only"`)
6. ✅ No empty strings or null values for these enums

**Grep audit:**
```bash
grep -n "thermostatOperatingState\|thermostatMode\|thermostatFanMode" drivers/your-driver/*.groovy | grep -i "\"[^\"]*\")" | head -20
```

Review each emit to confirm the value is in the valid set.

---

## References

- **Hubitat Thermostat Capability Spec** — Official docs at docs.hubitat.com
- **daikin-wifi v0.1.7** — Corrected `thermostatOperatingState` mapping; removed invalid `"drying"` value
- **Skill: hubitat-event-hygiene** — Event emission guidelines; ensure every emit includes `descriptionText`
