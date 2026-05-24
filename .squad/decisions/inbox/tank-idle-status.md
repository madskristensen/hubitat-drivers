# Decision: Climate Advisor v0.3.4 — contextual idle status for dashboard

**Date:** 2026-05-23  
**Author:** Tank  
**Version:** 0.3.4

## Problem

When no advisory messages were active, the `latestMessage` attribute on the child device showed:

```
All clear — no climate issues detected
```

And `houseStatus` showed `"House — all clear"`. These are informationally empty on a SharpTools kitchen tablet tile where the device is always visible — there's no value in staring at "all clear" when you could see actual ambient conditions.

## Solution: `buildIdleStatus()`

New private method in `climate-advisor-app.groovy`, called from `evaluateAll()` only when `allMessages` is empty (no active advisory candidates). Produces a single rich line for the `latestMessage` attribute.

**Format:** `{emoji} {condition} · {temp}°F · AQI {n} ({category}) · House comfortable`

**Examples:**
- `☀️ Sunny · 72°F · AQI 38 (good) · House comfortable`
- `🌧️ Rain · 58°F · AQI 42 · House comfortable`  
- `☁️ Cloudy · 65°F · AQI 51 (moderate) · House comfortable`
- `🌙 Clear · 48°F · AQI 30 · House comfortable`
- `House comfortable` (all sensors missing — absolute fallback)

## Design decisions

**Separator:** ` · ` (U+00B7 MIDDLE DOT with spaces). Clean on both SharpTools and Hubitat Dashboard without visual noise of pipes or dashes.

**Weather emoji mapping:** keyed on `contains()` matches of the weather attribute string (case-insensitive). Priority order: rain override (from `rainDetected`) → snow → storm → fog → partly cloudy → cloudy → clear/sunny → unknown (raw string + generic emoji). Rain is handled via the existing `rainDetected` boolean rather than re-reading the weather attribute, keeping it consistent with the evaluator logic.

**Day/night detection:** `isNighttime()` reads `location.sunrise` and `location.sunset` (Hubitat `Date` objects). "Clear/sunny" conditions emit 🌙 after sunset, ☀️ during day. Fails gracefully to `false` (daytime) if location data is unavailable.

**Temperature display:** Integer (`.setScale(0, BigDecimal.ROUND_HALF_UP).toInteger()`). Fractional degrees add noise on a dashboard tile.

**AQI categories (EPA standard):**
- 0–50: good
- 51–100: moderate  
- 101–150: sensitive groups
- 151–200: unhealthy
- 201–300: very unhealthy
- 301+: hazardous

**`houseStatus` attribute:** Updated idle case from `"House — all clear"` → `"House comfortable"` for consistency with the new idle line suffix.

**No new preference:** Feature ships on by default. Mads can request a toggle later if needed.

**Null safety:** Every segment is independently guarded. `buildIdleStatus(null, false, null)` returns `"House comfortable"` without exceptions — verified by reading the logic path.

## Files changed

- `apps/climate-advisor/climate-advisor-app.groovy` — `buildIdleStatus`, `isNighttime`, `aqiCategory` methods; `evaluateAll` idle branches updated; version 0.3.4
- `drivers/climate-advisor/climate-advisor-device.groovy` — version 0.3.4, changelog
- `apps/climate-advisor/packageManifest.json` — version 0.3.4
- `packageManifest.json` (root) — Climate Advisor app + driver entries 0.3.4
- `apps/climate-advisor/README.md` — "Idle dashboard line" section added
