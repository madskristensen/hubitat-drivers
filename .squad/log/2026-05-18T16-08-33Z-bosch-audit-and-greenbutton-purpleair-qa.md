# Session Log: Bosch Home Connect Audit + Green Button/PurpleAir Q&A

**Date:** 2026-05-18  
**Time:** 16:08:33-07:00  
**Topic:** Bosch Home Connect existing-driver audit; Green Button + PurpleAir hyperlocal-AQI Q&A  
**Coordinator Mode:** Direct (no agent required for Green Button/PurpleAir — factual explanations only)

---

## Main Task: Bosch Home Connect Audit

**Agent:** cypher-3 (completed background)  
**Verdict:** Install craigde/hubitat-homeconnect-v3 community driver  
**Highlights:**
- HPM-published, 13 appliance types, fridge driver covers Mads's use case
- OAuth Authorization Code Grant works via Hubitat App + cloud callback — pattern reusable
- SSE reliability proven post-March 2026 (watchdog + reconnect logic)
- Rubric 67/100 for BUILD-NEW; INSTALL verdict trumps rubric

---

## Sidebar: Green Button Connect (Asked by Mads)

**Status:** PSE (Pacific Gas & Electric's ESI program) supports Green Button download + API  
**ESPI lag:** 24–48 hours from meter to Green Button data  
**Use case fit:** Dashboards, historical analysis only; **NOT real-time alerts**  
**Action:** Candidate for follow-up audit if Mads wants real-time consumption data (would require sub-hour polling or webhook integration, adding complexity)

---

## Sidebar: PurpleAir Hyperlocal Air Quality (Asked by Mads)

**Status:** PurpleAir exposes outdoor AQI hyperlocal (block-level resolution)  
**API:** Available; indoor/outdoor sensor options  
**Use case fit:** Composable Rule Machine rules (e.g., "if AQI > 150, pause HVAC + notify")  
**Action:** Candidate for follow-up audit if Mads wants to add AQI-based automation rules

---

## Artifacts

- `.squad/orchestration-log/2026-05-18T16-08-33Z-cypher-3.md` (NEW)
- `.squad/log/2026-05-18T16-08-33Z-bosch-audit-and-greenbutton-purpleair-qa.md` (NEW — this file)
- `.squad/decisions.md` (MERGED inbox)
- `.squad/agents/cypher/history.md` (APPENDED)
- `.squad/agents/tank/history.md` (APPENDED)
- `.squad/agents/trinity/history.md` (APPENDED)

---

## Next Steps

1. Test craigde driver on Mads's fridge (HPM install, Bosch OAuth setup, door-open Rule Machine rule)
2. Monitor SSE stability; log any reconnection events
3. If issues arise, escalate to craigde or fork
4. Follow up on Green Button / PurpleAir if Mads wants automation layers

**Status:** COMPLETE — all three (Bosch audit, Green Button Q&A, PurpleAir Q&A) addressed. Ready for Mads to execute install.
