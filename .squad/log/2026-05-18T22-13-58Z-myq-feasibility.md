# Session Log: MyQ Garage Door Driver Feasibility

**Date:** 2026-05-18T22:13:58Z  
**Topic:** MyQ garage door driver feasibility investigation  
**Participants:** Cypher (background), Trinity (background)  
**Status:** Complete

---

## Summary

Cypher + Trinity investigated whether a Hubitat MyQ driver is feasible. **Verdict: build ratgdo ESPHome HTTP driver.** Cloud MyQ is permanently dead (Chamberlain blocked Oct 2023 with hostile enforcement). Recommended path is local ratgdo/Konnected blaQ hardware using REST HTTP polling. Architecture sketch and full feasibility report filed. Awaiting Mads's answer on hardware ownership before any Groovy code is written.

---

## Key Findings

1. **Cloud MyQ:** Permanently closed to third parties. No public developer program. Community workarounds (pymyq, homebridge-myq) abandoned. API breakage guaranteed if attempted.

2. **Local Hardware:** ratgdo ESPHome (active, April 2026) + Konnected blaQ (May 2026) both expose stable documented REST API on port 80. Protocol-identical. Hubitat-compatible via asynchttpGet polling.

3. **Driver Architecture:** GarageDoorControl + ContactSensor + Switch (light child optional) + HealthCheck. Single-device driver for ratgdo; no parent/child needed. Garage-door-specific safety: audit trail (INFO logs), no auto-close timer, rate-limiting on close/open, obstruction exposure.

---

## Decisions Logged

- `.squad/decisions.md` → cypher-myq-feasibility + trinity-myq-driver-architecture (merged from inbox)

## Orchestration Logs

- `.squad/orchestration-log/2026-05-18T22-13-58Z-cypher.md`
- `.squad/orchestration-log/2026-05-18T22-13-58Z-trinity.md`

---

## Next Steps

1. Mads: Confirm hardware ownership (MyQ opener vs. ratgdo vs. Konnected)
2. If green-light: Tank builds ratgdo-garage driver
3. Architecture sketch serves as implementation blueprint
