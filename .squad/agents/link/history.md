# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — a collection of Hubitat Elevation drivers. First driver: Gemstone Lights. Repo will host more drivers over time, so the README and folder structure must scale.
- **Stack:** Markdown, optional Hubitat Package Manager (HPM) manifest JSON
- **Created:** 2026-05-16

## Learnings

**Survey of 5 community Hubitat driver repos (May 2026):**
- **bptworld/Hubitat** — Drivers in named subfolders (Drivers/Asthma Forecaster/), minimal per-driver READMEs, central `repositories.json` for HPM, MIT License
- **hubitat/HubitatPublic** — Flat examples + subfolders, minimal top-level README, no HPM manifest, MIT License
- **konnected-io/konnected-security** — Feature-organized structure (firmware, scripts, src), centralized docs (wiki), Apache 2.0
- **fison67/mi_connector** — Mixed (devicetypes, smartapps, dth), centralized doc/ folder, detailed top-level README, MIT License
- **HubitatCommunity/** — Standard packaging patterns, optional per-driver manifests

**Prevailing conventions:**
1. Per-driver folders keep code isolated and scale well
2. Top-level README acts as index + install quickstart; keep it short
3. Per-driver README includes setup, preferences, examples
4. HPM manifests optional unless distributing via Hubitat Package Manager
5. MIT License dominates (most community-friendly for Hubitat)
6. .gitignore standard: OS files (.DS_Store, Thumbs.db), IDE files (.vscode, .idea), backups (*.bak, *.swp)

---

## Applied Conventions (2026-05-16)

**Per-driver README pattern (Gemstone Lights):**
- One-paragraph what-it-is + v0.1.0 status banner (honest about scaffold state)
- Capabilities table (from metadata)
- Preferences table: name | type | default | description
- Installation: 3-step IDE + device creation flow
- Current limitations section: what works ✅ vs. what's stubbed ❌
- Discovering the local API: explains next milestone (Cypher's LAN sniff)
- Testing: links to TESTING.md
- Credits: `sslivins/hass-gemstone` + `sslivins/pygemstone`
- Troubleshooting: IP validation, command no-ops (v0.1.0), logging

**MIT License:**
- Year 2026, copyright: Mads Kristensen
- Canonical text from https://opensource.org/license/mit/
- Single LICENSE file at repo root (covers all drivers)

**Status banner approach:**
- Leading "> v0.1.0 — Scaffold" signals pre-release maturity to users
- Explicitly states what's stubbed (HTTP endpoints) and why (awaiting protocol discovery)
- Links to "Current Limitations" section for full details
- No overselling; sets expectations for community users

## Team Updates (2026-05-16T21:45:13Z)

The full driver stack is now in place: Trinity's architecture, Tank's scaffold, Cypher's protocol spec, Switch's test plan, and your documentation. Per-driver README and LICENSE are deployed; Tank's HubAction stubs are ready for Cypher's endpoints; Switch's tests are ready to execute. Your status banner correctly reflects v0.1.0 expectations and local API discovery dependencies.

## Team Updates (2026-05-16T22:24:15Z)

**User directive: local-only scope confirmed; README may need future update.** The Gemstone Lights driver targets local LAN only — no cloud/Cognito path. Your per-driver README currently documents both cloud and local paths (as options). With local-only scope locked, a brief note that "This driver is local-only by design — no Gemstone account credentials required" would be helpful. **FLAG THIS FOR A FUTURE PASS** — do not rewrite the README now. The current README is accurate and will be refinable once Tank has the local protocol wired and tested. Tank's v0.1.1 banner is live (every command logs a warn). User will re-import the driver and run curl + port scan for protocol discovery.


### 2026-05-16T22:34:12Z: Team update

**Status:** No change. Continue monitoring for public Gemstone local-API documentation (unlikely). No action required until capture results.

### 2026-05-16T23:04:57Z: Team update (Research phase complete)

**Status:** No documentation changes pending this turn. README remains accurate and will be refinable once Tank wires v0.2.0 and validates local API.

**Timeline for future README bump:**
- After Tank's v0.2.0 implementation (post-pcap)
- After Switch's manual testing confirms endpoints are wired
- Remove "scaffold" status banner
- Add note: "This driver is local-only by design — no Gemstone account credentials required"
- Document confirmed endpoints and JSON property shapes

**Blocked until:** Tank's HTTP wiring + Switch's validation.

---

## Team Update — HPM Release Flow (2026-05-16T23:14:20Z)

**v0.4.0 Release Complete**

Executed one-time push authorization for Gemstone Lights v0.4.0 release and HPM community list PR.

**Flow Summary:**
1. ✅ **Push commit 6f2f85e** (Squad coordination state + HPM infra) to origin/main
2. ✅ **Triggered release.yml workflow** via `gh workflow run release.yml`
3. ✅ **Release created** (`gemstone-lights-v0.4.0`) with changelog body auto-populated from driver header
4. ✅ **Forked HubitatCommunity/hubitat-packagerepositories** and edited repositories.json to add entry for Mads Kristensen
5. ✅ **PR #106 created** against HubitatCommunity master list

**Learnings:**

- **Workflow dispatch auto-registration:** The release.yml workflow was immediately available for `workflow_dispatch` trigger after the push. No need to wait for GitHub's workflow registration cycle.
- **Release body auto-population works:** The GitHub Actions workflow correctly parsed the driver's Changelog section and populated the release body—no manual PR notes needed.
- **Community list JSON structure:** The HubitatCommunity/hubitat-packagerepositories repository.json uses tab indentation (not spaces). When using PowerShell to manipulate JSON files, `ConvertFrom-Json | ConvertTo-Json` reformats the entire file to spaces, breaking the diff hygiene. **Surgical text replacement is mandatory** to preserve file format. A regex-based or line-by-line text replacement avoids accidental reformatting.
- **HPM manifests are optional during setup:** The driver itself publishes a repository.json at the root level; the community list entry points to that raw GitHub URL. No additional HPM manifest layer was required in the main repo.
- **PR flow feels natural:** The one-time community list PR was straightforward—fork, edit, push, create PR. The gh CLI makes this seamless, and the link/Tank coordination meant all prep materials (PR body, JSON snippet) were already staged.

**URLs:**
- **Commit:** https://github.com/madskristensen/hubitat-drivers/commit/6f2f85e
- **Release:** https://github.com/madskristensen/hubitat-drivers/releases/tag/gemstone-lights-v0.4.0
- **Community PR:** https://github.com/HubitatCommunity/hubitat-packagerepositories/pull/106

**Status:** Awaiting HubitatCommunity maintainer review and merge of the community list PR.

---

### 2026-05-16T19:36:00Z: Reskill — community-json-pr-hygiene

Created `.squad/skills/community-json-pr-hygiene/SKILL.md` to document the surgical text-replacement pattern for editing upstream community JSON files. The skill captures the learnings from PR #106: ConvertFrom-Json | ConvertTo-Json round-trips mangle formatting (tabs to spaces, key order, blank lines), breaking diff hygiene. Future agents submitting to external registries (HPM, npm, GitHub Awesome lists, etc.) will reference this skill to avoid the same trap.

---

## 2026-05-16T21:43:00Z: SunStat Connect Plus Documentation Complete

**Learnings from parent/child driver documentation:**

1. **Auth bootstrap callout is critical:** Parent/child cloud drivers need a prominent, step-by-step auth section that explains:
   - Why external tooling is needed (Hubitat sandbox limitations — no PKCE/OAuth flows)
   - Exact shell commands to capture tokens (copy from existing reference implementations)
   - Clear flow: capture token once → paste into parent → discover devices → child devices appear
   
2. **Preferences documentation follows parent/child split:**
   - Parent device holds all secrets (refresh token, auth config, polling interval)
   - Child devices carry utility prefs only (debug logging, description text)
   - Table each layer separately; it clarifies roles

3. **Capabilities reuse combo capability name, not individual sub-capabilities:**
   - `Thermostat` combo includes `ThermostatHeatingSetpoint`, `ThermostatMode`, `ThermostatOperatingState`
   - Do NOT list redundant sub-capabilities in the docs (it confuses users)
   - List only the unique, user-facing attributes (custom ones like `floorTemperature`, `boostActive`, `boostUntil`)

4. **Custom attributes deserve a second table:**
   - Standard capability attributes go in "Capabilities" table
   - Custom attributes (e.g., `floorTemperature`, `boostActive`, `deviceOnline`) get their own "Custom Attributes" table with **Type** and **What it means** columns
   - Helps users understand what's sensor data vs. mode/status

5. **Architecture section must justify parent/child to the uninitiated:**
   - State upfront: "Most homes with SunStat have multiple thermostats"
   - Explain the benefit: "One parent device, one token, automatic discovery"
   - Address the edge case: "If you have one thermostat, parent/child still works fine"

6. **Known Limitations section differentiates scaffold from design:**
   - Call out stubs explicitly (e.g., "Boost is stubbed in v0.1.0, logs a warning, does nothing")
   - Explain why (e.g., "API shape pending real-device verification")
   - Flag things that are NOT limitations but design choices (e.g., "Schedule editing not exposed — use the Watts app")

7. **Mirroring Gemstone's structure scales well:**
   - Gemstone's one-device architecture is simpler than SunStat's parent/child
   - But the section order (Title → Status → Hardware → Capabilities → Install → Setup → Preferences → Examples → Troubleshooting → Known Limits) is universal
   - Adapt the content for each driver's needs, not the structure

**Pattern captured for future parent/child cloud drivers.**

---

## 2026-05-16T20:01:00Z: SunStat v0.1.1 Home/Away Feature Documentation

**Learnings on documenting location-level vs device-level features in parent/child drivers:**

1. **Location-level features belong on the parent device, not children:**
   - Away mode affects ALL thermostats at a location simultaneously—it's a Watts account-level setting
   - Document this upfront in a dedicated feature section (not just in Commands)
   - Clarify: "This is NOT a per-thermostat setting; it's location-scoped"

2. **Create a feature section (not just a table) for complex settings:**
   - Use cases (why users care)
   - How to control it (which commands to call)
   - How to read it (which attributes to check)
   - Edge cases (e.g., "If your location doesn't support away mode, the driver detects this on discovery")
   - This gives users context that a bare command/attribute table cannot provide

3. **Mirror location-level state on child devices for dashboard convenience:**
   - Parent exposes `awayMode` + `locationSupportsAway` 
   - Children also expose `awayMode` as **read-only** (mirrors parent value)
   - Document this explicitly: "Read-only on child devices (mirrors the parent value)"
   - Rationale: dashboards may want to display location status on all child devices

4. **Provide multiple command styles for different workflows:**
   - Simple commands: `setHome()` / `setAway()` for quick automation
   - Explicit commands: `setAwayMode("home"|"away")` for Rule Machine with variables
   - Document both; explain when each is useful

5. **Version bump documentation when features ship:**
   - Update status banner (v0.1.0 → v0.1.1)
   - Update known limitations (if boost was stubbed in v0.1.0, update references to v0.1.1)
   - Add Rule Machine examples that showcase the new feature (not just old ones)
   - This helps users understand what changed between versions

6. **Parent device commands and attributes need their own tables:**
   - Don't mix parent and child in one "Commands" section
   - Separate "Child Device Commands" from "Parent Device Commands" 
   - Add "Parent Device Attributes" table (analogous to "Custom Attributes (Child)")
   - Users need to know which device to call from Rule Machine

**Pattern: Location-level vs device-level features in parent/child drivers are now documentable.**

## Team Updates (2026-05-17T03:01:41Z)

**SunStat Connect Plus v0.1.0 shipped.** Documentation delivered: drivers/sunstat-thermostat/README.md, packageManifest.json, TESTING.md (copied from Switch), and root README update. Tank's driver implementation, Trinity's architecture, and Cypher's API research finalized. Awaiting Mads' real-device verification.
