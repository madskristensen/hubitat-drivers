# Skill: Hubitat Mode-Aware Setpoint Commands (tempUp/tempDown)

**Confidence:** low  
**First validated:** 2026-05-18 — Daikin WiFi v0.1.7 (multi-mode thermostat setpoint adjustment)  
**Author:** Tank

---

## Summary

In multi-mode thermostats, temperature adjustment commands (`tempUp`, `tempDown`) must be **mode-aware**: the command should adjust only the setpoint(s) relevant to the current mode, not all setpoints unconditionally.

**Pattern:**
- **Heat mode:** Adjust `heatingSetpoint` only
- **Cool mode:** Adjust `coolingSetpoint` only
- **Auto mode:** Adjust **both** `heatingSetpoint` and `coolingSetpoint` by the same delta
- **Off/Dry/Fan modes:** Log a message that nothing changes; return immediately

---

## Daikin BRP069B Implementation

The Daikin driver supports 6 modes via the `/aircon/get_control_info` endpoint:
- `pow=1, mode=0` → Heat
- `pow=1, mode=1` → Cool
- `pow=1, mode=2` → Auto / both heat and cool
- `pow=1, mode=3` → Dry (dehumidify, no heating/cooling)
- `pow=1, mode=4` → Fan (circulation only)
- `pow=0` → Off

### ❌ Incorrect (mode-blind)

```groovy
def tempUp() {
    // WRONG: always adjusts heating, ignoring mode
    def current = device.currentValue("heatingSetpoint")
    setHeatingSetpoint(current + 0.5)
}
```

### ✅ Correct (mode-aware)

```groovy
def tempUp() {
    String mode = device.currentValue("thermostatMode")
    if (mode in ["off", "fan", "dry"]) {
        log.info "[Driver] ${device.displayName} mode '${mode}' has no active setpoint"
        return
    }
    
    Double stepDelta = device.currentValue("unit") == "C" ? 0.5 : 1.0
    
    if (mode == "heat") {
        Double current = device.currentValue("heatingSetpoint")
        setHeatingSetpoint(current + stepDelta)
    } else if (mode == "cool") {
        Double current = device.currentValue("coolingSetpoint")
        setCoolingSetpoint(current + stepDelta)
    } else if (mode == "auto") {
        Double heatCurrent = device.currentValue("heatingSetpoint")
        Double coolCurrent = device.currentValue("coolingSetpoint")
        setHeatingSetpoint(heatCurrent + stepDelta)
        setCoolingSetpoint(coolCurrent + stepDelta)
    }
}

def tempDown() {
    String mode = device.currentValue("thermostatMode")
    if (mode in ["off", "fan", "dry"]) {
        log.info "[Driver] ${device.displayName} mode '${mode}' has no active setpoint"
        return
    }
    
    Double stepDelta = device.currentValue("unit") == "C" ? 0.5 : 1.0
    
    if (mode == "heat") {
        Double current = device.currentValue("heatingSetpoint")
        setHeatingSetpoint(current - stepDelta)
    } else if (mode == "cool") {
        Double current = device.currentValue("coolingSetpoint")
        setCoolingSetpoint(current - stepDelta)
    } else if (mode == "auto") {
        Double heatCurrent = device.currentValue("heatingSetpoint")
        Double coolCurrent = device.currentValue("coolingSetpoint")
        setHeatingSetpoint(heatCurrent - stepDelta)
        setCoolingSetpoint(coolCurrent - stepDelta)
    }
}
```

---

## Key Design Decisions

### 1. Reuse Existing Setters

Call `setHeatingSetpoint(...)` and `setCoolingSetpoint(...)` rather than duplicating their null-check, range-validation, and write logic:

```groovy
// ✅ GOOD: all validation happens in the setters
setHeatingSetpoint(current + stepDelta)

// ❌ BAD: duplicates validation, easy to miss edge cases
Double newVal = current + stepDelta
if (newVal < 5 || newVal > 40) { return }
sendControlWrite([stemp: newVal, mode: "0"])  // Wrong mode value; forgot to guard "stemp"
```

### 2. Step Size by Unit

- **Celsius:** 0.5°C per step (granular, typical for EU thermostats)
- **Fahrenheit:** 1°F per step (typical for US thermostats)

Set the step size based on the device's current unit setting, not the user's locale:

```groovy
Double stepDelta = device.currentValue("unit") == "C" ? 0.5 : 1.0
```

### 3. Auto Mode: Adjust Both Setpoints

In Auto mode, a single `tempUp` or `tempDown` adjusts **both** `heatingSetpoint` and `coolingSetpoint` in the same direction by the same delta. This preserves the separation between heat and cool setpoints while shifting the overall comfort band up or down:

```
Before tempUp: Heat 18°C, Cool 24°C  (separation: 6°C)
After tempUp:  Heat 18.5°C, Cool 24.5°C  (separation: 6°C)
```

Do NOT collapse them into a single value — maintain the dual-setpoint design.

---

## Related Patterns

### Off/Dry/Fan Mode Handling

These modes have no active heating or cooling. Log at INFO level (not WARN) to avoid spurious alarms:

```groovy
if (mode in ["off", "fan", "dry"]) {
    log.info "[Driver] ${device.displayName} mode '${mode}' has no active setpoint — ignoring tempUp"
    return
}
```

### Safe Getter Pattern

Guard `currentValue()` calls with safe dereference and fallback:

```groovy
Double current = device.currentValue("heatingSetpoint") ?: 20.0
```

This handles freshly-installed devices that may not have any setpoint set yet.

---

## Test Coverage

1. **Mock each mode:**
   - Set `thermostatMode = "heat"` and call `tempUp()`; verify only `heatingSetpoint` changes
   - Set `thermostatMode = "cool"` and call `tempUp()`; verify only `coolingSetpoint` changes
   - Set `thermostatMode = "auto"` and call `tempUp()`; verify both setpoints increase by same delta
   - Set `thermostatMode = "off"` and call `tempUp()`; verify log message and no setpoint change

2. **Edge cases:**
   - Both setters missing attributes (fresh device) — guard with `?: defaultValue`
   - Both setters at max/min bounds — setter validation prevents out-of-range
   - Rapid tempUp/tempDown calls — setter queuing logic handles (or log throttling if no queue)

---

## References

- **daikin-wifi v0.1.7** — `tempUp()` and `tempDown()` methods implementing full mode-aware logic
- **Skill: hubitat-sentinel-value-guards** — Guard numeric attributes before dereference
- **Skill: hubitat-event-hygiene** — Setpoint changes emit `descriptionText` describing the mode and delta
