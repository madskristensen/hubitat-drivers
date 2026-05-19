# Skill: Groovy String × Integer Multiplication Trap

**Confidence:** medium  
**Validated:** 2026-05-18 — PurpleAir AQI v0.4.0 retry/backoff fix (`update_interval` enum preference stayed a String, so `"60" * 5` became `"6060606060"`)  
**Authors:** Tank

---

## Summary

Hubitat enum preferences arrive in driver code as **Strings**, not numbers. In Groovy, `String * Integer` means **string repetition**, not arithmetic multiplication.

That makes retry/backoff code dangerous:

```groovy
String update_interval = "60"
assert update_interval * 5 == "6060606060"   // not 300
```

If a driver does `Integer.valueOf(update_interval * failCount)` it will parse the repeated string into a huge integer instead of the intended minutes. In polling drivers this can silently break retry timing or overflow into nonsense delays.

---

## Safe Pattern

Coerce once at the top of the function, then do all arithmetic with integers:

```groovy
Integer updateIntervalMinutes = settings.update_interval?.toString()?.isNumber() ?
    settings.update_interval.toString().toInteger() : 60
Integer retryDelaySeconds = updateIntervalMinutes * failCount * 60
```

For disabled polling, guard the scheduler separately:

```groovy
if (updateIntervalMinutes != 0) {
    runIn(retryDelaySeconds, "refresh")
}
```

---

## Red Flags to Audit

- `settings.someEnum * 2`
- `Integer.valueOf(settings.someEnum * failCount)`
- `runIn(settings.interval * 60, ...)`
- Any retry or backoff math that touches `enum`/`text` preferences before coercion

If the preference came from Hubitat UI and is not a `number` input, assume it is a String until proven otherwise.

---

## Why This Bites Hubitat Drivers

Hubitat preference values often look numeric in the UI (`"1"`, `"5"`, `"60"`, `"0"`) so it is easy to forget they are still strings in Groovy. The bug usually hides until a rare error path exercises backoff math.

This is especially risky in:
- cloud REST polling drivers
- exponential backoff logic
- schedule interval calculations
- quota-sensitive APIs where a bad retry path can create a storm

---

## References

- `drivers/purpleair-aqi/purpleair-aqi.groovy` v0.4.0 — retry scheduling now coerces `update_interval` once before backoff math
- `.squad/agents/trinity/history.md` — 2026-05-18 PurpleAir audit note on String × Integer repetition in Groovy
