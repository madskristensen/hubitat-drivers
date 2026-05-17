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

---

## 2026-05-17T16:53:47Z — Touchstone LED Fireplace Tuya Feasibility (Cypher + Trinity)

**Topic:** touchstone-fireplace-fireplace-feasibility

Feasibility pass completed. Real-device validation phase incoming.

**Device:** Touchstone Sideline LED fireplace (Tuya-based; WiFi)  
**Control:** Tuya Local (LAN) over rawSocket + AES-128-ECB  
**Effort:** Medium (2–3 sessions)

**ACTION FOR SWITCH:**

Real-device validation will require Mads to run tinytuya tooling against his actual fireplace unit. Plan the following test tasks:

1. **Model confirmation** — Label check + tinytuya scan to verify Sideline vs other Touchstone variants
2. **Protocol version** — Confirm v3.3 vs v3.4/v3.5 (determines framing complexity)
3. **Full DP map** — Run tinytuya wizard/scan; confirm DP IDs match assumptions (especially DP 101 = flame color, DP 104 = ember/log color)
4. **Local connectivity test** — After key extraction, run tinytuya connectivity test to confirm local LAN control works before Tank writes Groovy code
5. **Connection limit** — Test whether Tuya module allows dual TCP connections (simultaneous mobile app + Hubitat driver)

See `.squad/orchestration-log/2026-05-17T165347Z-cypher.md` for full list of open questions (section 5).

**Key learning:** Palette-indexed colors (not RGB) simplify the DP layer but require named commands for honest UX. No ColorControl capability. Trinity's architecture is sound but capability mapping was corrected.
