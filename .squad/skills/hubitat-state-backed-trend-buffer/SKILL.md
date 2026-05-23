---
name: "hubitat-state-backed-trend-buffer"
description: "Pattern for keeping a rolling temperature-trend sample buffer in Hubitat state, computing slope over a configurable window, and classifying rising/falling/steady/unknown."
domain: "groovy"
confidence: "high"
source: "earned — Climate Advisor v0.1.0"
---

## Context

Use when a Hubitat app or driver needs to track a time-series of sensor values (temperature, humidity, etc.) in state, compute a slope over a rolling window, and expose a human-readable trend classification. Works entirely within Hubitat's sandbox (no `System.*`, no reflection).

## Pattern

### Sample buffer append + trim

```groovy
def outdoorTempHandler(evt) {
    try {
        BigDecimal t = evt.value as BigDecimal
        Long ts = now()
        List samples = (state.outdoorSamples ?: []) + [[now: ts, t: t]]
        Long cutoff = ts - (((settings.trendWindowMinutes ?: 30) as Integer) + 5) * 60 * 1000L
        state.outdoorSamples = samples.findAll { it.now >= cutoff }
    } catch (Exception e) {
        log.warn "outdoorTempHandler sample error: ${e.message}"
    }
    // Debounce: coalesces rapid events (e.g., outdoor temp + contact event firing together)
    runIn(1, "evaluateAll", [overwrite: true])
}
```

> **Critical:** Call `runIn(1, "evaluateAll", [overwrite: true])` rather than `evaluateAll()` directly.
> The debounce ensures that when multiple events arrive within the same second (outdoor temp change + contact sensor + thermostat setpoint), only one evaluation pass runs instead of N simultaneous passes.
> Do **not** call `evaluateAll()` synchronously from an event handler — it bypasses the coalescing and can cause redundant state writes and child-device event storms.

Key points:
- Each sample: `[now: epochMs, t: BigDecimal]`
- Trim window = `(trendWindowMinutes + 5)` minutes — the +5 buffer ensures oldest sample inside the evaluation window survives one late-arriving event.
- Re-cast on read: `(state.outdoorSamples ?: []) as List` — Hubitat's JSON round-trip can silently change type.
- Accumulate with `+` rather than mutating in place to avoid Hubitat state-serialization quirks.

### Slope computation

```groovy
Map computeTrend(List samples, Integer windowMinutes, BigDecimal risingThreshold, BigDecimal fallingThreshold) {
    Long newestTs = now()
    Long cutoff = newestTs - windowMinutes * 60 * 1000L
    List window = (samples ?: []).findAll { it.now >= cutoff }.sort { it.now }

    if (window.size() < 2) { return [trend: "unknown", slope10min: null] }

    def oldest = window.first()
    def newest = window.last()
    BigDecimal spanMinutes = (newest.now - oldest.now) / 60000G
    if (spanMinutes < 5G) { return [trend: "unknown", slope10min: null] }

    BigDecimal slopePerMinute = ((newest.t as BigDecimal) - (oldest.t as BigDecimal)) / spanMinutes
    BigDecimal slope10min = slopePerMinute * 10G

    String trend = slope10min > risingThreshold ? "rising" :
                   slope10min < fallingThreshold ? "falling" :
                   "steady"
    return [trend: trend, slope10min: slope10min]
}
```

Returns: `[trend: "rising"|"falling"|"steady"|"unknown", slope10min: BigDecimal|null]`

### Edge cases

| Condition | Return |
|---|---|
| Fewer than 2 samples in window | `unknown`, null |
| Sample span < 5 minutes | `unknown`, null |
| Normal | classified trend, slope10min in °F/10min |

Predictive logic **must gate on `trend != "unknown"`** before using the result.

### Per-key sub-buffers (multi-zone)

For per-zone indoor trend, maintain `state.indoorSamples` as a `Map` keyed by zone ID:

```groovy
private void appendIndoorSample(String zoneId, BigDecimal avgTemp) {
    Long ts = now()
    Map indoorSamples = (state.indoorSamples ?: [:]) as Map
    List zoneSamples = (indoorSamples[zoneId] ?: []) as List
    zoneSamples = zoneSamples + [[now: ts, t: avgTemp]]
    Long cutoff = ts - (((settings.trendWindowMinutes ?: 30) as Integer) + 5) * 60 * 1000L
    indoorSamples[zoneId] = zoneSamples.findAll { it.now >= cutoff }
    state.indoorSamples = indoorSamples
}
```

Always re-assign `state.indoorSamples` at the end — never rely on mutating a Map reference in Hubitat state.

### Attribute declarations

```groovy
attribute "outdoorTrend",          "ENUM", ["rising", "falling", "steady", "unknown"]
attribute "outdoorTempSlope10min", "NUMBER"
attribute "indoorTrend",           "ENUM", ["rising", "falling", "steady", "unknown"]
attribute "indoorTempSlope10min",  "NUMBER"
```

## Anti-patterns

- Using `System.currentTimeMillis()` — use `now()` instead.
- Mutating the list in-place (`state.outdoorSamples.add(...)`) — Hubitat may not dirty-track nested mutations; always reassign.
- Missing `as BigDecimal` coercion on temperature values — Integer division will silently truncate slope calculation.
- Forgetting the `+ 5` buffer in the trim cutoff — the evaluation window and the trim window must not be identical or the oldest in-window sample will be trimmed before being read.
- Not guarding on `trend == "unknown"` before acting on slope in predictive logic.
- Calling `evaluateAll()` synchronously from an event handler — always use `runIn(1, "evaluateAll", [overwrite: true])` to coalesce rapid multi-source events.
- Calling `appendSample()` from inside the evaluation pass — only append samples from the relevant sensor's dedicated event handler, not from the orchestrator. Appending from the evaluator causes state bloat and slope deflation (identical readings extend the apparent time span, artificially flattening the computed slope).

## Examples

- `apps/climate-advisor/climate-advisor-app.groovy` — `outdoorTempHandler`, `computeTrend`, `appendIndoorSample`, `indoorTrendResult`
