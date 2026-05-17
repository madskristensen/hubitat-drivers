# Releasing a new version

1. Bump the version in all 6 spots in the driver:
   - File header comment
   - `DRIVER_VERSION` const
   - `USER_AGENT` inlined literal
   - Changelog entry
   - `packageManifest.json` top-level `version`
   - `packageManifest.json` driver-entry `version`
2. Add a changelog entry to the driver's `.groovy` file header
3. Commit + push to `main`
4. The `release.yml` workflow auto-creates a git tag + GitHub Release with the changelog as the body
5. HPM users see "update available" the next time their hub polls (or they hit "check for updates")

You can also trigger the workflow manually from the **Actions** tab via **Release** → **Run workflow**.
