# Hubitat Changelog Single-Line Format for release.yml

## Pattern

Driver changelogs must be single-line format inside the `Changelog:` section for the `release.yml` workflow to parse release notes correctly.

## The Rule

**Location:** Driver header comment block (top of `.groovy` file)

**Structure:**
```groovy
/**
 *  Driver Name
 *  Author: X
 *  Version: X.Y.Z
 *
 *  Changelog:
 *    X.Y.Z — YYYY-MM-DD — <description on ONE line>
 *    X.Y.Z — YYYY-MM-DD — <description on ONE line>
 */
```

**Details:**
- `Changelog:` label is REQUIRED (parser looks for this line first)
- Indentation: 4 spaces after `*` marker
- Version: directly as `X.Y.Z` (no "Version: " prefix)
- Separator: em-dash or hyphen `[—-]` with spaces (` — ` or ` - `)
- Date: ISO 8601 format `YYYY-MM-DD`
- Description: Free-form text; join clauses with semicolons (`;`) if needed
- **CRITICAL:** Single line only — line wrapping will break the regex parser

## Why: The regex

The `release.yml` workflow (line 136) uses this Python regex:
```python
r'^(\d+\.\d+\.\d+)\s+[—-]\s+(\d{4}-\d{2}-\d{2})\s+[—-]\s+(.*)$'
```

The `$` anchor forces line-end matching. Multi-line entries don't match because the continuation lines don't start with a version number.

## ✅ Correct Examples

### PurpleAir (pattern gold standard)
```groovy
 *  Changelog:
 *    0.4.0 — 2026-05-18 — BUG FIXES: failCount string-multiplication, disabled-poll retry storm, lat/lng degree math, weighted-avg NaN at distance=0; POLISH: refresh-on-save, canonical async error handling, AirQuality capability
 *    0.3.0 — 2026-05-18 — Added pm2_5, temperature, humidity, and confidence attributes; parseJson guard for blank search_coords + empty API bodies
 *    0.1.0 — 2026-05-18 — Initial fork; Trinity audit fixes: AQ&U string mismatch, LRAPA/Woodsmoke case + wrong PM2.5 field, failCount precedence
```

### Honeywell T6 Pro
```groovy
 *  Changelog:
 *    0.5.0 — 2026-05-18 — syncClock UX: replace every-3-hours auto-schedule with daily 4am cron (24x fewer Z-Wave frames; DST handled within 24h); remove manual syncClock command
 *    0.3.0 — 2026-05-18 — Emit thermostatFanState attribute from ThermostatFanStateReport (Pick #1); handle battery-low notification events 10/11
 *    0.1.0 — 2026-05-18 — Initial fork; Trinity audit fixes: add txtEnable preference (BLOCKER), fix currentValue() calls, add unschedule()
```

## ❌ Incorrect Examples (and why they fail)

### Multi-line format
```groovy
 *  Version: 0.5.0 — 2026-05-18 — Removed MQTT support: reverted to local REST polling after
 *                                broker compatibility issues. Cleaner, simpler,
 *                                more reliable.
```
**Problem:** Line continuation breaks regex (lines 2–3 don't start with version number)

### Missing Changelog: label
```groovy
 *  0.5.0 — 2026-05-18 — Removed MQTT support...
 *  0.4.2 — 2026-05-18 — Add clearOverlayMessage() command...
```
**Problem:** Parser looks for "Changelog:" gate first; these lines are skipped

### "Version:" prefix
```groovy
 *  Changelog:
 *    Version: 0.5.0 — 2026-05-18 — Removed MQTT support...
```
**Problem:** Line starts with "Version: " (regex expects version number directly)

### Wrong indentation
```groovy
 *  Changelog:
 *  0.5.0 — 2026-05-18 — Removed MQTT support...
 *  0.4.2 — 2026-05-18 — Add clearOverlayMessage()...
```
**Problem:** 2-space indent vs required 4 spaces (not critical, but inconsistent with team pattern)

## Failure Mode: release.yml Workflow

If your changelog format is wrong, `release.yml` will fail with:
```
No changelog entry found for version X.Y.Z in drivers/<driver-name>/<driver-name>.groovy
```

**Debugging steps:**
1. Check `Changelog:` label exists (case-sensitive)
2. Verify entry starts with version number (no "Version: " prefix)
3. Ensure single-line format (no line wrapping in comment block)
4. Confirm indentation is 4 spaces after `*` marker
5. Check date is `YYYY-MM-DD` (no time or timezone)

## Workflow Trigger Note

The `release.yml` workflow is **path-filtered** to only run when these files change:
- `drivers/**/packageManifest.json` (driver folder manifests)
- `packageManifest.json` (root bundle manifest)

Changing `.groovy` files alone will NOT trigger the workflow. Use `gh workflow run release.yml --ref main` to manually test changelog parsing.

## References

- **release.yml source:** `.github/workflows/release.yml` (lines 109–154 for Python parser)
- **Established rule:** 2026-05-19 decision doc (Mads + Link)
- **Drivers at compliance:** PurpleAir v0.4.0+, Honeywell T6 Pro v0.5.0, Fully Kiosk v0.5.0+, Touchstone v0.1.30+, SunStat v0.1.11+
- **Earlier compliance:** Daikin WiFi v0.1.7+ (already single-line), Gemstone Lights v0.4.16+ (already single-line)
