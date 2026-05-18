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

## 2026-05-17 Sessions Archived (tank-1, tank-2, tank-4 early)

Archived at 2026-05-18T01:41:11Z when main history.md exceeded 15 KB (20,381 bytes).

### Gemstone v0.4.9 — Multi-Effect Orchestration (Session tank-1, ~180 s)

- Shipped next/previous effect buttons + effect carousel UI.
- Built `effectNameForPatternId` and `effectIndexForPatternId` reverse lookup maps at catalog finalization (O(1) instead of O(n) linear scans on every effect activation).
- Pattern: store effect metadata keyed by patternId; build reverse index maps at catalog refresh time; consume maps in command paths.

### SunStat v0.1.5 / v0.1.6 — Async HTTP Migration (Session tank-2, ~240 s)

- **Key finding:** Synchronous HTTP on hot paths (polling, token refresh) stalls the Hubitat hub thread.
- **Migration pattern:** Replace blocking `httpGet` / `httpPost` with `asynchttpGet` / `asynchttpPatch`. Keep token refresh synchronous (`refreshTokensSync`) so caller always has valid token. Dispatch per-device polls via async with data map carrying `[childDni, deviceId, retry401: true]`.
- **On 401 in callback:** Call `throttled401Refresh()` (rate-limited to once per 60 s) then re-issue as fresh `asynchttpGet` with `retry401: false`. NEVER nest a sync HTTP call inside an async callback.
- **Duration:** 2 full rewrites of parent driver (v0.1.5 partial async + v0.1.6 complete async migration).

### Cross-Driver Audit (Session tank-4 early, 2026-05-17 15:41 UTC)

Scanned Gemstone, SunStat, Touchstone. Found 11 anti-patterns:

1. Sync HTTP on hot paths → async + rate-limit
2. Nested blocking HTTP → dispatch fresh async on error
3. Frequent `state.rxBuffer` writes → only on partial frame remaining
4. Dead state writes → audit and remove
5. O(n) reverse lookups → build O(1) maps at init
6. JSON round-trip for shallow clones → use `new LinkedHashMap(source)`
7. Boxed Integer in loops → use `int` primitive
8. Guard block duplication → extract helper
9. Double-negative guards → prefer `== true`
10. Missing `capability "Actuator"` on command parents
11. `USER_AGENT` literal not synced to `DRIVER_VERSION` → add comment "keep in sync"

**Init-time cleanup pattern:** Any `state.*InFlight` flag must reset to `false` in `initialize()` — hub reboot mid-operation leaves flags `true`.

### Touchstone v0.1.4 — Safety + Sandbox Fixes (2026-05-17 12:22 UTC)

- **v0.1.3 bundled with v0.1.4** (v0.1.3 never user-released; only v0.1.4 shipped).
- **v0.1.3:** Added power-on defaults (flame color, log color, flame brightness, temp setpoint, heat level). Pattern: `runInMillis(1500, "applyOnPowerOnDefaults")` for firmware settle window.
- **v0.1.4:** Removed `defaultHeatLevel` per Mads's safety directive (heater must never auto-toggle — radiant heat → fire/burn risk). Removed 2 executable `.getClass()` calls (Hubitat sandbox blocks reflection at runtime).
- **Key decision:** Hardware safety > convenience. Added "Safety" README section: driver intentionally does NOT auto-start heater.

### Touchstone v0.1.5 — App-only Preference UI Audit (2026-05-17 12:22 UTC)

- **Learning:** Hubitat drivers must use only `input` fields; app UI helpers (`paragraph`, `section`, `href`, `app`, `mode`, `pageDefault`) are not allowed.
- **Action:** Removed `paragraph` header; moved explanatory text into per-field `description:` text.
- **Audit result:** No other app-only constructs found (`section`, `href`, `app`, `mode`, `pageDefault` all clean).

### Touchstone v0.1.6 — Flame Speed + Enum Bounds-Check (2026-05-17 15:50 UTC)

- **DP 103 (Flame Speed):** New command `setFlameSpeed(speed)` with enum ["Slow", "Medium", "Fast"]. (Switch to verify on hardware.)
- **DP 105 (Log Brightness):** New command `setLogBrightness(level)` with numeric strings 1–12. (Unverified; paired with power-on default preference.)
- **Removed:** `power` attribute (duplicate of `switch`). Internal DP role key `"power"` unchanged.
- **Bounds-check hardening:** Added OPTIONS bounds-checks in `applyDps()` for all enum DPs (101, 102, 103, 104, 105). Prevents invalid device echoes from blanking the Hubitat UI.
- **Commit:** 3fe727c

### Touchstone v0.1.10 — Enum Bounds-Check Reinforcement (2026-05-17 17:39 UTC)

- **Learning:** When user reports "display shows +1", bug is usually in the WRITE-side emit path (not parse-side). Both paths often reuse the same off-by-one math.
- **Hubitat Commands-tab quirk:** Numeric-string ENUM parameters advance dropdown cosmetically but send correct value. Workaround: use `type: 'NUMBER'` with `range` instead.
- **Status:** Awaiting Cypher's empirical test (DP 105/109 read-only confirmation) before final changelog.

### Touchstone Color Palette Learnings

- **DP 101 (Flame Color):** Verified labels from Tuya app screenshot: 1=Orange (default), 2=Blue, 3=White, 4=Orange+Blue, 5=Orange+White, 6=Blue+White.
- **DP 104 (Charcoal Color):** Renamed from "Log Color" (a guess). Tuya app verified: 1=Orange, 2=Red, 3=Blue, 4=Yellow, 5=Green, 6=Purple, 7=Cyan, 8=Magenta, 9=White, 10=Pink, 11=Rainbow (8-segment), 12=Spotlight (best-guess).
- **Future label verification:** Always request app screenshots before inventing labels. Hardware-independent labels (Dimmest, Dim, Medium, Brighter, Brightest) are safe; palette colors require verification.

### Touchstone v0.1.17 — Charcoal Color Rename (2026-05-17 — Breaking Change)

- **Commands:** `setLogColor(number)` → `setCharcoalColor("LabelName")`
- **Attributes:** `logColor` → `charcoalColor`
- **Preferences:** `defaultLogColor` → `defaultCharcoalColor`
- **No backward-compat alias** (incompatible signatures: number → string enum).
- **Existing numeric preferences silently skipped** on power-on; users must re-select.

---

## Status at Archive Time (2026-05-18T01:41:11Z)

- **Shipped:** Touchstone v0.1.4–v0.1.17 (latest at archive time)
- **In flight:** Touchstone v0.1.18 (persistent socket + Tuya push subscriptions), v0.1.11 (DP 105 removal)
- **Pending Mads test:** DP 105 write-path confirmation; DP 109 ember brightness writable target
- **Gemstone v0.4.10:** Multi-controller zones (controllerName preference, Option A-lite architecture) — shipped parallel to archive session
- **Next wave:** Switch to run Tests 34–37 (Touchstone socket) and Tests 19–22 (Gemstone zones)


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


---

## Previous Sessions (Archived)\n
# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Active Milestones Summary

### 2026-05-17: Gemstone Lights v0.4.0–v0.4.8
✅ **Released.** HPM publishing infrastructure + community PR submitted.

Key learnings:
- **API Envelope Pattern:** Unwrap {errorNumber, errorMessage, body: <payload>} in dedicated helpers
- **WebCoRE Overload Shadowing:** Distinct command names avoid metadata conflicts (playEffectByName)
- **Error Capture:** Use
esponse.getErrorData() for 4xx responses; never skip diagnostic logs
- **ARGB & Byte Order:** Gemstone uses ABGR (not ARGB); include 0xFF alpha; force long arithmetic with 0xFFL
- **State Hygiene:** Favorites-only caching; non-favorites are on-demand; transient state lives in locals
- **Log Discipline:** Count bounds even at debug; every sendEvent needs descriptionText

### 2026-05-16T20:01:41-07:00: SunStat Connect Plus v0.1.0–v0.1.4
✅ **Shipped.** Parent/child OAuth architecture; token refresh (24-hr TTL); polling + discovery.

Key learnings:
- **EnergyMeter:** Built-in nergy attribute; siblings must be declared explicitly; guard array access with instanceof
- **Hold/Schedule Enums:** Use string enums ("holding"/"following") not bools; lowercase Hubitat-side values; emit optimistic updates
- **Setpoint Steps:** Apply step-rounding *before* clamping; persist step to state each poll
- **Token Preference Size:** Tokens >1KB use command setRefreshToken(String) not password preference
- **API Envelope Unwrapping:** Two helpers—parseResponseBody() for Maps, parseResponseList() for Lists
- **URL Path Encoding:** Always .replace("+", "%20") after URLEncoder for path segments (not query strings)

---

## Bosch Home Connect (In Scoping)

### 2026-05-17T16:31:55Z — Cypher + Trinity Scoping
**Verdict:** Feasible on Hubitat.

**Key decisions:**
- Parent App + child Driver (like SunStat)
- Device Flow OAuth2 (no redirect URI blocker)
- Polling at 90-120s cadence (1000 req/day rate limit)
- ContactSensor per door

**ACTION:** When spawned for implementation, read .squad/decisions/decisions.md first. Effort: Medium (2 sessions).

### 2026-05-17T16:45:09Z — Bosch Home Connect Consumer Auth Investigation

**Decision:** Developer portal registration path is unavoidable.

**Context:** User requested elimination of developer portal onboarding. Cypher investigated 5 consumer-auth alternatives (hcpy, SingleKey ID, openHAB direct binding, Homebridge plugins). All blocked by:
- CAPTCHA on SingleKey ID login (2024)
- Local WebSocket protocol (no Hubitat support)
- No consumer REST API for state polling

**Verdict:** Official developer API (Device Flow) remains the only feasible path. One-time 5-minute registration is the cost of a working driver.

**Design implication:** When Tank implements, use Device Flow OAuth2 with user-registered client_id + client_secret (entered as preferences). The technical path is confirmed; no further consumer-auth alternatives exist.

---


---


| DP | Type | Observed | Notes |
|---|---|---|---|
| 101 | string-enum | "1" | Likely flame color/effect |
| 102 | string-enum | "5" | Likely flame brightness |
| 103 | string-enum | "1" | Likely flame speed |
| 104 | string-enum | "4" | Likely log/ember color |
| 105 | string-enum | "5" | Likely log brightness |
| 107 | bool | false | TBD |
| 108 | bool | false | TBD |

### ACTION FOR TANK

1. Read `.squad/decisions.md` for full DP details + Trinity's corrected capability mapping (named commands, not ColorControl)
2. Scaffold driver with Tuya Local protocol layer (rawSocket + AES-128-ECB framing)
3. Implement Wire capabilities + custom commands per Trinity's architecture
4. Borrow Tuya Local protocol reference from kkossev/Hubitat if needed; acknowledge license per project policy
5. Ready for Switch integration testing once LED DP empirical mapping is complete

### Effort Estimate (Revised)

**Session 1:** Driver scaffold + heater DPs (1, 2, 3, 5, 13–15) + capability wiring
**Session 2:** LED DP mapping (101–108) via Tuya app interaction; Switch validates on real device; driver refinement
**Session 3 (conditional):** Protocol version v3.4/v3.5 support if discovered in Session 2

Pattern learned: Empirical DP mapping via app interaction is faster than reverse-engineering firmware; rely on user doing the interactive exploration before driver development. This validates assumptions early.

---

## Core Patterns (Reusable)

1. **Parent/Child OAuth:**
   - Parent holds state.accessToken, state.refreshToken, state.tokenExpiresAt
   - getValidToken() guard before every HTTP call; refresh if
ow > expiresAt - 300
   - Children store cloud device IDs as DataValue("cloudDeviceId")
   - isComponent: false on child creation
   - Parent calls child.parseDeviceState(body) after each poll

2. **API Response Handling:**
   - Check
esponse.getErrorData() for 4xx responses (getData() is null)
   - Unwrap envelopes in dedicated helpers (parseResponseBody(), parseResponseList())
   - Log diagnostic details (body type, sample data) on every response path
   - Never replace server-assigned IDs with synthetic strings

3. **Capability Metadata:**
   - Use distinct command names to avoid WebCoRE overload shadowing
   - Every sendEvent needs descriptionText
   - Standard capabilities + custom attributes for non-standard state
   - Emit optimistic updates before API calls

4. **State & Preference Hygiene:**
   - Tokens >1KB: use command parameter not preference (preference limit ~1024 chars)
   - Transient working data: local variables, not state.*
   - Persistent caches: favorites-only; on-demand non-favorites
   - Log bounds even at debug level for collections

---

**For detailed learning notes, see archived history.**

---

## 2026-05-17T18:55:16Z — Touchstone v0.1.2 Shipped (Scribe Cross-Agent Sync)

**Topic:** touchstone-driver-shipped

v0.1.1 (Sideline Elite scaffold) + v1.1 (Device Profile generalization) + v1.2 (critical CRC32 import fix) are now landed in main. Both Tank sessions are complete.

**v1.1 outcomes:**
- Driver renamed to `"Touchstone / Tuya Fireplace"` (Mads' Option C decision)
- Device Profile preference with three modes (Sideline Elite / Generic Tuya / Custom)
- Discovery commands: `discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`
- All commands routed through `dpFor(role)` to respect active profile

**v1.2 outcomes:**
- Removed forbidden `java.util.zip.CRC32` import; replaced with pure-Groovy table-driven CRC32
- Removed `java.io.ByteArrayOutputStream`; replaced with `concatBytes()` helper
- Verified all remaining imports (groovy.transform.Field, groovy.json, javax.crypto) are Hubitat-safe
- **This was the blocking install error for v0.1.1; unblocked by v1.2**

**Manifest sync (by Scribe):**
- packageManifest.json version bumped from 0.1.1 → 0.1.2 to match driver code

**Documentation (by Link):**
- README (18.2 KB): device support, setup, discovery workflow, troubleshooting
- HPM manifest ready for publish

**Test plan (by Switch, v0.1.0):**
- 19 tests locked for Sideline Elite; smoke pass = 30 min for Mads
- v1.1 testing will expand to Generic/Custom profiles (queued for next batch)

**Next phase:**
- Mads: Re-test Hubitat install to confirm import allowlist fix works
- Mads: Review documentation; iterate if clarifications needed
- Switch: Expanded validation for Generic/Custom profiles (queued)
- Community: Users with other Touchstone models can now use Custom profile + discovery commands

---

## 2026-05-17T17:14:00Z — Touchstone Tuya Portal-Free Key Extraction Audit (Cypher — Conclusion)

**Topic:** tuya-portal-free-2026

Cypher completed definitive audit of all 2026 Tuya local-key extraction methods. **Conclusion affects Touchstone driver UX.**

### KEY FINDING

**Portal-free path exists but is not applicable to Mads** (no Home Assistant). The iot.tuya.com portal signup (which Mads is already pursuing via Coordinator walkthrough) is the **durable and correct choice** for the Touchstone driver.

### Technical Summary

- **Portal-free path:** `make-all/tuya-local` cloud-auth (HA integration) via `apigw.iotbing.com`. Requires Home Assistant + SmartLife app scan. **Relies on hardcoded Tuya `client_id` that can be revoked unilaterally.**
- **All other portal-free methods:** Broken (MITM deprecated 2022), impractical (ADB backup blocked), or not applicable (BLE is setup-only, not for key material).
- **For Mads (no HA):** `tinytuya wizard` or direct iot.tuya.com setup is the only option. Mads is mid-flow on the latter.

### Impact on Touchstone README

Tank's driver will work with any local key source. Link should document **both** key-extraction methods in README:
1. **Preferred (if user has HA):** `make-all/tuya-local` cloud-auth (5 min, no dev account) — note the Tuya client_id dependency
2. **Fallback (no HA):** `tinytuya wizard` via iot.tuya.com (20 min, free dev account) — or direct signup like Mads

**No changes to Touchstone driver architecture.** Both key-extraction paths yield the same permanent `localKey` value for Tuya Local (LAN) protocol.

See `.squad/decisions.md` section "2026-05-17: 2026 Tuya Portal-Free Key Extraction Assessment" for full audit and SUPERSEDES note correcting prior session cypher-6 claims.

## Learnings

### 2026-05-17T11:07:22.475-07:00 — Touchstone Tuya v3.3 scaffold

- Hubitat `interfaces.rawSocket.connect(..., byteInterface: true)` still feeds Tuya payloads into `parse(String message)` as hex text in practice; buffer the hex stream, split on `000055AA`, and validate CRC32 before decrypting.
- Tuya v3.3 control traffic is AES-128-ECB / PKCS5Padding over the JSON body. Most commands prepend ASCII `3.3` + 12 zero bytes **after** encryption; `DP_QUERY` (`0x0a`) and heartbeat (`0x09`) skip that protocol header.
- 22-character Tuya IDs are the `device22` edge case: status queries may need command `0x0d` plus a null-valued `dps` map instead of command `0x0a`. If the device answers with `data unvalid`, switch query mode and retry once.
- kkossev patterns adopted here are lifecycle + hygiene patterns, not the protocol layer itself: `installed()` sets defaults then `initialize()`, `updated()` unschedules + re-inits, debug logging auto-disables after 30 minutes, and `parse()` must log-and-bail on bad frames rather than crash.
- Touchstone's single-client Tuya socket is worth treating as scarce: queue requests, close the socket quickly after idle, and back off at 5s / 15s / 30s when another client (for example Smart Life) appears to be holding the TCP slot.
- DP 14 (°F setpoint) can lie immediately after a power transition. Keep optimistic setpoint state locally and schedule a delayed refresh instead of trusting the first post-power status frame.

### 2026-05-17T11:24:33-07:00 — Touchstone v1.1 generalization

- The safest cross-model Tuya pattern is **Tested + Generic + Custom**: keep the verified Sideline Elite DP map as the default, keep the Generic profile conservative (power / heat / setpoint only), and expose explicit DP-number overrides for Custom instead of guessing LED mappings.
- Hubitat driver preferences can gate Custom-only inputs with `if (settings?.deviceProfile == "Custom")`, but those extra fields only appear after the user saves/reopens the device page. The command/path logic still needs hardcoded fallbacks so an untouched Custom profile resolves to usable defaults.
- Let `dpFor(role)` resolve the active DP at command time and parse time, not once during `updated()`. That way a profile change or edited DP number takes effect on the very next command/status frame without rebooting the driver.
- In-driver discovery is practical on Hubitat: `discoverDPs()` logs the typed DP dump, `captureBaseline()` + `captureDiff()` isolate remote-button changes, and `setRawDP()` gives users an auditable way to experiment without leaving the device page.

### 2026-05-17T11:31:31-07:00 — Hubitat import allowlist / CRC32

- Hubitat driver imports are stricter than plain Groovy: `java.util.zip.CRC32` is rejected at install time, and simple `java.io.*` helpers are risky enough that a tiny pure-Groovy byte concatenation helper is the safer default.
- For reusable lookup tables in Hubitat drivers, `@Field static final` is the right pattern; it builds once at driver load instead of rebuilding inside every parse/send call.
- Tuya v3.3 packet checksums use canonical CRC-32/ISO-HDLC parameters: init `0xFFFFFFFF`, reversed polynomial `0xEDB88320`, reflected byte updates, xor-out `0xFFFFFFFF`, and the resulting unsigned 32-bit value still gets written big-endian into the `55AA` frame.

---

### 2026-05-17T18:24:33Z — Cross-Agent Decision Sync (Scribe)

**Topic:** touchstone-driver-shipped

**Naming Decision (Option C):** Driver display name is `"Touchstone / Tuya Fireplace"` (not just "Touchstone Sideline Elite"), and file path remains `drivers/touchstone-fireplace/touchstone-fireplace.groovy`. This threads the needle: community discoverability + honest scope + room for other Tuya WiFi fireplaces. (Decision merged from coordinator directive; Scribe appended for Tank + Switch awareness.)

**Generalization Scope:** Tank v1.1 (in flight) will add Device Profile dropdown + discovery commands for multi-Touchstone-model support. Scope deferred from v0.1.0 because driver is sealed and shipping. (Decision merged from Mads directive; Scribe appended for Tank awareness.)

**Test Plan Status:** Switch completed 19-test real-device plan (merged to decisions.md). Test coverage: pre-flight, happy path (9 tests), state sync (2 tests), recovery (3 tests), edge cases (3 tests), stability (1 test), cleanup. All with clear pass/fail criteria and known limitations documented. Ready for Mads smoke test after Tank v0.1.0 ships.

**v1.1 In Flight:** Tank parallel session is drafting Device Profile preference + discovery workflow (`discoverDPs()`, `captureBaseline()`, `captureDiff()`, `setRawDP()`). Scope: make driver work for other Touchstone models without manual DP discovery. Awaiting real-device validation from Mads before landing v1.1.

### 2026-05-17T11:58:55-07:00 — Touchstone v0.1.3 power-on defaults

- For Hubitat-side presets that should ride on `on()`, keep the switch event immediate, queue DP 1 right away, then use `runInMillis(...)` to apply optional follow-up DP writes asynchronously so the user-visible power toggle stays responsive.
- Touchstone's post-power-on settle window matters: DP 14 / Fahrenheit setpoint can revert briefly during the off→on transition, so a short (~1500 ms) delay before default writes gives the firmware time to settle before setpoint/LED follow-up commands land.
- Conditional preferences by Device Profile are practical here: gate Sideline-only flame/log/brightness defaults behind `if (settings?.deviceProfile != "Generic Tuya Fireplace")`, but still resolve DPs at command time so hidden stale values cannot bypass profile mapping.

### 2026-05-17T11:58:55-07:00 — Touchstone v0.1.4 safety + sandbox

- Hubitat's driver sandbox blocks reflection-style runtime inspection broadly, not just imports: avoid `.getClass()`, instance `.class`, `.metaClass`, `respondsTo()`, method/field introspection, and similar type-probing helpers in driver code.
- For heater-capable devices, never auto-toggle the heater DP from implicit flows like power-on defaults. Safe power-on defaults can restore LEDs and the target setpoint, but heater state must stay behind an explicit user command such as `setHeatLevel(...)`.

---
