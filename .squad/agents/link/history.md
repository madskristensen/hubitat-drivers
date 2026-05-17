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
