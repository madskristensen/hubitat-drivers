# Releasing a new version

1. Bump each changed driver or app's source version (header and version constant/helper), any inlined user-agent version, and its changelog.
2. Synchronize its component `packageManifest.json` package/entry versions and `dateReleased`. For parent/child packages, keep each entry at its own source version. Update the root bundle's component entries, increment the root package version, and update its release date. Adding a component also requires a stable UUID and raw source URL in the root bundle; keep repository discovery pointed at that root manifest.
	  Run `.\tests\Run-Tests.ps1` to verify the Groovy regressions and manifest coverage before releasing. See [test prerequisites](tests/README.md). Test affected integrations on a hub as well; mocks do not validate firmware or cloud behavior.
3. Commit + push to `main`
4. The `release.yml` workflow auto-creates a git tag + GitHub Release with the changelog as the body
5. HPM users see "update available" the next time their hub polls (or they hit "check for updates")

You can also trigger the workflow manually from the **Actions** tab via **Release** → **Run workflow**.
