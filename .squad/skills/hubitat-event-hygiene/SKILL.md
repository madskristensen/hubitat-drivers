# Skill: Hubitat Event Hygiene

**Confidence:** medium  
**Source:** gemstone-lights.groovy v0.4.8

## Problem

Hubitat's Events tab shows a **Description** column. When a driver calls `sendEvent(name: ..., value: ...)` without a `descriptionText:` argument, that column is blank — making it impossible to tell at a glance what changed and why. This affects every event type that comes from status-refresh paths (polling, API responses, state-sync helpers), not just user-initiated commands.

## Rule

**Every `sendEvent(...)` call must include a `descriptionText:` argument.**

Use `device.displayName` (not a hardcoded device name). `device.displayName` reflects whatever the user renamed the device in Hubitat — hardcoded names break as soon as anyone renames the device.

## Patterns by Attribute

| Attribute | `descriptionText` template |
|-----------|---------------------------|
| `switch` | `"${device.displayName} turned ${value}"` |
| `level` | `"${device.displayName} level set to ${value}%"` |
| `hue` | `"${device.displayName} hue set to ${value}"` |
| `saturation` | `"${device.displayName} saturation set to ${value}"` |
| `color` | `"${device.displayName} color set to ${value}"` |
| `colorName` | `"${device.displayName} color name set to ${value}"` |
| `colorMode` | `"${device.displayName} color mode → ${value}"` |
| `colorTemperature` | `"${device.displayName} color temperature set to ${value}K"` |
| `effectName` | `"${device.displayName} effect → ${value}"` |
| `effectNumber` | `"${device.displayName} effect index → ${value}"` |
| `lightEffects` | `"${device.displayName} effects catalog refreshed (${count} entries)"` — derive `count` from the map size; never dump the raw JSON |
| `favoriteEffects` | `"${device.displayName} favorite effects updated"` |
| `authStatus` | `"${device.displayName} auth status → ${value}"` |
| anything else | `"${device.displayName} ${attributeName} → ${value}"` |

## Anti-Patterns

```groovy
// BAD — Description column is blank in the Events tab
sendEvent(name: "effectName", value: safeValue, type: "digital")

// BAD — Hardcoded device name breaks when user renames the device
sendEvent(name: "colorMode", value: safeValue, descriptionText: "Gemstone color mode → ${safeValue}", type: "digital")

// BAD — Raw JSON in descriptionText: the UI truncates it and it is not human-readable
sendEvent(name: "lightEffects", value: lightEffectsJson, descriptionText: "${device.displayName} lightEffects: ${lightEffectsJson}", type: "digital")
```

## Good Examples

```groovy
// GOOD — every call site in a status-refresh helper
sendEvent(name: "effectName", value: safeValue, descriptionText: "${device.displayName} effect → ${safeValue}", type: "digital")
sendEvent(name: "colorMode", value: safeValue, descriptionText: "${device.displayName} color mode → ${safeValue}", type: "digital")
sendEvent(name: "colorTemperature", value: kelvin, unit: "K", descriptionText: "${device.displayName} color temperature set to ${kelvin}K", type: "digital")
sendEvent(name: "lightEffects", value: lightEffectsJson, descriptionText: "${device.displayName} effects catalog refreshed (${lightEffectsMap?.size() ?: 0} entries)", type: "digital")
sendEvent(name: "authStatus", value: value, descriptionText: "${device.displayName} auth status → ${value}", type: "digital")
```

## Audit Checklist

When touching a driver, grep for all `sendEvent(` calls and confirm every one has `descriptionText:`:

```
grep -n "sendEvent(" *.groovy | grep -v "descriptionText:"
```

That command should produce **zero output** for a hygiene-clean driver.

## Relationship to Log Hygiene

This skill is about the **Events tab** (Hubitat device UI → Events). The `hubitat-log-hygiene` skill is about the **live log** (Hubitat → Logs). They are separate concerns:
- Events tab: populated by `sendEvent(descriptionText: ...)` — one entry per state change
- Live log: populated by `log.info/debug/warn/error` — noisy stream, purged frequently
