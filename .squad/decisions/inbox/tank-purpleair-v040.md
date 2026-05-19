## 2026-05-18 — PurpleAir v0.4.0 shipped

**Author:** Tank
**Requested by:** Mads Kristensen
**Status:** completed

### Scope shipped
- Landed all four Trinity production bugs in `drivers/purpleair-aqi/`: retry/backoff String-math fix, disabled-poll retry guard, corrected `distance2degrees()` lat/lng math with pole clamp, and zero-distance protection in `sensorAverageWeighted()`.
- Added polish requested for v0.4.0: canonical async HTTP error handling, refresh-on-save, `AirQuality` capability plus `airQualityIndex`, `runEvery*` scheduling, hub temperature-scale conversion, cleaner `sites` output, stable `AQI` units, and 60-second `lastActivity` throttling.
- Flattened the driver-header changelog to one line per version so `.github/workflows/release.yml` can extract release notes from PurpleAir again.
- Bumped `packageManifest.json`, README changelog, and driver header to `0.4.0`.

### Architectural choices
- **Retry math now coerces once, then reuses the integer.** This avoids repeated String parsing and prevents Groovy's `String * Integer` repetition trap from reappearing elsewhere in the backoff path.
- **Disabled polling stays disabled even on failures.** Error callbacks still log useful diagnostics, but `update_interval == "0"` never schedules a retry.
- **Exact sensor-coordinate matches prefer the zero-distance set.** When a search point lands on a sensor, averaging only that co-located subset is more correct than letting divide-by-zero poison the weighted average.
