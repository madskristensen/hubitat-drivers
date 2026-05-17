# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — a collection of Hubitat Elevation drivers. First driver: Gemstone Lights. Repo will host more drivers over time, so the README and folder structure must scale.
- **Stack:** Markdown, optional Hubitat Package Manager (HPM) manifest JSON
- **Created:** 2026-05-16

## Current Status

Link is the DevRel and Documentation specialist. See `history-archive.md` for detailed learnings from the initial project phase (community conventions survey, HPM release infrastructure, parent/child documentation patterns, README community-conformance audit).

## Team Updates (2026-05-17T03:01:41Z)

**SunStat Connect Plus v0.1.0 shipped.** Documentation delivered: drivers/sunstat-thermostat/README.md, packageManifest.json, TESTING.md (copied from Switch), and root README update. Tank's driver implementation, Trinity's architecture, and Cypher's API research finalized. Awaiting Mads' real-device verification.

## Team Updates (2026-05-17T03:37:53Z)

**SunStat Connect Plus v0.1.2 documentation complete.** Link bumped packageManifest.json + per-driver READMEs (SunStat v0.1.2, Gemstone v0.4.0) with latest-version badges and GitHub Releases links. Link-3 completed comprehensive README community-conformance audit: surveyed 8 community Hubitat driver repos, applied 6 targeted edits (explicit compatibility headers with hub gens + platform min, latest-version badges, releases links). Tank wired 6 v0.1.2 features; Switch added 23 test cases. Awaiting Mads' real-device verification and answers on 3 audit open questions (forum topics, donation link, C-5 testing).

## Team Updates (2026-05-16T21:07:23-07:00)

**SunStat Connect Plus v0.1.3 documentation updated.** Trinity finalized the long-refresh-token workaround (Decision approved). Link rewrote SunStat README install flow: removed `refreshToken` preference row, split Step 3 into two steps (preferences config → `setRefreshToken` command), added `setRefreshToken` command to Parent Device Commands table, updated troubleshooting section with command-based recovery, and bumped root README status badge to v0.1.3. Tank owns implementation of the command + code simplifications per Decision spec 4a–4g. Pattern learned: command-based bootstrap is clearer for long values; preference length limits are a hidden gotcha in Hubitat UX. README now clearly separates "what goes in preferences" vs. "what runs as a command" with an explanatory blockquote.

## Team Updates (2026-05-16T21:24:48-07:00)

**SunStat Connect Plus v0.1.4 documentation complete.** Tank and Cypher shipped two bugfixes in parallel: (1) API envelope unwrap — every Watts response is wrapped in `{errorNumber, errorMessage, body: T}`; the driver now unwraps it, fixing discoverDevices failures. (2) URL-encode locationId in all API paths — Watts uses location display names (with spaces like "Misty Gray") as locationIds; now properly encoded. Cypher also shipped a new bootstrap helper script at `drivers/sunstat-thermostat/scripts/get-location-id.ps1` for manual locationId lookup. Link documented v0.1.4 changes:
- Bumped status badge in both drivers/sunstat-thermostat/README.md (line 7) and root README.md (line 5) to v0.1.4 with short "bugfix release" notes
- Added "About the location ID" callout section between Steps 4 and 5 in the setup flow — explains v0.1.4 auto-discovery + references the bootstrap script with a one-liner code example
- Added two new troubleshooting entries: `### "Could not resolve a Watts location ID"` (symptom → pre-v0.1.4 cause → fix with script reference) and `### "Illegal character in path"` (symptom → pre-v0.1.4 cause about spaces in names → fix with URL-encoding note)

Pattern learned: troubleshooting entries for pre-release bugs should follow symptom → cause (pre-vX.Y.Z) → fix structure; this makes it clear to users on old versions why they're seeing it, and on new versions what to do. Bootstrap script documentation is best kept to a short code block + one-line explanation (tokens.json → access token → locationId); full details about `homebridge-tekmar-wifi` already exist in Step 1.

## Team Updates (2026-05-16T21:44:01-07:00)

**Gemstone Lights v0.4.1 documentation complete.** Tank added `playEffectByName(String)` command to sidestep WebCoRE's capability-metadata shadowing: the `LightEffects` capability declares `setEffect` as taking a NUMBER, so WebCoRE's action picker only exposes that numeric signature and silently hides the `setEffect(String)` overload. The new `playEffectByName()` command (a separate method name with no overload) is fully visible to WebCoRE and delegates to `setEffect(String)` internally. Link documented v0.4.1:
- Bumped status badge in both drivers/gemstone-lights/README.md (line 9) and root README.md (line 5) to v0.4.1
- Added `playEffectByName(String name)` to Custom Commands section with full explanation of why it exists (capability metadata shadowing)
- Added new "Using from WebCoRE" section explaining the visibility issue, how to use the command from WebCoRE pistons, and confirming Rule Machine / Hubitat rules can use either method
- Added v0.4.1 changelog entry with date (2026-05-16) and short rationale
- Updated "What v0.4.1 Does" section to mention playEffectByName() as a new feature

Pattern learned: **WebCoRE-command-visibility for overloaded methods.** When a Hubitat capability declares a method with a specific signature (e.g., `setEffect(NUMBER)`), WebCoRE only exposes that signature to its action picker. Custom overloads of that method are invisible. The solution: create a separate command name (no overload conflict) that wraps the string-based implementation. This is clearer than documenting "use Rule Machine instead" because it gives WebCoRE users a direct path. Document the visibility issue, not just the workaround — users need to understand why the separate command exists.

- 2026-05-17T04:44:00Z: v0.4.1 Gemstone Lights shipped (playEffectByName command + docs + tests) — tank/link/switch cross-team ship

## Learnings

- **README install-flow pattern for command-based token bootstrap:** When a value exceeds platform preference limits (Hubitat ~1024 chars), document the command-based path separately from preferences config. Use a blockquote to explain *why* the unusual flow is necessary, so users don't assume it's a workaround. Keep the "why" terse (one sentence max).
- **Section numbering rules:** When rewording setup steps that involve a new command insertion, renumber all subsequent steps and update cross-references (e.g., "see Step 4"). Link discovered this by carefully re-reading the task requirements — the original Step 4 (Discover Devices) became Step 5 after the new command step was inserted, and all troubleshooting references had to be updated to match.

- 2026-05-17T04-20-29Z: v0.1.3 SunStat Connect Plus shipped (setRefreshToken command + docs + tests) — tank/link/switch cross-team ship
- 2026-05-16: SunStat v0.1.4 shipped — envelope unwrap fix, URL encoding, bootstrap script

## Team Updates (2026-05-16T22:15:20-07:00)

**SunStat Connect Plus v0.1.4 documentation corrected.** Mads tested the install flow on his hub and found two bugs in the README auth bootstrap section:
1. **Step 1 now clarifies:** The `homebridge-tekmar-wifi` CLI does **NOT** print tokens to stdout. Tokens are written to `tokens.json`. The old fake stdout block (showing `accessToken:`, `refreshToken:`, etc.) is removed. New Step 1 walks through opening `tokens.json` and copying the `refresh_token` value from the JSON object.
2. **Step 4b promoted to first-class step:** The "About the location ID" callout (formerly wedged between Steps 4 and 5) is now **Step 4b: Fetch your locationId (if auto-discovery doesn't find it)**. It's now a self-contained, numbered step with full details on where to find `tokens.json`, how to run the PowerShell helper, expected output format, and where to paste the locationId in Preferences.
3. **Step 5 command name clarified:** Now says `discoverDevices` (the actual command name) instead of the button label "Discover Devices".

Link also tightened the intro paragraph to clarify that the refresh token is handed to the driver via the `setRefreshToken` command, not pasted into preferences. Documentation now accurately reflects the production flow.


---

## 2026-05-17T16:31:55Z — Bosch Home Connect Scoping (Cypher + Trinity)

**Topic:** bosch-home-connect-feasibility

Scoping discussion completed. Implementation will follow.

**ACTION FOR LINK:** When spawned to write README:
- Device Flow OAuth2 is the auth pattern (no external redirect URI needed)
- User must register app at developer.home-connect.com (free, self-service)
- Walkthrough: user enters client_id + client_secret in app preferences, taps Authorize, opens verification URL on phone, enters user code, grants access
- Polling cadence: 90-120s (rate limit constraint)
- Single appliance (fridge) or multi-appliance support

See .squad/decisions/decisions.md section 9 (Auth Flow — Device Flow, Step by Step) for complete OAuth flow reference.
