# Skill: Hubitat Event Hygiene

**Confidence:** high
**Source:** Verified across 5 independent driver releases (2026-05-18): touchstone v0.1.25–v0.1.28 (skip-if-match + dedupe patterns), gemstone v0.4.12–v0.4.15 (skip-if-match + dedupe patterns), sunstat v0.1.8–v0.1.10 (skip-if-match pattern). Pattern applied to both LAN (Touchstone) and cloud drivers (Gemstone, SunStat) across command-path and parse-path emit sites.

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

## Coarse Timestamp Events: Throttle, Don't Spam

Some attributes are intentionally **coarse** signals, not high-resolution telemetry. `lastActivity` is the canonical example: users need an at-a-glance "did this driver talk to the device recently?" timestamp, not a new event on every heartbeat ACK, poll callback, or child fan-out.

Pattern:

```groovy
private void touchActivity() {
    Long lastEmittedAt = (state.lastActivityEmittedAt ?: 0L) as Long
    if ((now() - lastEmittedAt) < 60000L) {
        return
    }
    state.lastActivityEmittedAt = now()
    String ts = new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")
    sendEvent(name: "lastActivity", value: ts,
              descriptionText: "${device.displayName} last activity")
}
```

Use this when the attribute is a freshness marker rather than a per-packet metric. The 60-second floor keeps dashboards and Rule Machine useful while avoiding hundreds or thousands of low-information events per day.

## Numeric Telemetry Dedupe

Polling drivers should not emit unchanged numeric attributes on every poll. For temperatures, setpoints, and energy counters, compare the current attribute and incoming value numerically (`BigDecimal`) before calling `sendEvent`, otherwise harmless scale differences like `68` vs `68.0` create false-positive events.

```groovy
private void emitIfChanged(String name, value, String descTxt, String unit = null) {
    def current = device.currentValue(name)
    boolean changed
    if (current instanceof Number || value instanceof Number) {
        changed = safeBigDecimal(current, null) != safeBigDecimal(value, null)
    } else {
        changed = current?.toString() != value?.toString()
    }
    if (!changed) {
        return
    }
    Map evt = [name: name, value: value, descriptionText: descTxt]
    if (unit) {
        evt.unit = unit
    }
    sendEvent(evt)
}
```

Use this in poll/parse paths; keep explicit user-command paths separate if you intentionally want a digital event for the command itself.

## Parse/Push Dedupe vs Command Echoes

Some drivers intentionally emit a **digital** event immediately after a successful outbound write so dashboards and automations reflect the command before the device's next poll/push echo arrives. Keep that command path separate from parse/poll dedupe helpers.

```groovy
private Boolean emitParsedAttributeIfChanged(String name, value, String descTxt, String unit = null) {
    if (!attributeValueChanged(name, value)) {
        return false
    }
    Map evt = [name: name, value: value, descriptionText: descTxt]
    if (unit) {
        evt.unit = unit
    }
    sendEvent(evt)
    return true
}
```

Use this split when the same state can surface twice: once from the command handler (digital echo) and again from the device's refresh/push response. Dedupe only the parse/push copy; do **not** route user-command events through the same skip-if-match helper unless you explicitly want to suppress those digital echoes.

## Relationship to Log Hygiene

This skill is about the **Events tab** (Hubitat device UI → Events). The `hubitat-log-hygiene` skill is about the **live log** (Hubitat → Logs). They are separate concerns:
- Events tab: populated by `sendEvent(descriptionText: ...)` — one entry per state change
- Live log: populated by `log.info/debug/warn/error` — noisy stream, purged frequently

---

## 2026-05-18 Validation: Skip-If-Match + Parse-Path Dedupe Across 5 Releases

**Validated independently across:**
- **Touchstone v0.1.25–v0.1.26**: Command-path idempotency (`on()`, `off()`, other command handlers) preventing audible relay clicks and wire noise
- **Touchstone v0.1.28**: Parse-path dedupe in `applyDps()` + `emitAttribute()` loop, suppressing unchanged attributes from periodic refreshes and device echoes
- **Gemstone v0.4.12–v0.4.13**: Command-path idempotency (`setEffect()`, cloud PUT guards) reducing cloud API quota churn
- **Gemstone v0.4.15**: Parse-path dedupe in `handleRefreshResponse()`, gating unchanged telemetry behind change checks for poll-driven refreshes
- **SunStat v0.1.8–v0.1.10**: Command-path idempotency (`setFloorMinTemp()`, cloud PATCH guards) + state caching for redundant-write detection

**Pattern:** Both LAN devices (Tuya direct) and cloud drivers (HTTP API) benefit from the same dual-path strategy:
1. **Command path** → emit immediately (digital event) without skip-if-match to give user immediate visual feedback
2. **Parse/poll/push path** → check current value before emit to dedupe unchanged state

**Result:** Reduces event spam in history, prevents audible artifacts on LAN fireplaces, saves cloud API quota on HTTP drivers. No behavioral regression; all diffs are emit-count reductions only.
