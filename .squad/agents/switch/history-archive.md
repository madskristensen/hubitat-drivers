# ARCHIVE SUMMARY — Switch Project History

## Key Contributions (2026-05-16 onwards)

**Gemstone Lights Test Planning:**
- Test plan structure designed for LAN driver validation
- Learnings captured: optimistic updates + polling reconciliation pattern standard for LAN drivers
- Test plan designed but blocked pending Tank's HTTP endpoint wiring (awaits Cypher's protocol discovery)
- Test plan reusable for future LAN light drivers (brightness, effects, polling reconciliation)

**SunStat Connect Plus Test Coverage:**
- **v0.1.0 smoke tests:** 25 core test cases (discovery, modes, setpoints, tile integration)
- **v0.1.2 feature coverage:** Added 23 test cases (#26–#48) covering energy, schedule, hold, outdoor sensor, precision rounding, bounds clamping
- **v0.1.3 setRefreshToken:** Added 13 test cases (#59–#71) covering command args, input validation, token persistence, hub reboot recovery, code review checks
- **v0.1.4 envelope + URL encoding:** Added 16 test cases (#72–#87) covering API envelope unwrap, URL path encoding, discovery logging, end-to-end scenarios

### Learnings Recorded

- Hubitat drivers tested manually (no Jest/RSpec), via device UI + IDE Logs
- Parent-to-child attribute mirroring has acceptable lag (one poll cycle)
- Optimistic state + reconciliation is standard pattern for all network drivers
- Cloud drivers must expect transient failures; test offline graceful degradation
- Token command args bypass ~1024-char preference limit
- Energy attributes require temporal state tracking; tolerances ±0.5 kWh
- Bounds clamping + warning logs is safe validation pattern

## Status

- Gemstone: Test plan structurally ready; blocked pending protocol discovery
- SunStat: 71 total test cases across 4 versions; v0.1.4 tests ready for Mads' device verification

---
*Archive created 2026-05-17 — full history > 15KB; moved to history-archive.md*
