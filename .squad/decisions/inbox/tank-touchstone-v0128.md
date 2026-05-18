## 2026-05-18 — Tank — Touchstone v0.1.28 (perf todos #2/#4)

### What changed
- In `drivers/touchstone-fireplace/touchstone-fireplace.groovy`, `parse()` now builds the concatenated receive buffer locally and passes it into `consumeReceiveBuffer(buffer)`; only leftover partial-frame hex is written back to `state.rxBuffer`, and the state key is removed when the chunk was fully consumed.
- Added parse-only event dedupe helpers and routed parsed `heatLevel`, `flameColor`, `flameBrightness`, `charcoalColor`, `flameSpeed`, `heatingSetpoint`, and `temperature` updates through them so unchanged push/refresh frames stop creating duplicate Events rows.
- Left the existing command-path `emitAttribute(..., "digital")` behavior intact, so user-issued writes still produce immediate digital echoes after a real outbound DP write.
- Bumped the Touchstone driver metadata/changelog to `0.1.28`, updated `drivers/touchstone-fireplace/packageManifest.json` to `0.1.28`, and documented the reusable parse-path dedupe rule in `.squad/skills/hubitat-event-hygiene/SKILL.md`.

### Why
- Both requested perf items were on the hot inbound socket path. Avoiding full-buffer state writes and unchanged parse events cuts Hubitat state/database churn on every heartbeat, refresh, and physical-remote push without changing Tuya frame parsing behavior.
- Separating parse dedupe from command echoes preserves UX: automations still get an immediate digital confirmation when they actually changed device state, but the later device echo no longer clutters event history with redundant rows.
