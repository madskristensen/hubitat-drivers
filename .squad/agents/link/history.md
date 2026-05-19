# Documentation Specialist — Link

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers
- **Stack:** Markdown, JSON (HPM manifests), community documentation patterns
- **Created:** 2026-05-16

---

## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.

## 2026-05-18 — Hubitat Write-Only Property Gotcha + HubAction Constructor Table

### Key Lessons from Daikin v0.1.1 hotfix

1. **Groovy JavaBean Naming + Scheduler Method Shadowing**  
   Custom command setX(x) creates a write-only property x on the driver object. If the code also calls the platform's x() scheduler method (e.g., schedule(cron, method)), Groovy's dynamic dispatch resolves the name as the write-only property instead of the method → runtime error ("Cannot read write-only property"). Workaround: use unEvery* idiomatic methods instead of calling schedule by name. Affected drivers: any Thermostat capability driver that calls schedule(cron, method) in addition to providing the setSchedule() stub.

2. **HubAction Constructor Overloads**  
   Valid forms for LAN HTTP: HubAction(String), HubAction(String, Protocol), HubAction(String, Protocol, String dni), HubAction(String, Protocol, String dni, Map options), HubAction(Map), HubAction(Map, Protocol) ← **preferred for GET**. Invalid form: HubAction(Map, Protocol, Map) does NOT exist. Callback must be inside the params Map when using 2-arg form.

3. **Test on First Install Before Shipping**  
   Both bugs were immediately visible on first Save Preferences after install. Smoke-test drivers on hub before tagging v1.0 releases.

## 2026-05-18 — Daikin v0.1.4 Roadmap Complete

**Daikin WiFi driver v0.1.4 shipped; v0.1.0+ roadmap CLOSED.** Tank-6 bundled final three capability items (commit 1dd21fe):
1. **Econo/Powerful mode** — setSpecialMode + specialMode ENUM, polled every fast-refresh
2. **get_model_info cache** — Called in initialize(); caches name, firmware, humidity/swing flags for diagnostics
3. **Event hygiene audit** — All five checks passed (no anti-patterns detected)

No documentation updates required for v0.1.4 (feature-level only; README and manifests already cover capabilities). Hardware verification pending on Mads's BRP069B unit.

## 2026-05-19T04:30:00-07:00 — HPM Registration + Changelog Format Fix (Release Blocker Resolution)

**Collaborators:** Link (docs + registration), Mads (direction), Tank/Scribe (prior drivers)

### Scope

Three drivers shipped this session but had release blockers preventing GitHub Release tags:
1. **release.yml workflow blocker:** Fully Kiosk v0.5.0's changelog entries had multi-line format that didn't match the Python regex in `release.yml` (line 136)
2. **HPM discovery blocker:** Drivers were not registered in root `packageManifest.json` or root `README.md` — HPM discovers drivers from root files only, not per-driver folders

Both blockers resolved in coordinated commits.

### Changes Made

#### PART A: Flattened Changelog Entries

**Drivers audited:** All 8 drivers in `drivers/` folder

**Multi-line issues found and fixed:**
- **Fully Kiosk (fully-kiosk.groovy)** — Versions 0.5.0, 0.4.2, 0.4.1, 0.3.0, 0.2.0, 0.1.0 had multi-line format WITHOUT `Changelog:` label and with "Version: " prefix. **Fixed:** Added `Changelog:` label, removed "Version: " prefix, used PurpleAir indentation pattern (4 spaces, single-line entries)
- **Honeywell T6 Pro (honeywell-t6-pro.groovy)** — Versions 0.5.0, 0.4.0, 0.3.0, 0.2.0, 0.1.0 had multi-line format but ALREADY had `Changelog:` label and correct prefix. **Fixed:** Flattened to single-line; preserved existing indentation

**Already single-line (no changes):**
- PurpleAir AQI — All versions v0.4.0 down to v0.1.0 already single-line
- Daikin WiFi — All versions v0.1.7 down to v0.1.0 already single-line
- Gemstone Lights — All versions v0.4.16 down to v0.1.0 already single-line
- SunStat Parent — All versions v0.1.11 down to v0.1.0 already single-line
- SunStat Child — All versions v0.1.11 down to v0.1.0 already single-line
- Touchstone Fireplace — All versions v0.1.30 down to v0.1.0 already single-line

#### PART B: HPM Registration

**Drivers registered in root files (with versions at ship time):**
1. Honeywell T6 Pro Thermostat v0.5.0 — ID: b4c17e3a-9f82-4d56-a031-7e8c3b0f2d91
2. Fully Kiosk Browser v0.5.0 — ID: ead8e688-5b88-48b7-97f9-16b0d921bd2f
3. PurpleAir AQI Virtual Sensor v0.4.0 — ID: 71f1eb74-4302-4a96-9c4d-20391936bf94

**Root README.md changes:**
- Status line: "4 production-ready drivers" → "7 production-ready drivers"
- Network count: "🌐 2 Local LAN" → "🌐 4 Local LAN"; "☁️ 2 Cloud API" → "☁️ 3 Cloud API"
- Added Climate Control row: Honeywell T6 Pro (Z-Wave thermostat)
- Added new section: "📱 Mobile & Tablet" → Fully Kiosk Browser
- Added new section: "🌍 Air Quality" → PurpleAir AQI Virtual Sensor
- Updated network compatibility table with 3 new drivers
- Updated HPM URLs list to include all 7 drivers

**Root packageManifest.json changes:**
- Added 3 driver entries to `drivers` array with UUIDs, namespaces ("mads"), location URLs, and versions
- Bumped root bundle version: 1.0.5 → 1.1.0 (signals HPM bundle refresh)
- dateReleased set to 2026-05-18

### Workflow Verification

**release.yml workflow trigger:** Configured to run only on changes to packageManifest.json files (not .groovy files alone)

**First run (commit 33a2ec7):** FAILED — "No changelog entry found for version 0.5.0 in drivers/fully-kiosk/fully-kiosk.groovy"
- Root cause: Fully Kiosk lacked `Changelog:` label and had "Version: " prefix (not PurpleAir pattern)

**Subsequent .groovy fixes (commits d7ab4e4, 273f065):** NOT auto-triggered because workflow watches packageManifest.json, not .groovy files

**Manual workflow_dispatch (run 26076293354):** SUCCESS
- All 4 tags created:
  - bundle-v1.1.0 (2026-05-19 4:30:54 AM)
  - fully-kiosk-v0.5.0 (2026-05-19 4:30:55 AM)
  - honeywell-t6-pro-v0.5.0 (2026-05-19 4:30:57 AM)
  - purpleair-aqi-v0.4.0 (2026-05-19 4:30:58 AM)

### Key Learnings

1. **release.yml Python regex pattern** (line 136): `r'^(\d+\.\d+\.\d+)\s+[—-]\s+(\d{4}-\d{2}-\d{2})\s+[—-]\s+(.*)$'`
   - Line must start with VERSION NUMBER (not "Version: X.Y.Z")
   - Requires `Changelog:` label before version entries
   - Single-line format ONLY; multi-line entries break parsing
   - Uses em-dash OR hyphen (—-) between groups

2. **HPM bundle discovery:** Drivers are discovered from **root** `packageManifest.json` ONLY, not from per-driver folders
   - End-users see root bundle or per-driver URLs in HPM, not per-driver manifests
   - Root `README.md` inventory table must exist; it's the source of truth for user discovery

3. **Root packageManifest.json structure:**
   ```json
   {
     "packageName": "bundle name",
     "author": "author",
     "minimumHEVersion": "2.3.0",
     "dateReleased": "YYYY-MM-DD",
     "version": "X.Y.Z",
     "drivers": [
       {
         "id": "UUID",
         "name": "Driver name",
         "namespace": "mads",
         "location": "https://raw.githubusercontent.com/.../driver.groovy",
         "required": false,
         "version": "X.Y.Z"
       }
     ]
   }
   ```

4. **Workflow trigger logic:** The release.yml workflow is path-filtered to ONLY trigger on packageManifest.json changes
   - .groovy file changes alone will NOT trigger the workflow
   - Use `gh workflow run release.yml --ref main` for manual testing

5. **Changelog format consistency:** PurpleAir and Honeywell v0.5 set the pattern; Fully Kiosk needed alignment
   - Indentation: 4 spaces after comment marker (`* `)
   - Entry start: Direct version number (no "Version: " label)
   - Lines under Changelog: section use same leading `*` comment marker, continue with 4-space indent

### Files Changed

1. `drivers/fully-kiosk/fully-kiosk.groovy` — Flatten 6 versions; add Changelog: label; remove Version: prefix
2. `drivers/honeywell-t6-pro/honeywell-t6-pro.groovy` — Flatten 5 versions (already had Changelog: and correct format)
3. `packageManifest.json` — Add 3 drivers to `drivers` array; bump version to 1.1.0
4. `README.md` — Update status counts; add 3 drivers to inventory; add 2 new category sections; update compatibility table; expand HPM URLs list

### Commits

1. **33a2ec7** `chore(release): register 3 drivers in root for HPM + flatten changelog headers for release.yml regex` — Initial pass (failed)
2. **d7ab4e4** `fix: add Changelog: label to fully-kiosk header for release.yml regex matching` — Added missing label
3. **273f065** `fix: correct fully-kiosk changelog format for release.yml regex` — Removed "Version: " prefix, fixed indentation
4. **6d2ed6a** `chore: retrigger release.yml workflow for fully-kiosk fixes` — Empty commit to manual-trigger workflow

**Final SHA on main:** 6d2ed6a

## 2026-05-19 — Scribe Session: Inbox Merge + Orchestration Logs

**Link Agent:** Registered 3 drivers (Honeywell T6 Pro v0.5.0, Fully Kiosk v0.5.0, PurpleAir AQI v0.4.0) in root packageManifest.json and README.md. Established single-line changelog format rule required by release.yml (line 136 regex). Root bundle version bumped to 1.1.0.

**Commits:**
- 33a2ec7 (Honeywell T6 Pro registration)
- d7ab4e4 (Fully Kiosk registration)
- 273f065 (PurpleAir registration)
- 6d2ed6a (Changelog format rule, bundle v1.1.0)

**Deliverable:** Orchestration log created (.squad/orchestration-log/2026-05-19-043500Z-link.md)
