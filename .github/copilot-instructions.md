# Repository Instructions

## Hubitat Package Manager

When adding or removing a driver or app, update the root `packageManifest.json` in the same change:

- Add or remove its entry in the appropriate `drivers` or `apps` array.
- Use a unique, stable UUID and the raw GitHub URL for the Groovy source file.
- Keep the entry's name, namespace, version, and required status synchronized with the source and any component-level package manifest.
- Increment the root package version and update `dateReleased`.
- Keep `repository.json` pointed at the root `packageManifest.json` so HPM discovers the complete bundle; do not replace it with a single component manifest.
- Validate every changed JSON manifest before completing the change.
