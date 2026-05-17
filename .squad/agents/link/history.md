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

## Learnings

- **README install-flow pattern for command-based token bootstrap:** When a value exceeds platform preference limits (Hubitat ~1024 chars), document the command-based path separately from preferences config. Use a blockquote to explain *why* the unusual flow is necessary, so users don't assume it's a workaround. Keep the "why" terse (one sentence max).
- **Section numbering rules:** When rewording setup steps that involve a new command insertion, renumber all subsequent steps and update cross-references (e.g., "see Step 4"). Link discovered this by carefully re-reading the task requirements — the original Step 4 (Discover Devices) became Step 5 after the new command step was inserted, and all troubleshooting references had to be updated to match.

- 2026-05-17T04-20-29Z: v0.1.3 SunStat Connect Plus shipped (setRefreshToken command + docs + tests) — tank/link/switch cross-team ship
