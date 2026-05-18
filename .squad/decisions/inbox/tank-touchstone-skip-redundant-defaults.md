# Decision: Skip redundant DP writes in power-on defaults

**Date:** 2026-05-18  
**Author:** Tank  
**Driver:** Touchstone Fireplace v0.1.23  
**Status:** Shipped

---

## Context

The `applyOnPowerOnDefaults()` function writes four DPs (flameColor, flameBrightness, flameSpeed, charcoalColor) shortly after the fireplace turns on, to restore user-configured preferred visual state. Previously it always sent these writes unconditionally — even if the device was already in the desired state (e.g., the user turned the fireplace off and back on and the flame color never changed).

Unconditional writes cause:
- Unnecessary wire traffic (Tuya TCP frames sent when nothing changes)
- Visible device flicker/click on some Tuya fireplace hardware (the device resets the effect even when writing the same value)
- Misleading `[info] Applied default: flameColor=Orange` log entries when nothing actually changed

## Decision

Before each DP write in `applyOnPowerOnDefaults()`, compare `device.currentValue(attributeName)` against the configured default label. Only send the write if:
- The current attribute value **differs** from the configured default, OR
- The current attribute value is **null** (state unknown — driver just installed or no status received yet)

If the current value matches the configured default, skip the write entirely. Log at `traceLog` level.

## Rationale

- **Null = proceed**: After power-on there is a race between the defaults window and the first STATUS frame from the device. If the driver has no prior state, skipping would silently fail to apply the user's preference. Treating null as "needs the default" is the safe side.
- **Each default is independent**: Skipping flameColor does not affect flameBrightness. All four are evaluated independently.
- **Log hygiene**: Skipped lines at `traceLog` (protocol firehose, off by default). Applied lines at `debugLog` (readable in production). Consistent with v0.1.22 trace/debug taxonomy.
- **No protocol behavior change**: Timing, ordering, and the enqueue/pump mechanism are unchanged. This is purely a conditional guard around the existing write path.

## Pattern (reusable)

```groovy
String current = device.currentValue("attributeName")
if (current != null && current == configuredDefault) {
    traceLog "applyOnDefaults: skipping defaultAttributeName — already '${configuredDefault}'"
} else {
    debugLog "applyOnDefaults: applying defaultAttributeName = '${configuredDefault}' (was '${current}')"
    // existing DP write logic
}
```

Applicable to any driver that writes user-configured defaults at a lifecycle event (power-on, scene, schedule trigger). See also: Gemstone Smart Heater zone defaults, SunStat Solar Control setpoint defaults.
