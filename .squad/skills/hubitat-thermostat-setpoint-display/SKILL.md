# SKILL: Hubitat Thermostat setpointDisplay Attribute

**Confidence:** low
**Domain:** Groovy / Hubitat driver development
**First applied:** daikin-wifi v0.1.6 (2026-05-18)
**Extracted by:** Tank

---

## Problem

Raw thermostat driver attributes (`heatingSetpoint`, `coolingSetpoint`, `thermostatMode`) require dashboard users to tile multiple attributes and mentally infer the active value from mode. Peer drivers (Venstar, Ecobee, Honeywell, Sensi per Trinity's 2026-05-18 ecosystem survey) solve this with a single computed display string.

---

## Pattern: composeSetpointDisplay()

Add a private method that returns a mode-appropriate human-readable string:

```groovy
attribute "setpointDisplay", "string"   // in metadata definition block
```

```groovy
private String composeSetpointDisplay() {
    String mode  = device.currentValue("thermostatMode") ?: "off"
    String scale = location?.temperatureScale ?: "F"
    BigDecimal h = device.currentValue("heatingSetpoint") as BigDecimal
    BigDecimal c = device.currentValue("coolingSetpoint") as BigDecimal
    String hStr  = h != null ? "${h}" : "--"
    String cStr  = c != null ? "${c}" : "--"
    switch (mode) {
        case "off":  return "Off"
        case "heat": return "Heat: ${hStr}°${scale}"
        case "cool": return "Cool: ${cStr}°${scale}"
        case "auto": return "Auto: ${hStr}°${scale} / ${cStr}°${scale}"
        case "dry":  return "Dry"
        case "fan":  return "Fan"
        default:     return "${mode.capitalize()}: ${hStr}°${scale}"
    }
}
```

### Where to emit

**Parse path (poll read-back):** at the end of your `handleControlInfo` (or equivalent), AFTER mode and setpoint events have been emitted:
```groovy
emitIfChanged("setpointDisplay", composeSetpointDisplay(), null, "${device.displayName} setpoint display updated")
emitLastActivity()
```

**Command path (immediate refresh):** at the end of each setter that changes mode or setpoints so the dashboard tile updates without waiting for the next poll:
```groovy
// in setThermostatMode, off(), setHeatingSetpoint, setCoolingSetpoint:
sendControlWrite([...])
emitIfChanged("setpointDisplay", composeSetpointDisplay(), null, "${device.displayName} setpoint display updated")
```

### Why this works

`sendEvent` is synchronous in Hubitat drivers — `device.currentValue()` reflects just-emitted values immediately. So `composeSetpointDisplay()` called after emitting `thermostatMode` / `heatingSetpoint` / `coolingSetpoint` will see the new values.

---

## Null safety

`device.currentValue()` returns `null` on a freshly installed device before any poll has run. Always guard:
- `device.currentValue("thermostatMode") ?: "off"` — fall through to `case "off"` → "Off"
- `h != null ? "${h}" : "--"` — show "--" until first poll populates setpoints

---

## Applicability

Any Hubitat thermostat driver with:
- `thermostatMode` attribute (standard Thermostat capability)
- `heatingSetpoint` and/or `coolingSetpoint` attributes
- `emitIfChanged()` event hygiene helper

**Candidates in this repo:** SunStat Connect Plus — could adopt with minimal changes (one method + emit calls in `parseThermostat` + mode/setpoint setters).

---

## Confidence note (low)

Pattern is sourced from Trinity's ecosystem survey observation of peer drivers, not from official Hubitat capability documentation. The `setpointDisplay` attribute name is not a Hubitat platform standard — it's a community convention. Dashboard tiles that reference it by name will only work if this exact attribute name is used. Consider whether to standardize naming before adopting across multiple drivers.

---

## References

- `drivers/daikin-wifi/daikin-wifi.groovy` v0.1.6 — `composeSetpointDisplay()` method + 5 emit call sites
- `.squad/decisions/inbox/tank-daikin-wifi-v016-bundle.md` — Item 2 rationale
- `.squad/files/daikin-ecosystem-survey-memo.md` — Trinity's peer-driver survey (setpointDisplay pattern observed in Venstar, Ecobee, Honeywell, Sensi)
