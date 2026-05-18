# Decision: DP 105 (Log Brightness) Non-Writable on Sideline Elite — Command Removed

**Date:** 2026-05-17  
**Author:** Tank (Driver Developer)  
**Driver version:** v0.1.11

## Finding

Mads tested `setLogBrightness("12")` directly from the Hubitat device page on the real
Touchstone Sideline Elite. The fireplace logs did not respond. This was a direct call to the
named command's send path — it bypassed `setRawDP` and its known coercion bug, refuting the
earlier hypothesis that the failure was purely a type-coercion issue.

Additionally, Cypher confirmed in the decisions log that DP 105 is string-typed per the
device YAML, so a string → integer coercion issue in `setRawDP` would not have explained
the named-command failure anyway.

**Conclusion:** DP 105 is read-only or unimplemented on the Sideline Elite firmware. Writes
are silently dropped regardless of value type or send path.

## Action Taken

- Removed `setLogBrightness` command from driver (v0.1.11)
- Removed `logBrightness` attribute from driver metadata
- Removed `defaultLogBrightness` power-on default preference
- Removed `LOG_BRIGHTNESS_OPTIONS` constant
- Removed `logBrightness: 105` from `SIDELINE_PROFILE_DPS`
- DP 105 inbound status updates are now silently absorbed at debug level only (the device
  does appear to send DP 105 in status responses, but the value is not actionable)

## Pending

The actual write target for log/ember brightness control on the Sideline Elite is unknown.
DP 109 is under separate investigation by Cypher (ember brightness write target). If a
confirmed writable DP is identified, the command may be re-added with the correct DP number.

Do NOT re-add `setLogBrightness` pointed at DP 105 without hardware-confirmed write evidence.
