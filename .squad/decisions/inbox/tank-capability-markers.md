## 2026-05-18 — Tank — Cloud driver metadata hygiene

### What changed
- In `drivers/gemstone-lights/gemstone-lights.groovy`, added `capability "Polling"` to the metadata definition and bumped the driver + `drivers/gemstone-lights/packageManifest.json` to `0.4.16`.
- In `drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy`, added `capability "Polling"` and `capability "Actuator"` to the parent metadata definition and bumped the parent to `0.1.11`.
- Synced `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy` and `drivers/sunstat-thermostat/packageManifest.json` to `0.1.11` so the SunStat release stays parent/child aligned.

### Why
- Both cloud drivers already implement `poll()`, but Hubitat app discovery keys off declared capabilities. Adding `Polling` advertises the existing command without changing behavior.
- The SunStat parent exposes commands (`discoverDevices`, `setHome`, `setAway`, `setAwayMode`, `setRefreshToken`) and should advertise `Actuator` as the marker that it accepts commands. This closes the final perf/quality todo from Tank's 2026-05-18 board.

### Guardrails kept
- No explicit `command "poll"` declarations were added; `capability "Polling"` already defines that contract.
- `capability "Actuator"` is treated as marker-only; no duplicate commands were introduced.
- SunStat child behavior is unchanged in `0.1.11`; the child bump is version-sync only.
