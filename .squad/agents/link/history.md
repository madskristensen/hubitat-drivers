# Documentation Specialist — Link

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers
- **Stack:** Markdown, JSON (HPM manifests), community documentation patterns
- **Created:** 2026-05-16

## 2026-05-17T13:21:30-07:00 — Touchstone v0.1.5 added to root README (First Public Release)

**Collaborators:** Mads (prep for v0.1.5 first public release), Link (root README update)

### Scope

Touchstone / Tuya Fireplace is now ready for first public release (v0.1.5). Root README needed a new driver entry: updated status line, drivers table, manual install section, HPM URLs, and compatibility details.

### Changes Made

1. **Status line:** Updated from "Two beta drivers" to "Three beta drivers" with current versions: Gemstone Lights v0.4.8, SunStat Connect Plus v0.1.4, Touchstone / Tuya Fireplace v0.1.5.

2. **Drivers table:** Added row for Touchstone with factual description (LAN integration, Sideline Elite + other Tuya WiFi fireplaces, DP discovery for unmapped models).

3. **Manual install section:** Generalized from Gemstone-only example to framework applicable to all drivers. Changed step 3 to reference "driver source code (e.g., `drivers/gemstone-lights/...`, `drivers/touchstone-fireplace/...`, etc.)" and step 4 to reference driver types by example rather than hardcoded Gemstone.

4. **Compatibility details:** Added Touchstone entry explaining LAN reachability requirement and local key bootstrap via `tinytuya wizard`.

5. **HPM URLs:** Expanded from single Gemstone example to bulleted list of all three drivers' packageManifest URLs for user clarity.

### Pattern: Adding a New Driver to Root README

When a new driver reaches release readiness, update root README in five points:

1. **Status line** — Increment driver count and add new driver name + version with brief what's-new framing (what integration type, what's notable)
2. **Drivers table** — Add row with Status (Beta/Stable), Description (what devices, key features, distinguishing tech), and link to per-driver README
3. **Manual install section** — Generalize example code paths and device type names with "(e.g., ...)" patterns so section is reusable for all drivers
4. **Driver Compatibility Details** — Add network/integration requirement for the new driver (cloud API, LAN, etc.)
5. **HPM URLs** — Decide between keeping single example or expanding to list. For multi-driver projects, list is cleaner and more user-friendly.

### Key Learnings

- Root README is community-facing; clarity > brevity when supporting multiple installation paths (HPM, manual)
- Gemstone was v0.4.1 in old README but is now v0.4.8 per current manifest — always verify versions from packageManifest.json, not assumption or prior README text
- Manual install section becomes boilerplate when generalized; good refactor opportunity when adding driver N+1

---

## 2026-05-17T19:29:40Z — Touchstone v0.1.5 docs bump (Tank's paragraph() fix)

**Collaborators:** Tank (v0.1.5 code), Link (docs)

### Documentation Role in v0.1.5 Release

v0.1.5 is a fast-follow patch for Tank's Hubitat sandbox `paragraph()` removal. Documentation bump follows the **rapid version-bump-only pass pattern**: minimal structural changes, version anchors updated, changelog and troubleshooting entries added.

### Changes Made

1. **packageManifest.json:** Version bumped 0.1.4 → 0.1.5; dateReleased kept as 2026-05-17 (same day release)
2. **README.md:**
   - Status header: v0.1.4 → v0.1.5
   - Latest section updated with bugfix note (removed app-only `paragraph()`)
   - Troubleshooting CRC32 entry: "v0.1.5 or later"
   - Troubleshooting Reflection entry: "v0.1.5 or later"
   - **NEW:** Troubleshooting entry: "No signature of method: Script1.paragraph()" → points users to v0.1.5+
   - Changelog: Added v0.1.5 entry (BUGFIX — removed `paragraph()` from preferences block)

### Key Learnings for Hubitat Sandbox Pattern

**Three Hubitat sandbox families now consolidated in SKILL.md:**
1. **Import allowlist** — Blocks `java.util.zip.CRC32` and others
2. **Reflection blocked** — Blocks `.getClass()`, `.getMethods()`, etc.
3. **App-only preference UI** — Blocks `paragraph()`, `section()`, `href()`, etc. (v0.1.5 fix)

Cross-reference in docs: "driver has now been hardened against all three Hubitat sandbox restriction families."

### Version-Bump-Only Pass Pattern (Established v0.1.4–v0.1.5)

For fast-follow patches with only version changes and minor doc additions:
- Keep README structure intact; don't reorganize sections
- Update version in headers/Latest section, troubleshooting entries, and CRC/Reflection anchors
- Add new troubleshooting entry only if it's a directly new error message
- Changelog always goes first (before older entries)
- No need to refactor sections or rewording existing text

---

## 2026-05-17T12:22:15-07:00 — Touchstone v0.1.5 docs bump (Tank's paragraph() fix)

**Collaborators:** Tank (v0.1.5 code), Link (docs)

### Documentation Role in v0.1.5 Release

v0.1.5 is a fast-follow patch for Tank's Hubitat sandbox `paragraph()` removal. Documentation bump follows the **rapid version-bump-only pass pattern**: minimal structural changes, version anchors updated, changelog and troubleshooting entries added.

### Changes Made

1. **packageManifest.json:** Version bumped 0.1.4 → 0.1.5; dateReleased kept as 2026-05-17 (same day release)
2. **README.md:**
   - Status header: v0.1.4 → v0.1.5
   - Latest section updated with bugfix note (removed app-only `paragraph()`)
   - Troubleshooting CRC32 entry: "v0.1.5 or later"
   - Troubleshooting Reflection entry: "v0.1.5 or later"
   - **NEW:** Troubleshooting entry: "No signature of method: Script1.paragraph()" → points users to v0.1.5+
   - Changelog: Added v0.1.5 entry (BUGFIX — removed `paragraph()` from preferences block)

### Key Learnings for Future Sandbox Fixes

**Three Hubitat sandbox families now documented in Touchstone:**
1. **Import allowlist** — Blocks `java.util.zip.CRC32` and others
2. **Reflection blocked** — Blocks `.getClass()`, `.getMethods()`, etc.
3. **App-only preference UI** — Blocks `paragraph()`, `section()`, `href()`, etc. (v0.1.5 fix)

Cross-reference in docs: "driver has now been hardened against all three Hubitat sandbox restriction families" — pattern borrowed from tuya-local-groovy skill. Enables users to understand the broader defensive architecture without duplicating the full pattern documentation.

### Version-Bump-Only Pass Pattern

For fast-follow patches with only version changes and minor doc additions:
- Keep README structure intact; don't reorganize sections
- Update version in headers/Latest section, troubleshooting entries, and CRC/Reflection anchors
- Add new troubleshooting entry only if it's a directly new error message
- Changelog always goes first (before older entries)
- No need to refactor sections or rewording existing text

---

## Active Milestones Summary

### Touchstone Fireplace Driver (v0.1.5 — Current)

v0.1.5 documentation shipped with fast-follow patch for `paragraph()` sandbox restriction. packageManifest and README updated; changelog reflects all three sandbox families now addressed.

**Key learnings:**
- Hardware safety in docs: explicit, clear, direct language about intentional feature omissions
- Version anchoring in troubleshooting: tie error messages to version ranges for user self-service
- Changelog hygiene: only list versions released to users; internal intermediate states omitted
- Device Profile docs: enable community contribution via user-discoverable preset patterns
- **Sandbox pattern docs:** Consolidate across driver families (three restriction families); cross-reference rather than duplicate

---

## 2026-05-17T18:58:55Z — Touchstone v0.1.4 documentation + bundled commit (Cross-Agent Batch)

**Collaborators:** Tank (v0.1.3 + v0.1.4), Link, Switch (test surface)

### Documentation Role in v0.1.4 Release

v0.1.4 is the first public release users will see. Documentation was updated to omit the internal v0.1.3 (buggy intermediate state) from the changelog. This is the first time we've publicly released a version that had an internal-only predecessor; pattern is now established for future "fast-follow safety patches."

### Key Learnings for Future Hardware Safety Drivers

1. **Explicit omissions in docs:** "no defaultHeatLevel preference and there never will be" — clarity > silence
2. **Safety section placement:** New first-class section in README (not buried in Settings)
3. **Troubleshooting as version anchors:** Users can self-identify by error message and version mapping
4. **README schema for driver profiles:** Device Profile dropdown enabling community contribution to DP preset library
5. **Changelog hygiene:** Only list versions released to users; internal buggy states omitted

### v0.1.4 README Changes

- Status header: v0.1.2 → v0.1.4
- Added Power-on Defaults section (explains ~1.5s delay, Device Profile gating)
- New Safety subsection (radiant heat element fire/burn risk; no auto-heater)
- Troubleshooting: CRC32 (still in v0.1.2, fixed v0.1.4); Reflection error (v0.1.3 only, fixed v0.1.4)
- Changelog: v0.1.4 entry; v0.1.2 release note; v0.1.3 omitted

### Versioning Pattern Established

v0.1.3 → v0.1.4 fast-follow safety patch pattern:
- Internal buggy state (v0.1.3) never published
- Users see only the hardened v0.1.4
- Changelog reflects user-visible releases only
- Troubleshooting index maps errors to version ranges so users can locate fixes

---

See history-archive.md for detailed earlier sessions (Gemstone, SunStat, Bosch README scoping).


## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.

