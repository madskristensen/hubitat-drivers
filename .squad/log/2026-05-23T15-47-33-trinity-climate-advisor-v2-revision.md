# Orchestration Log: Trinity — Climate Advisor v2 Revision

**Date:** 2026-05-23T15:47:33-07:00  
**Agent:** Trinity  
**Task:** Revise Climate Advisor architecture to be generic and shareable  
**Status:** Complete (Decision merged, awaiting Tank implementation)

## Summary

Trinity revised the Climate Advisor architecture from v1 (hardcoded zones and devices) to v2 (fully user-configurable, generic for HPM distribution).

**Key changes:**
- Dropped `ContactSensor` capability — HomeKit no longer a requirement; removed custom string attribute assumption
- All zone configuration moved to app preferences (8 zones, user-selectable up to zoneCount)
- All devices (thermostats, contacts, weather, AQI, speakers) now selected via capability-typed inputs
- Maintained parent app + child virtual device architecture
- Maintained rich custom attributes (severity, severityText, latestMessage, messages, houseStatus, tempTrend, activeAlertCount)

**Two directives captured:**
1. Climate Advisor must be generic & shareable; HomeKit not required
2. Use main page + per-zone sub-pages (href-linked), not single-page dynamic sections

**v2 Decision entry:** `.squad/decisions/decisions.md` "Architecture Proposal: Climate Advisor — v2 (Generic, SharpTools-first) — SUPERSEDES v1"

**Next step:** Tank implements app and driver per v2 specification. Implementation criteria in decisions.md § 3–7.

---

## Files Modified

- `.squad/decisions/decisions.md` — merged inbox files, marked v1 as superseded

## Files Removed (Inbox)

- `.squad/decisions/inbox/trinity-climate-advisor-architecture-v2.md`
- `.squad/decisions/inbox/copilot-directive-20260523T154428.md`
- `.squad/decisions/inbox/copilot-directive-zone-ux-20260523T154808.md`

---

## Artifact Outputs

Trinity inbox artifact: `trinity-climate-advisor-architecture-v2.md` (now merged into decisions.md)
