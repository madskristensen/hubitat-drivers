# PurpleAir AQI Virtual Sensor — Local-test Fork

> ⚠️ **TEMPORARY FORK — PR-BOUND**
> This is a local test copy of [pfmiller0's PurpleAir AQI Virtual Sensor](https://github.com/pfmiller0/Hubitat/blob/main/PurpleAir%20AQI%20Virtual%20Sensor.groovy)
> with 3 bug fixes applied for validation before submitting upstream as a PR.
> **Once the PR is accepted and merged, delete this driver and switch back to the upstream version.**

---

## What This Is

A Hubitat virtual sensor that reads AQI data from the PurpleAir cloud API
(`api.purpleair.com/v1/sensors`) using your API key. Supports geolocation-based
multi-sensor averaging (uses nearby sensors automatically) or a specific sensor
index (e.g., a neighbor's sensor). Implements US EPA Barkjohn 2021 AQI correction
for wildfire smoke, plus Woodsmoke, AQ&U, LRAPA, and CF=1 correction algorithms.

This fork exists solely to test 3 bugs found by Trinity code audit (2026-05-18)
before pushing the fixes upstream to pfmiller0.

---

## Install

### Via Hubitat Package Manager (HPM)

1. In HPM, choose **Install** → **From URL**
2. Paste: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/purpleair-aqi/packageManifest.json`

### Manual

1. In Hubitat, go to **Drivers Code** → **New Driver**
2. Paste the contents of `purpleair-aqi.groovy`
3. Save

### Setup

1. Get a free PurpleAir API key at [develop.purpleair.com](https://develop.purpleair.com/) (email contact@purpleair.com if the portal isn't available)
2. Create a new **Virtual Device** using the **PurpleAir AQI Virtual Sensor** driver
3. Enter your API key in preferences
4. Choose a sensor mode:
   - **Search for devices** (default): uses hub GPS coordinates to find and average nearby sensors
   - **Specific sensor**: uncheck "Search for devices" and enter a sensor index from the map URL (`?select=INDEX` at map.purpleair.com)
5. Optionally choose a conversion algorithm (US EPA recommended for wildfire smoke accuracy)
6. Save preferences — the driver will start polling on the selected interval

---

## What's Fixed (vs. upstream v1.3.2)

### Fix 1 — AQ&U conversion was dead code (BLOCKER)

**Location:** `apply_conversion()` function

The code checked for `"AQ and U"` but the preference dropdown emits `"AQ&U"`. The
string never matched, so selecting AQ&U silently returned raw PM2.5 instead of the
corrected value (`0.778 × PM + 2.65`).

```groovy
// Before (broken):
} else if ( conversion == "AQ and U" ) {

// After (fixed):
} else if ( conversion == "AQ&U" ) {
```

### Fix 2 — LRAPA and Woodsmoke requested wrong PM2.5 field (BLOCKER)

**Location:** `sensorCheck()` function

The code checked for lowercase `"lrapa"` and `"woodsmoke"` against preference
values `"LRAPA"` and `"Woodsmoke"` (mixed case). The check never matched, so
instead of requesting `pm2.5_cf_1` (the channel-1 field required by both
conversion formulas), the API was queried for `pm2.5` (atmospheric). The
conversion math was then applied to the wrong input, producing incorrect AQI values.

```groovy
// Before (broken):
if (conversion == "lrapa" || conversion == "woodsmoke" || conversion == "CF=1") {

// After (fixed):
if (conversion == "LRAPA" || conversion == "Woodsmoke" || conversion == "CF=1") {
```

### Fix 3 — failCount never incremented; exponential backoff never triggered (MAJOR)

**Location:** `httpResponse()` function, error-handling block

Operator-precedence bug: `state.failCount?:0 + 1` evaluates as
`state.failCount ?: (0 + 1)` = `state.failCount ?: 1`. Because `failCount`
starts at `null` (falsy), it became `1` on first error — and stayed `1` on every
subsequent error. The exponential backoff (`failCount × interval`) never grew
beyond 1×, so the driver hammered PurpleAir at the normal poll rate during outages
instead of progressively backing off.

```groovy
// Before (broken):
state.failCount = state.failCount?:0 + 1

// After (fixed):
state.failCount = (state.failCount ?: 0) + 1
```

---

## Upstream Status — PR-Bound

**Mads will submit these fixes as a PR to [pfmiller0/Hubitat](https://github.com/pfmiller0/Hubitat)
once they are validated locally.** pfmiller0 last committed June 2025 and is an active maintainer.

See `UPSTREAM-PR-DRAFT.md` in this directory for the draft PR description.

This fork should be considered a staging area, not a permanent home. Once the PR
is merged upstream:

1. Delete `drivers/purpleair-aqi/` from this repo
2. Re-add the driver via `importUrl` pointing to pfmiller0's upstream
3. Close any HPM entries referencing this fork

---

## Attribution

**Original driver:** [PurpleAir AQI Virtual Sensor](https://github.com/pfmiller0/Hubitat/blob/main/PurpleAir%20AQI%20Virtual%20Sensor.groovy)
by Peter Miller (`pfmiller0`), version 1.3.2. No explicit license declared in the
upstream repo; used here under fair-use assumptions for bug-fix contribution purposes.

**This fork** is a minimal patch set (3 lines changed) created by Mads Kristensen
on 2026-05-18 for local validation prior to an upstream PR. It is not intended as
a competing or permanent fork.

---

## Changelog

| Version | Date | Notes |
|---------|------|-------|
| 0.1.0 | 2026-05-18 | Initial local-test fork; Trinity audit fixes #1 (AQ&U string), #2 (LRAPA/Woodsmoke case + field), #3 (failCount precedence) |
