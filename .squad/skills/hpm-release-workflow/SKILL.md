---
name: "hpm-release-workflow"
description: "Prepare Hubitat repository.json metadata and automate GitHub releases from packageManifest version bumps."
domain: "hubitat"
confidence: "high"
source: "earned"
---

## Context
Use this skill when a Hubitat driver repo needs HPM publisher metadata, a repeatable release workflow, or a manual handoff for the one-time HubitatCommunity master-list PR.

## Rules
1. Keep the version aligned in six places: driver header comment, `DRIVER_VERSION`, `USER_AGENT` literal, driver header changelog entry, package manifest top-level `version`, and the driver entry `version`.
2. Root `repository.json` points at each driver's raw `packageManifest.json`, not the `.groovy` file.
3. Use HPM category values from `settings.json` for `category`; add tags only when the repo wants extra discoverability.
4. Release tags should be deterministic: `<driver-folder>-v<version>`.
5. Release notes come from the matching version entry under the driver's header `Changelog:` block.
6. In no-agent-pushes mode, agents may edit, stage, and commit locally, but they stop before `git push`, `gh repo fork`, or `gh pr create`.

## Workflow Pattern
1. Trigger GitHub Actions on `workflow_dispatch` and on `push` to `main` when `drivers/**/packageManifest.json` changes.
2. Checkout with full history and tags.
3. Find each `packageManifest.json`, read `.version` with `jq`, derive the driver slug from the folder name, and skip any tag that already exists.
4. Parse `<driver-folder>.groovy` for the matching changelog entry and use it as the GitHub Release body.
5. Create an annotated tag, then create the GitHub Release.

## One-time Community List Submission
- Publish root `repository.json` in the driver repo.
- Add a `{ "name": "...", "location": "https://raw.githubusercontent.com/.../repository.json" }` object to `HubitatCommunity/hubitat-packagerepositories` `repositories.json`.
- Keep a local handoff file with the JSON snippet and PR body so the repo owner can run the fork/push/PR flow manually.

## Example
- Driver folder: `drivers/gemstone-lights/`
- Manifest version: `0.4.0`
- Tag: `gemstone-lights-v0.4.0`
- Release body source: `drivers/gemstone-lights/gemstone-lights.groovy` `Changelog:` line for `0.4.0`
