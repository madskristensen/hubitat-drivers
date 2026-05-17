# Documentation Specialist — Link

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers
- **Stack:** Markdown, JSON (HPM manifests), community documentation patterns
- **Created:** 2026-05-16

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
