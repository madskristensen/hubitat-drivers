---
agent: Link
date: 2026-05-16T23:14:20Z
topic: v0.4.0 Release & HPM Community List Submission
status: completed
---

# v0.4.0 Release Execution — Complete

## Authorization
One-time push approval from Mads for:
- Push commit 6f2f85e to origin/main
- Trigger release.yml workflow
- Fork HubitatCommunity/hubitat-packagerepositories and submit community PR

## Execution Timeline

### Step 1: Sanity Check (Passed)
- Commit 6f2f85e ahead of origin/main ✅
- Working tree clean ✅
- Diff shows ~100+ files (Squad state + release infra) ✅

### Step 2: Push to GitHub
```
git push origin main
Result: b2ba84e..6f2f85e main -> main
SHA verified: 6f2f85e65c43e6eb7a2165383a70cdba37d4e156
```

### Step 3: Release Workflow
```
gh workflow run release.yml
Run ID: 25978959810
Status: ✓ Completed (success)
```

Release created:
- **Tag:** gemstone-lights-v0.4.0
- **URL:** https://github.com/madskristensen/hubitat-drivers/releases/tag/gemstone-lights-v0.4.0
- **Body:** Auto-populated from driver Changelog v0.4.0 entry
  > "Added LightEffects, ColorTemperature, and colorMode support. Favorites now surface first..."

### Step 4: Community List PR
```
Fork: HubitatCommunity/hubitat-packagerepositories (madskristensen/hubitat-packagerepositories)
Branch: add-madskristensen-hubitat-drivers
Commit: b96f92d
Diff: 4 insertions (only the new entry)
PR: #106
```

**Repository Entry Added:**
```json
{
  "name": "Mads Kristensen",
  "location": "https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/repository.json"
}
```

**PR URL:** https://github.com/HubitatCommunity/hubitat-packagerepositories/pull/106

## Key Decisions Made

1. **JSON Edit Strategy:** PowerShell's `ConvertFrom-Json | ConvertTo-Json` reformats the entire file (spaces instead of tabs). Used surgical text replacement via regex to preserve file format and keep diff clean.

2. **Release Trigger:** Workflow dispatch worked immediately after push; no re-registration delay observed.

3. **Repository Layout:** Driver publishes repository.json at root level; community list entry points directly to that.

## Results

✅ **Commit landed on main**
✅ **Release created and tagged**
✅ **Community PR submitted**
✅ **Awaiting maintainer review**

### Install URLs (Live)
- **HPM-friendly manifest:** https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/repository.json
- **Direct driver manifest:** https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json

Users can install immediately via HPM once the community PR merges.

## Blockers / Follow-up
- HubitatCommunity maintainer must merge PR #106 for inclusion in master list
- Driver is ready for public use (v0.4.0, all features complete per Tank)
