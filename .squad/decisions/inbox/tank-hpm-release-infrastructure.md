### 2026-05-16: Tank HPM release infrastructure
**By:** Tank
**What:** Add the repo-level HPM publishing kit: root `repository.json`, `.github/workflows/release.yml`, `RELEASING.md`, a top-level README HPM install section, and `release-tools/` handoff files for the one-time HubitatCommunity master-list PR.
**Why:** v0.4.0 is already public, but HPM still needs the publisher index plus a repeatable tag/release flow that does not rely on manual tagging each version.
**Shape:**
- `repository.json` is the publisher index that points Hubitat Package Manager at `drivers/gemstone-lights/packageManifest.json`.
- `release.yml` derives tags as `<driver-folder>-v<version>`, parses the matching `.groovy` header changelog entry, and creates the annotated tag + GitHub Release automatically.
- `RELEASING.md` documents the six version touchpoints in the driver/manifest pair.
- `release-tools/` carries the JSON snippet, PR body, and manual command list Mads needs for the one-time HubitatCommunity `repositories.json` submission.
**Operating model:** Per the no-agent-pushes directive, agents stop at the local commit. Mads runs the push, optional manual workflow dispatch, and community-list PR commands himself.
