---
name: "repo-public-release-checklist"
description: "Prepare a local project for a first public GitHub release without leaking internal AI/session artifacts."
domain: "release"
confidence: "high"
source: "earned"
---

## Context
Use this when promoting a locally developed project to its first public GitHub repo, especially when the working directory contains internal AI/team state, capture files, editor caches, or local automation scaffolding.

## Rules
1. Read any internal directives first and identify folders/files that must never be published.
2. Audit public docs for internal path references, stale architecture text, missing install URLs, and missing status disclaimers.
3. Ensure manifest/package metadata matches the release version, author, documentation link, and raw GitHub URLs before pushing.
4. Add ignore rules before `git add .`; at minimum cover internal coordination dirs (for example `.squad/`, `.copilot/`), packet captures, editor state, and machine-local caches.
5. Inspect `git status --short` before committing and explicitly verify excluded paths are not staged.
6. Watch for non-obvious local artifacts outside the obvious internal dirs (for example `.vs/`, `.github/agents/`, internal workflows, or `.gitattributes` files that only exist to support local team tooling).
7. Commit first, then create/publish the GitHub repo, and verify the remote tree through the GitHub API.
8. Confirm the HPM/raw-install URL resolves publicly after push.

## Examples
- `hubitat-drivers` v0.4.0 public release on 2026-05-16
  - Repo: `https://github.com/madskristensen/hubitat-drivers`
  - HPM: `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json`

## Anti-Patterns
- Running `git add .` before the ignore rules exist
- Publishing `.squad/`, `.copilot/`, `.pcap`, or editor/system caches
- Leaving user docs pointing at internal paths or internal code names
- Assuming only `.squad/` is private and missing adjacent local automation files under `.github/`
