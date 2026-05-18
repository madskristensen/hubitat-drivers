# Cypher — Integration / Protocol Engineer

**⚠️ SUMMARIZED 2026-05-18T11:55:00Z — Main history archived to `history-archive.md` (updated file size check).**

---

## Current Active Work (2026-05-18)

### HPM Multi-Driver Bundle Feasibility Research (Session cypher-4)
- **Shipped:** 2026-05-18
- **Verdict:** ✅ Feasible — HPM natively supports multiple drivers in one manifest
- **Key Findings:**
  - In-repo precedent: SunStat already ships 2-driver manifest (parent + child)
  - Bundle manifest goes at repo root; per-driver manifests remain unchanged (additive)
  - UUID reuse mandatory for HPM Match-Up deduplication
  - Version coupling: bundle version independent of per-driver versions
  - Release workflow needs update: add root manifest path trigger, handle `driver_dir == "."` case

### Tuya Autodiscovery on Hubitat Feasibility Research (Session cypher-4)
- **Shipped:** 2026-05-18
- **Verdict:** ⚠️ Feasible-with-caveats — passive UDP blocked, but active TCP Plan B viable
- **Key Findings:**
  - ❌ Passive UDP broadcast listening: NOT supported by Hubitat (staff confirmed 2018, unchanged)
  - ✅ Active TCP probe: feasible via sequential rawSocket.connect() on /24 subnet port 6668
  - Tuya v3.3 discovery via active TCP: DP_QUERY frame + gwId match (fail-closed)
  - Scan time: 2s/IP worst case → ~8 min full sweep; smart ±20 range typically <1 min
  - Fallback (primary recommendation): DHCP reservation in router (zero driver code)

### Gemstone Zones / Segments — API Feasibility Research (Session cypher-3)
- **Shipped:** 2026-05-17 
- **Verdict:** ✅ Feasible via multi-instance + controllerName preference (Option A-lite)

## Key Findings From Tank-15 Support

Cypher-4 research directly enabled two Tank-15 ships:
1. **HPM Bundle v1.0.0:** Research validated manifest schema, UUID reuse requirement, release workflow changes
2. **Touchstone v0.1.20 Discovery:** Research confirmed active TCP approach as only viable Hubitat path for Tuya autodiscovery

---

## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.
- 2026-05-18: Daikin WiFi research memo (`daikin-driver-assessment.md`) was used as direct input for Tank-2's clean-room driver implementation (commit b26c04f). Clean-room approach proved the feasibility of independent authorship using only research prose, no upstream source code.

---

## Archive

**Older sessions (2026-05-16 to 2026-05-17):** SunStat/Watts research, Bosch feasibility, Gemstone color investigation, Touchstone DP analysis, Tuya key extraction audit, Hubitat sandbox learnings, system.arraycopy blocker, and cross-driver patterns saved to `history-archive.md`.
