# Project Context

- **Owner:** Mads Kristensen
- **Project:** hubitat-drivers — Groovy device drivers for Hubitat Elevation
- **Stack:** Groovy (Hubitat sandbox), Hubitat platform APIs
- **Created:** 2026-05-16

## Active Milestones

### 2026-05-17: Gemstone Lights v0.4.0 — HPM Release Infrastructure & Public Listing
**Status:** Complete

**Scope:**
- Root repository.json — HPM publisher index pointing to driver packageManifest
- .github/workflows/release.yml — Automated release workflow on packageManifest version bump
- RELEASING.md — Six-step version-bump checklist
- README.md (updated) — Added HPM install instructions
- release-tools/ — Community PR handoff (instructions, JSON snippet, PR body)
- .squad/ team infrastructure — Decisions, agent charters, histories, casting registry

**Deliverables:**
- **GitHub Release:** https://github.com/madskristensen/hubitat-drivers/releases/tag/gemstone-lights-v0.4.0
- **HPM Publisher Index:** https://raw.githubusercontent.com/madskristensen/hubitat-drivers/main/repository.json
- **Community PR:** https://github.com/HubitatCommunity/hubitat-packagerepositories/pull/106 (awaiting maintainer merge)

**Status:**
✅ v0.4.0 released and live on GitHub
✅ HPM publishing kit complete
✅ Community PR submitted (awaiting merge for full HPM discoverability)

### 2026-05-16T20:01:41-07:00: SunStat Connect Plus v0.1.0

**Status:** COMPLETED

**Scope:** Full parent/child driver pair for the SunStat Connect Plus electric floor heating thermostat via the Watts® Home cloud API.

**Files delivered:**
- drivers/sunstat-thermostat/sunstat-thermostat-parent.groovy — cloud auth, token refresh, device discovery, polling, command routing
- drivers/sunstat-thermostat/sunstat-thermostat-child.groovy — Thermostat capability surface, all command handlers, parseDeviceState()
- drivers/sunstat-thermostat/CHANGELOG.md — v0.1.0 entry

**Architecture:**
- Parent holds all credentials and tokens; children never touch auth
- Azure AD B2C token refresh via httpPost with application/x-www-form-urlencoded encoding
- Proactive token refresh scheduled ~5 minutes before expiry; full retry on 401
- Children delegate all API calls via parent.sendDevicePatch(deviceId, settingsMap)
- Parent pushes state via child.parseDeviceState(body) after each poll

**Key implementation notes:**
- Floor probe disconnected sentinel guard: floorTemperature > 110°F → device.deleteCurrentState() + warning log
- thermostatSetpoint mirrors heatingSetpoint (heat-only device)
- setBoost / cancelBoost are honest stubs — API not documented; log warning, no API call
- All Thermostat capability commands present; unsupported ones (cool/fan/emergencyHeat) log warnings
- supportedThermostatModes = ["heat", "off"], supportedThermostatFanModes = ["auto"] set on installed()
- Temperature unit conversion between API unit and location.temperatureScale on every poll
- Setpoint clamped to data.Target.Min/data.Target.Max cached from each poll

## Team Updates (2026-05-17T03:01:41Z)

**SunStat Connect Plus v0.1.0 shipped.** Driver implementation (sunstat-thermostat-parent.groovy, sunstat-thermostat-child.groovy) delivered with full parent/child architecture, token refresh middleware, and capability profile. Trinity's architecture and Switch's test plan finalized. Awaiting Mads' real-device verification (Mode.Enum, modelId, ROPC probe, httpPatch sandbox compatibility).

---

**For archived sessions and learnings, see history-archive.md**

## Learnings

### EnergyMeter Capability Quirks
Adding `capability "EnergyMeter"` gives you the built-in `energy` attribute automatically — no separate `attribute "energy", "number"` declaration needed (Hubitat adds it for you). Custom sibling attributes (`energyYesterday`, `energyMonth`, `energyLastMonth`) must be declared explicitly. The `energy` event requires `unit: "kWh"` to display correctly in dashboards. Always guard array access with `instanceof List` before indexing — older SunStat firmwares may omit the entire `data.Energy` block; log a debug message and skip rather than erroring.

### Hold/Schedule Attribute Patterns
API integer-as-boolean fields (e.g. `data.Target.Hold`) should map to descriptive string enums (`"holding"/"following"/"unknown"`) rather than `"true"/"false"` — this is more self-documenting and survives future API revisions where non-zero values may encode hold duration. For API string enums that must be surfaced as commands (e.g. `SchedEnable`), always use lowercase Hubitat-side values (`"on"/"off"`) even if the API uses titlecase (`"On"/"Off"`). Always emit optimistic attribute updates before the PATCH call so dashboards respond immediately.

### Step-Rounding Pattern for Setpoints
When `data.Target.Steps` is present, persist it to `state.setpointStep` each poll and apply it in every setpoint write command: `rounded = (Math.round(temp / step) * step).setScale(2, ROUND_HALF_UP)`. A `validStep()` helper (returns `step > 0 ? step : 1.0`) prevents division-by-zero if the API returns 0 for an unknown firmware. Apply step-rounding *before* clamping, not after, so the clamped value is always on a valid step boundary.

### Command-vs-Preference for Long Strings (v0.1.3)
Hubitat's `password`/`text` preference type silently truncates values beyond ~1024 chars. For tokens longer than that (e.g. Azure AD B2C refresh tokens at ~1660 chars), use a `command` with a `STRING` parameter instead — command arguments bypass the preference size limit. v0.1.3 removed the `refreshToken` password preference, added `setRefreshToken(String)` command, removed the initialize() migration block, simplified `tokenBootstrapReady()` and `refreshTokensSync()` to state-only reads. Lines touched in parent: header/constants (~4–31), definition (~54), preferences (~60–65), updated() (~110), initialize() (~125–134), commands section (~192–206), refreshTokensSync() (~529–536), tokenBootstrapReady() (~704–707). packageManifest.json top-level + both driver entries bumped to 0.1.3.

## Team Updates (2026-05-16T21:07:23-07:00)

**SunStat Connect Plus v0.1.3 complete.** Replaced `refreshToken` password preference with `setRefreshToken(String)` command to bypass Hubitat's ~1024-char preference limit on saved values. Removed initialize() migration block; `state.refreshToken` is now the sole source of truth. packageManifest.json bumped to 0.1.3 across all entries.

- 2026-05-17T04-20-29Z: v0.1.3 SunStat Connect Plus shipped (setRefreshToken command + docs + tests) — tank/link/switch cross-team ship

## Learnings — 2026-05-17T04:24:48Z: locationId Discovery Code Audit

### What I found in the discovery code path

**`runDiscovery()` (lines 287–325)**
- `defaultLocId` is extracted as `body?.defaultLocationId` from the `GET /User` response.
- `settings.locationId` is a `text` preference (string). The code does `safeStr(settings.locationId).trim() ?: defaultLocId` — correct, but if the user never set it AND the API omits `defaultLocationId` from the User response (e.g., returns `null` or an empty string), both fallback slots are empty.
- Third fallback: `fetchFirstLocationId()` — if that also returns null, the "Could not resolve" error fires.
- No intermediate logging between fallbacks. The user sees only the final error message with no clue which of the three paths failed.

**`fetchFirstLocationId()` (lines 327–345)**
- Assumes the `/Location` endpoint returns a raw JSON **array** (`body instanceof List ? body : []`).
- If the API now returns `{"locations": [...], "total": N}` (envelope/wrapper shape), the cast yields `[]` and the method silently returns `null`.
- Non-200 status codes are silently ignored — the method returns `null` with zero logging.
- `resp.data` is used raw (no `parseResponseBody()` helper), so Content-Type negotiation surprises can produce unexpected types.
- Exception is logged but the return value is still `null` with no context about what shape the body was.

**`buildApiParams()` (lines 631–653)**
- Authorization: `Bearer ${state.accessToken}` ✅
- `Api-Version: 2.0` (via `WATTS_API_VERSION` constant) ✅
- `User-Agent: Hubitat SunStat Connect Plus/0.1.3` ✅
- All three are correctly set. Not the cause.

### What I'd change (ready-to-implement fix, pending Cypher's diagnosis)

**a. Loud diagnostic logging in `fetchFirstLocationId()`**
```groovy
if (status == 200) {
    def body = resp.data
    String bodyType = body?.getClass()?.simpleName ?: "null"
    String sample = (body instanceof Map) ? body.keySet().take(5).toString()
                  : (body instanceof List) ? "size=${body.size()}"
                  : body?.toString()?.take(80) ?: "(empty)"
    log.warn "[SunStat] GET /Location → HTTP ${status}, type=${bodyType}, sample=${sample}"
    // dual-shape handling below
} else {
    log.warn "[SunStat] GET /Location → HTTP ${status} (expected 200) — locationId unresolvable from this path"
}
```

**b. Handle both response shapes in `fetchFirstLocationId()`**
```groovy
List locations
if (body instanceof List)                        locations = body as List
else if (body instanceof Map && body.locations instanceof List) locations = body.locations as List
else                                              locations = []

if (!locations) {
    log.warn "[SunStat] GET /Location body has no usable locations list (type=${bodyType})"
}
result = safeStr(locations[0]?.locationId)
```

**c. Loud fallback logging in `runDiscovery()`**
After the three-path resolution chain, before the final error, add:
```groovy
String settingsVal = safeStr(settings.locationId).trim()
log.warn "[SunStat] locationId resolution: settings.locationId='${settingsVal}', " +
         "defaultLocationId='${defaultLocId}', fetchFirstLocationId='${resolvedLocationId}'"
```

No file writes, no version bump, no commit — waiting for Cypher's `cypher-sunstat-location-id-discovery-fix.md`.

## Team Updates (2026-05-16T21:24:48-07:00)

**SunStat Connect Plus v0.1.4 shipped.** Implemented Cypher's 6-change spec plus Mads' production URL-encoding bug fix. packageManifest.json and CHANGELOG.md updated.

### Learnings — v0.1.4

#### API Envelope Pattern
The Watts API wraps every response in `{errorNumber, errorMessage, body: <payload>}`. The previous `parseResponseBody()` returned the whole envelope Map instead of unwrapping `.body`, causing `body?.defaultLocationId` to always be null. Fix: check `m.containsKey("body") && m.body instanceof Map` → return `m.body`. Always do this check before returning a Map from an API response helper.

#### Dedicated List helper (`parseResponseList`)
List-body endpoints (`GET /Location`, `GET /Location/{id}/Devices`) can't be handled by `parseResponseBody()` because the envelope's `.body` is a List, not a Map. The fix is a dedicated `parseResponseList()` that unwraps `m.body as List`. Lesson: when an API has a universal envelope, write two unwrappers — one for Map payloads, one for List payloads — and never use `resp.data instanceof List` for real envelope-wrapped APIs.

#### URL-encoding for Path Segments (not query params)
`URLEncoder.encode(s, "UTF-8")` is form-encoding: spaces become `+`. URL path segments require `%20`. Always follow `URLEncoder.encode(...)` with `.replace("+", "%20")` when building path segments. Watts uses location display names (e.g. "Misty Gray") as locationIds — any space in a display name breaks Java's URI parser with "Illegal character in path at index N". Apply `encodePathSegment()` to every dynamic value spliced into a URL path, not just query strings.
- 2026-05-16: SunStat v0.1.4 shipped — envelope unwrap fix, URL encoding, bootstrap script

### WebCoRE Capability-Overload Shadowing (v0.4.1)

When a Hubitat driver declares `capability "LightEffects"`, Hubitat registers `setEffect(NUMBER)` as the canonical capability method in its metadata. WebCoRE's action picker reads that metadata. If you also declare `command "setEffect", [[type:"STRING"]]` in the same `definition{}`, it is a *Groovy overload* of the same method name — but WebCoRE only sees the one signature from the capability descriptor, so the String overload is **invisible** to the WebCoRE UI. The fix: give the WebCoRE-facing command a **distinct name** (`playEffectByName`) so there is no overload collision. WebCoRE then sees it as a separate command and renders a STRING input field. The implementation is a one-liner delegate to the existing `setEffect(String)` — no duplicated logic. Confidence: confirmed by Mads' production WebCoRE install.

- 2026-05-17T04:44:00Z: v0.4.1 Gemstone Lights shipped (playEffectByName command + docs + tests) — tank/link/switch cross-team ship

### v0.4.2 Gemstone — Diagnostic-First + Payload Theory (2026-05-16)

#### Capture errorData for 400 responses
Hubitat's async HTTP framework puts the error response body in `response.getErrorData()`, NOT `response.getData()`. `getData()` returns null on 4xx. Pattern: call `responseBody(response)` first; if empty, fall back to `response.getErrorData()` wrapped in a try/catch. This surfaces the actual API error message (e.g., `{"message":"Invalid pattern id format"}`), enabling targeted follow-up fixes without guessing.

#### Silent-queue guard pattern
`executeOrQueueRequest`'s Guard 2 (`!hasUsableAccessToken() || requiresDevice && !deviceId`) produces zero logs, making it impossible to distinguish "command queued" from "command silently dropped". Fix: add an unconditional `log.info` (NOT `infoLog` — must not be gated on `txtEnable`) with the specific reason before calling `queueRequest()`. Use `log.info`, never `infoLog`, for any diagnostic guard that must be visible regardless of user preferences.

#### ARGB alpha byte — color functions must include 0xFF alpha
`hubitatHueSatToArgb` and `kelvinToArgb` returned `0x00RRGGBB` (alpha=0, transparent). The Gemstone API uses full ARGB format; alpha=0 likely means invisible/no-op. Fix: prefix `(0xFF << 24) |` to the return value. Note: `0xFF000000` = `-16777216` as a signed 32-bit int — bitwise OR still produces the correct bit pattern; JSON serializes as a negative integer which is valid. Always include the alpha byte when building ARGB integers for APIs that use ARGB format.

#### UUID preservation in read-modify-write patterns
`buildColorRequest` and `buildColorTemperatureRequest` both called `pattern.id = generatePatternId()`, overwriting the real UUID returned by the API with a synthetic `"hubitat-{timestamp}-{seq}"` string. The Gemstone API validates pattern IDs as UUIDs and rejects non-UUID strings with HTTP 400. Fix: remove the `pattern.id` override entirely; `currentOrDefaultPattern()` already carries the real UUID from the last `refresh()`. Rule: in read-modify-write patterns, never replace server-assigned identifiers with client-generated synthetic strings unless the API explicitly allows custom IDs.

#### Omit null fields instead of sending null
`pattern.referencePatternId = null` sends `"referencePatternId": null` in the JSON body. If the API schema requires the field to be either a valid UUID or absent, null causes schema validation failure. Fix: `pattern.remove("referencePatternId")` omits the field entirely. Rule: for optional relationship fields, prefer omission over explicit null when the API's validation rules are unknown.

- v0.4.3 — flattened multi-line 400 bodies for single-line log display so Gemstone server tracebacks are visible. Hubitat log.error truncates at first newline — known platform pattern.
