# Cypher — Integration / Protocol Engineer

**Status:** Audit cycle 2026-05-18 complete. PurpleAir INSTALL verdict (88/100) — 5th consecutive install verdict in a row. Driver-opportunity shortlist + Trinity rubric established. OAuth callback retrofit governance principle: not applicable without public OAuth registration portal.

---

## Audit Verdicts — 2026-05-18 Complete Cycle

### PurpleAir AQI Virtual Sensor (pfmiller0/Hubitat)

**Verdict:** INSTALL (88/100 Trinity rubric)  
**Date:** 2026-05-18T16:25:00-07:00  
**File:** .squad/decisions/inbox/cypher-purpleair-pfmiller-audit.md → merged into decisions.md

**Key Findings:**
- **Protocol:** Cloud REST on `api.purpleair.com/v1/sensors`
- **Auth:** `X-API-Key` header (free API key, no registration required for read-only)
- **Sensor modes:** Geolocation-based averaging OR explicit sensor index (neighbor's sensor)
- **EPA AQI:** Full Barkjohn 2021 conversion (wildfire smoke correction)
- **Sandbox:** `asynchttpGet` only; fully safe
- **Test requirement:** No hardware (any public sensor or hub location)
- **Last commit:** 2025-06-18 (responsive to API changes)

**Scoring Details:**
| Dimension | Score | Rationale |
|-----------|-------|-----------|
| Local vs. cloud | 10 | Cloud REST, stable API |
| Mads can test | 15 | No hardware; any public sensor by ID or hub geolocation |
| User demand | 15 | PNW wildfire season monitoring + neighbor sensor use case |
| Sandbox-safe | 15 | `asynchttpGet` + headers only, no crypto/reflection |
| Vendor stability | 15 | PurpleAir v1 API documented, API key monetized (stable incentive) |
| Effort | 10 | Zero — copy via importUrl or HPM |
| Maintenance | 8 | Last commit ~11 months ago; responsive but not 2026-active |
| **Total** | **88/100** | **Strong Fit → INSTALL** |

**Impact:** Closes the cloud-API gap for air quality monitoring. This is the **5th consecutive "install existing community driver" verdict**.

**Audit lesson:** Prior search for "PurpleAir Hubitat cloud driver" returned zero results. 
- Generic repo names (`pfmiller0/Hubitat`) don't surface well in search
- File names don't match keywords ("AQI Virtual Sensor" vs. "api.purpleair")
- User-pointed URLs override search conclusions (this driver was found via Mads's direct link)

**Recommendation:** When repo search returns zero, follow with GitHub code search (`code:"api.purpleair.com" lang:groovy`) before claiming greenfield.

---

### Five-Verdict Summary

1. ✅ **Bosch Home Connect** (2026-05-17) → INSTALL craigde/hubitat-homeconnect-v3 (67/100)
2. ✅ **Rainbird LNK** (2026-05-17) → IMPROVE-EXISTING MHedish (92/100)
3. ✅ **PurpleAir Cloud-API** (2026-05-18) → INSTALL pfmiller0 (88/100)
4. ✅ **MyQ Ecosystem** (2026-05-16) → BUILD ratgdo ESPHome HTTP (greenfield)
5. ✅ **Daikin BRP069B** (2026-05-16) → BUILD complete (v0.1.0–v0.1.7 shipped)

**Result:** Mads's complete driver stack now has **zero open BUILD candidates**.

---

## Updated Team Updates

**Archive restoration (2026-05-18T23:06:44-07:00):** Scribe corrected the archive-gate misfire from commit 06f8a35. Size threshold triggered archival without verifying date eligibility — entries dated 2026-05-16 to 2026-05-18 were moved despite the "older than 7 days" rule. All 8 entries restored to decisions.md; archive cleared to placeholder.

**Canonical ledger:** decisions.md now has full session context (Bosch spec, consumer auth analysis, driver rubric, opportunity survey, PurpleAir audit). Size: 24 KB → ~92 KB.

---

## Learnings from Audit Cycle

**Search strategy refinement:** Code-level search (`code:"string" lang:groovy`) catches file-name mismatches that repo-level search misses. For next audit: always code-search if repo search returns zero.

**User-pointed URLs as primary:** When a user provides a direct GitHub URL, treat it as primary evidence. Override prior search-based conclusions.

**Platform constraint documentation:** OAuth callback pattern is only applicable if vendor provides public OAuth Authorization Server + public client registration. When vendor offers none (Gemstone, SunStat), pattern is not applicable. Governance principle established.
