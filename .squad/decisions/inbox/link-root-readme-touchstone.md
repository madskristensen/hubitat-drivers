# Decision — Link: Root README Updated for Touchstone v0.1.5 Public Release

**Date:** 2026-05-17T13:21:30-07:00  
**Agent:** Link (DevRel / Documentation)  
**Context:** Touchstone / Tuya Fireplace v0.1.5 shipped and is now ready for first public release. Root README needed to reflect third driver alongside Gemstone Lights and SunStat Connect Plus.

---

## Five-Point Edit Pattern Applied

### 1. Status Line (Line 5)

**Old:**
> Two beta drivers available — **Gemstone Lights** v0.4.1 (working in production, author-tested) and **SunStat Connect Plus** v0.1.4 (cloud scaffold, energy monitoring, schedule control, and hold detection now available; token bootstrap via `setRefreshToken` command; v0.1.4 fixes discoverDevices auto-resolution and locationId URL encoding).

**New:**
> Three beta drivers available — **Gemstone Lights** v0.4.8 (cloud REST, working in production), **SunStat Connect Plus** v0.1.4 (cloud REST, energy monitoring and schedule control), and **Touchstone / Tuya Fireplace** v0.1.5 (LAN integration, now ready for first public release).

**Rationale:** Increment from "two" to "three". Update Gemstone from v0.4.1 (stale) to v0.4.8 (verified from current packageManifest.json). Add Touchstone v0.1.5 with integration-type framing (LAN, now ready for release).

---

### 2. Drivers Table (Lines 22–26)

**Added Row:**
```
| **Touchstone / Tuya Fireplace** | Beta | LAN integration for Touchstone Sideline Elite and other Tuya WiFi fireplaces. Flame color, log color, brightness, heater control. Includes in-driver DP discovery for unmapped models. | [Driver README](drivers/touchstone-fireplace/README.md) |
```

**Rationale:** Three-column factual summary: device support (Sideline Elite + Tuya WiFi fireplaces), key features (flame/log color, brightness, heater), and technical edge (DP discovery for unmapped models). Consistent with Gemstone/SunStat row structure.

---

### 3. Manual Install Section (Lines 28–35)

**Old:** Step 3 hardcoded to Gemstone path (`drivers/gemstone-lights/gemstone-lights.groovy`), step 4 hardcoded to Gemstone device type (`Gemstone Lights`).

**New:** Steps 3–4 generalized with "(e.g., `drivers/gemstone-lights/...`, `drivers/touchstone-fireplace/...`, etc.)" pattern. Step 5 now references per-driver README for Preferences guidance rather than assuming email/password pattern.

**Rationale:** Manual install is boilerplate applicable to all drivers; generalization reduces maintenance burden and makes section reusable for future drivers without edit. Removed Gemstone-specific assumption (email/password) in favor of generic "Preferences" → "per-driver README" pattern.

---

### 4. Driver Compatibility Details (Lines 43–46)

**Added Line:**
```
- **Touchstone / Tuya Fireplace** — Requires LAN reachability to the Tuya WiFi module; needs the device's Tuya local key (obtained once via `tinytuya wizard`)
```

**Rationale:** Explicit network requirement (LAN reachability vs. cloud HTTPS) and bootstrap method (one-time `tinytuya wizard` setup). Users can quickly assess whether driver is viable for their network.

---

### 5. HPM URLs Section (Lines 7–14)

**Old:** Single example URL for Gemstone only.

**New:** Bulleted list of all three driver packageManifest URLs.

**Rationale:** For multi-driver project, listing all URLs upfront is clearer than making users search for or infer URLs. Reduces friction for users who want to install Touchstone without first reading per-driver README.

---

## Version Verification

All versions verified from packageManifest.json (source of truth):
- **Gemstone Lights:** 0.4.8 (manifest last updated 2026-05-16; old README stated 0.4.1 — corrected)
- **SunStat Connect Plus:** 0.1.4 (consistent with old README)
- **Touchstone / Tuya Fireplace:** 0.1.5 (just released; manifest dated 2026-05-17)

---

## Pattern Artifact

**Five-point checklist for adding driver N+1 to root README:**
1. ✅ Status line: Increment count, add new driver + version, brief what's-new framing
2. ✅ Drivers table: Add row with status, description, link to per-driver README
3. ✅ Manual install: Generalize with "(e.g., ...)" pattern; remove device-specific assumptions
4. ✅ Compatibility details: Add network/bootstrap requirements for new driver
5. ✅ HPM URLs: Decide: single example vs. list (recommend list for 3+ drivers)

This pattern is now established and documented in `.squad/agents/link/history.md` for future reference.

---

## No Breaking Changes

- Existing Gemstone and SunStat rows unchanged (structure preserved)
- "Listed in the community HPM master list" section untouched (still references PR #106)
- All per-driver READMEs and packageManifest.json files unchanged
- Version bump to Gemstone (0.4.1 → 0.4.8) is a correction of stale README text, not a code change
