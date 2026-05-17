# ARCHIVE SUMMARY — Cypher Project History

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
*Archive created 2026-05-17 — full history > 15KB; moved to history-archive.md*
