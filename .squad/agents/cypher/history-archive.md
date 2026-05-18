# ARCHIVE SUMMARY — Cypher Project History

## 2026-05-18 Reskill — 3-Audit Cycle Consolidation

**Sessions archived:**
- cypher-3: Gemstone Zones / Segments API Feasibility (shipped 2026-05-17)
- cypher-4: HPM Bundle Manifest + Tuya Autodiscovery research (shipped 2026-05-18)

**Ephemeral learnings trimmed:**
- HPM multi-driver bundle research findings (shipped to Tank)
- Tuya autodiscovery feasibility analysis (shipped to Touchstone driver)

**Skills established this reskill:**
- NEW: hubitat-sentinel-value-guards (HIGH confidence — Daikin "-" sentinel pattern)
- NEW: hubitat-driver-perf-audit-checklist (LOW confidence — systematic audit methodology)
- NEW: hubitat-upstream-feature-gap-pattern (LOW confidence — clean-room feature discipline)
- NEW: daikin-brp069b-endpoint-catalog (MEDIUM confidence — all 28 endpoints, project-specific)

---

## Key Contributions (2026-05-16 onwards)

**Gemstone Lights Protocol Research:**
- Full AWS Amplify cloud API documented (Cognito SRP, REST endpoints)
- Determined local API not publicly documented; requires mitmproxy/packet capture
- Blocked waiting for Mads' UniFi packet capture at .squad/research/gemstone-capture.pcap

**SunStat Connect Plus / Watts Home API Research:**
- Identified identical API between SunStat and Tekmar WiFi (both Watts Water Technologies)
- Full Azure AD B2C auth spec (MSAL, token refresh simplified vs Cognito SRP)
- Documented device data model, endpoints, and token flow
- Recommended user-driven initial login via homebridge-tekmar-wifi CLI (external tool)
- Token refresh fully feasible in Hubitat

**v0.1.4 SunStat Bug Diagnosis (2026-05-17T04:24Z):**
- Root cause identified: Watts API wraps responses in {errorNumber, errorMessage, body: T}
- Driver never unwrapped .body, causing all GET endpoints to return empty
- Specified 6 concrete code changes for Tank implementation
- Shipped drivers/sunstat-thermostat/scripts/get-location-id.ps1 bootstrap helper
- Decision memo (.squad/decisions/inbox/cypher-sunstat-location-id-discovery-fix.md) ready for Tank

## Learnings Recorded

- Gemstone/Watts discrepancy: Different cloud stacks (AWS vs Azure AD B2C)
- API response envelope patterns: Every Watts endpoint wraps in ApiResponse<T>
- Token lifecycle differences: Watts tokens rotate on refresh; Azure AD B2C-specific handling
- Local discovery blocked by lack of published specifications

## Status

- Gemstone: Awaiting packet capture; cloud API complete
- SunStat: v0.1.4 diagnosis complete; Tank to implement fixes

---
*Archive updated 2026-05-18 — reskill after 3-audit cycle consolidation.*
