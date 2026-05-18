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
## Summary of Shipped Drivers (Prior Sessions)

**Touchstone Fireplace (Latest: v0.1.22)**
- v0.1.1–0.1.5: Core scaffold, safety hardening, reflection fixes
- v0.1.18: Persistent socket + heartbeat + push subscriptions
- v0.1.19: Child lock (DP 108)
- v0.1.20: Active TCP discovery + DHCP recovery
- v0.1.22: Log hygiene (trace/debug split)

**Gemstone Smart Heater (Latest: v0.4.10)**
- Multi-controller zone support with named controller binding
- lastActivity attribute for passive monitoring

**SunStat Solar Control (Latest: v0.1.7)**
- lastActivity attribute integration

**HPM Bundle (v1.0.0)**
- Root packageManifest.json with all 4 drivers
- release.yml bundle tag generation

---

## Key Learnings Archive

See `history-archive-2026-05-18.md` for detailed learnings on:
- Persistent socket lifecycle and heartbeat patterns
- Tuya protocol nuances (empty heartbeat payload, frame handling)
- Multi-controller binding with blank-preference idiom
- Active TCP discovery state machines
- HPM bundle assembly and versioning
- DP 108 child lock boolean wiring
- And more...

## Learnings

### Skip-if-match pattern for power-on defaults (v0.1.23)

When a driver applies user-configured defaults at a lifecycle event (power-on, scene activation, etc.), first check `device.currentValue(attributeName)` against the configured default label before writing the DP:

```groovy
String current = device.currentValue("flameColor")
if (current != null && current == configuredDefault) {
    traceLog "applyOnDefaults: skipping defaultFlameColor — already '${configuredDefault}'"
} else {
    debugLog "applyOnDefaults: applying defaultFlameColor = '${configuredDefault}' (was '${current}')"
    // proceed with DP write
}
```

Key rules:
- **null current value = proceed with write.** The device state may not be known yet immediately after power-on. Skipping when state is unknown would silently fail to apply the user's preference.
- **Each default is independent.** Evaluate each attribute separately — don't gate all four on a single all-or-nothing check.
- **Log hygiene:** skipped lines go to `traceLog`, applied lines go to `debugLog`. Consistent with v0.1.22 trace/debug taxonomy.
- **DPs not mapped for the profile** (the existing `log.warn` branches) are unaffected — skip-if-match only applies inside the "DP is mapped" branch.

**Applicable to similar drivers:**
- **Gemstone Smart Heater** — if it applies configured zone defaults at zone-activate time, apply the same pattern per zone attribute.
- **SunStat Solar Control** — if defaults are applied at schedule trigger, apply the same pattern for setpoint/mode attributes.
- Generally: any driver with a `defaultFoo` preference that writes a DP at a lifecycle event should use this pattern to avoid spurious wire traffic and device flicker.


---

## 2026-05-17T19:29:40Z — Touchstone v0.1.5 paragraph() fix (App-only UI audit)

**Requested by:** Mads

### Completed

- Removed `paragraph` header from `preferences {}` block
- Moved power-on defaults explanation into per-field `description:` text
- Audited for app-only constructs (`section`, `href`, `app`, `mode`, `pageDefault`) — clean
- Bumped driver version to v0.1.5
- Consolidated Hubitat sandbox families into `.squad/skills/tuya-local-groovy/SKILL.md`

### Key Learning

Hubitat driver preferences are not the same as app preferences. Drivers should use only `input` fields; app UI helpers like `paragraph()`, `section()`, `href()`, `app()`, `mode()`, and `pageDefault()` will fail at install time in drivers and should be replaced with `description:` text on each field.

---

## 2026-05-17T19:29:40Z — Touchstone v0.1.4 shipped (Cross-Agent Batch Awareness)

**Collaborators:** Tank (2 runs), Link, Switch (test surface awareness)

### v0.1.3 + v0.1.4 are bundled in a single commit

v0.1.3 shipped optional power-on defaults (flame color, log color, flame brightness, temp setpoint, heat level). Link updated docs. Then immediately hardened v0.1.4: removed heater auto-apply per Mads's safety directive, fixed Hubitat sandbox reflection bugs. v0.1.3 was never released; users only see v0.1.4.

### Cross-Team Coverage

1. **Tank v0.1.3:** Added power-on defaults (runInMillis 1500ms delay for firmware settle window)
2. **Tank v0.1.4:** Removed defaultHeatLevel (fire/burn safety); removed 2 executable reflection calls (parse() exception logging, dpValueType() fallback)
3. **Link v0.1.4:** Updated README with Power-on Defaults + Safety sections; bumped packageManifest to v0.1.4; changelog omits v0.1.3
4. **Switch (test surface):** Aware that defaults apply ~1.5s after on(); heater never auto-toggles; v0.1.4 should install without sandbox reflection errors

### Key Decisions Captured in decisions.md

- User directive: heater must never auto-start (safety)
- Hubitat bug: sandbox rejects e.getClass() at line 449
- Documentation pattern: hardware safety > convenience; be explicit about intentional omissions

---

See history-archive.md for detailed earlier sessions (Gemstone, SunStat, Bosch feasibility).

## Learnings

- 2026-05-17T12:22:15-07:00 — Hubitat driver preferences are not the same as app preferences: drivers should use only `input` fields, and app-only UI helpers like `paragraph`, `section`, `href`, `app`, `mode`, and `pageDefault` will fail in drivers. Put explanatory copy into each input's `description:` instead.
- 2026-05-17T13:21:30-07:00 — The repo release workflow parses driver `Changelog:` entries with the regex in `.github/workflows/release.yml` line ~106, so each changelog line must use a plain `YYYY-MM-DD` date; ISO 8601 timestamps with time/offset will break release-note generation.
- 2026-05-17T15:41:32-07:00 — Cross-driver audit (Gemstone, SunStat, Touchstone) surfaced these anti-patterns to avoid in future drivers:
  1. **Synchronous HTTP on hot paths** — SunStat parent uses `httpGet/Post/Patch` (blocking) throughout polling and token refresh. With N children this stalls the hub thread for N×timeout. Always prefer `asynchttpGet/Post` for polling drivers; only use synchronous HTTP when the response is needed inline and there is no alternative (e.g. token bootstrap).
  2. **Nested blocking HTTP** — SunStat parent calls `refreshTokensSync()` (synchronous `httpPost`) inside an `httpGet` callback closure (line 487). This double-nests hub thread blocking. Token refresh triggered by 401 should be async or at minimum handled outside the callback.
  3. **`state.rxBuffer` persisted on every `parse()` call** — Touchstone writes the hex receive buffer to `state` on every incoming TCP chunk (line 479). Hubitat state writes are relatively expensive I/O; only persist the buffer when a partial frame remains after processing. Clear on next write rather than on every call.
  4. **Dead state writes** — Gemstone stores `state.idToken` (line 1121) on every Cognito auth but never reads it for any API call. `state.lastDps` in Touchstone (line 1109) is written but never read. Audit `state.*` for write-only fields; remove or make them explicitly "diagnostic only".
  5. **O(n) reverse-index scans** — `effectNameForPatternId` and `effectIndexForPatternId` in Gemstone do `.find {}` linear scans over the catalog on every effect activation. Build reverse lookup maps (patternId→name, patternId→index) at catalog finalization time to make lookups O(1).
  6. **`cloneMap` JSON round-trip overhead** — Gemstone's `cloneMap()` does `JsonSlurper().parseText(JsonOutput.toJson(source))` for every map copy (~14 call sites, hot paths). For shallow maps, `new LinkedHashMap(source)` is much faster. Reserve JSON round-trip deep-copy only for maps with nested mutable structures.
  7. **Boxed Integer in byte-copy inner loops** — Touchstone uses `for (Integer i = ...)` in `concatBytes`, `sliceBytes`, `startsWithBytes`, `protocol33HeaderBytes`. Use `int` (primitive) to avoid autoboxing. `System.arraycopy` (java.lang, sandbox-safe) is even better for bulk copies.
  8. **Guard block copy-paste** — Gemstone duplicates the same credential+catalog guard verbatim in 5 command handlers (`setEffect×2`, `setNextEffect`, `setPreviousEffect`, `refreshEffectCatalog`). Extract to a private helper to reduce maintenance surface.
  9. **`infoLog` double-negative guard** — Touchstone checks `settings.txtEnable != false` (line 1643). Prefer `settings.txtEnable == true` for clarity; a missing/null setting reads as enabled with the double-negative.
  10. **Missing `capability "Actuator"` on command-accepting parent** — SunStat parent accepts commands (setHome, setAway, setAwayMode, setRefreshToken, discoverDevices) but doesn't declare `capability "Actuator"`. Convention: any driver that accepts commands should declare Actuator.
  11. **`USER_AGENT` literal not linked to `DRIVER_VERSION`** — All three drivers hard-code the version in both `DRIVER_VERSION` and `USER_AGENT`. The sandbox prevents cross-@Field refs but a comment "keep in sync with DRIVER_VERSION" should appear on both lines (not just USER_AGENT).

- 2026-05-17T15:50:06-07:00 — Init-time stale-flag-reset is a common pattern for stateful async drivers: any driver that guards operations behind `state.*InFlight` boolean flags must reset those flags to `false` at the top of `initialize()`, because a hub reboot or crash mid-operation leaves them `true` and causes all subsequent operations to silently no-op. If a third driver adopts this pattern, extract a shared `clearInFlightFlags()` private helper rather than duplicating the reset block.

- 2026-05-17T15:50:06-07:00 — SunStat async migration pattern (v0.1.5): keep token refresh synchronous (`refreshTokensSync`) so the caller always has a valid token before fan-out; dispatch per-device polls and location-state fetches via `asynchttpGet` with a data map carrying `[childDni, deviceId, retry401: true]`; on 401 in a callback, call `throttled401Refresh()` (rate-limited to once per 60s) then re-issue as a fresh `asynchttpGet` with `retry401: false` — never nest a sync HTTP call inside an async callback. For PATCH, use `asynchttpPatch` with the same 401 single-retry pattern.

- 2026-05-17T15:50:06-07:00 — Pseudo-boost pattern for cloud thermostats (SunStat v0.1.6): when no native boost API exists, implement boost as a driver-managed state machine: (1) save preBoostSetpoint; (2) PATCH the real setpoint + optionally suppress schedule; (3) set `state.boostActive = true`, `state.boostUntil = now() + window`; (4) arm `runIn(seconds, "boostExpired")` — always `unschedule` first to prevent duplicates; (5) on `boostExpired()`/`cancelBoost()`, restore setpoint + schedule, clear state, unschedule. Hub-restart recovery has two paths: `initialize()` re-arms the timer if `boostUntil` is in the future, else immediately calls `boostExpired()`; `parseDeviceState` (poll callback) checks the same condition so a boost that overran while the hub was offline is caught on the next poll. This pattern is reusable for any "timed override" feature on cloud thermostats (vacation presets, setback overrides, etc.).

- 2026-05-17T20:04:44-07:00 — HealthCheck capability pattern (Touchstone v0.1.21): `ping()` calls `sendHeartbeat()` immediately (reusing existing frame builder), sets `state.pingPending = true` and `state.pingRequestedAt = now()`, then arms `runIn(5, "pingTimeout")`. In `parse()`, on every successful inbound frame, stamp `lastActivity`, clear `pingPending`, and emit `healthStatus = online`. `pingTimeout()` checks if `state.lastSocketEventTs < state.pingRequestedAt`; if so, emits `healthStatus = offline`. For socketState, `scheduleReconnect()` emits `healthStatus = offline` only after ≥2 consecutive reconnect attempts (checked via `state.reconnectAttempts >= 2`) to suppress flicker on the first miss. `initialize()` and `updated()` both reset `state.pingPending = false` to clear orphan state from prior sessions.

- 2026-05-17T20:04:44-07:00 — lastActivity-only pattern for cost-sensitive cloud drivers (Gemstone v0.4.11, SunStat v0.1.7): declare `attribute "lastActivity", "string"` in metadata; add a `touchActivity()` private helper that emits `sendEvent(name: "lastActivity", value: new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX"), ...)`. Call it exclusively on 2xx success paths — never on error, 401, or timeout branches. For parent/child drivers (SunStat), the parent's `touchActivity()` also cascades to all children via `child.setLastActivity(ts)`; the child exposes a `void setLastActivity(String timestamp)` method that controls its own event emission (cleaner than parent calling `child.sendEvent()` directly).

- 2026-05-18 — Log hygiene pattern for Tuya protocol drivers (Touchstone v0.1.22): the two-tier `debugLog` / `traceLog` split keeps `logEnable=true` readable in production. **Trace taxonomy (off by default):** heartbeat send + ACK, periodic refresh queue/send/receive/decoded-payload, read-only DP echoes (e.g. DP 105), per-DP echoes when decoded value equals current device attribute. **Debug taxonomy (on when logEnable is enabled):** user-initiated writes (on/off, setFlameColor, etc.), per-DP echoes where the value *changed*, socket lifecycle events, protocol mode switches. **"Log only on change" rule:** before `debugLog` in `applyDps`, compare decoded label against `device.currentValue(attr)` — route to `traceLog` if unchanged. **Heartbeat split pattern:** check `cmd == TUYA_CMD_HEARTBEAT` before the generic received-frame log and route to `traceLog`; non-heartbeat cmds fall through to `debugLog`. **Refresh chatter suppression:** gate "Queued" and "Sent" log lines on `reason == "refresh"` → `traceLog`, else `debugLog`. The `traceEnable` preference mirrors `logEnable` with its own `traceOff()` auto-disable at 30 minutes. Protocol behavior (timing, DP map, command dispatch) must never be changed during a logging-only cleanup.

- 2026-05-17T20:04:44-07:00 — When to add full HealthCheck vs just lastActivity: local TCP drivers (e.g. Touchstone) with persistent sockets should get the full `capability "HealthCheck"` + `ping()` — the socket can silently die between heartbeats and `ping()` forces an immediate probe at zero cost. Cloud REST drivers (Gemstone, SunStat) should use `lastActivity`-only — adding `ping()` would burn API quota on every hub health-check cycle. The lightweight timestamp lets Rule Machine and dashboards detect stale cloud connectivity (e.g., "no activity in 10 min → alert") without per-ping API hits.

- 2026-05-17T20:04:44-07:00 — Hook points discovered for each driver's response-handling code: Touchstone — `parse()` after `consumeReceiveBuffer()` returns processed > 0 (covers heartbeat ack, push frames, command responses); Gemstone — `apiResponseCallback()` just before `updateAuthenticatedStatus()` at end (covers refresh, command, effectCatalogPage) + `handleDevicesResponse()` before its own `updateAuthenticatedStatus()` call (covers the discoverDevices early-return path); SunStat — `pollChildDeviceCallback()` at the status-200 success block before `child.parseDeviceState()`, `fetchLocationStateCallback()` when `loc` is found before `parseLocationState()`, and `refreshTokensSync()` inside the `httpPost` success block after `scheduleProactiveRefresh()`.

---

Participated in 4-way driver improvement scan with Trinity, Cypher, Switch. Findings consolidated by Squad. Orchestration log: .squad/orchestration-log/2026-05-17T15-41-32-tank.md.

---

## 2026-05-17T15:50:06Z — Touchstone v0.1.6 — flame speed, log brightness, drop power attribute

### DP 103 — Flame Speed label↔value mapping (community-derived; Switch to verify on hardware)

| Label | DP value sent |
|-------|--------------|
| `"Slow"` | `"1"` |
| `"Medium"` | `"2"` |
| `"Fast"` | `"3"` |

`FLAME_SPEED_OPTIONS = ["Slow", "Medium", "Fast"]`. These labels are inferred — the Sideline Elite YAML/device reported DP 103 as a 3-value enum but label names were not directly observed. Switch should run `setFlameSpeed("Slow")` / `"Medium"` / `"Fast"` and watch for visible flame animation differences.

### DP 105 — Log Brightness

`LOG_BRIGHTNESS_OPTIONS = ["1".."12"]` (raw numeric strings, 12 levels). Mirrors the `logColor` raw-string pattern. Sent as-is to the device; the device interprets them as integer enum steps. No label translation needed (user sees 1–12 which is self-describing).

### `power` attribute removal

The `power` attribute (`attribute "power", "enum", ["on", "off"]`) was a duplicate of `switch`. It was emitted alongside every `switch` event, causing doubled events for the same state change. Removed in v0.1.6. `switch` is the canonical on/off attribute. The internal DP role key `"power"` (maps to DP 1) is unchanged — it's a role string in `SIDELINE_PROFILE_DPS`, not the removed attribute.


## Team updates

- 2026-05-17: Participated in top-3 driver improvements batch — sunstat v0.1.6, touchstone v0.1.6, gemstone v0.4.9.

## Learnings (continued)

- 2026-05-17T16:15:00-07:00 — When inserting a new function near an existing one, ALWAYS verify the surrounding `def` lines are intact in a post-edit view — a single misaligned old_str can swallow a signature line and produce orphan-body parse errors that only surface at hub load time.

- 2026-05-17T16:19:32-07:00 — defaultLogBrightness is the symmetric gap — v0.1.6 added setLogBrightness without a corresponding default preference. Mads asked only for flame speed in v0.1.8; flag this as a likely follow-up if he reports it.

- 2026-05-17T23:22:29-07:00 — Touchstone v0.1.6 power-on defaults now have full v0.1.6-command symmetry — every named command has a `default*` preference counterpart that auto-applies during the defaults window. The pattern is firmly established: input declaration + application block + DP-write.

## Learnings (continued v0.1.10)

- 2026-05-17 — Hubitat enum attribute display: when the OPTIONS list labels are themselves numeric strings ("1","2","3"...), the inbound parse path must NOT use the DP value as an array index — that produces an off-by-one (and blanks the UI on the max value). Use direct passthrough or a labeled lookup table. Additionally, the defensive bounds-check must be present before any `emitAttribute` call on enum DPs: emit only if the incoming DP value is in the known options list, else log.warn and bail. Without this guard, out-of-range device values (or driver bugs) silently set the attribute to a value that is not in the enum list, causing the Hubitat dropdown to blank.

## 2026-05-17 (session tank-4) — Touchstone v0.1.10 enum bounds-check hardening

**Requested by:** Mads (real-hardware bug: display blanking on out-of-range device echoes)

### What shipped

Added in OPTIONS bounds-checks + log.warn + early bail in pplyDps() for enum DPs (101, 102, 103, 104, 105) before writing to UI attributes.

**Why:** Device sometimes pushes out-of-range DP values during echo (DP write response). Without bounds-check, driver applies invalid value to enum attribute, causing Hubitat UI to blank (value not in declared OPTIONS list).

**Verification:** Mads reported "one higher" display anomaly (showed flame effect 4 when device had 3). Investigation: NOT driver off-by-one, likely device firmware noise. Fix prevents invalid values from reaching UI.

**Commit:** 3fe727c — bounds-checks for all enum DPs.

**v0.1.10 status:** Awaiting Cypher's empirical test result (DP 105/109 read-only confirmation) before final changelog.

- 2026-05-17 — When fixing display bugs in Hubitat drivers, examine BOTH the WRITE-side emit AND the INBOUND parse paths. v0.1.10 fixed the parse-side (added bounds checks for echoed-back DP values) but missed the actual write-side off-by-one that emitted OPTIONS[dpValue] after computing dpValue = idx + 1. The lesson: when a user reports 'always shows +1', the bug is almost certainly in the WRITE path where the same off-by-one math is reused for both wire output AND attribute emit.

- 2026-05-17 — Hubitat Commands-tab dropdown bug: when a command parameter declares `type: 'ENUM'` with numeric-string constraints (e.g., ['1','2','3','4','5']), the dropdown widget advances one position after the user presses Set — purely cosmetic platform UI quirk, value sent and attribute emitted are both correct. Workaround: declare such parameters as `type: 'NUMBER'` with `range: 'N..M'` instead — Hubitat renders an input field, no dropdown to advance. Label enums (non-numeric strings) keep ENUM since they're not affected as severely and changing them harms UX.

- 2026-05-17T17:39:11-07:00 — Touchstone color palettes (DP 101 flame, DP 104 log) stay NUMBER input until someone with hardware reports the actual visible color for each palette index. Inventing labels without verification creates worse UX than honest numeric input. setFlameBrightness named-ENUM is appropriate because Dimmest/Dim/Medium/Brighter/Brightest are hardware-independent labels — the mapping is intuitive regardless of device.

- 2026-05-17 — Touchstone flame color palette (DP 101) verified labels from Tuya app screenshot: 1=Orange (default), 2=Blue, 3=White, 4=Orange+Blue, 5=Orange+White, 6=Blue+White. Future verifications for other Tuya devices should also request app screenshots before inventing labels.


- Touchstone DP 104 = 'Charcoal Color' in the Tuya app (not 'Log Color' — the driver historically used the wrong term). 12 palette values verified: 1=Orange (default), 2=Red, 3=Blue, 4=Yellow, 5=Green, 6=Purple, 7=Cyan, 8=Magenta, 9=White, 10=Pink, 11=Rainbow (8-segment), 12=Spotlight (best-guess; mostly-white circle with orange wedge in the app). Rename completed in v0.1.17 — breaking change, no alias.

## Learnings (v0.1.18 — Persistent Socket)

- 2026-05-17 — **Persistent socket lifecycle pattern:** use `interfaces.rawSocket.connect()` on `initialize()` and keep it open forever. Schedule a 10 s heartbeat via `runIn(HEARTBEAT_INTERVAL_SECONDS, "sendHeartbeat")` inside the `sendHeartbeat` handler itself — this is the Hubitat-safe alternative to sub-minute cron (`schedule()` can be unreliable for sub-60 s intervals on some hub versions). On success, `sendHeartbeat` reschedules itself; on send failure, it calls `scheduleReconnect()` and returns without rescheduling.

- 2026-05-17 — **Tuya heartbeat (cmd 9) requires truly empty payload.** `buildTuyaFrame(TUYA_CMD_HEARTBEAT, "")` would AES-encrypt the empty string, producing 16 bytes of PKCS5 padding — that's NOT a valid Tuya heartbeat. Fix: add a special case in `encryptTuyaPayload()` that returns `new byte[0]` when `cmd == TUYA_CMD_HEARTBEAT`. The frame then has length=8 (crc+suffix only), matching the `b'\x00\x00\x55\xaa...\x00\x09\x00\x00\x00\x0c...'` reference bytes from TinyTuya.

- 2026-05-17 — **intentionalCloseAt timestamp pattern for socketStatus suppression:** rather than a boolean `intentionalClose` flag (which has race conditions between `closeSocket()` → `initialize()` clearing it → `socketStatus()` firing), use a timestamp: `state.intentionalCloseAt = now()` in `closeSocket()`, and in `socketStatus()` check `(now() - intentionalAt) < 3000L`. Stale timestamps from previous sessions are safe — after a hub reboot, `now()` will be days/hours later, so `now() - oldTs >> 3000` and the guard never fires.

- 2026-05-17 — **Push frame handling is free:** existing `parse()` → `consumeReceiveBuffer()` → `processFrame()` → `applyDps()` pipeline already handles any inbound Tuya STATUS frame (cmd 8) regardless of whether it was solicited. Push frames from the physical remote arrive on the same socket and get processed identically. No separate push handler needed — just don't close the socket after a response.

- 2026-05-17 — **pumpQueue without ensureSocketConnected:** in the persistent socket model, `pumpQueue()` should check `state.socketOpen != true` and return early rather than trying to connect on-demand. The reconnect path (`scheduleReconnect` → `reconnectSocket` → `openSocket`) will pump the queue after it succeeds. This avoids a race where pumpQueue opens a second connection while reconnect is already in progress.

- 2026-05-17 — **Do NOT close socket on responseTimeout in persistent model.** The old pattern (responseTimeout → closeSocket → scheduleRetry) was necessary when each poll opened a fresh connection. With a persistent socket, closing on timeout would tear down the connection unnecessarily. Instead: requeueInFlight + scheduleRetry only. If the socket is genuinely broken, `socketStatus()` will fire and trigger reconnect independently.

- 2026-05-17 — **Poll interval reduction with persistent socket:** 5 minutes is the right default for the safety-net refresh poll when push updates are live. The old 60 s default was compensating for missed physical-remote events that are now handled by push frames. Reducing the default avoids unnecessary load on both the Hubitat hub and the Tuya device.

- 2026-05-17 — **Multi-controller binding via blank-defaults preference (Gemstone v0.4.10):** The cleanest way to support "N physical devices, each bound to a different cloud entity" is a single `controllerName` text preference on the existing driver. Blank = first-found (100% backward compat). Non-blank = case-insensitive trim match against discovered device names. Graceful degradation: no-match logs available names and falls back to first device. Set `state.availableControllers` (sorted, comma-joined) after discovery so users can copy-paste exact spelling. Suppress the "multiple controllers" warning when `controllerName` is set — multiple devices is expected and the warn would be noise. This pattern is reusable for any cloud driver where the same API account can return multiple independently addressable devices.

- 2026-05-17 — **Name-match sanitization idiom:** always do `settings.controllerName?.trim()?.toLowerCase()` and `safeString(it?.name).trim().toLowerCase()` before comparing. Guards against leading/trailing spaces in user preferences AND in cloud-returned names. Gemstone controller names come from a mobile app where users may accidentally add spaces.

- 2026-05-17 — **Backward-compat-via-blank-preference idiom:** when adding a new binding preference to an existing driver, make the blank/null case reproduce the exact old behavior with zero code divergence. Use `?: ""` to normalize null/blank to empty string, then `if (wanted)` to branch. Users upgrading see no change unless they explicitly set the new preference.


## Learnings (v0.1.19 + v0.1.20 + HPM Bundle — 2026-05-17)

### DP 108 Child Lock

- 2026-05-17 — Boolean DP wiring pattern: DP 108 is a Tuya BOOL type. Write: sendDpWrite("108", normalized == "on", ...). Read in applyDps(): asBoolean() + map to on/off string. Key gotcha: do not name the method parameter "state" — shadows Hubitat's state map. Use lockState or similar.
- 2026-05-17 — Attribute type for boolean DPs: declare as attribute "childLock", "enum", ["on", "off"]. Constraint list should match command ENUM constraints exactly.
- 2026-05-17 — Optimistic attribute emit on write: emit childLock before device echo. Device echo via applyDps() DP 108 overwrites with confirmed state ~2 s later. Pattern: emit on write side, also handle in applyDps().

### HPM Bundle Assembly

- 2026-05-17 — Bundle UUID reuse required: id UUID in bundle packageManifest.json MUST match per-driver manifest UUID. HPM Match-Up uses id+name+namespace. Diverged UUIDs create duplicate installs.
- 2026-05-17 — release.yml gotcha for root manifest: find drivers hard-codes the drivers/ prefix; root manifest not found. basename(dirname(root manifest)) returns "." breaking tag generation. Fix: update find to scan root; add conditional for driver_dir == "." to set tag=bundle-vX.Y.Z and skip changelog extraction.
- 2026-05-17 — No per-driver version fields in bundle: use only top-level version. Mixing top-level + per-driver versioning causes HPM update-check issues.
- 2026-05-17 — Bundle version bump convention: when any per-driver bumps, also bump root packageManifest.json. Document in repo README Contributing section.

### Active TCP Discovery State Machine

- 2026-05-17 — Smart-range scan via pre-computed queue: build probe order in discover() as state.discoveryProbeQueue (List of ints). Smart phase: +-20 from known IP first. Full sweep: remaining 1-254. discoveryProbeNext() pops from front. 254 integers in state ~1 KB — fine for Hubitat state storage.
- 2026-05-17 — Socket state during discovery: stamp state.intentionalCloseAt = now() before each close in discoveryProbeNext(). Reuses v0.1.18 suppression mechanism to prevent each probe-close from triggering normal disconnect->reconnect handler.
- 2026-05-17 — Guards against cross-contamination: add discoveryMode guards in openSocket(), reconnectSocket(), sendHeartbeat(). Without these, a scheduled reconnect from before discovery could fire and clobber state.socketOpen during the scan.
- 2026-05-17 — Fail-closed devId match: only accept match if response.devId == storedDevId. If no devId in response (heartbeat echo, wrong-key garbage), log warn and skip. Prevents wrong Tuya device being accepted.
- 2026-05-17 — parse() post-processing guard: during discovery, consumeReceiveBuffer() routes to discoveryHandleResponse() via processFrame(). Add discoveryMode guard after consumeReceiveBuffer() in parse() to skip pumpQueue() and other normal-mode operations.
- 2026-05-17 — Timeout-based fallback for unreachable IPs: Hubitat rawSocket does not guarantee synchronous failure. Always schedule runIn(3, "discoveryProbeTimeout") after each connect. Cancel with unschedule("discoveryProbeTimeout") when discoveryHandleResponse fires. Timeout moves to next IP.

### 2026-05-18 — Pending perf/quality todo sweep

- Repo-backed audit status: Trinity's redundant-write audit is effectively closed except **SC-4** (`setFloorMinTemp` still lacks a cached-current-value guard). The later perf audit is only partially recoverable from commit history: fixes **#1** (lastActivity throttling), **#3** (Touchstone heartbeat 10s→20s), and **#5** (SunStat child telemetry dedupe) are documented in shipped commits, but the underlying specs for **#2** and **#4** were not preserved in current `.squad/decisions*` files.
- Highest-value still-open Touchstone perf items are all in the hot parse path: stop persisting `state.rxBuffer` on every chunk when no partial frame remains, remove dead `state.lastDps` writes, and replace byte-copy helper loops with primitive/System.arraycopy-style copies. These are pure driver-internal optimizations in `drivers/touchstone-fireplace/touchstone-fireplace.groovy`.
- The next Gemstone wins are cache-shape and event-hygiene work in `drivers/gemstone-lights/gemstone-lights.groovy`: add reverse lookup maps for `patternId -> name/index`, replace `cloneMap()` JSON round-trips with a lighter copy strategy, and gate unchanged refresh telemetry (`switch`/`level`/`hue`/`saturation`) behind change checks.
- SunStat's remaining repo-backed audit item is still `setFloorMinTemp()` in `drivers/sunstat-thermostat/sunstat-thermostat-child.groovy`: cache the parsed `Schedule.Floor.W` value alongside `state.floorAway`, then skip the read-modify-write PATCH when the requested warmth value already matches.

## Learnings (v0.1.28 — 2026-05-18)

- 2026-05-18 — Touchstone parse-path buffer hygiene: build the concatenated socket hex in a local variable, feed it through `consumeReceiveBuffer(buffer)`, and persist `state.rxBuffer` only when a partial Tuya frame remains; fully-consumed chunks should remove the state key instead of rewriting it.
- 2026-05-18 — Parse/poll event dedupe in Hubitat should be path-aware: compare inbound telemetry against `device.currentValue(...)` (numeric attrs via `BigDecimal`) and skip unchanged `sendEvent`s on the parse path, while leaving user-command handlers free to emit immediate digital events after real outbound writes.
- 2026-05-18 — Hot-path clone hygiene for Hubitat Groovy: if the real data shapes are `Map`/`List` trees of scalars (Gemstone patterns, queued request payloads), replace `JsonOutput.toJson(...)` + `JsonSlurper.parseText(...)` deep copies with a small recursive container clone. You keep state/request isolation without paying JSON serialization cost on every refresh, queue, and effect activation.

## 2026-05-18 — Touchstone v0.1.29 (perf todos #6/#7)

### What changed

- Removed the dead `state.lastDps` write from `processFrame()` after re-grepping the driver and confirming there were no `state.lastDps` readers to migrate.
- Added one-time `state.remove("lastDps")` cleanup in `initialize()` so upgraded devices shed the stale state key without putting the write back on the parse path.
- Reworked `concatBytes()`, `sliceBytes()`, `startsWithBytes()`, and `protocol33HeaderBytes()` to use primitive `int` counters, with `System.arraycopy(...)` for contiguous copies in concat/slice/header assembly.
- Bumped the driver and `drivers/touchstone-fireplace/packageManifest.json` to `0.1.29` and added release-note-safe changelog entries dated `2026-05-18`.

### Why

- These were the last two medium-priority Touchstone perf items still sitting directly on the Tuya send/receive path. Removing dead state churn and boxed byte-copy loops trims Hubitat overhead without touching AES framing, CRC32, or any reflection-sensitive code.

## Learnings (v0.1.29 — 2026-05-18)

- 2026-05-18 — When retiring a hot-path `state.*` cache in a Hubitat driver, remove the write from the hot path and clear the legacy key once during `initialize()`; otherwise the stale state variable survives upgrades even though nothing reads it anymore.
- 2026-05-18 — Tuya framing helpers on Hubitat should stay on plain `byte[]` plus primitive `int` math: use `System.arraycopy(...)` for contiguous `concatBytes`/`sliceBytes`/header copies, and reserve manual loops only for comparison scans like `startsWithBytes()`.


---
## Archived Prior (moved from history.md at 2026-05-18T17:11:04Z)
