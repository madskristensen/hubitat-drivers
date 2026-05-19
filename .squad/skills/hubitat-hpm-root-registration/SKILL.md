---
name: "HPM Root Registration"
description: "End-users discover drivers via root packageManifest.json and README, not per-driver folder manifests"
domain: "release-workflow, hpm, documentation"
confidence: "high"
source: "validated in Link's HPM registration session (commits 33a2ec7, d7ab4e4, 273f065, 6d2ed6a)"
---

## Context

Hubitat Package Manager (HPM) discovers drivers from the **root** `packageManifest.json` file only, not from individual driver folder manifests.

A common mistake is to put a driver's packageManifest.json in the driver's own folder (e.g., `drivers/my-driver/packageManifest.json`) and expect HPM to find it. End-users won't see it. HPM looks only at the repo root.

## Patterns

### Root packageManifest.json Structure

The root manifest must list all drivers in a `drivers` array with name, namespace, download URL, and version:

```json
{
  "packageName": "My Hubitat Drivers",
  "author": "Your Name",
  "version": "1.1.0",
  "minimumHEVersion": "2.3.0",
  "dateReleased": "2026-05-19",
  "drivers": [
    {
      "id": "unique-uuid-1",
      "name": "My First Driver",
      "namespace": "myname",
      "location": "https://raw.githubusercontent.com/owner/repo/main/drivers/my-first/my-first.groovy",
      "required": false,
      "version": "1.0.0"
    },
    {
      "id": "unique-uuid-2",
      "name": "My Second Driver",
      "namespace": "myname",
      "location": "https://raw.githubusercontent.com/owner/repo/main/drivers/my-second/my-second.groovy",
      "required": false,
      "version": "2.5.0"
    }
  ]
}
```

### Root README.md Inventory

Include a human-readable table of all drivers so users can discover them before HPM:

```markdown
## Available Drivers

| Driver | Type | Status | HPM |
|--------|------|--------|-----|
| My First Driver | Local LAN | ✅ Stable | [HPM Link](hpm://import?url=https://...) |
| My Second Driver | Cloud API | ✅ Stable | [HPM Link](hpm://import?url=https://...) |
```

This inventory is also the **source of truth** for which drivers are active in the bundle.

### Changelog Format for Release Workflow

Each driver's `.groovy` header must have a `Changelog:` section with single-line entries:

```groovy
/**
 * My Driver
 * ...
 * Changelog:
 *     1.0.0 — 2026-05-19 — Initial release with feature X and Y
 *     0.9.0 — 2026-05-18 — Beta release for testing
 */
```

**Important:** The release.yml workflow uses a regex to parse this:
```regex
r'^(\d+\.\d+\.\d+)\s+[—-]\s+(\d{4}-\d{2}-\d{2})\s+[—-]\s+(.*)$'
```

This regex requires:
- Version number at line start (no "Version: " prefix)
- Date in YYYY-MM-DD format
- Single-line entries only (no line wrapping)
- Either em-dash (—) or hyphen (-) as separator

## Examples

**Applied in hubitat-drivers repo** (commit 6d2ed6a):
- Root packageManifest.json lists 7 drivers (Honeywell T6 Pro, Fully Kiosk, PurpleAir AQI, Daikin, Gemstone, SunStat, Touchstone)
- Root README.md has inventory table + HPM URLs
- All 7 drivers have single-line changelog format
- release.yml workflow auto-creates GitHub Release tags on push to main

See:
- `packageManifest.json` (root)
- `README.md` (root, lines 20–60)
- `drivers/purpleair-aqi/purpleair-aqi.groovy` (header, Changelog section)

## Checklist for New Driver Release

Before tagging a new driver release:

- [ ] Driver entry added to root `packageManifest.json` with UUID, namespace, GitHub raw URL, version
- [ ] Root `packageManifest.json` version bumped (increment patch or minor)
- [ ] Driver row added to root `README.md` inventory table
- [ ] Driver changelog in `.groovy` header uses single-line format (version — date — description)
- [ ] Test: `gh workflow run release.yml --ref main` succeeds
- [ ] Test: GitHub Release page shows new tag(s)
- [ ] Test: HPM link works (manual import test in Hubitat)

## Anti-Patterns

❌ **Driver-folder manifest:**
```
drivers/my-driver/
  ├── my-driver.groovy
  └── packageManifest.json  ← Won't be discovered by HPM
```

❌ **Multi-line changelog (breaks release.yml regex):**
```groovy
* 1.0.0 — 2026-05-19 — Initial release
*   with lots of features
*   described across multiple lines  ← Breaks regex
```

❌ **Missing root README inventory:**
Users can't find drivers without the inventory table.

## Why This Matters

- **End-user discovery:** HPM needs the root manifest to list available drivers
- **Automation:** release.yml can only parse single-line changelog entries
- **Bundle semantics:** HPM treats the root bundle as the unit of release (not individual drivers)
- **Maintenance burden:** Users get confused if they can't find your driver in HPM

Every driver release must update root packageManifest.json and README.md as a prerequisite. This is mandatory, not optional.
