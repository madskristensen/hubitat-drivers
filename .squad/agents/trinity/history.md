# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation home automation hubs. First driver: Gemstone Lights (permanent outdoor LED lighting) via local HTTP API. Folder must accommodate additional drivers over time.
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs (`hubitat.device.HubAction`, `parseLanMessage`, `runIn`, etc.), HTTP over LAN
- **Created:** 2026-05-16

## Learnings

<!-- Append new learnings below. Each entry is something lasting about the project. -->

### 2026-05-16 — Repo layout, conventions, and Gemstone driver architecture

**Folder structure (decided):**
- Top-level `drivers/` (lowercase), one `kebab-case` subfolder per driver.
- Gemstone driver path: `drivers/gemstone-lights/gemstone-lights.groovy`.
- Each subfolder holds: `.groovy` file, `README.md`, `packageManifest.json`.
- Repo root holds: `README.md`, `LICENSE`, `.gitignore`, and `repository.json` (HPM author index — add when first driver is HPM-published).

**HPM packaging (discovered):**
- Two-level HPM system: per-driver `packageManifest.json` in the driver subfolder + repo-root `repository.json` (author index).
- `id` field in `packageManifest.json` must be a UUID v4, generated once, never changed — HPM tracks installs by it.
- `location` URLs must use `raw.githubusercontent.com`, not `github.com/blob/`.
- Driver `namespace` for this repo: `"mads"` (consistent across all drivers).
- `minimumHEVersion`: `"2.3.0"` baseline for C-7/C-8.

**Community conventions surveyed (repos):**
- `jshimota01/hubitat` — best example of per-driver subfolder + HPM manifest pattern
- `bptworld/Hubitat` — large multi-driver repo, Title Case subfolders, not all drivers HPM-published
- `dcmeglio/hubitat-bond` — single-package repo with root-level manifest; flat `drivers/` folder
- `hubitat/HubitatPublic` — official examples, flat structure, no HPM

**File naming (decided):**
- Driver `.groovy` files: `kebab-case` matching the folder name. No version number in filename — version lives in `definition` block and `packageManifest.json`.

**Gemstone driver design (decided):**
- Single driver (no parent/child) until protocol research confirms independently addressable zones.
- Capabilities: Actuator, Switch, SwitchLevel, ColorControl, LightEffects, Refresh, Initialize. ColorTemperature only if confirmed by protocol research.
- Commands: `on()`, `off()`, `setLevel()`, `setColor()`, `setEffect()`, `refresh()`, `initialize()`.
- Polling: `runEvery5Minutes` (configurable), optimistic updates on command, reconcile on poll.
- Logging: `logEnable` preference (default false), 30-min auto-off via `runIn(1800, logsOff)`.

**Constraints:**
- Drivers run on Hubitat C-7/C-8, Groovy sandbox — no external libs, no classpath additions.
- Parent/child split is deferred; revisit after Cypher completes protocol research.

## Team Updates (2026-05-16T21:45:13Z)

Cypher has confirmed that the cloud API is fully documented in public references (sslivins/hass-gemstone, sslivins/pygemstone). Your architectural decisions for the driver capabilities (Switch, SwitchLevel, LightEffects, Refresh, Initialize) will drive the HTTP endpoint selection. Local API remains unknown — Mads is sniffing network traffic to 192.168.1.238 to discover it. Tank has your driver design wired into the scaffold (HubAction stubs ready for endpoint filling).

## Team Updates (2026-05-16T22:24:15Z)

**Scope tightened to local-only; user directive confirmed.** The driver will NOT implement any cloud path (no Cognito/AWS, no cloud mirrors). v0.2.0 targets the local controller at 192.168.1.238 exclusively. Your architecture and capability choices (Switch, SwitchLevel, LightEffects, Refresh, Initialize) remain sound — they map directly to the local API endpoints once Cypher's discovery phase uncovers them. Tank's v0.1.1 scaffold is live with warn banner. Next milestone: Mads will provide curl + port-scan output; Cypher analyzes that to narrow endpoint candidates; Tank begins HTTP wiring against narrowed targets.


### 2026-05-16T22:34:12Z: Team update

**Status:** No architectural change. Scope remains local-only. v0.2.0 timeline now explicitly tied to user mitmproxy capture success.

**Next gate:** After capture analysis, finalize endpoint design and per-capability strategy.

### 2026-05-16T23:04:57Z: Team update (Research phase complete)

**Status:** No architectural change. Scope remains local-only. v0.2.0 timeline now tied to Mads' UniFi packet capture action.

**Findings from driver extraction phase:**
- C4 driver confirmed: TCP port 80, driver.lua encrypted (PKCS#7)
- ELAN driver confirmed: edrvc binary format, encrypted
- PDF analysis: JSON property shape finalized (animation, patternId, brightness, speed, colors, all 0-255 ranges)
- 70+ API probes: all 404, routing envelope still unknown

**Architecture validated:** Switch, SwitchLevel, LightEffects, Refresh, Initialize remain sound for local-only scope. No changes needed.

**Next gate:** Tank's v0.2.0 implementation blocked until Mads' UniFi pcap reveals routing envelope. Once Tank wires endpoints, Switch's test plan becomes actionable.

### 2026-05-16T20:01:41-07:00: SunStat Connect Plus — architecture design

**Driver:** SunStat Connect Plus (Watts electric floor heating thermostat via cloud API)
**Output:** `.squad/decisions/inbox/trinity-sunstat-architecture.md`

**Patterns reused from Gemstone:**
- Namespace `mads`, `kebab-case` folder + file naming (`drivers/sunstat-thermostat/`)
- `@Field static final` literals only — no cross-field references (sandbox rule)
- `logEnable` / `txtEnable` preference pair with 30-min auto-off via `runIn(1800, "logsOff")`
- 300-second proactive token refresh via `runIn` (from `hubitat-cognito-token-refresh` skill)
- Scaffold transparency warn banner in command stubs before API is wired
- v0.1.0 scaffold → v0.2.0 working driver version cadence
- `state.*` for auth tokens; `atomicState.*` only on demonstrated race condition
- `descriptionText`-prefixed events gated on `txtEnable`

**New patterns introduced (not in Gemstone):**
- **Parent/child from day one** — unlike Gemstone (deferred due to unknown protocol), SunStat's multi-device reality is known at architecture time. One parent handles cloud auth + discovery; one child per thermostat.
- **`Thermostat` combo capability with constrained modes** — declare `Thermostat` for dashboard/RM/voice integration, then set `supportedThermostatModes = ["heat","off"]` and `supportedThermostatFanModes = ["auto"]` at `installed()` to hide unavailable modes. This is the right pattern for any heat-only device.
- **`setBoost(minutes)` over `emergency heat` mode** — using a custom command for timed override is cleaner than hijacking a voice-assistant-visible HVAC mode.
- **Dual-sensor split** — `temperature` (TemperatureMeasurement) for ambient, custom `floorTemperature` attribute for floor probe. Pattern applicable to any dual-sensor thermostat.
- **Temperature unit normalization** — emit all temperatures with `unit: location.temperatureScale`; convert Celsius API values to Fahrenheit when hub is configured for °F.

**Conventions for Mads to approve:**
- Driver names: `SunStat Connect Plus` (parent) / `SunStat Connect Plus Thermostat` (child)
- `isComponent: false` on child devices — lets users rename individual thermostat devices
- `signalStrength` custom attribute flagged as optional pending Cypher's API spec

**Skill extracted:** `.squad/skills/hubitat-heat-only-thermostat/SKILL.md`

---

### 2026-05-16T23:36Z: Reskill — no-push handoff pattern

**Pattern:** User established a no-agent-pushes operating model mid-session (agents prepare locally, commit, ask for per-task push approval). Captured in `.copilot/skills/git-workflow/SKILL.md` new section "## No-Push Handoff Pattern" (Option A: extended existing skill rather than sibling) because the pattern is fundamental to how agents use git workflow, not a standalone workflow. Confidence `medium`; survived user course-correction and one-shot exception grant. Anti-patterns: pushing on stale approval, conflating "asked" with "approved", forgetting handoff block.

## Team Updates (2026-05-17T03:01:41Z)

**SunStat Connect Plus v0.1.0 shipped.** Architecture proposal (trinity-sunstat-architecture.md) merged into decisions.md. Tank implemented full parent/child driver pair. Switch drafted comprehensive test plan. Cypher's API research finalized. Awaiting Mads' real-device verification (Mode.Enum, modelId, ROPC probe, httpPatch sandbox compatibility).

## Team Updates (2026-05-17T03:37:53Z)

**SunStat Connect Plus v0.1.2 released with 6 new features.** Tank wired EnergyMeter + 4 energy attrs, schedule control, thermostat hold, outdoor sensor, setpoint rounding, floor bounds. Switch expanded test coverage to 48 cases (#26-#48). Link bumped manifests/READMEs (v0.1.2, v0.4.0). Link-3 audited all 3 READMEs against 8 community Hubitat repos, applied 6 targeted edits (compatibility headers, version badges, releases links). Awaiting Mads' real-device verification and 3 README audit answers (forum threads, donation link, C-5 testing).

