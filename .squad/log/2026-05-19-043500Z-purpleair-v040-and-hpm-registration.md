# Session Log — PurpleAir v0.4.0 Shipped + HPM Registration Complete

**Date:** 2026-05-19  
**Session ID:** 2026-05-19-043500Z  
**System:** Scribe  
**Outcome:** ✅ SUCCESS

## Executive Summary

Three drivers now discoverable via HPM: Honeywell T6 Pro v0.5.0, Fully Kiosk v0.5.0, PurpleAir AQI v0.4.0. PurpleAir shipped with all 5 production bugs fixed (Trinity audit picks), polish features (refresh-on-save, canonical async errors, AirQuality cap, 60s throttling), and new attributes. Changelog single-line format rule established and implemented across all drivers (required by release.yml regex).

## Drivers Shipped

### PurpleAir AQI v0.4.0 (Tank #22)
- **Trinity Bugs Fixed:** String-math retry backoff, disabled-poll retry guard, distance2degrees() pole clamp, zero-distance protection, divide-by-zero guards
- **New Features:** AirQuality capability + airQualityIndex attribute, runEvery* scheduling, hub temperature-scale conversion, canonical async HTTP error handling, refresh-on-save
- **Polish:** Cleaner sites output, stable AQI units, 60-second lastActivity throttling
- **Commit:** 2d62b05

### Fully Kiosk Browser v0.5.0 (Tank #20)
- **Change:** MQTT-to-REST wording clarification (removed outdated MQTT references)
- **Changelog:** Flattened to single-line format (release.yml regex requirement)
- **Commit:** 61644e4

### Honeywell T6 Pro Thermostat v0.5.0 (Link)
- **Status:** Already complete, registered in root manifest
- **Commit:** Part of HPM registration pass

## HPM Registration Work (Link)

**Drivers Now Root-Registered:**
1. Honeywell T6 Pro Thermostat v0.5.0 — Z-Wave climate control
2. Fully Kiosk Browser v0.5.0 — tablet/mobile remote control (REST API)
3. PurpleAir AQI Virtual Sensor v0.4.0 — air quality sensor (cloud API)

**Bundle Version:** 1.1.0 (was 1.0.5)

**Files Modified:**
- Root `packageManifest.json` — added 3 new drivers to bundle
- Root `README.md` — added 3 drivers to inventory table

**Related Commits:**
- 33a2ec7 (Honeywell T6 Pro registration)
- d7ab4e4 (Fully Kiosk registration)
- 273f065 (PurpleAir registration)
- 6d2ed6a (Changelog format rule + bundle v1.1.0)

## Changelog Single-Line Format Rule

**Status:** ESTABLISHED + IMPLEMENTED

**Rationale:** `.github/workflows/release.yml` (line 136) uses Python regex to extract version + date + description from driver changelogs. Multi-line format breaks the regex anchor pattern.

**Rule Details:**
- Header: `Changelog:` label REQUIRED
- Indentation: 4 spaces after `*` marker
- Format: `X.Y.Z — YYYY-MM-DD — <description>` (all on one line)
- Regex pattern: `^(\d+\.\d+\.\d+)\s+[—-]\s+(\d{4}-\d{2}-\d{2})\s+[—-]\s+(.*)$`

**Applied to:**
- PurpleAir v0.3.0, v0.4.0
- Fully Kiosk v0.5.0
- Honeywell T6 Pro (reference)
- Touchstone Fireplace (reference)

## User Directives Recorded

1. **User Identity:** Mads Kristensen (never Brady)
2. **HPM Mandate:** Every new driver + notable version bump MUST be registered in root packageManifest + README before shipping

## Quality Audits Completed

- **Cypher #10:** Fully Kiosk v0.6+ scope audit (no feature creep)
- **Cypher #11:** PurpleAir v0.3+ async guards + averaging logic audit (production-ready)
- **Trinity #6:** PurpleAir post-v0.3.0 bug audit (5 bugs identified for v0.4.0)

## Orchestration Log Files Created

- `.squad/orchestration-log/2026-05-19-043500Z-cypher-10.md`
- `.squad/orchestration-log/2026-05-19-043500Z-cypher-11.md`
- `.squad/orchestration-log/2026-05-19-043500Z-tank-20.md`
- `.squad/orchestration-log/2026-05-19-043500Z-tank-21.md`
- `.squad/orchestration-log/2026-05-19-043500Z-trinity-6.md`
- `.squad/orchestration-log/2026-05-19-043500Z-tank-22.md`
- `.squad/orchestration-log/2026-05-19-043500Z-link.md`

## Decisions Merged

5 inbox files merged into `.squad/decisions.md` (prepended in reverse-chronological order):
1. Link HPM registration + changelog format rule
2. Tank PurpleAir v0.4.0 shipped
3. Tank PurpleAir v0.3.0 shipped
4. User directive: HPM registration mandatory
5. User directive: User's name is Mads

## Next Steps

- All agent spawns completed and logged
- All drivers shipped and HPM-registered
- Root bundle version (1.1.0) published
- Release workflow now able to parse changelog entries from all drivers
