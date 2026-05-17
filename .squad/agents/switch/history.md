# Switch — QA / Testing Engineer

Recent contributions documented in history-archive.md. Current session: SunStat v0.1.4 test design.

- 2026-05-17T04:44:01Z: Gemstone v0.4.1 — Added 8 test cases for playEffectByName command (Tests 11–18); shipped v0.4.1 cross-team

- 2026-05-17T03:37:53Z: SunStat v0.1.2 test coverage expanded; 23 new test cases for energy/schedule/hold/outdoor/precision/bounds


---

## 2026-05-17T16:31:55Z — Bosch Home Connect Scoping (Cypher + Trinity)

**Topic:** bosch-home-connect-feasibility

Scoping discussion completed. Implementation will follow once Tank builds parent App + driver.

**ACTION FOR SWITCH:** When spawned to test, validate on real Bosch fridge:
1. **Door enum namespace:** Does appliance return BSH.Common.EnumType.DoorState.Open or Refrigeration.Common.EnumType.Door.States.Open for door status?
2. **Alarm timing:** How long before appliance fires door-open alarm? (Cypher's research suggests ~3 min, but model-dependent.)
3. **Multi-door support:** If appliance has Refrigerator2/Freezer, confirm status keys are returned.

See .squad/decisions/decisions.md section 8 (Open Questions for Switch) for full test plan.
