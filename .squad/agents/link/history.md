# Documentation Specialist — Link

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers
- **Stack:** Markdown, JSON (HPM manifests), community documentation patterns
- **Created:** 2026-05-16

## Active Milestones Summary

### Touchstone Fireplace Driver (Current)

v0.1.4 documentation shipped with README covering Power-on Defaults, Safety considerations, Device Profiles, and user-friendly troubleshooting. packageManifest bumped to v0.1.4. Changelog omits v0.1.3 (internal buggy state never released).

**Key learnings:**
- Hardware safety in docs: explicit, clear, direct language about intentional feature omissions
- Version anchoring in troubleshooting: tie error messages to version ranges for user self-service
- Changelog hygiene: only list versions released to users; internal intermediate states omitted
- Device Profile docs: enable community contribution via user-discoverable preset patterns

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
