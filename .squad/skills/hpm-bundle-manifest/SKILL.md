# Skill: HPM Multi-Driver Bundle Manifest

**Created:** 2026-05-17  
**Used in:** hubitat-drivers repo v1.0.0 bundle manifest

---

## Problem

Hubitat Package Manager (HPM) allows per-driver install via individual `packageManifest.json` URLs. When a repo contains multiple drivers, users must visit each driver directory individually. A **bundle manifest** at the repo root lets users install all drivers from a single HPM URL and receive grouped update notifications.

## Solution

Create a single `packageManifest.json` at the repository root that lists all drivers, then update CI/CD to handle the root manifest as a distinct release artifact.

## Bundle Manifest Structure

```json
{
  "packageName": "My Hubitat Drivers Bundle",
  "author": "Author Name",
  "version": "1.0.0",
  "minimumHEVersion": "2.3.7",
  "licenseFile": "https://raw.githubusercontent.com/owner/repo/main/LICENSE",
  "releaseNotes": "See individual driver changelogs.",
  "documentationLink": "https://github.com/owner/repo",
  "communityLink": "https://community.hubitat.com/...",
  "drivers": [
    {
      "id": "<UUID-matching-per-driver-manifest>",
      "name": "Driver Display Name",
      "namespace": "author.namespace",
      "location": "https://raw.githubusercontent.com/owner/repo/main/drivers/xyz/xyz.groovy",
      "required": false,
      "primary": true
    }
  ]
}
```

### Rules

1. **UUID must match the per-driver manifest exactly.** HPM Match-Up identifies components by `id + name + namespace`. Mismatched UUIDs create duplicate tracked components.
2. **Never include per-entry `version` fields in a bundle manifest.** HPM does not expect per-driver versioning inside a bundle. Version at the top level only.
3. **`required: false` for all non-essential drivers** — lets the user choose which drivers to install. Set `required: true` only for a driver that every user will need.
4. **`primary: true` on the first/main driver, `false` on child device handlers** (if you include component drivers).

## Version Coupling Convention

| Manifest | Versioning |
|----------|------------|
| `drivers/*/packageManifest.json` | Mirrors driver version (e.g., `0.1.20`) |
| Root `packageManifest.json` | Independent `major.minor.patch` (e.g., `1.0.1`) |
| When? | Bump bundle patch on any per-driver ship; bump minor when adding a new driver to the bundle |

## CI/CD: release.yml Updates

When using a workflow that auto-creates GitHub releases from `packageManifest.json` files, three changes are needed:

### 1. Add root manifest to push trigger

```yaml
on:
  push:
    paths:
      - 'packageManifest.json'            # ← add this
      - 'drivers/**/packageManifest.json'
```

### 2. Update `find` to scan repo root

```bash
# Before
find drivers -type f -name 'packageManifest.json'

# After
{ find . -maxdepth 1 -name 'packageManifest.json'; find drivers -type f -name 'packageManifest.json'; } | sort
```

### 3. Handle root manifest separately (driver_dir == ".")

When `find` returns `./packageManifest.json`, the derived `driver_dir` will be `"."`. The default tag/slug derivation (based on `basename`) breaks. Add an explicit branch:

```bash
driver_dir=$(dirname "$manifest")

if [ "$driver_dir" = "." ]; then
  version=$(jq -r '.version' "$manifest")
  tag="bundle-v${version}"
  notes="Bundle version ${version}: see individual driver changelogs."
  # Create release directly — skip changelog extraction
  gh release create "$tag" --title "Bundle v${version}" --notes "$notes" || true
  continue
fi

# Normal per-driver path below...
slug=$(basename "$driver_dir")
tag="${slug}-v${version}"
```

## User-Facing Documentation

Add to repo `README.md`:
```markdown
### Install all drivers (HPM bundle)

Paste this URL in Hubitat Package Manager → Install → From URL:
https://raw.githubusercontent.com/owner/repo/main/packageManifest.json
Select which drivers to install.

> **Note:** If you already installed a driver via a per-driver URL, do not install it again from the bundle — use only one path to avoid duplicate update notifications.
```

## Gotchas

- HPM duplicate-detection when mixing bundle and per-driver install: largely untested. Advise users to pick one install method.
- If `packageName` in the bundle differs from any per-driver manifest `packageName`, HPM may display them as distinct packages. Keep names consistent if you want a seamless upgrade path.
- Bundle releases need a different tag prefix (e.g., `bundle-v1.0.1`) to avoid collisions with per-driver tags (e.g., `touchstone-v0.1.20`).
