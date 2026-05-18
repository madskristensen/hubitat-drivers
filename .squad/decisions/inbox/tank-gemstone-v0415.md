## 2026-05-18 — Tank — Gemstone v0.4.15 (perf todos #3/#4)

### What changed
- In `drivers/gemstone-lights/gemstone-lights.groovy`, `cloneMap()` no longer round-trips through JSON. It now recursively clones only mutable containers (`Map`, `List`) and reuses scalar values, which matches the real hot-path shapes used by Gemstone patterns, queued requests, callback data, and cached effect patterns.
- Added refresh-only event dedupe helpers and routed `handleRefreshResponse()` switch/level/hue/saturation emits through them so unchanged poll payloads stop creating duplicate Events rows.
- Left the existing command-path `sendEvent(..., type: "digital")` behavior alone for `on/off/setLevel/setColor/setColorTemperature` and effect activation; only refresh telemetry now skips unchanged emits.
- Bumped Gemstone metadata/changelog to `0.4.15`, updated `drivers/gemstone-lights/packageManifest.json` to `0.4.15`, and captured the reusable copy-hygiene rule in `.squad/skills/hubitat-hot-path-copy-hygiene/SKILL.md`.

### Why
- Both requested items sat on Gemstone's hot paths. Removing JSON serialization from internal map copies cuts avoidable CPU/GC work during refreshes, retries, queueing, and effect activation while still isolating mutable nested structures from `state`.
- Dedupe on the refresh/poll path keeps Hubitat's event history stat-oriented instead of echoing identical cloud telemetry, without taking away the immediate digital confirmation users expect after a real command.
