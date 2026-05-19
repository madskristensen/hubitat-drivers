# UPSTREAM PR DRAFT

> **STATUS: De-prioritized.** v0.2.0 forked Mads's namespace and applied broader improvements (emitIfChanged, lastActivity, sentinel guards, logsOff, descriptionText hygiene, UUID in manifest); the original 3-bug fixes remain cherry-pickable as a minimal upstream PR but are no longer the design goal. See directive 2026-05-18T17:31 in `.squad/decisions/inbox/`.

> **For Mads:** If you ever want to submit the 3-bug minimal PR to pfmiller0, copy the title and body below into the GitHub PR at
> https://github.com/pfmiller0/Hubitat/compare
> Fill in the `[bracket]` placeholders with your actual test data. The v0.2.0 improvements are NOT part of this PR — cherry-pick only the 3 original fixes.

---

## Title

```
fix: AQ&U / LRAPA / Woodsmoke conversion + failCount precedence (3 bugs)
```

---

## Body

Hi Peter — thanks for maintaining this driver; it's the only good Hubitat PurpleAir
cloud driver out there.

I found three bugs while testing on [Mads's PNW location] using [neighbor's sensor ID]
over [test period]. All three are small fixes. I've validated the corrected output
against the raw API values manually.

---

### Bug 1 — AQ&U conversion was dead code

**File:** `PurpleAir AQI Virtual Sensor.groovy`
**Function:** `apply_conversion()`
**Approx. original line:** ~310

The preference dropdown emits `"AQ&U"` but `apply_conversion()` checks for
`"AQ and U"`. The strings never match, so selecting AQ&U silently returns raw
`pm2.5` instead of the corrected value. The `AQandU_conversion()` function is
never called.

```groovy
// Before
} else if ( conversion == "AQ and U" ) {
    return AQandU_conversion(PM25)

// After
} else if ( conversion == "AQ&U" ) {
    return AQandU_conversion(PM25)
```

**Test:** Select "AQ&U" conversion, note AQI value. Select "CF=1" (passthrough),
note raw AQI. With the fix, AQ&U should produce a higher corrected value
(`0.778 × PM + 2.65`). Before the fix both modes return the same AQI.

---

### Bug 2 — LRAPA and Woodsmoke request wrong PM2.5 field (case mismatch)

**File:** `PurpleAir AQI Virtual Sensor.groovy`
**Function:** `sensorCheck()`
**Approx. original line:** ~85

The `pm25_count` selection checks for lowercase `"lrapa"` and `"woodsmoke"` against
preference values `"LRAPA"` and `"Woodsmoke"` (mixed case). The condition never
matches, so the code falls through to the `else` branch and requests `pm2.5`
(atmospheric) instead of `pm2.5_cf_1` (channel 1). Both LRAPA and Woodsmoke
formulas are defined against `pm2.5_cf_1` per the PurpleAir map documentation, so
applying them to the wrong field produces incorrect AQI output.

```groovy
// Before
if (conversion == "lrapa" || conversion == "woodsmoke" || conversion == "CF=1") {
    pm25_count="pm2.5_cf_1"

// After
if (conversion == "LRAPA" || conversion == "Woodsmoke" || conversion == "CF=1") {
    pm25_count="pm2.5_cf_1"
```

**Test:** Select "LRAPA" conversion. Enable debug logging. Confirm `particle ct query`
in the log shows `pm2.5_cf_1` (not `pm2.5`). Before the fix it shows `pm2.5`.

---

### Bug 3 — failCount never increments; exponential backoff never triggers

**File:** `PurpleAir AQI Virtual Sensor.groovy`
**Function:** `httpResponse()`
**Approx. original line:** ~103

Operator-precedence bug in the `failCount` increment line:

```groovy
state.failCount = state.failCount?:0 + 1
```

Groovy evaluates `+` before `?:`, so this parses as:

```groovy
state.failCount = state.failCount ?: (0 + 1)
// = state.failCount ?: 1
```

On the first error `state.failCount` is `null` (falsy), so it becomes `1`.
On every subsequent error it's `1` (truthy), so it stays `1`. The backoff delay
`failCount × interval` never grows above 1×. The driver hammers the API at the
normal poll rate during extended outages instead of progressively backing off.

```groovy
// Before
state.failCount = state.failCount?:0 + 1

// After
state.failCount = (state.failCount ?: 0) + 1
```

**Test:** Temporarily set an invalid API key. Observe that `runIn()` delay grows
across successive failures (1×, 2×, 3×, 4× interval). Before the fix the delay
stays constant at 1×.

---

These fixes were validated against [Mads's PNW location] using [neighbor's sensor ID]
over [test period].

Thanks for maintaining this — it's the only good Hubitat PurpleAir cloud driver out there.

— Mads Kristensen
