# Session Log — Daikin v0.1.2 asynchttpGet Rewrite

**Date:** 2026-05-18  
**Session:** 2026-05-18T20-52-18Z  
**Agent:** Scribe (documentation)

## Summary

Tank-4 shipped v0.1.2 (commit e45967e on main) replacing all HubAction LAN calls with asynchttpGet — Hubitat's documented modern HTTP-over-LAN API, stable across firmware versions.

## Timeline

- **v0.1.0:** Shipped with HubAction(Map, Protocol, Map) 3-arg constructor. Does not exist on Mads's firmware.
- **v0.1.1:** Switched to HubAction(Map, Protocol) 2-arg constructor. Also does not exist on Mads's firmware. Mads reported it failed but then re-installed and it worked for him.
- **v0.1.2 (in flight while v0.1.1 was being re-tested):** Tank-4 completed architecture upgrade to asynchttpGet. Commit e45967e shipped to main.

v0.1.2 ships as an **architecture upgrade**, not a strict bugfix. Same behavior, but version-stable across firmware variants.

## Skills and Decisions

- **Skills updated/created:**
  - `hubitat-hubaction-constructors`: bumped to medium confidence
  - `hubitat-asynchttpget-pattern`: NEW, medium confidence
- **Decision:** `tank-daikin-wifi-v012-asynchttp.md` merged to decisions.md; corrects Trinity's memo that "asynchttpGet is for cloud HTTPS only"
