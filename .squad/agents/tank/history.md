# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation. First target: Gemstone Lights via local HTTP at 192.168.1.238 (user-configurable in driver preferences).
- **Stack:** Groovy (Hubitat sandbox), `hubitat.device.HubAction`, `parseLanMessage`, `sendEvent`, `runIn`
- **Created:** 2026-05-16

## Learnings

<!-- Append new learnings below. Each entry is something lasting about the project. -->
- 2026-05-16: HPM publishing for this repo has two public JSON layers — per-driver `packageManifest.json` plus a root `repository.json`; the one-time HubitatCommunity submission adds only the root repository URL to the shared `repositories.json` list.
- 2026-05-16: A clean GitHub Actions release-on-version-bump pattern for Hubitat drivers is: trigger on `drivers/**/packageManifest.json`, derive a `<driver-folder>-v<version>` tag, parse the matching `.groovy` header changelog entry, and use that text for the GitHub Release body.
- 2026-05-16: No-agent-pushes operating model — agents may edit, stage, and commit locally, but Mads alone runs remote mutations such as `git push`, `gh repo fork`, and `gh pr create` after reviewing the prepared handoff.
- 2026-05-16: Direct Cognito `InitiateAuth` from a Hubitat driver must use `X-Amz-Target: AWSCognitoIdentityProviderService.InitiateAuth` with `Content-Type: application/x-amz-json-1.1`; the request body itself stays bare JSON with PascalCase keys and uppercase `AuthParameters` names.
- 2026-05-16: On any Cognito auth or refresh failure, log the request shape plus `resp.hasError`, `resp.status`, `resp.errorMessage`, `resp.headers`, `resp.data`, and `resp.errorJson`, but never log the password, tokens, or the full ClientId. If Hubitat shows `status=408` with `hasError=true`, inspect `resp.errorMessage` before blaming Cognito — Hubitat may be surfacing a local encoder or transport failure rather than an AWS response.
- 2026-05-16: Never use GString interpolation between two `@Field static final` constants in Hubitat drivers; use `+` concatenation or a helper method to avoid static-init compile errors.
- 2026-05-16: Correction to the earlier static-field note: `+` concatenation is NOT enough. Hubitat rejects any cross-reference between `@Field static final` initializers; use inline literals or compute values at use-site inside method bodies instead.
- 2026-05-16: Hubitat sandbox has TWO layers of restrictions: parse-time (no cross-`@Field` references) AND runtime (no `System.*`/`Thread.*`/`Runtime.*`/reflection/file-IO). Always audit BOTH layers when writing a new Hubitat driver.
- 2026-05-16: Hubitat HTTP encoder pitfall — must pre-serialize body String for non-standard Content-Types like AWS's `application/x-amz-json-1.1`. Decouple wire Content-Type (headers map) from Hubitat encoder hint (contentType param).
- 2026-05-16: Gemstone named effects come from two paginated cloud catalogs — saved presets at `GET /folders/pattern/list?page=N` and Gemstone-managed effects at `GET /downloads/folders/pattern/listGemstoneManaged?page=N`. Resolve `setEffect(name)` case-insensitively after trim, refresh the cache on a 1-hour TTL, and prefer the saved preset when a custom and built-in effect share the same visible name.
- 2026-05-16: Hubitat LightEffects works best as three separate layers: raw lookup (`state.effectCatalog` name → patternId), ordered favorites (`state.favorites` name → patternId), and dashboard index lookup (`state.effectIndex` index → patternId). Decorate favorites only at the user-facing layer (`lightEffects`, `favoriteEffects`, info logs) so both `setEffect("⭐ Pulse")` and `setEffect("Pulse")` resolve cleanly.
- 2026-05-16: Cypher's Gemstone cloud spec exposes favorites inline on `/folders/pattern/list` records via `isFavorite`; there is no separate favorites endpoint in the captured spec. The same spec still exposes no native CCT / white-temperature endpoint, so Hubitat `ColorTemperature` must use an RGB white-spectrum fallback through `PUT /deviceControl/play/pattern` while the driver tracks `colorMode = CT` explicitly.
- 2026-05-16: Public release published at `https://github.com/madskristensen/hubitat-drivers` with HPM install URL `https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/drivers/gemstone-lights/packageManifest.json`. Release-prep gotcha: ignore rules must cover the full `.squad/` tree plus adjacent local-only artifacts discovered during `git add` (`.copilot/`, `*.pcap`, `.vs/`, `.github/agents/`, `.github/workflows/squad-*.yml`, `.github/workflows/sync-squad-labels.yml`, and `.gitattributes`) before the first public commit.

### 2026-05-16T14:08:16-07:00: Gemstone Lights driver scaffold (v0.1.0)

**Driver scaffold structure choices:**
- Lifecycle: `installed()` sets every preference default explicitly (avoids null surprises on first save), then calls `initialize()`. `updated()` unschedules everything before re-initialising — this prevents doubled schedules when preferences change.
- `initialize()` owns all `runEvery*` scheduling behind a switch on `pollInterval`; it is the single source of truth for the polling schedule. `runIn(2, "refresh")` gives the hub a breath before the first LAN call.
- `uninstalled()` calls `unschedule()` to clean up cron jobs when the driver is removed.
- `logsOff()` uses `device.updateSetting` (not `settings`) — the correct Hubitat pattern to persist the change back to the preference.

**Hubitat idioms applied (follow in future drivers):**
- Optimistic events: emit `sendEvent` *before* `sendCommand`. UI feels instant; next poll reconciles truth.
- `setLevel(0)` implies `switch: off`; `setLevel(>0)` implies `switch: on` — emit both events in one call.
- `setHue` / `setSaturation` delegate to `setColor` (avoids duplicating logic; ColorControl capability requires both commands).
- `setNextEffect` / `setPreviousEffect` track `state.currentEffect` and wrap around `state.effectCount` — necessary because LightEffects capability requires both and the driver must know "where it is" without a round-trip.
- `parse()` is always wrapped in try/catch; uses `parseLanMessage()` for LAN responses.
- `@Field static final String DRIVER_VERSION` at the top — cheap to grep, visible in logs.
- UUID `257ada29-4d65-4f90-9183-da6cc75ef908` is the stable HPM id for this driver — never regenerate it.

**What is wired:**
- Full metadata, preferences, lifecycle, all capability commands, logging helpers, HPM manifest.
- IP validation regex in `updated()` and `sendCommand()` (guards against empty/malformed pref).
- Polling schedule wired to `pollInterval` preference with correct Hubitat `runEvery*` methods.
- `logsOff` 30-min auto-disable registered in `updated()` when `logEnable` is true.

**What is stubbed (pending Cypher's local probe):**
- `sendCommand()` logs intent but does NOT send a `HubAction` — the local endpoint path, method, and body are unknown. Commented-out `HubAction` skeleton is left in place for the second pass.
- `parse()` logs the raw `parseLanMessage` result but emits no device events — response shape is unknown.
- `state.effectCount` stays 0 until `refresh()` can populate it from a real device response.
- Cloud API (Cognito/JWT) is deliberately NOT implemented; driver assumes local once Cypher confirms.

### 2026-05-16: Gemstone cloud REST driver v0.2.0

**Key paths updated:**
- `drivers/gemstone-lights/gemstone-lights.groovy`
- `drivers/gemstone-lights/README.md`
- `drivers/gemstone-lights/TESTING.md`
- `drivers/gemstone-lights/packageManifest.json`

**Architecture + implementation patterns:**
- Use Hubitat async HTTP with a small queued-request model: queue commands in `state.pendingRequests` while auth or device discovery is in flight, then flush once both `state.accessToken` and `state.deviceId` are ready.
- Cognito login for this driver is `USER_PASSWORD_AUTH`; cache `accessToken`, `refreshToken`, `idToken`, and absolute expiry in `state`, then schedule a proactive refresh about 5 minutes before expiry.
- Treat Gemstone REST `401` as a one-time token-recovery path: queue the failed request, refresh once, replay once, and surface persistent auth failure through the `authStatus` attribute.
- Gemstone brightness is `0–255` on the wire. Hubitat `setLevel(0)` should map to off; all other levels round-trip through `Math.round(level * 255 / 100)` and the inverse on refresh.
- `play/pattern` needs a full pattern object. Cache the raw pattern from `currentlyPlaying` in `state.lastPattern`, deep-clone it, then mutate only `brightness`, `colors`, and `animation` so unknown fields survive.
- For accounts with multiple home groups, probe groups in order until one returns devices; if a home group returns multiple devices, v0.2.0 binds the first device and logs the choice.
- `authStatus` should only emit when its value changes, otherwise periodic refreshes spam the device event history.

## Team Notes Summary

**v0.1.0 diagnosis complete** — driver is pure scaffold (no HTTP traffic). Added v0.1.1 warn banner on every command to clarify stub status. **Scope locked local-only** (no cloud path). v0.2.0 waits on local API discovery (packet capture from Mads). All speculative probing exhausted (70+ combinations, all 404). Routing mechanism unknown; awaits capture dissection. Key properties confirmed: `animation`, `patternId`, `brightness`, `speed`, `colors` (all 0-255 ranges). Framework fingerprinting inconclusive; secondary concern.

### 2026-05-16T16:50:00-07:00: v0.2.0 Cloud Driver Shipped

**Status:** COMPLETED

The architecture discovery session (Mads' packet capture across UniFi gateway + 3 APs) confirmed the Gemstone controller speaks **AWS IoT MQTT exclusively**; local LAN protocol is reserved for encrypted vendor drivers (Control4 PKCS#7, ELAN Cindev binary). No local protocol can be discovered by sniffing because there is no traffic to sniff.

**Decision:** Scope amendment locked v0.2.0 to **cloud REST** (Cognito SRP auth + AWS REST control endpoints). Pure-local remains future work pending vendor disclosure.

**v0.2.0 Delivered:**
- **gemstone-lights.groovy** (cloud auth + REST endpoints)
  - Cognito `USER_SRP_AUTH` with HMAC-SHA256 SRP math in Groovy
  - Token refresh schedule (proactive ~5 min before expiry)
  - Home group discovery → first device binding (logged for transparency)
  - Capabilities: Switch, SwitchLevel, ColorControl, Refresh, Initialize
  - Optimistic state + 30s polling reconciliation
  - Graceful error recovery (401 token refresh, 5xx retries, timeout handling)

- **README.md** (per-driver user guide)
  - Setup walkthrough + preferences table
  - Limitations banner (v0.2.0 scope)
  - Testing link to TESTING.md

- **TESTING.md** (manual test plan)
  - 13 test sections covering lifecycle, capabilities, network failure, hub reboot
  - Protocol-level tests deferred (no longer needed; cloud endpoint testing replaces local HTTP testing)

- **packageManifest.json** (HPM-ready)
  - UUID stable: `257ada29-4d65-4f90-9183-da6cc75ef908`
  - minimumHEVersion: 2.3.0

**Key Patterns Applied:**
- Queue requests while auth/discovery in flight; flush on ready
- Cache `lastPattern` from `currentlyPlaying` for read-modify-write on brightness/color changes
- `authStatus` attribute emits only on change (prevents spam)
- Brightness mapped 0-100 Hubitat ↔ 0-255 wire
- Hue/saturation/value → ARGB int encoding for color channel
- All preferences validation in `updated()`; no validation in command handlers

**Scope Delivered:**
✅ Auth (Cognito SRP)
✅ Discovery (home groups → devices)
✅ Switch on/off
✅ SwitchLevel (brightness)
✅ ColorControl (hue/sat/value → ARGB)
✅ Refresh (poll every 30s)
✅ Initialize (schedule setup)

❌ LightEffects (v0.3.0 — deferred pending effect catalogue UX)
❌ Multi-device selection UI (v0.3.0)
❌ ColorTemperature (not in Gemstone API)

**References:**
- Architecture decision: copilot-mqtt-architecture-2026-05-16.md
- Scope lock: copilot-scope-amendment-cloud-v0.2.0-2026-05-16.md
- Cloud API spec: decisions.md (Cypher 2026-05-16T14:08:16-07:00)
- Driver design: decisions.md (Trinity 2026-05-16T14:08:16-07:00)
- Orchestration log: .squad/orchestration-log/2026-05-16T16-50-00-tank.md
