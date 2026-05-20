# Pattern: Hubitat Groovy Driver Performance Checklist

**Date:** 2026-05-19  
**Source:** PST02 v1.1.0 → v1.2.0 audit  
**Author:** Tank

---

## Reusable Patterns Discovered

### 1. Implicit-global trap in `def` methods

In Hubitat Groovy drivers, any variable assigned **without** `def` or a type declaration inside a `def` method body becomes a script-level `Binding` entry rather than a stack-local variable. This means:

- It persists between method calls (stale value risk)
- It requires a map lookup on every access (slower than stack locals)
- It can mask bugs (resync check using stale `resync` from prior wakeup)

**Pattern to apply:** Always declare with a type or `def`:
```groovy
// BAD — implicit global
resync = state.pendingResync
value  = resolveConfigParam5()

// GOOD — stack locals
boolean resync = state.pendingResync as boolean
Integer value  = resolveConfigParam5()
```

**Hotspots to audit:** `deviceSync()` and any switch-heavy event handler where case bodies reuse a variable name across cases without re-declaring.

---

### 2. Cache `isPst02BVariant()` (or any settings/getDataValue call) in resolve helpers

Helper functions that are called multiple times per wakeup should cache any settings/`getDataValue` reads in a local variable at the top:

```groovy
Integer resolveConfigParam5() {
    boolean isB = isPst02BVariant()   // ONE settings/getDataValue read
    if (parameterMode == "raw") return (para5Raw != null ? para5Raw.toInteger() : (isB ? 61 : 56))
    Integer value = 0
    if (!isB) value = setBit(value, 0x04, ...)
    ...
}
```

Without caching, `isPst02BVariant()` (which may call `getDataValue("deviceId")`) is invoked twice per function call and N×3 times per `deviceSync()` wakeup.

---

### 3. Redundant `configurationGet` in resync info-read block

When a driver has a "diff-check" block that sends `configurationGet(N)` conditionally on `resync || state.N != value`, the `resync ||` arm already covers the resync case. A separate "info-only reads on resync" block that also sends `configurationGet(N)` for the same parameter N is **always** a duplicate.

On battery devices, every Z-Wave roundtrip drains the battery. Audit resync info-read blocks against the diff-check block above them.

---

### 4. Inline `sendEvent` for simple events

For single-attribute events with no complex logic, prefer:
```groovy
sendEvent(name: "battery", value: batteryLevel, unit: "%", descriptionText: "battery is ${batteryLevel}%")
```
over:
```groovy
def map = [:]
map.name = "battery"
map.value = batteryLevel
...
sendEvent(map)
```

The inline form skips one `HashMap` allocation and is easier to read. Reserve the local `Map map` pattern for events that require conditional branching to set the value or name.

---

### 5. Typed return types reduce dynamic dispatch overhead

Declare concrete return types on frequently-called helpers:
- `Integer setBit(Integer, Integer, Boolean)` — already typed args; add `Integer` return
- `boolean isPst02BVariant()` — short-circuits on preference strings; `boolean` return avoids boxing
- `Integer resolveConfigParam5/6/7()` — always returns a byte-range int; `Integer` return

The Hubitat sandbox JVM benefits from type hints on hot paths even in dynamic Groovy because the JIT can optimize known-type call sites.

---

### 6. `${cmd.toString()}` → `${cmd}` in GStrings

In Groovy GStrings, `${obj}` already calls `obj.toString()` implicitly. `${cmd.toString()}` is redundant and creates an extra method call. Use `${cmd}` throughout.

---

## When to Apply This Checklist

Apply to any new Hubitat driver or driver audit:
1. Grep for bare assignments (`^    [a-z]+ =` without `def`/type) inside `def` methods
2. Count calls to any function that reads `settings.*` or `getDataValue()`; cache if called >1× in a hot path
3. Diff-check resync blocks against info-only resync blocks for duplicate `configurationGet` calls
4. Review `def map = [:]` patterns; inline if the event has no conditional branching
5. Verify all `log.trace`/`log.debug` string args use `${cmd}` not `${cmd.toString()}`
