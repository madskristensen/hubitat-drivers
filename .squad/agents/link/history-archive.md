# Link Agent — History Archive (Pre-2026-05-17)

## Summary

Link is the DevRel and Documentation specialist for the hubitat-drivers project. This archive captures the key learnings and conventions from the initial project phase (2026-05-16 through early 2026-05-17).

## Core Learnings

### Community Hubitat Driver Conventions (Surveyed 5 repos, May 2026)

1. **Folder structure:** Per-driver subfolders with kebab-case naming scale well for multi-driver repos.
2. **Top-level README:** Acts as index + install quickstart; keep it short and reference per-driver READMEs.
3. **Per-driver README:** Includes capabilities table, preferences, setup steps, examples, troubleshooting.
4. **HPM manifests:** Optional unless distributing via Hubitat Package Manager; per-driver packageManifest.json is standard.
5. **MIT License:** Dominates the community (most friendly for Hubitat); single repo-root LICENSE file covers all drivers.

### Gemstone Lights Documentation (v0.1.0 → v0.4.0)

- **Status banner approach:** "v0.1.0 — Scaffold" signals pre-release maturity and sets expectations.
- **Local-only scope:** Confirmed mid-session (user directive); documentation remains accurate for future v0.2.0 refinement post-protocol discovery.
- **Section order:** Title → Status → Hardware → Capabilities → Install → Setup → Preferences → Examples → Troubleshooting → Known Limits (universal pattern).
- **Local API documentation:** Not published by Gemstone; discovery requires network capture or C4 driver reverse-engineering.

### HPM Release Infrastructure (v0.4.0 Published)

- **Workflow dispatch:** Immediately available after commit push; no wait for GitHub's registration cycle.
- **Release body auto-population:** GitHub Actions correctly parses driver Changelog section and populates release notes.
- **Community list JSON format:** HubitatCommunity/hubitat-packagerepositories uses tab indentation (not spaces). PowerShell's ConvertTo-Json reformats to spaces, breaking diff hygiene — **surgical text replacement is mandatory**.
- **Community list PR pattern:** Fork → edit JSON → push → create PR; straightforward with gh CLI.

**URLs:**
- Release: https://github.com/madskristensen/hubitat-drivers/releases/tag/gemstone-lights-v0.4.0
- Community PR: https://github.com/HubitatCommunity/hubitat-packagerepositories/pull/106

### SunStat Connect Plus Documentation (Parent/Child Architecture, v0.1.0 → v0.1.2)

**Auth bootstrap:** Cloud drivers need prominent, step-by-step auth section explaining sandbox limitations and token capture flow.

**Parent/child documentation split:**
- Parent holds secrets (refresh token, auth config, polling interval)
- Child carries utility prefs only (debug logging, description text)
- Document each layer separately for clarity

**Capabilities and attributes:**
- Combo capabilities like `Thermostat` should NOT list redundant sub-capabilities
- Custom attributes get their own table with Type and "What it means" columns
- Parent and child commands/attributes need separate documentation

**Feature-rich releases (v0.1.2 — 6 new features):**
- Create dedicated feature sections (not just table rows) explaining use cases, how to control, edge cases
- Document actual API state names in attribute enums (e.g., `on`/`off` vs. `true`/`false`)
- Distinguish between true commands and read-only mirrors with explicit annotations
- Provide v0.X-specific Rule Machine examples showcasing new capabilities
- Update Known Limitations with v0.X-specific stubs and API dependencies

### README Community-Conformance Audit (Link-3, May 2026)

**Conventions Adopted (6 targeted edits across 3 READMEs):**
1. Explicit compatibility headers (hub gens + min platform version) at driver README top
2. Root README: enhanced compatibility section with per-driver network requirements
3. Root README: link to RELEASING.md for versioning transparency
4. Per-driver README: latest version badge + GitHub Releases link
5. Format: "Compatibility: Hubitat Elevation C-7, C-8 | Platform 2.3.3.x or later"

**Conventions NOT Adopted (Intentional):**
- Screenshots/GIFs (low SNR for cloud REST drivers)
- CI badges (no Groovy standard CI ecosystem)
- Donation links (optional; Mads' decision)
- Fingerprint info (Zigbee/Z-Wave only; N/A for cloud REST)
- Multi-hub comparison (C-5 unverified; explicit C-7/C-8 prevents support burden)

**Open Questions for Mads:**
1. Hubitat Community forum dedicated threads available for linking?
2. Add PayPal/Venmo donation links (common but optional)?
3. C-5 hub testing verified, or keep C-7/C-8 as explicit support tier?

## Skills Extracted

- `.squad/skills/community-json-pr-hygiene/SKILL.md` — Surgical text-replacement pattern for upstream JSON edits (preserves formatting vs. ConvertTo-Json round-trips)
- `.squad/skills/hubitat-heat-only-thermostat/SKILL.md` — Parent/child thermostat architecture with constrained modes and custom commands (reusable for future dual-sensor/multi-device drivers)

## Team Handoffs

- **Tank:** Documentation defines driver scope; Tank implements per specification.
- **Switch:** Documentation specifies what to test; Switch designs test plan aligned to docs.
- **Trinity:** Documentation mirrors architecture decisions; Trinity refines as real-device data arrives.
- **Cypher:** Documentation links to API specs and protocol discoveries.

---

**Archive created:** 2026-05-17T03:37:53Z  
**Main history.md:** Reset to learnings + current team updates only.
