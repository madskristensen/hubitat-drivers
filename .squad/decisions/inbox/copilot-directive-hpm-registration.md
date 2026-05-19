### 2026-05-18T21:05-07:00: User directive — HPM registration is mandatory
**By:** Mads Kristensen (via Copilot)
**What:** Every new driver — and any driver bumping a notable version (e.g., feature add, scope change) — MUST be registered in the root `README.md` AND the root `packageManifest.json` of this repo before being considered "shipped." HPM (Hubitat Package Manager) discovers drivers via the root manifest; an unregistered driver is invisible to HPM users no matter what packageManifest.json sits in the driver folder.

**Per-driver checklist** when shipping/updating:
1. Driver `.groovy` file — version + changelog entry (single-line format for release.yml regex)
2. Driver folder `packageManifest.json` — version bump
3. Driver folder `README.md` — changelog row
4. **Root `README.md`** — driver listed in the inventory table with current version + short description + install link
5. **Root `packageManifest.json`** — driver bundle entry updated (location, version, name)
6. Commit + push (release workflow tags and publishes)

**Why:** User request — without root-level registration, drivers ship to GitHub but never reach end-users through HPM. Captured as a permanent team rule.

**Immediate follow-up:** Verify T6 Pro v0.3.0, Fully Kiosk Browser v0.5.0, and PurpleAir AQI v0.3.0 are all present and current in both root files. Queued for after Trinity's quality audit completes (so any quality fixes get bundled in the same registration pass).
