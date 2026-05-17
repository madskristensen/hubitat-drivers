# Tank — History Archive (Pre-2026-05-17)

Archived: Gemstone Lights v0.1.0–v0.3.0 development, detailed technical learnings, and early prototype work.

## Learnings (Archived)

- 2026-05-16: Azure AD B2C token refresh for Watts Home API uses `application/x-www-form-urlencoded` POST — this IS one of Hubitat's three built-in encoders, so no pre-serialization quirk is needed. Set `requestContentType: "application/x-www-form-urlencoded"` and pass a pre-built query string as `body`.
- 2026-05-16: Watts Home refresh tokens ROTATE on every refresh call — the old token is invalidated immediately. After every successful refresh, persist the new `refresh_token` to `state.refreshToken` before anything else.
- 2026-05-16: Parent/child Hubitat pattern: parent creates children with `addChildDevice("namespace", "ChildDriverName", dni, [name: label, isComponent: false])`. Children call `parent.someMethod(arg)` to route API calls back. Parent calls `child.someMethod(data)` to push state updates.
- 2026-05-16: Hubitat thermostat capability combo (`Thermostat`) bundles heating/cooling/fan commands. For heat-only devices, constrain via `sendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(["heat","off"]))` and `supportedThermostatFanModes = ["auto"]` in `installed()`. Still implement all required commands — fan/cool stubs just log a warning.
- 2026-05-16: Floor probe sentinel: Watts Home API (and the EU Watts Vision variant) report `data.Sensors.Floor.Val` as ~100°C / 212°F when the probe is physically disconnected. Guard: if converted value exceeds 110°F / 43°C, call `device.deleteCurrentState("floorTemperature")` and log a warning instead of emitting the bogus reading.
- 2026-05-16: `httpPatch` is the correct Hubitat built-in for PATCH calls. Use a `httpMethod(method, params, closure)` dispatcher shim to keep call-sites clean when method is determined at runtime.
- 2026-05-16: `thermostatSetpoint` attribute (Thermostat combo) should mirror `heatingSetpoint` for heat-only devices. Set it every time `heatingSetpoint` is set or polled so dashboard displays the correct active setpoint.
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
- 2026-05-16: **Reskill on hpm-release-workflow:** Skill was pre-set at "high" confidence; Link validated end-to-end execution (push → workflow_dispatch → tag → release → community PR). No gaps found. Key confirmation: JSON manipulation on community list requires surgical text edits (not serialization) to preserve indentation; PowerShell's `ConvertFrom-Json | ConvertTo-Json` breaks formatting by normalizing tabs to spaces.

---

### 2026-05-16T14:08:16-07:00: Gemstone Lights driver scaffold (v0.1.0)

**Status:** ARCHIVED

Gemstone Lights driver v0.1.0 scaffold completed with:
- Full metadata, preferences, lifecycle, all capability commands, logging helpers, HPM manifest
- IP validation regex in `updated()` and `sendCommand()`
- Polling schedule wired to `pollInterval` preference
- Stubs for `sendCommand()`, `parse()`, and cloud API (pending Cypher's local probe)

**Learnings captured** in archive: Hubitat lifecycle patterns, optimistic event handling, driver logging conventions.

---

### 2026-05-16: Gemstone cloud REST driver v0.2.0

**Status:** ARCHIVED (COMPLETED)

Gemstone Lights v0.2.0 shipped with full Cognito SRP auth, device discovery, and cloud REST control endpoints:
- Switch, SwitchLevel, ColorControl, Refresh, Initialize capabilities
- Optimistic state + 30s polling reconciliation
- Graceful error recovery (401 token refresh, 5xx retries, timeout handling)
- User queue model for requests during auth/discovery in flight

---

### 2026-05-16T16:50:00-07:00: Gemstone Lights v0.4.0 Release Infrastructure Prep

**Status:** ARCHIVED

Release infrastructure established (GitHub Actions workflow, HPM manifest layer, RELEASING.md checklist, community PR tooling).
